# Theme editor plan

A wizard for building ZapThemes themes without writing JSON. It has four steps:

1. **Palette:** pick a palette, generate a new one, or start by duplicating an existing theme.
2. **Common names:** see the colors mapped to common names (Foreground, Background, Heading1, ...),
   and add more names.
3. **Colors and fonts:** assign a color and a font to each common name.
4. **Controls:** every known control type, with its properties set to a common name, or
   overridden with a color or font of their own.

It ships as a library and an app. `zapthemes-editor` has a JFace `ThemeWizard` that any ZapThemes
app can open, for example from its preferences, plus a standalone app
(`./gradlew :zapthemes-editor:run`). Both are built on ZapThemes itself.

Publishing to Maven Central stays on hold until the user says everything works. Each step below
ends in its own PR, with tests, demo coverage, and screenshots on Windows, GTK and macOS.

## Decisions (asked and answered)

| Question | Answer |
|---|---|
| Delivery | A library (an embeddable wizard) and a standalone app |
| Palette generator, first version | Harmony rules; lock and regenerate; extract from an image; contrast checks |
| What a common name holds | A color and a font in general. Some names hold only one: `border` is only a color, `code` is only a font |
| Step 4 scope | Control types plus their style-bit variants (`Button[CHECK]`), style classes (`Button.primary`) and states (hover, disabled) |
| Preview | A live preview on every page |
| Saving | A full standalone theme: every rule is written out, with no `extends`, so it doesn't depend on library updates |
| Bundled themes | Rewrite zap-base, zap-light, zap-dark and zap-high-contrast on common names: add base names where needed, and avoid literal colors and fonts where a name fits |
| Versioning | Theme JSON gets a format version, and themes are upgraded on load after library updates |
| Re-editing | Round-trip: opening a saved theme fills all four steps, and nothing the editor knows is lost |

## Terms

The user-facing words don't match today's JSON, so the format changes with them:

- **Palette** (step 1): a set of raw colors, called *swatches*, with no meaning yet. Generated,
  picked, or taken from a theme. In JSON: a new `swatches` section.
- **Common name** (steps 2–3): a meaning such as `foreground`, `border` or `heading1`, holding a
  color, a font, or both. In JSON: `names` replaces today's `palette`, and `"$heading1"` keeps
  referring to one.
- **Property** (step 4): a key of a control rule (`Table.headerBackground`), whose value is a
  common name or an override.
- **Default map:** the library's mapping from each property to the common name it uses unless a
  theme says otherwise. For example, `Table.headerBackground` defaults to `$surface`.

## Theme format v2

```json
{
  "formatVersion": 2,
  "id": "ocean-dark",
  "name": "Ocean Dark",
  "dark": true,
  "swatches": { "ink": "#0E1A24", "deep": "#16283A", "sea": "#2AACB8", "foam": "#DDE7EE", "coral": "#F07178" },
  "names": {
    "background": { "color": "$ink" },
    "surface":    { "color": "$deep" },
    "foreground": { "color": "$foam", "font": { "name": ["Segoe UI", "Cantarell", ".AppleSystemUIFont"], "height": 9 } },
    "border":     { "color": "#2B4256" },
    "accent":     { "color": "$sea" },
    "heading1":   { "color": "$foam", "font": { "inherit": "$foreground", "height": 14, "style": "bold" } },
    "code":       { "font": { "name": ["Cascadia Mono", "DejaVu Sans Mono", "Menlo"], "height": 10 } }
  },
  "defaults": { "background": "$background", "foreground": "$foreground", "font": "$foreground" },
  "controls": { "Table": { "headerBackground": "$surface", "selectionBackground": "#1F4F6E" } },
  "classes": {}, "colors": {}, "fonts": {}, "platform": {},
  "editor": { "libraryVersion": "1.1.0", "generator": { "rule": "analogous", "base": "#2AACB8", "locked": ["ink"] } }
}
```

- **References:** `"$x"` resolves by the type of the key it's used in. A color key (`background`,
  `...Color`, `...Background`, `...Foreground`) takes the name's color, and a font key (`font`,
  `...Font`) takes its font. Using a name that lacks that part is a validation error. Names can
  refer to swatches and to other names, and cycles are reported.
