#!/usr/bin/env bash
set -euo pipefail

APP_NAME="LogicSeek"
VERSION="$1"

IMAGE_DIR="build/jpackage/${APP_NAME}"
APPDIR="build/appimage/${APP_NAME}.AppDir"
OUT_DIR="build/jpackage"

rm -rf "$APPDIR"
mkdir -p "$APPDIR"
cp -r "${IMAGE_DIR}/." "$APPDIR/"

cat > "$APPDIR/AppRun" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
exec "\$HERE/bin/${APP_NAME}" "\$@"
EOF
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Exec=${APP_NAME}
Icon=${APP_NAME}
Categories=Utility;
EOF

cp "$APPDIR/lib/${APP_NAME}.png" "$APPDIR/${APP_NAME}.png"

curl -L -o appimagetool.AppImage \
  https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
chmod +x appimagetool.AppImage

# GitHub-hosted runners have no /dev/fuse, so extract-and-run instead of mounting.
ARCH=x86_64 ./appimagetool.AppImage --appimage-extract-and-run \
  "$APPDIR" "${OUT_DIR}/${APP_NAME}-${VERSION}-x86_64.AppImage"