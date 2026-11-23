package jpro.lang;

import java.util.*;

import jpro.lang.Lexer.Tok;
import jpro.lang.Node.*;

/**
 * Phân tích cú pháp: danh sách token -> cây cú pháp.
 *
 * Cú pháp theo Java:
 *   int x = 5;   final double G = 9.8;   var u = @unit;
 *   int cong(int a, int b){ return a + b; }       void chay(){ ... }
 *   if(a < b){ ... }else{ ... }     while(c){ ... }     for(int i = 0; i < 10; i++){ ... }
 *   switch(x){ case 1, 2 -> { ... }  default -> { ... } }   hoặc  case 1: ... break;
 * Các cách viết cũ (let, const, fn, for i in a..b, case 1 { }) vẫn được chấp nhận.
 */
final class Parser{
    /** Bắt buộc ';' cuối câu lệnh như Java. Đặt false để cho phép xuống dòng thay ';' như bản cũ. */
    static final boolean REQUIRE_SEMICOLON = true;

    static final Set<String> KEYWORDS = Syntax.KEYWORDS;

    private static final Set<String> ASSIGN_OPS = new HashSet<>(Arrays.asList(
        "=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", "&=", "|=", "^="
    ));

    private final List<Tok> t;
    private int p = 0;
    /** Các nhóm case kiểu "case x:" -> có kết thúc bằng break/return/... hay không. */
    private final Map<Node.Block, Boolean> classicEnds = new IdentityHashMap<>();

    private Parser(List<Tok> tokens){
        this.t = tokens;
    }

    static List<Stmt> parse(String src){
        Parser ps = new Parser(Lexer.lex(src));
        List<Stmt> out = new ArrayList<>();
        while(ps.peek().type != Tok.EOF) out.add(ps.stmt(true));
        return out;
    }

    // ---------- tiện ích ----------
    private Tok peek(){ return t.get(p); }
    private Tok peek(int k){ return t.get(Math.min(p + k, t.size() - 1)); }
    private Tok prev(){ return t.get(Math.max(0, p - 1)); }

    private Tok next(){
        Tok x = t.get(p);
        if(p < t.size() - 1) p++;
        return x;
    }

    private boolean at(String s){ return peek().is(s); }

    private boolean accept(String s){
        if(at(s)){
            next();
            return true;
        }
        return false;
    }

    private JProError err(Tok k, String msg){
        return new JProError(k.line, k.col, msg);
    }

    private Tok expect(String s){
        if(!at(s)) throw err(peek(), "Cần '" + s + "' nhưng gặp " + peek());
        return next();
    }

    private String ident(String what){
        Tok k = peek();
        if(k.type != Tok.IDENT) throw err(k, "Cần " + what + " nhưng gặp " + k);
        if(KEYWORDS.contains(k.text)) throw err(k, "'" + k.text + "' là từ khóa, không dùng làm " + what + " được");
        next();
        return k.text;
    }

    private <T extends Stmt> T mark(T s, Tok k){
        s.line = k.line;
        s.col = k.col;
        return s;
    }

    private <T extends Expr> T mark(T e, Tok k){
        e.line = k.line;
        e.col = k.col;
        return e;
    }

    /** Kết thúc câu lệnh: ';' như Java. */
    private void endStmt(){
        if(accept(";")) return;
        Tok k = peek();
        if(!REQUIRE_SEMICOLON && (k.type == Tok.EOF || k.is("}") || k.nl)) return;
        Tok last = prev();
        throw err(last, "Thiếu ';' ở cuối câu lệnh (sau " + last + ")");
    }

    // ---------- nhận dạng khai báo kiểu Java ----------
    /** Token có thể là tên kiểu: int, String, Unit, ... (không tính các từ khóa điều khiển). */
    private static boolean typeName(Tok a){
        return a.type == Tok.IDENT
            && (Syntax.PRIMITIVE.contains(a.text) || a.text.equals("void") || !KEYWORDS.contains(a.text));
    }

    private static boolean varName(Tok b){
        return b.type == Tok.IDENT && !KEYWORDS.contains(b.text);
    }

