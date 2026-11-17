---
name: convert-to-zapappwindow
description: Converts a JFace application whose windows extend ApplicationWindow (or TitleBarWindow) to ZapThemes' ZapAppWindow, so an Office-style ribbon and backstage replace the menu bar and tool bar while the status line stays. Use when the user asks to move a JFace/SWT app to the ribbon, to ZapAppWindow, or to an Office-style UI, or to turn its menus and tool bar into ribbon tabs. Not for general ribbon questions or for apps without JFace windows.
---

# Convert a JFace application window to ZapAppWindow

`ZapAppWindow` (artifact `com.zapthemes:zapthemes-jface-ribbon`, package `com.zapthemes.jface.ribbon`)
is JFace's `ApplicationWindow` with an Office title bar, a ribbon in place of the menu bar and tool
bar, and the status line. Changing the base class is enough for the app to run: the window turns its
menu and tool bar managers into ribbon tabs and the File backstage at run time. This skill goes
further and writes what a person would: stable action ids, a hand-written `createRibbon()` and
`createBackstage()`, and then removes the menu-bar code those replace.

Read [references/mapping.md](references/mapping.md) before step 3 and
[references/ribbon-api.md](references/ribbon-api.md) before step 5. Use only the calls listed there;
don't guess at ribbon API.

Follow the project's own conventions throughout: explicit imports (no wildcards), its formatting,
Lombok if it already uses it, its build tool. Ask before steps 2 (a new dependency) and 8 (deleting
code). Keep the user informed after each step; offer to stop after step 3.

## 1. Find the windows

Search the sources for:

- classes that extend `ApplicationWindow` or `TitleBarWindow`, directly or through the app's own base
  class (follow `extends` chains to the app's classes);
- `addMenuBar()`, `addToolBar(`, `addCoolBar(`, `addStatusLine()`, and overrides of
  `createMenuManager`, `createToolBarManager`, `createToolBarManager2`, `createCoolBarManager`,
  `createCoolBarManager2`;
- other code that uses the managers or bars: `getMenuBarManager()`, `getToolBarManager()`,
  `getCoolBarManager()`, `getToolBarControl()`, `shell.getMenuBar()`.

List each window with what it uses. With more than one window, ask which to convert. Note windows
that are dialogs or editors rather than application windows; they are out of scope.

## 2. Add the dependency

Find the version of the app's other `com.zapthemes` artifacts and add
`com.zapthemes:zapthemes-jface-ribbon` at the same version, in the app's build file (see
[references/gradle.md](references/gradle.md) for Gradle Kotlin, Gradle Groovy and Maven).

If the app doesn't use ZapThemes yet, stop and explain first: ZapAppWindow needs `zapthemes-jface`,
the ZapThemes fork of JFace, in place of `org.eclipse.jface`, and its widgets come from ZapThemes
proxies. Point to the library's `docs/examples.md` (moving existing SWT and JFace code over) and ask
before going on.

## 3. Change the base class, then build

Replace `extends ApplicationWindow` (or `extends TitleBarWindow`) with `extends ZapAppWindow` and
add `import com.zapthemes.jface.ribbon.ZapAppWindow;`. Leave everything else as it is. Build.

What the app now does is in [references/mapping.md](references/mapping.md), "What the base class
change does". Tell the user the app now runs with an automatic ribbon, and offer to stop here.
`TitleBarWindow` hooks (`createTitleBar`, `addQuickAccess`, `getTitleBarPreferences`) keep working,
since `ZapAppWindow` extends `TitleBarWindow`.

Fix compile errors that come from the change: code that read `getToolBarControl()` as a `ToolBar`,
or `getMenuBarManager().getMenu()` (now `null`: there is no native menu bar).

## 4. Give every action an id

Users' ribbon customizations and the quick access toolbar store command ids, so ids must not change
when menus do. For every action reached from the menu bar or tool bar (submenus included) that has
neither an id nor an action definition id, add `setId(...)` where the action is created, using the
id the automatic conversion would generate, minus its `auto.` prefix (the rule is in
[references/mapping.md](references/mapping.md), "Ids"). For example `&Rename...@F2` in the Edit
menu becomes `rename.setId("edit.rename");`.

