#!/usr/bin/env bash
set -euo pipefail
if [[ "$(uname -s)" != Linux || "$PWD" != /srv/davplayer ]]; then
  echo 'Run in /srv/davplayer on 天翼云' >&2; exit 1
fi
image='ghcr.io/cirruslabs/android-sdk@sha256:8f3fb92fc5c21ee8b9cfe560947ecd0ded39c1b0df7d20d937b8217c8c57c49b'
docker run --rm --platform linux/amd64 --network host --volume "$PWD:/workspace:ro" --workdir /workspace "$image" bash -lc '
  set -euo pipefail
  apk=app/build/outputs/apk/debug/app-debug.apk
  tools=/opt/android-sdk-linux/build-tools/34.0.0
  metadata="$($tools/aapt dump badging "$apk")"
  printf "%s\n" "$metadata" | head -n 8
  [[ "$(printf "%s\n" "$metadata" | sed -n "/^sdkVersion:/s/[^0-9]//gp")" == 18 ]]
  signatures="$($tools/apksigner verify --verbose "$apk")"
  printf "%s\n" "$signatures"
  printf "%s\n" "$signatures" | grep -q "Verified using v1 scheme (JAR signing): true"
  sha256sum "$apk"
'
