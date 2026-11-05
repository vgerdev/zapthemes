# ZapThemes — SWT/JFace Theming Library: Implementation Plan

## 1. Goals

- A pure SWT/JFace theming library (no Eclipse RCP, no OSGi runtime required) that works in a plain
  `Display`/`Shell` app or a JFace `ApplicationWindow`.
- **Strategy 1 — subclass proxies.** For every SWT and Nebula control there is a proxy class with the
  same simple name in a parallel package. For example, `com.zapthemes.swt.widgets.Button` extends
  `org.eclipse.swt.widgets.Button`. Each proxy constructor:
  1. calls `super(...)`
  2. calls `setData("THEME_CONTROL_TYPE", "Button")`
  3. applies the current theme through the `ThemeManager`.

  This happens **immediately, inside the constructor**.
- JFace and Nebula (released and incubation) are **forked as source**. A codemod rewrites every
  control instantiation inside them to use the proxies.
- A `ThemeManager` holds the current theme. On theme change it does a tree scan of every `Shell` and
  re-styles each control according to `getData("THEME_CONTROL_TYPE")`.
- Themes are JSON files, read and written with **GSON**.
- Targets Windows, Linux (GTK), macOS (Cocoa) and FreeBSD (GTK).
- Native controls that ignore colors are handled by owner-draw renderers. Where a canvas-based control
  exists (for example Nebula Grid instead of Table), it is offered as the themeable replacement.
- NatTable and Nebula Visualization come in later, optional phases.

### Why Strategy 1 and not Strategy 2

Strategy 2 would rename `Composite` to `CompositeOriginal` and add `Composite extends CompositeOriginal`.
That breaks the type hierarchy. `Shell`, `Group`, `Canvas` and every Nebula widget would still extend
`CompositeOriginal`, so they would no longer *be* a `Composite`, and JFace, layouts and user code would
stop compiling. It would also mean patching SWT's per-platform source six or more times.

Subclass proxies avoid both problems. A proxy is a real `Button`/`Control`/`Composite`, so it can be
passed anywhere SWT or JFace expects one.

## 2. Toolchain and baseline versions

| Item | Choice | Notes |
|---|---|---|
| JDK | 25 (Gradle toolchain) | `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` |
| Gradle | 9.1.0 wrapper | `gradle wrapper --gradle-version 9.1.0` |
| Build DSL | Kotlin DSL + `build-logic` included build | Shared convention plugins |
| Dependency versions | `gradle/libs.versions.toml` | One version catalog |
| SWT | `org.eclipse.platform:org.eclipse.swt.<ws>.<os>.<arch>` (Maven Central) | 3.135.0 (Eclipse 4.41 / 2026-09) |
| JFace upstream | `eclipse-platform/eclipse.platform.ui` → `bundles/org.eclipse.jface`, tag `R4_41` (JFace 3.40.0) | Plus `org.eclipse.core.commands` and `org.eclipse.equinox.common` from Maven Central |
| Nebula upstream | `eclipse/nebula` git repo at a pinned commit | Released and incubation widgets live in the same repo (paths mapped in Phase 0) |
| JSON | `com.google.code.gson:gson` (latest 2.x) | |
| Code generation and codemod | JavaPoet (proxy generation), JavaParser (codemod and audit) | Both run as Gradle tasks in `build-logic` |
| Tests | JUnit 5 + AssertJ, in-process SWT tests | |

**JDK 25 notes**
- SWT uses JNI. Every run and test JVM gets `--enable-native-access=ALL-UNNAMED`.
- macOS also needs `-XstartOnFirstThread`.
- Use the classpath, not JPMS modules. Every jar gets an `Automatic-Module-Name`.

**Gradle note on SWT resolution.** The SWT POM references `org.eclipse.swt.${osgi.platform}`, which
Gradle cannot resolve. The `build-logic` plugin `zapthemes.swt-platform` will:
1. Detect the OS and architecture (or take `-Pswt.platform=…`).
2. Substitute the matching fragment.
3. Publish per-platform variants.

