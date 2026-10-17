#!/usr/bin/env bash
set -euo pipefail
if [[ "$(uname -s)" != Linux || "$PWD" != /srv/davplayer ]]; then
  echo 'Run in /srv/davplayer on 天翼云' >&2; exit 1
fi
image='davplayer-android:36-temurin17.0.20.1-1'
docker run --rm --platform linux/amd64 --network host --volume "$PWD:/workspace:ro" --workdir /workspace "$image" bash -lc '
  set -euo pipefail
  export JAVA_HOME=/opt/davplayer-jdk17
  export PATH="$JAVA_HOME/bin:$PATH"
  apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
  tools=/opt/android-sdk-linux/build-tools/35.0.0
  metadata="$($tools/aapt dump badging "$apk")"
  printf "%s\n" "$metadata" | head -n 8
  [[ "$(printf "%s\n" "$metadata" | sed -n "/^sdkVersion:/s/[^0-9]//gp")" == "18" ]]
  if [[ "$apk" == */preview/* ]]; then
    if printf "%s\n" "$metadata" | grep -q "^application-debuggable"; then
      echo "Preview must not be debuggable" >&2; exit 1
    fi
  fi
  signatures="$($tools/apksigner verify --verbose "$apk")"
  printf "%s\n" "$signatures"
  printf "%s\n" "$signatures" | grep -q "Verified using v1 scheme (JAR signing): true"
  python3 verify-native-alignment.py "$apk"
  "$tools/zipalign" -c -P 16 4 "$apk"
  sha256sum "$apk"
' _ "$@"