- **Font fallbacks:** `"name"` may be a list. The first font installed on the system wins, so one
  standalone theme works on every platform. `"inherit"` derives one font from another.
- **`formatVersion`:** a theme without it is version 1. `ThemeJson` upgrades it in steps on load
  (v1 → v2: `palette` colors become `names` color entries, `font.*` entries become font-only names,
  and every `$ref` is rewritten). A later version that adds control properties fills them in on
  load from the default map, using the theme's own names. Saving always writes the current version.
- **`editor`:** generator settings, locks, and the library version the theme was written with.
  The runtime ignores it, and the editor reads it when it reopens the theme.
- **`extends`:** still loads for older themes and other users' themes. The editor flattens it when
  it duplicates or saves a theme.
- The schema, `ThemeJson.write` and the validator (`zapthemes-themes`) all follow these changes.

## Property catalog and default map

Today stylers read their keys in code (`context.color(style, "headerBackground")`) and declare
nothing, so step 4 has no list to show. A catalog fixes that:

- `PropertySpec(key, kind, defaultName, ...)`, where `kind` is color, font, int, boolean, string
  or choice, grouped by type in a `TypeSpec`. Each module describes its types through a
  `CatalogProvider` service, implemented next to its styler provider (the same class, where there
  is one); figure types are described by the visualization module.
- `PropertyCatalog.load()` covers SWT and JFace types, the Flat widgets, Nebula, Nebula
  incubation, NatTable, Visualization figures, the jface.text classes (`*.ruler`, `*.frame`, syntax
  keys) and the JFace registry keys.
- Each type also lists its style-bit variants, the style classes the library uses (`popup`,
  `primary`), and its owner-draw states.
- The default map is the `defaultName` of every spec. It's what step 4 shows as "(default)", and
  what upgrades on load fill in.
- **Tests:**
  - every key a bundled theme uses is in the catalog;
  - every key a styler reads is in the catalog, by recording what `ResolvedStyle` is asked for
    while the demos' smoke tests run, on every CI platform;
  - every `defaultName` exists in the base names.

## Base common names

| Group | Names | Holds |
|---|---|---|
| Surfaces | `background`, `surface`, `raised`, `stripe`, `hover`, `selection` | color |
| Text | `foreground` (the default font), `muted`, `disabled`, `onAccent`, `link`, `selectionForeground` | color + font, or color |
| Headings | `heading1`, `heading2`, `heading3`, `caption`, `strong` | color + font |
| Lines | `border`, `gridLine`, `focus` | color |
| Accent and status | `accent`, `error`, `warning`, `success`, `info` | color |
| Code | `code` (font), `keyword`, `string`, `comment`, `number` | font, or color |

Users can add names in step 2 (`badgeBackground`, say) and pick them anywhere in step 4. Each base
name declares its contrast pairs (`foreground` on `background`, `onAccent` on `accent`, ...), which
the contrast checks use.

## The wizard

All pages share a preview pane on the right that is re-themed on every edit. It holds a button
row, check and radio buttons, a text field and a combo, a table and a tree, tabs, a styled text
with headings and code, a progress bar, a Nebula Grid, and a small XYGraph. In step 4, clicking a
control in the preview selects its type in the list.

1. **Palette**
   - Three choices: pick a saved palette or a bundled theme's swatches; *new palette*; or
     *duplicate a theme*, which loads a theme file or a bundled theme and prefills every step.
   - The generator:
     - a swatch strip (5–8 colors, any can be locked);
     - harmony rules from a base color (complementary, analogous, triadic, tetradic,
       monochromatic);
     - *Regenerate* re-rolls the unlocked swatches;
     - per-swatch hue, saturation and lightness sliders;
     - a tints-and-shades row for each swatch (in OKLCH, so the steps look even);
     - *From image* extracts dominant colors with k-means on the image's pixels;
     - a light/dark toggle that picks the background end.
   - Palettes can be saved for reuse in the user's config directory
     (`~/.zapthemes/palettes/*.json`).
