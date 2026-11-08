#!/system/bin/sh
# 手动 Android 编译链（无 Gradle）：aapt2 -> javac -> d8 -> 打包 -> 签名
set -e

TC=/data/user/0/com.deepseek.harness/files/DSH/toolchain
PROJ=/data/user/0/com.deepseek.harness/files/DSH/talk

SR=$TC/sysroot/data/data/com.termux/files/usr/lib
J=$TC/jdk/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
BT=$TC/sdk/build-tools/36.0.0
AJ=$TC/sdk/platforms/android-36/android.jar
AAPT2=$TC/sysroot/data/data/com.termux/files/usr/bin/aapt2

export LD_LIBRARY_PATH="$SR:$J/lib:$J/lib/server"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PROJ/build/tmp"

cd "$PROJ"
rm -rf build
mkdir -p build/gen build/classes build/dex build/tmp

echo "== 1/5 aapt2 compile =="
"$AAPT2" compile --dir res -o build/res.zip

echo "== 2/5 aapt2 link =="
"$AAPT2" link -I "$AJ" --manifest AndroidManifest.xml \
    -o build/base.apk --java build/gen build/res.zip

echo "== 3/5 javac =="
"$J/bin/javac" -source 11 -target 11 -encoding UTF-8 -nowarn \
    -classpath "$AJ" -d build/classes \
    src/com/dsh/talk/MainActivity.java \
    src/com/dsh/talk/Store.java \
    src/com/dsh/talk/McpServer.java \
    src/com/dsh/talk/VoiceService.java \
    build/gen/com/dsh/talk/R.java

echo "== 4/5 d8 =="
"$J/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$AJ" --min-api 29 --output build/dex \
    build/classes/com/dsh/talk/*.class

echo "== 5/5 package + sign =="
env -u LD_LIBRARY_PATH python3 pack.py build/base.apk build/dex/classes.dex build/unsigned.apk

if [ ! -f debug.keystore ]; then
    "$J/bin/keytool" -genkeypair -keystore debug.keystore -alias lab \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname "CN=AudioLab" >/dev/null 2>&1
fi

"$J/bin/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/Talk.apk build/unsigned.apk

"$J/bin/java" -jar "$BT/lib/apksigner.jar" verify build/Talk.apk

echo "== OK =="
ls -la build/Talk.apk
