# Using ZapThemes and writing themes

This guide covers adding ZapThemes to an SWT or JFace application, choosing and switching themes,
writing your own theme in JSON, and making your own widgets theme-aware. The
[samples](../zapthemes-samples/src/main/java/com/zapthemes/samples) show each step in a small,
runnable program:

| Sample | Shows | Run |
|---|---|---|
| [HelloZapThemes](../zapthemes-samples/src/main/java/com/zapthemes/samples/HelloZapThemes.java) | Proxies, bundled themes, matching the desktop, live switching | `./gradlew :zapthemes-samples:runHello` |
| [CustomThemeSample](../zapthemes-samples/src/main/java/com/zapthemes/samples/CustomThemeSample.java) | A theme that extends a bundled one, style classes, validation | `./gradlew :zapthemes-samples:runCustomTheme` |
| [CustomWidgetSample](../zapthemes-samples/src/main/java/com/zapthemes/samples/CustomWidgetSample.java) | Your own canvas widget, themed through setters | `./gradlew :zapthemes-samples:runCustomWidget` |
| [JFaceSample](../zapthemes-samples/src/main/java/com/zapthemes/samples/JFaceSample.java) | An `ApplicationWindow` on the forked JFace, theme listeners | `./gradlew :zapthemes-samples:runJFace` |

## 1. Add the library

| Artifact (`com.zapthemes:`) | Contents |
|---|---|
| `zapthemes-core` | Theme model, JSON loading, `ThemeManager`, stylers |
| `zapthemes-swt` | Proxies for every SWT widget: `com.zapthemes.swt.{widgets,custom,browser,opengl}` |
| `zapthemes-themes` | The bundled themes: `zap-light`, `zap-dark`, `zap-high-contrast` |
| `zapthemes-widgets` | Opt-in canvas replacements: `FlatButton`, `FlatGroup`, `FlatProgressBar` |
| `zapthemes-jface` | JFace 3.40 fork: a drop-in `org.eclipse.jface` whose widgets are proxies |
| `zapthemes-jface-notifications` | JFace notifications 0.8.100 fork: a drop-in `org.eclipse.jface.notifications` (`NotificationPopup`) on the forked JFace |
| `zapthemes-jface-text` | JFace Text 3.31.100 fork: a drop-in `org.eclipse.jface.text` (`SourceViewer`, rulers, hovers, content assist), with `zapthemes-text`, `org.eclipse.text` 3.14.800 built from source |
| `zapthemes-nebula`, `zapthemes-nebula-incubation` | Nebula proxies and stylers, with the forked widgets (`zapthemes-nebula-fork`, `zapthemes-nebula-incubation-fork`) and draw2d (`zapthemes-draw2d`) |

Every module needs JDK 25 and depends on SWT 3.135.0. As with JFace on Maven Central, the POMs name the
platform-neutral `org.eclipse.platform:org.eclipse.swt`. Maven resolves it to your platform's
fragment. Gradle ignores the Maven profiles that do this, so with Gradle you replace the placeholder
with the fragment yourself:

```kotlin
dependencies {
    implementation("com.zapthemes:zapthemes-jface:1.0.0")    // or zapthemes-swt for plain SWT
    implementation("com.zapthemes:zapthemes-themes:1.0.0")
    implementation("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.135.0") // your platform
}
configurations.all {
    exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
}
```

The fragments are `org.eclipse.swt.win32.win32.{x86_64,aarch64}`, `org.eclipse.swt.gtk.linux.{x86_64,aarch64}`
and `org.eclipse.swt.cocoa.macosx.{x86_64,aarch64}`. FreeBSD has no fragment on Maven Central; use the
`x11-toolkits/swt` port's jar. Run with `--enable-native-access=ALL-UNNAMED`,
plus `-XstartOnFirstThread` on macOS.

`zapthemes-jface` declares the capability `org.eclipse.platform:org.eclipse.jface`. If another
dependency also pulls in the official JFace, Gradle reports a capability conflict instead of mixing the
two. Resolve it in favor of the fork:

```kotlin
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.eclipse.platform:org.eclipse.jface") {
        select(candidates.first { (it.id as? ModuleComponentIdentifier)?.module == "zapthemes-jface" })
    }
}
```

