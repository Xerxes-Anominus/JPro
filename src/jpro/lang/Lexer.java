package jpro.lang;

import java.util.*;

/** Tách mã nguồn thành các token. */
final class Lexer{
    static final class Tok{
        static final int IDENT = 0, NUM = 1, STR = 2, AT = 3, COLOR = 4, OP = 5, RAW = 6, EOF = 7;

        final int type;
        final String text;
        final int line, col;
        final boolean nl; // có xuống dòng ngay trước token này
        double num;

        Tok(int type, String text, int line, int col, boolean nl){
            this.type = type;
            this.text = text;
            this.line = line;
            this.col = col;
            this.nl = nl;
        }

        boolean is(String s){
            return (type == OP || type == IDENT) && text.equals(s);
        }

        @Override
        public String toString(){
            switch(type){
                case EOF: return "hết mã";
                case STR: return "chuỗi \"" + text + "\"";
                case RAW: return "khối asm";
                case AT: return "'@" + text + "'";
                default: return "'" + text + "'";
            }
        }
    }

    private static final String[] OPS = {
        "===", "**=", "<<=", ">>=",
        "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
        "++", "--", "**", "<<", ">>", "..", "->",
        "+", "-", "*", "/", "%", "=", "<", ">", "!", "~", "&", "|", "^",
        "(", ")", "{", "}", "[", "]", ",", ";", ".", ":", "?"
    };

    private final String s;
    private final int n;
    private int i = 0, line = 1, lineStart = 0;
    private final List<Tok> out = new ArrayList<>();

    private Lexer(String src){
        this.s = src;
        this.n = src.length();
    }

    static List<Tok> lex(String src){
        Lexer lx = new Lexer(src.replace("\r\n", "\n").replace('\r', '\n'));
        lx.run();
        return lx.out;
    }

    private JProError err(int ln, int col, String msg){
        return new JProError(ln, col, msg);
    }

    private static boolean identStart(char c){
        return Character.isLetter(c) || c == '_';
    }

