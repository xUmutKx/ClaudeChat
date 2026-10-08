#!/data/data/com.termux/files/usr/bin/bash
# Claude Chat: setup and repair. Run inside Termux. Safe to run again: it skips whatever is already done.
set -e
say() { printf '\n\033[1;33m== %s\033[0m\n' "$1"; }

say "Letting Claude Chat start things in Termux"
mkdir -p ~/.termux
grep -qs '^allow-external-apps *= *true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings 2>/dev/null || true

say "Termux packages"
export DEBIAN_FRONTEND=noninteractive
pkg update -y
pkg install -y proot-distro curl

DISTRO=@DISTRO@
ROOTFS="$PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO"
if [ ! -d "$ROOTFS" ]; then
  say "Installing $DISTRO (a few minutes)"
  proot-distro install "$DISTRO"
fi

say "Claude Code, GitHub CLI and tools inside $DISTRO"
proot-distro login "$DISTRO" -- bash -lc 'export DEBIAN_FRONTEND=noninteractive; if ! command -v claude >/dev/null || ! command -v gh >/dev/null || ! command -v script >/dev/null; then apt-get update && apt-get install -y curl nodejs npm git gh util-linux ca-certificates && (command -v claude >/dev/null || npm install -g @anthropic-ai/claude-code); fi'

say "Starting the Claude Chat connection"
mkdir -p "$ROOTFS/root/claudechat"
echo '@BRIDGE_B64@' | base64 -d > "$ROOTFS/root/claudechat/bridge.js"
termux-wake-lock 2>/dev/null || true
pkill -f 'claudechat/bridge.js' 2>/dev/null || true
nohup proot-distro login "$DISTRO" -- bash -lc 'CC_TOKEN=@TOKEN@ CC_PORT=@PORT@ exec node /root/claudechat/bridge.js' >/dev/null 2>&1 &
sleep 4
say "Done. Go back to Claude Chat: it connects by itself."
