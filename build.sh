#!/bin/bash
# 用 Debian 的 aapt v1 + r8 的 d8 + apksigner 手工打 APK
set -e
cd "$(dirname "$0")"

SDK=/root/sdk
PLATFORM=$SDK/plat/android-34/android.jar
D8=$SDK/r8.jar
OUT=build
PKG=提词器.apk

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "==> javac"
javac --release 8 -nowarn -encoding UTF-8 -cp "$PLATFORM" -d "$OUT/classes" \
  src/com/dsh/tp/*.java

echo "==> d8"
java -cp "$D8" com.android.tools.r8.D8 --min-api 24 --lib "$PLATFORM" \
  --release --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "==> aapt package"
aapt package -f -M AndroidManifest.xml -S res -I "$PLATFORM" -F "$OUT/unsigned.apk"

echo "==> aapt add dex"
(cd "$OUT/dex" && aapt add "../unsigned.apk" classes.dex)

echo "==> zipalign"
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f prompt.keystore ]; then
  echo "==> keytool"
  keytool -genkeypair -keystore prompt.keystore -alias prompt -storepass android \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Prompt, O=DSH, C=CN" >/dev/null 2>&1
fi

echo "==> apksigner"
apksigner sign --ks prompt.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias prompt --out "$PKG" "$OUT/aligned.apk"

apksigner verify "$PKG"
ls -l "$PKG"