    /** "Kiểu tên" — hai tên liền nhau chỉ có thể là khai báo biến hoặc hàm. */
    private boolean typedHead(int k){
        return typeName(peek(k)) && varName(peek(k + 1));
    }

    private boolean atModifier(){
        return peek().type == Tok.IDENT && Syntax.MODIFIERS.contains(peek().text);
    }

    // ---------- câu lệnh ----------
    private Node.Block block(){
        Tok open = expect("{");
        List<Stmt> body = new ArrayList<>();
        while(!at("}")){
            if(peek().type == Tok.EOF) throw err(open, "Dấu '{' này chưa có '}' đóng tương ứng");
            body.add(stmt(false));
        }
        expect("}");
        return mark(new Node.Block(body), open);
    }

    /** Thân của if / else / while / for: khối { } hoặc một câu lệnh đơn như Java. */
    private Node.Block body(){
        if(at("{")) return block();
        Tok k = peek();
        if(k.type == Tok.EOF) throw err(k, "Thiếu thân câu lệnh");
        List<Stmt> one = new ArrayList<>();
        one.add(stmt(false));
        return mark(new Node.Block(one), k);
    }

    private Stmt stmt(boolean top){
        Tok k = peek();
        if(k.type == Tok.IDENT){
            if(Syntax.MODIFIERS.contains(k.text)) return modified(top);
            switch(k.text){
                case "export": if(!top) throw err(k, "export chỉ được khai báo ở ngoài cùng"); return exportDecl(false);
                case "exportFunc": if(!top) throw err(k, "exportFunc chỉ được khai báo ở ngoài cùng"); return exportDecl(true);
                case "import": if(!top) throw err(k, "import chỉ được khai báo ở ngoài cùng"); return importDecl(false);
                case "importFunc": if(!top) throw err(k, "importFunc chỉ được khai báo ở ngoài cùng"); return importDecl(true);
                case "let": case "var": return let(false);
                case "const": return let(true);
                case "fn":
                    if(!top) throw err(k, "Hàm chỉ được khai báo ở ngoài cùng (không lồng trong khối khác)");
                    return fn();
                case "if": return ifStmt();
                case "while": {
                    next();
                    Expr c = expr();
                    return mark(new While(c, body()), k);
                }
                case "loop": {
                    next();
                    return mark(new Loop(block()), k);
                }
                case "once": {
                    next();
                    return mark(new Once(block()), k);
                }
                case "for": return forStmt();
                case "switch": return switchStmt();
                case "break": next(); endStmt(); return mark(new Break(), k);
                case "continue": next(); endStmt(); return mark(new Continue(), k);
                case "return": {
                    next();
                    Expr v = null;
                    Tok n = peek();
                    if(!(n.is(";") || n.is("}") || n.type == Tok.EOF || (!REQUIRE_SEMICOLON && n.nl))) v = expr();
                    endStmt();
                    return mark(new Return(v), k);
                }
                case "end": next(); endStmt(); return mark(new End(false), k);
                case "stop": next(); endStmt(); return mark(new End(true), k);
                case "asm": return asm();
                case "else": throw err(k, "'else' không có 'if' đi kèm");
                case "case": case "default": throw err(k, "'" + k.text + "' chỉ dùng được bên trong switch { ... }");
                default: break;
            }
            if(typedHead(0)) return typed(top, false);
        }
        if(k.is(";")){
            next();
            return mark(new Node.Block(new ArrayList<Stmt>()), k);
        }
        if(k.is("{")) return block();
        return simple(true);
    }

    /** export tên; hoặc exportFunc tên; */
    private Stmt exportDecl(boolean function){
        Tok k = next();
        String name = ident(function ? "tên hàm export" : "tên biến export");
        endStmt();
        return mark(new ExportDecl(name, function), k);
    }

