package jpro;

import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.blocks.logic.*;

/**
 * Processor đặc quyền (như world-processor) nhưng nhập mã bằng ngôn ngữ JPro.
 * Mã JPro được biên dịch ra mlog rồi nạp vào processor như bình thường,
 * nên mọi lệnh của processor/world-processor đều dùng được.
 */
public class JProProcessor extends LogicBlock{
    public static final String DEFAULT_SOURCE =
        "// Chương trình JPro. Chạy lặp lại liên tục, giống processor thường.\n" +
        "// Bấm 'Biên dịch' để áp dụng, 'Trợ giúp' để xem danh sách hàm.\n" +
        "\n" +
        "once {\n" +
        "    print(\"Xin chào từ JPro!\")\n" +
        "    printFlush(message1)\n" +
        "}\n";

    public JProProcessor(String name){
        super(name);
        buildType = JProBuild::new;

        // Đồng bộ mã nguồn JPro tới mọi người chơi (mlog đã được đồng bộ qua cấu hình mặc định)
        config(String.class, (JProBuild build, String src) -> build.source = src);
    }

    public class JProBuild extends LogicBuild{
        /** Mã nguồn JPro người dùng gõ. */
        public String source = DEFAULT_SOURCE;

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
