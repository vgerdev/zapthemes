# Ribbon plan

An Office-style ribbon for SWT and JFace apps, to replace the menu bar and tool bars, like
Microsoft Word's and Excel's. It has tabs of groups of commands, galleries, adaptive resizing,
KeyTips, collapsing, contextual tabs, the File backstage, and end-user customization saved to a
file. It is drawn on canvases in `zapthemes-widgets` and every color comes from the theme.

Publishing to Maven Central stays on hold until the user says everything works. Each step below
ends in its own PR against `master`, stacked on the step before, with tests, the `RibbonDemo` and
its screenshots.

## Decisions

Asked and answered by the user:

| Question | Answer |
|---|---|
| Features | Core ribbon, galleries, adaptive resizing, KeyTips, collapse and pin, contextual tabs |
| Backstage | The File tab opens a full-window backstage in this effort |
| Customization | The app defines the ribbon; end users customize it in a *Customize Ribbon* dialog, as in Office; saved to a preferences file the app chooses |
| API | Java and JFace only. No declarative file |

Decided here (the user can change them later):

| Question | Answer |
|---|---|
| Drawing | Canvases in `zapthemes-widgets`, like the Flat widgets. The ribbon is one canvas; popups are themed `Shell`s with canvases of their own |
| Colors | Only from the theme: the `Ribbon*` types below, defaulting to common names. The app's brand color (Word blue, Excel green) is an *accent override* the theme may switch off (high contrast does) |
| Icons | The app supplies 16 and 32 px images, and optionally larger ones for HiDPI and zoom. The ribbon ships no icons; the demo draws its own |
| Combos | Drawn on the canvas, with an editor `Text` created only while typing, so a group can move into a popup without reparenting native controls |
| Modules | The ribbon and the shared command model live in `zapthemes-widgets`, which now depends on `zapthemes-swt` for its popups' proxies. JFace support is a new module, `zapthemes-jface-ribbon` |
| Commands | One command model, `com.zapthemes.widgets.command`, shared with the title bar's quick access toolbar; one registry per shell; one customization file with a section per bar |
| Zoom | Sizes come from the theme font's height, so the ribbon follows the zoom of PR #19 once it merges (step 7 checks it) |

## Terms

- **Command:** something the user can do, with an id (`"home.bold"`), a label, images, a
  description, a shortcut, a style (push, toggle, split, drop-down), and enabled and checked
  states. Ribbon buttons, quick access toolbar buttons and menu items show commands.
- **Tab → group → item:** a tab holds groups; a group holds items. An item shows one command
  (a button), or is a combo, a gallery, a color button or a separator.
- **Size:** how an item is drawn. *Large*: a 32 px icon over a one- or two-line label, a whole
  column tall. *Medium*: a 16 px icon and its label, three to a column. *Small*: the icon only.
- **Reduction:** shrinking a group one size at a time, down to *collapsed*: one large drop-down
  button that opens the group as a popup.
- **KeyTip:** a letter badge shown over a tab or item after Alt, typed to run it.
- **Contextual tab set:** tabs that only show while something is selected (a table: *Table
  Design*, *Layout*), drawn with an accent of their own.
- **Backstage:** the page the File tab opens over the whole window.

## The command model (shared with the title bar)

`com.zapthemes.widgets.command`, in `zapthemes-widgets`. Nothing in it is ribbon-specific.

```java
CommandRegistry commands = CommandRegistry.of(shell);   // one per shell, created on first use
Command bold = commands.add(new Command("home.bold", "&Bold")
        .style(Command.Style.TOGGLE)
        .images(Images.of(bold16, bold32))
        .description("Make your text bold.")
        .shortcut(SWT.MOD1 | 'B')
        .category("Home")
        .onRun(e -> editor.toggleBold()));
bold.setChecked(true);           // every bar showing it repaints
bold.setEnabled(false);
```

