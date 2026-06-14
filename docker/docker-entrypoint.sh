#!/bin/bash
set -e

COMMAND="${1:-start}"

# --- IDE first-run bypass (so the container boots straight into the project) ----------------
ide_config_dir() {
    local prod
    case "${IDE_TYPE:-GO}" in GO) prod="GoLand";; IC) prod="IdeaIC";; IU) prod="IntelliJIdea";; *) prod="GoLand";; esac
    echo "${prod}$(echo "${IDE_VERSION:-2026.1}" | cut -d. -f1,2)"
}

# Pre-accept the dialogs that otherwise block an unattended boot. NOT seedable here: the
# commercial license — the License/Register dialog still needs a one-time trial/license
# activation (it's account-bound), so it can't be baked into the image. See README.
seed_ide_first_run() {
    local cfg="$HOME/.config/JetBrains/$(ide_config_dir)"
    local eula="${LUNAR_EULA_VERSION:-2.0}"
    local project="/home/lunar/test"

    # EULA: the accepted EUA version is a java.util.prefs value compared by MAJOR version
    # (com.intellij.ide.gdpr.EndUserAgreement). Bump LUNAR_EULA_VERSION if JetBrains raises the
    # EULA major and it starts prompting again.
    cat > /tmp/SeedEula.java <<'JAVA'
import java.util.prefs.Preferences;
public class SeedEula { public static void main(String[] a) throws Exception {
    Preferences p = Preferences.userRoot().node("jetbrains").node("privacy_policy");
    p.put("eua_accepted_version", System.getProperty("eua", "2.0")); p.flush();
} }
JAVA
    "${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/java" "-Deua=$eula" /tmp/SeedEula.java >/dev/null 2>&1 && rm -f /tmp/SeedEula.java

    # Data-sharing consents: pre-answered (declined), versions matched to the bundled consents.json.
    mkdir -p "$HOME/.local/share/JetBrains/consentOptions"
    local ts; ts="$(date +%s)000"
    cat > "$HOME/.local/share/JetBrains/consentOptions/accepted" <<EOF
rsch.send.usage.stat:1.1:0:$ts
eap:2021.2:0:$ts
ai.data.collection.and.use.policy:1.0:0:$ts
ea.auto.report:1.0:0:$ts
EOF

    # Project trust: pre-trust the mounted test project so "Trust Project?" doesn't prompt.
    mkdir -p "$cfg/options"
    cat > "$cfg/options/trusted-paths.xml" <<EOF
<application>
  <component name="Trusted.Paths">
    <option name="TRUSTED_PROJECT_PATHS">
      <map>
        <entry key="$project" value="true" />
      </map>
    </option>
  </component>
  <component name="Trusted.Paths.Settings">
    <option name="TRUSTED_PATHS">
      <list>
        <option value="$(dirname "$project")" />
      </list>
    </option>
  </component>
</application>
EOF
    echo "[✓] Seeded first-run bypass (EULA v$eula, data-sharing declined, trusted: $project)"
}

