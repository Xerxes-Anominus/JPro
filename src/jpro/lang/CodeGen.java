package jpro.lang;

import java.util.*;

import jpro.lang.Builtins.Builtin;
import jpro.lang.Builtins.Part;
import jpro.lang.Node.*;

/** Sinh mã mlog từ cây cú pháp. */
final class CodeGen{
    static final class Label{
        int index = -1;
    }

    static final class Fn{
        final String name;
        final List<String> params;
        final FnDecl decl;
        final Label start = new Label();
        final Set<String> locals = new HashSet<>();
        boolean returnsValue;

        Fn(FnDecl d){
            decl = d;
            name = d.name;
            params = d.params;
        }
    }

    private static final Map<String, String> BIN = new HashMap<>();
    private static final Map<String, String> CMP = new HashMap<>();
    private static final Map<String, String> NEG = new HashMap<>();

    static{
        BIN.put("+", "add"); BIN.put("-", "sub"); BIN.put("*", "mul"); BIN.put("/", "div");
        BIN.put("%", "mod"); BIN.put("**", "pow");
        BIN.put("==", "equal"); BIN.put("!=", "notEqual"); BIN.put("===", "strictEqual");
        BIN.put("<", "lessThan"); BIN.put("<=", "lessThanEq"); BIN.put(">", "greaterThan"); BIN.put(">=", "greaterThanEq");
        BIN.put("<<", "shl"); BIN.put(">>", "shr"); BIN.put("&", "and"); BIN.put("|", "or"); BIN.put("^", "xor");
        BIN.put("&&", "land");
        CMP.put("<", "lessThan"); CMP.put("<=", "lessThanEq"); CMP.put(">", "greaterThan"); CMP.put(">=", "greaterThanEq");
        CMP.put("==", "equal"); CMP.put("!=", "notEqual");
        NEG.put("lessThan", "greaterThanEq"); NEG.put("lessThanEq", "greaterThan");
        NEG.put("greaterThan", "lessThanEq"); NEG.put("greaterThanEq", "lessThan");
        NEG.put("equal", "notEqual"); NEG.put("notEqual", "equal");
    }

    /** Lệnh mlog "đặc quyền": chỉ chạy được ở processor cấp 2 (world). Dùng để kiểm tra khối asm ở cấp 1. */
    static final Set<String> PRIVILEGED_OPS = new HashSet<>(Arrays.asList(
        "getblock", "setblock", "spawn", "bullet", "status", "weathersense", "weatherset", "spawnwave", "setrule",
        "message", "cutscene", "effect", "explosion", "fetch", "sync", "clientdata", "getflag", "setflag", "setprop",
        "playsound", "playmusic", "setmarker", "makemarker", "localeprint", "query", "jdraw"));

    final List<Object[]> code = new ArrayList<>();
    final List<JProError> errors = new ArrayList<>();
    /** Cấp của processor: 1 = như logic processor, 2 = như world processor. */
    final int level;
    private int bindSites;

    CodeGen(){
        this(2);
    }

    CodeGen(int level){
        this.level = level;
    }

    private final Map<String, Fn> fns = new LinkedHashMap<>();
    final Set<String> exportedVars = new LinkedHashSet<>();
    final Set<String> exportedFns = new LinkedHashSet<>();
    final List<ImportDecl> importedVars = new ArrayList<>();
    final List<ImportDecl> importedFns = new ArrayList<>();
    private final Map<String, Object> consts = new HashMap<>();
    /** Biến final có giá trị chỉ biết lúc chạy: chỉ được gán lúc khai báo. Khóa = hàm + "." + tên. */
    private final Set<String> finals = new HashSet<>();
    private final ArrayDeque<Label[]> loops = new ArrayDeque<>();
    private Fn cur;
    private int tmp, uniq;

    // ---------- tiện ích ----------
    private JProError err(Expr e, String m){ return new JProError(e.line, e.col, m); }
    private JProError err(Stmt s, String m){ return new JProError(s.line, s.col, m); }

    /** Báo lỗi nếu tính năng chỉ có ở cấp 2 mà processor đang là cấp 1. */
    private void needWorld(int line, int col, String what){
        if(level < 2) throw new JProError(line, col, "'" + what + "' chỉ dùng được ở Processor cấp 2 (như world processor). Cấp 1 chỉ có các lệnh của logic processor");
    }

    private void emit(Object... parts){ code.add(parts); }
    private void place(Label l){ l.index = code.size(); }

    private String temp(){
        return (cur == null ? "" : cur.name) + "__t" + (tmp++);
    }

    private void jumpAlways(Label l){
        emit("jump", l, "always", "0", "0");
    }

