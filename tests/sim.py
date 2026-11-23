import sys, os, math, subprocess, json

class U:
    """Lính giả lập: ctl = @controlled (0 tự do, 1 processor, 2 người chơi, 3 lệnh RTS), ctrl = @controller."""
    n=0
    def __init__(s, ctl=0, ctrl=None, flag=0):
        U.n+=1; s.id=U.n; s.ctl=ctl; s.ctrl=ctrl; s.flag=flag; s.dead=0
    def __repr__(s): return "U%d"%s.id

class World:
    def __init__(s, units): s.units=units; s.idx=0

def eq(a,b):
    if isinstance(a,U) or isinstance(b,U): return a is b
    return a==b or (num(a)==num(b) and not isinstance(a,str) and not isinstance(b,str))

def num(x):
    if x is None: return 0.0
    if isinstance(x,str): return 1.0
    if isinstance(x,U): return 1.0
    return float(x)

def run_mlog(code, mem=None, env=None, maxsteps=100000):
    prog=[l.split() if '"' not in l else None for l in code.strip().split('\n')]
    # tách token có chuỗi
    import shlex
    prog=[shlex.split(l, posix=False) for l in code.strip().split('\n')] if code.strip() else []
    v={'@counter':0,'false':0,'true':1,'null':None,'@this':'@this','@unit':None,'@thisx':10,'@thisy':20}
    v.update(env or {})
    mem = mem if mem is not None else {}
    out=[]
    def val(t):
        if t in v: return v[t]
        if t.startswith('"'): return t[1:-1]
        if t.startswith('@'): return t
        try: return float(t)
        except: return v.get(t)
    world=v.get('__world')
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
            elif f=='equal': r=1.0 if eq(a,b) else 0.0
            elif f=='notEqual': r=0.0 if eq(a,b) else 1.0
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
            t={'always':True,'equal':eq(a,b),'notEqual':not eq(a,b),
               'lessThan':A<B,'lessThanEq':A<=B,'greaterThan':A>B,'greaterThanEq':A>=B}[c]
            if t: v['@counter']=int(ins[1])
        elif o=='print':
            x=val(ins[1]); out.append(("%g"%x) if isinstance(x,float) else str(x))
        elif o=='ubind':
            if ins[1]=='null': v['@unit']=None
            elif world and world.units:
                world.idx%=len(world.units); v['@unit']=world.units[world.idx]; world.idx+=1
            else: v['@unit']=None
        elif o=='sensor':
            obj=val(ins[2]); prop=ins[3]
            if isinstance(obj,U):
                v[ins[1]]={'@controlled':obj.ctl,'@controller':obj.ctrl,'@flag':obj.flag,'@dead':obj.dead,'@id':obj.id}.get(prop)
            else: v[ins[1]]=None
        elif o=='ucontrol':
            u=v.get('@unit')
            if isinstance(u,U):
                if ins[1]=='flag': u.flag=num(val(ins[2]))
                elif ins[1]=='idle': u.ctl=1; u.ctrl='@this'
        elif o=='read': v[ins[1]]=mem.get(int(num(val(ins[3]))),0.0)
        elif o=='write': mem[int(num(val(ins[3])))]=val(ins[1])
        elif o in('end','stop'): break
        else: pass
    return ''.join(out), v, mem

def jpro(src, level=2):
    open('/tmp/s.jpro','w').write(src)
    r=subprocess.run(['java','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-cp',os.environ.get('JPRO_CP','out'),'T','/tmp/s.jpro',str(level)],capture_output=True,text=True)
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
def expect_error(name, src, frag, level=2):
    try:
        jpro(src, level)
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


# ---- hai cấp độ ----
expect_error("cấp 1 không dùng được world.*", "world.setBlock(block, @copper-wall, 1, 1);", "chỉ dùng được ở Processor cấp 2", 1)
expect_error("cấp 1 không dùng được cutscene.*", "cutscene.zoom(2);", "chỉ dùng được ở Processor cấp 2", 1)
expect_error("cấp 1 không dùng được Fill.*", "Fill.circle(5, 5, 2);", "chỉ dùng được ở Processor cấp 2", 1)
expect_error("cấp 1 không dùng được Lines.*", "Lines.line(0, 0, 4, 4);", "chỉ dùng được ở Processor cấp 2", 1)
expect_error("cấp 1 không dùng được gán thuộc tính (setprop)", "unit.health = 1;", "chỉ dùng được ở Processor cấp 2", 1)
expect_error("cấp 1 chặn lệnh đặc quyền trong asm", "asm {\n    setflag \"a\" 1\n}", "chỉ dùng được ở Processor cấp 2", 1)
def ok_compile(name, src, level):
    try:
        jpro(src, level); print("OK  ", name); R.append(True)
    except Exception as e:
        print("FAIL", name, "->", e); R.append(False)
