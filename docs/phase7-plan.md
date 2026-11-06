# Phase 7 plan

Phase 7 ([PLAN.md](PLAN.md) §10) covers the Eclipse UI libraries that v1 left out. Publishing to
Maven Central stays on hold until the user says everything is working. Each step below ends in its
own PR with tests, demo/example coverage and screenshots on Windows, GTK and macOS.

| # | Step | What | Why this order |
|---|---|---|---|
| 1 ✅ | **Data binding** | Use the official `org.eclipse.jface.databinding` and `org.eclipse.core.databinding*` jars from Maven Central on the forked JFace (capability resolution, as the guide shows). They create no widgets, so no fork is needed; verify with the JFace snippets 064/065 and a ZapNotes form. Then add the Nebula `*ObservableValue` classes left out in Phase 5 (DateChooser, FormattedText). | Smallest, unblocks the most: 2 snippets, Nebula binding, and a common need in real apps. |
| 2 ✅ | **JFace notifications** | Fork `org.eclipse.jface.notifications` (small: popups that create widgets) with the codemod; theme the popup with a style class like PopupDialog's. | Found by the examples: 6 snippets need it. Same tools as the JFace fork. |
| 3 ✅ | **`jface.text`** | Fork `org.eclipse.jface.text` (and `org.eclipse.text` as a plain dependency): `SourceViewer`, content assist, hovers, annotation rulers. Map its color preferences and hard-coded colors to theme classes and the `colors` registry. | The largest fork; its hovers and content assist reuse the step-2 and JFace popup work. |
| 4 ✅ | **NatTable bridge** | `zapthemes-nattable`: a NatTable `ThemeConfiguration` generated from the current ZapThemes theme, switched live. NatTable paints everything itself, so no fork. | Independent of 1–3; needs its own demo. |
| 5 ✅ | **Nebula Visualization** | Fork the Visualization widgets (xygraph, ...) like Phases 4–5, with stylers for their colors. | Least used; last. |

## Step 5 in detail

`zapthemes-visualization` forks `widgets/visualization` (`xygraph`, 90 files, 111 widget creations
rewritten in its dialogs and toolbar; `widgets`, 40 files) at the pinned Nebula commit, with
`getSystemColor` routed through the theme. Its graphs and widgets are draw2d figures, which the
control stylers cannot reach, so the theme's rules for figure types (by simple class name, in
`controls`) go through `FigureTheming`: setters by key, the class's and its superclasses' rules,
the most general first, never the theme's `defaults` (a new core API, `ThemeManager.ruleOf`, gives a
type's rule alone). Originals come back when a theme drops a key; for colors and fonts that is the
figure's own value, often none (inherited). A patch makes `XYGraph`, `ToolbarArmedXYGraph`,
`AbstractScaledWidgetFigure` and `IntensityGraphFigure` attach the theme in `addNotify` and detach
it in `removeNotify`, so applications need no call; figures added later (legends) are themed on the
next layout.

Covered by `FigureThemingTest` (types, a graph across a switch, a legend added later, trace colors
kept, widget fills, originals restored, removal), the new `VisualizationDemo` (XYGraph with toolbar
and traces, six widget figures; smoke test and screenshots), and upstream's `PureJavaExample` in
the snippets browser.

## Step 4 in detail

`zapthemes-nattable` uses NatTable 2.7.0 from Maven Central as is (it paints itself; its POM asks
for the official JFace, which the capability rule replaces with the fork). `NatTableTheming`, a
`StylerProvider`, registers a styler for the control type `NatTable` (NatTable's package is one the
type resolver knows), so every table is themed when the `Skin` event reaches it and again on every
theme switch, without a call from the application. The styler builds a
`ZapNatTableThemeConfiguration` (NatTable's modern theme, with colors and fonts from the theme's
`NatTable` rule, inverted icons on dark backgrounds) and hands it to `NatTable.setTheme`; it applies
it once more on the first paint, in case `configure()` ran after it. A theme with nothing for
NatTable leaves an untouched table alone and gives a themed one NatTable's default theme back.
`addExtension` keeps the application's label styles across switches; `MANUAL` opts a table out.

Covered by `NatTableThemingTest` (registry styles across a switch, painted header and body pixels,
dark icons, extensions, opt-out, no theme) and the new `NatTableDemo` (headers, stripes, a selection
with its anchor, a frozen column; smoke test and screenshots).

## Step 3 in detail

`zapthemes-jface-text` forks `bundles/org.eclipse.jface.text` at `R4_41` (3.31.100), both source
folders (`src`, `projection`): 340 files, 83 widget creations rewritten, `getSystemColor` routed
through `registries.systemColors`, no raw creation left. `zapthemes-text` builds
`bundles/org.eclipse.text` (3.14.800) unchanged: it shares packages with `jface.text`, and the JVM
refuses unsigned classes in a package of the signed published jar. Patches
(`src/fork/text-patches`, `src/fork/projection-patches`), through `TextTheming`:

1. Information controls (hovers, content assist details) → `popup`, status line `popup muted`.
2. Content assist: proposal list → `popup` (no stripes: `Table.popup` in the bundled themes),
   context information and its selector → `popup` in a `frame`; colors the app set are kept.
3. Rulers (vertical, composite, columns, overview) → `ruler`; line numbers and column backgrounds
   paint in its colors.
4. The folded-code hover → `popup`.

Syntax colors: `TextTheming.KEYWORD`, ... are `ColorRegistry` keys the bundled themes set. Covered
by `SourceViewerThemingTest`, the new `TextDemo` (editor, content assist, context information,
hover; smoke test and screenshots), upstream's `CodeMiningDemo` and `InlinedAnnotationDemo` in the
snippets browser, and ZapNotes' note body (a `SourceViewer` with line numbers and Markdown
highlighting) on the staged artifacts.

## Step 2 in detail

`zapthemes-jface-notifications` forks `bundles/org.eclipse.jface.notifications` at `R4_41`
(0.8.100): 5 files, 8 widget creations rewritten by the codemod, no raw creation left. Two patches
(`src/fork/notifications-patches`):

1. `AbstractNotificationPopup.createContents` calls `NotificationTheming.apply`: shell and title line →
   `notification` (the bundled themes give it `$border`), content area and its controls → `popup`,
   a title still in upstream's dark-shadow color → `title` (dark shadow is unreadable on dark themes).
   Without a theme the controls are only tagged and look as upstream.
2. `CommonImages`: outside OSGi upstream looks for the close icons next to an Eclipse `bin/` folder,
   so a plain Java application had no close button image; the fork finds them at the jar root.

Covered by `NotificationPopupThemingTest` (colors across a theme switch, proxies, close icon, no
theme), the six notification snippets in `zapthemes-examples`, a Notification entry in the JFace
demo (smoke test and screenshots), and ZapNotes' export notification on the staged artifacts.

## Step 1 in detail

1. Add `org.eclipse.platform:org.eclipse.jface.databinding` and `org.eclipse.core.databinding*`
   to the version catalog; check which JFace version they require and that the fork satisfies it.
2. `zapthemes-examples`: stop excluding Snippet064 and Snippet065 and run them in
   `screenshotsSnippets`.
3. `zapthemes-nebula-incubation(-fork)`: stop excluding `**/*ObservableValue.java`; compile, and
   test that binding a DateChooser and a FormattedText to a `WritableValue` works on the proxies.
4. ZapNotes: bind the note form (title, tags) with `DataBindingContext` instead of the modify
   listeners, as a real application would; the smoke test must stay green.
5. Docs: the guide's "Add the library" gains the data binding coordinates.
