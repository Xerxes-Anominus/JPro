package jpro;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.graphics.Color;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.blocks.logic.*;

import jpro.lang.*;
import jpro.ui.*;

import java.util.regex.*;

/** JPro editor: directly editable TextArea, synchronized line gutter and static variable inspector. */
public class JProEditor{
    private static final Pattern DECL = Pattern.compile("(?m)^\\s*(?:(?:final|const)\\s+)?(?:var|int|double|boolean|String|Unit|[A-Z][A-Za-z0-9_<>]*)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=\\s*([^;]*))?;");
    private static final Pattern FUNC = Pattern.compile("(?m)^\\s*(?:(?:void|int|double|boolean|var|String|Unit|[A-Z][A-Za-z0-9_<>]*)\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{");

    /** Giới hạn cỡ mlog nén của LogicBlock (maxCompressedLen). */
    private static final int MAX_COMPRESSED = 16_000;

    /** Kết quả lưu: lỗi biên dịch (nếu có) và có áp dụng được mlog mới hay không. */
    private static final class Saved{
        final JPro.Result result;
        final String tooLong;

        Saved(JPro.Result result, String tooLong){
            this.result = result;
            this.tooLong = tooLong;
        }

        boolean ok(){
            return result.ok() && tooLong == null;
        }
    }

    public static void show(JProProcessor.JProBuild build){
        show(build, -1, -1);
    }

    /** Mở editor; line/col > 0 thì đưa con trỏ tới vị trí đó (dùng khi quay lại sửa lỗi). */
    public static void show(JProProcessor.JProBuild build, int line, int col){
        final BaseDialog dialog = new BaseDialog(Core.bundle.get("jpro.editor.title") + " - " + Core.bundle.get("jpro.level." + build.level()));
        final String[] status = {""};
        final JProTextArea area = new JProTextArea(build.source, Styles.areaField);
        // Gutter must use the exact same font/line metrics as the TextArea.
        // Label's default font can differ from Styles.areaField and causes line numbers
        // to drift vertically from the code, especially with scaled fonts.
        final Label gutter = new Label("1", new Label.LabelStyle(Styles.areaField.font, Color.gray));
        gutter.setAlignment(Align.top | Align.right);
        gutter.setWrap(false);
        // Match the TextArea's top inset through the table cell (Label has no padTop method).
        final float gutterTopInset = Styles.areaField.background == null ? 0f : Styles.areaField.background.getTopHeight();
        final Table editorRow = new Table();
        editorRow.add(gutter).width(42f).top().padRight(5f).padTop(gutterTopInset);
        editorRow.add(area).grow().top();
        final ScrollPane pane = new ScrollPane(editorRow, Styles.defaultPane);
        area.setScroller(pane);
        Runnable updateGutter = () -> {
            int lines = 1;
            String src = area.getText();
            for(int i = 0; i < src.length(); i++) if(src.charAt(i) == '\n') lines++;
            StringBuilder nums = new StringBuilder(lines * 4);
            for(int n = 1; n <= lines; n++){
                if(n > 1) nums.append('\n');
                nums.append(n);
            }
            gutter.setText(nums.toString());
            editorRow.invalidateHierarchy();
        };
        area.setOnTextChanged(updateGutter);
        updateGutter.run();
        if(line > 0) moveCursor(area, build.source, line, col);
        dialog.cont.add(pane).grow();
        dialog.cont.row();
        dialog.cont.label(() -> status[0]).left().growX().wrap().pad(6f);

        dialog.buttons.defaults().size(150f, 60f);
        dialog.buttons.button("@jpro.compile", Icon.ok, () -> {
            String src = area.getText();
            Saved sv = save(build, src, true);
            if(sv.ok()){
                status[0] = Core.bundle.format("jpro.compile.success", sv.result.instructions());
            }else{
                status[0] = errorSummary(sv);
                if(!sv.result.ok()) moveCursor(area, src, sv.result.errors.get(0).line, sv.result.errors.get(0).col);
            }
        });
        dialog.buttons.button("@jpro.mlog", Icon.copy, () -> {
            JPro.Result r = JPro.compile(area.getText(), build.level());
            if(!r.ok()){
                status[0] = r.errors.get(0).getMessage();
                moveCursor(area, area.getText(), r.errors.get(0).line, r.errors.get(0).col);
                return;
            }
            Core.app.setClipboardText(r.mlog);
            status[0] = Core.bundle.format("jpro.mlog.copied", r.instructions());
        });
        dialog.buttons.button("@jpro.vars", () -> showVars(area.getText()));
        dialog.buttons.button("@jpro.copy", Icon.copy, () -> {
            Core.app.setClipboardText(area.getText());
            status[0] = Core.bundle.get("jpro.copy.success");
        });
        dialog.buttons.button("@jpro.paste", () -> {
            String clip = Core.app.getClipboardText();
            if(clip == null || clip.isEmpty()){
                status[0] = Core.bundle.get("jpro.paste.empty");
                return;
            }
            String src = area.getText();
            area.setText(src + (src.isEmpty() || src.endsWith("\n") ? "" : "\n\n") + clip);
            status[0] = Core.bundle.get("jpro.paste.success");
        });
        dialog.buttons.button("@jpro.export", () -> {
            String exported = extractDeclarations(area.getText());
            if(exported.isEmpty()){
                status[0] = Core.bundle.get("jpro.export.empty");
                return;
            }
            Core.app.setClipboardText(exported);
            status[0] = Core.bundle.get("jpro.export.success");
        });
        dialog.buttons.button("@jpro.clear", () -> {
            BaseDialog confirm = new BaseDialog("@jpro.clear.title");
            confirm.cont.add("@jpro.clear.confirm").wrap().width(360f).pad(8f);
            confirm.buttons.button("@cancel", Icon.cancel, confirm::hide).size(140f, 60f);
            confirm.buttons.button("@jpro.clear", () -> {
                area.setText("");
                status[0] = Core.bundle.get("jpro.clear.success");
                confirm.hide();
            }).size(140f, 60f);
            confirm.show();
        });
        dialog.buttons.button("@jpro.help", Icon.info, () -> showHelp(build.level()));
        dialog.buttons.button("@jpro.saveclose", Icon.ok, dialog::hide);
        dialog.closeOnBack();

        // Thoát editor (nút, phím back...) là tự lưu; lỗi thì hiện hộp thoại lỗi.
        dialog.hidden(() -> autosave(build, area.getText()));
        dialog.show();
    }