ok_compile("cấp 1 vẫn dùng được unit.*, draw.*, block.*", "unit.move(1, 2); draw.rect(0, 0, 4, 4); drawFlush(display1); turret1.enabled = 0; unit.bind(@poly, 2);", 1)
ok_compile("cấp 2 dùng được world.* và Draw/Fill/Lines/Drawf", "world.setBlock(block, @copper-wall, 1, 1); Draw.color(255, 0, 0); Fill.circle(5, 5, 2); Lines.line(0, 0, 4, 4); Drawf.circles(3, 3, 2); Draw.flush(); u.health = 1;", 2)

# ---- Draw/Fill/Lines -> lệnh jdraw ----
g = jpro("Draw.color(255, 128, 0); Fill.rect(2, 3, 4, 5); Lines.circle(1, 2, 3); Lines.poly(1, 2, 6, 3); Draw.flush();", 2)
exp = ["jdraw color 255 128 0 255 0 0", "jdraw frect 2 3 4 5 0 0", "jdraw lcircle 1 2 3 0 0 0", "jdraw lpoly 1 2 6 3 0 0", "jdraw flush 0 0 0 0 0 0"]
ok = g.strip().split("\n") == exp
print("OK  " if ok else "FAIL", "Draw/Fill/Lines sinh đúng lệnh jdraw", "" if ok else "\n   " + repr(g))
R.append(ok)

# ---- unit.bind(loại, số_lượng) ----
def bind_run(name, units, tickcode, ticks, check_fn):
    code = jpro(tickcode, 1)
    w = World(units)
    env = {'__world': w}
    outs = []
    for t in range(ticks):
        env['@counter'] = 0
        out, vv, _ = run_mlog(code, env=env)
        env.update(vv)
        outs.append(out)
    ok = check_fn(units, outs)
    print("OK  " if ok else "FAIL", name, "" if ok else "\n   %r" % (outs[-3:],))
    R.append(ok)

prog = 'for(int i = 0; i < 6; i++){ if(unit.bind(@poly, 3)){ print(unit.id, ","); } else { print("x,"); } }'
def mk():
    return [U() for _ in range(8)]
us = mk(); us[2].ctl = 1; us[2].ctrl = 'khac'; us[5].ctl = 2; us[5].ctrl = 'nguoichoi'
def chk1(units, outs):
    ids = set()
    for o in outs:
        for t in o.split(','):
            if t and t != 'x': ids.add(int(t))
    mine = [u for u in units if u.ctrl == '@this']
    foreign = {units[2].id, units[5].id}
    return len(ids) == 3 and not (ids & foreign) and len(mine) == 3 and all(u.flag != 0 for u in mine)
bind_run("bind(@poly,3): tối đa 3 lính, bỏ qua lính của processor/người chơi khác", us, prog, 6, chk1)

us = mk()
def chk2(units, outs):
    return all(o.count('x') == 0 for o in outs) and len([u for u in units if u.ctrl == '@this']) == 8
bind_run("bind(@poly,99): lấy hết lính tự do", us, prog.replace(', 3)', ', 99)'), 6, chk2)

us = [U(ctl=1, ctrl='khac') for _ in range(4)]
def chk3(units, outs):
    return all(o.count('x') == 6 for o in outs) and all(u.ctrl == 'khac' for u in units)
bind_run("bind: tất cả lính bị người khác giữ -> trả false, không cướp", us, prog, 3, chk3)

# lính của mình bị processor khác cướp giữa chừng -> được bù bằng lính tự do khác
us = mk()
code = jpro(prog, 1)
w = World(us); env = {'__world': w}
for t in range(4):
    env['@counter'] = 0; _, vv, _ = run_mlog(code, env=env); env.update(vv)
mine0 = [u for u in us if u.ctrl == '@this']
victim = mine0[0]; victim.ctl = 1; victim.ctrl = 'ke_cuop'
seen = set()
for t in range(8):
    env['@counter'] = 0; out, vv, _ = run_mlog(code, env=env); env.update(vv)
    for tk in out.split(','):
        if tk and tk != 'x': seen.add(int(tk))
mine1 = [u for u in us if u.ctrl == '@this']
ok = len(mine1) == 3 and victim not in mine1 and victim.id not in seen
print("OK  " if ok else "FAIL", "bind: lính bị cướp giữa chừng được thay bằng lính khác", "" if ok else "\n   %r %r" % (mine1, seen))
R.append(ok)

# lính chết giữa chừng
us = mk()
code = jpro(prog, 1)
w = World(us); env = {'__world': w}
for t in range(4):
    env['@counter'] = 0; _, vv, _ = run_mlog(code, env=env); env.update(vv)
dead = [u for u in us if u.ctrl == '@this'][0]
w.units.remove(dead); dead.dead = 1; dead.ctl = 0; dead.ctrl = None
for t in range(8):
    env['@counter'] = 0; out, vv, _ = run_mlog(code, env=env); env.update(vv)
mine2 = [u for u in us if u.ctrl == '@this' and u is not dead]
ok = len(mine2) == 3
print("OK  " if ok else "FAIL", "bind: lính chết được bù lính mới", "" if ok else "\n   %r" % (mine2,))
R.append(ok)

print("TỔNG:", sum(1 for r in R if r), "/", len(R))
sys.exit(0 if all(R) else 1)
