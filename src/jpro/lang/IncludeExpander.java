package jpro.lang;

import java.util.*;
import arc.Core;
import jpro.lang.Node.*;

/** Expands include directives from linked JPro processors and isolates imported names. */
final class IncludeExpander{
    private IncludeExpander(){}

    static List<Stmt> expand(List<Stmt> root, Map<String, String> sources){
        Map<String, List<Stmt>> libraries = new LinkedHashMap<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        Set<String> occupied = globalNames(root);

        // Resolve legacy include statements and Nasp namespace declarations before transforming references.
        Map<String, String> namespaces = new LinkedHashMap<>();
        Map<String, Include> declarations = new LinkedHashMap<>();
        for(Stmt s : root){
            String alias = includeAlias(s);
            if(alias == null) continue;
            if(!(s instanceof Include) && !(s instanceof Let && "Nasp".equals(((Let)s).type)))
                continue;
            if(declarations.containsKey(alias)) continue;
            Include in = s instanceof Include ? (Include)s : new Include(alias);
            declarations.put(alias, in);
            String src=sources == null ? null : sources.get(alias);
            if(src == null) throw new JProError(s.line,s.col,message("jpro.include.missing", "No linked JPro processor found for alias '{0}'.", alias));
            List<Stmt> parsed;
            try{ parsed=Parser.parse(src); }
            catch(JProError e){ throw new JProError(s.line,s.col,message("jpro.include.syntax", "Included processor '{0}' has a syntax error: {1}", alias, e.msg)); }
            for(Stmt child:parsed) if(child instanceof Include || (child instanceof Let && "Nasp".equals(((Let)child).type)))
                throw new JProError(child.line,child.col,message("jpro.include.nested", "Nested includes are not supported yet. Include processors directly from the current processor."));
            rejectNestedAliasCalls(parsed);
            Set<String> names=globalNames(parsed);
            String prefix=uniquePrefix("jproInclude"+aliasNumber(alias)+"_",occupied,names);
            prefixes.put(alias,prefix);
            if(s instanceof Let) namespaces.put(((Let)s).name,prefix);
            occupied.addAll(prefixed(names,prefix));
            libraries.put(alias,parsed);
        }
        // Namespace references are compile-time aliases; they do not produce runtime variables.
        Map<String, String> rewrites = new LinkedHashMap<>(prefixes);
        rewrites.putAll(namespaces);

        List<Stmt> expanded=new ArrayList<>();
        Set<String> emitted=new HashSet<>();
        for(Stmt s:root){
            String alias = includeAlias(s);
            if(alias != null && declarations.containsKey(alias)){
                if(!emitted.add(alias)) continue;
                List<Stmt> lib=libraries.get(alias);
                String prefix=prefixes.get(alias);
                expanded.addAll(transform(lib,prefix,globalNames(lib),rewrites,true));
            }else if(s instanceof Let && "Nasp".equals(((Let)s).type)){
                // Invalid or malformed Nasp declaration is diagnosed above / below, not emitted.
                throw new JProError(s.line,s.col,message("jpro.include.syntax", "Nasp must be initialized with include(processorN)."));
            }else{
                expanded.add(stmt(s,null,Collections.<String>emptySet(),rewrites,false,null));
            }
        }
        return expanded;
    }

    private static String includeAlias(Stmt s){
        if(s instanceof Include) return ((Include)s).alias;
        if(s instanceof Let && "Nasp".equals(((Let)s).type)){
            Expr init=((Let)s).init;
            if(init instanceof Call){
                Call c=(Call)init;
                if(c.ns==null && "include".equals(c.name) && c.args.size()==1 && c.args.get(0) instanceof Name){
                    String alias=((Name)c.args.get(0)).name;
                    if(alias.matches("processor[1-9][0-9]*")) return alias;
                }
            }
            return null;
        }
        return null;
    }

    private static void rejectNestedAliasCalls(List<Stmt> library){
        for(Stmt s:library){
            Node.walkStmt(s,new StmtVisitor(){
                @Override public void visit(Stmt node){
                    Node.stmtExprs(node,new Visitor(){
                        @Override public void visit(Expr e){
                            if(e instanceof Call && ((Call)e).ns != null && ((Call)e).ns.matches("processor[1-9][0-9]*"))
                                throw new JProError(e.line,e.col,message("jpro.include.crosscall", "Included processors cannot call another processor alias yet; include the dependency directly in the current processor."));
                        }
                    });
                }
            });
        }
    }


    private static String message(String key,String fallback,String... args){
        String text=Core.bundle != null && Core.bundle.has(key) ? Core.bundle.get(key) : fallback;
        for(int i=0;i<args.length;i++) text=text.replace("{"+i+"}",args[i]);
        return text;
    }

