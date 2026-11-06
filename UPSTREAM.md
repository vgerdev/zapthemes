# Pinned upstream sources

The forks are generated from pristine upstream sources fetched by `scripts/fetch-upstream.sh`
into `third_party/` (git-ignored). Bump a ref here and in the script together.

| Source | Repository | Ref | Commit | Release |
|---|---|---|---|---|
| JFace | https://github.com/eclipse-platform/eclipse.platform.ui (`bundles/org.eclipse.jface`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | JFace 3.40.0 (Eclipse 4.41, 2026-09) |
| JFace notifications | same repository (`bundles/org.eclipse.jface.notifications`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | 0.8.100 (Eclipse 4.41) |
| JFace Text | same repository (`bundles/org.eclipse.jface.text`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | 3.31.100 (Eclipse 4.41) |
| org.eclipse.text (built unchanged, see `zapthemes-text`) | same repository (`bundles/org.eclipse.text`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | 3.14.800 (Eclipse 4.41) |
| JFace Text examples (for `zapthemes-examples`) | same repository (`examples/org.eclipse.jface.text.examples`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | Eclipse 4.41 |
| JFace snippets (for `zapthemes-examples`) | same repository (`examples/org.eclipse.jface.snippets`) | tag `R4_41` | `f0ef795ed3148ad7aec4157c78f5ea9c8b7f3380` | Eclipse 4.41 |
| SWT ControlExample (for `zapthemes-examples`) | https://github.com/eclipse-platform/eclipse.platform.swt (`examples/org.eclipse.swt.examples`) | tag `R4_41` | `3e3b24b4691c4f18a7d5e22653fe28d4c47385d7` | SWT 3.135.0 (Eclipse 4.41) |
| Nebula | https://github.com/eclipse/nebula (`widgets/**`) | `master` | `5d0bcbb9c6e3a118685a5ec91c6f5d8eb7833893` | snapshot 2026-11-04 |
| draw2d (GEF Classic, for Nebula Timeline) | https://download.eclipse.org/tools/gef/classic/release/3.29.0 (p2) | `org.eclipse.draw2d_3.23.100.202608071137.jar` | sha256 `b1dc7fd7afc4c0ecc60b1c8fb98406ff612ade1640483dc50dec404eca1581e1` | GEF Classic 3.29.0 |
| SWT | Maven Central `org.eclipse.platform:org.eclipse.swt.<ws>.<os>.<arch>` | `3.135.0` | — | Eclipse 4.41 |
| NatTable (for `zapthemes-nattable`) | Maven Central `org.eclipse.nebula.widgets.nattable:org.eclipse.nebula.widgets.nattable.core` | `2.7.0` | — | NatTable 2.7.0 |

SWT, draw2d and NatTable are consumed as published binaries; they are not forked.