case "$COMMAND" in
    start)
        echo "=== Lunar IDE Docker Container ==="
        echo "Display: $DISPLAY"
        echo "VNC Port: $VNC_PORT"
        
        # Start Xvfb. Clear any stale lock/socket left by a previous boot first: on
        # `docker start`/`docker restart` the writable layer is reused, so a leftover
        # /tmp/.X<n>-lock makes the new Xvfb fail to claim the display and exit, which
        # (via `wait $XVFB_PID` below) tears the whole container down within seconds.
        DISPLAY_NUM="${DISPLAY#:}"
        rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}" 2>/dev/null || true
        echo "[*] Starting Xvfb (virtual X display)..."
        Xvfb ${DISPLAY} -screen 0 1920x1080x24 &
        XVFB_PID=$!
        sleep 2
        
        # Start window manager
        echo "[*] Starting Openbox (window manager)..."
        openbox --replace &
        OPENBOX_PID=$!
        sleep 1
        
        # Start VNC server
        echo "[*] Starting x11vnc server..."
        x11vnc -display ${DISPLAY} \
            -forever \
            -nopw \
            -listen 0.0.0.0 \
            -rfbport ${VNC_PORT} \
            -rfbauth /home/lunar/.vnc/passwd &
        VNC_PID=$!
        sleep 2
        
        echo "[✓] VNC Server ready at 0.0.0.0:${VNC_PORT}"
        
        # Detect IDE executable (supports multiple IDE types)
        IDE_BIN=""
        for exe in goland pycharm idea clion rider jetbrains-gateway webstorm phpstorm datagrip rubymine android-studio aqua dataspell; do
            if [ -f "/home/lunar/ide/bin/${exe}.sh" ]; then
                IDE_BIN="/home/lunar/ide/bin/${exe}.sh"
                break
            fi
        done
        
        # Start IDE if found
        if [ -n "$IDE_BIN" ]; then
            echo "[*] Starting IDE ($(basename $IDE_BIN))..."

            # Custom <PRODUCT>_VM_OPTIONS = bundled defaults (preserve heap/GC) + the
            # consent-confirmation kill switch (belt-and-suspenders with the seeded consent file),
            # plus the JDWP agent when LUNAR_DEBUG=1.
            PROD=$(basename "$IDE_BIN" .sh)
            CUSTOM_VMOPTS="/home/lunar/lunar.vmoptions"
            {
                [ -f "/home/lunar/ide/bin/${PROD}64.vmoptions" ] && cat "/home/lunar/ide/bin/${PROD}64.vmoptions"
                echo "-Djb.consents.confirmation.enabled=false"
                [ -n "${LUNAR_DEBUG:-}" ] && echo "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${JDWP_PORT:-5005}"
            } > "$CUSTOM_VMOPTS"
            export "$(echo "$PROD" | tr '[:lower:]' '[:upper:]')_VM_OPTIONS=$CUSTOM_VMOPTS"
            [ -n "${LUNAR_DEBUG:-}" ] && echo "[*] JDWP enabled on ${JDWP_PORT:-5005} (attach: docker exec -it lunar-ide jdb -attach localhost:${JDWP_PORT:-5005})"

            # A persisted-config mount (LUNAR_PERSIST_CONFIG) can arrive root-owned (a fresh docker
            # named volume); make it writable by the IDE user before the IDE/seed touch it.
            sudo chown -R "$(id -u):$(id -g)" "$HOME/.config/JetBrains" 2>/dev/null || true

            # Pre-accept EULA / data-sharing / project-trust so startup isn't blocked.
            seed_ide_first_run

            # Open test project if it exists
            if [ -d "/home/lunar/test" ]; then
                echo "[*] Opening test project: /home/lunar/test"
                $IDE_BIN /home/lunar/test &
            else
                $IDE_BIN &
            fi

            IDE_PID=$!
            sleep 5
            echo "[✓] IDE started (PID: $IDE_PID)"

            # Stable log path (req 7): symlink the active IDE log to /home/lunar/logs/idea.log
            mkdir -p /home/lunar/logs
            IDE_LOG_DIR=$(ls -d /home/lunar/.cache/JetBrains/*/log 2>/dev/null | head -1)
            if [ -n "$IDE_LOG_DIR" ]; then
                ln -sfn "$IDE_LOG_DIR/idea.log" /home/lunar/logs/idea.log
                echo "[✓] Log linked: /home/lunar/logs/idea.log -> $IDE_LOG_DIR/idea.log"
            fi
        else
            echo "[!] IDE not found. Use 'docker cp' to copy IDE installation."
            echo "[!] Expected executable in: /home/lunar/ide/bin/*.sh"
        fi
        
        # Setup cleanup
        cleanup() {
            echo "[*] Shutting down..."
            kill $XVFB_PID 2>/dev/null || true
            kill $VNC_PID 2>/dev/null || true
            kill $OPENBOX_PID 2>/dev/null || true
            [ -n "$IDE_PID" ] && kill $IDE_PID 2>/dev/null || true
        }
        trap cleanup EXIT INT TERM
        
        echo "[✓] Container running. Connect via VNC at localhost:${VNC_PORT}"
        echo "[*] Press Ctrl+C to shutdown"
        
        # Keep running
        wait $XVFB_PID
        ;;
        
    bash)
        /bin/bash
        ;;
        
    *)
        echo "Usage: $0 {start|bash}"
        exit 1
        ;;
esac
