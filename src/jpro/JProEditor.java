package jpro;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.blocks.logic.*;

import jpro.lang.*;
import jpro.ui.*;

/** Hộp thoại soạn mã JPro. */
public class JProEditor{

    public static void show(JProProcessor.JProBuild build){
        final BaseDialog dialog = new BaseDialog(Core.bundle.get("jpro.editor.title"));
        final String[] status = {""};

        // Ô soạn mã có tô màu cú pháp, đặt trong ScrollPane để cuộn khi mã dài
        final JProTextArea area = new JProTextArea(build.source, Styles.areaField);
        final ScrollPane pane = new ScrollPane(area, Styles.defaultPane);
        area.setScroller(pane);
        dialog.cont.add(pane).grow();
        dialog.cont.row();
        dialog.cont.label(() -> status[0]).left().growX().wrap().pad(6f);

        dialog.buttons.defaults().size(190f, 64f);
        dialog.buttons.button("@jpro.compile", Icon.ok, () -> {
            String src = area.getText();
            JPro.Result r = JPro.compile(src);
            if(!r.ok()){
                StringBuilder sb = new StringBuilder();
                int shown = 0;
                for(JProError e : r.errors){
                    if(shown++ >= 4){
                        sb.append("[lightgray]... và ").append(r.errors.size() - 4).append(" lỗi khác\n");
                        break;
                    }
                    sb.append("[scarlet]").append(e.getMessage()).append("[]\n");
                }
                status[0] = sb.toString().trim();
                moveCursor(area, src, r.errors.get(0).line, r.errors.get(0).col);
                return;
            }
            build.configure(LogicBlock.compress(r.mlog, build.relativeConnections()));
            build.configure(src);
            status[0] = "[green]Biên dịch xong: " + r.instructions() + " lệnh mlog[]";
        });

        dialog.buttons.button("@jpro.mlog", Icon.copy, () -> {
            JPro.Result r = JPro.compile(area.getText());
            if(!r.ok()){
                status[0] = "[scarlet]" + r.errors.get(0).getMessage() + "[]";
                return;
            }
            Core.app.setClipboardText(r.mlog);
            status[0] = "[accent]Đã chép mã mlog (" + r.instructions() + " lệnh) vào bộ nhớ tạm[]";
        });

        dialog.buttons.button("@jpro.help", Icon.info, JProEditor::showHelp);
        dialog.addCloseButton();
        dialog.show();
    }

    private static void moveCursor(TextArea area, String src, int line, int col){
        int pos = 0, ln = 1;
        while(ln < line){
            int nl = src.indexOf('\n', pos);
            if(nl < 0) break;
            pos = nl + 1;
            ln++;
        }
        pos = Math.min(src.length(), pos + Math.max(0, col - 1));
        area.setCursorPosition(pos);
    }

    /** Danh sách hàm có sẵn, lấy trực tiếp từ bảng Builtins nên luôn đúng với trình biên dịch. */
    public static void showHelp(){
        BaseDialog d = new BaseDialog(Core.bundle.get("jpro.help"));
        d.cont.pane(t -> {
            t.defaults().left().growX().pad(4f);
            t.add(Core.bundle.get("jpro.syntax")).wrap().row();
            String lastGroup = null;
            for(Builtins.Builtin b : Builtins.all()){
                String key = b.key;
                int dot = key.indexOf('.');
                String group = dot < 0 ? "chung" : key.substring(0, dot);
                if(!group.equals(lastGroup)){
                    t.add("[accent]== " + group + " ==").padTop(12f).row();
                    lastGroup = group;
                }
                t.add("[lightgray]" + b.usage() + "[]\n" + b.doc).wrap().row();
            }
        }).grow();
        d.addCloseButton();
        d.show();
    }
}
