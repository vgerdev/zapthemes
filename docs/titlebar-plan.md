# Title bar plan

An Office-style title bar for SWT shells, like Microsoft Word's and Excel's. One bar, in the app's
accent color, holds from left to right:

1. the app icon, which opens the system menu (Restore, Move, Size, Minimize, Maximize, Close);
2. the quick access toolbar (AutoSave, Save, Undo, Redo, and the customize dropdown);
3. the document title with its menu ("Document6 - AutoRecovered ˅");
4. a wide, centered search box;
5. slots for app controls ("Upgrade your plan", Comments);
6. the account avatar ("PD"), which opens a profile pop-up;
7. minimize, maximize/restore and close.

It ships in `zapthemes-widgets` (SWT) with a JFace window in `zapthemes-jface`. The ribbon is out of
scope. Publishing to Maven Central stays on hold until the user says everything works. Each step
below ends in its own PR, with tests, demo coverage and screenshots.

## Decisions (asked and answered)

| Question | Answer |
|---|---|
| Native or drawn | Native where possible: Windows extends the client area into the frame, macOS uses a full-size content view with the traffic lights, GTK and anything else get a drawn frame |
| Elements, first version | System menu, window buttons, avatar with a profile pop-up, quick access toolbar, title with a document menu, search box, right-side slots |
| Customization | The app decides which commands can be in the quick access toolbar; users add, remove and reorder them and can show the toolbar below the bar; saved in a preferences file the app chooses |
| API | SWT (`TitleBar` on any `Shell`) and JFace (`TitleBarWindow`, actions in the quick access toolbar, title menu and profile pop-up) |

Decided here (the user can change them later):

- **Colors come from the theme**, through new common names `titleBar`, `titleBarForeground`,
  `titleBarInactive` and `titleBarInactiveForeground`, and `TitleBar` properties for the window
  buttons' hover and pressed colors. Close turns red on hover (`#C42B1C`, as on Windows 11).
- **Accent override:** `TitleBar.setAccent(RGB)` (Word blue `#185ABD`, Excel green `#107C41`). The
  bar's background becomes the accent and everything on it is derived from it: the foreground by
  contrast, hover and pressed by blending the foreground in, the search box a lighter tint. Themes
  decide whether an app accent applies: `TitleBar.useAccent` is true in the light theme and false
  in the dark and high-contrast themes, which keep their own bar, as Office does in dark mode.
- **Inactive windows dim the bar:** the theme's inactive colors, or with an accent, the accent
  blended halfway to the theme background and a muted foreground.
- **High contrast and dark themes** work, a theme switch updates the bar live, and when the OS is
  in high-contrast mode (`Display.getHighContrast()`) the bar uses the system title colors
  (`SWT.COLOR_TITLE_*`).
- **The profile pop-up's content comes from the app:** name, email, avatar image or initials, and a
  list of actions. It is themed as other ZapThemes pop-ups (style class `popup`).

## Platform research

What SWT 3.135.0's internals give, checked with `javap` on the published fragments:

| Need | Windows (`internal.win32.OS`) | macOS (`internal.cocoa`) | GTK (`internal.gtk*`) |
|---|---|---|---|
| Remove the caption, keep the frame | `WM_NCCALCSIZE`, `CallWindowProc`, `SetWindowLongPtr(GWLP_WNDPROC)` with an `internal.Callback`, `SetWindowPos(SWP_FRAMECHANGED)` | `objc_msgSend(window, "setStyleMask:", mask \| NSWindowStyleMaskFullSizeContentView)`, `setTitlebarAppearsTransparent:`, `setTitleVisibility:` | `gtk_window_set_titlebar`, `gtk_window_set_decorated` (no move or resize drag helpers) |
| Hit testing | `WM_NCHITTEST`; `HTCAPTION`, `HTSYSMENU`, `HTCLIENT`, `HTTRANSPARENT` (the `HTTOP*`, `HTMAXBUTTON` codes are plain constants) | none: views take the mouse; `performWindowDragWithEvent:` and `performZoom:` | none: drawn |
| Snap layouts (Windows 11) | `HTMAXBUTTON` from `WM_NCHITTEST` over our maximize button, with `WM_NCMOUSEMOVE`/`WM_NCLBUTTONDOWN`/`UP` handled for it | n/a | n/a |
| Shadow, rounded corners | Kept by the frame (left, right and bottom stay non-client); `DwmSetWindowAttribute` for corners; `DwmExtendFrameIntoClientArea` is not in `OS` and is called through Java's FFM (`java.lang.foreign`) when native access is enabled, to give back the 1 px top border | Native | Drawn (no shadow) |
| System menu | `GetSystemMenu`, `TrackPopupMenu`, `WM_SYSCOMMAND` `SC_KEYMENU` (Alt+Space) | none (the app menu bar is the system menu) | Drawn SWT menu |
| Monitor, DPI | `MonitorFromWindow`, `GetMonitorInfo`, `GetDpiForWindow`, `GetSystemMetricsForDpi` | — | — |

So, per platform:

- **Windows 10/11, native.** The bar's shell keeps `SWT.SHELL_TRIM`. `TitleBar` subclasses the
  shell's window procedure (and its own, which answers `HTTRANSPARENT` over caption areas so the
  shell gets the hit test). `WM_NCCALCSIZE` lets the default procedure compute the frame and then
  puts the top back, so only the caption goes; when maximized it keeps the frame's thickness at the
  top so nothing is cut off. `WM_NCHITTEST` answers resize edges along the top, `HTCAPTION` over
  empty bar space (drag, double-click to maximize, Aero snap and shake, the caption's context menu
  all stay native), `HTMAXBUTTON` over maximize (snap layouts), and `HTCLIENT` over the icon, items,
  search, title, slots, avatar, minimize and close. The icon opens the native system menu at its
  position; Alt+Space keeps opening it natively. All calls go through reflection, as
  `Win32ThemeSupport` does, so other SWT versions fall back to the drawn frame with a warning.
- **macOS, native (CI only).** A full-size content view, a transparent and hidden title, the native
  traffic lights kept. The bar leaves room for the traffic lights on the left, draws no window
  buttons, drags with `performWindowDragWithEvent:` and zooms on double-click with `performZoom:`.
  If any selector is missing the bar falls back to the drawn frame. This cannot be tested locally;
  CI screenshots show it.
- **GTK and anything else, drawn.** The shell is created `SWT.NO_TRIM` (`TitleBar.shellStyle`
  gives the right style per platform). The bar draws the window buttons and the system menu, drags
  the shell, maximizes on double-click, and a display filter gives the shell resize edges on all
  four sides. On Wayland moving a toplevel with `setLocation` does nothing; there the bar calls
  `gtk_window_begin_move_drag` and `begin_resize_drag` through FFM when native access is enabled.
  - **GTK client-side decorations (proposed, not in the first version):** `gtk_window_set_titlebar`
    with an empty widget turns on CSD (shadow, rounded corners, resize edges from GTK) while the SWT
    bar draws the title. It must be called before the window is realized, and SWT realizes the shell
    in `createHandle`; worth a spike once the drawn frame works.
- **A shell with native trim on GTK** (not created with `TitleBar.shellStyle`) keeps the window
  manager's title bar; the bar then hides its icon and window buttons and works as a header row.
- `-Dzapthemes.titleBar.native=false` forces the drawn frame anywhere, for debugging.

## The API

`TitleBar` is a `Composite` attached to any `Shell`, rather than a `TitleBarShell`: `Shell` can't be
subclassed outside SWT (proxies only re-tag it), apps already create their shells, and JFace creates
the shell itself. The bar must be the shell's first, direct child and laid out across the top; the
shell's other children go below it.