**Data binding.** The official `org.eclipse.platform:org.eclipse.jface.databinding` (1.16.0 for
Eclipse 4.41) works on the fork unchanged: it observes widgets and never creates them. Its POM asks
for the official JFace, which the capability rule above replaces with the fork. The
`*ObservableValue` classes of Nebula DateChooser and FormattedText are included; add
`jface.databinding` yourself to use them, as with upstream Nebula.

**Notifications.** `zapthemes-jface-notifications` replaces `org.eclipse.jface.notifications` and
declares its capability, like `zapthemes-jface`. Its popup takes the theme's `notification` class
(frame) and `popup` class (content area) instead of upstream's white content; it brings
`org.eclipse.core.runtime`, as upstream does.

**Source viewers.** `zapthemes-jface-text` replaces `org.eclipse.jface.text` and brings
`zapthemes-text` in place of `org.eclipse.text`. Both declare the upstream capabilities. `org.eclipse.text` creates no widgets, but it shares
packages with `jface.text` and its published jar is signed, so the JVM would refuse the unsigned
fork's classes next to it; `zapthemes-text` is the same code, unsigned. Rulers take the theme's
`ruler` class (line numbers in its foreground), hovers, content assist and context information
`popup` (status lines also `muted`), and the context popups' frame `frame`. Colors the application
sets (`setBackground` on a ruler column, `ContentAssistant.setProposalSelectorBackground`, ...) win.
Syntax colors stay the application's: the bundled themes set `com.zapthemes.text.keyword`, `.string`,
`.comment` and `.number` in JFace's `ColorRegistry` (constants in `TextTheming`), for scanners to use
and to re-read when the registry changes. With a top margin on the text widget
(`StyledText.setMargins`), upstream's line number column paints no numbers; use side margins.

## 2. Create widgets from the proxies

A proxy is a subclass of the SWT widget that tags itself with its control type
(`THEME_CONTROL_TYPE`) and applies the current theme in its constructor. Import the proxies for
`new`, and keep using the SWT types everywhere else:

```java
import org.eclipse.swt.widgets.Composite;      // types: parameters, fields, casts
import com.zapthemes.swt.widgets.Button;       // creation
import com.zapthemes.swt.widgets.Shell;

Shell shell = new Shell(display);
Button ok = new Button(shell, SWT.PUSH);       // themed now, and on every theme switch
```

A proxy `Shell` is an `org.eclipse.swt.widgets.Shell`, so all SWT and JFace API accepts it. If a file
needs both the proxy and the original under the same simple name, import the proxy and fully qualify
the other one (or use `var`).

Code that still creates `org.eclipse.swt` widgets directly is themed too, through SWT's `Skin` event,
but one event-loop turn later. You may see a brief flash of platform colors. The
`zapthemes.raw-widget-audit` Gradle plugin in this repository reports such creations.

JFace and the Nebula forks create proxies internally, so viewers, dialogs, wizards, preference pages
and Nebula widgets need no change. Nebula proxies live in `com.zapthemes.nebula.<widget>`, for example
`com.zapthemes.nebula.grid.Grid`.

## 3. Choose and switch themes

```java
ThemeRegistry registry = new ThemeRegistry();
BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
registry.loadFile(Path.of("acme-dark.json"));                 // your own, optional

ThemeManager themes = ThemeManager.get(display);              // one per Display
themes.setTheme(registry.resolve(
        BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme())));
```

Set the theme before creating widgets. `setTheme` can be called at any time later: it re-themes
every open shell, JFace's color and font registries, and the themed system colors used by Nebula's
painters. To react after a switch (for example to refresh content that shows colors), add a
`ThemeChangeListener`.

`zap-high-contrast` is black with white text and yellow accents. Every text color in it reaches the
WCAG AAA contrast ratio of 7:1 against its background, which a test checks. When Windows itself runs
a contrast theme, some native controls draw with the system's contrast colors whatever the
application sets. Following `display.getHighContrast()` keeps the rest of the UI consistent with them.

## 4. Write a theme