    /**
     * Biên dịch rồi áp dụng. Mã nguồn luôn được lưu để không mất chữ đã gõ; mlog chỉ đổi khi biên dịch thành công.
     * @param force true = luôn nạp lại mlog (nút Biên dịch); false = chỉ nạp khi mã nguồn có đổi (tự lưu lúc thoát)
     */
    private static Saved save(JProProcessor.JProBuild build, String src, boolean force){
        JPro.Result r = JPro.compile(src, build.level());
        String tooLong = null;
        boolean changed = !src.equals(build.source);
        if(r.ok() && (force || changed)){
            byte[] bytes = LogicBlock.compress(r.mlog, build.relativeConnections());
            if(bytes.length > MAX_COMPRESSED) tooLong = Core.bundle.format("jpro.error.toolong", MAX_COMPRESSED, bytes.length);
            else build.configure(bytes);
        }
        if(changed) build.configure(src);
        return new Saved(r, tooLong);
    }

    private static void autosave(JProProcessor.JProBuild build, String src){
        if(build == null || !build.isValid()) return;
        Saved sv = save(build, src, false);
        if(!sv.ok()) showErrors(build, src, sv);
    }

    private static String errorSummary(Saved sv){
        if(sv.tooLong != null) return sv.tooLong;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for(JProError e : sv.result.errors){
            if(shown++ >= 4){
                sb.append(Core.bundle.format("jpro.moreerrors", sv.result.errors.size() - 4)).append('\n');
                break;
            }
            sb.append(e.getMessage()).append('\n');
        }
        return sb.toString().trim();
    }

    /** Hộp thoại lỗi sau khi thoát editor: liệt kê lỗi, bấm vào để quay lại đúng dòng. */
    private static void showErrors(JProProcessor.JProBuild build, String src, Saved sv){
        final BaseDialog d = new BaseDialog("@jpro.error.title");
        d.cont.image(Icon.warning).color(Color.scarlet).size(40f).padBottom(4f).row();
        d.cont.add("@jpro.error.saved").wrap().width(480f).center().pad(4f).row();
        d.cont.pane(t -> {
            t.defaults().growX().pad(3f);
            if(sv.tooLong != null){
                t.add("[scarlet]" + sv.tooLong).wrap().left().row();
            }
            for(JProError e : sv.result.errors){
                t.button(b -> {
                    b.left();
                    b.add("[accent]" + Core.bundle.format("jpro.error.at", e.line, e.col) + "[]").left().width(110f).top().pad(4f);
                    b.add(e.msg).wrap().left().growX().pad(4f);
                }, Styles.grayt, () -> {
                    d.hide();
                    show(build, e.line, e.col);
                }).growX().row();
            }
        }).maxHeight(320f).growX();
        d.buttons.defaults().size(180f, 60f);
        d.buttons.button("@jpro.error.fix", Icon.pencil, () -> {
            d.hide();
            if(sv.result.errors.isEmpty()) show(build);
            else show(build, sv.result.errors.get(0).line, sv.result.errors.get(0).col);
        });
        d.buttons.button("@close", Icon.ok, d::hide);
        d.closeOnBack();
        d.show();
    }


