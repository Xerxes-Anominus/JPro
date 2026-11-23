import jpro.lang.JPro;
public class ExportSyntaxCheck{
    public static void main(String[] args){
        String valid = "var copper = 42; fn a(){ return copper; } export copper; exportFunc a;";
        JPro.Result r = JPro.compile(valid, 2);
        if(!r.ok()) throw new AssertionError("valid exports rejected: " + r.errors.get(0).getMessage());
        String imports = "import processor1.copper; importFunc processor2.a;";
        r = JPro.compile(imports, 2);
        if(r.ok() || !r.errors.get(0).getMessage().contains("chưa được resolve"))
            throw new AssertionError("imports must clearly require linker context");
        String badAlias = "import core.copper;";
        r = JPro.compile(badAlias, 2);
        if(r.ok() || !r.errors.get(0).getMessage().contains("processor1"))
            throw new AssertionError("invalid alias should be rejected");
        System.out.println("OK export syntax / import syntax / alias validation");
    }
}