A theme is one JSON object. Point your editor at the schema for completion and checks, at
[`zapthemes/theme.schema.json`](../zapthemes-themes/src/main/resources/zapthemes/theme.schema.json) in the
`zapthemes-themes` jar. The bundled [`zap-dark.json`](../zapthemes-themes/src/main/resources/zapthemes/themes/zap-dark.json)
is the most complete reference.

```json
{
  "id": "acme-dark",
  "name": "Acme Dark",
  "extends": "zap-dark",
  "palette":  { "accent": "#E8590C", "danger": "#C62828", "font.headline": { "height": 14, "style": "bold" } },
  "defaults": { "background": "$bg", "foreground": "$fg" },
  "controls": {
    "Table":        { "selectionBackground": "$selection", "states": { "hover": { "background": "$hover" } } },
    "Tree":         { "inherit": "Table" },
    "Label[WRAP]":  { "foreground": "$qualifier" }
  },
  "classes":  { "Button.danger": { "background": "$danger" }, "*.headline": { "font": "$font.headline" } },
  "platform": { "*": { "darkThemePreferred": true }, "win32": { "darkScrollbars": true } },
  "registries": {
    "colors":       { "HYPERLINK_COLOR": "$accent" },
    "fonts":        { "org.eclipse.jface.headerfont": "$font.headline" },
    "systemColors": { "COLOR_LIST_SELECTION": "$accent" }
  }
}
```

### Blocks

