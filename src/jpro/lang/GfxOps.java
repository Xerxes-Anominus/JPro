package jpro.lang;

import java.util.*;

/**
 * Bảng các hàm vẽ lên thế giới (Draw, Fill, Lines, Drawf) chỉ dùng ở cấp World Processor.
 *
 * Trình biên dịch đổi mỗi hàm thành một dòng mlog tự thêm của mod:
 *     jdraw <tên_lệnh> a b c d e f        (luôn đủ 6 toán hạng, thừa thì điền 0)
 * và phần chạy trong game (JProLogic / JDraw) đọc lại đúng bảng này, nên hai bên không bao giờ lệch nhau.
 *
 * Tọa độ và kích thước tính theo Ô (giống mọi hàm khác của JPro); {@link Op#mask} cho biết toán hạng nào
 * là độ dài theo ô (sẽ nhân 8 để ra đơn vị thế giới khi vẽ). Các toán hạng còn lại (màu, số cạnh, góc, độ mờ...)
 * giữ nguyên giá trị.
 *
 * Lưu ý: file này không phụ thuộc Mindustry để có thể kiểm thử ngoài game.
 */
public final class GfxOps{
    private GfxOps(){}

    public static final class Op{
        /** Tên gọi trong JPro, vd "Fill.rect". */
        public final String key;
        public final String sig;
        /** Mẫu lệnh mlog cho {@link Builtins}. */
        public final String template;
        public final String doc;
        /** Tên lệnh ở toán hạng đầu của "jdraw", vd "frect". */
        public final String name;
        public final int id;
        /** Bit i = 1: toán hạng thứ i là độ dài theo ô. */
        public final int mask;

        Op(int id, String key, String sig, String name, String operands, int mask, String doc){
            this.id = id;
            this.key = key;
            this.sig = sig;
            this.name = name;
            this.template = "jdraw " + name + " " + operands;
            this.mask = mask;
            this.doc = doc;
        }
    }

    public static final String WORLD_NAMESPACES = "Draw Drawf Lines Fill";

    public static final List<Op> ALL;
    private static final Map<String, Op> BY_NAME = new HashMap<>();
    private static final List<Op> BY_ID = new ArrayList<>();

    private static void add(String key, String sig, String name, String operands, int mask, String doc){
        Op o = new Op(BY_ID.size(), key, sig, name, operands, mask, doc);
        BY_ID.add(o);
        BY_NAME.put(name, o);
    }

    // mặt nạ độ dài theo ô cho các dạng toán hạng hay gặp
    private static final int L2 = 0b1111, C = 0b111, POLY = 0b1011, T3 = 0b111111;

