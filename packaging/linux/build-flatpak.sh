#!/usr/bin/env bash
set -euo pipefail

APP_NAME="LogicSeek"
APP_ID="com.derpderp.LogicSeek"
VERSION="$1"

WORKDIR="build/flatpak"
IMAGE_DIR="build/jpackage/${APP_NAME}"

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
cp -r "$IMAGE_DIR" "$WORKDIR/app-image"
cp packaging/flatpak/logicseek.desktop "$WORKDIR/"
cp "packaging/flatpak/${APP_ID}.yml" "$WORKDIR/"

pushd "$WORKDIR" >/dev/null
flatpak-builder --force-clean --user --repo=repo build-dir "${APP_ID}.yml"
flatpak build-bundle repo "${APP_NAME}-${VERSION}-x86_64.flatpak" "${APP_ID}"
popd >/dev/null

cp "${WORKDIR}/${APP_NAME}-${VERSION}-x86_64.flatpak" build/jpackage/