import jpro.lang.JPro;

public class ExportSyntaxCheck{
    public static void main(String[] args){
        String valid = "var copper = 42; function local(){ return copper; } function add(a, b){ return a + b; }";
        JPro.Result result = JPro.compile(valid, 2);
        if(!result.ok()) throw new AssertionError(result.errors.toString());
        System.out.println("OK function syntax");
    }
}
