#!/usr/bin/env bash
set -euo pipefail
remote_workspace=/srv/davplayer
image='davplayer-android:34-temurin17.0.20.1-1'
if [[ "$(uname -s)" != Linux || "$PWD" != "$remote_workspace" ]]; then
  echo "Run this script in the dedicated 天翼云 workspace: $remote_workspace" >&2
  exit 1
fi
command -v docker >/dev/null
if ! docker image inspect "$image" >/dev/null 2>&1; then
  docker build --network host --tag "$image" docker/
fi
cache_dir="${GRADLE_USER_HOME:-$remote_workspace/cache/gradle}"
mkdir -p "$cache_dir" signing
chmod 700 signing
if [[ $# -eq 0 ]]; then set -- assembleDebug; fi
docker run --rm --platform linux/amd64 --network host \
  --volume "$PWD:/workspace" \
  --volume "$cache_dir:/root/.gradle" \
  --volume "$PWD/signing:/root/.android" \
  --env ANDROID_HOME=/opt/android-sdk-linux \
  --env ANDROID_SDK_ROOT=/opt/android-sdk-linux \
  --workdir /workspace "$image" bash -lc '
    set -euo pipefail
    export JAVA_HOME=/opt/davplayer-jdk17
    export PATH="$JAVA_HOME/bin:$PATH"
    java -version 2>&1 | head -n 1 | grep -E "version \"17[.]" >/dev/null || {
      echo "Pinned toolchain requires JDK 17; inspect the image before building" >&2; exit 1;
    }
    test -d "$ANDROID_HOME/platforms/android-34"
    test -x "$ANDROID_HOME/build-tools/34.0.0/aapt"
    if [[ ! -f /root/.android/debug.keystore ]]; then
      keytool -genkeypair -keystore /root/.android/debug.keystore \
        -storepass android -alias androiddebugkey -keypass android \
        -dname "CN=Android Debug,O=DavPlayer,C=CN" \
        -keyalg RSA -keysize 2048 -validity 10000
      chmod 600 /root/.android/debug.keystore
    fi
    ./gradlew --no-daemon "$@"
  ' _ "$@"