    /** import processorN.tên; hoặc importFunc processorN.tên; */
    private Stmt importDecl(boolean function){
        Tok k = next();
        String processor = ident("alias processor (processor1, processor2, ...)");
        if(!processor.matches("processor[1-9][0-9]*"))
            throw err(k, "Alias import phải có dạng processor1, processor2, ...");
        expect(".");
        String name = ident(function ? "tên hàm import" : "tên biến import");
        endStmt();
        return mark(new ImportDecl(processor, name, function), k);
    }

    /** Bắt đầu bằng final / static / public / private / protected. */
    private Stmt modified(boolean top){
        Tok first = peek();
        boolean fin = false;
        while(atModifier()){
            if(at("final")) fin = true;
            next();
        }
        Tok k = peek();
        if(k.is("let") || k.is("var")){
            Stmt s = let(false);
            return markFinal(s, fin, k);
        }
        if(k.is("const")) return let(true);
        if(k.is("fn")){
            if(!top) throw err(k, "Hàm chỉ được khai báo ở ngoài cùng (không lồng trong khối khác)");
            return fn();
        }
        if(typedHead(0)) return typed(top, fin);
        throw err(k, "Sau '" + first.text + "' phải là khai báo biến hoặc hàm, nhưng gặp " + k);
    }

    private Stmt markFinal(Stmt s, boolean fin, Tok at){
        if(fin && s instanceof Let){
            Let l = (Let)s;
            if(l.init == null) throw err(at, "final cần có giá trị: final var " + l.name + " = ...;");
            l.isFinal = true;
        }
        return s;
    }

    /** Khai báo kiểu Java: "int x = 5;" hoặc hàm "int cong(int a, int b){ ... }". */
    private Stmt typed(boolean top, boolean fin){
        Tok type = next();
        Tok nameTok = peek();
        String name = ident("tên");
        if(at("(")){
            if(!top) throw err(type, "Hàm chỉ được khai báo ở ngoài cùng (không lồng trong khối khác)");
            return fnRest(type, type, name);
        }
        if(type.is("void")) throw err(type, "'void' chỉ dùng làm kiểu trả về của hàm");
        Expr init = null;
        if(accept("=")) init = expr();
        else if(fin) throw err(nameTok, "final cần có giá trị: final " + type.text + " " + name + " = ...;");
        endStmt();
        Let l = mark(new Let(name, init, false), type);
        l.isFinal = fin;
        return l;
    }

    /** let / var / const (cách viết cũ, và "var" của Java). */
    private Stmt let(boolean isConst){
        Tok k = next();
        String name = ident("tên biến");
        Expr init = null;
        if(accept("=")) init = expr();
        else if(isConst) throw err(k, "const cần có giá trị: const " + name + " = ...;");
        endStmt();
        return mark(new Let(name, init, isConst), k);
    }

    /** fn tên(a, b) { ... } — cách viết cũ. */
    private Stmt fn(){
        Tok k = next();
        String name = ident("tên hàm");
        return fnRest(null, k, name);
    }

    private Stmt fnRest(Tok retType, Tok k, String name){
        expect("(");
        List<String> params = new ArrayList<>();
        if(!at(")")){
            do{
                while(at("final")) next();
                // "kiểu tên" hoặc chỉ "tên"
                if(typedHead(0)) next();
                String pn = ident("tên tham số");
                if(params.contains(pn)) throw err(peek(), "Tham số '" + pn + "' bị trùng");
                params.add(pn);
            }while(accept(","));
        }
        expect(")");
        FnDecl d = mark(new FnDecl(name, params, block()), k);
        if(retType != null) checkReturns(d, retType);
        return d;
    }

    /** Kiểm tra return khớp kiểu trả về: void thì không trả giá trị, kiểu khác thì phải trả. */
    private void checkReturns(final FnDecl d, final Tok retType){
        final boolean isVoid = retType.is("void");
        final boolean[] found = {false};
        Node.walkStmt(d.body, new Node.StmtVisitor(){
            @Override
            public void visit(Stmt s){
                if(!(s instanceof Return)) return;
                Return r = (Return)s;
                if(isVoid && r.value != null)
                    throw new JProError(s.line, s.col, "Hàm void '" + d.name + "' không được trả về giá trị");
                if(!isVoid && r.value == null)
                    throw new JProError(s.line, s.col, "Hàm '" + d.name + "' kiểu " + retType.text + " phải trả về giá trị: return giá_trị;");
                if(r.value != null) found[0] = true;
            }
        });
        if(!isVoid && !found[0])
            throw new JProError(d.line, d.col, "Hàm '" + d.name + "' kiểu " + retType.text + " thiếu câu lệnh return giá_trị;");
    }

