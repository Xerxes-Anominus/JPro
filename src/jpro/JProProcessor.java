package jpro;

import arc.func.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.logic.*;

/**
 * Processor nhập mã bằng ngôn ngữ JPro. Mã JPro được biên dịch ra mlog rồi nạp vào processor như bình thường.
 *
 * Có hai cấp độ:
 *  - Cấp 1: như logic processor (khối thường). Dùng lệnh điều khiển, unit, draw lên display...
 *  - Cấp 2: như world processor (đặc quyền, tầm với vô hạn). Thêm world.*, cutscene.*, setprop
 *    và Draw / Drawf / Lines / Fill vẽ thẳng lên thế giới.
 */
public class JProProcessor extends LogicBlock{
    public static final String DEFAULT_SOURCE =
        "// Chương trình JPro. Chạy lặp lại liên tục, giống processor thường.\n" +
        "// Thoát editor là tự lưu và biên dịch; bấm 'Trợ giúp' để xem danh sách hàm.\n" +
        "\n" +
        "once {\n" +
        "    print(\"Xin chào từ JPro!\");\n" +
        "    printFlush(message1);\n" +
        "}\n";

    /** 1 = như logic processor, 2 = như world processor. */
    public final int level;

    public JProProcessor(String name, int level){
        super(name);
        this.level = level;
        buildType = JProBuild::new;

        if(level >= 2){
            privileged = true;
            //LogicBlock chỉ cho sửa processor đặc quyền trong editor bản đồ; khối của mod thì ai cũng sửa được
            destructible = true;
            canOverdrive = false;
        }

        // Đồng bộ mã nguồn JPro tới mọi người chơi (mlog đã được đồng bộ qua cấu hình mặc định)
        config(String.class, (JProBuild build, String src) -> build.source = src);
    }

    /** Khối của mod luôn cho phép cấu hình (LogicBlock mặc định khóa processor đặc quyền ngoài editor). */
    @Override
    public boolean accessible(){
        return true;
    }

    public class JProBuild extends LogicBuild{
        /** Mã nguồn JPro người dùng gõ. */
        public String source = DEFAULT_SOURCE;
        /** Bộ đệm đang vẽ dở (Draw/Fill/Lines/Drawf, chỉ cấp 2). */
        public JDraw.Frame drawNext = new JDraw.Frame();
        /** Khung hình đang hiển thị trên thế giới; thay bằng drawNext mỗi lần Draw.flush(). */
        public JDraw.Frame drawShown = new JDraw.Frame();

        public int level(){
            return level;
        }

        /** Draw.flush(): công bố những gì vừa vẽ. */
        public void drawFlush(){
            JDraw.Frame t = drawShown;
            drawShown = drawNext;
            drawNext = t;
            drawNext.clear();
            if(drawShown.count() > 0) JDraw.show(this);
        }

        @Override
        public void updateCode(String str, boolean keep, Cons<LAssembler> assemble){
            super.updateCode(str, keep, assemble);
            //mã mới: bỏ hình cũ
            drawNext.clear();
            drawShown.clear();
            JDraw.hide(this);
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            JDraw.hide(this);
        }

        /** Cấp 2 liên kết được mọi khối như world processor, kể cả khối của người chơi (LogicBlock mặc định chặn). */
        @Override
        public boolean validLink(Building other){
            if(!privileged) return super.validLink(other);
            return other != null && other.isValid() && !(other instanceof ConstructBuild);
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(Icon.pencil, Styles.cleari, () -> JProEditor.show(this)).size(40);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.str(source);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            String s = read.str();
            source = s == null ? DEFAULT_SOURCE : s;
        }
    }
}
