#!/usr/bin/env bash
# Regenerate platform icons from the master artwork.
# Usage: replace src/main/resources/icon.png, then re-run this script.
set -euo pipefail

SRC="src/main/resources/icon.png"
OUT_DIR="packaging/icons"

if [ ! -f "$SRC" ]; then
  echo "Source icon not found: $SRC" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

python3 - "$SRC" "$OUT_DIR" <<'PYEOF'
import sys
from PIL import Image

src, out_dir = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")

if im.width != im.height or im.width not in (512, 1024):
    sys.exit(f"Expected a square 512x512 or 1024x1024 PNG, got {im.width}x{im.height}")

im.resize((256, 256), Image.LANCZOS).save(f"{out_dir}/icon.png")
im.save(f"{out_dir}/icon.ico", sizes=[(16, 16), (32, 32), (48, 48), (256, 256)])
print(f"Wrote {out_dir}/icon.png and {out_dir}/icon.ico from {src} ({im.width}x{im.height})")
PYEOF
