#!/bin/bash
set -euo pipefail

media3="$(grep -oP '^media3 = "\K[^"]+' gradle/libs.versions.toml)"
work="$RUNNER_TEMP/media3"
module="$work/libraries/decoder_ffmpeg"

git clone --quiet --depth 1 --branch "$media3" https://github.com/androidx/media.git "$work"
git clone --quiet --depth 1 --branch "$FFMPEG_BRANCH" https://github.com/FFmpeg/FFmpeg.git "$module/src/main/jni/ffmpeg"

python3 - "$module/src/main/jni/build_ffmpeg.sh" <<'PY'
import sys
path = sys.argv[1]
script = open(path).read()
cut = script.index("./configure \\\n    --libdir=android-libs/x86 ")
open(path, "w").write(script[:cut])
PY

cat >> "$module/build.gradle.kts" <<KTS

android.ndkVersion = "$NDK_VERSION"
android.defaultConfig.minSdk = $MIN_SDK
android.defaultConfig.ndk.abiFilters += setOf("arm64-v8a", "armeabi-v7a")
KTS

"$module/src/main/jni/build_ffmpeg.sh" \
  "$module/src/main" "$ANDROID_HOME/ndk/$NDK_VERSION" linux-x86_64 "$MIN_SDK" $FFMPEG_DECODERS

(cd "$work" && JAVA_HOME="$MEDIA3_JAVA_HOME" ./gradlew --no-daemon --quiet :lib-decoder-ffmpeg:assembleRelease)

mkdir -p app/libs
cp "$module/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar" app/libs/
