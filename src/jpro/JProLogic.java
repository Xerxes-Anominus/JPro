package jpro;

import arc.scene.ui.layout.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.logic.LExecutor.*;

import jpro.lang.GfxOps;

/**
 * Đăng ký lệnh mlog riêng của mod: "jdraw <tên> a b c d e f" (do trình biên dịch JPro sinh ra từ
 * Draw / Drawf / Lines / Fill). Đây là lệnh đặc quyền: processor cấp 1 tự bỏ qua nó như mọi lệnh world.
 *
 * Lệnh chỉ GHI thao tác vào bộ đệm của processor; việc vẽ thật nằm ở {@link JDraw}.
 */
public final class JProLogic{
    private JProLogic(){}

    private static boolean inited;

    /** Gọi một lần khi mod khởi động (trước khi bất kỳ mã processor nào được nạp). */
    public static void init(){
        if(inited) return;
        inited = true;

        LAssembler.customParsers.put("jdraw", tokens -> {
            int op = tokens.length > 1 ? GfxOps.idOf(tokens[1]) : -1;
            if(op < 0) return new LStatements.InvalidStatement();
            String[] a = new String[6];
            for(int i = 0; i < 6; i++) a[i] = tokens.length > 2 + i ? tokens[2 + i] : "0";
            return new JDrawStatement(op, a);
        });

        JDraw.init();
    }

    public static class JDrawStatement extends LStatement{
        public int op;
        public String[] a;

        public JDrawStatement(int op, String[] a){
            this.op = op;
            this.a = a;
        }

        @Override
        public void build(Table table){
            table.add("jdraw " + GfxOps.byId(op).name);
        }

        @Override
        public LInstruction build(LAssembler builder){
            LVar[] v = new LVar[6];
            for(int i = 0; i < 6; i++) v[i] = builder.var(a[i]);
            return new JDrawI(GfxOps.byId(op), v);
        }

        @Override
        public boolean privileged(){
            return true;
        }

        @Override
        public String name(){
            return "JDraw";
        }

        @Override
        public void write(StringBuilder b){
            b.append("jdraw ").append(GfxOps.byId(op).name);
            for(String s : a) b.append(' ').append(s);
        }
    }

    public static class JDrawI implements LInstruction{
        final int op, mask;
        final boolean flush, clear;
        final LVar[] v;

        JDrawI(GfxOps.Op o, LVar[] v){
            this.op = o.id;
            this.mask = o.mask;
            this.flush = o.name.equals("flush");
            this.clear = o.name.equals("clear");
            this.v = v;
        }

        @Override
        public void run(LExecutor exec){
            if(!(exec.thisv.building() instanceof JProProcessor.JProBuild b) || !b.isValid()) return;
            if(flush){
                b.drawFlush();
                return;
            }
            if(clear){
                b.drawNext.clear();
                return;
            }
            b.drawNext.add(op, arg(0), arg(1), arg(2), arg(3), arg(4), arg(5));
        }

        /** Giá trị toán hạng; độ dài theo ô (theo mask) đổi sang đơn vị thế giới. */
        private float arg(int i){
            float x = v[i].numf();
            if(Float.isNaN(x) || Float.isInfinite(x)) return 0f;
            return ((mask >> i) & 1) != 0 ? x * Vars.tilesize : x;
        }
    }
}
