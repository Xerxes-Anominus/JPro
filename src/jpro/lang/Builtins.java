package jpro.lang;

import java.util.*;

/**
 * Bảng hàm có sẵn. Mỗi hàm được định nghĩa bằng một "mẫu" lệnh mlog:
 *   {0}      giá trị của tham số số 0 (biểu thức)
 *   {$0}     tham số số 0 là TỪ KHÓA mlog viết nguyên văn (enemy, any, distance, floor...)
 *   {>0}     tham số số 0 là TÊN BIẾN nhận kết quả đầu ra
 *   {n|mặc định}  tham số tùy chọn, thiếu thì dùng giá trị mặc định
 *   {r}      biến chứa kết quả trả về của hàm
 */
public final class Builtins{
    static final int VALUE = 1, SYMBOL = 2, OUT = 3;
    static final int P_LIT = 0, P_ARG = 1, P_RES = 2;

    static final class Part{
        final int type, arg;
        final String lit, def;

        Part(int type, int arg, String lit, String def){
            this.type = type;
            this.arg = arg;
            this.lit = lit;
            this.def = def;
        }
    }

    public static final class Builtin{
        public final String key, sig, doc;
        final Part[] parts;
        final int[] kinds;
        final int minArgs, maxArgs;
        final boolean hasResult;

        Builtin(String key, String sig, String template, String doc){
            this.key = key;
            this.sig = sig;
            this.doc = doc;
            List<Part> ps = new ArrayList<>();
            int max = 0, min = 0;
            boolean res = false;
            int[] k = new int[16];
            for(String tok : template.trim().split("\\s+")){
                if(tok.startsWith("{") && tok.endsWith("}")){
                    String in = tok.substring(1, tok.length() - 1);
                    if(in.equals("r")){
                        ps.add(new Part(P_RES, 0, null, null));
                        res = true;
                        continue;
                    }
                    int kind = VALUE;
                    if(in.startsWith("$")){
                        kind = SYMBOL;
                        in = in.substring(1);
                    }else if(in.startsWith(">")){
                        kind = OUT;
                        in = in.substring(1);
                    }
                    String def = null;
                    int bar = in.indexOf('|');
                    if(bar >= 0){
                        def = in.substring(bar + 1);
                        in = in.substring(0, bar);
                    }
                    int idx = Integer.parseInt(in);
                    k[idx] = kind;
                    max = Math.max(max, idx + 1);
                    if(def == null) min = Math.max(min, idx + 1);
                    ps.add(new Part(P_ARG, idx, null, def));
                }else{
                    ps.add(new Part(P_LIT, 0, tok, null));
                }
            }
            this.parts = ps.toArray(new Part[0]);
            this.kinds = Arrays.copyOf(k, Math.max(max, 1));
            this.minArgs = min;
            this.maxArgs = max;
            this.hasResult = res;
        }

        public String usage(){
            return key + "(" + sig + ")";
        }
    }

    private static final Map<String, Builtin> MAP = new LinkedHashMap<>();

    private static void add(String key, String sig, String tpl, String doc){
        MAP.put(key, new Builtin(key, sig, tpl, doc));
    }

    public static Builtin find(String ns, String name){
        return MAP.get(ns == null ? name : ns + "." + name);
    }

    public static boolean isNamespace(String ns){
        for(String k : MAP.keySet()) if(k.startsWith(ns + ".")) return true;
        return false;
    }

    public static boolean isTopLevel(String name){
        return MAP.containsKey(name) || name.equals("print");
    }

    public static Collection<Builtin> all(){
        return MAP.values();
    }

    /** Gợi ý tên gần giống khi gõ sai. */
    public static String suggest(String ns, String name){
        String want = ns == null ? name : ns + "." + name;
        String best = null;
        int bd = 4;
        List<String> keys = new ArrayList<>(MAP.keySet());
        keys.add("print");
        for(String k : keys){
            int d = dist(want.toLowerCase(), k.toLowerCase());
            if(d < bd){
                bd = d;
                best = k;
            }
        }
        return best;
    }

