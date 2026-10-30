#!/bin/sh
# Install loom-cli for a lunar-ci job (MAINT-40, MAINT-48 design §2.5). Used by the `build` job's
# unit-test store and the `corpus` job's findings store in .gitea/workflows/build-plugin.yml.
#
# This is loom's own "install loom-cli" step (loom/.gitea/workflows/docs-lint.yml), rewritten for
# dash: no pipefail, no `set -e`, and every guard is an explicit `if … exit 1` naming what failed.
# loom-cli is UNPINNED: the newest release is resolved per job. `LOOM_CLI_VERSION` (set as a repo
# variable, which arrives as plain env) installs that tag instead. It is an incident override for a
# loom-cli release that breaks a job, never a standing pin. `LOOM_MODULES_TOKEN` (org secret) arrives
# as plain env too. No release asset on this forge is fetchable anonymously, and a sign-in page comes
# back as HTTP 200, so `tar tzf` proves the download is an archive before it is trusted. The binary
# goes to /usr/local/bin (pods run as root), so later steps find it on PATH. Nothing here touches the
# store. The last command, `loom-cli --version`, records which version ran and is the exit status.
loom_api=https://gitea.internetisalie.net/api/v1/repos/loom/loom-cli
if [ -n "${LOOM_CLI_VERSION:-}" ]; then
  echo "loom-cli ${LOOM_CLI_VERSION} (override)"
else
  LOOM_CLI_VERSION=$(curl -fsSL -H "Authorization: token ${LOOM_MODULES_TOKEN:-}" "${loom_api}/releases/latest" | grep -o '"tag_name":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "${LOOM_CLI_VERSION}" ]; then
    echo "::error::could not resolve loom-cli's latest release from ${loom_api}/releases/latest" >&2
    exit 1
  fi
  echo "loom-cli ${LOOM_CLI_VERSION} (resolved latest)"
fi
loom_dir=$(mktemp -d)
if ! curl -fsSL -o "${loom_dir}/loom-cli.tgz" -H "Authorization: token ${LOOM_MODULES_TOKEN:-}" \
    "https://gitea.internetisalie.net/loom/loom-cli/releases/download/${LOOM_CLI_VERSION}/loom-cli-linux-amd64.tar.gz"; then
  echo "::error::could not download loom-cli ${LOOM_CLI_VERSION}" >&2
  exit 1
fi
if ! tar tzf "${loom_dir}/loom-cli.tgz" > /dev/null; then
  echo "::error::the loom-cli ${LOOM_CLI_VERSION} download is not an archive (a sign-in page?)" >&2
  exit 1
fi
if ! { tar xzf "${loom_dir}/loom-cli.tgz" -C "${loom_dir}" loom-cli && install -m 0755 "${loom_dir}/loom-cli" /usr/local/bin/loom-cli; }; then
  echo "::error::could not install loom-cli ${LOOM_CLI_VERSION}" >&2
  exit 1
fi
loom-cli --version
