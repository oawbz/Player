#!/usr/bin/env bash
set -euo pipefail
remote_workspace=/srv/davplayer
image='davplayer-android:36-temurin17.0.20.1-1'
if [[ "$(uname -s)" != Linux || "$PWD" != "$remote_workspace" ]]; then
  echo "Run this script in the dedicated 天翼云 workspace: $remote_workspace" >&2
  exit 1
fi
command -v docker >/dev/null
if ! docker image inspect "$image" >/dev/null 2>&1; then
  parent_image='davplayer-android:35-temurin17.0.20.1-1'
  if ! docker image inspect "$parent_image" >/dev/null 2>&1; then
    docker build --network host --tag "$parent_image" docker/
  fi
  docker build --network host --file docker/Dockerfile.sdk36 --tag "$image" docker/
fi
if [[ " $* " == *" -Papi18=true "* ]]; then ./build-ffmpeg-api18-docker.sh; else ./build-ffmpeg-docker.sh; fi
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
    test -d "$ANDROID_HOME/platforms/android-36"
    test -x "$ANDROID_HOME/build-tools/35.0.0/aapt"
    if [[ ! -f /root/.android/debug.keystore ]]; then
      echo "Signing key missing: restore the existing local signing/debug.keystore before building" >&2
      exit 1
    fi
    ./gradlew --no-daemon "$@"
  ' _ "$@"
