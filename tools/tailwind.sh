#!/usr/bin/env bash
# Runs the Tailwind CSS standalone binary (no node/npm), downloading it on first use.
# Usage: tools/tailwind.sh <tailwind args>   e.g. tools/tailwind.sh -i src/main/css/application.css -o target/classes/static/css/application.css --watch
set -euo pipefail

TAILWIND_VERSION="v4.3.3"
DIR="$(cd "$(dirname "$0")" && pwd)"
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS-$ARCH" in
  Linux-x86_64)   TARGET="linux-x64" ;;
  Linux-aarch64)  TARGET="linux-arm64" ;;
  Darwin-arm64)   TARGET="macos-arm64" ;;
  Darwin-x86_64)  TARGET="macos-x64" ;;
  *) echo "Unsupported platform $OS-$ARCH — install tailwindcss manually and put it on PATH as tools/tailwindcss" >&2; exit 1 ;;
esac

BIN="$DIR/tailwindcss-$TAILWIND_VERSION-$TARGET"
if [ ! -x "$BIN" ]; then
  echo "Downloading tailwindcss $TAILWIND_VERSION ($TARGET)..." >&2
  curl -fsSL -o "$BIN" "https://github.com/tailwindlabs/tailwindcss/releases/download/$TAILWIND_VERSION/tailwindcss-$TARGET"
  chmod +x "$BIN"
fi

exec "$BIN" "$@"