Platforms: `win32.win32.{x86_64,aarch64}`, `gtk.linux.{x86_64,aarch64}`, `cocoa.macosx.{x86_64,aarch64}`,
`gtk.freebsd.{x86_64,aarch64}`.
**FreeBSD (confirmed in Phase 0):** Eclipse publishes no FreeBSD fragment anywhere. The plugin uses a
local SWT jar (`-Pswt.localJar`, default: the `x11-toolkits/swt` port location).

## 3. Repository layout

```
zapthemes/
├─ settings.gradle.kts
├─ gradle/libs.versions.toml
├─ build-logic/                    # conventions: java-25, swt-platform, proxy-codegen, codemod, audit, publishing
├─ third_party/
│  ├─ jface-upstream/              # pristine JFace at the pinned tag — fetched by scripts/fetch-upstream.sh, git-ignored
│  └─ nebula-upstream/             # pristine Nebula at the pinned commit — fetched, git-ignored
├─ zapthemes-core/                 # Theme model, GSON, ThemeManager, stylers, ThemeKeys
├─ zapthemes-swt/                  # generated + hand-tuned SWT proxies (com.zapthemes.swt.widgets/custom/browser…)
├─ zapthemes-widgets/              # owner-draw renderers + canvas replacements (FlatButton, FlatCombo…)
├─ zapthemes-jface/                # forked JFace (org.eclipse.jface.* packages, drop-in), codemodded
├─ zapthemes-nebula/               # forked released Nebula widgets, codemodded, plus their proxies + stylers
├─ zapthemes-nebula-incubation/    # forked incubation widgets, codemodded, plus their proxies + stylers
├─ zapthemes-themes/               # bundled JSON themes (light, dark, high-contrast) + theme.schema.json
├─ zapthemes-tools/                # proxy generator, codemod, audit (used by build-logic)
├─ zapthemes-demo/                 # ApplicationWindow demo / theme gallery / live switcher
└─ (later) zapthemes-nattable, zapthemes-visualization
```

Dependency direction, with no cycles:
`core ← swt ← widgets ← jface ← nebula ← nebula-incubation ← demo`.
`zapthemes-core` depends only on SWT and GSON. It knows nothing about proxies, so it can theme *any*
control.

## 4. Core design (`zapthemes-core`)

### 4.1 Key types

```
com.zapthemes.core
├─ ThemeKeys            // THEME_CONTROL_TYPE = "THEME_CONTROL_TYPE", THEME_CLASS = "THEME_CLASS"
├─ ThemeManager         // current theme per Display; applyOnCreate(); setTheme(); tree scan; listeners
├─ ThemeRegistry        // load themes from classpath / directories / files; list available themes
├─ ThemeResources       // per-Display Color/Font/Image cache; old theme's resources disposed after switch
├─ style/
│  ├─ ControlStyler<T extends Widget>  // apply(T widget, ResolvedStyle style, ThemeContext ctx)
│  ├─ StylerRegistry    // THEME_CONTROL_TYPE → styler; ServiceLoader SPI so Nebula modules plug in
│  ├─ StyleResolver     // defaults → type → style-bit rule → THEME_CLASS → platform; caches by (type, style bits, class)
│  └─ ControlTypeResolver // for untagged widgets (native dialogs, 3rd-party): nearest known class name
└─ model/ + json/       // Theme, Palette, ControlRule, StateProps, ColorSpec, FontSpec + GSON TypeAdapters
```

### 4.2 Proxies (`zapthemes-swt`, and the Nebula modules)

```java
package com.zapthemes.swt.widgets;

public class Button extends org.eclipse.swt.widgets.Button {
    public Button(org.eclipse.swt.widgets.Composite parent, int style) {
        super(parent, style);
        ThemeManager.applyOnCreate(this, "Button");
    }
    @Override protected void checkSubclass() { }   // SWT blocks subclassing by default
}
```

- **Generated, not hand-written.** The `zapthemes.proxy-codegen` Gradle task reads the SWT and Nebula
  class files (ASM / reflection). For each public, non-abstract `Widget` subclass with public
  constructors, it emits a proxy with:
  - every public constructor,
  - the `checkSubclass()` override,
  - the `applyOnCreate` call,
  - copied Javadoc stubs.
