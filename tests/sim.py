import sys, os, math, subprocess, json

def run_mlog(code, mem=None, env=None, maxsteps=100000):
    prog=[l.split() if '"' not in l else None for l in code.strip().split('\n')]
    # tách token có chuỗi
    import shlex
    prog=[shlex.split(l, posix=False) for l in code.strip().split('\n')] if code.strip() else []
    v={'@counter':0,'false':0,'true':1,'null':None}
    v.update(env or {})
    mem = mem if mem is not None else {}
    out=[]
    def val(t):
        if t in v: return v[t]
        if t.startswith('"'): return t[1:-1]
        if t.startswith('@'): return t
        try: return float(t)
        except: return v.get(t)
    def num(x):
        if x is None: return 0.0
        if isinstance(x,str): return 1.0
        return float(x)
    steps=0
    while True:
        pc=int(v['@counter'])
        if pc>=len(prog) or pc<0: break
        ins=prog[pc]; v['@counter']=pc+1; steps+=1
        if steps>maxsteps: raise Exception("quá nhiều bước")
        o=ins[0]
        if o=='set':
            if ins[1]=='@counter': v['@counter']=int(num(val(ins[2])))
            else: v[ins[1]]=val(ins[2])
        elif o=='op':
            f=ins[1]; a=val(ins[3]); b=val(ins[4]) if len(ins)>4 else 0
            A,B=num(a),num(b)
            if f=='add': r=A+B
            elif f=='sub': r=A-B
            elif f=='mul': r=A*B
            elif f=='div': r=A/B if B else None
            elif f=='idiv': r=math.floor(A/B) if B else None
            elif f=='mod': r=math.fmod(A,B)
            elif f=='pow': r=A**B
            elif f=='equal': r=1.0 if (a==b or A==B and not isinstance(a,str)) else 0.0
            elif f=='notEqual': r=0.0 if (a==b or A==B and not isinstance(a,str)) else 1.0
            elif f=='land': r=1.0 if A!=0 and B!=0 else 0.0
            elif f=='lessThan': r=1.0 if A<B else 0.0
            elif f=='lessThanEq': r=1.0 if A<=B else 0.0
            elif f=='greaterThan': r=1.0 if A>B else 0.0
            elif f=='greaterThanEq': r=1.0 if A>=B else 0.0
            elif f=='shl': r=float(int(A)<<int(B))
            elif f=='shr': r=float(int(A)>>int(B))
            elif f=='or': r=float(int(A)|int(B))
            elif f=='and': r=float(int(A)&int(B))
            elif f=='xor': r=float(int(A)^int(B))
            elif f=='not': r=float(~int(A))
            elif f=='max': r=max(A,B)
            elif f=='min': r=min(A,B)
            elif f=='abs': r=abs(A)
            elif f=='floor': r=math.floor(A)
            elif f=='sqrt': r=math.sqrt(A)
            elif f=='len': r=math.hypot(A,B)
            else: raise Exception("op chưa hỗ trợ "+f)
            v[ins[2]]=r
        elif o=='jump':
            c=ins[2]; a=val(ins[3]) if len(ins)>3 else 0; b=val(ins[4]) if len(ins)>4 else 0
            A,B=num(a),num(b)
            t={'always':True,'equal':(a==b or (A==B and not isinstance(a,str))),'notEqual':not(a==b or (A==B and not isinstance(a,str))),
               'lessThan':A<B,'lessThanEq':A<=B,'greaterThan':A>B,'greaterThanEq':A>=B}[c]
            if t: v['@counter']=int(ins[1])
        elif o=='print':
            x=val(ins[1]); out.append(("%g"%x) if isinstance(x,float) else str(x))
        elif o=='read': v[ins[1]]=mem.get(int(num(val(ins[3]))),0.0)
        elif o=='write': mem[int(num(val(ins[3])))]=val(ins[1])
        elif o in('end','stop'): break
        else: pass
    return ''.join(out), v, mem

def jpro(src):
    open('/tmp/s.jpro','w').write(src)
    r=subprocess.run(['java','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-cp',os.environ.get('JPRO_CP','out'),'T','/tmp/s.jpro'],capture_output=True,text=True)
    o=r.stdout.split('\n',1)
    if o[0].startswith('LỖI'): raise Exception(o[0])
    return o[1] if len(o)>1 else ''

def check(name, src, expect, **kw):
    code=jpro(src)
    out,v,mem=run_mlog(code, **kw)
    ok = out==expect
    print(("OK  " if ok else "FAIL"), name, "" if ok else f"\n   mong đợi {expect!r}\n   nhận được {out!r}")
    return ok

