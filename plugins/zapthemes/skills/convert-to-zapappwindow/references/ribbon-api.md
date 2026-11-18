# Ribbon API for createRibbon and createBackstage

Only these calls. Imports are explicit: `com.zapthemes.widgets.ribbon.*` classes (`Ribbon`,
`RibbonTab`, `RibbonGroup`, `RibbonButton`, `RibbonComboBox`, `Backstage`, `Size`),
`com.zapthemes.widgets.command.*` (`Command`, `CommandMenu`, `Images`) and
`com.zapthemes.jface.ribbon.*` (`ZapAppWindow`, `RibbonContributions`, `ActionCommands`).

## Actions as commands (`com.zapthemes.jface.ribbon`)

| Call | Does |
|---|---|
| `command(action)` (in `ZapAppWindow`) | Registers the action as a command of the window's ribbon and returns it. The command follows the action: text (its `&` mnemonic kept, its accelerator left out), tooltip as description, image, enabled, checked, accelerator. Check box and radio actions are toggles; `AS_DROP_DOWN_MENU` with an `IMenuCreator` is a split button |
| `RibbonContributions.add(group, action)` | Registers the action and adds a button for it to `group`; returns the `RibbonButton` |
| `RibbonContributions.fill(group, items...)` | Adds a button per `IContributionItem`: an `ActionContributionItem` is its action, a `MenuManager` (it needs an id) a drop-down whose menu is built from the manager each time it opens; separators and other items are skipped. Returns `List<RibbonButton>` |
| `RibbonContributions.fill(group.row(), items...)` | The same into a row of small buttons, separators kept |
| `ActionCommands.setLargeImage(action, imageDescriptor32)` | The 32 px image for the action's large buttons. Call before the action is registered |

An action needs `getId()` or `getActionDefinitionId()`; `command(action)` throws otherwise (step 4
gives every action an id). The command's id is the action's id, and `command(action)` returns the
same command every time it is called for one action, so call it wherever you need the command.
`ActionCommands.setRadioGroup(actions...)` makes radio actions mutually exclusive when they are not
added to one group through `RibbonContributions`.

## Tabs and groups

| Call | Does |
|---|---|
| `ribbon.addTab(id, label)` | A tab at the end; returns `RibbonTab` |
| `ribbon.addTab(index, id, label)` | A tab at `index` |
| `ribbon.tab(id)`, `ribbon.tabs()` | Look up; `tab(id)` is `null` when there is none |
| `ribbon.removeTab(tab)` | Remove (an automatic `auto.` tab, say) |
| `tab.setKeyTip("H")` | The tab's KeyTip (one or two letters); returns the tab |
| `tab.addGroup(id, label)`, `tab.addGroup(index, id, label)` | A group; returns `RibbonGroup` |
| `tab.group(id)`, `tab.groups()`, `tab.removeGroup(group)` | Look up, remove |
| `group.add(command)`, `group.add("commandId")` | A button for a command; returns `RibbonButton` |
| `group.add(item)` | Any `RibbonItem` (a `RibbonComboBox`); returns it |
| `group.row()` | A row of small items stacked two or three to a column (the Font group); `row.add(command)`, `row.add(item)`, `row.separator()` |
| `group.setDialogLauncher("commandId")` | The ↘ button at the group's bottom right (opens a dialog) |
| `group.setImage(images)` | The group's icon when it is collapsed |
| `group.setSizes(Size.LARGE, Size.SMALL)` | How far the group may reduce (`LARGE`, `MEDIUM`, `SMALL`, `COLLAPSED`) |
| `button.large()` | Draw the item large (32 px icon over its label) |
| `button.sizes(max, min)` | Its largest and smallest size |
| `button.keyTip("P")` | The item's KeyTip |

`large()`, `sizes(...)` and `keyTip(...)` return the item (as `RibbonItem`), so they chain:
`RibbonContributions.add(group, save).large().keyTip("S");`. `ribbon.commands()` is the window's
`CommandRegistry`: `add(command)`, `find(id)` (an `Optional`), `get(id)`, `all()`.

