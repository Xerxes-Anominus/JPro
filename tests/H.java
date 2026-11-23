import jpro.ui.Highlighter;
import jpro.ui.Highlighter.Span;
import java.nio.file.*;
import java.util.*;

public class H{
    static final String[] NAMES = {"DEFAULT","KEYWORD","CONTROL","TYPE","METHOD","FIELD","CONSTANT","STRING","NUMBER","COMMENT","CONTENT","COLOR"};
    public static void main(String[] a) throws Exception{
        String src = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        if(a.length > 1){
            for(Span s : Highlighter.spans(src))
                System.out.printf("%-9s %s%n", NAMES[s.kind], src.substring(s.start, s.end).replace("\n", "\\n"));
            return;
        }
        // kiểm tra độ bền: cắt cụt và xáo trộn ngẫu nhiên, không được ném lỗi, span phải hợp lệ
        Random rnd = new Random(1);
        int checks = 0;
        for(int t = 0; t < 20000; t++){
            String s = src;
            int cut = rnd.nextInt(s.length() + 1);
            s = s.substring(0, cut);
            if(rnd.nextBoolean() && s.length() > 2){
                char[] cs = s.toCharArray();
                for(int k = 0; k < 3; k++) cs[rnd.nextInt(cs.length)] = "\"@#/*{}()\n.-x0".charAt(rnd.nextInt(14));
                s = new String(cs);
            }
            int prevEnd = 0;
            for(Span sp : Highlighter.spans(s)){
                if(sp.start < prevEnd || sp.end > s.length() || sp.end <= sp.start) throw new RuntimeException("span sai: " + sp.start + ".." + sp.end + " trong " + s.length());
                prevEnd = sp.end;
            }
            if(Highlighter.colors(s).length != s.length()) throw new RuntimeException("độ dài màu sai");
            checks++;
        }
        System.out.println("OK " + checks + " chuỗi ngẫu nhiên, không lỗi");
    }
}