```java
Shell shell = new Shell(display, TitleBar.shellStyle(SWT.SHELL_TRIM));
shell.setLayout(new GridLayout());
TitleBar bar = new TitleBar(shell, SWT.NONE);
bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
bar.setAccent(new RGB(0x18, 0x5A, 0xBD));         // Word blue; null = the theme's bar
bar.setTitle("Document6 - AutoRecovered");
bar.setTitleMenu(documentMenu);                    // a Menu shown under the title
bar.setSearchHint("Search");                       // shows the search box; getSearchText()
bar.setProfile(new TitleBarProfile("Paul Davenport", "paul@example.com").initials("PD")
        .action("View account", ...).action("Switch account", ...).action("Sign out", ...));

TitleBarItem save = new TitleBarItem(bar, SWT.PUSH);
save.setId("save");
save.setText("Save");                              // menu text, tooltip and screen-reader name
save.setImage(saveIcon);
save.addListener(SWT.Selection, e -> save());
new TitleBarItem(bar, SWT.CHECK).setId("autosave"); // drawn as the AutoSave switch

Composite slot = bar.getSlot();                    // app controls before the avatar
new FlatButton(slot, SWT.PUSH).setText("Upgrade your plan");

bar.setPreferences(Path.of(configDir, "titlebar.properties"));
```

- `TitleBarItem` is an SWT `Item` (`SWT.PUSH`, `SWT.CHECK`, `SWT.DROP_DOWN`): id, text, image,
  tooltip, enabled, selection; `SWT.Selection` events (detail `SWT.ARROW` for the dropdown part).
  Every item is a quick-access *command*; `setShown(false)` keeps it available but off the bar.
- `TitleBar.getPart(x, y)` answers the hit test (`TitleBar.Part`: `CAPTION`, `SYSTEM_MENU`,
  `ITEM`, `CUSTOMIZE`, `TITLE`, `SEARCH`, `SLOT`, `AVATAR`, `MINIMIZE`, `MAXIMIZE`, `CLOSE`,
  `RESIZE_TOP`, `RESIZE_TOP_LEFT`, `RESIZE_TOP_RIGHT`), shared by every platform and tested
  without native code. `getFrameMode()` says what the platform got: `NATIVE`, `DRAWN`, `EMBEDDED`.
- `minimize()`, `toggleMaximize()`, `close()` and `openSystemMenu()` do what the buttons do.
- Sizes follow the theme font: the bar is 2.4 font heights tall (32 px at 9 pt, as Office), and
  every gap is a fraction of it. A theme change (including the font zoom of PR #19, which
  re-applies the theme) re-lays the bar out.

## Each element

| Element | Behavior |
|---|---|
| Icon / system menu | `shell.getImage()`, 16 px. Click or Alt+Space: the native system menu on Windows; elsewhere a themed SWT menu with Restore, Move, Size, Minimize, Maximize and Close, enabled by state (Move and Size drag with the keyboard arrows on the drawn frame). Double-click closes, as on Windows. |
| Quick access toolbar | Shown items in their order, 16 px icons in square hit boxes; `CHECK` items paint as a labeled switch ("AutoSave ● Off"); `DROP_DOWN` items have an arrow part. The customize button (˅) ends the toolbar. |
| Title | Text in `TitleBar.titleFont`, ellipsized; with a title menu a ˅ follows it and a click opens the menu under it. Centered in the space between the toolbar and the search box when there's room, as Office does. |
| Search | A rounded box with a magnifier and a borderless `Text` (`SWT.SEARCH`); up to 42 font heights wide, shrinking first when the window narrows. Enter fires `SWT.DefaultSelection` on the bar. Ctrl+F style shortcuts are the app's. |
| Slots | `getSlot()`: a composite with a row layout, right-aligned before the avatar. |
| Avatar | The profile's image clipped to a circle, or its initials on a circle in `avatarBackground`; opens the profile pop-up. |
| Profile pop-up | A `popup` shell under the avatar: a large avatar, name, email, then each action as a link-style button; closes on Escape, deactivation or an action. |
| Window buttons | 46×32 boxes at 9 pt (Windows 11 metrics), glyphs drawn with lines; maximize shows restore when maximized. Hidden on macOS (traffic lights) and in `EMBEDDED` mode. |

