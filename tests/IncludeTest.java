import jpro.lang.*;
import java.util.*;

public class IncludeTest{
    public static void main(String[] args){
        String lib1="""
            var shared = 10;
            function calc(x){ var temp = x + shared; return temp; }
            function helper(){ return calc(1); }
        """;
        String lib2="""
            var shared = 20;
            function calc(x){ var temp = x * shared; return temp; }
        """;
        Map<String,String> libs=new LinkedHashMap<>();
        libs.put("processor1",lib1);
        libs.put("processor2",lib2);
        String src="""
            var jproInclude1_shared = 999;
            include processor1;
            include processor1;
            include processor2;
            a = processor1.calc(5);
            b = processor2.calc(5);
            c = processor1.helper();
        """;
        JPro.Result r=JPro.compile(src,2,libs);
        if(!r.ok()) throw new AssertionError(r.errors.get(0).getMessage());
        if(!r.mlog.contains("jproInclude1_2_shared") || !r.mlog.contains("jproInclude2_shared"))
            throw new AssertionError("Included globals were not safely namespaced around a user collision.\n"+r.mlog);
        if(count(r.mlog,"op add jproInclude1_2_calc__temp") != 1)
            throw new AssertionError("Repeated include should only expand once.\n"+r.mlog);
        if(!r.mlog.contains("jproInclude1_2_calc") || !r.mlog.contains("jproInclude2_calc"))
            throw new AssertionError("Included functions were not namespaced.\n"+r.mlog);
        JPro.Result missing=JPro.compile("include processor1;",2);
        if(missing.ok() || !missing.errors.get(0).msg.contains("No linked JPro processor"))
            throw new AssertionError("Missing linked processor should be reported clearly.");
        JPro.Result nested=JPro.compile("if(true){ include processor1; }",2,libs);
        if(nested.ok() || !nested.errors.get(0).msg.contains("only allowed at top level"))
            throw new AssertionError("Nested include must be rejected.");
        JPro.Result nestedAlias=JPro.compile("include processor1;",2,Collections.singletonMap("processor1","function f(){ return processor2.g(); }"));
        if(nestedAlias.ok() || !nestedAlias.errors.get(0).msg.contains("cannot call another processor alias"))
            throw new AssertionError("Nested processor alias calls must be rejected rather than mis-resolved.");
        JPro.Result oldStatic=JPro.compile("staticFunc f(){ return 1; }",2);
        if(oldStatic.ok()) throw new AssertionError("staticFunc is obsolete and should no longer be accepted.");
        JPro.Result namespace=JPro.compile("Nasp math = include(processor1); a = math.calc(5); b = math.shared;",2,Collections.singletonMap("processor1", "var shared = 10; function calc(x){ return x + shared; }"));
        if(!namespace.ok()) throw new AssertionError("Nasp namespace syntax failed: " + namespace.errors.get(0).getMessage());
        if(!namespace.mlog.contains("jproInclude1_calc") || !namespace.mlog.contains("jproInclude1_shared"))
            throw new AssertionError("Nasp member references were not resolved into isolated symbols.\n" + namespace.mlog);
        JPro.Result badNamespace=JPro.compile("Nasp math = include(processor9);",2);
        if(badNamespace.ok() || !badNamespace.errors.get(0).msg.contains("No linked JPro processor"))
            throw new AssertionError("Nasp include should report an unlinked processor.");
        System.out.println("OK includes, Nasp namespace, aliases, collision namespaces, duplicate/missing validation");
    }
    private static int count(String s,String needle){int n=0,p=0;while((p=s.indexOf(needle,p))>=0){n++;p+=needle.length();}return n;}
}
