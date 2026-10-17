#!/usr/bin/env bash
set -euo pipefail
[[ "$(uname -s)" == Linux && "$PWD" == /srv/davplayer ]] || { echo 'Run in /srv/davplayer on 天翼云'; exit 1; }
docker run --rm --network host --platform linux/amd64 -v "$PWD:/workspace" davplayer-android:36-temurin17.0.20.1-1 bash -lc '
set -euo pipefail
export JAVA_HOME=/opt/davplayer-jdk17
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p /workspace/cache/legacy-sdk
cp -a /opt/android-sdk-linux/licenses /workspace/cache/legacy-sdk/
/opt/android-sdk-linux/cmdline-tools/latest/bin/sdkmanager --sdk_root=/workspace/cache/legacy-sdk "ndk;23.2.8568313"
'