    private Stmt ifStmt(){
        Tok k = next();
        Expr c = expr();
        Node.Block th = body();
        Stmt el = null;
        if(at("else")){
            next();
            el = at("if") ? ifStmt() : body();
        }
        return mark(new If(c, th, el), k);
    }

    private Stmt forStmt(){
        Tok k = next();
        boolean paren = accept("(");
        // cách viết cũ: for i in 0..10 [step 2]
        if(peek().type == Tok.IDENT && peek(1).is("in")){
            String var = ident("tên biến lặp");
            expect("in");
            Expr from = expr();
            expect("..");
            Expr to = expr();
            Expr step = null;
            if(at("step")){
                next();
                step = expr();
            }
            if(paren) expect(")");
            return mark(new ForIn(var, from, to, step, body()), k);
        }
        if(!paren) throw err(peek(), "Vòng for cần dấu ngoặc: for (int i = 0; i < 10; i++) { ... }");
        Stmt init = null;
        if(!at(";")){
            if(at("let") || at("var")) init = letNoEnd();
            else if(atModifier() || typedHead(0)) init = declNoEnd();
            else init = simple(false);
        }
        expect(";");
        Expr cond = at(";") ? null : expr();
        expect(";");
        Stmt step = at(")") ? null : simple(false);
        expect(")");
        return mark(new ForC(init, cond, step, body()), k);
    }

    private Stmt letNoEnd(){
        Tok k = next();
        String name = ident("tên biến");
        Expr init = null;
        if(accept("=")) init = expr();
        return mark(new Let(name, init, false), k);
    }

    /** "int i = 0" trong for(...), không có ';' ở cuối. */
    private Stmt declNoEnd(){
        boolean fin = false;
        while(atModifier()){
            if(at("final")) fin = true;
            next();
        }
        if(at("let") || at("var")) return letNoEnd();
        if(!typedHead(0)) throw err(peek(), "Cần khai báo biến, ví dụ int i = 0, nhưng gặp " + peek());
        Tok type = next();
        String name = ident("tên biến");
        Expr init = null;
        if(accept("=")) init = expr();
        Let l = mark(new Let(name, init, false), type);
        l.isFinal = fin && init != null;
        return l;
    }

    // ---------- switch ----------
    private Stmt switchStmt(){
        Tok k = next();
        Expr subject = expr();
        expect("{");
        List<Case> cases = new ArrayList<>();
        Node.Block def = null;
        List<Expr> pending = new ArrayList<>(); // "case 1: case 2:" rỗng -> gộp vào case kế tiếp
        List<Node.Block> order = new ArrayList<>();
        List<Tok> orderTok = new ArrayList<>();
        while(!at("}")){
            Tok c = peek();
            if(c.type == Tok.EOF) throw err(k, "switch này chưa có '}' đóng tương ứng");
            if(c.is("case")){
                next();
                List<Expr> vals = new ArrayList<>(pending);
                pending.clear();
                do{ vals.add(expr()); }while(accept(","));
                Node.Block b;
                if(accept("->")) b = arrowBody();
                else if(accept(":")){
                    b = classicBody();
                    if(b == null){
                        pending.addAll(vals);
                        continue;
                    }
                }else if(at("{")) b = block();
                else throw err(peek(), "Sau 'case ...' cần '->', ':' hoặc '{', nhưng gặp " + peek());
                cases.add(new Case(vals, b));
                order.add(b);
                orderTok.add(c);
            }else if(c.is("default")){
                next();
                if(def != null) throw err(c, "switch chỉ có một 'default'");
                pending.clear();
                if(accept("->")) def = arrowBody();
                else if(accept(":")){
                    def = classicBody();
                    if(def == null) def = mark(new Node.Block(new ArrayList<Stmt>()), c);
                }else if(at("{")) def = block();
                else throw err(peek(), "Sau 'default' cần '->', ':' hoặc '{', nhưng gặp " + peek());
                order.add(def);
                orderTok.add(c);
            }else{
                throw err(c, "Trong switch chỉ được có 'case ...' hoặc 'default', gặp " + c);
            }
        }
        Tok close = expect("}");
        if(!pending.isEmpty()) cases.add(new Case(pending, mark(new Node.Block(new ArrayList<Stmt>()), close)));
        // Chỉ nhóm cuối mới được "rơi" xuống hết switch; các nhóm trước phải có break/return/...
        for(int i = 0; i < order.size() - 1; i++){
            Boolean ends = classicEnds.get(order.get(i));
            if(ends != null && !ends)
                throw err(orderTok.get(i), "JPro không hỗ trợ rơi xuống case kế tiếp (fall-through): hãy thêm 'break;' ở cuối nhóm này");
        }
        return mark(new Switch(subject, cases, def), k);
    }

