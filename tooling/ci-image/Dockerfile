# lunar-ci — job image for the `lunar-ci` runner pool (runs-on: lunar-ci).
#
# Every `runs-on: lunar-ci` job runs in this image, as a POD on the job's own VM k3s.
# There is no Docker daemon involved: the glimmer-gitea-runner k8s executor pulls this
# with containerd (internal/app/run/runner_k8s.go — "per task (no dind)") and mounts a
# socket only when the pool sets vm.docker, which lunar-ci does not.
#
# It replaces docker.gitea.com/runner-images:ubuntu-latest, which exists to emulate a
# GitHub Actions host — a multi-GB image of toolchains, pulled onto every fresh VM. Its
# one justification here would have been `node`, for the JS actions; that executor does
# not run `uses:` at all, so node is dead weight too. Everything below is used.
#
# BUILD amd64. The pool is n2-standard-4 (C4 has zero CPUS_PER_VM_FAMILY quota in
# northamerica-northeast1, and the chart's c4a default is arm64). From an arm64 host
# pass --platform=linux/amd64 or the VM cannot run the image at all.
#
#   docker build --platform=linux/amd64 \
#     -t northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:0.1.0 \
#     tooling/ci-image
#   docker push northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:0.1.0
#
# GAR in northamerica-northeast1, NOT registry.internetisalie.net: the VM pulls over its
# own GCP egress, in-region. The fleet LAN registry is reachable — the pool masquerades
# 192.168.2.0/24 over the WireGuard tunnel — but every pull would then cross the home
# uplink. Same region as the pool is the point.
#
# Built by hand, like the runner image itself; there is no CI lane for it. Bump the tag
# in deployment/values/lunar-ci.yaml deliberately.
FROM debian:13-slim

# git: REQUIRED, and not by any step — the executor emits its own checkout preamble that
#   runs INSIDE this container before the first step (`git init` / `git remote add` /
#   `git fetch --depth=1` / `git checkout FETCH_HEAD` into /workspace,
#   runner_k8s.go:675). An image without git cannot even get the source.
# openjdk-21: lunar targets Java 21. Baked because setup-java is an action and actions do
#   not run on this pool. A JDK is a stable, declared, versioned toolchain — unlike a
#   pre-resolved GRADLE_USER_HOME, which would freeze mutable dependency state
#   (modules-2 carries TTLs and absolute paths) into an immutable artifact and go stale
#   silently. Gradle still resolves dependencies at job time.
# fontconfig + fonts-dejavu-core: NOT optional. Any test that initialises an editor
#   colour scheme throws "RuntimeException: Fontconfig head is null" without them, which
#   is most of the headless editor/inlay suite.
# lua5.4 + lua-socket: TestLuaDebugHarness and the rockspec-runtime tests exec the
#   interpreter and open a DBGp TCP socket.
# curl + ca-certificates: the UniTrack ingest step, and TLS for everything Gradle fetches.
#
# Deliberately ABSENT, both verified rather than assumed:
#   nodejs          — only JS actions need it, and `uses:` is a stated non-goal of this
#                     executor, so no action ever runs. It was the last reason to carry
#                     the gitea runner image.
#   build-essential — the standard workflow installs it, but nothing in the suite needs a
#                     C toolchain: LuaCompilerProbeTest is written env-independently
#                     ("without depending on the CI host having (or lacking) a real C
#                     toolchain") and tolerates a null probe. The other build-essential
#                     hits are assertions on user-facing message TEXT, not compiler use.
RUN apt-get update && apt-get install -y --no-install-recommends \
        git \
        openjdk-21-jdk-headless \
        fontconfig fonts-dejavu-core \
        lua5.4 lua-socket \
        curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Two names on purpose. TestLuaDebugHarness resolves the interpreter through the JVM's
# user.home (so ~/bin/lua, matching what tooling/gce-builder's sync wires on the builder
# VM); /usr/local/bin/lua covers anything that just wants it on PATH. Pods run as the
# image's root user, so HOME is /root.
RUN ln -sf /usr/bin/lua5.4 /usr/local/bin/lua \
    && mkdir -p /root/bin \
    && ln -sf /usr/bin/lua5.4 /root/bin/lua

# Warm the font cache once here rather than in every job.
RUN fc-cache -f

# JAVA_HOME for Gradle. Nothing else is put on PATH deliberately: Debian's packages land
# java and lua in default-PATH locations via /usr/bin and /usr/local/bin, so the job does
# not depend on this image's PATH surviving whatever the executor injects.
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Fail the BUILD rather than a job three weeks later if a package rename or a Debian
# point release breaks one of these. Each line is a prerequisite something depends on;
# none is decorative. The lua-socket check in particular catches the case where the
# package installs but the C module is unloadable by this interpreter version.
RUN git --version \
    && java -version \
    && "$JAVA_HOME/bin/java" -version \
    && lua -v \
    && /root/bin/lua -v \
    && fc-match sans >/dev/null \
    && lua -e "require('socket')" \
    && curl --version >/dev/null