- **`Command`:** id (required, stable: customizations store it), label with `&` mnemonic,
  description (the super tooltip's body), shortcut (an SWT accelerator, shown as text in the
  tooltip), `Images` (a size → image source, `Images.of(small, large)` or a lambda
  `size -> image`, asked for 16, 20, 24, 32, 40, 48 depending on zoom), `Style`
  (`PUSH`, `TOGGLE`, `SPLIT`, `DROP_DOWN`), category (for the customization dialogs' *Choose
  commands from* list), enabled, checked, visible, a menu (`CommandMenu`: command ids and
  separators, or a `Consumer<Menu>` that fills an SWT menu), and a handler
  (`Consumer<CommandEvent>`; the event says which bar and widget ran it, and for a split
  button whether the arrow part was used).
- **Listeners:** `Command.addListener(CommandListener)` gets a property name
  (`Command.ENABLED`, `CHECKED`, `LABEL`, `IMAGES`, `DESCRIPTION`, `VISIBLE`) on every change.
  The ribbon and the title bar repaint from these, so the app only changes the command.
- **`CommandRegistry`:** `add`, `get(id)`, `all()` in registration order, `categories()`,
  listeners for added and removed commands. `CommandRegistry.of(shell)` keeps one per shell in
  its data, so the ribbon and the title bar find the same one without being wired together.
  Apps may also create a registry and pass it to both.
- **`CustomizationStore`:** a JSON file the app chooses (`CustomizationStore.open(path)`), with
  one section per bar: `"ribbon"` and `"quickAccessToolbar"`. `section(name)` reads a
  `JsonElement`, `put(name, element)` replaces one and `save()` writes the file atomically (a
  temporary file then a move). Unknown sections are kept, so two bars, or two versions of an
  app, share one file. A missing or unreadable file is an empty store (the error is logged).

  ```json
  {
    "formatVersion": 1,
    "quickAccessToolbar": { "commands": ["file.save", "edit.undo", "edit.redo"], "belowRibbon": false },
    "ribbon": { "tabs": [ ... ], "collapsed": false }
  }
  ```
- **For the title bar:** the quick access toolbar holds command ids (and `"|"` for separators),
  draws each command at 16 px with the same `Images`, runs `Command.run`, and stores its list in
  the `"quickAccessToolbar"` section. *Add to Quick Access Toolbar* on a ribbon item's context
  menu calls a hook (`Ribbon.setQuickAccessHook(Consumer<Command>)`) the title bar sets, so
  neither depends on the other.

## API and model

Plain SWT, a builder over the model; every node has an id, so customization can store it.

```java
Ribbon ribbon = new Ribbon(shell, SWT.NONE);               // or new Ribbon(shell, SWT.NONE, commands)
ribbon.setFileTab("File", backstage -> { ... });            // the backstage, see below
RibbonTab home = ribbon.addTab("home", "Home");
RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard");
clipboard.setImage(Images.of(paste16, paste32));            // its icon when collapsed
clipboard.setDialogLauncher("home.clipboard.dialog");       // a command id; draws the ↘ button
clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE);  // max and min size
clipboard.add("home.cut");                                  // medium, then small
clipboard.add("home.copy");

RibbonGroup font = home.addGroup("home.font", "Font");
font.row().add(new RibbonComboBox("home.fontName").width(120).items(fontNames))
          .add(new RibbonComboBox("home.fontSize").width(42).items("8", "9", "10", "11", "12"))
          .add("home.grow").add("home.shrink");
font.row().add("home.bold").add("home.italic").add("home.underline").separator()
          .add(new RibbonColorButton("home.fontColor").color(red));

RibbonGroup styles = home.addGroup("home.styles", "Styles");
styles.add(new RibbonGallery("home.styles.gallery", styleRenderer).columns(5, 3).itemSize(72, 40));

ribbon.addRightCommand("comments");                         // the tab row's right side
ribbon.addRightCommand("share").emphasis(true);
```