    private static void showVars(String src){
        BaseDialog d = new BaseDialog(Core.bundle.get("jpro.vars.title"));
        d.cont.pane(t -> {
            t.defaults().left().growX().pad(4f);
            t.add("[accent]" + Core.bundle.get("jpro.vars.variables") + "[]").row();
            Matcher m = DECL.matcher(src);
            boolean any = false;
            while(m.find()){
                any = true;
                String value = m.group(2);
                t.add("[white]" + m.group(1) + "[]  [lightgray]=" + (value == null ? "(uninitialized)" : value.trim()) + "[]").wrap().row();
            }
            if(!any) t.add(Core.bundle.get("jpro.vars.none")).row();
            t.add("[accent]" + Core.bundle.get("jpro.vars.functions") + "[]").padTop(10f).row();
            Matcher f = FUNC.matcher(src);
            any = false;
            while(f.find()){
                String fn = f.group(1);
                if(fn.equals("if") || fn.equals("while") || fn.equals("for") || fn.equals("switch") || fn.equals("catch") || fn.equals("once") || fn.equals("loop")) continue;
                any = true;
                t.add("[white]" + fn + "[]  [lightgray] (" + f.group(2).trim() + ")[]").wrap().row();
            }
            if(!any) t.add(Core.bundle.get("jpro.vars.nofunctions")).row();
            t.add("[lightgray]" + Core.bundle.get("jpro.vars.staticnote") + "[]").wrap().padTop(10f).row();
        }).grow();
        d.addCloseButton();
        d.show();
    }


    /** Export top-level variable declarations and function definitions as a reusable snippet. */
    private static String extractDeclarations(String src){
        StringBuilder out = new StringBuilder();
        Matcher vars = DECL.matcher(src);
        while(vars.find()) out.append(vars.group()).append('\n');
        Matcher funcs = FUNC.matcher(src);
        while(funcs.find()){
            int open = src.indexOf('{', funcs.start());
            if(open < 0) continue;
            int depth = 0;
            boolean inString = false, escaped = false;
            for(int i = open; i < src.length(); i++){
                char ch = src.charAt(i);
                if(inString){
                    if(escaped) escaped = false;
                    else if(ch == '\\') escaped = true;
                    else if(ch == '"') inString = false;
                    continue;
                }
                if(ch == '"'){ inString = true; continue; }
                if(ch == '{') depth++;
                else if(ch == '}' && --depth == 0){
                    out.append(src, funcs.start(), i + 1).append('\n');
                    break;
                }
            }
        }
        return out.toString().trim();
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

    /** Help text and builtin function documentation. */
    public static void showHelp(int level){
        BaseDialog d = new BaseDialog(Core.bundle.get("jpro.help"));
        d.cont.pane(t -> {
            t.defaults().left().growX().pad(4f);
            t.add(Core.bundle.get("jpro.syntax")).wrap().row();
            t.add(Core.bundle.get("jpro.level.note")).wrap().padTop(6f).row();
            String lastGroup = null;
            for(Builtins.Builtin b : Builtins.all()){
                String key = b.key;
                int dot = key.indexOf('.');
                String groupKey = dot < 0 ? "common" : key.substring(0, dot);
                String groupBundleKey = "jpro.group." + groupKey;
                String group = Core.bundle.has(groupBundleKey) ? Core.bundle.get(groupBundleKey) : groupKey;
                if(!group.equals(lastGroup)){
                    t.add("[accent]== " + group + " ==").padTop(12f).row();
                    lastGroup = group;
                }
                boolean locked = b.world && level < 2;
                String tag = b.world ? "[sky][" + Core.bundle.get("jpro.level.2short") + "][] " : "";
                t.add((locked ? "[gray]" : "[lightgray]") + tag + b.usage() + "[]\n" + (locked ? "[gray]" + b.doc + "[]" : b.doc)).wrap().row();
            }
        }).grow();
        d.addCloseButton();
        d.show();
    }
}
