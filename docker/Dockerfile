FROM ghcr.io/cirruslabs/android-sdk@sha256:8f3fb92fc5c21ee8b9cfe560947ecd0ded39c1b0df7d20d937b8217c8c57c49b
SHELL ["/bin/bash", "-euo", "pipefail", "-c"]
# The pinned SDK image contains JDK 21. Install a checksum-pinned JDK 17 without changing AGP/Gradle.
RUN curl --fail --location --connect-timeout 20 --max-time 600 --retry 3 --retry-delay 3 \
      'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' \
      --output /tmp/davplayer-jdk17.tar.gz \
    && printf '%s  %s\n' '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e' /tmp/davplayer-jdk17.tar.gz | sha256sum --check - \
    && mkdir -p /opt/davplayer-jdk17 \
    && tar -xzf /tmp/davplayer-jdk17.tar.gz -C /opt/davplayer-jdk17 --strip-components=1 \
    && rm /tmp/davplayer-jdk17.tar.gz
ENV JAVA_HOME=/opt/davplayer-jdk17
ENV PATH="/opt/davplayer-jdk17/bin:${PATH}"
LABEL cn.awbz.davplayer.jdk.version="17.0.20.1+1" \
      cn.awbz.davplayer.jdk.sha256="3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e"
RUN java -version