- `Ribbon` (a `Canvas`, theme type `Ribbon`) owns the tabs, the contextual tab sets, the right
  commands, the backstage and the collapse state, and paints the tab row and the selected tab's
  panel.
- `RibbonTab`: id, label, KeyTip, groups, hidden; `RibbonGroup`: id, label, image, KeyTip, dialog
  launcher, priority, reduction limits, items.
- Items (`RibbonItem` subclasses, model objects painted by the ribbon, not widgets):
  `RibbonButton` (any command; its style picks push, toggle, split or drop-down),
  `RibbonColorButton`, `RibbonComboBox`, `RibbonGallery`, `RibbonSeparator`, and `RibbonRow`
  (a horizontal run of small items, two or three stacked in a column, like the Font group).
- `group.add("id")` looks the command up in the ribbon's registry; `group.add(Command)` registers
  it first.
- Changes to the model relayout and repaint; changes to commands repaint.
- `Ribbon.getModel()` describes the current structure as plain records (ids, labels, hidden), the
  input of customization and of tests.

## Controls

| Item | Draws | Mouse and keyboard |
|---|---|---|
| Push button | Icon and label in its size; hover and pressed backgrounds | Click, Enter, Space |
| Toggle button | As push, with the *checked* background and border while checked | Flips the command's checked state, then runs it |
| Split button | Two hit areas: the main part, and an arrow (below the label when large, to the right when medium or small) | The main part runs the command; the arrow opens its menu |
| Drop-down button | The label with a ˅ | Opens the menu (`CommandMenu` or the app's `Consumer<Menu>`) under the item |
| Color button | A split button with a color bar under the icon | The main part applies the current color; the arrow opens a palette popup: theme colors, standard colors, *Automatic*, *More colors…* (the SWT `ColorDialog`) |
| Combo box | A field and an arrow, its width fixed by the app | Typing opens an editor `Text` over the field (Enter commits, Esc cancels); the arrow opens a list popup; a selection listener gets the value |
| Gallery | A bordered strip of item previews drawn by the app, with ˄ ˅ scroll arrows and an expand button | Click selects; arrows scroll a row; expand opens the full gallery as a popup grid, with optional commands under it (*Clear formatting*) |
| Group label | The group's label at the bottom, and a ↘ dialog launcher at its right when set | The launcher runs its command |
| Collapsed group | A large button with the group's image and label and a ˅ | Opens the group at full size as a popup under it |
| Right commands | Bordered buttons at the right of the tab row; *emphasis* fills one with the accent (Share) | As push or drop-down buttons |
| Collapse ˅ | A small button at the panel's bottom right | Collapses the ribbon (pin ⊣ while it is shown as an overlay) |

Every item shows a *super tooltip* after a hover delay: the label in bold, the shortcut
(*Bold (Ctrl+B)*), the description, and for a disabled item the reason when the command has one.

## Layout and adaptive resizing

**Measurements** come from the theme font's height `h` (about 15 px at 100 %), so zoom and the
platform's font sizes scale everything together: the tab row is `h + 12`, the panel holds three
rows of `h + 7`, plus the group label row; large icons are 32 px at 100 % and the image size is
picked to match (`Images` is asked for 32, 40 or 48).

**Groups lay out in columns.** A large item takes a column. Consecutive medium or small items
stack three to a column. `RibbonRow`s stack two or three to a column; each row lays its items out
left to right, with separators. A gallery takes a column as wide as its visible columns. The
group is as wide as its columns, or its label, whichever is wider.

**Each group has four sizes:** large, medium, small and collapsed. At a group size, each item is
drawn at that size, clamped to its own limits (`sizes(max, min)`); for example Paste stays large
until the group collapses, while Cut goes medium, then small. A gallery shows its maximum columns
when large, its minimum when medium, and becomes a large drop-down button when small.

**Reduction:**

1. Start with every group large (its largest size).
2. If the tab fits, stop.
3. Otherwise take the next step of the tab's reduction order and apply it, skipping steps that
   do not change the width. Repeat from 2.
4. If every step is taken and it still does not fit, the panel scrolls with arrows at its ends.

The default order goes size by size: every group to medium, then every group to small, then
every group collapsed; within one size, groups go by priority (`group.setPriority(int)`, lower
reduces first), then right to left. `group.setMinimumSize(Size)` stops a group's reduction (a
group that must never collapse). An app can replace the order (`tab.setReductionOrder(
step(font, MEDIUM), step(styles, SMALL), ...)`); groups the order leaves out are reduced by
the default afterwards. The layout is a pure function of widths, so tests check it without
painting: `RibbonLayout.layout(tab, width, gc)` returns each group's size and bounds and each
item's size and bounds.

