import jpro.lang.*;
import java.nio.file.*;

public class T{
    public static void main(String[] a) throws Exception{
        String src = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        JPro.Result r = JPro.compile(src);
        if(!r.ok()){
            for(JProError e : r.errors) System.out.println("LỖI " + e.getMessage());
            return;
        }
        System.out.println("-- " + r.instructions() + " lệnh");
        System.out.print(r.mlog);
    }
}