Give submenus that become drop-downs an id too: `new MenuManager("&Theme", "window.theme")`.

## 5. Write `createRibbon(Ribbon)`

Build the ribbon the menus and tool bar described, the way a person would design it. Calls are in
[references/ribbon-api.md](references/ribbon-api.md).

- **Home first:** the tool bar's commands and the most-used commands of the Edit menu (clipboard,
  undo/redo, find), in groups with real labels (*Clipboard*, *Editing*). The main one or two
  commands of a group are large (`.large()`).
- **A tab per remaining menu** (not File), in menu order, labeled with the menu text without `&`, its
  mnemonic as the tab's KeyTip. Cut groups at the menu's separators. Label each group from what it
  holds; when that isn't clear from the code, ask the user.
- **Submenus** become drop-down buttons (`RibbonContributions.fill(group, submenuManager)`).
- **Check and radio actions** become toggle buttons by themselves.
- **Large icons:** where the app has 32 px versions of its icons, call
  `ActionCommands.setLargeImage(action, descriptor32)` before the action is added. Don't invent
  icons; list the actions that have none.
- **Native controls on the tool bar** (`ControlContribution`): if one is a combo, add a
  `RibbonComboBox` with the same items and wire its `onChange` to what the control did. Otherwise
  list it for the user: the ribbon can't host native controls.
- **KeyTips:** set item KeyTips from the menu mnemonics where they don't clash within a tab.

Register actions through `command(action)` or `RibbonContributions.add(group, action)`; both keep the
command in sync with the action (enabled, checked, text, accelerator).

Override `convertMenus()` to return `false` once `createRibbon()` builds everything, so the window
doesn't also convert the managers.

## 6. Write `createBackstage(Backstage)`

From the File menu: each action is a command entry (`backstage.addCommand(command(action).id())`),
in menu order, with the menu's separators (`addSeparator()`). A submenu such as *Recent Files*
becomes a page (`addPage`) listing its entries as buttons. *Exit* or *Close* come last, as in
Office. Ask the user whether to add pages they want (Info, Options opening the app's preferences
dialog).

Every action that was in the File menu must still be registered (`command(action)`), so its
accelerator keeps working, even when only the backstage shows it.

## 7. Keep the status line

Keep `addStatusLine()` and every `setStatus(...)`, `getStatusLineManager()` and `run(fork,
cancelable, runnable)` call: they work unchanged, and hovering a ribbon button now also shows its
action's tooltip there. Check that no status or progress code went through the old tool bar's
control.

## 8. Remove the replaced code (ask first)

With `convertMenus()` returning `false` and `createRibbon()`/`createBackstage()` covering every
action:

- if nothing else uses `getMenuBarManager()`, `getToolBarManager()` or `getCoolBarManager()`, remove
  `addMenuBar()`, `addToolBar(...)`/`addCoolBar(...)` and the `create*Manager` overrides;
- if other code still uses a manager (it adds items at run time, or a context menu reuses a
  submenu), keep it. Code that changes the menus at run time and relied on the automatic ribbon
  should then call `refreshRibbon()`, or keep `convertMenus()` returning `true`.

Show the user what will be deleted and ask before deleting.

## 9. Build, run and check

1. Build and run the app's tests.
2. Launch the app (use the `run` skill if the project has one, else the project's run task) and
   check that it opens with the ribbon.
3. **Every action is reachable:** list every action that was in the old menus and tool bar and
   check each one is on a tab, in the backstage, or in a drop-down. Report any that aren't.
4. **Every accelerator still runs:** list the old accelerators (from action texts like
   `"&Save@Ctrl+S"` or `"&Save\tCtrl+S"`, and `setAccelerator` calls) and check each one maps to its
   action. `CommandKeyBindings.install(shell).bindings()` gives the shortcut-to-command table at run
   time; a project with UI tests can assert on it.
5. Take a screenshot if the project has a way to (a demo or smoke-test option), and show it.

Report what was left out: skipped `ControlContribution`s, actions with no large icon, shortcut
clashes, and any menu entries the user should place by hand.
