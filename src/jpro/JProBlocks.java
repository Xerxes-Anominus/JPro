package jpro;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.meta.*;

import static mindustry.type.ItemStack.*;

public class JProBlocks{
    /** Cấp 1: như logic processor. */
    public static JProProcessor codeProcessorLv1;
    /** Cấp 2: như world processor (tên khối giữ nguyên "code-processor" để bản đồ cũ vẫn nạp được). */
    public static JProProcessor codeProcessor;

    public static void load(){
        codeProcessorLv1 = new JProProcessor("code-processor-lv1", 1){{
            requirements(Category.logic, with(Items.copper, 100, Items.lead, 80, Items.graphite, 40, Items.silicon, 60));

            // như logic processor
            instructionsPerTick = 8;
            range = 8 * 22;
            alwaysUnlocked = true;
        }};

        codeProcessor = new JProProcessor("code-processor", 2){{
            requirements(Category.logic, with(Items.copper, 200, Items.lead, 100, Items.graphite, 80, Items.silicon, 120));

            // đặc quyền như world processor: chỉ hiện ở chế độ sandbox / trình chỉnh sửa
            buildVisibility = BuildVisibility.sandboxOnly;
            range = Float.MAX_VALUE;
            instructionsPerTick = 25;      // mặc định; dùng setRate(n) trong mã để tăng
            maxInstructionsPerTick = 500;  // trần giống world-processor
            alwaysUnlocked = true;
        }};
    }
}
