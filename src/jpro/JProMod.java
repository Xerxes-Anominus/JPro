package jpro;

import arc.util.*;
import mindustry.mod.*;

/** Mod JPro: processor viết mã bằng tay như lập trình, biên dịch ra mlog. */
public class JProMod extends Mod{

    public JProMod(){
        Log.info("[JPro] đã tải");
    }

    @Override
    public void loadContent(){
        JProBlocks.load();
    }

    @Override
    public void init(){
        //đăng ký lệnh mlog riêng trước khi bất kỳ processor nào nạp mã
        JProLogic.init();
    }
}
