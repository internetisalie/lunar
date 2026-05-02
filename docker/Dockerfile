# Multi-stage Docker build for Lunar IDE container
# Stage 1: Base image with runtime dependencies
FROM ubuntu:24.04 AS base

# Install essential runtime packages
RUN apt-get update && apt-get install -y \
    xvfb \
    x11vnc \
    openbox \
    menu \
    sudo \
    firefox \
    git \
    wget \
    curl \
    tar \
    gzip \
    unzip \
    openjdk-21-jdk \
    libxrender1 \
    libxrandr2 \
    libgl1-mesa-dri \
    libglib2.0-0 \
    fontconfig \
    fonts-dejavu \
    tigervnc-common \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -s /bin/bash lunar && \
    usermod -aG sudo lunar && \
    echo "lunar ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

# Stage 2: Runtime image with pre-staged IDE
FROM base

# Set environment variables
ENV DISPLAY=:99 \
    VNC_PORT=5900 \
    IDE_PORT=9090 \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    IDE_TYPE=GO \
    IDE_VERSION=2026.1.1

# Download and extract IDE
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

# Create working directories
RUN mkdir -p /home/lunar/.vnc \
    /home/lunar/.config/openbox && \
    chown -R lunar:lunar /home/lunar

# Copy VNC startup script
COPY --chown=lunar:lunar vnc-start.sh /usr/local/bin/vnc-start.sh
RUN chmod +x /usr/local/bin/vnc-start.sh

# Copy openbox config
COPY --chown=lunar:lunar openbox-rc.xml /home/lunar/.config/openbox/rc.xml

# Copy entrypoint script
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
    ([ -f /tmp/lunar-plugin.zip ] && unzip /tmp/lunar-plugin.zip -d "$PLUGIN_DIR" || echo "No plugin found to install")

# Expose ports
EXPOSE 5900 9090

# Set working directory
WORKDIR /home/lunar

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["start"]