#!/usr/bin/env bash
# Fetches the pristine upstream sources the forks are generated from, at the refs pinned in
# UPSTREAM.md, into third_party/ (git-ignored). Sparse, blob-less clones keep this small.
set -euo pipefail

JFACE_REPO=https://github.com/eclipse-platform/eclipse.platform.ui
JFACE_REF=R4_41
NEBULA_REPO=https://github.com/eclipse/nebula
NEBULA_COMMIT=5d0bcbb9c6e3a118685a5ec91c6f5d8eb7833893

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
    '/bundles/org.eclipse.jface/*'
fetch "$root/third_party/nebula-upstream" "$NEBULA_REPO" "$NEBULA_COMMIT" \
    '/widgets/*/*/src/' '/widgets/*/*/META-INF/' '/widgets/*/*/*/src/' '/widgets/*/*/*/META-INF/'