| Block | Meaning |
|---|---|
| `id`, `name` | Unique id (what `registry.resolve` takes) and display name |
| `extends` | Id of the theme this one is layered on. Each block is merged key by key, so a theme can change only what differs. A palette entry replaced here also changes every rule of the parent that references it. |
| `palette` | Named values referenced from anywhere as `"$name"`. Colors are `#RRGGBB` or `#RRGGBBAA`. Fonts are `{ "name": "Segoe UI", "height": 9, "style": "bold\|italic" }`; a missing name or height keeps the system font's. An entry may reference another. |
| `defaults` | Properties for every widget, applied first |
| `controls` | Rules by control type. Keys: `Type` (`"Button"`, `"Grid"`, `"FlatButton"`), or `Type[BIT\|BIT]` (`"Button[CHECK]"`, `"Text[MULTI\|WRAP]"`), which applies when the widget has all of those SWT style bits. `"inherit": "Other"` starts a rule from another type's rule. |
| `classes` | Rules a widget opts into with `themes.setThemeClass(widget, "danger large")`: `Type.cls` for one type, `*.cls` for any. The JFace fork sets `banner`, `title`, `info` (tooltips and hovers), `popup` (`PopupDialog`; its info text also `muted`), `error` (status line errors), `plain` and `pane`; the notifications fork sets `notification` (the popup's frame) and `popup` (its content); the JFace Text fork sets `ruler`, `popup` (hovers, content assist; status lines also `muted`) and `frame` (context information): give them rules, as the bundled themes do |
| `states` (inside a rule) | Overrides while a widget is `hover`, `pressed`, `selected`, `focused` or `disabled` |
| `platform` | What the OS draws itself: `*` for all platforms, then `win32`, `gtk` or `cocoa` |
| `registries` | Named resources of other toolkits: JFace `colors` and `fonts`, and `systemColors`, which replaces `Display.getSystemColor` constants inside the Nebula and JFace Text forks |

### Which rule wins

For one widget, properties merge in this order, later steps winning:

1. `defaults`
2. `controls[Type]`, after its `inherit` chain
3. each `controls[Type[BITS]]` whose bits the widget has
4. for each class in `THEME_CLASS`, in order, `classes[*.cls]` then `classes[Type.cls]`

### Properties

| Widgets | Properties |
|---|---|
| Every control and item | `background`, `foreground`, `font`; composites also take `backgroundMode` (`none`, `default`, `force`) |
| Table, Tree | `headerBackground`, `headerForeground`. Owner-drawn: `selectionBackground`, `selectionForeground`, `alternateRowBackground` (Table), `gridLineColor`, `states.hover.background`, and `focusCellBackground` / `focusCellForeground` for the focus cell of JFace's `FocusCellOwnerDrawHighlighter` |
| CTabFolder | `selectionBackground`, `selectionForeground`, `selectionBarThickness`, `highlight`, `tabHeight` |
| StyledText | `selectionBackground`, `selectionForeground`, `marginColor` |
| Link | `linkForeground` |
| FlatButton, FlatGroup, FlatProgressBar | `borderColor`, `accentColor`, `arc`, `boxBackground`, `titleForeground`, `titleFont`, `barColor`, `errorColor`, `pausedColor`, and every state |
| Nebula widgets, your own widgets | Any setter: `fooColor` calls `setFooColor(...)`, and a dotted key reaches a part (`itemRenderer.selectionBackgroundColor` calls `getItemRenderer().setSelectionBackgroundColor(...)`). Values are converted to the setter's type: color, font, int, boolean or string. When a later theme leaves a key out, the widget's original value is restored. |

Native controls take only their static properties. State overrides apply where ZapThemes or the widget
paints: Table and Tree rows (`hover`), the Flat widgets (every state), and Nebula widgets that have
per-state setters.

`platform` settings:

| Block | Key | Effect |
|---|---|---|
| `*` | `darkThemePreferred` | Dark title bars and native dialogs (`Shell.setDarkThemePreferred`) |
| `win32` | `darkScrollbars`, `darkCombos`, `darkTextIcons`, `progressBarColors` | SWT's Windows dark-mode switches (booleans) |
| `win32` | `menuBarBackground`, `menuBarForeground`, `menuBarBorderColor`, `tableHeaderLineColor`, `disabledLabelForeground` | Colors Windows draws itself |
| `gtk` | `menuBarBackground`, `menuBarForeground`, `menuBackground`, `menuForeground`, `menuSelectionBackground`, `menuSelectionForeground` | Menu bar and menu colors, as GTK CSS. GTK 3 keeps a light menu bar under `darkThemePreferred`, so a dark theme needs these. |

### Check a theme

`ThemeValidator.validate(registry.resolve(id))` returns every problem with its path: unknown palette
references, malformed colors and fonts, unknown style bits, broken `inherit` chains, and malformed
class or state rules. An empty list means the theme is valid. Run it in a unit test for each of your
themes, as [ThemeValidatorTest](../zapthemes-core/src/test/java/com/zapthemes/core/ThemeValidatorTest.java)
does for the bundled ones.

## 5. Make your own widgets theme-aware

Do what the Nebula proxies do (see [Gauge](../zapthemes-samples/src/main/java/com/zapthemes/samples/Gauge.java)):

1. Expose the look through setters: `setNeedleColor(Color)`, `setThickness(int)`. Accept `null` as
   "back to my default".
2. Call `ThemeManager.applyOnCreate(this, "Gauge")` as the last statement of the constructor. It
   tags the widget and applies the current theme.
3. Register a styler for the type in a `StylerProvider`, listed in
   `META-INF/services/com.zapthemes.core.style.StylerProvider`. A `PropertyStyler` maps theme keys to
   your setters; override `apply` to `redraw()` afterwards.
4. Write the widget's rule in your themes (`"Gauge": { "needleColor": "$accent" }`), against palette
   entries so that it follows each theme.

A widget that paints from the theme directly can instead call `themes.styleOf(this)` in its paint
listener, as `FlatButton` does. To write a styler from scratch, implement `ControlStyler`. It must
set every property it owns, with `null` where the theme has none, so that switching themes leaves
nothing from the previous one behind.

## 6. Tips

- Don't dispose fonts you get from a theme (`getFont()` on a themed widget, `ThemeContext.font`):
  they are cached and shared until the display is disposed. Theme colors are safe to dispose,
  because each caller gets its own.
- To restyle one widget without a new theme, give it a class and add a class rule. Calling
  `setBackground` directly works, but it lasts only until the next theme switch.
- Toolkit-level resources (JFace `ColorRegistry` and `FontRegistry` keys, system colors) go in
  `registries`, not in control rules.
- To try a theme quickly, keep it in a file and pass it to the custom-theme sample:
  `./gradlew :zapthemes-samples:runCustomTheme --args=$PWD/my-theme.json`. It prints validation
  problems to stderr.