R=[]
# ---- cú pháp Java ----
R.append(check("hàm có kiểu, lồng nhau + return", '''
double sq(double x){ return x*x; }
double hyp(double a, double b){ return sqrt(sq(a)+sq(b)); }
print(hyp(3,4), " ", sq(5));''', "5 25"))
R.append(check("for kiểu Java, giảm dần", '''
for(int i = 10; i >= 0; i -= 3){ print(i, ","); }
for(int j = 0; j < 7; j += 3){ print(j, ";"); }''', "10,7,4,1,0;3;6;"))
R.append(check("while + break + continue", '''
int i = 0;
int s = 0;
while(true){
    i++;
    if(i > 10){ break; }
    if(i % 2 == 0){ continue; }
    s += i;
}
print(s);''', "25"))
R.append(check("thân một câu lệnh, không { }", '''
int t = 0;
for(int k = 1; k <= 4; k++) t += k;
if(t == 10) print("muoi"); else print("khac");''', "muoi"))
R.append(check("switch mũi tên", '''
for(int n = 0; n < 5; n++){
    switch(n){
        case 1, 2 -> print("a");
        case 3 -> { print("b"); }
        default -> print("-");
    }
}''', "-aab-"))
R.append(check("switch cổ điển (gộp case rỗng, break)", '''
for(int n = 0; n < 6; n++){
    switch(n){
        case 0:
        case 1:
            print("x");
            break;
        case 2:
            print("y");
            break;
        default:
            print("-");
    }
}''', "xxy---"))
R.append(check("break trong switch chỉ thoát switch, không thoát vòng for", '''
for(int n = 0; n < 3; n++){
    switch(n){
        case 1:
            if(n == 1) break;
            print("không bao giờ");
            break;
        default:
            print("d");
    }
    print(n);
}''', "d01d2"))
R.append(check("continue trong switch nhảy tới vòng for", '''
for(int n = 0; n < 3; n++){
    switch(n){
        case 1: continue;
        default: print("d");
    }
    print(n);
}''', "d0d2"))
R.append(check("ternary + toán tử", '''
int a = 7;
print(a > 5 ? "lớn" : "nhỏ", " ", 2 ** 3 ** 1, " ", -a + 1, " ", a & 3, " ", a << 2);''', "lớn 8 -6 3 28"))
R.append(check("bộ nhớ cell1[i] +=", '''
cell1[2] = 5;
cell1[2] += 10;
cell1[3] = cell1[2] * 2;
print(cell1[2], " ", cell1[3]);''', "15 30"))
R.append(check("once chỉ chạy 1 lần", '''
once { int n = 0; }
n++;
print(n);''', "1"))
R.append(check("biến cục bộ hàm không đè biến toàn cục", '''
int x = 100;
int f(int a){ int x = a * 2; return x; }
print(f(4), " ", x);''', "8 100"))
R.append(check("final gấp thành hằng", '''
final int A = 3;
final int B = A * 4 + 1;
print(B);''', "13"))
R.append(check("final có giá trị lúc chạy: đọc được", '''
int base = 6;
final int d = base * 2;
print(d);''', "12"))
R.append(check("ngắn mạch && ||", '''
int a = 0;
String r = "";
if(a != 0 && 10 / a > 1){ r = "sai"; } else { r = "ok"; }
if(a == 0 || 10 / a > 1){ r = "ok!"; }
print(r);''', "ok!"))
# ---- cách viết cũ vẫn chạy (có ;) ----
R.append(check("cú pháp cũ: let / const / fn / for in", '''
const K = 2;
fn nhan(x){ return x * K; }
let tong = 0;
for i in 0..4 { tong += nhan(i); }
print(tong);''', "12"))

# ---- lỗi phải được báo đúng ----
def expect_error(name, src, frag):
    try:
        jpro(src)
        print("FAIL", name, "- lẽ ra phải báo lỗi")
        R.append(False)
    except Exception as e:
        ok = frag in str(e)
        print("OK  " if ok else "FAIL", name, "" if ok else "-> " + str(e))
        R.append(ok)
expect_error("thiếu ;", "int x = 5\nprint(x);", "Thiếu ';'")
expect_error("gán lại final", "final int N = 3;\nN = 4;", "không gán lại")
expect_error("gán lại final lúc chạy", "int b = 1;\nfinal int d = b + 1;\nd = 5;", "biến final")
expect_error("hàm int thiếu return", "int f(int a){ print(a); }", "thiếu câu lệnh return")
expect_error("hàm void trả giá trị", "void g(){ return 1; }", "không được trả về giá trị")
expect_error("fall-through", "int v = 1;\nswitch(v){ case 1: print(1); case 2: print(2); break; }", "fall-through")

print("TỔNG:", sum(1 for r in R if r), "/", len(R))
sys.exit(0 if all(R) else 1)
