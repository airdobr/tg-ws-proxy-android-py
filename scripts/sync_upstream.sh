#!/bin/sh
# Копирует ядро из Flowseal/tg-ws-proxy (тег или ветка) в app/src/main/python.
# Использование: scripts/sync_upstream.sh [tag]   (по умолчанию последний релиз)
set -e
cd "$(dirname "$0")/.."
TAG="${1:-$(curl -fsSL https://api.github.com/repos/Flowseal/tg-ws-proxy/releases/latest | python3 -c 'import sys,json;print(json.load(sys.stdin)["tag_name"])')}"
TMP="$(mktemp -d)"
git clone -q --depth 1 --branch "$TAG" https://github.com/Flowseal/tg-ws-proxy.git "$TMP/up"
DST=app/src/main/python
rm -rf "$DST/proxy" "$DST/utils"
mkdir -p "$DST/utils"
cp -r "$TMP/up/proxy" "$DST/proxy"
find "$DST/proxy" -name __pycache__ -prune -exec rm -rf {} +
cp "$TMP/up/utils/logging_setup.py" "$DST/utils/"
: > "$DST/utils/__init__.py"
cp "$TMP/up/LICENSE" "$DST/UPSTREAM_LICENSE"
echo "$TAG" > UPSTREAM_VERSION
rm -rf "$TMP"
echo "Upstream: $TAG"