## Combo boxes

```java
RibbonComboBox size = new RibbonComboBox("format.fontSize", "Font Size")
        .width(48).items("8", "10", "12", "14").text("10").editable(true)
        .description("Change the font size.")
        .onChange(value -> editor.setFontSize(Integer.parseInt(value)));
group.row().add(size);
```

## Commands that are not actions

```java
Command zoom = ribbon.commands().add(new Command("view.zoom", "&Zoom")
        .description("Zoom in or out.").images(Images.of(zoom16, zoom32))
        .style(Command.Style.DROP_DOWN).menu(CommandMenu.of("view.zoomIn", "view.zoomOut"))
        .shortcut(SWT.MOD1 | '=').onRun(e -> zoomIn()));
```

`Command.Style` is `PUSH`, `TOGGLE`, `SPLIT` or `DROP_DOWN`. `CommandMenu.of(ids...)` is a menu of
other commands; `new CommandMenu.Popup(control -> menuManager.createContextMenu(control))` reuses a
JFace menu.

## Backstage (in `createBackstage(Backstage backstage)`)

| Call | Does |
|---|---|
| `backstage.addCommand(command(action).id())` | An entry that runs the command and closes the backstage |
| `backstage.addSeparator()` | A line between entries |
| `backstage.addPage(id, label, images, parent -> { ... })` | A page; the factory fills `parent` (a one-column `GridLayout`) when it is first shown. `images` may be `null` |
| `backstage.setKeyTip(id, "R")` | An entry's KeyTip |
| `backstage.addOpenListener(open -> { ... })` | Told when it opens and closes (to refresh a page) |

Page contents are ordinary SWT controls created through the ZapThemes proxies
(`new com.zapthemes.swt.widgets.Label(parent, SWT.NONE)`), or `com.zapthemes.widgets.FlatButton` for
buttons. Give a page title the theme class `backstageTitle`:
`ThemeManager.get(display).setThemeClass(label, "backstageTitle")`.

## Shortcuts (`com.zapthemes.widgets.command.CommandKeyBindings`)

The ribbon installs these for its shell; nothing to set up. `CommandKeyBindings.install(shell)` returns
the ones already installed (it never makes a second set). `bindings()` maps each accelerator to the
command it runs now: enabled and visible commands only. `find(SWT.MOD1 | 'S')` gives the command one
accelerator runs, as an `Optional`.

## Screenshots

`Shell.print` misses the title bar and ribbon of a `ZapAppWindow` on Windows. Compose the image from
the shell's children instead:

```java
Rectangle area = shell.getClientArea();
Image image = new Image(shell.getDisplay(), area.width, area.height);
GC gc = new GC(image);
for (Control child : shell.getChildren()) {
    Rectangle b = child.getBounds();
    if (!child.getVisible() || b.isEmpty()) {
        continue;
    }
    Image part = new Image(shell.getDisplay(), b.width, b.height);
    GC partGc = new GC(part);
    child.print(partGc);
    partGc.dispose();
    gc.drawImage(part, b.x - area.x, b.y - area.y);
    part.dispose();
}
gc.dispose();
ImageLoader loader = new ImageLoader();
loader.data = new ImageData[] { image.getImageData() };
loader.save(file, SWT.IMAGE_PNG);
image.dispose();
```

Never copy the screen (`new GC(display).copyArea`): it captures whatever window is in front.

## Window-level

| Call | Does |
|---|---|
| `ribbon.setAccentColor(new RGB(...))` | The app's brand color for the File tab and backstage (themes may turn it off) |
| `ribbon.addRightCommand("commandId")` | A button at the tab row's right (Share, Comments) |
| `ribbon.addContextualTabSet(id, label, accent)` | Tabs shown only for a selection: `set.addTab(id, label)`, `set.setVisible(true)` |
| `addQuickAccess(action)` (from `TitleBarWindow`) | Puts an action in the title bar's quick access toolbar |