    private static int aliasNumber(String alias){
        try{return Integer.parseInt(alias.substring("processor".length()));}catch(Exception ignored){return 1;}
    }
    private static String uniquePrefix(String base,Set<String> occupied,Set<String> imported){
        String p=base; int n=2;
        while(collides(p,occupied,imported)) p=base+n++ + "_";
        return p;
    }
    private static boolean collides(String prefix,Set<String> occupied,Set<String> names){
        for(String n:names) if(occupied.contains(prefix+n)) return true;
        return false;
    }
    private static Set<String> prefixed(Set<String> names,String prefix){
        Set<String> out=new HashSet<>(); for(String n:names) out.add(prefix+n); return out;
    }

    /** Names declared in top-level execution scope, excluding function-local declarations. */
    private static Set<String> globalNames(List<Stmt> list){
        Set<String> names=new LinkedHashSet<>();
        for(Stmt s:list) collectGlobal(s,names);
        return names;
    }
    private static void collectGlobal(Stmt s,Set<String> names){
        if(s==null)return;
        if(s instanceof Let) names.add(((Let)s).name);
        else if(s instanceof FnDecl) names.add(((FnDecl)s).name);
        else if(s instanceof ForIn) names.add(((ForIn)s).var);
        else if(s instanceof ForC){ ForC f=(ForC)s; collectGlobal(f.init,names); collectGlobal(f.step,names); collectGlobal(f.body,names); }
        else if(s instanceof Block) for(Stmt c:((Block)s).body) collectGlobal(c,names);
        else if(s instanceof If){ If x=(If)s; collectGlobal(x.then,names); collectGlobal(x.els,names); }
        else if(s instanceof While) collectGlobal(((While)s).body,names);
        else if(s instanceof Loop) collectGlobal(((Loop)s).body,names);
        else if(s instanceof Once) collectGlobal(((Once)s).body,names);
        else if(s instanceof Switch){ Switch x=(Switch)s; for(Case c:x.cases) collectGlobal(c.body,names); collectGlobal(x.def,names); }
    }

    private static Set<String> localsOf(FnDecl fn){
        Set<String> locals=new HashSet<>(fn.params);
        Node.walkStmt(fn.body,new StmtVisitor(){
            @Override public void visit(Stmt s){
                if(s instanceof Let) locals.add(((Let)s).name);
                else if(s instanceof ForIn) locals.add(((ForIn)s).var);
            }
        });
        return locals;
    }
    private static List<Stmt> transform(List<Stmt> list,String prefix,Set<String> globals,Map<String,String> aliases,boolean imported){
        List<Stmt> out=new ArrayList<>(list.size());
        for(Stmt s:list) out.add(stmt(s,prefix,globals,aliases,imported,null));
        return out;
    }
    private static String rename(String n,String prefix,Set<String> globals){
        return prefix!=null && globals.contains(n) ? prefix+n : n;
    }

