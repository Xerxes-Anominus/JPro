package jpro.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.pooling.*;

/**
 * Ô soạn mã có tô màu cú pháp (từ khóa, tên hàm, chuỗi, số, chú thích...).
 *
 * Cách làm: vẫn là TextArea thật (con trỏ, bôi đen, bàn phím... giữ nguyên), chỉ thay bước VẼ CHỮ:
 * mỗi dòng được vẽ thành từng đoạn cùng màu, vị trí từng đoạn đo bằng GlyphLayout.
 *
 * Để vị trí khớp với TextArea, ô này luôn đủ rộng cho dòng dài nhất (không tự xuống dòng)
 * và đủ cao cho mọi dòng; hãy đặt nó trong ScrollPane (xem JProEditor).
 */
public class JProTextArea extends TextArea{
    private static final class Run{
        final int line;
        final float x;
        final String text;
        final int rgb;

        Run(int line, float x, String text, int rgb){
            this.line = line;
            this.x = x;
            this.text = text;
            this.rgb = rgb;
        }
    }

    private final TextFieldStyle fieldStyle;
    private final Seq<Run> runs = new Seq<>();
    private final Color tmp = new Color(), saved = new Color();
    private String cachedSrc;
    private Font cachedFont;
    private float longest;
    private ScrollPane scroller;

    public JProTextArea(String text, TextFieldStyle style){
        super(text, style);
        this.fieldStyle = style;
        // nội dung đổi -> tính lại kích thước, và cuộn theo con trỏ
        changed(() -> {
            invalidateHierarchy();
            Core.app.post(this::scrollToCursor);
        });
    }

    /** ScrollPane chứa ô này, để tự cuộn theo con trỏ khi gõ. */
    public void setScroller(ScrollPane pane){
        this.scroller = pane;
    }

    private float padX(){
        Drawable bg = fieldStyle.background;
        return bg == null ? 0f : bg.getLeftWidth() + bg.getRightWidth();
    }

    private float padY(){
        Drawable bg = fieldStyle.background;
        return bg == null ? 0f : bg.getTopHeight() + bg.getBottomHeight();
    }

    private int lineCount(String s){
        int n = 1;
        for(int i = 0; i < s.length(); i++) if(s.charAt(i) == '\n') n++;
        return n;
    }

    // LƯU Ý: constructor của TextField gọi getPrefWidth()/getPrefHeight() NGAY khi khởi tạo
    // (setSize(getPrefWidth(), getPrefHeight())), lúc đó các trường của lớp này (fieldStyle, runs...)
    // chưa được gán -> NullPointerException -> crash game khi mở trình soạn mã. Vì vậy phải kiểm tra null.
    @Override
    public float getPrefWidth(){
        if(fieldStyle == null) return super.getPrefWidth();
        ensure(fieldStyle.font, getText());
        return Math.max(super.getPrefWidth(), longest + padX() + 8f);
    }

    @Override
    public float getPrefHeight(){
        if(fieldStyle == null) return super.getPrefHeight();
        float lh = fieldStyle.font.getLineHeight();
        return Math.max(super.getPrefHeight(), (lineCount(getText()) + 1) * lh + padY());
    }

    private void scrollToCursor(){
        if(scroller == null) return;
        String s = getText();
        int pos = Math.min(getCursorPosition(), s.length());
        int line = 0;
        for(int i = 0; i < pos; i++) if(s.charAt(i) == '\n') line++;
        float lh = fieldStyle.font.getLineHeight();
        Drawable bg = fieldStyle.background;
        float top = getHeight() - (bg == null ? 0f : bg.getTopHeight()) - line * lh;
        scroller.scrollTo(0f, top - lh, 1f, lh);
    }

    /** Tính lại các đoạn màu khi nội dung hoặc font đổi. */
    private void ensure(Font font, String src){
        if(font == cachedFont && src != null && (src == cachedSrc || src.equals(cachedSrc))) return;
        cachedFont = font;
        cachedSrc = src;
        runs.clear();
        longest = 0f;

        boolean markup = font.getData().markupEnabled;
        font.getData().markupEnabled = false; // '[' trong mã không phải là màu của Mindustry
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        layout.setText(font, "|");
        float bar = layout.width; // đo độ rộng tới vị trí i = rộng("phần trước" + "|") - rộng("|")

        int[] colors = Highlighter.colors(src);
        int ls = 0, line = 0;
        while(true){
            int le = src.indexOf('\n', ls);
            boolean last = le < 0;
            if(last) le = src.length();
            if(le > ls){
                layout.setText(font, src.substring(ls, le) + "|");
                longest = Math.max(longest, layout.width - bar);
                int i = ls;
                while(i < le){
                    int c = colors[i];
                    int j = i + 1;
                    while(j < le && colors[j] == c) j++;
                    String seg = src.substring(i, j);
                    if(!seg.trim().isEmpty()){
                        float off = 0f;
                        if(i > ls){
                            layout.setText(font, src.substring(ls, i) + "|");
                            off = layout.width - bar;
                        }
                        runs.add(new Run(line, off, seg, c));
                    }
                    i = j;
                }
            }
            if(last) break;
            ls = le + 1;
            line++;
        }
        Pools.free(layout);
        font.getData().markupEnabled = markup;
    }

    /** Thay cho cách vẽ một màu của TextArea: vẽ từng đoạn theo màu cú pháp. */
    @Override
    protected void drawText(Font font, float x, float y){
        ensure(font, getText());

        boolean markup = font.getData().markupEnabled;
        font.getData().markupEnabled = false;
        saved.set(font.getColor());

        float lh = font.getLineHeight();
        float visibleTop = Core.scene.getHeight() + lh * 2f;
        for(int k = 0; k < runs.size; k++){
            Run r = runs.get(k);
            float cy = y - r.line * lh;
            if(cy < -lh * 2f || cy > visibleTop) continue; // ngoài màn hình thì khỏi vẽ
            font.setColor(tmp.set((r.rgb << 8) | 0xff));
            font.draw(r.text, x + r.x, cy);
        }

        font.setColor(saved);
        font.getData().markupEnabled = markup;
    }
}