#!/usr/bin/env bash
set -euo pipefail
[[ "$(uname -s)" == Linux && "$PWD" == /srv/davplayer ]] || { echo 'Run in /srv/davplayer on 天翼云'; exit 1; }
image='davplayer-android:36-temurin17.0.20.1-1'
docker run --rm --network host --platform linux/amd64 -v "$PWD:/workspace" -w /workspace "$image" bash -lc '
set -euo pipefail
ndk=/opt/android-sdk-linux/ndk/26.2.11394342
bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
module=/workspace/third_party/media3-ffmpeg/src/main
source=/workspace/cache/ffmpeg-6.0.1
stamp="$(sha256sum "$module/jni/ffmpeg_jni.cc" | cut -d" " -f1)-6.0.1-ndk26.2-api23-audio-v2"
if [[ -f "$source/complete" && "$(cat "$source/complete")" == "$stamp" && -f "$module/jniLibs/arm64-v8a/libffmpegJNI.so" && -f "$module/jniLibs/armeabi-v7a/libffmpegJNI.so" ]]; then exit 0; fi
mkdir -p /workspace/cache "$source"
if [[ ! -f "$source/configure" ]]; then
 curl -fL --retry 2 --max-time 180 https://ffmpeg.org/releases/ffmpeg-6.0.1.tar.xz -o /workspace/cache/ffmpeg-6.0.1.tar.xz
 echo "9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623  /workspace/cache/ffmpeg-6.0.1.tar.xz" | sha256sum -c -
 tar -xJf /workspace/cache/ffmpeg-6.0.1.tar.xz -C "$source" --strip-components=1
fi
cd "$source"
for abi in armeabi-v7a arm64-v8a; do
 [[ ! -f ffbuild/config.mak ]] || make distclean
 if [[ "$abi" == armeabi-v7a ]]; then arch=arm; cpu=armv7-a; triple=armv7a-linux-androideabi; else arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android; fi
 mkdir -p "android-libs/$abi" "$module/jniLibs/$abi"
 options=()
 for decoder in aac ac3 eac3 dca flac mp3 alac opus vorbis mlp truehd pcm_s16le pcm_s24le pcm_s32le pcm_f32le; do options+=("--enable-decoder=$decoder"); done
 ./configure --target-os=android --arch="$arch" --cpu="$cpu" --enable-cross-compile --cc="$bin/${triple}23-clang" --cxx="$bin/${triple}23-clang++" --ar="$bin/llvm-ar" --nm="$bin/llvm-nm" --ranlib="$bin/llvm-ranlib" --strip="$bin/llvm-strip" --libdir="$source/android-libs/$abi" --enable-static --disable-shared --enable-pic --disable-doc --disable-programs --disable-everything --disable-autodetect --disable-network --disable-avdevice --disable-avformat --disable-swscale --disable-postproc --disable-avfilter --disable-symver --disable-v4l2-m2m --disable-vulkan --disable-gpl --disable-nonfree --enable-swresample "${options[@]}"
 make -j2
 make install-libs
 "$bin/${triple}23-clang++" -std=c++11 -shared -fPIC -O2 -static-libstdc++ -I"$source" "$module/jni/ffmpeg_jni.cc" -Wl,--start-group "$source/android-libs/$abi/libswresample.a" "$source/android-libs/$abi/libavcodec.a" "$source/android-libs/$abi/libavutil.a" -Wl,--end-group -llog -landroid -lz -lm -Wl,-Bsymbolic -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -o "$module/jniLibs/$abi/libffmpegJNI.so"
 "$bin/llvm-strip" --strip-unneeded "$module/jniLibs/$abi/libffmpegJNI.so"
done
printf "%s" "$stamp" > "$source/complete"
'
