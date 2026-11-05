# Phase 0 — Findings

Date: 2026-11-04. Everything below was verified on Windows 11 x86_64 with Zulu JDK 25 unless it
says otherwise.

## Results

| Spike | Result |
|---|---|
| Gradle 9.1.0 wrapper + JDK 25 toolchain | ✅ Builds with the configuration cache on. Convention plugins are written in Java (`build-logic`), not `kotlin-dsl`, to avoid Kotlin/JDK 25 target issues. |
| SWT resolution | ✅ SWT 3.135.0 (Eclipse 4.41). Its POM picks the fragment with Maven OS profiles, which Gradle ignores. `zapthemes.swt-platform` excludes `org.eclipse.platform:org.eclipse.swt` everywhere and adds `org.eclipse.swt.<ws>.<os>.<arch>`. Other platforms are selected with `-Pswt.platform=…`. |
| FreeBSD SWT | ❌ **Not published upstream.** It is missing from Maven Central and from the 4.41 drop on download.eclipse.org, and `eclipse.platform.swt/binaries` has no FreeBSD fragment. The plugin uses a local jar (`-Pswt.localJar`, default `/usr/local/share/java/classes/swt.jar` from the `x11-toolkits/swt` port). The CI job is written but commented out. |
| Proxy generator | ✅ Generates 10 proxies by reflection on uninitialized classes, so no natives load. Proxies keep every public constructor, override `checkSubclass()`, and call `ThemeManager.applyOnCreate`. |
| Immediate apply on creation | ✅ Verified: tag plus colors on creation, tree re-theme of all shells (child dialog shells included), and the retry path for a fragile user subclass. The `SWT.Skin` safety net themes widgets created without a proxy. 50 theme switches leave no font leak. |
| Codemod | ✅ Verified on JFace `ButtonFactory`, `Window` and `ToolTip`. The output is byte-identical to upstream except the rewritten type names (the test asserts this). Handles explicit and wildcard imports, local classes that shadow SWT names, anonymous subclasses, and `Widget::new` references. |
| Audit | ✅ Flags raw creations; `// zapthemes:allow-raw` exempts a line. |
| Demo smoke test | ✅ `:zapthemes-demo:smokeTest` opens a real window built from proxies, switches to dark, checks every control, and exits 0. |
| Linux / macOS / FreeBSD runs | ⏳ Not run. `.github/workflows/build.yml` covers Windows, Ubuntu x86_64 and arm64 (Xvfb), and macOS. |

## JFace (R4_41 = 3.40.0)

- 421 source files. Dependencies: `org.eclipse.core.commands` 3.13.0, `org.eclipse.equinox.common`
  3.21.0 and `org.eclipse.equinox.bidi`. The POM also lists `org.eclipse.osgi`; check in Phase 2
  whether it is needed without OSGi.
- **196 raw widget creations in 78 files.** The biggest counts are Composite 38, Label 38,
  Button 15, Text 13, MenuItem 9, ToolItem 9 and Menu 7. Run `./gradlew :zapthemes-tools:auditJfaceUpstream`
  to repeat the count.
- **New in recent JFace:** `Dialog.createButton` and others now build widgets through
  `org.eclipse.jface.widgets.*Factory`, e.g. `ButtonFactory`:
  `(Composite parent) -> new Button(parent, style)`. These lambdas are ordinary `new` expressions,
  so the codemod handles them, and a large part of JFace is themed by rewriting about 20 factory
  classes.
- The count includes non-visual `Widget`s (`DragSource`, `DropTarget`, `Tracker`). Phase 1 will
  exclude them from the audit, since there is nothing to theme.

## Nebula

- The repository is **`github.com/eclipse/nebula`**. The plan had `eclipse-nebula/nebula`, which
  doesn't exist; the plan is now fixed.
- Every requested widget is there. Source layout:
  - `widgets/<name>/org.eclipse.nebula.widgets.<name>/src`
  - Opal widgets (Breadcrumb, CheckboxGroup, DualList, Header, RangeSlider, StarRating, plus
    SwitchButton, TitledSeparator, RoundedToolbar, TextAssist, …) are under
    `widgets/opal/<name>/org.eclipse.nebula.widgets.opal.<name>/src`.
  - BadgedLabel, Carousel, Chips, ProgressCircle, SegmentedBar and the Opal widgets depend on
    `opal.commons`. CDateTime depends on `org.eclipse.nebula.cwt`.
