# Using ZapThemes and writing themes`focusColor` (focus), `useAppAccent` (true), `arc` (4), and the states `hover`, `pressed`, `checked`, `disabled`, `focused`, each with `background`, `foreground`, `borderColor` (they win over the `item…` keys) |

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
| `zapthemes-widgets` | Opt-in canvas replacements: `FlatButton`, `FlatGroup`, `FlatProgressBar`; the Office-style `Ribbon` and the shared command model (`com.zapthemes.widgets.command`) |
| `zapthemes-jface-ribbon` | JFace support for the ribbon: actions as commands, contribution items, `RibbonApplicationWindow` |
| `zapthemes-jface` | JFace 3.40 fork: a drop-in `org.eclipse.jface` whose widgets are proxies |
| `zapthemes-jface-notifications` | JFace notifications 0.8.100 fork: a drop-in `org.eclipse.jface.notifications` (`NotificationPopup`) on the forked JFace |
| `zapthemes-jface-text` | JFace Text 3.31.100 fork: a drop-in `org.eclipse.jface.text` (`SourceViewer`, rulers, hovers, content assist), with `zapthemes-text`, `org.eclipse.text` 3.14.800 built from source |
| `zapthemes-nattable` | NatTable bridge: every NatTable takes the theme as a NatTable `ThemeConfiguration`, switched live (NatTable 2.7.0 as published) |
| `zapthemes-visualization` | Nebula Visualization fork (XYGraph, gauges, meters, knobs, tanks, thermometers, ...) whose draw2d figures take the theme's figure rules |
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

**NatTable.** NatTable paints its cells itself, so it is not forked: add `zapthemes-nattable`, which
brings `org.eclipse.nebula.widgets.nattable:org.eclipse.nebula.widgets.nattable.core` 2.7.0. Its POM
asks for the official JFace: the capability rule above resolves it to the fork. There is nothing to
call. Every `NatTable` gets a NatTable `ThemeConfiguration` built from the theme's `NatTable` rule
when it is created and on every switch; the bundled themes start that rule from `Table`
(`"inherit": "Table"`), so headers, selection, focus cell (the selection anchor), stripes, grid
lines and hover match the theme's tables. `ZapNatTableThemeConfiguration` lists the keys, including
the NatTable-only `freezeSeparatorColor`, `summaryRowBackground`, `filterRowBackground`,
`errorForeground` and `accentColor`. Styles for your own labels go in a NatTable `IThemeExtension`
added with `NatTableTheming.addExtension(table, ...)`, so they survive theme switches;
`table.setData(NatTableTheming.MANUAL, true)` leaves a table's NatTable theme to you.

**Visualization.** `zapthemes-visualization` is Nebula Visualization (`XYGraph` and the widget
figures: gauge, meter, knob, tank, thermometer, progress bar, slider) forked like the other Nebula
widgets; its dialogs are proxies. Its graphs and gauges are draw2d figures, not SWT widgets, so the
theme gives rules for figure types by simple class name in `controls` (`"XYGraph"`, `"PlotArea"`,
`"Axis"`, `"Legend"`, `"AbstractScaledWidgetFigure"`, `"AbstractLinearMarkedFigure"`, ...). The
figures apply them themselves when shown and follow every switch (`FigureTheming`): the rules of a
figure's class and its Nebula Visualization superclasses, the most general first, through its
setters (`majorGridColor` calls `setMajorGridColor`), without the theme's `defaults`. Trace colors
stay the application's: XYGraph's defaults start with a dark blue, so set them on dark themes. `FigureTheming.follow(canvas, figure)` does the same for a figure tree of
your own.

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

### Zoom

