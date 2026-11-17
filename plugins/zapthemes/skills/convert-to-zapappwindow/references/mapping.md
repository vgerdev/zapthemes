# ApplicationWindow to ZapAppWindow

## What the base class change does

| Written for `ApplicationWindow` | In a `ZapAppWindow` |
|---|---|
| `addMenuBar()`, `createMenuManager()` | The manager is created and kept (`getMenuBarManager()` returns it); no native menu bar. The **File** menu (id `file`, else the text *File*) becomes the **backstage**: its actions are entries, its submenus pages of buttons, *About* from the Help menu and *Exit*/*Close* (by id or label) last. Every other top-level menu becomes a **tab**: groups cut at visible `Separator`s, named by a named separator (`new Separator("clipboard")` → *Clipboard*) else by the group's first item; submenus and dynamic `ContributionItem`s become drop-down buttons; mnemonics become KeyTips |
| `addToolBar(style)`, `createToolBarManager(style)`; `addCoolBar(style)`, `createCoolBarManager(style)` | The manager is created and kept; no `ToolBar`/`CoolBar` widget. Its items become a **Home** tab, first: a group per section (named by a named separator, else *Commands 1*, *Commands 2*, or *Commands* alone), large buttons for actions with an image. `ControlContribution`s are skipped (logged) |
| Accelerators (`"&Save@Ctrl+S"`, `"&Save\tCtrl+S"`, `setAccelerator`) | Every action in the menus and tool bar is registered as a command, shown on the ribbon or not, and `CommandKeyBindings` runs it by its accelerator |
| `addStatusLine()`, `setStatus`, `getStatusLineManager()`, `run(fork, cancelable, runnable)` | Unchanged. Hovering a ribbon button shows its action's tooltip in the status line (`setRibbonStatusMessages(false)` turns that off). `run` also disables the ribbon and the shortcuts |
| `getToolBarControl()` | The ribbon. `getCoolBarControl()` is `null` |
| `getMenuBarManager().getMenu()`, `shell.getMenuBar()` | `null`: there is no native menu bar |
| Changing the menus at run time | Enabled, checked, label and accelerator changes reach the ribbon through the actions. After adding or removing items call `refreshRibbon()` |

Everything the conversion makes has an id starting with `auto.`. `createRibbon(Ribbon)` runs after
it and can change or remove it; `convertMenus()` returning `false` turns it off. `createBackstage`
runs after the File menu's entries; a backstage left empty gets a *Close* entry.
`getFileTabLabel()` returning `null` removes the File tab.

Hooks of `ZapAppWindow`:

| Hook | Use |
|---|---|
| `protected void createRibbon(Ribbon ribbon)` | Build or adjust the ribbon; runs once, after the conversion |
| `protected void createBackstage(Backstage backstage)` | Backstage pages and entries; runs when the backstage is first used |
| `protected boolean convertMenus()` | `false` when `createRibbon` builds everything |
| `protected Command command(IAction action)` | Registers an action as a command (call in `createRibbon`/`createBackstage`) |
| `protected Path getRibbonPreferences()` | The file users' ribbon and quick access toolbar customizations are saved in; `null` keeps none |
| `protected String getFileTabLabel()` | `"File"`; `null` for no File tab |
| `public void refreshRibbon()` | Rebuild the automatic part after menu changes |
| `public Ribbon getRibbon()` | The ribbon, once the shell exists |
| `protected RibbonConversion createRibbonConversion()` | Replace the conversion rules |

## Ids

The id the conversion gives an action that has neither `getId()` nor `getActionDefinitionId()`.
Step 4 uses the same id without `auto.`.

1. **Path:** the top-level menu's key; for an action in a submenu, the menu's key, `.`, the
   submenu's key (nested submenus add more). A menu's key is its id when it has one, else the slug
   of its text. Actions only on the tool bar have the path `home`; *About* from the Help menu has
   `help`.
2. **Label:** the action's text without its accelerator (after a tab, else after the last `@`),
   without `&`, trimmed, without trailing `.` or `…`.
3. **Slug:** keep letters and digits; lower-case the first character; upper-case a character that
   follows anything else; drop the rest. `Save All` → `saveAll`, `New Note...` → `newNote`,
   `notes.txt` → `notesTxt`, `About ZapNotes` → `aboutZapNotes`.
4. **Id:** `auto.` + path + `.` + slug: `auto.edit.rename`, `auto.file.saveAll`,
   `auto.file.recent.notesTxt`.
5. Menus are read in order, then the tool bar, and an action keeps the first id it gets, so an action
   in the File menu and on the tool bar is `auto.file.…`. A second, different action with the same
   id gets `2`, `3` … appended.

## Before and after

Before:

```java
final class NotesWindow extends ApplicationWindow {
    NotesWindow() {
        super(null);
        save = action("&Save@Ctrl+S", this::save);
        rename = action("&Rename...@F2", this::renameSelected);
        addMenuBar();
        addToolBar(SWT.FLAT | SWT.RIGHT);
        addStatusLine();
    }

    @Override
    protected MenuManager createMenuManager() {
        MenuManager bar = new MenuManager();
        MenuManager file = new MenuManager("&File");
        file.add(save);
        file.add(new Separator());
        file.add(action("E&xit", app::exit));
        MenuManager edit = new MenuManager("&Edit");
        edit.add(rename);
        bar.add(file);
        bar.add(edit);
        return bar;
    }

    @Override
    protected ToolBarManager createToolBarManager(int style) {
        ToolBarManager toolBar = new ToolBarManager(style);
        toolBar.add(save);
        return toolBar;
    }
}
```

After step 3 the class only says `extends ZapAppWindow`. After steps 4 to 8:

```java
final class NotesWindow extends ZapAppWindow {
    NotesWindow() {
        super(null);
        save = action("&Save@Ctrl+S", this::save);
        save.setId("file.save");
        rename = action("&Rename...@F2", this::renameSelected);
        rename.setId("edit.rename");
        exit = action("E&xit", app::exit);
        exit.setId("file.exit");
        addStatusLine();
    }

    @Override
    protected boolean convertMenus() {
        return false;
    }

    @Override
    protected void createRibbon(Ribbon ribbon) {
        RibbonTab home = ribbon.addTab("home", "Home").setKeyTip("H");
        RibbonContributions.add(home.addGroup("home.notes", "Notes"), save).large();
        RibbonContributions.add(home.addGroup("home.editing", "Editing"), rename);
        command(exit);
    }

    @Override
    protected void createBackstage(Backstage backstage) {
        backstage.addCommand(command(save).id());
        backstage.addSeparator();
        backstage.addCommand(command(exit).id());
    }
}
```
