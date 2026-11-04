# JFace fork patches

Hand edits to the fork, applied by `forkJfaceSources` after the codemod, in name order, with
`git apply`. They are unified diffs against the **codemodded** upstream (LF line endings), with
paths relative to the source root (`a/org/eclipse/jface/...`).

Keep them minimal: each one only adds a call into `com.zapthemes.jface.JFaceTheming` next to
upstream code that hard-codes colors, so the area gets a style class the theme can style.

| Patch | What |
|---|---|
| `0001-title-area-banner.patch` | TitleAreaDialog / WizardDialog banner → `banner` (title label also `title`); clipped-message tooltip → `info` |
| `0002-input-dialog-error-text.patch` | InputDialog read-only error text → `plain` |
| `0003-preference-dialog-pane.patch` | PreferenceDialog page area and sash → `pane` |

## Adding or refreshing a patch

1. `./gradlew :zapthemes-jface:forkJfaceSources` (with the existing patches moved aside when
   refreshing one) to get the codemodded sources in `build/generated/sources/fork/jface/java`.
2. Copy the file to `a/<relative path>` and `b/<relative path>` in a scratch directory and edit
   the `b/` copy.
3. In the scratch directory:
   `git diff --no-index --src-prefix= --dst-prefix= a/<path> b/<path> > <this dir>/NNNN-name.patch`
4. Re-run the build. A patch that no longer applies after an upstream bump fails the build with
   the `git apply` error.