- Released vs. incubation is not visible in the folder layout; it comes from the Nebula features and
  update sites. Phase 4 maps it.
- Dependencies beyond SWT/JFace:

  | Widget | Extra dependency | Proposed handling |
  |---|---|---|
  | CDateTime, CTreeCombo, Pagination | `org.eclipse.core.runtime`, but only `Assert`, `IStatus`, `ListenerList`, … which live in `equinox.common` | Compile against `equinox.common`; no OSGi runtime |
  | FormattedText, DateChooser, RadioGroup | `core.databinding` / `jface.databinding`, only in `*ObservableValue` classes | Use the official databinding jars from Maven Central (they work with the drop-in JFace fork), or leave those classes out |
  | RadioGroup | `org.eclipse.ui.forms`, only in `RadioGroupFormToolkit` | Leave that one class out (keeps the core RCP-free) |
  | DateChooser | imports `org.eclipse.swt.internal` | Check usage in Phase 5 |
  | **Timeline** | **draw2d (GEF) + EMF ecore**; 35 of its 48 files use them. EMF is on Maven Central (2.43.0); **draw2d is not** | **Decision needed** — see below |

## Platform dark-mode hooks (from SWT 3.135.0 sources)

- **win32** (`Display.setData`): `org.eclipse.swt.internal.win32.useDarkModeExplorerTheme`,
  `useShellTitleColoring`, `menuBarForegroundColor` / `menuBarBackgroundColor` / `menuBarBorderColor`,
  `Combo.useDarkTheme`, `Text.useDarkThemeIcons`, `Label.disabledForegroundColor`,
  `Table.headerLineColor`, `ProgressBar.useColors`, `all.use_WS_BORDER`.
- **gtk**: `Display.setData("org.eclipse.swt.internal.gtk.css", css)`, and the system property
  `org.eclipse.swt.internal.gtk.theme` (a `:dark` suffix selects the dark variant).
- **cocoa**: `org.eclipse.swt.internal.cocoa.OS.setTheme(boolean)` and `OS.isSystemDarkAppearance()`.
- None of these are exercised yet. They go into `PlatformThemeSupport` in Phase 3.

## Deviations from the plan

- `third_party/` is **fetched** by `scripts/fetch-upstream.sh` at the refs pinned in `UPSTREAM.md`,
  instead of a committed subtree and submodule. The repository stays small and the result is equally
  reproducible.
- The proxy generator uses string templates rather than JavaPoet; that is enough for the
  constructor-only proxies.
- JavaParser's `LexicalPreservingPrinter` (3.28.2) silently failed to print the changed types, so
  the codemod edits the type's exact source range instead.

## Risks discovered

- **macOS tests:** SWT must run on thread 0 (`-XstartOnFirstThread`), but Gradle test workers don't
  run tests on thread 0. On macOS, SWT tests may need a custom launcher (a `JavaExec` that runs the
  JUnit console launcher on the main thread). Not checked yet.
- **FreeBSD:** the ports SWT version can lag behind 3.135.0. Proxies are generated from whichever
  SWT is on the classpath, so an older jar changes the API surface.

## Decisions (2026-11-04)

1. **Timeline:** fetch draw2d from the **GEF Classic p2 update site** and fork and theme Timeline
   like the other widgets. EMF ecore comes from Maven Central. The fetch step is added in Phase 5.
2. **FreeBSD:** two supported routes, both unverified (no FreeBSD machine yet):
   - **Native:** a FreeBSD JDK plus the ports SWT jar (`x11-toolkits/swt`) via `-Pswt.localJar`.
   - **Linux compatibility layer:** run a *Linux* JDK 25 under FreeBSD's Linux compatibility layer,
     with Linux GTK3 installed in `/compat/linux`, and use the Linux SWT
     (`-Pswt.platform=gtk.linux.x86_64`). This needs no build changes, because SWT's JNI libraries
     are Linux ELF and load into a Linux JVM. The trade-offs: the app runs as a Linux process, the
     GTK3 packages for the compatibility layer are thinner, and aarch64 support there is less
     mature.
3. **Data binding:** left out for now. The `*ObservableValue` classes in FormattedText,
   DateChooser and RadioGroup are excluded from the forks. Data binding for all controls will be
   revisited as a later phase.
