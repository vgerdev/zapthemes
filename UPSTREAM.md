# Pinned upstream sources

The forks are generated from pristine upstream sources fetched by `scripts/fetch-upstream.sh`
into `third_party/` (git-ignored). Bump a ref here and in the script together.

| Source | Repository | Ref | Commit | Release |
|---|---|---|---|---|
| JFace | https://github.com/eclipse-platform/eclipse.platform.ui (`bundles/org.eclipse.jface`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | JFace 3.40.0 (Eclipse 4.41, 2026-09) |
| Nebula | https://github.com/eclipse/nebula (`widgets/**`) | `master` | `5d0bcbb9c6e3a118685a5ec91c6f5d8eb7833893` | snapshot 2026-11-04 |
| draw2d (GEF Classic, for Nebula Timeline) | https://download.eclipse.org/tools/gef/classic/release/3.29.0 (p2) | `org.eclipse.draw2d_3.23.100.202608071137.jar` | sha256 `b1dc7fd7afc4c0ecc60b1c8fb98406ff612ade1640483dc50dec404eca1581e1` | GEF Classic 3.29.0 |
| SWT | Maven Central `org.eclipse.platform:org.eclipse.swt.<ws>.<os>.<arch>` | `3.135.0` | — | Eclipse 4.41 |

SWT and draw2d are consumed as published binaries; they are not forked.
