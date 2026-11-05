# Pinned upstream sources

The forks are generated from pristine upstream sources fetched by `scripts/fetch-upstream.sh`
into `third_party/` (git-ignored). Bump a ref here and in the script together.

| Source | Repository | Ref | Commit | Release |
|---|---|---|---|---|
| JFace | https://github.com/eclipse-platform/eclipse.platform.ui (`bundles/org.eclipse.jface`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | JFace 3.40.0 (Eclipse 4.41, 2026-09) |
| Nebula | https://github.com/eclipse/nebula (`widgets/**`) | `master` | `5d0bcbb9c6e3a118685a5ec91c6f5d8eb7833893` | snapshot 2026-11-04 |
| SWT | Maven Central `org.eclipse.platform:org.eclipse.swt.<ws>.<os>.<arch>` | `3.135.0` | — | Eclipse 4.41 |

SWT is consumed as published binaries; it is not forked.
