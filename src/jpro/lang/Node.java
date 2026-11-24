package jpro.lang;

import java.util.*;

/** Cây cú pháp. */
final class Node{
    private Node(){}

    interface Visitor{ void visit(Expr e); }
    interface StmtVisitor{ void visit(Stmt s); }

    abstract static class Expr{ int line, col; }
    abstract static class Stmt{ int line, col; }

    // ----- biểu thức -----
    static final class Num extends Expr{ final double v; Num(double v){ this.v = v; } }
    static final class Str extends Expr{ final String v; Str(String v){ this.v = v; } }
    /** true / false / null */
    static final class Lit extends Expr{ final String text; Lit(String t){ text = t; } }
    /** @copper, @unit, @"tên lạ" */
    static final class At extends Expr{ final String name; At(String n){ name = n; } }
    /** #rrggbb -> %rrggbb */
    static final class Col extends Expr{ final String text; Col(String t){ text = t; } }
    static final class Name extends Expr{ final String name; Name(String n){ name = n; } }
    static final class Unary extends Expr{
        final String op; final Expr e;
        Unary(String op, Expr e){ this.op = op; this.e = e; }
    }
    static final class Binary extends Expr{
        final String op; final Expr l, r;
        Binary(String op, Expr l, Expr r){ this.op = op; this.l = l; this.r = r; }
    }
    static final class Ternary extends Expr{
        final Expr c, a, b;
        Ternary(Expr c, Expr a, Expr b){ this.c = c; this.a = a; this.b = b; }
    }
    static final class Index extends Expr{
        final Expr base, idx;
        Index(Expr base, Expr idx){ this.base = base; this.idx = idx; }
    }
    static final class Member extends Expr{
        final Expr base; final String prop;
        Member(Expr base, String prop){ this.base = base; this.prop = prop; }
    }
    static final class Call extends Expr{
        final String ns, name; final List<Expr> args;
        Call(String ns, String name, List<Expr> args){ this.ns = ns; this.name = name; this.args = args; }
        String full(){ return ns == null ? name : ns + "." + name; }
    }

    // ----- câu lệnh -----
    static final class Include extends Stmt{
        final String alias;
        Include(String alias){ this.alias = alias; }
    }
    static final class Block extends Stmt{
        final List<Stmt> body;
        Block(List<Stmt> body){ this.body = body; }
    }
    static final class Let extends Stmt{
        final String name; final Expr init; final boolean isConst;
        String type;
        /** final kiểu Java: hằng nếu giá trị cố định được, ngược lại là biến chỉ gán một lần. */
        boolean isFinal;
        Let(String name, Expr init, boolean isConst){ this.name = name; this.init = init; this.isConst = isConst; }
        Let(String type, String name, Expr init, boolean isConst){ this(name, init, isConst); this.type = type; }
    }
    static final class FnDecl extends Stmt{
        final String name; final List<String> params; final Block body;
        FnDecl(String name, List<String> params, Block body){ this.name = name; this.params = params; this.body = body; }
    }
    static final class Assign extends Stmt{
        final Expr target; final String op; final Expr value;
        Assign(Expr target, String op, Expr value){ this.target = target; this.op = op; this.value = value; }
    }
    static final class ExprStmt extends Stmt{
        final Call call;
        ExprStmt(Call call){ this.call = call; }
    }
    static final class If extends Stmt{
        final Expr cond; final Block then; final Stmt els;
        If(Expr cond, Block then, Stmt els){ this.cond = cond; this.then = then; this.els = els; }
    }
    static final class While extends Stmt{
        final Expr cond; final Block body;
        While(Expr cond, Block body){ this.cond = cond; this.body = body; }
    }
    static final class Loop extends Stmt{
        final Block body;
        Loop(Block body){ this.body = body; }
    }
    static final class Once extends Stmt{
        final Block body;
        Once(Block body){ this.body = body; }
    }
    static final class ForC extends Stmt{
        final Stmt init, step; final Expr cond; final Block body;
        ForC(Stmt init, Expr cond, Stmt step, Block body){ this.init = init; this.cond = cond; this.step = step; this.body = body; }
    }
    static final class ForIn extends Stmt{
        final String var; final Expr from, to, step; final Block body;
        ForIn(String var, Expr from, Expr to, Expr step, Block body){
            this.var = var; this.from = from; this.to = to; this.step = step; this.body = body;
        }
    }
    static final class Case{
        final List<Expr> values; final Block body;
        Case(List<Expr> values, Block body){ this.values = values; this.body = body; }
    }
    static final class Switch extends Stmt{
        final Expr subject; final List<Case> cases; final Block def;
        Switch(Expr subject, List<Case> cases, Block def){ this.subject = subject; this.cases = cases; this.def = def; }
    }
    static final class Break extends Stmt{}
    static final class Continue extends Stmt{}
    static final class Return extends Stmt{
        final Expr value;
        Return(Expr value){ this.value = value; }
    }
    /** end = chạy lại từ đầu; stop = dừng hẳn. */
    static final class End extends Stmt{
        final boolean stop;
        End(boolean stop){ this.stop = stop; }
    }
    static final class Asm extends Stmt{
        final List<String> lines; final List<Integer> lineNos;
        Asm(List<String> lines, List<Integer> lineNos){ this.lines = lines; this.lineNos = lineNos; }
    }