## KeyTips

- **Alt** pressed and released with no other key, or **F10**, shows badges on the tabs, the
  backstage's File tab, the right commands, and (with the title bar) the quick access toolbar.
  A display filter on the ribbon's shell catches the keys; the ribbon takes focus while
  badges show.
- Typing a tab's KeyTip selects the tab and shows the badges of its items. Typing an item's
  KeyTip runs it, or opens its menu, gallery, list or collapsed group, whose own items then show
  KeyTips.
- KeyTips may have two letters. Typing the first letter hides the badges that do not start with
  it.
- **Esc** goes back one level; at the top level it hides the badges and gives focus back to the
  control that had it. A mouse click, losing focus or Alt again hides them too.
- KeyTips come from `setKeyTip("FN")`, else they are assigned: the first letter of the label
  not taken in that level, then the first letter plus a digit or second letter. Assignment is
  deterministic, so tests and documentation can name them.
- Badges are drawn on the canvas under the item's center (on top of the tab for tabs); popups
  draw their own.

## Collapse and pinning

- **Ctrl+F1**, double-clicking a tab, or the ˅ button collapses the ribbon to the tab row; the
  window's layout gets the space back.
- While collapsed, clicking a tab shows its panel as an overlay: a popup shell under the tab row,
  over the content. Clicking outside, Esc, or running a command closes it. The overlay's ⊣ pin
  button, Ctrl+F1, or double-clicking a tab expands the ribbon again.
- `Ribbon.setCollapsed(boolean)`, `isCollapsed()`, and a listener. The state is saved with the
  customization when there is a store.

## Contextual tabs

```java
RibbonContextualTabSet table = ribbon.addContextualTabSet("table", "Table Tools", 2);  // accent 1–4
RibbonTab design = table.addTab("table.design", "Table Design");
table.setVisible(editor.isInTable());                   // the app calls this on selection changes
```

- A visible set adds its tabs after the regular tabs. Its tabs are drawn in its accent color
  (`contextualAccent1` … `contextualAccent4` in the theme), with a strip of it above the tab and
  the set's label in the accessible name and tooltip.
- Showing a set does not select it; Office selects the first contextual tab only when the
  selection was just made by inserting, so `setVisible(true, true)` selects it.
- Hiding a set whose tab is selected selects the tab that was selected before it showed.

## Backstage

```java
ribbon.setFileTab("File", backstage -> {
    backstage.addPage("info", "Info", infoImage, parent -> buildInfo(parent));
    backstage.addPage("new", "New", null, parent -> buildNew(parent));
    backstage.addCommand("file.save");                   // a nav entry that runs a command
    backstage.addSeparator();
    backstage.addPage("options", "Options", null, parent -> buildOptions(parent));
});
```

- The File tab, drawn in the accent, opens the `Backstage` (theme type `RibbonBackstage`): a
  composite over the whole of the ribbon's shell's client area, including the ribbon, with a
  left navigation column (accent-colored in Office; themeable), a back arrow at its top, and the
  selected page on the right. Pages are created on first show and kept.
- While it shows, the backstage swaps its parent's layout for one that gives it the whole client
  area and hides its siblings, and swaps it back when it closes, so it works with any layout and
  with JFace's `ApplicationWindow`.
