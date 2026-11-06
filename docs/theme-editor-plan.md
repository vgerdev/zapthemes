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

- `PropertySpec(type, key, kind, defaultName, description)`, where `kind` is color, font, int,
  boolean, string or enum (renderer ids). It's registered next to each styler
  (`StylerRegistry.register(type, styler, specs...)`), and figure types register with
  `FigureTheming`.
- `PropertyCatalog.all()` covers SWT and JFace types, the Flat widgets, Nebula, Nebula
  incubation, NatTable, Visualization figures, the jface.text classes (`*.ruler`, `*.frame`, syntax
  keys) and the JFace registry keys.
- Each type also lists its style-bit variants, the style classes the library uses (`popup`,
  `primary`), and its owner-draw states.
- The default map is the `defaultName` of every spec. It's what step 4 shows as "(default)", and
  what upgrades on load fill in.
- **Tests:**
  - every key a bundled theme uses is in the catalog;
  - every key a styler reads is in the catalog, by recording what `ResolvedStyle` is asked for
    while the demos' smoke tests run on GTK;
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
| 1 | **Theme format v2** | `formatVersion`; `swatches`, `names` with color + font, references resolved by key type, chained references with cycle errors, font fallback lists and `inherit`, the `editor` section, `dark`; v1 → v2 upgrade on load; the schema, `ThemeJson.write`, and validator messages. Old themes keep loading. | Everything else is built on this format. |
| 2 | **Property catalog + default map** | `PropertySpec`, registration in every styler provider and `FigureTheming`, variants, classes and states per type; the catalog coverage tests above. | Step 4 of the wizard and upgrades on load both need it. |
| 3 | **Bundled themes on common names** | Rewrite the four bundled themes as v2 with the base names, turning literals into names where one fits. **Equivalence test:** every catalog type, variant, class and state resolves to the same colors and fonts before and after, and screenshot diffs show no change. | Shows that the format and names are enough for real themes before any UI exists. |
| 4 | **Scoped preview themes (core)** | `ThemeManager.preview(Composite, Theme)` themes one subtree with a theme of its own, independent of the display's theme, and follows edits without disposing anything. This is needed because the wizard itself must stay readable while the theme being edited may be half-finished. | The riskiest core change: stylers, Skin listeners, registries and figures all assume one theme per display. |
| 5 | **Palette model and generator** | Pure Java with no UI: OKLCH/HSL conversions, harmony rules, lock and regenerate (seeded, so tests are repeatable), tints and shades, k-means on `ImageData`, WCAG contrast, auto-fill from palette to names. Unit tests run headless. | Testable on its own; pages 1–3 are thin on top of it. |
| 6 | **Wizard: palette, names, colors and fonts** | `zapthemes-editor` with `ThemeWizard`, pages 1–3, the preview pane, and the editing model (an undo stack per page; "set by user" vs auto-filled). | |
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