    static{
        // ----- Draw: trạng thái vẽ -----
        add("Draw.color", "r, g, b, a=255", "color", "{0} {1} {2} {3|255} 0 0", 0,
            "Đặt màu vẽ cho các lệnh sau (mỗi kênh 0..255)");
        add("Draw.colorPacked", "màu", "col", "{0} 0 0 0 0 0", 0,
            "Đặt màu vẽ từ màu packed (vd: #ff8800 hoặc packColor(r,g,b,a))");
        add("Draw.blend", "chế_độ", "blend", "{0} 0 0 0 0 0", 0,
            "Đặt blending: 0=normal, 1=additive, 2=multiply, 3=screen, 4=disabled");
        add("Draw.blendReset", "", "blendreset", "0 0 0 0 0 0", 0,
            "Đặt blending về normal");
        add("Draw.alpha", "độ_mờ", "alpha", "{0} 0 0 0 0 0", 0,
            "Đổi độ mờ của màu hiện tại (0..255)");
        add("Draw.z", "lớp", "z", "{0} 0 0 0 0 0", 0,
            "Chọn lớp vẽ (số lớn nằm trên). Mặc định 120 = trên cả công trình và lính");
        add("Draw.flush", "", "flush", "0 0 0 0 0 0", 0,
            "Công bố mọi thứ vừa vẽ ra màn hình thế giới (giống drawFlush). Khung hình giữ nguyên tới lần flush sau");
        add("Draw.clear", "", "clear", "0 0 0 0 0 0", 0,
            "Bỏ những gì đã vẽ dở chưa flush (muốn xóa hẳn màn hình thì gọi Draw.clear() rồi Draw.flush())");

        // ----- Lines: nét viền -----
        add("Lines.stroke", "độ_dày_pixel", "stroke", "{0} 0 0 0 0 0", 0,
            "Độ dày nét vẽ của Lines và Drawf, tính bằng pixel (1 ô = 8 pixel)");
        add("Lines.line", "x1, y1, x2, y2", "lline", "{0} {1} {2} {3} 0 0", L2,
            "Vẽ đoạn thẳng (tọa độ theo ô)");
        add("Lines.rect", "x, y, rộng, cao", "lrect", "{0} {1} {2} {3} 0 0", L2,
            "Viền hình chữ nhật, (x, y) là góc dưới trái");
        add("Lines.circle", "x, y, bán_kính", "lcircle", "{0} {1} {2} 0 0 0", C,
            "Viền hình tròn");
        add("Lines.poly", "x, y, số_cạnh, bán_kính, góc=0", "lpoly", "{0} {1} {2} {3} {4|0} 0", POLY,
            "Viền đa giác đều");
        add("Lines.square", "x, y, bán_kính, góc=0", "lsquare", "{0} {1} {2} {3|0} 0 0", C,
            "Viền hình vuông tâm (x, y); bán_kính là nửa cạnh");
        add("Lines.arc", "x, y, bán_kính, phần, góc=0", "larc", "{0} {1} {2} {3} {4|0} 0", C,
            "Viền cung tròn; phần 0..1 là tỉ lệ vòng tròn");
        add("Lines.dashLine", "x1, y1, x2, y2, số_đoạn", "ldash", "{0} {1} {2} {3} {4} 0", L2,
            "Đường nét đứt chia thành số_đoạn khúc");
        add("Lines.dashCircle", "x, y, bán_kính", "ldcircle", "{0} {1} {2} 0 0 0", C,
            "Hình tròn nét đứt");

        // ----- Fill: tô đặc -----
        add("Fill.rect", "x, y, rộng, cao", "frect", "{0} {1} {2} {3} 0 0", L2,
            "Tô hình chữ nhật, (x, y) là góc dưới trái");
        add("Fill.circle", "x, y, bán_kính", "fcircle", "{0} {1} {2} 0 0 0", C,
            "Tô hình tròn");
        add("Fill.poly", "x, y, số_cạnh, bán_kính, góc=0", "fpoly", "{0} {1} {2} {3} {4|0} 0", POLY,
            "Tô đa giác đều");
        add("Fill.tri", "x1, y1, x2, y2, x3, y3", "ftri", "{0} {1} {2} {3} {4} {5}", T3,
            "Tô tam giác");
        add("Fill.square", "x, y, bán_kính, góc=0", "fsquare", "{0} {1} {2} {3|0} 0 0", C,
            "Tô hình vuông tâm (x, y); bán_kính là nửa cạnh");
        add("Fill.arc", "x, y, bán_kính, phần, góc=0", "farc", "{0} {1} {2} {3} {4|0} 0", C,
            "Tô hình quạt; phần 0..1 là tỉ lệ vòng tròn");

        // ----- Drawf: hình trang trí có sẵn của Mindustry (dùng màu hiện tại của Draw.color) -----
        add("Drawf.circles", "x, y, bán_kính", "dcircles", "{0} {1} {2} 0 0 0", C,
            "Vòng tròn đôi kiểu tầm bắn của tháp pháo");
        add("Drawf.dashCircle", "x, y, bán_kính", "ddcircle", "{0} {1} {2} 0 0 0", C,
            "Vòng tròn nét đứt có bóng");
        add("Drawf.dashLine", "x1, y1, x2, y2", "ddline", "{0} {1} {2} {3} 0 0", L2,
            "Đường nét đứt có bóng");
        add("Drawf.target", "x, y, bán_kính, độ_mờ=1", "dtarget", "{0} {1} {2} {3|1} 0 0", C,
            "Dấu ngắm mục tiêu; độ_mờ 0..1");
        add("Drawf.arrow", "x1, y1, x2, y2, độ_dài, bề_rộng", "darrow", "{0} {1} {2} {3} {4} {5}", T3,
            "Mũi tên từ (x1, y1) tới (x2, y2)");
        add("Drawf.select", "x, y, bán_kính", "dselect", "{0} {1} {2} 0 0 0", C,
            "Khung chọn bốn góc");
        add("Drawf.square", "x, y, bán_kính, góc=0", "dsquare", "{0} {1} {2} {3|0} 0 0", C,
            "Hình vuông có viền và nền mờ");

        ALL = Collections.unmodifiableList(new ArrayList<>(BY_ID));
    }

    /** Số thứ tự của lệnh theo tên, hoặc -1 nếu không có. */
    public static int idOf(String name){
        Op o = BY_NAME.get(name);
        return o == null ? -1 : o.id;
    }

    public static Op byId(int id){
        return id >= 0 && id < BY_ID.size() ? BY_ID.get(id) : null;
    }

    /** Có phải nhóm hàm chỉ dùng ở cấp World Processor (Draw, Drawf, Lines, Fill). */
    public static boolean isWorldNamespace(String ns){
        if(ns == null) return false;
        for(String s : WORLD_NAMESPACES.split(" ")) if(s.equals(ns)) return true;
        return false;
    }
}