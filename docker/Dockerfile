# Multi-target Docker build for Lunar.
#
# Layering (each target builds on the previous):
#   build            ubuntu + JDK21 + git/build deps        -> reproducible `./gradlew build/test`
#   ide              FROM build + xvfb + pre-staged GoLand  -> the heavy ~1.2GB IDE layer, cached
#   integration-test FROM ide, entrypoint runs integrationTest
#   vnc              FROM ide + x11vnc/openbox/firefox       -> interactive VNC debug IDE (default)
#
# The repo source is NOT baked in; bind-mount it at /workspace to run gradle (see docker-helper.sh).
# Build a specific target with `docker build --target <name> docker/`.

# ============================================================================
# Stage: build — JVM toolchain only. Enough to run `./gradlew build` / `test`.
# Includes fontconfig + a font because headless editor/inlay unit tests
# throw "Fontconfig head is null" without them.
# ============================================================================
FROM ubuntu:24.04 AS build

RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    git \
    wget \
    curl \
    tar \
    gzip \
    unzip \
    sudo \
    fontconfig \
    fonts-dejavu \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Non-root build/run user (matches host UID 1000 for bind-mount write access).
RUN useradd -m -s /bin/bash lunar && \
    usermod -aG sudo lunar && \
    echo "lunar ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

USER lunar
WORKDIR /workspace
CMD ["./gradlew", "build"]

# ============================================================================
# Stage: ide — adds a virtual X display + the IDE's native render libs, and
# bakes the pre-staged GoLand into its own layer so integration runs and the
# VNC IDE don't re-download the ~1.2GB archive.
# ============================================================================
FROM build AS ide

USER root
RUN apt-get update && apt-get install -y \
    xvfb \
    libxrender1 \
    libxrandr2 \
    libgl1-mesa-dri \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

ENV DISPLAY=:99 \
    IDE_TYPE=GO \
    IDE_VERSION=2026.1.1

# Download and extract the IDE (one cached layer).
RUN mkdir -p /home/lunar/ide && \
    IDE_PRODUCT=$(case "$IDE_TYPE" in \
        GO) echo "goland" ;; \
        IC) echo "ideaIC" ;; \
        *) echo "ideaIC" ;; \
    esac) && \
    DOWNLOAD_PATH=$(case "$IDE_TYPE" in \
        GO) echo "go" ;; \
        *) echo "idea" ;; \
    esac) && \
    wget -q "https://download.jetbrains.com/${DOWNLOAD_PATH}/${IDE_PRODUCT}-${IDE_VERSION}.tar.gz" -O /tmp/ide.tar.gz && \
    tar -xzf /tmp/ide.tar.gz -C /home/lunar/ide --strip-components=1 && \
    rm /tmp/ide.tar.gz && \
    chown -R lunar:lunar /home/lunar/ide

USER lunar
WORKDIR /workspace

# ============================================================================
# Stage: integration-test — runs `./gradlew integrationTest` under Xvfb.
# Bind-mount the repo at /workspace; exit code propagates for CI.
# ============================================================================
FROM ide AS integration-test

USER root
COPY integration-test-entrypoint.sh /usr/local/bin/integration-test-entrypoint.sh
RUN chmod +x /usr/local/bin/integration-test-entrypoint.sh

USER lunar
WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/integration-test-entrypoint.sh"]
CMD ["integrationTest"]

# ============================================================================
# Stage: vnc — interactive VNC desktop running the IDE with the Lunar plugin
# pre-installed. This is the default target (today's debug behavior).
# ============================================================================
FROM ide AS vnc

USER root
RUN apt-get update && apt-get install -y \
    x11vnc \
    openbox \
    menu \
    firefox \
    tigervnc-common \
    && rm -rf /var/lib/apt/lists/*

ENV VNC_PORT=5900 \
    IDE_PORT=9090

RUN mkdir -p /home/lunar/.vnc \
    /home/lunar/.config/openbox && \
    chown -R lunar:lunar /home/lunar

COPY --chown=lunar:lunar openbox-rc.xml /home/lunar/.config/openbox/rc.xml

COPY --chown=lunar:lunar docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

USER lunar

# VNC password setup (default: vncpass, can be overridden)
RUN mkdir -p ~/.vnc && \
    x11vnc -storepasswd "vncpass" ~/.vnc/passwd && \
    chmod 600 ~/.vnc/passwd

# Install plugin into user-local JetBrains plugin directory
COPY --chown=lunar:lunar lunar-plugin.zip /tmp/lunar-plugin.zip
RUN IDE_PRODUCT=$(case "$IDE_TYPE" in \
        GO) echo "GoLand" ;; \
        IC) echo "IdeaIC" ;; \
        IU) echo "IntelliJIdea" ;; \
        *) echo "IdeaIC" ;; \
    esac) && \
    IDE_MAJOR=$(echo "$IDE_VERSION" | cut -d. -f1,2) && \
    PLUGIN_DIR="/home/lunar/.local/share/JetBrains/${IDE_PRODUCT}${IDE_MAJOR}" && \
    mkdir -p "$PLUGIN_DIR" && \
    ([ -s /tmp/lunar-plugin.zip ] && unzip /tmp/lunar-plugin.zip -d "$PLUGIN_DIR" || echo "No plugin found to install")

# Expose ports
EXPOSE 5900 9090

# Set working directory
WORKDIR /home/lunar

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["start"]