When the window narrows the bar drops, in order: the search box to a magnifier button, the title,
then toolbar items from the end into the customize menu.

## Customization and persistence

- **The app** creates the items: everything it creates is *available*; `setShown` sets what shows by
  default; `bar.setCustomizable(false)` hides the customize button.
- **The customize menu** (Office's): a header, a check item per available command, "Show Below the
  Ribbon" / "Show Above the Ribbon", and "More Commands...", which opens a themed dialog: the
  available list, the shown list, Add, Remove, Move Up, Move Down, Reset.
- **Below:** the toolbar moves to a second row inside the bar (Office's "below the ribbon" is
  under the app's ribbon, which ZapThemes doesn't have; the app can read `isQuickAccessBelow()` and
  host a `QuickAccessToolbar` elsewhere instead).
- **Persistence:** `setPreferences(Path)` loads the file if it exists (ignoring unknown ids, adding
  new commands with their defaults) and saves on every change, as a properties file:

  ```properties
  # ZapThemes title bar
  quickAccess.shown=autosave,save,undo,redo
  quickAccess.known=autosave,email,new,open,print,redo,save,undo
  quickAccess.below=false
  ```

  `TitleBarCustomization` holds the state, `load`/`store` can be called with any
  `java.util.Properties`, so an app can keep it inside its own preferences.

## Theming

New base common names (group "Title bar", color only), with contrast pairs:

| Name | zap-light | zap-dark | zap-high-contrast |
|---|---|---|---|
| `titleBar` | `$accent` | `#2B2D30` | `#000000` |
| `titleBarForeground` | `#FFFFFF` | `$fg` | `#FFFFFF` |
| `titleBarInactive` | `#7EA1F4` (accent toward white) | `#252629` | `#000000` |
| `titleBarInactiveForeground` | `#F4F6FB` | `$disabled` | `#C0C0C0` |

A new catalog type `TitleBar` (group "Flat widgets"):

| Property | Kind | Default name | |
|---|---|---|---|
| `background`, `foreground` | color | `titleBar`, `titleBarForeground` | |
| `inactiveBackground`, `inactiveForeground` | color | `titleBarInactive`, `titleBarInactiveForeground` | |
| `font`, `titleFont` | font | `foreground`, `foreground` | |
| `hoverBackground`, `pressedBackground` | color | — | derived from the foreground when unset |
| `closeHoverBackground`, `closeHoverForeground`, `closePressedBackground` | color | — | `#C42B1C`, white, `#B22A1B` when unset |
| `searchBackground`, `searchForeground`, `searchBorderColor` | color | — | derived |
| `avatarBackground`, `avatarForeground` | color | `accent`, `onAccent` | |
| `useAccent` | boolean | `true` | whether `setAccent` applies |
| `focusColor` | color | `focus` | keyboard focus ring |

The profile pop-up is a `Shell` with the class `popup`, like other ZapThemes pop-ups; its name label
has the classes `popup strong` and its email `popup muted`. The search field (`TitleBarSearch`) and
the slot (`TitleBarSlot`) are typed so that they take the bar's colors rather than `Text`'s and
`Composite`'s. `:zapthemes-demo:checkCatalog` covers all of it through `TitleBarDemo`'s smoke test.

## JFace integration

`com.zapthemes.jface.TitleBarWindow extends ApplicationWindow`:

- creates the `TitleBar` as the shell's first child in `configureShell` and lays it out above the
  tool bar, cool bar, contents and status line (a copy of `ApplicationWindowLayout` with the bar
  first, since that layout assumes no other children);
- `createTitleBar(TitleBar)` is the hook for the title, accent, search, slots and profile;
- `addQuickAccess(IAction)` creates a `TitleBarItem` bound to the action (text, image from the
  image descriptor, tooltip, enabled, checked for `AS_CHECK_BOX` actions, kept in sync through
  property change listeners; running it runs the action);
- `setTitleMenu(MenuManager)` and `TitleBarProfile.action(IAction)`;
- the window's shell style comes from `TitleBar.shellStyle(getShellStyle())`.

## Accessibility

- **Keyboard:** Alt+Space opens the system menu everywhere (native on Windows, our menu elsewhere).
  Like a native caption, the bar is not a tab stop and doesn't take the focus when the window opens
  (the document keeps it, as in Office). F6 (`setFocusKey`, `focusBar()`) moves the focus to the
  bar and back; there, arrow keys move between the icon, items, title, search, avatar and window
  buttons, Home and End jump, Enter or Space activates, Alt+Down opens a drop-down, and Escape goes
  back to where the focus was. The search `Text` (once shown) and slot controls are ordinary tab
  stops; `focusSearch()` is for the app's search shortcut.
- **Screen readers:** the bar is `ROLE_TOOLBAR` named after the title; each painted part is an
  accessible child (`getChildCount`, `getChildAtPoint`, `getLocation`, `getName`, `getRole`,
  `getState`): items as push buttons or check buttons with their text, the title as a push button
  "Document6 - AutoRecovered, document menu", the avatar as "Account: Paul Davenport", the window
  buttons as "Minimize", "Maximize"/"Restore", "Close".
- Focus is drawn with `focusColor`; high-contrast mode uses system colors.

## Risks

- **Window procedure subclassing (Windows).** SWT dispatches through its own window procedure; ours
  must chain to it for everything else and stay alive as long as the shell (one `Callback` per
  shell, disposed with it). Exceptions in the callback must never escape into native code. If the
  `Callback` or an `OS` method is missing, the bar falls back to `DRAWN` mode, which then needs a
  `NO_TRIM` shell, so it logs a warning and works as `EMBEDDED` under the native caption.
- **Hit testing over child windows.** `WM_NCHITTEST` goes to the child under the mouse, so the bar
  answers `HTTRANSPARENT` over caption areas; that's why the bar must be a direct child of the shell.
- **`Shell.computeTrim` and `pack()`** still count the removed caption on Windows; `TitleBar`
  overrides nothing there, so a packed shell is a caption taller than it needs. Documented.
- **Menu bars:** a shell with a `SWT.BAR` menu keeps it between frame and client; not supported
  together with the native frame (Office has none).
- **Black in the extended frame:** GDI's black is transparent in a DWM-extended area; extending by
  1 px at the top keeps that to a line.
- **macOS can't be tested here;** wrong client area or stolen drags show up only in CI screenshots.
- **FFM calls** (`DwmExtendFrameIntoClientArea`, GTK drags) need `--enable-native-access`; without
  it they are skipped, never warned about at run time.
- **Zoom (PR #19):** the bar follows theme fonts, so it scales once #19 merges; until then its sizes
  follow only the theme's font size.

## Steps

| # | Step | What |
|---|---|---|
| 1 ✅ | **Title bar widget, drawn frame, theming** | `TitleBar`, `TitleBarItem`, `TitleBarProfile` and its pop-up; layout, painting, hit testing, the window buttons, the drawn system menu; the drawn frame (drag, double-click, resize edges); the new base names, catalog entries and bundled theme values; accent override and inactive dimming; accessibility; `TitleBarDemo` (Word and Excel looks) with a smoke test and screenshots in every theme. Tests: hit testing, button actions, theme and accent switching, profile pop-up. |
| 2 ✅ | **Windows native frame** | Window procedure subclassing, `WM_NCCALCSIZE`, `WM_NCHITTEST` with snap layouts, the native system menu, maximized insets, DPI; FFM `DwmExtendFrameIntoClientArea`. Tests: the hit-test codes per part, the client area covering the caption, buttons through the native path. Checked by hand: snap, drag, resize, maximize, shake. |
| 3 ✅ | **Customization and persistence** | `TitleBarCustomization`, the customize menu, "More Commands..." dialog, toolbar below the bar, the preferences file. Tests: round-trip through a file, unknown and new ids, reorder. |
| 4 | **JFace** | `TitleBarWindow`, action-bound items, title menu and profile actions; the JFace variant of the demo. Tests: actions in sync, layout under the bar. |
| 5 | **macOS native frame, docs** | Full-size content view, traffic-light inset, drag and zoom; `docs/theme-authoring.md` (title bar names and properties), `docs/PLAN.md`, the README. CI screenshots on macOS and GTK. |

## Step 1 in detail

`com.zapthemes.widgets.titlebar` in `zapthemes-widgets` (which now uses the `zapthemes-swt` proxies
for the search field, slot, menus and pop-up). `TitleBar` is a double-buffered `Canvas` that paints
everything except the search `Text` and the slot `Composite`; its layout produces a list of hit
rectangles that painting, the mouse, the keyboard, accessibility and `getPart(x, y)` all read. The
row is 2.1 font heights tall (32 px for Segoe UI 9 pt) and every size scales with it. When the middle
is too narrow the search box shrinks to 180 px, then becomes a magnifier button (clicking it expands
the box over the title), then the title is ellipsized and dropped, then toolbar items drop from the
end (they stay in the customize menu). `TitleBarItem` is an `Item` (`PUSH`, `CHECK` drawn as the
AutoSave switch, `DROP_DOWN`, and `TRAIL` for outlined buttons like "Upgrade your plan"), with
`TitleBarGlyph` line icons (new, open, save, undo, redo, print, email, search, share, comment) drawn in
the bar's foreground. The search hint is painted (the `Text` shows once clicked or typed in, so the
hint follows the bar's colors on every platform); `SWT.Modify` and `SWT.DefaultSelection` on the bar
report the search text.

`WindowFrame` chooses the frame: a `NO_TRIM` shell gets `DrawnFrame` (drag with restore-from-maximized,
double-click, a display filter that gives all four edges resize cursors and drags, keyboard Move and
Size from the drawn system menu, Alt+Space); a trimmed shell gets the native frame where step 2 or 5
provides one, else `EMBEDDED` (no icon, no window buttons). `TitleBar.shellStyle` picks `NO_TRIM`
until a native frame exists. `TitleBarColors` derives everything from the theme rule, the accent
(`useAccent`), the active state (inactive accent bars are 70% accent over the window background) or
the OS high-contrast colors. The bundled themes define the four new names and a `TitleBar` rule; the
light theme lets the accent in, dark and high contrast keep their bar.

Tests (`TitleBarTest`, 22): frame modes, hit testing of every part, left-to-right order and the
centered search box, narrowing, scaling, drawn-frame edges and resizing, the window buttons,
double-click, items (push, check, drop-down arrow, disabled), the customize menu, ordering, the
system menu's states, keyboard focus and activation, screen-reader names, theme and accent switching
live, inactive dimming, painted pixels, catalog coverage, initials and the profile pop-up. The demo
(`TitleBarDemo`: Word-like and Excel-like windows) has a smoke test that checks every part, the
colors in each theme, every widget's tag and the pop-up, and screenshots of both windows in each
theme plus a narrow bar.

## Step 2 in detail

`Win32Frame` subclasses the shell's and the bar's window procedures with SWT's `Callback` (four
`long` arguments) and `SetWindowLongPtr(GWLP_WNDPROC)`, chaining to SWT's procedures through
`CallWindowProc`; all of `OS` is reached through method handles, so another SWT version only loses
the native frame (the bar then works `EMBEDDED`, with a warning). On dispose the procedures are put
back and `SWP_FRAMECHANGED` brings the caption back.

- `WM_NCCALCSIZE` reads the proposed rectangle, lets SWT and Windows compute the frame, and writes
  the original top back (plus `SM_CYFRAME + SM_CXPADDEDBORDER` for the window's DPI when maximized).
- `WM_NCHITTEST` on the bar answers `HTTRANSPARENT` over caption, title-without-menu, top-edge and
  maximize parts; the shell first asks the default procedure (side and bottom borders stay native)
  and maps the rest with `Win32Frame.hitCode`. Coordinates go from screen pixels to bar points with
  `ScreenToClient` and the bar's pixel-to-point ratio, so it holds at any monitor scale.
- The maximize button gets `HTMAXBUTTON`, which is what turns on Windows 11 snap layouts; its hover
  and press come as `WM_NCMOUSEMOVE`/`WM_NCLBUTTONDOWN`/`UP`, which the bar paints
  (`setFrameState`) and acts on. A timer clears the hover when the pointer has left, since
  non-client leave messages are not guaranteed. Minimize and close stay client parts.
- **System menu:** SWT binds `TrackPopupMenu` returning a `boolean`, so `TPM_RETURNCMD` can't give the
  command back. The icon instead posts `WM_SYSCOMMAND`/`SC_KEYMENU` with a space, exactly what
  Alt+Space does: the native menu opens under the icon with Windows' own item states. Right-click on
  the caption shows it at the pointer, natively.
- **No FFM:** with only the caption removed, the side and bottom frame keep the DWM shadow, Windows
  11's rounded corners and its 1 px border (top included), so `DwmExtendFrameIntoClientArea` is not
  needed and there is no `--enable-native-access` requirement. Windows 10 shows no 1 px border at the
  top (the bar's own color is the top edge).

Tests (`Win32FrameTest`, Windows only): the frame mode and shell style, the client area starting at
the window's top, real `WM_NCHITTEST` messages for caption, title, maximize, minimize, close, the
icon, an item, the top edge and corner, the client area below the bar and the left border, maximize
and restore through non-client clicks (and no top edge or cut-off when maximized), the hit-code map,
and the caption coming back when the bar is disposed. `TitleBarDemo` now runs with the native frame
on Windows, and `:zapthemes-demo:screenCapturesTitleBar` saves the windows from the screen, frame
included.

Not verified by hand in this step: dragging, Aero snap and shake, the snap-layout flyout and the
shadow were not exercised with a real mouse; the hit codes that drive them are tested.

## Step 3 in detail

`TitleBarCustomization` (a record: shown ids in order, known ids, below) is the state; `of(bar)`,
`store`/`load` with `Properties`, and `save`/`load` with a file. `known` is what makes upgrades
work: a command the app added after the file was saved isn't known, so it keeps the app's default,
while a known command missing from `shown` was hidden by the user. Saving keeps the file's other
keys (an app can share its own preferences file) and writes through a temporary file and a move.

On `TitleBar`: `setPreferences(Path)` (loads now, saves on every change), `getCustomization()` and
`setCustomization()` for apps that keep the state themselves (every change also fires
`SWT.Settings` on the bar), `resetCustomization()` (back to the app's toolbar as it was when the
preferences were set or the customize menu first opened), `setQuickAccessBelow()` and
`openCustomizeDialog()`. The customize menu now ends with "More Commands..." and "Show Below the
Title Bar" / "Show Above the Title Bar". Below, the toolbar gets its own 34 px row in the window's
colors (its empty space is `Part.TOOLBAR`, client area, not caption), and the caption row keeps
only the icon, title, search, right side and window buttons. `QuickAccessDialog` lists the
available commands and the toolbar with Add, Remove, Move Up, Move Down, Reset and the below check
box; OK applies and saves.

`TitleBarDemo` keeps each window's toolbar in `~/.zapthemes/titlebar-demo-<window>.properties` (a
temporary directory in smoke runs); its smoke test checks that a second bar reads back a hidden
command and the below position, and the screenshots add the toolbar below the bar.

Tests (`TitleBarCustomizationTest`, 8): the round trip through a file, new commands keeping their
defaults and unknown ids ignored, other keys kept, `Properties` round trip, the `Settings` event and
`setCustomization`, the customize menu's entries, the below row's layout and hit testing, and the
dialog's add, move, remove, below, OK (saved) and reset.

Also fixed here and in step 1: text is measured with antialiasing on, as it is painted, since GDI+
text is wider than GDI's (titles were ellipsized with room to spare).