2. **Common names**
   - A table of names (grouped as above) showing the color and font each one gets.
   - On entry, the names are filled automatically from the palette:
     - background from the darkest or lightest swatch, depending on light/dark;
     - surface and stripe as small lightness steps from it;
     - foreground as the swatch with the best contrast on the background;
     - accent from the most saturated swatch;
     - status colors moved toward red, amber, green and blue.
   - *Add name*, *Rename* and *Remove* work only on names nothing uses, or the uses are reassigned
     first. Each name has a kind: color, font, or both.
3. **Colors and fonts**
   - For each name: a color, picked from the swatches, their tints and shades, or a custom color
     (`ColorDialog`); and a font (family fallback list, size, style, or "inherit from another
     name").
   - Contrast badges (WCAG ratio, AA/AAA) on every declared pair, with warnings below 4.5:1 for
     text.
4. **Controls**
   - A tree of every catalog type, grouped by library (SWT, JFace, Flat, Nebula, NatTable,
     Visualization, Text), with child nodes for variants, classes and states. A filter box sits on
     top.
   - Selecting a node shows its properties in a table with these columns: property, kind, common
     name, and override.
     - The common-name combo lists the compatible names. The default-map entry shows as
       "(default)".
     - *Override* sets a literal color or font for this rule only, shown in italics with a reset
       button.
   - *Add variant / class / state* creates a new rule node.
5. **Finish**
   - id, name, dark flag, file location;
   - validation (references, contrast, cycles);
   - Finish writes the full standalone v2 theme.
   - The embeddable wizard also returns the `Theme`, and can register and apply it.

Cancel discards edits. A page you change re-runs the auto-fill of later pages only for values you
haven't set yourself.

## Steps

| # | Step | What | Why this order |
|---|---|---|---|
| 1 ✅ | **Theme format v2** | `formatVersion`; `swatches`, `names` with color + font, references resolved by key type, chained references with cycle errors, font fallback lists and `inherit`, the `editor` section, `dark`; v1 → v2 upgrade on load; the schema, `ThemeJson.write`, and validator messages. Old themes keep loading. | Everything else is built on this format. |
| 2 ✅ | **Property catalog + default map** | `PropertySpec`, registration in every styler provider and `FigureTheming`, variants, classes and states per type; the catalog coverage tests above. | Step 4 of the wizard and upgrades on load both need it. |
| 3 ✅ | **Bundled themes on common names** | Rewrite the four bundled themes as v2 with the base names, turning literals into names where one fits. **Equivalence test:** every catalog type, variant, class and state resolves to the same colors and fonts before and after, and screenshot diffs show no change. | Shows that the format and names are enough for real themes before any UI exists. |
| 4 ✅ | **Scoped preview themes (core)** | `ThemeManager.preview(Composite, Theme)` themes one subtree with a theme of its own, independent of the display's theme, and follows edits without disposing anything. This is needed because the wizard itself must stay readable while the theme being edited may be half-finished. | The riskiest core change: stylers, Skin listeners, registries and figures all assume one theme per display. |
| 5 ✅ | **Palette model and generator** | Pure Java with no UI: OKLCH/HSL conversions, harmony rules, lock and regenerate (seeded, so tests are repeatable), tints and shades, k-means on `ImageData`, WCAG contrast, auto-fill from palette to names. Unit tests run headless. | Testable on its own; pages 1–3 are thin on top of it. |
| 6 ✅ | **Wizard: palette, names, colors and fonts** | `zapthemes-editor` with `ThemeWizard`, pages 1–3, the preview pane, and the editing model (an undo stack per page; "set by user" vs auto-filled). | |
| 7 | **Wizard: controls, finish, round-trip** | Page 4 (tree, property table, overrides, adding variants, classes and states), the finish page, saving, reopening; a round-trip test (open, then save, gives the same file) on every bundled theme. | |
| 8 | **App, docs, CI** | The standalone app (`run`, `--smoke`, screenshots of each page in each theme); opening the wizard from the JFace demo and ZapNotes preferences; the theme authoring guide's "Theme editor" section and the v2 format; the README run list; CI smoke tests and screenshots on all platforms. | |

## Risks

- **Scoped themes (step 4):** if a subtree can't be themed apart reliably on every platform, the
  fallback is to theme the whole wizard with the edited theme, plus a *Safe mode* button that
  switches it back to the display's theme. Decide this before step 6.
- **Catalog drift:** a styler reading a key the catalog doesn't list. The recording test in step 2
  catches it, and it runs in CI.
- **Fonts across platforms:** fallback lists help, but sizes render differently on each platform.
  The preview shows the current platform only, so the guide says to check the theme on each one.
- **Upgrade semantics:** a standalone theme that gets new properties from the default map may pick
  a name the author never meant. Upgrades record what they added in `editor.upgraded`, and the
  editor flags those properties when the theme is reopened.
- **Generator quality:** harmony rules alone give garish UI palettes. The auto-fill in step 2
  (contrast-driven, with small lightness steps for surfaces) matters more than the generator.
  Judge it on screenshots.

## Assumptions to confirm

- Saved palettes live in `~/.zapthemes/palettes` (per user, outside any app). An app embedding the
  wizard can set a different directory.
- The wizard is a JFace `Wizard` in a `WizardDialog`, so apps already on the forked JFace get it
  directly. A plain-SWT host has to depend on `zapthemes-jface`.
- No machine-learning suggestions (Colormind-style) and no community gallery (Color Hunt-style) in
  the first version.

## Step 1 in detail

`Theme` is now format 2: `formatVersion`, `dark`, `swatches`, `names` (each `{ "color", "font" }`)
and `editor` replace `palette`. `ThemeJson.read` parses to a JSON tree, refuses a newer format with a
message to update, and runs `ThemeUpgrades` a step per version; v1 → v2 makes each palette entry a
name with one part (a font if the entry is a font object or refers to one, else a color; a reference
to an entry of a parent theme is a font when its name starts with `font`) and takes `dark` from
`darkThemePreferred`. `ThemeJson.write` always writes the current format, `formatVersion` first.

`ThemeNames` resolves `$name` for the part a read asks for (`ResolvedStyle.color`, `font`, or
`value` for any other key: the color, else the font), through swatches and chains of names, with
errors for an unknown name, a name without the part, a swatch read as a font, and cycles
(`$a -> $b -> $a`). `StyleResolver.names()` and `ThemeNames.of(theme)` read a name's color or font
directly. Fonts take a family list (`ThemeResources` uses the first installed one; a single family is
passed through as before) and `inherit`. `extends` merges names by part. The validator checks
swatches (plain colors, no clash with a name), names (only `color` and `font`, both resolve) and
every reference; the schema documents the new sections and is now valid JSON.

The bundled themes stayed version 1 until step 3, so every test, demo and smoke test ran them through
the upgrader. `ThemeFormatTest` covers the format, and checks that every reference in the bundled
themes resolves after the upgrade to the same value as under version 1's rules (over 300
references) and that the upgraded themes write and read back unchanged; it also checks that the
schema documents every section of `Theme`. The sample `acme-dark.json` and the authoring guide use
format 2.