    private static int dist(String a, String b){
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for(int j = 0; j <= b.length(); j++) prev[j] = j;
        for(int i = 1; i <= a.length(); i++){
            cur[0] = i;
            for(int j = 1; j <= b.length(); j++){
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    static{
        // ----- Nhập / xuất -----
        add("printFlush", "message", "printflush {0}", "Đẩy nội dung đã print ra khối message");
        add("drawFlush", "display", "drawflush {0}", "Đẩy nội dung đã vẽ ra màn hình (display)");
        add("format", "giá_trị", "format {0}", "Thay {0}, {1}.. đầu tiên trong bộ đệm print bằng giá trị");
        add("printChar", "mã_ký_tự", "printchar {0}", "In một ký tự theo mã unicode");
        add("localePrint", "tên", "localeprint {0}", "In chuỗi bản địa hóa của bản đồ");
        add("wait", "giây", "wait {0}", "Chờ một khoảng thời gian (giây)");
        add("getLink", "số_thứ_tự", "getlink {r} {0}", "Lấy khối được liên kết thứ n (từ 0). Số liên kết: @links");
        add("sensor", "đối_tượng, thuộc_tính", "sensor {r} {0} {1}", "Đọc thuộc tính, ví dụ sensor(b, @health). Viết gọn: b.health");
        add("setRate", "lệnh_mỗi_tick", "setrate {0}", "Đặt tốc độ chạy của processor (lệnh/tick)");
        add("sync", "biến", "sync {>0}", "Đồng bộ một biến tới các client");
        add("packColor", "r, g, b, a=1", "packcolor {r} {0} {1} {2} {3|1}", "Gộp r,g,b,a (0..1) thành 1 số màu");
        add("lookupBlock", "số", "lookup block {r} {0}", "Tra khối theo số thứ tự");
        add("lookupUnit", "số", "lookup unit {r} {0}", "Tra loại lính theo số thứ tự");
        add("lookupItem", "số", "lookup item {r} {0}", "Tra vật phẩm theo số thứ tự");
        add("lookupLiquid", "số", "lookup liquid {r} {0}", "Tra chất lỏng theo số thứ tự");
        add("radar", "khối, mục_tiêu1, mục_tiêu2, mục_tiêu3, sắp_xếp, thứ_tự=1",
            "radar {$1} {$2} {$3} {$4} {0} {5|1} {r}",
            "Tìm mục tiêu quanh khối. VD: radar(turret1, enemy, any, any, distance)");

        // ----- Toán học (lệnh op) -----
        String[] m1 = {"abs", "log", "log10", "floor", "ceil", "sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "rand"};
        for(String m : m1) add(m, "x", "op " + m + " {r} {0} 0",
            m.equals("rand") ? "Số ngẫu nhiên từ 0 đến x" : "Hàm " + m + "(x) (sin/cos/tan tính theo độ)");
        add("max", "a, b", "op max {r} {0} {1}", "Số lớn hơn");
        add("min", "a, b", "op min {r} {0} {1}", "Số nhỏ hơn");
        add("pow", "a, b", "op pow {r} {0} {1}", "a mũ b (cũng viết được a ** b)");
        add("idiv", "a, b", "op idiv {r} {0} {1}", "Chia lấy phần nguyên (không dùng được '//' vì là chú thích)");
        add("len", "dx, dy", "op len {r} {0} {1}", "Độ dài vector = sqrt(dx*dx + dy*dy)");
        add("angle", "dx, dy", "op angle {r} {0} {1}", "Góc (độ) của vector (dx, dy)");
        add("angleDiff", "a, b", "op angleDiff {r} {0} {1}", "Chênh lệch giữa hai góc (độ)");
        add("noise", "x, y", "op noise {r} {0} {1}", "Nhiễu simplex 2D");

        // ----- Điều khiển khối (control) -----
        add("block.enable", "khối, bật", "control enabled {0} {1} 0 0 0", "Bật/tắt một khối. Viết gọn: b.enabled = 0");
        add("block.shoot", "khối, x, y, bắn", "control shoot {0} {1} {2} {3} 0", "Cho tháp pháo bắn vào tọa độ (x, y)");
        add("block.shootp", "khối, lính, bắn", "control shootp {0} {1} {2} 0 0", "Cho tháp pháo bắn vào một đơn vị (có dự đoán vị trí)");
        add("block.config", "khối, giá_trị", "control config {0} {1} 0 0 0", "Đặt cấu hình của khối (vd: vật phẩm cho sorter). Viết gọn: b.config = @copper");
        add("block.color", "khối, r, g, b", "control color {0} {1} {2} {3} 0", "Đặt màu cho khối chiếu sáng (illuminator)");

        // ----- Vẽ (draw) -----
        add("draw.clear", "r, g, b", "draw clear {0} {1} {2} 0 0 0", "Xóa màn hình bằng một màu (0..255)");
        add("draw.color", "r, g, b, a=255", "draw color {0} {1} {2} {3|255} 0 0", "Đặt màu vẽ (0..255)");
        add("draw.colorPacked", "màu", "draw col {0} 0 0 0 0 0", "Đặt màu vẽ từ 1 số màu (vd #ff8800 hoặc packColor)");
        add("draw.stroke", "độ_dày", "draw stroke {0} 0 0 0 0 0", "Đặt độ dày nét");
        add("draw.line", "x1, y1, x2, y2", "draw line {0} {1} {2} {3} 0 0", "Vẽ đường thẳng");
        add("draw.rect", "x, y, rộng, cao", "draw rect {0} {1} {2} {3} 0 0", "Vẽ hình chữ nhật đặc");
        add("draw.lineRect", "x, y, rộng, cao", "draw lineRect {0} {1} {2} {3} 0 0", "Vẽ viền hình chữ nhật");
        add("draw.poly", "x, y, số_cạnh, bán_kính, góc=0", "draw poly {0} {1} {2} {3} {4|0} 0", "Vẽ đa giác đặc");
        add("draw.linePoly", "x, y, số_cạnh, bán_kính, góc=0", "draw linePoly {0} {1} {2} {3} {4|0} 0", "Vẽ viền đa giác");
        add("draw.triangle", "x1, y1, x2, y2, x3, y3", "draw triangle {0} {1} {2} {3} {4} {5}", "Vẽ tam giác");
        add("draw.image", "x, y, ảnh, cỡ, góc=0", "draw image {0} {1} {2} {3} {4|0} 0", "Vẽ biểu tượng, vd draw.image(50, 50, @copper, 32)");
        add("draw.print", "x, y, căn=bottomLeft", "draw print {0} {1} {$2|bottomLeft} 0 0 0", "Vẽ chữ từ bộ đệm print (căn: center, top, left...)");
        add("draw.translate", "x, y", "draw translate {0} {1} 0 0 0 0", "Dịch gốc tọa độ");
        add("draw.scale", "x, y", "draw scale {0} {1} 0 0 0 0", "Phóng to/thu nhỏ");
        add("draw.rotate", "độ", "draw rotate {0} 0 0 0 0 0", "Xoay");
        add("draw.reset", "", "draw reset 0 0 0 0 0 0", "Đặt lại translate/scale/rotate");

        // ----- Điều khiển lính (unit) -----
        add("unit.bind", "loại", "ubind {0}", "Chọn một lính thuộc loại này để điều khiển (vd: unit.bind(@poly))");
        add("unit.unbind", "", "ucontrol unbind 0 0 0 0 0", "Thả lính đang điều khiển");
        add("unit.idle", "", "ucontrol idle 0 0 0 0 0", "Đứng yên, tự hành động");
        add("unit.stop", "", "ucontrol stop 0 0 0 0 0", "Dừng mọi thứ");
        add("unit.move", "x, y", "ucontrol move {0} {1} 0 0 0", "Di chuyển tới (x, y) (ô lưới)");
        add("unit.approach", "x, y, bán_kính", "ucontrol approach {0} {1} {2} 0 0", "Lại gần (x, y) trong bán kính");
        add("unit.pathfind", "x, y", "ucontrol pathfind {0} {1} 0 0 0", "Đi theo đường tìm lối tới (x, y)");
        add("unit.autoPathfind", "", "ucontrol autoPathfind 0 0 0 0 0", "Tự tìm đường như lính địch trong wave");
        add("unit.boost", "bật", "ucontrol boost {0} 0 0 0 0", "Bật/tắt bay (mech)");
        add("unit.target", "x, y, bắn", "ucontrol target {0} {1} {2} 0 0", "Ngắm/bắn vào (x, y)");
        add("unit.targetp", "lính, bắn", "ucontrol targetp {0} {1} 0 0 0", "Ngắm/bắn vào một đơn vị");
        add("unit.itemDrop", "tới, số_lượng", "ucontrol itemDrop {0} {1} 0 0 0", "Thả vật phẩm vào khối");
        add("unit.itemTake", "từ, vật_phẩm, số_lượng", "ucontrol itemTake {0} {1} {2} 0 0", "Lấy vật phẩm từ khối");
        add("unit.payDrop", "", "ucontrol payDrop 0 0 0 0 0", "Thả hàng đang mang");
        add("unit.payTake", "lấy_lính", "ucontrol payTake {0} 0 0 0 0", "Nhấc khối/lính phía dưới");
        add("unit.payEnter", "", "ucontrol payEnter 0 0 0 0 0", "Vào khối payload bên dưới");
        add("unit.mine", "x, y", "ucontrol mine {0} {1} 0 0 0", "Đào quặng tại (x, y)");
        add("unit.setFlag", "giá_trị", "ucontrol flag {0} 0 0 0 0", "Gắn cờ số cho lính");
        add("unit.build", "x, y, khối, góc, cấu_hình", "ucontrol build {0} {1} {2} {3} {4}", "Xây khối tại (x, y)");
        add("unit.getBlock", "x, y, ra_loại, ra_công_trình, ra_nền", "ucontrol getBlock {0} {1} {>2|__void} {>3|__void} {>4|__void}", "Lấy thông tin ô; kết quả ghi vào 3 biến cho trước");
        add("unit.within", "x, y, bán_kính", "ucontrol within {0} {1} {2} {r} 0", "Lính có nằm trong bán kính quanh (x, y) không");
        add("unit.radar", "mục_tiêu1, mục_tiêu2, mục_tiêu3, sắp_xếp, thứ_tự=1", "uradar {$0} {$1} {$2} {$3} 0 {4|1} {r}", "Radar quanh lính đang điều khiển");
        add("unit.locate", "loại, nhóm, địch=true, quặng=@copper, ra_x, ra_y, ra_tìm_thấy, ra_công_trình",
            "ulocate {$0} {$1} {2|true} {3|@copper} {>4|__void} {>5|__void} {>6|__void} {>7|__void}",
            "Tìm công trình/quặng/điểm spawn. VD: unit.locate(building, core, true, @copper, x, y, found, b)");

        // ----- Thế giới (chỉ processor đặc quyền như world-processor) -----
        add("world.getBlock", "lớp, x, y", "getblock {$0} {r} {1} {2}", "Lấy khối/nền/quặng tại ô (lớp: floor, ore, block, building)");
        add("world.setBlock", "lớp, khối, x, y, đội=@derelict, góc=0", "setblock {$0} {1} {2} {3} {4|@derelict} {5|0}", "Đặt khối/nền/quặng vào ô");
        add("world.spawn", "loại, x, y, góc=0, đội=@crux", "spawn {0} {1} {2} {3|0} {4|@crux} {r}", "Tạo lính; trả về lính vừa tạo");
        add("world.applyStatus", "hiệu_ứng, lính, giây", "status false {$0} {1} {2}", "Gán hiệu ứng trạng thái (vd: world.applyStatus(burning, u, 5))");
        add("world.clearStatus", "hiệu_ứng, lính", "status true {$0} {1} 0", "Gỡ hiệu ứng trạng thái");
        add("world.spawnWave", "x, y, tự_nhiên=false", "spawnwave {0} {1} {2|false}", "Gọi một wave tại (x, y)");
        add("world.setRule", "luật, giá_trị, đội=0", "setrule {$0} {1} {2|0} 0 0 0 0", "Đổi luật chơi (waveSpacing, unitCap, buildSpeed, ...)");
        add("world.setMapArea", "x, y, rộng, cao", "setrule mapArea 0 0 {0} {1} {2} {3}", "Giới hạn vùng bản đồ chơi được");
        add("world.message", "kiểu, giây=3", "message {$0} {1|3}", "Hiện thông báo (kiểu: notify, announce, toast, mission)");
        add("world.effect", "kiểu, x, y, góc=0, màu=#ffffff, dữ_liệu=0", "effect {$0} {1} {2} {3|0} {4|%ffffff} {5|0}", "Tạo hiệu ứng hình ảnh");
        add("world.explosion", "đội, x, y, bán_kính, sát_thương, trên_không=true, mặt_đất=true, xuyên=false",
            "explosion {0} {1} {2} {3} {4} {5|true} {6|true} {7|false}", "Tạo vụ nổ gây sát thương");
        add("world.fetch", "kiểu, đội=@sharded, số=0, thêm=@conveyor", "fetch {$0} {r} {1|@sharded} {2|0} {3|@conveyor}",
            "Lấy thông tin (kiểu: unitCount, playerCount, coreCount, buildCount, unit, player, core, build)");
        add("world.getFlag", "cờ", "getflag {r} {0}", "Đọc cờ toàn cục (chuỗi) -> true/false");
        add("world.setFlag", "cờ, giá_trị", "setflag {0} {1}", "Đặt cờ toàn cục (chuỗi)");
        add("world.setProp", "thuộc_tính, đối_tượng, giá_trị", "setprop {0} {1} {2}", "Đặt thuộc tính, vd world.setProp(@health, u, 100). Viết gọn: u.health = 100");
        add("world.sound", "âm, âm_lượng=1, cao_độ=1", "playsound false {0} {1|1} {2|1} 0 0 0 true", "Phát âm thanh (vd @sfx-pew)");
        add("world.soundAt", "âm, x, y, âm_lượng=1, cao_độ=1", "playsound true {0} {3|1} {4|1} 0 {1} {2} true", "Phát âm thanh tại một vị trí");
        add("world.makeMarker", "kiểu, id, x, y, thay=true", "makemarker {$0} {1} {2} {3} {4|true}", "Tạo marker (shape, text, line, quad...)");
        add("world.setMarker", "thuộc_tính, id, a=0, b=0, c=0", "setmarker {$0} {1} {2|0} {3|0} {4|0}", "Sửa thuộc tính của marker");
        add("world.weather", "thời_tiết, bật", "weatherset {0} {1}", "Bật/tắt thời tiết");
        add("world.weatherActive", "thời_tiết", "weathersense {r} {0}", "Thời tiết có đang hoạt động không");

        // ----- Máy quay -----
        add("cutscene.pan", "x, y, tốc_độ", "cutscene pan {0} {1} {2} 0", "Đưa camera tới (x, y)");
        add("cutscene.zoom", "mức", "cutscene zoom {0} 0 0 0", "Thu phóng camera");
        add("cutscene.stop", "", "cutscene stop 0 0 0 0", "Trả camera về người chơi");
    }

    private Builtins(){}
}