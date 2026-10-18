#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify.sh
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/FlowWidgets-debug.apk
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root = Path.cwd()
excluded = {'.git', '.gradle', '.kotlin', '.idea', 'build', 'dist', '__pycache__'}
with ZipFile(root / 'dist/FlowWidgets-source.zip', 'w', ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if path.is_file() and not excluded.intersection(relative.parts) and path.name != 'local.properties':
            archive.write(path, 'FlowWidgets/' + relative.as_posix())
PY
(
  cd dist
  sha256sum FlowWidgets-debug.apk FlowWidgets-source.zip > SHA256SUMS
)

python3 - <<'PY_BUNDLE'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root = Path.cwd()
with ZipFile(root / 'dist/FlowWidgets-entrega.zip', 'w', ZIP_DEFLATED) as archive:
    for name in ['dist/FlowWidgets-debug.apk', 'dist/FlowWidgets-source.zip', 'dist/SHA256SUMS', 'docs/INSTALL.md', 'docs/VALIDATION.md', 'README.md', 'docs/images/FlowWidgets-design.png', 'docs/images/FlowWidgets-adaptive.png', 'docs/ADAPTIVE_WIDGETS.md', 'docs/TYPOGRAPHY.md', 'docs/images/FlowWidgets-Inter.png']:
        file = root / name
        if file.exists():
            archive.write(file, file.name)
PY_BUNDLE