## Step 2 in detail

`com.zapthemes.core.catalog`: `PropertyCatalog.load()` merges what core describes (`CoreCatalog`:
the 47 SWT widget types with the base properties of their kind, the built-in stylers' extra
properties, `systemColors` and the platform settings) with every `CatalogProvider` on the class
path: the Flat widgets, Nebula and Nebula incubation (a property per color or font setter of each
proxy, found by `TypeSpec.reflect`, plus the dotted and gradient keys their stylers set), NatTable
(inheriting Table's), the Visualization figures (each class inheriting its superclass, as figure
rules apply), JFace (its style classes and its `colors` and `fonts` registry names), JFace Text
(rulers, frames, syntax colors), notifications, and the sample Gauge. A type described twice is
merged. `BaseNames` lists the common names; default names were taken from zap-dark's references,
renamed to the base names (`bg` is `background`, `muted` is `raised`, `qualifier` is `muted`, `fg` is
`foreground`), with grid lines defaulting to `gridLine` and focus colors to `focus`.

`PropertyCatalog.undescribed(theme)` lists what a theme sets that the catalog does not describe;
`describesRead(owner, key)` answers for a key a styler reads. `KeyRecorder`
(`-Dzapthemes.recordKeys=<dir>`) records, per type, the keys stylers read; the demo smoke tests
record, and `:zapthemes-demo:checkCatalog`, run by `:zapthemes-demo:smokeTest`, fails on any key
read but not described (478 keys over 135 types). Making it pass found that `DefaultStyler` read
background, foreground and font of widgets it then did not set (menus, columns, carets); it now
reads only what it sets. It also found three unproxied Nebula parts themed by name and the plain
system colors the forks look up. The bundled themes lost two rules nothing read: `Button[PUSH]`
states and the high-contrast `Button[PUSH].borderColor` (native buttons paint neither).