    /** Thân của "case x ->" : một khối hoặc một câu lệnh. */
    private Node.Block arrowBody(){
        return body();
    }

    /** Thân của "case x:" — các câu lệnh đến case/default/} kế tiếp. Trả null nếu rỗng. */
    private Node.Block classicBody(){
        Tok start = peek();
        List<Stmt> list = new ArrayList<>();
        while(!(at("case") || at("default") || at("}"))){
            if(peek().type == Tok.EOF) throw err(start, "switch này chưa có '}' đóng tương ứng");
            list.add(stmt(false));
        }
        if(list.isEmpty()) return null;
        Stmt last = list.get(list.size() - 1);
        boolean ends = last instanceof Break || last instanceof Return || last instanceof Continue || last instanceof End;
        if(last instanceof Break) list.remove(list.size() - 1);
        Node.Block b = mark(new Node.Block(list), start);
        classicEnds.put(b, ends);
        return b;
    }

    private Stmt asm(){
        Tok k = next();
        expect("{");
        Tok raw = next();
        if(raw.type != Tok.RAW) throw err(k, "Sau 'asm' phải là khối { ... }");
        expect("}");
        List<String> lines = new ArrayList<>();
        List<Integer> nos = new ArrayList<>();
        String[] parts = raw.text.split("\n", -1);
        for(int i = 0; i < parts.length; i++){
            String ln = parts[i].trim();
            if(ln.isEmpty() || ln.startsWith("//") || ln.startsWith("#")) continue;
            if(ln.endsWith(";")) ln = ln.substring(0, ln.length() - 1).trim();
            lines.add(ln);
            nos.add(raw.line + i);
        }
        return mark(new Asm(lines, nos), k);
    }

    /** Gán, tăng/giảm, hoặc gọi hàm. */
    private Stmt simple(boolean needEnd){
        Tok k = peek();
        Expr e = expr();
        Tok o = peek();
        Stmt res;
        if(o.type == Tok.OP && ASSIGN_OPS.contains(o.text)){
            checkLvalue(e, o);
            next();
            Expr v = expr();
            res = new Assign(e, o.text, v);
        }else if(o.is("++") || o.is("--")){
            checkLvalue(e, o);
            next();
            Num one = new Num(1);
            one.line = o.line;
            one.col = o.col;
            res = new Assign(e, o.is("++") ? "+=" : "-=", one);
        }else{
            if(!(e instanceof Call))
                throw err(k, "Câu lệnh này không làm gì cả. Một câu lệnh phải là phép gán (x = ...;), x++; hoặc lời gọi hàm.");
            res = new ExprStmt((Call)e);
        }
        res.line = k.line;
        res.col = k.col;
        if(needEnd) endStmt();
        return res;
    }

    private void checkLvalue(Expr e, Tok at){
        if(!(e instanceof Name || e instanceof Index || e instanceof Member))
            throw err(at, "Không thể gán giá trị cho biểu thức bên trái dấu '" + at.text + "'");
    }

