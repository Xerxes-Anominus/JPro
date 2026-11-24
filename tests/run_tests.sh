#!/bin/sh
# Biên dịch gói jpro.lang + Highlighter rồi chạy toàn bộ kiểm thử. Cần JDK 17+ (chạy từ thư mục gốc của mod).
set -e
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" src/jpro/lang/*.java src/jpro/ui/Highlighter.java tests/T.java tests/H.java tests/IncludeTest.java
fail=0
java -cp "$OUT" IncludeTest
for f in examples/*.jpro; do
    lv=2
    case "$f" in *cap1*) lv=1;; esac
    if java -Dstdout.encoding=UTF-8 -cp "$OUT" T "$f" $lv | head -1 | grep -q "^LỖI"; then echo "LỖI: $f"; fail=1; else echo "ok  $f (cấp $lv)"; fi
done
java -cp "$OUT" H examples/01_xin_chao.jpro

JPRO_CP="$OUT" python3 tests/sim.py
exit $fail
