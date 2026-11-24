package jpro.ui;

import java.util.*;

import jpro.lang.Builtins;
import jpro.lang.Syntax;

/**
 * Tô màu mã JPro giống trình soạn Java: từ khóa, tên kiểu, tên hàm (method), thuộc tính,
 * chuỗi, số, chú thích, @nội_dung và màu #rrggbb.
 *
 * Lớp này thuần Java (không dùng Arc/Mindustry) và chịu được mã đang gõ dở:
 * chuỗi chưa đóng, chú thích chưa đóng, ký tự lạ... đều không gây lỗi.
 */
public final class Highlighter{
    private Highlighter(){}

    // ---- các loại token ----
    public static final int
        DEFAULT = 0,
        /** let var const function staticFunc final static void int double boolean true false null ... */
        KEYWORD = 1,
        /** if else while for switch case return break continue once loop end stop asm */
        CONTROL = 2,
        /** Tên kiểu (String, Unit) và nhóm hàm (unit, world, draw...) */
        TYPE = 3,
        /** Tên hàm / method: tinh(...), unit.move(...) */
        METHOD = 4,
        /** Thuộc tính: u.health */
        FIELD = 5,
        /** Hằng viết HOA: CHU_KY */
        CONSTANT = 6,
        STRING = 7,
        NUMBER = 8,
        COMMENT = 9,
        /** @copper, @unit, @"tên lạ" */
        CONTENT = 10,
        /** #ff8800 — tô đúng màu của nó */
        COLOR = 11;

    /** Bảng màu RGB (0xRRGGBB), theo kiểu tối của VS Code. Chỉ số = loại token. */
    public static final int[] PALETTE = {
        0xd4d4d4, // DEFAULT
        0x569cd6, // KEYWORD
        0xc586c0, // CONTROL
        0x4ec9b0, // TYPE
        0xdcdcaa, // METHOD
        0x9cdcfe, // FIELD
        0x4fc1ff, // CONSTANT
        0xce9178, // STRING
        0xb5cea8, // NUMBER
        0x6a9955, // COMMENT
        0xd7ba7d, // CONTENT
        0xd4d4d4  // COLOR (bị thay bằng chính màu được viết)
    };

    /** Một đoạn token: [start, end) trong chuỗi nguồn. */
    public static final class Span{
        public final int start, end, kind;
        /** Màu RGB cuối cùng của đoạn. */
        public final int rgb;

        Span(int start, int end, int kind, int rgb){
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.rgb = rgb;
        }
    }