    // ---------- biểu thức ----------
    Expr expr(){
        return ternary();
    }

    private Expr ternary(){
        Expr c = bin(1);
        if(at("?")){
            Tok q = next();
            Expr a = ternary();
            expect(":");
            Expr b = ternary();
            return mark(new Ternary(c, a, b), q);
        }
        return c;
    }

    private static int prec(Tok k){
        if(k.type != Tok.OP) return 0;
        switch(k.text){
            case "||": return 1;
            case "&&": return 2;
            case "|": return 3;
            case "^": return 4;
            case "&": return 5;
            case "==": case "!=": case "===": return 6;
            case "<": case "<=": case ">": case ">=": return 7;
            case "<<": case ">>": return 8;
            case "+": case "-": return 9;
            case "*": case "/": case "%": return 10;
            default: return 0;
        }
    }

    private Expr bin(int min){
        Expr left = unary();
        while(true){
            Tok o = peek();
            int pr = prec(o);
            if(pr == 0 || pr < min) break;
            // toán tử đầu dòng chỉ được nối tiếp biểu thức khi là && hoặc ||
            if(o.nl && !(o.is("&&") || o.is("||"))) break;
            next();
            Expr right = bin(pr + 1);
            left = mark(new Binary(o.text, left, right), o);
        }
        return left;
    }

    private Expr unary(){
        Tok k = peek();
        if(k.type == Tok.OP && (k.text.equals("-") || k.text.equals("!") || k.text.equals("~") || k.text.equals("+"))){
            next();
            return mark(new Unary(k.text, unary()), k);
        }
        return pow();
    }

    private Expr pow(){
        Expr base = postfix();
        Tok o = peek();
        if(o.is("**") && !o.nl){
            next();
            return mark(new Binary("**", base, unary()), o);
        }
        return base;
    }

    private List<Expr> args(){
        expect("(");
        List<Expr> a = new ArrayList<>();
        if(!at(")")){
            do{ a.add(expr()); }while(accept(","));
        }
        expect(")");
        return a;
    }

    private Expr postfix(){
        Expr e = primary();
        while(true){
            Tok k = peek();
            if(k.is("(") && !k.nl && e instanceof Name){
                e = mark(new Call(null, ((Name)e).name, args()), k);
            }else if(k.is("[") && !k.nl){
                next();
                Expr idx = expr();
                expect("]");
                e = mark(new Index(e, idx), k);
            }else if(k.is(".")){
                next();
                Tok n = next();
                String prop;
                if(n.type == Tok.IDENT) prop = "@" + n.text.replace('_', '-');
                else if(n.type == Tok.AT) prop = "@" + n.text;
                else throw err(n, "Sau dấu '.' cần tên thuộc tính, gặp " + n);
                if(peek().is("(") && !peek().nl && e instanceof Name && n.type == Tok.IDENT){
                    e = mark(new Call(((Name)e).name, n.text, args()), n);
                }else{
                    e = mark(new Member(e, prop), n);
                }
            }else break;
        }
        return e;
    }

    private Expr primary(){
        Tok k = next();
        switch(k.type){
            case Tok.NUM: return mark(new Num(k.num), k);
            case Tok.STR: return mark(new Str(k.text), k);
            case Tok.AT: return mark(new At(k.text), k);
            case Tok.COLOR: return mark(new Col(k.text), k);
            case Tok.IDENT:
                switch(k.text){
                    case "true": case "false": case "null": return mark(new Lit(k.text), k);
                    case "this": return mark(new At("this"), k);
                    default:
                        if(KEYWORDS.contains(k.text)) throw err(k, "'" + k.text + "' là từ khóa, không dùng trong biểu thức được");
                        return mark(new Name(k.text), k);
                }
            case Tok.OP:
                if(k.text.equals("(")){
                    Expr e = expr();
                    expect(")");
                    return e;
                }
                break;
            default: break;
        }
        throw err(k, "Không hiểu " + k + " ở vị trí này (cần một giá trị hoặc biểu thức)");
    }
}