- **Esc**, the back arrow, or running a command entry closes it. Up and Down move in the nav
  list; Tab moves into the page. (KeyTips over the nav entries are not done yet.)

## Message bar

`MessageBar` (theme type `RibbonMessageBar`): an icon, a bold title, a message, buttons
(`FlatButton`s) and a close button, placed by the app under the ribbon. Its `kind` (info, warning,
error, success) picks the theme colors. It is optional and small.

## Customization and persistence

- `Ribbon.setCustomizationStore(store)` loads the `"ribbon"` section and applies it to the app's
  structure; every change made through the dialog saves it.
- **What is stored:** the tabs in order, each with its id, a label when renamed, `hidden`, and
  its groups in order, each with id, label when renamed, and its items as command ids when
  changed (`null` when the group's items are the app's, so app updates to a group still show).
  User tabs and groups have ids `custom.tab.N` and `custom.group.N` and `"custom": true`.
- **Merging with app updates:** ids the app no longer defines are dropped; tabs and groups the
  app added since are inserted at their default positions; commands are looked up by id.
- **Customize Ribbon dialog** (`CustomizeRibbonDialog`, themed proxies; opened from a right-click
  on the ribbon, or by the app): *Choose commands from* (all commands, by category, or those not
  in the ribbon) with a filter, a list of commands, *Add >>* and *<< Remove*, a tree of tabs,
  groups and commands with check boxes to hide tabs, *Up* and *Down*, *New Tab*, *New Group*,
  *Rename…*, *Reset* (all, or the selected tab), *OK* and *Cancel*. OK applies and saves.
- Only command items can be added by users: combos and galleries keep their place in app groups
  and can be removed, but the dialog adds them back only through *Reset*. (Office has the same
  limit for most controls.)
- The round-trip test: customize through the model API, save, open a new ribbon on the same app
  structure and store, and get the same model.

## Theming

New theme types, with properties in the catalog (`FlatStylers` describes them; the four bundled
themes set them on common names):

| Type | Properties (default name) |
|---|---|
| `Ribbon` | `background` (raised), `foreground` (foreground), `tabStripBackground` (chrome), `tabForeground` (foreground), `tabHoverBackground` (hover), `selectedTabBackground` (raised), `selectedTabForeground` (accent), `tabIndicatorColor` (accent), `groupLabelForeground` (muted), `separatorColor` (border), `borderColor` (border), `itemHoverBackground` (hover), `itemPressedBackground` (selection), `itemCheckedBackground` (selection), `itemCheckedBorderColor` (accent), `disabledForeground` (disabled), `galleryBorderColor` (border), `galleryBackground` (surface), `gallerySelectionColor` (accent), `keyTipBackground` (badge), `keyTipForeground` (onBadge), `keyTipBorderColor` (border), `fileTabBackground` (accent), `fileTabForeground` (onAccent), `emphasisBackground` (accent), `emphasisForeground` (onAccent), `contextualAccent1`…`4` (accent, info, success, warning), `focusColor` (focus), `tabFont`, `groupLabelFont` (caption), `useAppAccent` (true), `arc` (4) |
| `RibbonPopup` | `background` (raised), `foreground`, `borderColor` (border): galleries, color palettes, collapsed groups, overlays, combo lists |
| `RibbonToolTip` | `background` (surface), `foreground`, `borderColor`, `titleFont` (strong), `muted` |
| `RibbonBackstage` | `background` (background), `foreground`, `navBackground` (accent), `navForeground` (onAccent), `navHoverBackground`, `navSelectedBackground`, `navSelectedForeground`, `titleFont` (heading1), `navFont` |
| `RibbonMessageBar` | `background` (warning-tinted surface), `foreground`, `borderColor`, `titleFont` (strong), and per kind `info`, `warning`, `error`, `success` states |