    private static boolean identPart(char c){
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean hex(char c){
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void run(){
        boolean nl = false;
        while(i < n){
            char c = s.charAt(i);
            if(c == '\n'){
                line++;
                i++;
                lineStart = i;
                nl = true;
                continue;
            }
            if(Character.isWhitespace(c)){
                i++;
                continue;
            }
            if(c == '/' && i + 1 < n && s.charAt(i + 1) == '/'){
                while(i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if(c == '/' && i + 1 < n && s.charAt(i + 1) == '*'){
                int sl = line, sc = i - lineStart + 1;
                i += 2;
                boolean closed = false;
                while(i < n){
                    char d = s.charAt(i);
                    if(d == '*' && i + 1 < n && s.charAt(i + 1) == '/'){
                        i += 2;
                        closed = true;
                        break;
                    }
                    if(d == '\n'){
                        line++;
                        lineStart = i + 1;
                        nl = true;
                    }
                    i++;
                }
                if(!closed) throw err(sl, sc, "Chú thích /* ... */ chưa được đóng bằng */");
                continue;
            }

            int col = i - lineStart + 1;

            if(identStart(c)){
                int st = i;
                while(i < n && identPart(s.charAt(i))) i++;
                String id = s.substring(st, i);
                out.add(new Tok(Tok.IDENT, id, line, col, nl));
                nl = false;
                if(id.equals("asm")) rawAsm();
                continue;
            }

            if(Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))){
                out.add(number(col, nl));
                nl = false;
                continue;
            }

            if(c == '"'){
                int st = ++i;
                while(i < n && s.charAt(i) != '"'){
                    if(s.charAt(i) == '\n') throw err(line, col, "Chuỗi không được xuống dòng — hãy dùng \\n");
                    i++;
                }
                if(i >= n) throw err(line, col, "Chuỗi chưa được đóng bằng dấu \"");
                out.add(new Tok(Tok.STR, s.substring(st, i), line, col, nl));
                i++;
                nl = false;
                continue;
            }

            if(c == '@'){
                i++;
                if(i < n && s.charAt(i) == '"'){
                    int st = ++i;
                    while(i < n && s.charAt(i) != '"' && s.charAt(i) != '\n') i++;
                    if(i >= n || s.charAt(i) != '"') throw err(line, col, "@\"...\" chưa được đóng bằng dấu \"");
                    out.add(new Tok(Tok.AT, s.substring(st, i), line, col, nl));
                    i++;
                }else{
                    int st = i;
                    while(i < n && identPart(s.charAt(i))) i++;
                    if(st == i) throw err(line, col, "Sau '@' phải có tên, ví dụ @copper, @unit, @time");
                    // Tên có gạch ngang như @copper-wall: '-' dính liền và theo sau là chữ cái thì thuộc về tên.
                    // Muốn trừ thì viết có khoảng trắng: @time - x
                    while(i + 1 < n && s.charAt(i) == '-' && identStart(s.charAt(i + 1))){
                        i++;
                        while(i < n && identPart(s.charAt(i))) i++;
                    }
                    // Tên nội dung trong game dùng dấu '-', ở đây cho phép viết '_' cho dễ gõ
                    out.add(new Tok(Tok.AT, s.substring(st, i).replace('_', '-'), line, col, nl));
                }
                nl = false;
                continue;
            }

            if(c == '#'){
                int st = ++i;
                while(i < n && hex(s.charAt(i))) i++;
                int len = i - st;
                if((len != 6 && len != 8) || (i < n && identPart(s.charAt(i))))
                    throw err(line, col, "Màu phải viết dạng #rrggbb hoặc #rrggbbaa, ví dụ #ff8800");
                out.add(new Tok(Tok.COLOR, "%" + s.substring(st, i).toLowerCase(), line, col, nl));
                nl = false;
                continue;
            }

            String op = null;
            for(String o : OPS){
                if(s.startsWith(o, i)){
                    op = o;
                    break;
                }
            }
            if(op == null) throw err(line, col, "Ký tự không hợp lệ: '" + c + "'");
            i += op.length();
            out.add(new Tok(Tok.OP, op, line, col, nl));
            nl = false;
        }
        out.add(new Tok(Tok.EOF, "", line, i - lineStart + 1, true));
    }

    private Tok number(int col, boolean nl){
        int st = i;
        double v;
        if(s.charAt(i) == '0' && i + 1 < n && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')){
            i += 2;
            int hs = i;
            while(i < n && (hex(s.charAt(i)) || s.charAt(i) == '_')) i++;
            String h = s.substring(hs, i).replace("_", "");
            if(h.isEmpty()) throw err(line, col, "Số hex không hợp lệ");
            v = (double)Long.parseLong(h, 16);
        }else if(s.charAt(i) == '0' && i + 1 < n && (s.charAt(i + 1) == 'b' || s.charAt(i + 1) == 'B')){
            i += 2;
            int bs = i;
            while(i < n && (s.charAt(i) == '0' || s.charAt(i) == '1' || s.charAt(i) == '_')) i++;
            String b = s.substring(bs, i).replace("_", "");
            if(b.isEmpty()) throw err(line, col, "Số nhị phân không hợp lệ");
            v = (double)Long.parseLong(b, 2);
        }else{
            while(i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
            // phần thập phân: '.' phải theo sau bởi chữ số (để 0..10 vẫn là một khoảng)
            if(i + 1 < n && s.charAt(i) == '.' && Character.isDigit(s.charAt(i + 1))){
                i++;
                while(i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
            }
            if(i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')){
                int save = i;
                i++;
                if(i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                if(i < n && Character.isDigit(s.charAt(i))){
                    while(i < n && Character.isDigit(s.charAt(i))) i++;
                }else{
                    i = save;
                }
            }
            try{
                v = Double.parseDouble(s.substring(st, i).replace("_", ""));
            }catch(NumberFormatException e){
                throw err(line, col, "Số không hợp lệ: " + s.substring(st, i));
            }
        }
        if(i < n && identStart(s.charAt(i))) throw err(line, col, "Số '" + s.substring(st, i + 1) + "...' không hợp lệ (dính chữ)");
        Tok t = new Tok(Tok.NUM, s.substring(st, i), line, col, nl);
        t.num = v;
        return t;
    }

    /** Sau từ khóa asm: lấy nguyên văn mọi thứ trong { ... } làm một token RAW. */
    private void rawAsm(){
        int j = i;
        while(j < n && s.charAt(j) != '\n' && Character.isWhitespace(s.charAt(j))) j++;
        // cho phép { ở dòng kế tiếp
        int k = j;
        while(k < n && Character.isWhitespace(s.charAt(k))) k++;
        if(k >= n || s.charAt(k) != '{') return; // để parser báo lỗi

        // cập nhật số dòng khi nhảy qua khoảng trắng
        for(; i < k; i++){
            if(s.charAt(i) == '\n'){
                line++;
                lineStart = i + 1;
            }
        }
        int col = i - lineStart + 1;
        out.add(new Tok(Tok.OP, "{", line, col, false));
        i++; // bỏ '{'
        int rawLine = line, rawCol = i - lineStart + 1;
        int st = i;
        boolean inStr = false;
        while(i < n){
            char c = s.charAt(i);
            if(c == '\n'){
                line++;
                lineStart = i + 1;
                inStr = false;
            }else if(c == '"'){
                inStr = !inStr;
            }else if(c == '}' && !inStr){
                break;
            }
            i++;
        }
        if(i >= n) throw err(rawLine, rawCol, "Khối asm { ... } chưa được đóng bằng }");
        out.add(new Tok(Tok.RAW, s.substring(st, i), rawLine, rawCol, false));
        out.add(new Tok(Tok.OP, "}", line, i - lineStart + 1, false));
        i++;
    }
}