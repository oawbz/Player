# Media3 1.2.1 FFmpeg audio extension

Official unmodified extension sources from https://github.com/androidx/media/tree/1.2.1/libraries/decoder_ffmpeg; hashes in UPSTREAM.sha256. Apache 2.0 notices retained in each source. The application bundles LGPL FFmpeg 6.0.1 audio only (no GPL/nonfree, network or video decoders); license texts are retained in app/src/main/assets/licenses.

Build with ../.. build-ffmpeg-api18-docker.sh in the dedicated remote Docker environment. Requires official NDK r23c 23.2.8568313 installed into cache/legacy-sdk using sdkmanager. armeabi-v7a targets API18, arm64-v8a targets API21. Both use static C++ runtime and 16 KiB LOAD/RELRO alignment. Outputs and compiler cache are ignored, source and build script are retained.