    private static Stmt stmt(Stmt s,String prefix,Set<String> globals,Map<String,String> aliases,boolean imported,Set<String> locals){
        if(s==null)return null;
        Stmt r;
        if(s instanceof Let){
            Let x=(Let)s; String n=imported && locals==null?rename(x.name,prefix,globals):x.name;
            Let y=new Let(x.type,n,expr(x.init,prefix,globals,aliases,imported,locals),x.isConst); y.isFinal=x.isFinal; r=y;
        }else if(s instanceof FnDecl){
            FnDecl x=(FnDecl)s; String n=imported?rename(x.name,prefix,globals):x.name;
            r=new FnDecl(n,new ArrayList<>(x.params),(Block)stmt(x.body,prefix,globals,aliases,imported,localsOf(x)));
        }else if(s instanceof Block){
            List<Stmt> b=new ArrayList<>(); for(Stmt c:((Block)s).body)b.add(stmt(c,prefix,globals,aliases,imported,locals)); r=new Block(b);
        }else if(s instanceof Assign){ Assign x=(Assign)s; r=new Assign(expr(x.target,prefix,globals,aliases,imported,locals),x.op,expr(x.value,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof ExprStmt) r=new ExprStmt((Call)expr(((ExprStmt)s).call,prefix,globals,aliases,imported,locals));
        else if(s instanceof If){ If x=(If)s; r=new If(expr(x.cond,prefix,globals,aliases,imported,locals),(Block)stmt(x.then,prefix,globals,aliases,imported,locals),stmt(x.els,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof While){ While x=(While)s; r=new While(expr(x.cond,prefix,globals,aliases,imported,locals),(Block)stmt(x.body,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof Loop) r=new Loop((Block)stmt(((Loop)s).body,prefix,globals,aliases,imported,locals));
        else if(s instanceof Once) r=new Once((Block)stmt(((Once)s).body,prefix,globals,aliases,imported,locals));
        else if(s instanceof ForC){ ForC x=(ForC)s; r=new ForC(stmt(x.init,prefix,globals,aliases,imported,locals),expr(x.cond,prefix,globals,aliases,imported,locals),stmt(x.step,prefix,globals,aliases,imported,locals),(Block)stmt(x.body,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof ForIn){ ForIn x=(ForIn)s; String v=imported && locals==null?rename(x.var,prefix,globals):x.var; r=new ForIn(v,expr(x.from,prefix,globals,aliases,imported,locals),expr(x.to,prefix,globals,aliases,imported,locals),expr(x.step,prefix,globals,aliases,imported,locals),(Block)stmt(x.body,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof Switch){ Switch x=(Switch)s; List<Case> cs=new ArrayList<>(); for(Case c:x.cases){List<Expr> vals=new ArrayList<>(); for(Expr v:c.values)vals.add(expr(v,prefix,globals,aliases,imported,locals)); cs.add(new Case(vals,(Block)stmt(c.body,prefix,globals,aliases,imported,locals)));} r=new Switch(expr(x.subject,prefix,globals,aliases,imported,locals),cs,(Block)stmt(x.def,prefix,globals,aliases,imported,locals)); }
        else if(s instanceof Return) r=new Return(expr(((Return)s).value,prefix,globals,aliases,imported,locals));
        else if(s instanceof Asm){ Asm x=(Asm)s; r=new Asm(new ArrayList<>(x.lines),new ArrayList<>(x.lineNos)); }
        else if(s instanceof Include) throw new JProError(s.line,s.col,message("jpro.include.unexpanded", "Include directives must be expanded before compilation."));
        else if(s instanceof Break) r=new Break(); else if(s instanceof Continue) r=new Continue(); else if(s instanceof End) r=new End(((End)s).stop); else r=s;
        r.line=s.line; r.col=s.col; return r;
    }

    private static Expr expr(Expr e,String prefix,Set<String> globals,Map<String,String> aliases,boolean imported,Set<String> locals){
        if(e==null)return null;
        Expr r;
        if(e instanceof Num) r=new Num(((Num)e).v); else if(e instanceof Str) r=new Str(((Str)e).v); else if(e instanceof Lit) r=new Lit(((Lit)e).text); else if(e instanceof At) r=new At(((At)e).name); else if(e instanceof Col) r=new Col(((Col)e).text);
        else if(e instanceof Name){String n=((Name)e).name; r=new Name(imported && (locals==null || !locals.contains(n))?rename(n,prefix,globals):n);}
        else if(e instanceof Unary){Unary x=(Unary)e; r=new Unary(x.op,expr(x.e,prefix,globals,aliases,imported,locals));}
        else if(e instanceof Binary){Binary x=(Binary)e; r=new Binary(x.op,expr(x.l,prefix,globals,aliases,imported,locals),expr(x.r,prefix,globals,aliases,imported,locals));}
        else if(e instanceof Ternary){Ternary x=(Ternary)e; r=new Ternary(expr(x.c,prefix,globals,aliases,imported,locals),expr(x.a,prefix,globals,aliases,imported,locals),expr(x.b,prefix,globals,aliases,imported,locals));}
        else if(e instanceof Index){Index x=(Index)e; r=new Index(expr(x.base,prefix,globals,aliases,imported,locals),expr(x.idx,prefix,globals,aliases,imported,locals));}
        else if(e instanceof Member){Member x=(Member)e;
            if(x.base instanceof Name && aliases.containsKey(((Name)x.base).name) && x.prop.startsWith("@"))
                r=new Name(aliases.get(((Name)x.base).name)+x.prop.substring(1).replace('-','_'));
            else r=new Member(expr(x.base,prefix,globals,aliases,imported,locals),x.prop);
        }
        else if(e instanceof Call){Call x=(Call)e; List<Expr> as=new ArrayList<>(); for(Expr a:x.args)as.add(expr(a,prefix,globals,aliases,imported,locals)); String n=x.name, ns=x.ns;
            if(imported && ns==null && globals.contains(n)) n=prefix+n;
            if(ns!=null && aliases.containsKey(ns)){n=aliases.get(ns)+n; ns=null;}
            r=new Call(ns,n,as);
        }else r=e;
        r.line=e.line; r.col=e.col; return r;
    }
}