New common names in `BaseNames`: `chrome` (window chrome: title bars and the ribbon's tab row,
also the title bar's), `badge` and `onBadge` (KeyTip and count badges). The bundled themes get
them; zap-light and zap-dark keep the ribbon close to Office's light and dark looks.

- States: items paint `hover`, `pressed`, `checked`, `disabled` and `focused` from the keys
  above (per-state overrides through a `states` block are not read yet).
- **The app's accent:** `ribbon.setAccentColor(rgb)` overrides `fileTabBackground`,
  `tabIndicatorColor`, `selectedTabForeground`, `emphasisBackground` and the backstage nav, unless
  the theme sets `useAppAccent: false` (zap-high-contrast does). The demo switches Word blue and
  Excel green.
- A theme switch repaints and relayouts live (the styler repaints; fonts change sizes).
- **High contrast:** borders on hovered and checked items, the focus ring always drawn, no
  meaning by color alone (checked items get a border as well as a fill).

## JFace integration

New module `zapthemes-jface-ribbon` (depends on `zapthemes-jface` and `zapthemes-widgets`):

- `ActionCommands.register(registry, IAction)` wraps an action as a command whose id is the
  action's id (required) or its definition id. Text, tooltip (as the description), image
  descriptors (16 px from `getImageDescriptor`, 32 px from a `"large"` descriptor the app may
  set through `ActionCommands.setLargeImage`), enabled, checked, the style (`AS_CHECK_BOX` is
  toggle, `AS_DROP_DOWN_MENU` with an `IMenuCreator` is split) and the accelerator follow the
  action through its property listener; running the command runs the action
  (`runWithEvent`).
- `RibbonContributions.fill(group, IContributionItem...)`: `ActionContributionItem`s become
  buttons, `Separator`s separators, `MenuManager`s drop-down buttons whose menu is the manager's,
  and any other item is wrapped as a command when it has an id.
- `RibbonApplicationWindow extends ApplicationWindow`: `addRibbon()` in the constructor instead
  of `addMenuBar()` / `addCoolBar()`; `createRibbon(Ribbon)` to fill it; the ribbon goes where the
  tool bar would, the backstage covers the window, the status line stays.
- Sizes and images through `ImageDescriptor`s are created per zoom and disposed with the ribbon
  (a `LocalResourceManager`).

## Accessibility

- **Keyboard:** every command can be reached without a mouse: KeyTips; and the ribbon takes
  focus (F6 from the content, or Alt then an arrow key), Left and Right move between tabs, Down
  or Tab enters the panel, Tab and Shift+Tab, and the arrow keys, move between items in reading
  order, Enter or Space runs one, Down opens a drop-down, Esc goes back to the tab row and then
  to the content. A focus ring is drawn on the focused tab or item.
- **Screen readers:** the ribbon's `Accessible` exposes children: the tabs (`ROLE_TABITEM`, in a
  `ROLE_TABFOLDER`), the groups (`ROLE_GROUPING`, named by the label), and the items
  (`ROLE_PUSHBUTTON`, `ROLE_CHECKBUTTON` with checked state for toggles, `ROLE_SPLITBUTTON`,
  `ROLE_COMBOBOX`, `ROLE_LIST` for galleries), with names, descriptions (the super tooltip text),
  keyboard shortcuts (the KeyTip and the accelerator), states (focused, disabled, checked,
  expanded), locations, focus changes (`setFocus(childID)`) and default actions.
- **Super tooltips** carry the shortcut, so the shortcut can be learned.

## Risks

- **Popups across platforms:** overlay and gallery popups are `ON_TOP | NO_TRIM` shells; on GTK
  under Wayland/Xvfb, positioning and focus differ. Close on deactivate, and test under Xvfb.
- **Alt handling:** Windows sends the Alt key to the menu bar; with no menu bar the filter gets
  it. GTK may give Alt to the window manager. F10 is the fallback, and on macOS (no Alt
  convention) KeyTips are off by default (`setKeyTipsEnabled`).
- **The backstage swapping its parent's layout:** an app whose layout data depends on the
  siblings being visible relays out on close. The JFace window and the demo are tested.