- The SWT public API is the same on all platforms, so proxies are generated once. Platform-only classes
  (`ole.win32.OleFrame`/`OleClientSite`, `opengl.GLCanvas`) go in platform source sets.
- Hand-written overrides live next to the generated code, for example a proxy that also installs an
  owner-draw listener. The generator skips any class that already has a hand-written proxy.
- Parameter and return types stay the original SWT types (`org.eclipse.swt.widgets.Composite parent`).
  A proxy is always accepted where an original is, and an original is accepted as a parent.
- `THEME_CONTROL_TYPE` is the simple class name: `"Button"`, `"CTabFolder"`, `"Grid"`, `"CDateTime"`.
  Nebula names that would clash get a prefix only when needed. Style differences such as
  `SWT.CHECK` / `SWT.RADIO` are handled in theme rules (§4.4), not in the type id.
- **Items** (`TableItem`, `TreeItem`, `TableColumn`, `CTabItem`, `ToolItem`, `MenuItem`, `GridItem`…)
  get proxies too, so they set the type and get fonts and colors. The parent's styler also re-themes
  them during a tree scan.
- **Covered SWT classes.**
  - `widgets`: Shell, Composite, Group, Canvas, Label, Link, Button, Text, Combo, List, Table, Tree,
    Spinner, Scale, Slider, ProgressBar, DateTime, Sash, TabFolder, ToolBar, CoolBar, ExpandBar, Menu,
    ToolTip, Tray/TrayItem, plus items.
  - `custom`: CLabel, CCombo, StyledText, CTabFolder, SashForm, ScrolledComposite, ViewForm, CBanner,
    TableCursor, plus items.
  - `browser`: Browser.
  - `opengl` and `ole` where present.

### 4.3 "Always immediate" application, and how it stays safe

- `applyOnCreate` sets `THEME_CONTROL_TYPE` and runs the styler **synchronously in the constructor**,
  as requested.
- For proxies of SWT and Nebula classes this is safe. The proxy's `super(...)` call finishes the
  original constructor, so every field of `Grid`, `CDateTime` etc. exists before the theme is applied.
- The risky case is a **user class extending a proxy** (`class MyView extends com.zapthemes.swt.widgets.Composite`)
  or an anonymous proxy subclass. There the styler runs before the subclass's own fields exist. Guards:
  1. Stylers call only base SWT setters, through `super`-level methods where possible.
  2. `applyOnCreate` catches `RuntimeException` from a styler, logs it once per class, and retries in
     `display.asyncExec`. The control is still themed before its first paint, and the failure is visible
     in the logs.
  3. A hook `ThemeManager.reapply(control)` lets subclasses call it at the end of their constructor if
     they override setters.
- The type id comes from the proxy, not from `getClass()`. A subclass of the `Composite` proxy is
  tagged `"Composite"` unless it calls `setData(THEME_CONTROL_TYPE, …)` itself.

### 4.4 ThemeManager

- One manager per `Display` (`ThemeManager.get(display)`), with a static default for convenience.
- `setTheme(Theme)` runs on the UI thread (`syncExec` if called from elsewhere):
  1. Build the new `ThemeResources`.
  2. For each `display.getShells()`:
     1. `setRedraw(false)`.
     2. Walk the tree depth-first: `Composite.getChildren()`, plus items of item-bearing controls, plus
        the `Menu` bar and pop-up menus.
     3. For each widget, read `getData("THEME_CONTROL_TYPE")`. If it is missing, infer the type with
        `ControlTypeResolver` and set it.
     4. Run the matching styler.
     5. `setRedraw(true)`, `layout(true, true)`, `redraw()`.
  3. Notify `ThemeChangeListener`s.
  4. Sync JFace `ColorRegistry` / `FontRegistry` keys (in the JFace module, through a listener).
  5. Dispose the old fonts and images.
- **Safety net:** a `Display` listener for `SWT.Skin` tags and themes widgets that were created without
  a proxy (code outside the forks, native-dialog children, third-party libraries).
- **Performance:** skip a control whose resolved-style hash hasn't changed.
- **Dev feature:** `ThemeManager.watch(Path)` reloads a theme JSON file when it changes, so themes can
  be edited live.

### 4.5 Theme JSON

