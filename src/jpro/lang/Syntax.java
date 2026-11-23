package jpro.lang;

import java.util.*;

/**
 * Danh sách từ khóa của ngôn ngữ JPro (cú pháp theo Java).
 * Parser dùng để kiểm tra tên, trình tô màu dùng để chọn màu — nên hai bên luôn khớp nhau.
 */
public final class Syntax{
    private Syntax(){}

    private static Set<String> set(String... a){
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(a)));
    }

    /** Điều khiển luồng: if, for, switch, return... (và các từ riêng của JPro: once, end, stop). */
    public static final Set<String> CONTROL = set(
        "if", "else", "while", "for", "loop", "switch", "case", "default",
        "break", "continue", "return", "end", "stop", "once", "asm"
    );

    /** Khai báo và bổ từ: final, static, void... (let / var / const / fn là cách viết cũ, vẫn dùng được). */
    public static final Set<String> DECLARE = set(
        "let", "var", "const", "fn", "final", "static", "public", "private", "protected", "void"
    );

    /** Bổ từ có thể đặt trước khai báo, như trong Java. */
    public static final Set<String> MODIFIERS = set("final", "static", "public", "private", "protected");

    /** Kiểu nguyên thủy. JPro không phân biệt kiểu lúc chạy (mlog chỉ có số, chuỗi, đối tượng). */
    public static final Set<String> PRIMITIVE = set(
        "int", "long", "short", "byte", "float", "double", "boolean", "char"
    );

    /** Giá trị cố định. */
    public static final Set<String> LITERAL = set("true", "false", "null", "this");

    /** Mọi từ khóa: không dùng làm tên biến / hàm được. */
    public static final Set<String> KEYWORDS;
    static{
        Set<String> all = new LinkedHashSet<>();
        all.addAll(CONTROL);
        all.addAll(DECLARE);
        all.addAll(PRIMITIVE);
        all.addAll(LITERAL);
        KEYWORDS = Collections.unmodifiableSet(all);
    }
}
