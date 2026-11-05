# Phase 7 plan

Phase 7 ([PLAN.md](PLAN.md) §10) covers the Eclipse UI libraries that v1 left out. Publishing to
Maven Central stays on hold until the user says everything is working. Each step below ends in its
own PR with tests, demo/example coverage and screenshots on Windows, GTK and macOS.

| # | Step | What | Why this order |
|---|---|---|---|
| 1 ✅ | **Data binding** | Use the official `org.eclipse.jface.databinding` and `org.eclipse.core.databinding*` jars from Maven Central on the forked JFace (capability resolution, as the guide shows). They create no widgets, so no fork is needed; verify with the JFace snippets 064/065 and a ZapNotes form. Then add the Nebula `*ObservableValue` classes left out in Phase 5 (DateChooser, FormattedText). | Smallest, unblocks the most: 2 snippets, Nebula binding, and a common need in real apps. |
| 2 ✅ | **JFace notifications** | Fork `org.eclipse.jface.notifications` (small: popups that create widgets) with the codemod; theme the popup with a style class like PopupDialog's. | Found by the examples: 6 snippets need it. Same tools as the JFace fork. |
| 3 | **`jface.text`** | Fork `org.eclipse.jface.text` (and `org.eclipse.text` as a plain dependency): `SourceViewer`, content assist, hovers, annotation rulers. Map its color preferences and hard-coded colors to theme classes and the `colors` registry. | The largest fork; its hovers and content assist reuse the step-2 and JFace popup work. |
| 4 | **NatTable bridge** | `zapthemes-nattable`: a NatTable `ThemeConfiguration` generated from the current ZapThemes theme, switched live. NatTable paints everything itself, so no fork. | Independent of 1–3; needs its own demo. |
| 5 | **Nebula Visualization** | Fork the Visualization widgets (xygraph, ...) like Phases 4–5, with stylers for their colors. | Least used; last. |

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
