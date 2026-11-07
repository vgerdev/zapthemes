# ZapThemes

A JSON-themed SWT/JFace library: theme-aware proxy subclasses of every SWT and Nebula control, a
`ThemeManager` that re-themes live UIs, and forks of JFace and Nebula that create their widgets
through the proxies. See [docs/PLAN.md](docs/PLAN.md), [docs/phase0-findings.md](docs/phase0-findings.md) and
[docs/phase1-notes.md](docs/phase1-notes.md), [docs/phase2-notes.md](docs/phase2-notes.md),
[docs/phase3-notes.md](docs/phase3-notes.md), [docs/phase4-notes.md](docs/phase4-notes.md),
[docs/phase5-notes.md](docs/phase5-notes.md) and [docs/phase6-notes.md](docs/phase6-notes.md).

**Using it in an app, and writing themes:** [docs/theme-authoring.md](docs/theme-authoring.md).
Releasing: [docs/releasing.md](docs/releasing.md).

![Native controls next to their canvas replacements, dark theme on Windows](docs/images/zap-dark-native-vs-canvas.png)

## Build

Requires JDK 25 (the Gradle 9.1.0 wrapper is included).

```bash
scripts/fetch-upstream.sh       # pristine JFace/Nebula/SWT-example sources into third_party/
./gradlew build                 # compile, generate proxies, run tests
./gradlew :zapthemes-demo:run             # JFace ApplicationWindow demo (forked JFace)
./gradlew :zapthemes-demo:runStandalone   # plain Display/Shell SWT demo
./gradlew :zapthemes-demo:runNebula       # Nebula released + incubation widgets demo
./gradlew :zapthemes-demo:runRibbon       # Office-style ribbon: galleries, KeyTips, backstage, customization
./gradlew :zapthemes-samples:runHello     # the samples: runHello, runCustomTheme, runCustomWidget, runJFace
./gradlew :zapthemes-examples:runExamples # Eclipse's SWT ControlExample and JFace snippets, on ZapThemes
./gradlew -p apps/zapnotes run            # ZapNotes, an editor-style JFace app on the staged artifacts (publishToStaging first)
./gradlew publishToMavenLocal             # try the library from your own app
```

Themes: `zap-light`, `zap-dark` and `zap-high-contrast` (WCAG AAA text contrast). An application
that never calls `setTheme` can pick one at launch: `-Dzapthemes.theme=zap-dark` (or `auto`, or a
theme file). See [docs/examples.md](docs/examples.md) for existing SWT/JFace code on ZapThemes.

Other platforms: `-Pswt.platform=gtk.linux.aarch64` etc. FreeBSD needs `-Pswt.localJar=<swt.jar>`.
