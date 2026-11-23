package jpro;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.meta.*;

import static mindustry.type.ItemStack.*;

public class JProBlocks{
    public static JProProcessor codeProcessor;

    public static void load(){
        codeProcessor = new JProProcessor("code-processor"){{
            requirements(Category.logic, with(Items.copper, 200, Items.lead, 100, Items.graphite, 80, Items.silicon, 120));
            
            buildVisibility = BuildVisibility.debugOnly;
            range = Float.MAX_VALUE;
            instructionsPerTick = 25;      // mặc định; dùng setRate(n) trong mã để tăng
            maxInstructionsPerTick = 500;  // trần giống world-processor
            alwaysUnlocked = true;
        }};
    }
}