Tests: `PropertyCatalogTest` (base names, the core types and defaults, reflection, merging,
`undescribed`, `describesRead`) and, in zapthemes-demo with every module, `CatalogCoverageTest`
(every module described, every bundled theme fully described, every default a base name with the
right part).

## Step 3 in detail

The bundled themes are format 2 on the common names. zap-dark and zap-light had the same rules with
different colors, so `zap-base` now holds every rule, written against names, plus the fonts
(`foreground`'s font is the default font; `heading1`-`heading3`, `strong` and `caption` derive from it
with `inherit`; `code` lists monospace families). zap-dark and zap-light give every base name a color
and keep only what really differs (dark's title bar hover, pressed, search and focus colors, and the
platform's dark flags); zap-high-contrast keeps only its overrides of zap-dark. 1,213 lines became 703.

Literal colors became names: syntax colors are `keyword`, `string`, `comment` and `number`; the
ribbon's contextual accents are `info`, `success` and `warning`; the message bar's backgrounds and
the title bar's account circle got theme names of their own (`warningBackground`, ..., `avatar`).
References use the most specific name that fits: `selectionForeground`, `link`, `focus` and
`gridLine` instead of the foreground, accent and raised colors they equal, so a theme can change them
apart. In high contrast the title bar's literal search and focus colors now follow its names.

**Equivalence test:** `BundledThemeStylesTest` (zapthemes-demo, with the whole catalog) resolves every
catalog and theme type (what differs from `defaults`; a figure's own rule), every style-bit and class
rule, every state, registry and platform setting of each bundled theme, with references followed to
colors, fonts and values, and compares it with a reviewed snapshot (`bundled-theme-styles.txt`). It was
written from the version 1 themes and passes unchanged with the rewrite. It stays as a regression
check: a change to a bundled theme that changes how it looks fails it and writes the new snapshot to
`build/` for review.

**Older themes that extend a bundled theme:** a version 1 theme referring to the old entry names
(`$bg`, `$qualifier`, `$font.strong`, ...) or overriding them would have broken. The v1 -> v2 upgrade
renames them when the theme extends a bundled theme, and pins `strong` and `heading1` to their old
fonts when the theme changes `font.default` (they now derive from it). `ThemeFormatTest` checks that a
v1 child of each bundled theme looks as it did over the v1 bundled themes, kept as test resources
(which also keep the upgrade's real-size test). The samples' themes use the new names.

Found while rewriting: a theme's `states` replace the parent's whole `states` object, so high
contrast's `FlatButton` has only its `focused` state (no hover, pressed or disabled colors) and its
`Ribbon` only `hover` and `pressed`. The rewrite keeps that; whether to merge states by state is a
question for later.

## Step 4 in detail

`ThemeManager.preview(Composite, Theme)` themes a composite and its subtree with a theme of its own.
Calling it again with an edited theme re-themes the same widgets in place; `endPreview` or disposing
the composite gives the subtree the theme around it again. Each preview keeps its own `StyleResolver`.
Every lookup that used the display's resolver now asks for the widget's: the nearest preview among
its parents, or the display's. A control's parent is `getParent()`; an item, a scroll bar or a caret is
reached through its own `getParent()`. A shell is always the display's, so dialogs and popups opened
from a preview are too. Nested previews work.

What follows the preview:
- `styleOf`, the stylers, and widgets created in the subtree later, both proxies and raw widgets
  themed through `SWT.Skin`.
- **Platform settings, per control.** On Windows the dark scrollbars, combos and tree arrows now use
  the `DarkMode_Explorer` and `DarkMode_CFD` window themes. These are dark whatever the
  application's mode, so a dark preview in a light window gets dark native parts. SWT's display-wide
  switches (`beforeTheme`) stay with the display's theme.
- **Widgets that paint from the theme.** `addThemeChangeListener(Widget, listener)` is called when
  the theme of that widget changes, through `setTheme` or a preview, and is removed on dispose. The
  ribbon and the title bar use it instead of the display-wide listener.
- **draw2d figures.** `FigureTheming` finds the canvas showing a figure through its
  `SWTEventDispatcher`, then reads rules with `ruleOf(canvas, type)` and listens on the canvas.
- **System colors.** The Nebula and JFace Text forks call `SystemColors.get(display, id)` without a
  widget, so `ambientRegistry` decides. While a preview is shown, it is the theme of the widget being
  themed, or else of the last painting or input event's widget, set by display filters that are
  installed only while a preview exists. A system color read outside any event (from a timer, say)
  gets the theme of the last event's widget.

What stays display-wide: shells (title bars, the Windows menu bar), menus, tool tips, dialogs, JFace's
`ColorRegistry` and `FontRegistry`, and the zoom.

Tests: `PreviewThemeTest` (zapthemes-swt) covers:
- only the subtree, items included, takes the preview;
- an edit in place disposes nothing;
- widgets added later get the preview;
- `setTheme` leaves the preview alone, and `endPreview` restores the display's theme;
- nested previews, and disposal;
- a shell cannot be previewed;
- widget listeners;
- system colors in paint events inside and outside the preview.

`RibbonTest` and `FigureThemingTest` check that a ribbon and an XYGraph in a preview follow it.
`PreviewDemo` (`./gradlew :zapthemes-demo:runPreview`) shows the same controls in the window's theme
and in a previewed one: buttons, check and radio buttons, text, combo, table, tree, styled text, a
Nebula Grid, an XYGraph and a progress bar. Its smoke test (`smokeTestPreview`, part of
`smokeTest`) checks every pair of bundled themes, an edit in place and the end of a preview; its
screenshots include the Grid and chart tabs.

The fallback in Risks (theme the whole wizard, with a Safe mode button) is not needed.

Open, seen in screenshots:
- On Windows, a light preview in the high-contrast window shows its single-line text field dark in
  `Control.print` captures. It is the same without the `DarkMode_` change, and GTK draws it light.
  Whether the screen shows it too is still to be checked.
- The Nebula Grid's unfocused selected row has dark text in zap-dark, inside or outside a preview.

## Step 5 in detail

The new module `zapthemes-editor` holds the palette model in `com.zapthemes.editor.palette`. It is
pure Java over SWT's `RGB` and `ImageData`, which need no display, so every test runs headless.
Step 6 adds the wizard to the same module.

- **`Oklch` and `Hsl`:** conversions from and to sRGB. `Oklch.toRgb()` brings an out-of-gamut color
  in by reducing chroma, keeping lightness and hue. `Oklch.distance` is the OKLab distance.
- **`Contrast`:** WCAG relative luminance, ratios and levels (AAA, AA, AA large, fail).
  `atLeast(color, minimum, against...)` moves a color's OKLCH lightness just far enough to reach a
  ratio on every background, keeping its hue.
- **`Harmony`:** complementary, analogous, triadic, tetradic and monochromatic, as hue offsets in
  OKLCH. The ids are what `editor.generator.rule` holds.
- **`PaletteGenerator(seed)`:** 5 to 8 swatches.
  - Three neutrals tinted with the base hue: `ground`, `panel` and `ink`, at the dark or light end
    as asked.
  - Accents on the rule's hues: `accent1`, `accent2`, ... (no swatch is named like a base name,
    since a swatch shadows a name of the same name). Lightness and chroma stay in ranges that suit
    interfaces.
  - `regenerate` re-rolls every unlocked swatch. Every swatch draws the same random values whether
    or not it is locked, so locking one leaves the others as they would be.
  - The same seed and the same calls give the same palettes.
- **`TintsAndShades.of(color, steps)`:** even OKLCH lightness steps from 0.97 to 0.18.
- **`ImagePalette.extract(imageData, k, seed)`:** k-means++ in OKLab on up to 20,000 sampled
  pixels, transparent ones left out, the most common color first.
- **`NameFill.fill(palette, userSet)`:** a color for every base name with a color part.
  - Background: the darkest or lightest swatch.
  - Surfaces and lines: lightness steps from it.
  - Foreground: the swatch with the best contrast, brought to 7:1.
  - Accent: the most saturated swatch. Selection, link, focus and badge follow from it, and
    `onAccent` is white or black.
  - Status colors: the accent's chroma moved to red, amber, green and blue. Code colors: hues
    around the accent's.
  - Every declared pair reaches its minimum. Colors the user set are kept, and the colors derived
    from them follow.
- **`ContrastCheck`:** every pair the base names declare (`NameSpec.contrastWith`), with ratio and
  level. The minimum is 4.5:1, or 3:1 for `disabled`.
- **`Palette`:** swatches, dark, and the rule and base when generated. `Palette.fromTheme` takes a
  theme's `swatches`, or else up to 8 of the most distinct colors its base names resolve to.
  `toSwatches()` gives a theme's `swatches` section.
- **`PaletteStore`:** saved palettes, as JSON in `~/.zapthemes/palettes` or a directory the app
  chooses. The swatches are in a theme's `swatches` shape and the settings in its
  `editor.generator` shape; a newer format is refused.

Tests (37):
- the conversions match published values and round-trip all of sRGB;
- gamut mapping, contrast ratios and `atLeast`;
- even tints and shades;
- generator determinism, ranges, rule hues, locks and regenerate;
- k-means on synthetic images, including noise, transparency and large images;
- auto-fill: 400 generated palettes (40 seeds, every rule, light and dark, sizes 5 to 8) all pass
  the contrast checks, user-set colors win, and the bundled themes' palettes fill readable names;
- palette files.

A rendered sheet of filled palettes, every rule in light and dark, looked like plausible UI themes.

**Found, then fixed:** the bundled zap-dark and zap-light, older than the declared pairs, fell short
on some of them; high contrast passed them all. Each failing color moved the least it could (OKLCH
lightness, hue kept) to pass, and the test now requires every bundled theme to pass every pair:
- **both:** the accent is a little darker, so white text on it passes (dark `#316FEB`, light
  `#2D6AE6`), and comments are lighter on dark and darker on light;
- **zap-dark:** links have their own lighter blue (`#5992FE`), and muted and disabled are a step
  lighter;
- **zap-light:** warning is a shade darker, the title bar takes the accent, and the inactive title
  bar is a muted slate blue (`#586E97`) instead of a pale blue that its text could not be read on.

## Step 6 in detail

`com.zapthemes.editor` holds the wizard, on an editing model with no UI.

**`EditorModel`** is what the pages edit:
- **The palette** (page 1), and the **base** the edited theme is layered on (`Theme.over`): the
  duplicated theme, or else `zap-dark` or `zap-light` by the palette's light/dark setting. Every
  rule of the base stays, so the preview and the result theme every control.
- **Names you added**, each holding a color, a font or both (page 2). A name must start with a
  letter, and may not be a base name, another added name or a swatch.
- **The colors and fonts you set** (page 3). A color is a literal (`ColorChoice.Literal`) or a
  reference to a swatch (`ColorChoice.Ref`, written `"$swatch"`, so it follows the palette). A font
  (`FontChoice`) is a family list, a size, a style, and optionally a name it inherits from.
- **Everything else is derived.** Colors you have not set are filled from the palette (`NameFill`)
  on every change; fonts you have not set are the base theme's. An added name starts with the
  accent's color and the foreground's font. A reference to a swatch the palette no longer has
  counts as unset.
- **`theme()`** is the theme all this gives: `swatches`, `names` (colors that equal a swatch are
  written as references), `dark`, and `editor.generator` (rule, base, locked swatches).
- **Duplicating a theme** takes its palette (`Palette.fromTheme`, with swatches renamed apart
  from the names, e.g. `backgroundSwatch`), its added names, and every name's color, marked as the
  theme's (`Source.THEME`) until you change it. *Fill from palette* (page 2) forgets them.
- **Undo, per page.** Each edit records how to undo and redo itself on its page's `UndoStack`, so
  undoing on one page leaves the others' edits alone. A run of edits to one swatch, color or font
  (a slider drag) undoes as one. Duplicating undoes as a whole.
- Renaming an added name renames whatever refers to it. Removing one, or dropping a part it holds,
  is refused while something refers to it. Font inheritance cycles and color reference cycles are
  refused.

**`ThemeWizard`** is a JFace `Wizard`; `ThemeWizard.dialog(shell, wizard)` opens it at a size
that fits the pages and the preview side by side, and `result()` is the theme Finish gave (null
after Cancel). Each page is a `WizardPage` with its controls on the left, a **`PreviewPane`** on
the right (`ThemeManager.preview`, so the wizard keeps the application's theme), and Undo and Redo
buttons that name the edit (also Ctrl+Z, and Ctrl+Y or Ctrl+Shift+Z, outside text fields). Each
page has its own preview pane, so the wizard works in any `WizardDialog`. The pane has a styled
document showing the headings, body, link, muted, strong, status and code names in their colors and
fonts, buttons (primary, disabled), check and radio buttons, a text field and a combo, a table and a
tree, a Nebula Grid and an XYGraph in tabs, and a progress bar.

1. **Palette:** duplicate a bundled theme or a theme file; use a saved palette or a bundled
   theme's colors; or generate (rule, base color, 5 to 8 swatches, dark or light, Regenerate,
   colors from an image, Save palette). A swatch strip with Lock; the selected swatch has hue,
   saturation and lightness sliders and a row of tints and shades.
2. **Common names:** a tree of the names by group, with what each holds, its color, its font and
   where its color comes from (palette, theme, you). *Add name* adds a color name and starts
   renaming it in place; added names are renamed in place (F2, Rename, or a second, slow click),
   and what they hold is chosen in place too. *Remove* and *Fill from palette*.
3. **Colors and fonts:** the names with their colors and worst contrast. For the selected name: the
   swatches, the tints and shades of one, a custom color, or *Fill from palette*; the font's
   families (the first one installed wins), size, bold and italic, the name it starts from, a font
   dialog, or the base theme's; and every contrast pair it is part of, with ratio and level.

Finish, for now, gives the theme; the finish page and saving come in step 7.

Tests:
- `EditorModelTest` (14, headless): new palettes give readable, valid themes on the right base;
  user-set colors survive regenerate; swatch references follow; per-page undo, and drags undoing as
  one; adding, renaming (references follow) and removing names; fonts and inheritance; duplicating
  every bundled theme keeps every base name's color and font and the rules of sample types; Fill
  from palette; swatch names apart from names; dangling swatch references.
- `ThemeWizardTest` (8): the wizard driven through its controls: preview and window themes apart;
  Lock, Regenerate, the hue slider (a drag undoes as one) and tints; duplicating, and colors from an
  image; saved palettes used and deleted; adding a name and renaming it in place, with refused
  names shown as the page's error; a swatch, a tint and a font for a name, with its contrast pairs;
  Finish and Cancel; Ctrl+Z and Ctrl+Y undoing only the page showing.
- `ThemeWizardDemo` (`./gradlew :zapthemes-demo:runThemeWizard`). Its smoke test
  (`smokeTestThemeWizard`, part of `smokeTest`) checks, for each bundled theme as the window's
  theme and on every page, that each control has the window's theme and each control of the
  preview the edited theme, and that edits on each page reach the preview and undo takes them back.
  `screenshotsThemeWizard` saves each page in each window theme.