```json
{
  "id": "zap-dark",
  "name": "Zap Dark",
  "extends": "zap-base",
  "palette": { "bg": "#1E1F22", "fg": "#DFE1E5", "accent": "#3574F0", "border": "#393B40",
               "font.default": { "name": "Segoe UI", "height": 9 } },
  "defaults": { "background": "$bg", "foreground": "$fg", "font": "$font.default" },
  "controls": {
    "Button":        { "background": "#2B2D30", "borderColor": "$border", "arc": 6,
                       "states": { "hover": { "background": "#35373B" }, "disabled": { "foreground": "#6F737A" } } },
    "Button[CHECK]": { "checkColor": "$accent" },
    "Table":         { "headerBackground": "#2B2D30", "headerForeground": "$fg",
                       "selectionBackground": "$accent", "alternateRowBackground": "#232427" },
    "Grid":          { "inherit": "Table" },
    "CTabFolder":    { "renderer": "zap.flat", "selectionBackground": "$bg", "tabBackground": "#2B2D30" },
    "CDateTime":     { "pickerBackground": "$bg", "todayColor": "$accent" }
  },
  "classes":  { "Button.primary": { "background": "$accent", "foreground": "#FFFFFF" } },
  "platform": { "win32": { "darkScrollbars": true }, "cocoa": { "appearance": "darkAqua" }, "gtk": {} }
}
```

Resolution order, each step overriding the one before:
1. `defaults`
2. `controls[<THEME_CONTROL_TYPE>]`, after `inherit`
3. `controls[<type>[STYLEBIT]]`
4. `classes[<type>.<THEME_CLASS>]`
5. `states` (owner-draw only)
6. `platform`

`extends` chains are merged first, and `$palette` references are resolved last. Unknown keys are kept,
so custom stylers can read their own properties. `zapthemes-themes/theme.schema.json` documents the
format and is checked in tests.

## 5. Forks and the codemod

### 5.1 JFace (`zapthemes-jface`)

- Keep the `org.eclipse.jface.*` packages and ship it as a **drop-in** for
  `org.eclipse.platform:org.eclipse.jface`. A Gradle capability conflict fails the build if both jars
  end up on the classpath. License stays EPL-2.0.
- Import the source from `third_party/jface-upstream` into `zapthemes-jface/src/main/java`, then run the
  codemod.

### 5.2 Nebula (`zapthemes-nebula`, `zapthemes-nebula-incubation`)

- Copy each widget bundle's `src` from `third_party/nebula-upstream` and keep its EPL notices.
- Compile against the JFace fork and the SWT proxies.
- Run the same codemod over the copied source. Internal children (CDateTime's Text and Button,
  TableCombo's Table, DualList's lists) are then proxies and get themed on creation.
- Nebula widgets keep extending the **original** SWT bases (`Grid extends org.eclipse.swt.widgets.Canvas`).
  Each widget then gets its own generated proxy (`com.zapthemes.nebula.grid.Grid extends …nebula.widgets.grid.Grid`).

### 5.3 Codemod rules (JavaParser, `zapthemes-tools`)

1. **Rewrite only instantiation expressions.** `new Button(p, s)` becomes
   `new com.zapthemes.swt.widgets.Button(p, s)`, fully qualified. Imports, field types, parameter
   types and return types are **not** changed, so public JFace and Nebula API signatures stay
   identical to upstream.
