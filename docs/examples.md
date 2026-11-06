# Existing SWT and JFace code on ZapThemes

`zapthemes-examples` runs two pieces of standard Eclipse code on ZapThemes, as an existing
application would be moved over. Neither is edited by hand:

| Program | Upstream (pinned in [UPSTREAM.md](../UPSTREAM.md)) | Size |
|---|---|---|
| SWT **ControlExample** | `eclipse.platform.swt`, `examples/org.eclipse.swt.examples`, tag `R4_41` | 37 files, 650 widget creations, 26 tabs |
| **JFace snippets** | `eclipse.platform.ui`, `examples/org.eclipse.jface.snippets`, tag `R4_41` | 87 files, 262 widget creations, 81 snippets |

## How they are moved over

1. **Codemod.** The build copies the sources and rewrites every `new Button(...)`, `new Table(...)`
   etc. to the `com.zapthemes.swt` proxy (`zapthemes.fork` plugin, the same one the JFace and Nebula
   forks use). The build fails if a themeable widget is still created directly; both report 0.
   `display.getSystemColor(...)` goes through the theme as well (`themeSystemColors()`).
2. **The forked JFace** replaces `org.eclipse.jface` on the class path, and the forked
   notifications and JFace Text replace `org.eclipse.jface.notifications` and `org.eclipse.jface.text`; the snippets use them as is.
3. **A theme without code changes.** Both programs create their own `Display` and never call
   `setTheme`. `-Dzapthemes.theme=<id>` (or `auto`, or the path of a theme file) themes them anyway:
   `zapthemes-themes` registers a `ThemeExtension` whose `initialTheme` reads the property when the
   display's `ThemeManager` is created.

For your own application the same steps apply: run the codemod over your sources once (or change
the imports to `com.zapthemes.swt.widgets` by hand), depend on `zapthemes-jface` instead of
`org.eclipse.jface`, and pick a theme with the property or `ThemeManager.setTheme`.

## Running them

```bash
./gradlew :zapthemes-examples:runExamples -Ptheme=zap-dark   # both, in one window with a theme picker
./gradlew :zapthemes-examples:runControlExample              # ControlExample alone (its own main)
./gradlew :zapthemes-examples:runSnippets                    # the snippets browser alone
```

`-Ptheme` defaults to `auto` (the bundled theme matching the desktop). Each snippet is a program of
its own with its own `Display`, so the browser starts it in a new JVM in the current theme.

## Checks

- `:zapthemes-examples:smokeTest` (CI, every platform) selects all 26 ControlExample tabs in every
  bundled theme: all 3,891 controls are tagged and have the theme's background.
- `:zapthemes-examples:screenshotsSnippets` (CI on Ubuntu; `-Ptheme`, `-Psnippet=<name part>`)
  starts every snippet in its own JVM, checks that every control in its windows is tagged, saves a
  PNG and closes it. Two are skipped because they fail without a theme too:
  - `Snippet060DialogWithURLImageButtons` loads its OK image from an eclipse.org URL that no
    longer exists;
  - `Snippet035TableCursorCellHighlighter` (GTK only) paints its cell cursor before a cell is
    selected and reads `getSelectedCells()[0]`.
  One more is skipped because it does not fit in memory reliably:
  `Snippet047VirtualBigTreeViewer` builds a random model of up to 20,000 × 20,000 nodes, which often
  exceeds the default heap of a CI machine. It runs from the snippets browser.

On Windows the snippet PNGs come out blank (`Control.print` from those short-lived processes); the
tag checks still run there.

## Bundles beyond JFace

Every snippet is included. The two data binding snippets (064, 065) run on the official
`jface.databinding` jar, which works on the forked JFace unchanged; the six notification snippets run
on `zapthemes-jface-notifications` ([Phase 7](phase7-plan.md), step 2). `screenshotsSnippets` saves
their popups as `<snippet>-2.png` next to the main window.

The snippets browser also lists the two demos of `examples/org.eclipse.jface.text.examples`
(`CodeMiningDemo`, `InlinedAnnotationDemo`, groups "text ..."), forked the same way and run on
`zapthemes-jface-text` ([Phase 7](phase7-plan.md), step 3), and Nebula Visualization's
`PureJavaExample` (group "visualization"; the other examples there are workbench views) on
`zapthemes-visualization` (step 5).
