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
proot-distro login "$DISTRO" -- bash -lc 'export DEBIAN_FRONTEND=noninteractive; if ! command -v claude >/dev/null || ! command -v gh >/dev/null || ! command -v script >/dev/null; then apt-get update && apt-get install -y curl xz-utils nodejs npm git gh util-linux ca-certificates && (node -v | grep -qE "^v(2[2-9]|[3-9][0-9])" || (A=$(uname -m); case $A in aarch64) A=arm64;; x86_64) A=x64;; *) A=armv7l;; esac; curl -fsSL https://nodejs.org/dist/v22.12.0/node-v22.12.0-linux-$A.tar.xz | tar -xJ -C /usr/local --strip-components=1 --exclude="*.md" --exclude=LICENSE)) && export UV_USE_IO_URING=0 NODE_OPTIONS=--max-old-space-size=1536 && (command -v claude >/dev/null || { (A=$(uname -m); case $A in aarch64) P=linux-arm64;; *) P=linux-x64;; esac; U=$(curl -fsSL https://registry.npmjs.org/@anthropic-ai/claude-code-$P/latest | grep -o "\"tarball\":\"[^\"]*\"" | head -1 | cut -d"\"" -f4); test -n "$U" && mkdir -p /tmp/ccn && curl -fsSL "$U" | tar -xz -C /tmp/ccn && install -m755 /tmp/ccn/package/claude /usr/local/bin/claude; rm -rf /tmp/ccn) || true; rm -rf /usr/local/lib/node_modules/@anthropic-ai/claude-code /usr/local/lib/node_modules/@anthropic-ai/.claude-code-*; npm install -g --no-audit --no-fund @anthropic-ai/claude-code || npm install -g --no-audit --no-fund --ignore-scripts @anthropic-ai/claude-code; }); fi; command -v claude >/dev/null || exit 1' || { say "Claude Code (claude) is missing. Run this in Termux, then run this setup again: proot-distro login $DISTRO -- npm install -g @anthropic-ai/claude-code"; say "Claude Code (claude) yok. Termux'ta şunu çalıştır, sonra bu kurulumu yeniden çalıştır: proot-distro login $DISTRO -- npm install -g @anthropic-ai/claude-code"; exit 1; }

say "Starting the Claude Chat connection"
mkdir -p "$ROOTFS/root/claudechat"
echo '@BRIDGE_B64@' | base64 -d > "$ROOTFS/root/claudechat/bridge.js"
termux-wake-lock 2>/dev/null || true
pkill -f 'claudechat/bridge.js' 2>/dev/null || true
nohup proot-distro login "$DISTRO" -- bash -lc 'CC_TOKEN=@TOKEN@ CC_PORT=@PORT@ exec node /root/claudechat/bridge.js' >/dev/null 2>&1 &
sleep 4
say "Done. Go back to Claude Chat: it connects by itself. / Bitti. Claude Chat'e dön: kendisi bağlanır."