2. Anonymous subclasses: `new Composite(p, s) { … }` → `new com.zapthemes.swt.widgets.Composite(p, s) { … }`.
3. Named subclasses inside the forks (e.g. JFace's `class ProgressIndicator extends Composite`,
   Nebula's `extends Canvas`) keep their original superclass. They get their own proxy, or are themed
   via `SWT.Skin` if they are internal.
4. **Audit gate.** A JavaParser audit runs in `check`. It fails the build if any
   `new <SWT/Nebula Widget subclass>(` remains in a fork, the demo, or our own modules, unless the line
   is marked `// zapthemes:allow-raw`.
5. **Upstream sync.** Bump the subtree or submodule, re-copy the pristine source, re-run the codemod,
   and re-apply the few hand patches kept in `<module>/patches/`. The fork contains no hand edits
   beyond those.

### 5.4 Spots to review by hand after the codemod

These areas need manual review even after the codemod has run:
- `Window.createShell`
- `Dialog.createButton`
- `ControlDecoration`
- field assist pop-ups
- `PopupDialog`, `ToolTip`
- all `CellEditor`s
- `ProgressMonitorPart`, `StatusLine`
- the Menu, ToolBar, CoolBar and StatusLine managers' `fill(...)` methods
- `WizardDialog`, `PreferenceDialog`, `FieldEditor`s

Out of scope for v1: `jface.text`, `jface.databinding`. They can be forked later with the same tools.

## 6. Nebula coverage

| Released (Phase 4) | Incubation (Phase 5) |
|---|---|
| Grid (+ GridColumn, GridColumnGroup, GridItem, GridTableViewer / GridTreeViewer) | CollapsibleButtons |
| CDateTime | CompositeTable |
| Gallery (+ renderers) | DateChooser |
| TableCombo | DateChooserCombo |
| PGroup (+ renderers) | FormattedText |
| Breadcrumb | RadioGroup |
| CheckboxGroup | Picture |
| DualList | Pagination |
| Header | Timeline |
| ProgressCircle | |
| RangeSlider | |
| StarRating | |
| CTreeCombo | |
| BadgedLabel | |
| Chips | |
| Carousel | |
| SegmentedBar | |
| Tiles | |
| As practical: PShelf, TitledSeparator, RoundedToolbar, SwitchButton, TextAssist, PasswordRevealer, CalendarCombo, SplitButton | |

Widgets that draw themselves through renderers (Grid, Gallery, PGroup, PShelf) get theme-aware
renderers set by their styler, such as `ThemedGridCellRenderer` or `ThemedPGroupRenderer`, rather than
plain `setBackground` calls.

## 7. Native-control limitations (`zapthemes-widgets`)

| Control | Approach |
|---|---|
| Table / Tree | The proxy styler installs `SWT.EraseItem` / `SWT.PaintItem` owner-draw (selection, hover, alternate rows, grid lines) and calls `setHeaderBackground` / `setHeaderForeground`. Canvas alternative: Nebula Grid + `GridTableViewer` / `GridTreeViewer` |
| CTabFolder | `ThemedCTabFolderRenderer`, set by the styler |
| Button (win32 / cocoa ignore colors) | Canvas replacement `FlatButton` (same API subset, accessibility, keyboard traversal). `ThemeOptions.preferCanvasReplacements` makes the demo and new code use it. A native `Button` proxy stays a native button. |
| Combo | `FlatCombo` based on `CCombo`, with a painted arrow and a themed pop-up |
| Group, ProgressBar, Scale / Slider | `FlatGroup`, `FlatProgressBar`, Nebula RangeSlider or a canvas slider |
| TabFolder | Recommend CTabFolder |
| Scrollbars, menus, Shell title bar, native dialogs | Platform dark-mode hooks: win32 explorer / immersive dark mode, GTK prefer-dark / CSS, Cocoa NSAppearance. These go through `PlatformThemeSupport` per OS. The internal `Display.setData` keys are checked in Phase 0. Documented as limitations. |

Renderers read the theme's `states` block (hover, pressed, focused, disabled, selected).

## 8. Demo (`zapthemes-demo`)

- `ZapDemo extends ApplicationWindow`, using the forked JFace, with a menu bar, tool bar and status line.
- Tabs: "SWT Basics", "Viewers", "Dialogs and Wizards", "Nebula", "Nebula Incubation",
  "Native vs Canvas".
- Theme switcher (bundled themes plus "Load JSON…") and a live-reload toggle.
- A `run` task with the platform JVM flags.

## 9. Testing and CI

- **Unit:** GSON round-trip, `extends` merge, palette and rule resolution, schema check, error messages
  that include line and column.
- **Proxy contract (reflection, every generated proxy):** creating it sets `THEME_CONTROL_TYPE` to the
  simple name and applies the expected colors and fonts. `checkSubclass` doesn't throw. Every public
  constructor of the original is present.
- **ThemeManager:**
  - Build a nested UI (SWT + JFace + Nebula), switch theme, and assert every descendant was
    re-styled, including untagged children.
  - Check that fonts don't leak across 100 switches.
  - Test the `SWT.Skin` safety net.
  - Test the retry path for a user subclass whose setters throw during construction.
- **Audit gate** (§5.3) runs in `check`.
- **Optional:** screenshot golden tests per theme and platform.
- **CI:** GitHub Actions on Windows, Ubuntu x86_64/arm64 (Xvfb) and macOS arm64, plus a FreeBSD VM job.

## 10. Phases

| Phase | Scope | Exit criteria |
|---|---|---|
| **0. Spikes** ✅ (see [phase0-findings.md](phase0-findings.md)) | Gradle 9.1 / JDK 25 skeleton; SWT resolution for all platforms including FreeBSD; proxy generator on 10 classes; codemod + audit on 3 JFace files; check the Nebula source paths; check the platform dark-mode keys | A themed Shell runs on every CI platform; each risk has a decision |
| **1. Core + SWT proxies** ✅ (see [phase1-notes.md](phase1-notes.md)) | Theme model / GSON / schema, ThemeRegistry, ThemeResources, ThemeManager (create-time apply, tree scan, Skin net), all SWT proxies + stylers, light/dark themes | Contract tests pass; a standalone `Display`/`Shell` sample switches themes |
| **2. JFace fork** ✅ (see [phase2-notes.md](phase2-notes.md)) | Import, codemod, manual review list, audit clean, JFace registry sync, capability conflict | ApplicationWindow demo themes menus, toolbars, dialogs, wizards and viewers |
| **3. Owner-draw and replacements** | Table/Tree owner-draw, CTabFolder renderer, FlatButton / FlatCombo / FlatGroup / FlatProgressBar, platform hooks | Native vs canvas comparison in the demo on all platforms; limitations documented |
| **4. Nebula released** | Fork + codemod, proxies, stylers, themed renderers, GridTableViewer path | Every listed widget is themed and switches theme live in the demo |
| **5. Nebula incubation** | Same for the incubation list; Timeline gets draw2d from the GEF Classic p2 update site and EMF ecore from Maven Central | Same as Phase 4 |
| **6. Release** | High-contrast theme, theme authoring guide, Javadoc, signed Maven publishing, samples | 1.0 |
| **7. Optional** | NatTable (bridge to NatTable `ThemeConfiguration`), Nebula Visualization, `jface.text` fork, data binding for all controls (`jface.databinding` + the Nebula `*ObservableValue` classes left out in Phases 4–5) | — |

## 11. Risks

| Risk | Mitigation |
|---|---|
| User code still does `new org.eclipse.swt.widgets.Button(...)` | `SWT.Skin` safety net themes it (deferred). Document "import from `com.zapthemes.swt.*`". Optionally ship the audit as a Gradle plugin for apps. |
| Both `com.zapthemes…Button` and `org.eclipse…Button` are imported, so names are ambiguous | Codemod uses fully qualified `new` expressions inside the forks. App guide: import proxies for creation and keep originals for types, or use `var`. |
| Fork drift from upstream | Pristine source + repeatable codemod + small patch series; sync every Eclipse release |
| Immediate apply breaks a user subclass that overrides setters | Catch and retry via `asyncExec` + `reapply()` hook (§4.3) |
| SWT internal platform keys change | Isolated in `PlatformThemeSupport`, tested per platform, fail safely |
| FreeBSD SWT artifacts missing | Repackage or build natives (decided in Phase 0) |
| Canvas replacements lose accessibility or native behavior | `Accessible` listeners, traversal and focus handling, and tests. Replacements are opt-in. |
| Font/image leaks on theme switches | `ThemeResources` disposal + leak test |

## 12. Assumptions to confirm

1. Group `com.zapthemes`, with proxy packages `com.zapthemes.swt.{widgets,custom,browser}` and
   `com.zapthemes.nebula.<widget>`.
2. The theme model includes palette variables, `extends`, style classes (`THEME_CLASS`), style-bit rules
   and state styles.
3. Canvas replacements are opt-in. Native proxies stay native.
4. The whole library is EPL-2.0, because it contains forked EPL code.
