package jpro.lang;

/** Lỗi cú pháp / biên dịch, kèm vị trí dòng và cột (đếm từ 1). */
public final class JProError extends RuntimeException{
    public final int line, col;
    public final String msg;

    public JProError(int line, int col, String msg){
        super(msg, null, false, false);
        this.line = line;
        this.col = col;
        this.msg = msg;
    }

    @Override
    public String getMessage(){
        return "Dòng " + line + ", cột " + col + ": " + msg;
    }
}