    // ----- duyệt cây -----
    /** Thăm s và mọi câu lệnh lồng bên trong. */
    static void walkStmt(Stmt s, StmtVisitor v){
        if(s == null) return;
        v.visit(s);
        if(s instanceof Include){
            // Include directives are expanded before code generation.
        }else if(s instanceof Block){
            for(Stmt c : ((Block)s).body) walkStmt(c, v);
        }else if(s instanceof FnDecl){
            walkStmt(((FnDecl)s).body, v);
        }else if(s instanceof If){
            walkStmt(((If)s).then, v);
            walkStmt(((If)s).els, v);
        }else if(s instanceof While){
            walkStmt(((While)s).body, v);
        }else if(s instanceof Loop){
            walkStmt(((Loop)s).body, v);
        }else if(s instanceof Once){
            walkStmt(((Once)s).body, v);
        }else if(s instanceof ForC){
            ForC f = (ForC)s;
            walkStmt(f.init, v);
            walkStmt(f.step, v);
            walkStmt(f.body, v);
        }else if(s instanceof ForIn){
            walkStmt(((ForIn)s).body, v);
        }else if(s instanceof Switch){
            Switch w = (Switch)s;
            for(Case c : w.cases) walkStmt(c.body, v);
            walkStmt(w.def, v);
        }
    }

    /** Thăm mọi biểu thức (kể cả biểu thức con) thuộc TRỰC TIẾP câu lệnh s, không đi vào câu lệnh lồng. */
    static void stmtExprs(Stmt s, Visitor v){
        if(s instanceof Let) walkExpr(((Let)s).init, v);
        else if(s instanceof Assign){
            walkExpr(((Assign)s).target, v);
            walkExpr(((Assign)s).value, v);
        }else if(s instanceof ExprStmt) walkExpr(((ExprStmt)s).call, v);
        else if(s instanceof If) walkExpr(((If)s).cond, v);
        else if(s instanceof While) walkExpr(((While)s).cond, v);
        else if(s instanceof ForC) walkExpr(((ForC)s).cond, v);
        else if(s instanceof ForIn){
            ForIn f = (ForIn)s;
            walkExpr(f.from, v);
            walkExpr(f.to, v);
            walkExpr(f.step, v);
        }else if(s instanceof Switch){
            Switch w = (Switch)s;
            walkExpr(w.subject, v);
            for(Case c : w.cases) for(Expr e : c.values) walkExpr(e, v);
        }else if(s instanceof Return) walkExpr(((Return)s).value, v);
    }

    static void walkExpr(Expr e, Visitor v){
        if(e == null) return;
        v.visit(e);
        if(e instanceof Unary) walkExpr(((Unary)e).e, v);
        else if(e instanceof Binary){
            walkExpr(((Binary)e).l, v);
            walkExpr(((Binary)e).r, v);
        }else if(e instanceof Ternary){
            walkExpr(((Ternary)e).c, v);
            walkExpr(((Ternary)e).a, v);
            walkExpr(((Ternary)e).b, v);
        }else if(e instanceof Index){
            walkExpr(((Index)e).base, v);
            walkExpr(((Index)e).idx, v);
        }else if(e instanceof Member) walkExpr(((Member)e).base, v);
        else if(e instanceof Call){
            for(Expr a : ((Call)e).args) walkExpr(a, v);
        }
    }
}
