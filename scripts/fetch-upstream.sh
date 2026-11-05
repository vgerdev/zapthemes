#!/usr/bin/env bash
# Fetches the pristine upstream sources the forks are generated from, at the refs pinned in
# UPSTREAM.md, into third_party/ (git-ignored). Sparse, blob-less clones keep this small.
set -euo pipefail

JFACE_REPO=https://github.com/eclipse-platform/eclipse.platform.ui
JFACE_REF=R4_41
# SWT's ControlExample, for zapthemes-examples (same release as the SWT 3.135.0 binaries).
SWT_REPO=https://github.com/eclipse-platform/eclipse.platform.swt
SWT_REF=R4_41
NEBULA_REPO=https://github.com/eclipse/nebula
NEBULA_COMMIT=5d0bcbb9c6e3a118685a5ec91c6f5d8eb7833893
# draw2d (GEF Classic), needed by Nebula Timeline; not on Maven Central.
DRAW2D_URL=https://download.eclipse.org/tools/gef/classic/release/3.29.0/plugins/org.eclipse.draw2d_3.23.100.202608071137.jar
DRAW2D_SHA256=b1dc7fd7afc4c0ecc60b1c8fb98406ff612ade1640483dc50dec404eca1581e1

root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$root/third_party"

fetch() { # dir repo ref sparse-patterns...
    local dir="$1" repo="$2" ref="$3"; shift 3
    if [ ! -d "$dir/.git" ]; then
        git clone --quiet --filter=blob:none --no-checkout "$repo" "$dir"
    fi
    # Nebula's paths exceed Windows' 260-character limit when the checkout is nested deeply.
    git -C "$dir" config core.longpaths true
    git -C "$dir" sparse-checkout set --no-cone "$@"
    git -C "$dir" fetch --quiet --depth 1 origin "$ref"
    git -C "$dir" checkout --quiet --detach FETCH_HEAD
    echo "$(basename "$dir"): $(git -C "$dir" rev-parse HEAD)"
}

fetch "$root/third_party/jface-upstream" "$JFACE_REPO" "$JFACE_REF" \
    '/bundles/org.eclipse.jface/*' '/bundles/org.eclipse.jface.notifications/*' '/examples/org.eclipse.jface.snippets/*'
fetch "$root/third_party/swt-upstream" "$SWT_REPO" "$SWT_REF"     '/examples/org.eclipse.swt.examples/*'
fetch "$root/third_party/nebula-upstream" "$NEBULA_REPO" "$NEBULA_COMMIT" \
    '/widgets/*/*/src/' '/widgets/*/*/src-gen/' '/widgets/*/*/META-INF/' '/widgets/*/*/*/src/' '/widgets/*/*/*/META-INF/'

sha256() { # portable: GNU coreutils on Linux/Windows, shasum on macOS
    if command -v sha256sum > /dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

draw2d="$root/third_party/gef/org.eclipse.draw2d.jar"
if [ ! -f "$draw2d" ] || [ "$(sha256 "$draw2d")" != "$DRAW2D_SHA256" ]; then
    mkdir -p "$(dirname "$draw2d")"
    curl -fsSL -o "$draw2d.part" "$DRAW2D_URL"
    if [ "$(sha256 "$draw2d.part")" != "$DRAW2D_SHA256" ]; then
        echo "draw2d checksum mismatch: $DRAW2D_URL" >&2
        exit 1
    fi
    mv "$draw2d.part" "$draw2d"
fi
echo "draw2d: $DRAW2D_SHA256"
