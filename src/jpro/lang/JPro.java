package jpro.lang;

import java.util.*;

import jpro.lang.Node.Stmt;

/** Điểm vào công khai của trình biên dịch JPro -> mlog. */
public final class JPro{
    private JPro(){}

    public static final class Result{
        /** Mã mlog (rỗng nếu có lỗi). */
        public final String mlog;
        public final List<JProError> errors;

        Result(String mlog, List<JProError> errors){
            this.mlog = mlog;
            this.errors = errors;
        }

        public boolean ok(){ return errors.isEmpty(); }

        /** Số lệnh mlog được sinh ra. */
        public int instructions(){
            if(mlog.isEmpty()) return 0;
            int n = 0;
            for(String l : mlog.split("\n")) if(!l.trim().isEmpty()) n++;
            return n;
        }
    }

    public static Result compile(String src){
        List<JProError> errs = new ArrayList<>();
        List<Stmt> prog;
        try{
            prog = Parser.parse(src);
        }catch(JProError e){
            errs.add(e);
            return new Result("", errs);
        }
        CodeGen cg = new CodeGen();
        try{
            cg.generate(prog);
        }catch(JProError e){
            cg.errors.add(e);
        }
        if(!cg.errors.isEmpty()){
            errs.addAll(cg.errors);
            Collections.sort(errs, new Comparator<JProError>(){
                @Override
                public int compare(JProError a, JProError b){
                    return a.line != b.line ? Integer.compare(a.line, b.line) : Integer.compare(a.col, b.col);
                }
            });
            return new Result("", errs);
        }
        return new Result(cg.render(), errs);
    }
}
