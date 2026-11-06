# JFace fork patches

Hand edits to the fork, applied by `forkJfaceSources` after the codemod, in name order, with
`git apply`. They are unified diffs against the **codemodded** upstream (LF line endings), with
paths relative to the source root (`a/org/eclipse/jface/...`).

Keep them minimal: most only add a call into `com.zapthemes.jface.JFaceTheming` next to
upstream code that hard-codes colors, so the area gets a style class the theme can style.

| Patch | What |
|---|---|
| `0001-title-area-banner.patch` | TitleAreaDialog / WizardDialog banner → `banner` (title label also `title`); clipped-message tooltip → `info` |
| `0002-input-dialog-error-text.patch` | InputDialog read-only error text → `plain` |
| `0003-preference-dialog-pane.patch` | PreferenceDialog page area and sash → `pane` |
| `0004-tooltips-and-hovers.patch` | DefaultToolTip (unless its colors were set) and ControlDecoration hovers → `info` |
| `0005-popup-dialog.patch` | PopupDialog (unless a subclass chose its colors) → `popup`, its info text also `muted` |
| `0006-status-line-error.patch` | StatusLine message → `error` while it shows an error |
| `0007-focus-cell.patch` | FocusCellOwnerDrawHighlighter registers its focus cell, so the theme's `focusCellBackground` paints it |
| `0008-font-registry-reuse.patch` | FontRegistry reuses a stale font made from the same data instead of allocating a new one, so repeated zooming and theme switches stay bounded |

## Adding or refreshing a patch

1. `./gradlew :zapthemes-jface:forkJfaceSources` (with the existing patches moved aside when
   refreshing one) to get the codemodded sources in `build/generated/sources/fork/jface/java`.
2. Copy the file to `a/<relative path>` and `b/<relative path>` in a scratch directory and edit
   the `b/` copy.
3. In the scratch directory:
   `git diff --no-index --src-prefix= --dst-prefix= a/<path> b/<path> > <this dir>/NNNN-name.patch`
4. Re-run the build. A patch that no longer applies after an upstream bump fails the build with
   the `git apply` error.