    static String fmt(double d){
        if(d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long)d);
        return new java.math.BigDecimal(Double.toString(d)).toPlainString();
    }

    private static String operandOf(Object c){
        return c instanceof Double ? fmt((Double)c) : (String)c;
    }

    private void checkName(String n, int line, int col){
        if(n.contains("__")) throw new JProError(line, col, "Tên '" + n + "' không được chứa '__' (dành riêng cho hệ thống)");
    }

    /** Tên biến khi ĐỌC. */
    private String varOf(String n, Expr at){
        checkName(n, at.line, at.col);
        Object c = consts.get(n);
        if(c != null) return operandOf(c);
        if(n.equals("unit")) return "@unit";
        if(cur != null && cur.locals.contains(n)) return cur.name + "__" + n;
        return n;
    }

    /** Tên biến khi GHI / khai báo. */
    private String targetOf(String n, int line, int col){
        checkName(n, line, col);
        if(consts.containsKey(n)) throw new JProError(line, col, "'" + n + "' là hằng (final/const), không gán lại được");
        if(n.equals("unit")) throw new JProError(line, col, "'unit' là tên dành riêng cho @unit, hãy đặt tên biến khác");
        if(cur != null && cur.locals.contains(n)) return cur.name + "__" + n;
        return n;
    }

    // ---------- chương trình ----------
    void generate(List<Stmt> prog){
        List<Stmt> main = new ArrayList<>();
        Set<String> declaredVars = new LinkedHashSet<>();
        Set<String> declaredFns = new LinkedHashSet<>();
        Set<String> exportKeys = new HashSet<>();
        for(Stmt s : prog){
            if(s instanceof ExportDecl){
                ExportDecl e = (ExportDecl)s;
                String key = (e.function ? "f:" : "v:") + e.name;
                if(!exportKeys.add(key)) errors.add(err(e, "Export bị khai báo lặp: " + e.name));
                (e.function ? exportedFns : exportedVars).add(e.name);
            }else if(s instanceof ImportDecl){
                ImportDecl i = (ImportDecl)s;
                (i.function ? importedFns : importedVars).add(i);
            }else if(s instanceof Let){
                declaredVars.add(((Let)s).name);
            }else if(s instanceof FnDecl){
                declaredFns.add(((FnDecl)s).name);
            }
        }
        for(String n : exportedVars) if(!declaredVars.contains(n)) errors.add(new JProError(1, 1, "Export biến '" + n + "' chưa được khai báo"));
        for(String n : exportedFns) if(!declaredFns.contains(n)) errors.add(new JProError(1, 1, "Export hàm '" + n + "' chưa được khai báo"));
        Set<String> importKeys = new HashSet<>();
        for(ImportDecl i : importedVars){
            String key = i.processor + "." + i.name;
            if(!importKeys.add("v:" + key)) errors.add(err(i, "Import biến bị lặp: " + key));
        }
        for(ImportDecl i : importedFns){
            String key = i.processor + "." + i.name;
            if(!importKeys.add("f:" + key)) errors.add(err(i, "Import hàm bị lặp: " + key));
        }
        for(Stmt s : prog){
            if(s instanceof ExportDecl || s instanceof ImportDecl){
                // declarations handled in metadata pass above
            }else if(s instanceof FnDecl){
                try{
                    declareFn((FnDecl)s);
                }catch(JProError e){
                    errors.add(e);
                }
            }else main.add(s);
        }
        try{
            checkRecursion();
        }catch(JProError e){
            errors.add(e);
        }
        // Directives are metadata only in this compiler pass. Import resolution requires linked processor context,
        // which is provided by the runtime/editor linking integration (not standalone compilation).
        for(ImportDecl i : importedVars) errors.add(err(i, "Import '" + i.processor + "." + i.name + "' chưa được resolve: cần link processor và bộ phân giải import"));
        for(ImportDecl i : importedFns) errors.add(err(i, "Import hàm '" + i.processor + "." + i.name + "' chưa được resolve: cần link processor và bộ phân giải import"));
        for(Stmt s : main) safe(s);
        if(!fns.isEmpty()){
            emit("end");
            for(Fn f : fns.values()){
                cur = f;
                tmp = 0;
                place(f.start);
                safe(f.decl.body);
                emit("set", "@counter", f.name + "__ra");
                cur = null;
            }
        }
    }

    private void safe(Stmt s){
        try{
            genStmt(s);
        }catch(JProError e){
            errors.add(e);
        }
    }

    private void declareFn(FnDecl d){
        checkName(d.name, d.line, d.col);
        if(fns.containsKey(d.name)) throw err(d, "Hàm '" + d.name + "' bị khai báo hai lần");
        if(Builtins.isTopLevel(d.name)) throw err(d, "'" + d.name + "' trùng tên với hàm có sẵn, hãy đặt tên khác");
        final Fn f = new Fn(d);
        for(String p : d.params){
            checkName(p, d.line, d.col);
            f.locals.add(p);
        }
        Node.walkStmt(d.body, new StmtVisitor(){
            @Override
            public void visit(Stmt s){
                if(s instanceof Let && !((Let)s).isConst) f.locals.add(((Let)s).name);
                else if(s instanceof ForIn) f.locals.add(((ForIn)s).var);
                else if(s instanceof Return && ((Return)s).value != null) f.returnsValue = true;
            }
        });
        fns.put(d.name, f);
    }

    private void checkRecursion(){
        Map<String, Set<String>> graph = new HashMap<>();
        for(final Fn f : fns.values()){
            final Set<String> callees = new LinkedHashSet<>();
            Node.walkStmt(f.decl.body, new StmtVisitor(){
                @Override
                public void visit(Stmt s){
                    Node.stmtExprs(s, new Visitor(){
                        @Override
                        public void visit(Expr e){
                            if(e instanceof Call && ((Call)e).ns == null && fns.containsKey(((Call)e).name))
                                callees.add(((Call)e).name);
                        }
                    });
                }
            });
            graph.put(f.name, callees);
        }
        Set<String> done = new HashSet<>();
        for(String n : graph.keySet()) dfs(n, graph, new ArrayList<String>(), done);
    }

    private void dfs(String n, Map<String, Set<String>> g, List<String> path, Set<String> done){
        if(done.contains(n)) return;
        int at = path.indexOf(n);
        if(at >= 0){
            StringBuilder sb = new StringBuilder();
            for(int i = at; i < path.size(); i++) sb.append(path.get(i)).append(" → ");
            sb.append(n);
            throw err(fns.get(n).decl, "Đệ quy không được hỗ trợ (mlog không có ngăn xếp): " + sb);
        }
        path.add(n);
        for(String c : g.get(n)) dfs(c, g, path, done);
        path.remove(path.size() - 1);
        done.add(n);
    }

    // ---------- câu lệnh ----------
    private void genStmt(Stmt s){
        int saved = tmp;
        if(s instanceof Let) genLet((Let)s);
        else if(s instanceof Assign) genAssign((Assign)s);
        else if(s instanceof ExprStmt) genCall(((ExprStmt)s).call, null, true);
        else if(s instanceof Block){
            for(Stmt c : ((Block)s).body) safe(c);
        }else if(s instanceof If) genIf((If)s);
        else if(s instanceof While) genWhile((While)s);
        else if(s instanceof ForC) genForC((ForC)s);
        else if(s instanceof ForIn) genForIn((ForIn)s);
        else if(s instanceof Loop){
            Label top = new Label(), end = new Label();
            place(top);
            loops.push(new Label[]{end, top});
            genStmt(((Loop)s).body);
            loops.pop();
            jumpAlways(top);
            place(end);
        }else if(s instanceof Once){
            String flag = "__once" + (uniq++);
            Label skip = new Label();
            emit("jump", skip, "equal", flag, "1");
            emit("set", flag, "1");
            genStmt(((Once)s).body);
            place(skip);
        }else if(s instanceof Switch) genSwitch((Switch)s);
        else if(s instanceof Break){
            Label[] l = loops.peek();
            if(l == null) throw err(s, "'break' chỉ dùng được bên trong vòng lặp hoặc switch");
            jumpAlways(l[0]);
        }else if(s instanceof Continue){
            // switch không có đích continue riêng: nhảy tới vòng lặp gần nhất bao quanh nó
            Label cont = null;
            for(Label[] l : loops){
                if(l[1] != null){
                    cont = l[1];
                    break;
                }
            }
            if(cont == null) throw err(s, "'continue' chỉ dùng được bên trong vòng lặp");
            jumpAlways(cont);
        }else if(s instanceof Return){
            Return r = (Return)s;
            if(cur == null){
                if(r.value != null) throw err(s, "'return giá_trị' chỉ dùng trong hàm (fn). Ở ngoài hàm hãy dùng 'return;' hoặc 'end;'");
                emit("end");
            }else{
                if(r.value != null) genExpr(r.value, cur.name + "__ret");
                emit("set", "@counter", cur.name + "__ra");
            }
        }else if(s instanceof End) emit(((End)s).stop ? "stop" : "end");
        else if(s instanceof Asm) genAsm((Asm)s);
        else throw err(s, "Câu lệnh không được hỗ trợ ở đây");
        tmp = saved;
    }

    private void genLet(Let s){
        if(s.isConst || s.isFinal){
            Object v = s.init == null ? null : constValue(s.init);
            if(v != null){
                checkName(s.name, s.line, s.col);
                if(consts.containsKey(s.name)) throw err(s, "Hằng '" + s.name + "' đã được khai báo");
                consts.put(s.name, v);
                return;
            }
            if(s.isConst) throw err(s, "const phải là giá trị cố định: số, chuỗi, @tên, màu hoặc hằng khác");
        }
        String name = targetOf(s.name, s.line, s.col);
        if(s.isFinal) finals.add(finalKey(s.name));
        if(s.init == null) emit("set", name, "null");
        else genExpr(s.init, name);
    }

    private Object constValue(Expr e){
        Double f = fold(e);
        if(f != null) return f;
        if(e instanceof Str) return "\"" + ((Str)e).v + "\"";
        if(e instanceof At) return "@" + ((At)e).name;
        if(e instanceof Col) return ((Col)e).text;
        if(e instanceof Lit) return ((Lit)e).text;
        if(e instanceof Name) return consts.get(((Name)e).name);
        return null;
    }

    private String finalKey(String n){
        return (cur == null ? "" : cur.name) + "." + n;
    }

    private void genAssign(Assign a){
        String binop = a.op.equals("=") ? null : a.op.substring(0, a.op.length() - 1);
        Expr tg = a.target;
        if(tg instanceof Name && finals.contains(finalKey(((Name)tg).name)))
            throw err(tg, "'" + ((Name)tg).name + "' là biến final, không gán lại được");
        if(tg instanceof Name){
            String name = targetOf(((Name)tg).name, tg.line, tg.col);
            if(binop == null) genExpr(a.value, name);
            else{
                String v = genExpr(a.value, null);
                emit("op", BIN.get(binop), name, name, v);
            }
        }else if(tg instanceof Index){
            Index ix = (Index)tg;
            String b = genExpr(ix.base, null);
            String i = genExpr(ix.idx, null);
            if(binop == null){
                String v = genExpr(a.value, null);
                emit("write", v, b, i);
            }else{
                String t = temp();
                emit("read", t, b, i);
                String v = genExpr(a.value, null);
                emit("op", BIN.get(binop), t, t, v);
                emit("write", t, b, i);
            }
        }else{
            Member m = (Member)tg;
            String o = genExpr(m.base, null);
            if(binop == null){
                setProp(m, o, m.prop, genExpr(a.value, null));
            }else{
                String t = temp();
                emit("sensor", t, o, m.prop);
                String v = genExpr(a.value, null);
                emit("op", BIN.get(binop), t, t, v);
                setProp(m, o, m.prop, t);
            }
        }
    }

    private void setProp(Expr at, String obj, String prop, String v){
        if(prop.equals("@enabled") || prop.equals("@config")) emit("control", prop.substring(1), obj, v, "0", "0", "0");
        else{
            needWorld(at.line, at.col, "Gán thuộc tính (" + prop + ", lệnh setprop)");
            emit("setprop", prop, obj, v);
        }
    }

    private void genIf(If s){
        Label els = new Label(), end = new Label();
        int saved = tmp;
        genCond(s.cond, els, false);
        tmp = saved;
        genStmt(s.then);
        if(s.els != null){
            jumpAlways(end);
            place(els);
            genStmt(s.els);
            place(end);
        }else place(els);
    }

    private boolean isTrue(Expr e){
        if(e instanceof Lit) return ((Lit)e).text.equals("true");
        Double f = fold(e);
        return f != null && f != 0;
    }

    private void genWhile(While s){
        if(isTrue(s.cond)){
            Label top = new Label(), end = new Label();
            place(top);
            loops.push(new Label[]{end, top});
            genStmt(s.body);
            loops.pop();
            jumpAlways(top);
            place(end);
            return;
        }
        Label body = new Label(), cond = new Label(), end = new Label();
        jumpAlways(cond);
        place(body);
        loops.push(new Label[]{end, cond});
        genStmt(s.body);
        loops.pop();
        place(cond);
        int saved = tmp;
        genCond(s.cond, body, true);
        tmp = saved;
        place(end);
    }

    private void genForC(ForC s){
        if(s.init != null) genStmt(s.init);
        Label body = new Label(), cont = new Label(), cond = new Label(), end = new Label();
        if(s.cond != null) jumpAlways(cond);
        place(body);
        loops.push(new Label[]{end, cont});
        genStmt(s.body);
        loops.pop();
        place(cont);
        if(s.step != null) genStmt(s.step);
        place(cond);
        if(s.cond != null){
            int saved = tmp;
            genCond(s.cond, body, true);
            tmp = saved;
        }else jumpAlways(body);
        place(end);
    }

    private void genForIn(ForIn s){
        String v = targetOf(s.var, s.line, s.col);
        int saved = tmp;
        Double stepV = s.step == null ? Double.valueOf(1) : fold(s.step);
        if(stepV == null || stepV == 0) throw err(s, "'step' phải là số cố định khác 0");
        genExpr(s.from, v);
        Double lf = fold(s.to);
        String limit;
        if(lf != null) limit = fmt(lf);
        else{
            limit = "__lim" + (uniq++);
            genExpr(s.to, limit);
        }
        tmp = saved;
        boolean up = stepV > 0;
        Label body = new Label(), cont = new Label(), end = new Label();
        Double ff = fold(s.from);
        boolean knownRun = ff != null && lf != null && (up ? ff < lf : ff > lf);
        if(!knownRun) emit("jump", end, up ? "greaterThanEq" : "lessThanEq", v, limit);
        place(body);
        loops.push(new Label[]{end, cont});
        genStmt(s.body);
        loops.pop();
        place(cont);
        emit("op", "add", v, v, fmt(stepV));
        emit("jump", body, up ? "lessThan" : "greaterThan", v, limit);
        place(end);
    }

    private void genSwitch(Switch s){
        int saved = tmp;
        String v = genExpr(s.subject, null);
        Label end = new Label();
        Label[] bodies = new Label[s.cases.size()];
        for(int i = 0; i < bodies.length; i++){
            bodies[i] = new Label();
            for(Expr val : s.cases.get(i).values){
                String x = genExpr(val, null);
                emit("jump", bodies[i], "equal", v, x);
            }
        }
        Label def = new Label();
        jumpAlways(s.def != null ? def : end);
        tmp = saved;
        // 'break' trong switch thoát khỏi switch (như Java); 'continue' không có đích riêng
        loops.push(new Label[]{end, null});
        for(int i = 0; i < bodies.length; i++){
            place(bodies[i]);
            genStmt(s.cases.get(i).body);
            jumpAlways(end);
        }
        if(s.def != null){
            place(def);
            genStmt(s.def);
        }
        loops.pop();
        place(end);
    }

    private static boolean isLabelLine(String ln){
        return ln.matches("[A-Za-z_][A-Za-z0-9_]*:");
    }

    private static List<String> splitTokens(String ln){
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for(int i = 0; i < ln.length(); i++){
            char c = ln.charAt(i);
            if(c == '"'){
                q = !q;
                sb.append(c);
            }else if(!q && Character.isWhitespace(c)){
                if(sb.length() > 0){
                    out.add(sb.toString());
                    sb.setLength(0);
                }
            }else sb.append(c);
        }
        if(sb.length() > 0) out.add(sb.toString());
        return out;
    }

    private void genAsm(Asm a){
        Map<String, Label> labels = new HashMap<>();
        for(String ln : a.lines){
            if(isLabelLine(ln)) labels.put(ln.substring(0, ln.length() - 1), new Label());
        }
        for(int i = 0; i < a.lines.size(); i++){
            String ln = a.lines.get(i);
            if(isLabelLine(ln)){
                place(labels.get(ln.substring(0, ln.length() - 1)));
                continue;
            }
            List<String> toks = splitTokens(ln);
            if(level < 2 && PRIVILEGED_OPS.contains(toks.get(0)))
                throw new JProError(a.lineNos.get(i), 1, "Lệnh mlog '" + toks.get(0) + "' chỉ dùng được ở Processor cấp 2 (như world processor)");
            Object[] parts = toks.toArray(new Object[0]);
            if(toks.get(0).equals("jump") && toks.size() >= 2){
                String tgt = toks.get(1);
                if(labels.containsKey(tgt)) parts[1] = labels.get(tgt);
                else if(!tgt.matches("\\d+"))
                    throw new JProError(a.lineNos.get(i), 1, "Nhãn '" + tgt + "' không tồn tại trong khối asm này (khai báo bằng '" + tgt + ":')");
            }
            emit(parts);
        }
    }

    // ---------- điều kiện (nhảy ngắn mạch) ----------
    /** Nhảy tới target khi giá trị của e bằng 'when'; ngược lại chạy tiếp. */
    private void genCond(Expr e, Label target, boolean when){
        if(e instanceof Lit){
            String x = ((Lit)e).text;
            boolean v = x.equals("true");
            if(v == when) jumpAlways(target);
            return;
        }
        Double f = fold(e);
        if(f != null){
            if((f != 0) == when) jumpAlways(target);
            return;
        }
        if(e instanceof Unary && ((Unary)e).op.equals("!")){
            genCond(((Unary)e).e, target, !when);
            return;
        }
        if(e instanceof Binary){
            Binary b = (Binary)e;
            if(b.op.equals("&&")){
                if(!when){
                    genCond(b.l, target, false);
                    genCond(b.r, target, false);
                }else{
                    Label skip = new Label();
                    genCond(b.l, skip, false);
                    genCond(b.r, target, true);
                    place(skip);
                }
                return;
            }
            if(b.op.equals("||")){
                if(when){
                    genCond(b.l, target, true);
                    genCond(b.r, target, true);
                }else{
                    Label yes = new Label();
                    genCond(b.l, yes, true);
                    genCond(b.r, target, false);
                    place(yes);
                }
                return;
            }
            String cmp = CMP.get(b.op);
            if(cmp != null){
                String l = genExpr(b.l, null);
                String r = genExpr(b.r, null);
                emit("jump", target, when ? cmp : NEG.get(cmp), l, r);
                return;
            }
        }
        String v = genExpr(e, null);
        emit("jump", target, when ? "notEqual" : "equal", v, "false");
    }

    // ---------- biểu thức ----------
    private Double fold(Expr e){
        if(e instanceof Num) return ((Num)e).v;
        if(e instanceof Name){
            Object c = consts.get(((Name)e).name);
            return c instanceof Double ? (Double)c : null;
        }
        if(e instanceof Unary){
            Unary u = (Unary)e;
            Double x = fold(u.e);
            if(x == null) return null;
            if(u.op.equals("-")) return -x;
            if(u.op.equals("+")) return x;
            return null;
        }
        if(e instanceof Binary){
            Binary b = (Binary)e;
            Double l = fold(b.l), r = fold(b.r);
            if(l == null || r == null) return null;
            double res;
            switch(b.op){
                case "+": res = l + r; break;
                case "-": res = l - r; break;
                case "*": res = l * r; break;
                case "/": if(r == 0) return null; res = l / r; break;
                case "%": if(r == 0) return null; res = l % r; break;
                case "**": res = Math.pow(l, r); break;
                default: return null;
            }
            if(Double.isNaN(res) || Double.isInfinite(res)) return null;
            return res;
        }
        return null;
    }

    /** Lưu operand vào dest nếu có. */
    private String fin(String operand, String dest){
        if(dest == null) return operand;
        if(!operand.equals(dest)) emit("set", dest, operand);
        return dest;
    }

    private String genExpr(Expr e, String dest){
        if(e instanceof Num) return fin(fmt(((Num)e).v), dest);
        if(e instanceof Str) return fin("\"" + ((Str)e).v + "\"", dest);
        if(e instanceof Lit) return fin(((Lit)e).text, dest);
        if(e instanceof At) return fin("@" + ((At)e).name, dest);
        if(e instanceof Col) return fin(((Col)e).text, dest);
        if(e instanceof Name) return fin(varOf(((Name)e).name, e), dest);
        if(e instanceof Unary) return genUnary((Unary)e, dest);
        if(e instanceof Binary) return genBinary((Binary)e, dest);
        if(e instanceof Ternary){
            Ternary t = (Ternary)e;
            String r = dest != null ? dest : temp();
            Label els = new Label(), end = new Label();
            int mark = tmp;
            genCond(t.c, els, false);
            tmp = mark;
            genExpr(t.a, r);
            jumpAlways(end);
            place(els);
            tmp = mark;
            genExpr(t.b, r);
            place(end);
            return r;
        }
        if(e instanceof Index){
            Index ix = (Index)e;
            String b = genExpr(ix.base, null);
            String i = genExpr(ix.idx, null);
            String r = dest != null ? dest : temp();
            emit("read", r, b, i);
            return r;
        }
        if(e instanceof Member){
            Member m = (Member)e;
            String o = genExpr(m.base, null);
            String r = dest != null ? dest : temp();
            emit("sensor", r, o, m.prop);
            return r;
        }
        if(e instanceof Call) return genCall((Call)e, dest, false);
        throw err(e, "Biểu thức không được hỗ trợ");
    }

    private String genUnary(Unary u, String dest){
        Double f = fold(u);
        if(f != null) return fin(fmt(f), dest);
        if(u.op.equals("+")) return genExpr(u.e, dest);
        String x = genExpr(u.e, null);
        String r = dest != null ? dest : temp();
        switch(u.op){
            case "-": emit("op", "sub", r, "0", x); break;
            case "!": emit("op", "equal", r, x, "false"); break;
            default: emit("op", "not", r, x, "0"); break;
        }
        return r;
    }

    private String genBinary(Binary b, String dest){
        Double f = fold(b);
        if(f != null) return fin(fmt(f), dest);
        String l = genExpr(b.l, null);
        String r = genExpr(b.r, null);
        String t = dest != null ? dest : temp();
        if(b.op.equals("||")){
            emit("op", "or", t, l, r);
            emit("op", "notEqual", t, t, "0");
        }else emit("op", BIN.get(b.op), t, l, r);
        return t;
    }

    // ---------- gọi hàm ----------
    private String genCall(Call c, String dest, boolean stmt){
        if(c.ns == null){
            Fn f = fns.get(c.name);
            if(f != null) return callFn(f, c, dest, stmt);
            if(c.name.equals("print")){
                if(!stmt) throw err(c, "'print' không trả về giá trị");
                if(c.args.isEmpty()) throw err(c, "print cần ít nhất một giá trị: print(\"x = \", x)");
                for(Expr a : c.args) emit("print", genExpr(a, null));
                return null;
            }
        }
        if("unit".equals(c.ns) && c.name.equals("bind") && c.args.size() == 2) return genBindSquad(c, dest, stmt);
        Builtin b = Builtins.find(c.ns, c.name);
        if(b == null){
            if(c.ns != null && !Builtins.isNamespace(c.ns))
                throw err(c, "Không có nhóm hàm '" + c.ns + "'. Các nhóm: draw, unit, block" + (level >= 2 ? ", world, cutscene, Draw, Drawf, Lines, Fill" : " (world, cutscene, Draw, Drawf, Lines, Fill chỉ có ở cấp 2)"));
            String sg = Builtins.suggest(c.ns, c.name);
            throw err(c, "Hàm '" + c.full() + "' không tồn tại" + (sg != null ? ". Ý bạn là '" + sg + "'?" : ""));
        }
        return builtin(b, c, dest, stmt);
    }

    private String builtin(Builtin b, Call c, String dest, boolean stmt){
        if(b.world) needWorld(c.line, c.col, b.key);
        int n = c.args.size();
        if(n < b.minArgs || n > b.maxArgs)
            throw err(c, "Sai số lượng tham số. Cách dùng: " + b.usage());
        if(!stmt && !b.hasResult)
            throw err(c, "'" + b.key + "' không trả về giá trị nên không dùng trong biểu thức được");
        String[] ops = new String[Math.max(b.maxArgs, 1)];
        for(int i = 0; i < n; i++){
            Expr a = c.args.get(i);
            switch(b.kinds[i]){
                case Builtins.SYMBOL: ops[i] = symbol(a, b, i); break;
                case Builtins.OUT: ops[i] = outVar(a, b, i); break;
                default: ops[i] = genExpr(a, null);
            }
        }
        String res = null;
        if(b.hasResult) res = dest != null ? dest : (stmt ? "__void" : temp());
        Object[] parts = new Object[b.parts.length];
        for(int i = 0; i < parts.length; i++){
            Part p = b.parts[i];
            if(p.type == Builtins.P_LIT) parts[i] = p.lit;
            else if(p.type == Builtins.P_RES) parts[i] = res;
            else parts[i] = p.arg < n ? ops[p.arg] : p.def;
        }
        emit(parts);
        return res;
    }

    /**
     * unit.bind(loại, số_lượng): giữ một "nhóm" tối đa số_lượng lính cùng loại.
     * Mỗi lần gọi chuyển @unit sang lính kế tiếp của nhóm. Lính đang bị processor khác / người chơi / lệnh RTS
     * điều khiển được bỏ qua. Lính của nhóm được đánh dấu bằng flag riêng của (processor, vị trí gọi) nên
     * hai processor, hoặc hai chỗ gọi, không giành nhau.
     * Số lính trong nhóm được đếm lại sau mỗi vòng quay hết danh sách lính của loại đó (lính chết/bị cướp thì được bù).
     * Kết quả (nếu dùng như biểu thức): true nếu bind được một lính.
     */
    private String genBindSquad(Call c, String dest, boolean stmt){
        String type = genExpr(c.args.get(0), null);
        String count = genExpr(c.args.get(1), null);
        String res = stmt ? null : (dest != null ? dest : temp());
        int k = bindSites++;
        String p = "__bind" + k + "_";
        String tag = p + "tag", first = p + "first", found = p + "found", num = p + "n", dead = p + "d",
            ctl = p + "c", flag = p + "f", own = p + "o", tries = p + "t";
        Label retry = new Label(), none = new Label(), wrap = new Label(), noFirst = new Label(), classify = new Label(),
            free = new Label(), accept = new Label(), skip = new Label(), end = new Label();

        // nhãn riêng của nhóm = (x*1000 + y)*100 + số thứ tự chỗ gọi + 1
        emit("op", "mul", tag, "@thisx", "1000");
        emit("op", "add", tag, tag, "@thisy");
        emit("op", "mul", tag, tag, "100");
        emit("op", "add", tag, tag, Integer.toString(k % 99 + 1));
        emit("set", tries, "0");
        place(retry);
        emit("ubind", type);
        emit("jump", none, "equal", "@unit", "null");
        // phát hiện đã quay hết một vòng danh sách lính
        emit("jump", wrap, "equal", "@unit", first);
        emit("jump", noFirst, "equal", first, "null");
        emit("sensor", dead, first, "@dead");
        emit("jump", wrap, "equal", dead, "1");
        emit("jump", classify, "always", "0", "0");
        place(noFirst);
        emit("set", first, "@unit");
        emit("set", found, "0");
        emit("jump", classify, "always", "0", "0");
        place(wrap);
        emit("set", num, found);
        emit("set", found, "0");
        emit("set", first, "@unit");
        // phân loại lính
        place(classify);
        emit("sensor", ctl, "@unit", "@controlled");
        emit("sensor", flag, "@unit", "@flag");
        emit("jump", free, "equal", ctl, "0");
        emit("sensor", own, "@unit", "@controller");
        emit("jump", skip, "notEqual", own, "@this");
        emit("jump", skip, "notEqual", flag, tag);
        emit("jump", accept, "always", "0", "0");
        place(free);
        emit("jump", accept, "equal", flag, tag);
        emit("jump", skip, "greaterThanEq", num, count);
        emit("op", "add", num, num, "1");
        emit("ucontrol", "flag", tag, "0", "0", "0", "0");
        emit("ucontrol", "idle", "0", "0", "0", "0", "0");
        place(accept);
        emit("op", "add", found, found, "1");
        if(res != null) emit("set", res, "true");
        jumpAlways(end);
        place(skip);
        emit("op", "add", tries, tries, "1");
        emit("jump", retry, "lessThan", tries, "64");
        place(none);
        emit("ubind", "null");
        if(res != null) emit("set", res, "false");
        place(end);
        return res;
    }

    private String symbol(Expr a, Builtin b, int i){
        if(a instanceof Name) return ((Name)a).name;
        if(a instanceof Str) return ((Str)a).v;
        if(a instanceof Lit) return ((Lit)a).text;
        if(a instanceof At) return "@" + ((At)a).name;
        throw err(a, "Tham số thứ " + (i + 1) + " của " + b.key + " phải là một từ khóa (vd: enemy, any, distance), không phải biểu thức");
    }

    private String outVar(Expr a, Builtin b, int i){
        if(a instanceof Name) return targetOf(((Name)a).name, a.line, a.col);
        throw err(a, "Tham số thứ " + (i + 1) + " của " + b.key + " phải là TÊN BIẾN để nhận kết quả");
    }

    private String callFn(Fn f, Call c, String dest, boolean stmt){
        int n = f.params.size();
        if(c.args.size() != n){
            StringBuilder sb = new StringBuilder(f.name).append('(');
            for(int i = 0; i < n; i++) sb.append(i > 0 ? ", " : "").append(f.params.get(i));
            throw err(c, "Hàm '" + f.name + "' cần " + n + " tham số: " + sb.append(')'));
        }
        String[] ops = new String[n];
        for(int i = 0; i < n; i++) ops[i] = genExpr(c.args.get(i), null);
        for(int i = 0; i < n; i++){
            String pn = f.name + "__" + f.params.get(i);
            if(!ops[i].equals(pn)) emit("set", pn, ops[i]);
        }
        emit("op", "add", f.name + "__ra", "@counter", "1");
        jumpAlways(f.start);
        if(stmt) return null;
        if(!f.returnsValue) throw err(c, "Hàm '" + f.name + "' không trả về giá trị (thiếu 'return giá_trị;')");
        String t = dest != null ? dest : temp();
        emit("set", t, f.name + "__ret");
        return t;
    }

    // ---------- xuất mlog ----------
    String render(){
        StringBuilder sb = new StringBuilder();
        for(Object[] ins : code){
            for(int i = 0; i < ins.length; i++){
                if(i > 0) sb.append(' ');
                Object p = ins[i];
                if(p instanceof Label){
                    int idx = ((Label)p).index;
                    if(idx < 0) throw new IllegalStateException("nhãn chưa được đặt");
                    sb.append(idx);
                }else sb.append(p);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}