- **Measuring text in layout:** widths depend on fonts, so tests assert relations (fits, sizes
  never grow as width shrinks, the order of reductions) and not pixel values.
- **Accessibility on a single canvas:** child ids work with Windows' MSAA/UIA bridge in SWT; on
  GTK (ATK) and macOS support is thinner, and is checked by hand.
- **The zoom PR (#19):** the ribbon reads only font metrics for sizes, so it should follow; if
  it lands a zoom factor API, icon sizes use it (step 7).
- **The title bar session:** it is building the quick access toolbar on the same command model in
  parallel. The model is in step 1, so it can merge first.

## Steps

| # | Step | What |
|---|---|---|
| 1–5 ✅ | **Commands and the ribbon** (PR 1) | The command model and `CustomizationStore`; `Ribbon` with tabs, groups, every button kind, combos, color buttons, labels, dialog launchers, right commands; super tooltips; galleries; adaptive resizing and collapsed groups; KeyTips; collapse, overlay and pin; contextual tab sets; the backstage and `MessageBar`; customization with `CustomizeRibbonDialog`; theming (types, catalog, base names, bundled themes); accessibility; `RibbonDemo` with its smoke test and screenshots |
| 6 ✅ | **JFace** (PR 2, on PR 1) | `zapthemes-jface-ribbon`: action commands, contribution items, `RibbonApplicationWindow`; the demo's ¶ button is a JFace action |
| 7 | **Follow-ups** | Zoom once #19 merges; the GTK and macOS checks; KeyTips in the backstage; renaming items in the dialog; per-state theme overrides |

Steps 1 to 5 landed as one PR instead of five: they share one surface, hit-test and popup model
(galleries, KeyTips, collapsed groups and the overlay are all `Hot`s of a `RibbonSurface`), so
splitting them would have meant writing each piece twice.

Each step's tests run in `zapthemes-widgets` (with a `Display`) and cover what it adds: layout and
reduction at several widths, KeyTip navigation, collapse and pin, contextual tabs, gallery
scrolling and selection, action sync, the customization round-trip and theme switching. The
`RibbonDemo` smoke test resizes through every reduction stage, opens the backstage and the KeyTips,
and saves screenshots in every theme.

## As built

- **Classes** (`com.zapthemes.widgets.ribbon`): `Ribbon` and `PanelSurface` extend
  `RibbonSurface`, a canvas that lays out `Hot`s (tabs, items, launchers, collapsed groups, gallery
  parts, ...) and paints, hit-tests, navigates and exposes them to screen readers. `RibbonLayout` is
  the pure layout and reduction; `RibbonMetrics` derives every size from the font height.
  `KeyTips`, `SuperToolTip`, the popups (`GroupPopup`, `GalleryPopup`, `ColorPalettePopup`,
  `ComboListPopup`), `Backstage`, `MessageBar`, `RibbonCustomization` and `CustomizeRibbonDialog`
  complete it.
- **Reduction detail:** a step that does not make its group narrower is skipped. A single large
  button is usually narrower than its medium form, so groups of one or two large buttons go
  straight from large to collapsed, as in Office.
- **Popups** are `ON_TOP | NO_TRIM` shells; one opened from another (a combo list in a collapsed
  group) keeps its parent open, and any other window taking the focus closes them.
- **Tab row:** when it runs out of room, right commands drop their labels, tabs their padding, and
  tabs that still do not fit are left out (the selected one stays).
- **Customization** stores `knownTabs` and `knownGroups` with the state, so tabs and groups an app
  update adds are inserted at their default places while ones the user removed stay removed.
- **`CommandMenu.Popup`** (added for JFace) lets a command's menu be a ready popup menu created for
  the bar's control: `IMenuCreator.getMenu(Control)`, `MenuManager.createContextMenu`.
- **Tests:** `RibbonTest` (39, in `zapthemes-widgets`) and `JFaceRibbonTest` (5); the demo's smoke
  test and `checkCatalog`.
