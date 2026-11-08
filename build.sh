#!/system/bin/sh
# 手动 Android 编译链（无 Gradle）：aapt2 -> javac -> d8 -> 打包 -> 签名
#
# 所有工具路径都可以用环境变量覆盖：
#   TC       工具链根目录
#   AAPT2    aapt2 可执行文件
#   JAVAC    javac 可执行文件
#   BT       build-tools 目录（需含 lib/d8.jar、lib/apksigner.jar）
#   AJ       android.jar
# 没设置时按常见位置自动探测。

set -e

PROJ=$(cd "$(dirname "$0")" && pwd)
cd "$PROJ"

msg() { echo "$1"; }
die() { echo "错误：$1" >&2; exit 1; }

# 返回第一个存在的路径
pick() {
    for p in "$@"; do
        if [ -n "$p" ] && [ -e "$p" ]; then
            echo "$p"
            return 0
        fi
    done
    return 1
}

if [ -z "$TC" ]; then
    TC=$(pick "$PROJ/toolchain" "$HOME/toolchain" \
        /data/user/0/com.deepseek.harness/files/DSH/toolchain \
        /sdcard/toolchain) || TC=""
fi
[ -n "$TC" ] && msg "== 工具链根目录：$TC =="

if [ -z "$AAPT2" ]; then
    AAPT2=$(pick "$TC/sysroot/data/data/com.termux/files/usr/bin/aapt2" \
        "$TC/sysroot/usr/bin/aapt2" "$TC/bin/aapt2") || AAPT2=""
fi
if [ -z "$JAVAC" ]; then
    JAVAC=$(pick "$TC/jdk/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/bin/javac" \
        "$TC/jdk/bin/javac") || JAVAC=""
fi
if [ -z "$BT" ]; then
    BT=$(pick "$TC/sdk/build-tools/36.0.0" \
        "$ANDROID_HOME/build-tools/36.0.0") || BT=""
fi
if [ -z "$AJ" ]; then
    AJ=$(pick "$TC/sdk/platforms/android-36/android.jar" \
        "$ANDROID_HOME/platforms/android-36/android.jar") || AJ=""
fi
if [ -z "$SR" ]; then
    SR=$(pick "$TC/sysroot/data/data/com.termux/files/usr/lib" \
        "$TC/sysroot/usr/lib") || SR=""
fi

if [ -z "$AAPT2" ]; then
    die "找不到 aapt2。设 AAPT2=/path/to/aapt2，或用 TC 指定工具链根目录。"
fi
if [ -z "$JAVAC" ]; then
    die "找不到 javac。设 JAVAC=/path/to/javac，或用 TC 指定工具链根目录。"
fi
if [ -z "$BT" ]; then
    die "找不到 build-tools。设 BT=/path/to/build-tools/36.0.0。"
fi
if [ -z "$AJ" ]; then
    die "找不到 android.jar。设 AJ=/path/to/android.jar。"
fi
if [ ! -f "$BT/lib/d8.jar" ]; then
    die "$BT 里没有 lib/d8.jar，build-tools 路径不对。"
fi
if [ ! -f "$BT/lib/apksigner.jar" ]; then
    die "$BT 里没有 lib/apksigner.jar，build-tools 需要 30 以上。"
fi

J=$(cd "$(dirname "$JAVAC")/.." && pwd)

msg "== 路径 =="
msg "  aapt2 : $AAPT2"
msg "  javac : $JAVAC"
msg "  bt    : $BT"
msg "  jar   : $AJ"

if [ -n "$SR" ]; then
    export LD_LIBRARY_PATH="$SR:$J/lib:$J/lib/server"
fi
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PROJ/build/tmp"

rm -rf build
mkdir -p build/gen build/classes build/dex build/tmp

msg "== 1/5 aapt2 compile =="
"$AAPT2" compile --dir res -o build/res.zip

msg "== 2/5 aapt2 link =="
"$AAPT2" link -I "$AJ" --manifest AndroidManifest.xml \
    -o build/base.apk --java build/gen build/res.zip

msg "== 3/5 javac =="
"$JAVAC" -source 11 -target 11 -encoding UTF-8 -nowarn \
    -classpath "$AJ" -d build/classes \
    src/com/dsh/talk/MainActivity.java \
    src/com/dsh/talk/Store.java \
    src/com/dsh/talk/McpServer.java \
    src/com/dsh/talk/VoiceService.java \
    build/gen/com/dsh/talk/R.java

msg "== 4/5 d8 =="
"$J/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$AJ" --min-api 29 --output build/dex \
    build/classes/com/dsh/talk/*.class

msg "== 5/5 package + sign =="
env -u LD_LIBRARY_PATH python3 pack.py build/base.apk build/dex/classes.dex build/unsigned.apk

if [ ! -f debug.keystore ]; then
    msg "== 生成 debug.keystore（正式分发请换成自己的固定密钥）=="
    "$J/bin/keytool" -genkeypair -keystore debug.keystore -alias lab \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname "CN=AudioLab" >/dev/null 2>&1
fi

"$J/bin/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/Talk.apk build/unsigned.apk

"$J/bin/java" -jar "$BT/lib/apksigner.jar" verify build/Talk.apk

msg "== OK =="
ls -la build/Talk.apk