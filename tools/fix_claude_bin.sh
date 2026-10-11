#!/bin/sh
# Waits until the arm64 native package finishes writing, then installs it as bin/claude.exe and checks it runs.
D=/usr/local/lib/node_modules/@anthropic-ai/claude-code
SRC=$D/node_modules/@anthropic-ai/claude-code-linux-arm64/claude
prev=-1; stable=0
while [ $stable -lt 6 ]; do
  s=$(stat -c %s "$SRC" 2>/dev/null || echo 0)
  if [ "$s" = "$prev" ] && [ "$s" -gt 0 ]; then stable=$((stable+1)); else stable=0; fi
  prev=$s; sleep 10
done
echo "stable size=$prev"
cp "$SRC" "$D/bin/claude.exe.new" && chmod 755 "$D/bin/claude.exe.new" && mv "$D/bin/claude.exe.new" "$D/bin/claude.exe"
timeout 30 "$D/bin/claude.exe" --version; echo "version exit=$?"