    private static boolean identStart(char c){ return Character.isLetter(c) || c == '_'; }
    private static boolean identPart(char c){ return Character.isLetterOrDigit(c) || c == '_'; }
    private static boolean hex(char c){ return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

    /** Tách mã nguồn thành các đoạn có màu. Các khoảng trống / toán tử không có đoạn (dùng màu mặc định). */
    public static List<Span> spans(String src){
        List<Span> out = new ArrayList<>();
        if(src == null) return out;
        final int n = src.length();
        int i = 0;
        while(i < n){
            char c = src.charAt(i);

            if(Character.isWhitespace(c)){
                i++;
                continue;
            }

            // chú thích
            if(c == '/' && i + 1 < n && src.charAt(i + 1) == '/'){
                int st = i;
                while(i < n && src.charAt(i) != '\n') i++;
                add(out, st, i, COMMENT);
                continue;
            }
            if(c == '/' && i + 1 < n && src.charAt(i + 1) == '*'){
                int st = i;
                i += 2;
                while(i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                add(out, st, i, COMMENT);
                continue;
            }

            // chuỗi "..." (chưa đóng thì tô đến hết dòng)
            if(c == '"'){
                int st = i++;
                while(i < n && src.charAt(i) != '"' && src.charAt(i) != '\n') i++;
                if(i < n && src.charAt(i) == '"') i++;
                add(out, st, i, STRING);
                continue;
            }

            // @copper, @copper-wall, @"tên lạ"
            if(c == '@'){
                int st = i++;
                if(i < n && src.charAt(i) == '"'){
                    i++;
                    while(i < n && src.charAt(i) != '"' && src.charAt(i) != '\n') i++;
                    if(i < n && src.charAt(i) == '"') i++;
                }else{
                    while(i < n && identPart(src.charAt(i))) i++;
                    while(i + 1 < n && src.charAt(i) == '-' && identStart(src.charAt(i + 1))){
                        i++;
                        while(i < n && identPart(src.charAt(i))) i++;
                    }
                }
                add(out, st, i, CONTENT);
                continue;
            }

            // màu #rrggbb / #rrggbbaa
            if(c == '#'){
                int st = i++;
                while(i < n && hex(src.charAt(i))) i++;
                int len = i - st - 1;
                if(len == 6 || len == 8){
                    int rgb = Integer.parseInt(src.substring(st + 1, st + 7), 16);
                    out.add(new Span(st, i, COLOR, readable(rgb)));
                }
                continue;
            }

            // số
            if(Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))){
                int st = i;
                if(c == '0' && i + 1 < n && (src.charAt(i + 1) == 'x' || src.charAt(i + 1) == 'X' || src.charAt(i + 1) == 'b' || src.charAt(i + 1) == 'B')){
                    i += 2;
                    while(i < n && (hex(src.charAt(i)) || src.charAt(i) == '_')) i++;
                }else{
                    while(i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                    // '.' phải theo sau bởi chữ số, để 0..10 vẫn là một khoảng
                    if(i + 1 < n && src.charAt(i) == '.' && Character.isDigit(src.charAt(i + 1))){
                        i++;
                        while(i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                    }
                    if(i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E')){
                        int save = i++;
                        if(i < n && (src.charAt(i) == '+' || src.charAt(i) == '-')) i++;
                        if(i < n && Character.isDigit(src.charAt(i))){
                            while(i < n && Character.isDigit(src.charAt(i))) i++;
                        }else i = save;
                    }
                }
                add(out, st, i, NUMBER);
                continue;
            }

            // tên / từ khóa
            if(identStart(c)){
                int st = i;
                while(i < n && identPart(src.charAt(i))) i++;
                String w = src.substring(st, i);

                if(w.equals("asm") && nextSignificant(src, i) == '{'){
                    add(out, st, i, CONTROL);
                    i = asmBlock(src, i, out);
                    continue;
                }
                add(out, st, i, classify(src, w, st, i));
                continue;
            }

            i++; // toán tử, dấu câu: màu mặc định
        }
        return out;
    }

    /** Màu cho từng ký tự: mảng RGB dài đúng bằng độ dài chuỗi, chỗ không có token là màu mặc định. */
    public static int[] colors(String src){
        int[] col = new int[src.length()];
        Arrays.fill(col, PALETTE[DEFAULT]);
        for(Span s : spans(src)){
            for(int k = s.start; k < s.end && k < col.length; k++) col[k] = s.rgb;
        }
        return col;
    }

    private static void add(List<Span> out, int st, int en, int kind){
        if(en > st) out.add(new Span(st, en, kind, PALETTE[kind]));
    }

    /** Ký tự không phải khoảng trắng kế tiếp từ vị trí i, hoặc 0 nếu hết. */
    private static char nextSignificant(String s, int i){
        while(i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i < s.length() ? s.charAt(i) : 0;
    }

    private static char prevSignificant(String s, int i){
        i--;
        while(i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return i >= 0 ? s.charAt(i) : 0;
    }

    private static int classify(String src, String w, int st, int en){
        if(Syntax.CONTROL.contains(w)) return CONTROL;
        if(Syntax.KEYWORDS.contains(w)) return KEYWORD;

        char next = nextSignificant(src, en);
        char prev = prevSignificant(src, st);
        boolean afterDot = prev == '.' && !(st >= 2 && src.charAt(st - 2) == '.' && src.charAt(st - 1) == '.');

        if(next == '(') return METHOD;
        if(afterDot) return FIELD;

        // nhóm hàm có sẵn: unit.move(), world.spawn(), draw.rect()... viết như tên lớp
        if(next == '.' && Builtins.isNamespace(w)) return TYPE;

        // "Kiểu tên": String s, Unit u, int x... — từ đứng trước một tên khác là tên kiểu
        int k = en;
        while(k < src.length() && Character.isWhitespace(src.charAt(k))) k++;
        if(k > en && k < src.length() && identStart(src.charAt(k))){
            int e2 = k;
            while(e2 < src.length() && identPart(src.charAt(e2))) e2++;
            String nx = src.substring(k, e2);
            if(!Syntax.KEYWORDS.contains(nx) && !nx.equals("in") && !nx.equals("step")) return TYPE;
        }

        if(isConstantName(w)) return CONSTANT;
        if(Character.isUpperCase(w.charAt(0))) return TYPE;
        return DEFAULT;
    }

    /** CHU_KY, SO_LINH, N... — toàn chữ HOA (có ít nhất một chữ cái). */
    private static boolean isConstantName(String w){
        boolean letter = false;
        for(int k = 0; k < w.length(); k++){
            char ch = w.charAt(k);
            if(Character.isLowerCase(ch)) return false;
            if(Character.isLetter(ch)) letter = true;
        }
        return letter;
    }

    /** Màu quá tối (như #000000) sẽ không đọc được trên nền tối: pha thêm trắng. */
    private static int readable(int rgb){
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if(lum >= 0.35) return rgb;
        r = (r + 255 * 2) / 3;
        g = (g + 255 * 2) / 3;
        b = (b + 255 * 2) / 3;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Khối asm { ... }: mlog viết thẳng. Dòng nào cũng là "lệnh toán_hạng...",
     * tô lệnh như từ khóa, nhãn "skip:" như thuộc tính, và chuỗi / số / @tên như thường.
     * Trả về vị trí ngay sau dấu '}' đóng (hoặc hết chuỗi).
     */
    private static int asmBlock(String src, int from, List<Span> out){
        final int n = src.length();
        int i = from;
        while(i < n && src.charAt(i) != '{') i++;
        if(i >= n) return n;
        i++; // sau '{'
        boolean inStr = false;
        boolean lineStart = true;
        while(i < n){
            char c = src.charAt(i);
            if(c == '\n'){
                inStr = false;
                lineStart = true;
                i++;
                continue;
            }
            if(!inStr && c == '}') return i;   // '}' đóng được xử lý như dấu câu thường
            if(Character.isWhitespace(c)){
                i++;
                continue;
            }
            if(!inStr && (c == '#' || (c == '/' && i + 1 < n && src.charAt(i + 1) == '/'))){
                int st = i;
                while(i < n && src.charAt(i) != '\n') i++;
                add(out, st, i, COMMENT);
                continue;
            }
            if(c == '"'){
                int st = i++;
                while(i < n && src.charAt(i) != '"' && src.charAt(i) != '\n') i++;
                if(i < n && src.charAt(i) == '"') i++;
                add(out, st, i, STRING);
                lineStart = false;
                continue;
            }
            int st = i;
            while(i < n && !Character.isWhitespace(src.charAt(i)) && src.charAt(i) != '"' && src.charAt(i) != '}') i++;
            if(i == st){
                i++;
                continue;
            }
            String w = src.substring(st, i);
            int kind;
            if(lineStart) kind = w.endsWith(":") ? FIELD : KEYWORD;
            else if(w.startsWith("@")) kind = CONTENT;
            else if(isNumber(w)) kind = NUMBER;
            else kind = DEFAULT;
            if(kind != DEFAULT) add(out, st, i, kind);
            lineStart = false;
        }
        return i;
    }

    private static boolean isNumber(String w){
        try{
            Double.parseDouble(w.replace("_", ""));
            return true;
        }catch(NumberFormatException e){
            return false;
        }
    }
}
