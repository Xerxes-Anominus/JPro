package jpro;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;

import jpro.lang.GfxOps;

/**
 * Vẽ lên thế giới cho World Processor (Draw, Fill, Lines, Drawf).
 *
 * Mlog chạy trong lúc cập nhật game, còn Draw/Fill/Lines chỉ vẽ được trong bước render. Vì vậy:
 *  1. Các lệnh "jdraw" chỉ GHI LẠI thao tác vào bộ đệm của processor ({@link Frame}).
 *  2. "Draw.flush()" công bố bộ đệm thành khung hình đang hiển thị (giống drawflush của màn hình logic).
 *  3. Mỗi khung hình render, {@link #render()} chạy lại khung hình của mọi processor đang có.
 *
 * Toán hạng độ dài đã được nhân 8 (ô -> đơn vị thế giới) lúc ghi, xem {@link JProLogic}.
 *
 * Hạn chế: processor chỉ chạy ở máy chủ / chơi đơn, nên hình vẽ chỉ hiện trên máy đang chạy processor.
 */
public final class JDraw{
    private JDraw(){}

    /** Mỗi thao tác chiếm 7 số: [mã lệnh, 6 toán hạng]. */
    static final int STRIDE = 7;
    /** Trần số thao tác của một khung hình (chống treo máy khi vòng lặp vẽ vô hạn). */
    static final int MAX_CMDS = 4096;
    /** Lớp vẽ mặc định: nằm trên công trình và lính. */
    static final float DEFAULT_Z = 120f;

    /** Một khung hình: danh sách thao tác vẽ. */
    public static final class Frame{
        float[] data = new float[STRIDE * 64];
        int count;

        public Frame(){}

        public void clear(){
            count = 0;
        }

        public int count(){
            return count;
        }

        void add(int op, float a, float b, float c, float d, float e, float f){
            if(count >= MAX_CMDS) return;
            int at = count * STRIDE;
            if(at + STRIDE > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[at] = op;
            data[at + 1] = a;
            data[at + 2] = b;
            data[at + 3] = c;
            data[at + 4] = d;
            data[at + 5] = e;
            data[at + 6] = f;
            count++;
        }
    }

    private interface Cmd{
        void run(float a, float b, float c, float d, float e, float f);
    }

    /** Processor đang có khung hình để vẽ. */
    private static final Seq<JProProcessor.JProBuild> active = new Seq<>();
    private static final Color cur = new Color(1f, 1f, 1f, 1f);
    private static final Cmd[] handlers = new Cmd[GfxOps.ALL.size()];
    private static boolean inited;

    /** Gọi một lần khi tải mod. */
    public static void init(){
        if(inited) return;
        inited = true;

        h("color", (a, b, c, d, e, f) -> {
            cur.set(a / 255f, b / 255f, c / 255f, d / 255f);
            Draw.color(cur);
        });
        h("alpha", (a, b, c, d, e, f) -> {
            cur.a = a / 255f;
            Draw.color(cur);
        });
        h("z", (a, b, c, d, e, f) -> Draw.z(a));
        h("stroke", (a, b, c, d, e, f) -> Lines.stroke(a));

        // Lines
        h("lline", (a, b, c, d, e, f) -> Lines.line(a, b, c, d));
        h("lrect", (a, b, c, d, e, f) -> Lines.rect(a, b, c, d));
        h("lcircle", (a, b, c, d, e, f) -> Lines.circle(a, b, c));
        h("lpoly", (a, b, c, d, e, f) -> Lines.poly(a, b, sides(c), d, e));
        h("lsquare", (a, b, c, d, e, f) -> Lines.square(a, b, c, d));
        h("larc", (a, b, c, d, e, f) -> Lines.arc(a, b, c, d, e));
        h("ldash", (a, b, c, d, e, f) -> Lines.dashLine(a, b, c, d, Math.max(1, Math.min(64, (int)e))));
        h("ldcircle", (a, b, c, d, e, f) -> Lines.dashCircle(a, b, c));

        // Fill
        // (x, y) là góc dưới trái, giống Lines.rect
        h("frect", (a, b, c, d, e, f) -> Fill.crect(a, b, c, d));
        h("fcircle", (a, b, c, d, e, f) -> Fill.circle(a, b, c));
        h("fpoly", (a, b, c, d, e, f) -> Fill.poly(a, b, sides(c), d, e));
        h("ftri", (a, b, c, d, e, f) -> Fill.tri(a, b, c, d, e, f));
        h("fsquare", (a, b, c, d, e, f) -> Fill.square(a, b, c, d));
        h("farc", (a, b, c, d, e, f) -> Fill.arc(a, b, c, d, e));

        // Drawf: dùng màu hiện tại
        h("dcircles", (a, b, c, d, e, f) -> Drawf.circles(a, b, c, cur));
        h("ddcircle", (a, b, c, d, e, f) -> Drawf.dashCircle(a, b, c, cur));
        h("ddline", (a, b, c, d, e, f) -> Drawf.dashLine(cur, a, b, c, d));
        h("dtarget", (a, b, c, d, e, f) -> Drawf.target(a, b, c, d, cur));
        h("darrow", (a, b, c, d, e, f) -> Drawf.arrow(a, b, c, d, e, f, cur));
        h("dselect", (a, b, c, d, e, f) -> Drawf.select(a, b, c, cur));
        h("dsquare", (a, b, c, d, e, f) -> Drawf.square(a, b, c, d, cur));

        // "flush" và "clear" xử lý ngay lúc ghi (xem JProLogic), không cần vẽ lại.

        Events.run(Trigger.drawOver, JDraw::render);
    }

    private static void h(String name, Cmd cmd){
        int id = GfxOps.idOf(name);
        if(id >= 0) handlers[id] = cmd;
    }

    private static int sides(float v){
        return Math.max(3, Math.min(128, (int)v));
    }

    /** Bắt đầu hiển thị khung hình của processor này. */
    static void show(JProProcessor.JProBuild b){
        if(!active.contains(b, true)) active.add(b);
    }

    /** Ngừng hiển thị (processor bị gỡ, hoặc mã được biên dịch lại). */
    static void hide(JProProcessor.JProBuild b){
        active.remove(b, true);
    }

    private static void render(){
        if(active.isEmpty()) return;
        for(int i = active.size - 1; i >= 0; i--){
            JProProcessor.JProBuild b = active.get(i);
            if(b == null || !b.isValid() || b.drawShown.count == 0){
                active.remove(i);
                continue;
            }
            replay(b.drawShown);
        }
    }

    private static void replay(Frame fr){
        cur.set(1f, 1f, 1f, 1f);
        Draw.color(cur);
        Draw.z(DEFAULT_Z);
        Lines.stroke(1f);

        float[] d = fr.data;
        for(int i = 0; i < fr.count; i++){
            int at = i * STRIDE;
            int op = (int)d[at];
            Cmd cmd = op >= 0 && op < handlers.length ? handlers[op] : null;
            if(cmd != null) cmd.run(d[at + 1], d[at + 2], d[at + 3], d[at + 4], d[at + 5], d[at + 6]);
        }

        Draw.reset();
        Lines.stroke(1f);
    }
}