Like Chrome, an app can be zoomed: **Ctrl + mouse wheel** (Cmd on macOS) makes every themed font
bigger or smaller, one step per wheel notch, and **Ctrl+=** (or Ctrl++), **Ctrl+-** and **Ctrl+0**
zoom in, zoom out and reset. A **trackpad pinch** zooms too: the zoom when the pinch began, times
the pinch, snapped to the nearest step. Windows precision touchpads often send a pinch as
Ctrl + wheel, which zooms the same way. The steps are Chrome's: 25, 33, 50, 67, 75, 80, 90, 100,
110, 125, 150, 175, 200, 250, 300, 400 and 500 %.

```java
themes.zoomIn();                       // 100 % -> 110 %
themes.setZoom(1.5);                   // any factor from 0.25 to 5.0
themes.resetZoom();
themes.addZoomListener((previous, zoom) -> status.setText(Math.round(zoom * 100) + " %"));
```

- The zoom multiplies the height the theme gives each font. A theme font with no height (the
  system font's size) is scaled too; name, bold and italic stay. Heights are rounded to whole
  points, so a 9 pt font is 10 pt at 110 % and 11 pt at 125 %.
- It is per `Display`, so it applies to the whole app. It applies live: existing widgets get the
  new fonts and every shell is laid out again. Shells, popups and dialogs opened later use the
  current zoom. Switching themes keeps the zoom, and zooming keeps the theme.
- **Controls without a theme font** follow the zoom too: at any zoom but 100 % they get the system
  font at that zoom. At 100 % they keep the platform default, as before.
- **JFace's `FontRegistry`** follows the zoom: the keys the theme sets in `registries.fonts`, and
  every other key, JFace's own (`JFaceResources.getTextFont()`, the banner font, ...) and yours.
  JFace dialogs and viewers take their fonts from it. A value you `put` for a key the theme doesn't
  set becomes that key's original and is zoomed right away. At 100 % every key holds its original
  again.
- **Fonts an app sets with `setFont`** are kept and scaled. Every proxy control reports its
  `setFont` calls (the theme's own are told apart), so a font the app sets (a bold label, a code
  editor's monospace font) is known to be the app's. From then on the control keeps that family
  and style through theme switches and zooms, at its height times the zoom. Controls created
  without a proxy take the theme's font on every pass, as before. The height
  is taken at the zoom the font was set at, so a font derived from an already zoomed one (a bold
  copy of the dialog font at 150 %) doesn't grow twice. At 100 % the control gets the app's own
  `Font` object back.
  - A font like the theme's (same family, height and style) counts as the theme's and keeps
    following the theme. That covers JFace's dialog font, which the bundled themes set to their
    default font.
  - Whichever was set last wins. `setThemeClass` makes the control follow the theme's font again,
    so a class with a font (`"*.mono": { "font": ... }`) replaces an app font set before it. An
    app font set after the class wins. `themes.followThemeFont(control)` drops an app font without
    changing classes.
  - A JFace registry font set on a control (`label.setFont(JFaceResources.getBannerFont())`)
    is such an app font. It scales with the zoom, but on a theme switch it keeps the family and
    style it had. To follow the new theme's banner font, set it again from a
    `ThemeChangeListener`.
  - Only controls are covered. Fonts on table and tree items (a JFace `IFontProvider`) are left to
    the app and the viewer, as before.
  - `ThemeResources.font(name, style, baseHeight)` gives a font at the current zoom for code that
    draws text itself.
- **Owner-drawn widgets** (`FlatButton`, `FlatGroup`, Nebula's painters, NatTable, Visualization
  figures) take their fonts from the theme, so they scale as well. They must use
  `ThemeContext.font` or the control's font, not a font they made once.
- **Visualization figures' own fonts** scale even when the theme sets none: XYGraph's 12 pt bold
  title, its axes' titles and scales, the gauges' labels. That's every font property of a figure
  (`font`, `titleFont`, ...). A figure's own font is taken as a 100 % size. One the app sets later
  (`graph.setTitleFont(...)`) is taken at the zoom it is set at, like an app font on a control, and
  at 100 % the figure gets that exact font back. Sizes in pixels inside the figures (line widths,
  markers, the XYGraph toolbar's icons) stay as they are.
- **The ribbon** follows the zoom as a whole: tabs, groups, items, galleries, KeyTips, popups,
  super tooltips, the File page (backstage) and the message bar. Its icons are asked for at the
  zoomed size, 32 and 64 px at 200 % and exact multiples of 16 / 32 px at other levels, so give
  the commands' `Images` large variants for a sharp result. Its `arc` is a size.
- **Column widths** of tables and trees scale, JFace viewer columns and Nebula `GridColumn`s
  included (any `...Column` item with `getWidth` / `setWidth`). Each column keeps its 100 % width
  as a fraction, so zooming in and out many times ends at the exact original width. A width that
  changed since the last zoom (the user dragged the column, or the app set or packed it) is taken
  at the zoom it changed at, so a column dragged to 300 px at 200 % is 150 px at 100 %. Columns
  created while zoomed keep the width the app gives them until the next zoom. Give them
  `themes.resources().zoomedPixels(160)` to start at the current zoom.
- **NatTable** row heights and column widths scale through NatTable's own scaling. NatTable keeps
  the sizes unscaled, so there's no drift either, sizes the user dragged stay in proportion, and a
  table created while zoomed starts at the current zoom.
- **Sizes in the theme** scale: the integer keys the property catalog describes as pixel sizes
  (`PropertySpec.size(...)`). Those are `arc` on the Flat widgets, CTabFolder's
  `selectionBarThickness` and `tabHeight`, and the sample Gauge's `thickness`. Other integers (a
  count, a style index) are left as they are. A library describes its own sizes the same way in
  its `CatalogProvider`. `ThemeContext.size(style, key)` reads one at the current zoom, and the
  generic setter styler (Nebula, your own widgets) and Visualization figures scale them on their
  own. The Flat widgets' built-in padding, margins and check boxes scale too.
- **Images** on widgets follow the zoom: any widget with `getImage` / `setImage` (Label, Button,
  CLabel, ToolItem, CTabItem, Nebula widgets) gets a copy of its image made for the zoom.
  - The copy asks the original for its pixels at that zoom (`Image.getImageData(zoom)`). An image
    loaded with zoom variants (`icon.png` next to `icon@1.5x.png` and `icon@2x.png`, through JFace's
    `ImageDescriptor.createFromURL` or an `ImageFileNameProvider`) therefore shows the sharp
    variant at 150 % and 200 %, and any other image is scaled smoothly. Ship `@2x` icons for the
    best results.
  - At 100 % the widget gets its own image back. An image the app sets is taken as a 100 % image,
    and one set while zoomed is zoomed once construction is over (new widgets) or on the next zoom.
  - The copies belong to ZapThemes: one per image and zoom level, disposed once the app disposes
    the original, or with the display. Don't dispose an image you get from a zoomed widget's
    `getImage()`.
  - Left out: shell icons, carets, table and tree item images (their native image lists keep the
    size of their first image), menu item images, a ToolItem's hot and disabled images, and
    images painted directly in paint listeners. Images reach a zoom only through `setImage`.
  - `themes.setZoomImagesEnabled(false)` gives every widget its own image back, and
    `setData(ThemeKeys.ZOOM_IMAGES, Boolean.FALSE)` does that for one widget and its children.
- **Tool bars** follow the zoom. On Windows the native tool bar measures a button with the font it
  had when the button was added, so ZapThemes measures the items again after a new font.
- **The native menu bar doesn't zoom.** SWT has no font for menus, and Windows draws the menu bar
  with the system menu font, at a height the system sets. Menus that drop down from it don't
  zoom either. On macOS the menu bar is the system's anyway.
- **Windows keep their size.** As in Chrome, the zoom reflows the contents inside the window.

**Turning it off.** `themes.setZoomShortcutsEnabled(false)` turns off the mouse, keyboard and
pinch shortcuts for the display. `setZoom` and the other methods keep working, for a View menu of your
own. The system property `-Dzapthemes.zoomShortcuts=false` starts every display with them turned
off. A control that uses Ctrl + wheel itself, such as a chart that zooms its plot, keeps it with
`chart.setData(ThemeKeys.ZOOM_SHORTCUTS, Boolean.FALSE)`. That covers its children and pinches over
them too, and its listeners then get the events as usual. Elsewhere the shortcuts consume the event, so the widget
under the mouse doesn't scroll. If your menus have their own Ctrl+= / Ctrl+- / Ctrl+0 accelerators,
turn the shortcuts off, or leave the accelerators out of the menu items, so a key doesn't zoom
twice.

### High contrast

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
  "formatVersion": 2,
  "id": "acme-dark",
  "name": "Acme Dark",
  "extends": "zap-dark",
  "dark": true,
  "swatches": { "orange": "#E8590C", "red": "#C62828" },
  "names": {
    "accent":   { "color": "$orange" },
    "danger":   { "color": "$red" },
    "headline": { "color": "$fg", "font": { "name": ["Segoe UI Semibold", "Cantarell", ".AppleSystemUIFont"], "height": 14, "style": "bold" } }
  },
  "defaults": { "background": "$bg", "foreground": "$fg" },
  "controls": {
    "Table":        { "selectionBackground": "$selection", "states": { "hover": { "background": "$hover" } } },
    "Tree":         { "inherit": "Table" },
    "Label[WRAP]":  { "foreground": "$qualifier" }
  },
  "classes":  { "Button.danger": { "background": "$danger" }, "*.headline": { "foreground": "$headline", "font": "$headline" } },
  "platform": { "*": { "darkThemePreferred": true }, "win32": { "darkScrollbars": true } },
  "registries": {
    "colors":       { "HYPERLINK_COLOR": "$accent" },
    "fonts":        { "org.eclipse.jface.headerfont": "$headline" },
    "systemColors": { "COLOR_LIST_SELECTION": "$accent" }
  }
}
```

### Blocks

| Block | Meaning |
|---|---|
| `formatVersion` | The format of the file, now `2`. A file without it is version 1, whose single `palette` held colors and fonts; it still loads, upgraded as it is read: each palette entry becomes a common name with that one part. A file of a newer version than the library is refused with a message to update. Themes are always written in the current format. |
| `id`, `name` | Unique id (what `registry.resolve` takes) and display name |
| `extends` | Id of the theme this one is layered on. Each block is merged key by key, so a theme can change only what differs; a common name merges by part, so giving it only a color keeps the parent's font. A name or swatch replaced here also changes every rule of the parent that refers to it. |
| `dark` | Whether the theme is dark, for tools such as the theme editor. Theming does not read it (use `platform` for what the OS draws). |
| `swatches` | Raw colors, `#RRGGBB` or `#RRGGBBAA`, referenced as `"$name"`: the theme's palette, before any meaning is given to them |
| `names` | Common names (`foreground`, `border`, `heading1`, ...), referenced as `"$name"`. Each is an object holding a `color`, a `font`, or both; either part may itself refer to a swatch or to another name. A reference takes the part its key asks for: a color key (`background`, `foreground`, `...Background`, `...Foreground`, `...Color`) takes the color, a font key (`font`, `...Font`) the font, and any other key the color if there is one, else the font. A name without the part asked for, an unknown name and a reference cycle are errors. |
| fonts | `{ "name": "Segoe UI", "height": 9, "style": "bold\|italic" }`. `name` may be a list, `["Segoe UI", "Cantarell", ".AppleSystemUIFont"]`: the first family installed is used, so one theme fits every platform. `"inherit": "$name"` starts from another name's font and overrides only what it gives. A missing name or height keeps the system font's. |
| `defaults` | Properties for every widget, applied first |
| `controls` | Rules by control type. Keys: `Type` (`"Button"`, `"Grid"`, `"FlatButton"`, `"NatTable"`, and figure types such as `"Axis"`), or `Type[BIT\|BIT]` (`"Button[CHECK]"`, `"Text[MULTI\|WRAP]"`), which applies when the widget has all of those SWT style bits. `"inherit": "Other"` starts a rule from another type's rule. |
| `classes` | Rules a widget opts into with `themes.setThemeClass(widget, "danger large")`: `Type.cls` for one type, `*.cls` for any. The JFace fork sets `banner`, `title`, `info` (tooltips and hovers), `popup` (`PopupDialog`; its info text also `muted`), `error` (status line errors), `plain` and `pane`; the notifications fork sets `notification` (the popup's frame) and `popup` (its content); the JFace Text fork sets `ruler`, `popup` (hovers, content assist; status lines also `muted`) and `frame` (context information): give them rules, as the bundled themes do |
| `states` (inside a rule) | Overrides while a widget is `hover`, `pressed`, `selected`, `focused` or `disabled` |
| `platform` | What the OS draws itself: `*` for all platforms, then `win32`, `gtk` or `cocoa` |
| `registries` | Named resources of other toolkits: JFace `colors` and `fonts`, and `systemColors`, which replaces `Display.getSystemColor` constants inside the Nebula and JFace Text forks |
| `editor` | The theme editor's own state (generator settings, locked swatches). Theming ignores it, and reading and writing a theme keeps it. |

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

`ThemeValidator.validate(registry.resolve(id))` returns every problem with its path: unknown names,
names without the part a key asks for, reference cycles, swatches that are not plain colors, a
swatch and a name of the same name, malformed colors and fonts, unknown style bits, broken `inherit` chains, and malformed
class or state rules. An empty list means the theme is valid. Run it in a unit test for each of your
themes, as [ThemeValidatorTest](../zapthemes-core/src/test/java/com/zapthemes/core/ThemeValidatorTest.java)
does for the bundled ones.

`PropertyCatalog.load().undescribed(theme)` lists what a theme sets that no module on the class path
describes: a misspelled type, property, style class, state, registry entry or platform setting, which
the validator cannot tell from a styler's own key. See [the property catalog](#the-property-catalog).

### The property catalog

`PropertyCatalog.load()` describes everything a theme can set, as the modules on the class path
declare it: each control type with its properties (key, kind, and the common name it takes by
default), style-bit variants and owner-draw states; the style classes the forks set; the
`registries` entries; and the `platform` settings. The theme editor lists it, and the defaults
together make the *default map*. `BaseNames` lists the common names those defaults refer to
(`background`, `surface`, `raised`, `foreground`, `muted`, `border`, `gridLine`, `accent`, ...).

## 5. Make your own widgets theme-aware

Do what the Nebula proxies do (see [Gauge](../zapthemes-samples/src/main/java/com/zapthemes/samples/Gauge.java)):

1. Expose the look through setters: `setNeedleColor(Color)`, `setThickness(int)`. Accept `null` as
   "back to my default".
2. Call `ThemeManager.applyOnCreate(this, "Gauge")` as the last statement of the constructor. It
   tags the widget and applies the current theme.
3. Register a styler for the type in a `StylerProvider`, listed in
   `META-INF/services/com.zapthemes.core.style.StylerProvider`. A `PropertyStyler` maps theme keys to
   your setters; override `apply` to `redraw()` afterwards.
4. Write the widget's rule in your themes (`"Gauge": { "needleColor": "$accent" }`), against common
   names so that it follows each theme.
5. Describe the type for tools such as the theme editor: implement `CatalogProvider` (listed in
   `META-INF/services/com.zapthemes.core.catalog.CatalogProvider`) and add a `TypeSpec`.
   `TypeSpec.reflect` finds a property per color or font setter; `.defaults("needleColor=accent")`
   gives each the common name it takes by default. [GaugeStylers](../zapthemes-samples/src/main/java/com/zapthemes/samples/GaugeStylers.java)
   does both steps 3 and 5.

A widget that paints from the theme directly can instead call `themes.styleOf(this)` in its paint
listener, as `FlatButton` does. To write a styler from scratch, implement `ControlStyler`. It must
set every property it owns, with `null` where the theme has none, so that switching themes leaves
nothing from the previous one behind.

## 6. Tips

- Don't dispose fonts you get from a theme (`getFont()` on a themed widget, `ThemeContext.font`):
  they are cached and shared until the display is disposed, one per font and zoomed height. Theme colors are safe to dispose,
  because each caller gets its own.
- To restyle one widget without a new theme, give it a class and add a class rule. Calling
  `setBackground` directly works, but it lasts only until the next theme switch.
- Toolkit-level resources (JFace `ColorRegistry` and `FontRegistry` keys, system colors) go in
  `registries`, not in control rules.
- To try a theme quickly, keep it in a file and pass it to the custom-theme sample:
  `./gradlew :zapthemes-samples:runCustomTheme --args=$PWD/my-theme.json`. It prints validation
  problems to stderr.

## 7. The ribbon

`zapthemes-widgets` has an Office-style `Ribbon` (see [the ribbon plan](ribbon-plan.md) and
`RibbonDemo`). It paints everything from these theme types; each property defaults to the common
name in brackets, so a theme on common names only sets what it wants different.

| Type | Properties |
|---|---|
| `Ribbon` | `background` (raised), `foreground`, `tabStripBackground` (chrome), `tabForeground`, `tabHoverBackground` (hover), `selectedTabBackground` (chrome: then only the underline shows), `selectedTabForeground` (accent), `tabIndicatorColor` (accent), `tabFont`, `groupLabelForeground` (muted), `groupLabelFont` (caption), `separatorColor`, `borderColor` (border), `itemHoverBackground` (hover), `itemPressedBackground`, `itemCheckedBackground` (selection), `itemCheckedBorderColor` (accent), `disabledForeground` (disabled), `fieldBackground`, `galleryBackground` (surface), `galleryBorderColor`, `gallerySelectionColor`, `keyTipBackground` (badge), `keyTipForeground` (onBadge), `keyTipBorderColor`, `fileTabBackground`, `fileTabForeground`, `emphasisBackground`, `emphasisForeground` (accent, onAccent), `contextualAccent1` … `4` (accent, info, success, warning), `focusColor` (focus), `useAppAccent` (true), `arc` (4) |
| `RibbonPopup` | `background`, `borderColor`: collapsed groups, the collapsed ribbon's overlay, galleries, color palettes |
| `RibbonToolTip` | `background`, `foreground`, `borderColor`, `titleFont` (strong) |
| `RibbonBackstage` | `background`, `foreground`, `navBackground` (accent), `navForeground` (onAccent), `navHoverBackground`, `navSelectedBackground`, `navSelectedForeground`, `navFont`, `titleFont` (heading1), `useAppAccent`; its pages use the style classes `backstage` (Composite) and `backstageTitle` (Label) |
| `RibbonMessageBar` | `background`, `foreground`, `borderColor`, `iconColor`, `titleFont`, and the states `info`, `warning`, `error`, `success` |

`chrome` (the ribbon's tab row; title bars have names of their own), `badge` and `onBadge` are common names for
these. An app's brand color (`Ribbon.setAccentColor`, Word blue or Excel green) replaces the
accent keys unless the theme sets `useAppAccent: false`, as zap-high-contrast does.

Commands are shared with other bars (the title bar's quick access toolbar) through
`com.zapthemes.widgets.command`: one `CommandRegistry` per shell, and one `CustomizationStore`
file with a section per bar. JFace apps add `zapthemes-jface-ribbon` for actions as commands and a
`RibbonApplicationWindow`.
