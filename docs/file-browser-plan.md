# File browser plan

A file-browsing control modeled on the Windows 11 File Explorer, themed by ZapThemes and built on
its proxies, for Windows (win32), Linux (GTK) and macOS (Cocoa). It browses the local filesystem,
a folder of it (a *rooted* view that never goes above that folder), or any virtual filesystem an
application provides, such as the SFTP, SCP, FTP, WebDAV and S3 sessions of zapscp once it moves
into this repository. The Explorer search box (top right) is left out for now.

Publishing to Maven Central stays on hold until the user says everything works. Each step below
ends in its own PR, with tests, demo coverage, and screenshots on Windows and GTK (macOS through
CI).

## Decisions (asked and answered)

| Question | Answer |
|---|---|
| Module | A new `zapthemes-files`: `com.zapthemes.files` (the filesystem abstraction, no SWT) and `com.zapthemes.files.ui` (the widgets). It depends on `zapthemes-swt` and `zapthemes-widgets`, not on JFace. |
| First version's views and panes | Details view, navigation tree, breadcrumb, status bar (step 1); command bar and menus (step 2); the icon views, List, Tiles and Content, details pane and preview pane later. |
| Icons and type names | Icons of our own (drawn at any zoom, themed) plus a table of common type names, with an optional platform icon provider (`Program.findProgram(ext).getImageData()`) for local files. |
| Context menus | Both, as in Windows 11: the condensed menu, ending in "Show more options" (Shift+F10), which opens the classic, longer one; Shift with a right click opens the classic one at once. No "App extensions" or "Customize menu": they serve shell extensions, which a cross-platform control doesn't have; the application's items go in both menus |
| File operations | The control does navigation, selection, sorting, rename in place, new folder and delete through the `FileSystem`; copy, move, transfers and conflicts are the application's (a `FileTransferHandler`), since zapscp already has a queue for them. |

## What zapscp needs

zapscp (`c:\business\mit\zapscp`) has a local and a remote pane (`ui/panel/FilePanel`, a Nebula
`Grid` with Name, Size, Modified, Rights and Owner), over one interface,
`vfs/FileSystemProvider`, with SFTP, SCP, FTP/FTPS, WebDAV and S3 providers and a local one:

- **Paths** are absolute strings in each provider's own syntax, and all path math goes through the
  provider (`normalize`, `resolve`, `parent`, `fileName`, `homeDirectory`). Remote paths are POSIX;
  `normalize` asks the server (SFTP `canonicalPath`, SCP `cd && pwd`).
- **Entries** (`FileEntry`): name, path, directory, symbolic link, size, modified, permissions as
  `rwxr-xr-x`, owner, and the time precision (FTP LIST gives minutes or days). A link to a folder
  counts as a folder. Hidden means a leading dot, on every platform.
- **Threading:** listings run on a virtual thread per pane; results come back through
  `asyncExec`; stale results are dropped by a generation counter. There is no cancellation, and
  "Loading …" shows meanwhile. Errors go to an error handler, and a lost connection to a listener.
- **Operations:** new folder, new file, rename in place (F2 or a slow second click, the name
  selected without its extension), recursive delete, duplicate, chmod with an octal field
  (no chown), edit (download, watch, upload), copy names and paths, paste files to upload.
- **Transfers** are the application's: `TransferService` and a `TransferQueue` (two jobs at once,
  pause, resume, retry), with conflict prompts. Panes are refreshed when a job ends.
- **Drag and drop:** none yet. The new control should add it: `FileTransfer` to and from the OS
  for local files, an in-process transfer for virtual entries, and a drop that the application
  turns into a transfer.
- **Pane features:** show hidden, a mask filter (`*.log; *.txt; dir/`, folders always shown),
  `refresh(selectName)`, `navigate(dir, selectName)`, back and forward, bookmarks, sorting with
  folders first, an `..` row, the selection summary ("N of M selected, size · X hidden"), menu
  fillers for items and empty space, and key, rename and error hooks.

## The filesystem abstraction

Plain Java in `com.zapthemes.files`, no SWT, so it is tested headless and zapscp's providers can
be adapted in a few lines each.

- **`FilePath`**: an immutable, absolute path within one filesystem, as a list of segments
  (`[]` is the top; on Windows the local top holds the drives, so `["C:\\", "tiaa-work"]`).
  `parent()`, `resolve(name)`, `segments()`, `isTop()`, `startsWith(other)`. Two paths compare by
  segments, case-insensitively when the filesystem says so.
- **`FileEntry`** (record): `name`, `path`, `kind` (`FILE`, `DIRECTORY`, `DRIVE`, `LINK_TO_FILE`,
  `LINK_TO_DIRECTORY`, `OTHER`), `size` (or -1), `modified` (`Instant`, may be null),
  `timePrecision`, `permissions` (`rwxr-xr-x` or null), `owner`, `group`, `hidden`, `linkTarget`,
  and a free `attributes` map for what a provider adds (an S3 storage class, say).
- **`FileSystem`** (interface; blocking calls, made off the UI thread):
  - `displayName()`, `topName()` ("This PC", "/", a host name), `home()`;
  - `list(FilePath)`, `stat(FilePath)` (follows links);
  - `parse(String)` and `format(FilePath)` for the address field, and `label(FilePath)` for a
    breadcrumb segment ("OS (C:)");
  - `capabilities()`: `CREATE_DIRECTORY`, `RENAME`, `DELETE`, `READ`, `WRITE`, `PERMISSIONS`,
    `OWNERS`, `CASE_INSENSITIVE`, `SLOW` (remote: the control shows loading and never lists on
    the UI thread even for one folder);
  - optional operations, refused by default with `UnsupportedOperationException`:
    `createDirectory`, `rename`, `delete(path, recursive)`, `openRead`, `openWrite`.
- **`LocalFileSystem`**: NIO. On Windows the top lists the drives
  (`FileSystems.getDefault().getRootDirectories()`), labeled with their volume names
  (`FileStore.name()`, "OS (C:)"); elsewhere the top is `/`. Hidden comes from
  `Files.isHidden` (the DOS attribute on Windows, a leading dot elsewhere). POSIX permissions and
  owners where the platform's view has them.
- **`RootedFileSystem`**: wraps any filesystem at a folder of it. Its top is that folder, named
  after it (or a name the application gives); `format` shows paths relative to it
  (`\reports\2025`); `parse` refuses anything outside, `..` included, and so does every
  operation, so the control can never leave the root.
- **`MemoryFileSystem`**: a tree in memory, built with `MemoryFileSystem.builder()`, with an
  optional delay per call and injected failures. It drives the tests, the demo's "remote" pane,
  and is the example of a virtual filesystem.
- **`FileLoader`** (UI side, in the control): runs `list` and `stat` on one virtual-thread
  executor per control, posts results through `asyncExec`, drops stale ones by a generation
  number, interrupts the previous call when the user moves on, and reports errors to an
  `ErrorHandler` (default: an inline message in the file view, "Access is denied").
- **Change events** (step 7): `FileSystem.watch(FilePath, Consumer<FilePath>)`, optional
  (`Capability.WATCH`), the local one on `WatchService`; the control refreshes the open folder.
- **Trash** (step 7): `FileSystem.trash(FilePath)`, optional (`Capability.TRASH`); the local one
  moves items to the Recycle Bin or Trash with one native call.

A zapscp adapter is then one class: `FilePath` ↔ provider string through `resolve` and `parent`,
`FileEntry` mapped field by field, `SLOW` for every remote protocol.

## The widgets

All in `com.zapthemes.files.ui`, built from ZapThemes proxies and the Flat widgets, themed through
a `FileBrowser` rule (and children's own types) described in the property catalog, and following
the zoom without resizing windows.

- **`FileBrowser`** (a `Composite`): the whole Explorer layout. Public API: `setFileSystem(fs,
  path)`, `navigate(path[, selectName])`, `refresh([selectName])`, `back()`, `forward()`, `up()`,
  `getCurrentPath()`, `getSelectedEntries()`, `setShowHidden`, `setFilter`, `addNavigationListener`,
  `addSelectionListener`, `addOpenListener` (Enter or double-click on a file), `setErrorHandler`,
  `setMenuFiller`, `setPinned(List<FilePath>)` for the tree's quick access.
- **Navigation row** (`AddressBar` plus Flat buttons): Back, Forward, Up, Refresh; the breadcrumb
  is owner drawn: an icon for the top, then a segment per folder with a › chevron after each.
  A chevron opens a scrolling drop-down of that level's subfolders (listed in the background);
  the trailing › lists the current folder's. Clicking empty space turns the bar into a text field
  with the path selected, a clear (×) button and a drop-down of recent locations. Narrow bars
  collapse leading segments into a « menu.
- **Command bar** (step 2): New ▾, Cut, Copy, Paste, Rename, Share, Delete (disabled when they
  don't apply), Sort ▾, View ▾, …; Details toggle at the right. Commands go through the
  `CommandRegistry` of `zapthemes-widgets`, so applications add their own.
- **Navigation pane** (`NavigationTree`, a `Tree`): pinned items with pin icons, a separator, the
  top ("This PC") with its drives or root folders, expandable lazily (children listed in the
  background with a "Loading…" row), then any extra roots the application adds (Network, a remote
  session). Full-row hover and selection. It follows the current folder.
- **File view** (`FileTable`, a virtual `Table`, Details view): Name, Date modified, Type, Size,
  plus optional columns (Permissions, Owner, Group) when the filesystem has them. Folders first,
  click a header to sort, a sort arrow, sizes in KB, type names, icons. Enter and double-click open
  folders. Rename in place (F2, Rename, slow second click), with the name selected without its
  extension. Other views (icons, List, Tiles, Content) later as an owner-drawn view.
- **Hidden and temporary files** (Office lock files such as `~$report.docx`, dot files) are shown,
  when hidden files are on, with a faded icon.
- **Status bar:** "9 items", "3 items selected 1.2 MB", "Loading…"; at the right, Details and
  Large icons toggles.
- **Context menus:** on empty space (View ▸, Sort by ▸, Group by ▸, Refresh, New ▸, Properties) and
  on items (Open, Cut, Copy, Rename, Delete, Properties), each extended by `setMenuFiller`.
- **Icons** (`FileIcons`): our own folder, file, document, image, archive, code, drive, computer,
  network and pin icons, drawn at the current zoom; a `IconProvider` hook for platform icons.

## Steps

| # | Step | What |
|---|---|---|
| 1 ✅ | **Abstraction and first control** | `zapthemes-files`; `FilePath`, `FileEntry`, `FileSystem`, local, rooted and memory filesystems; `FileLoader`; `FileBrowser` with the navigation row and breadcrumb (chevron drop-downs), navigation tree, Details view, status bar; tests; `FileBrowserDemo` with `--smoke` and screenshots, local and rooted and a slow memory "remote" pane side by side. |
| 2 ✅ | **Commands and operations** | Command bar, View/Sort menus, context menus; rename in place, new folder, delete (to trash where the platform has it); cut/copy/paste through the clipboard and a `FileTransferHandler`. |
| 3 ✅ | **Address editing** | The editable path field, clear button, completion of folder names, and the drop-down of recent and typed locations. A history entry is a *location* (a filesystem, a path and a label), not a string, so it can name places of any filesystem: plain paths (`C:\opt`), named places ("OneDrive", "Recycle Bin" style entries an application adds), remote folders. |
| 4 ✅ | **Drag and drop** | Drag out (`FileTransfer` for local files, an in-process `FileEntryTransfer` for virtual ones), drop in from the OS and between browsers, handed to the `FileTransferHandler`; drop highlighting on folders. |
| 5 ✅ | **More views and panes** | Icon sizes, List, Tiles, Content; Group by; details pane; preview pane (images, text). |
| 6 | **zapscp fit** | Permissions, Owner, Group columns; show hidden and mask filter; bookmarks into the tree; the adapter written and tested against zapscp's providers once it is merged. |
| 7 ✅ | **Platform icons and watching** | Platform icons and type names (opt-in); `watch` for local folders; local deletes to the platform trash. |

## Risks

- **Slow and failing listings:** every call off the UI thread, cancellation by interrupt (an SFTP
  call may ignore it, so results are also dropped by generation), errors shown inline. The memory
  filesystem's delay and failures test it.
- **Native Table and Tree theming:** GTK and Cocoa don't color everything (headers, full-row
  hover). We use what the `Table`/`Tree` stylers already do, and paint full-row hover where the
  platform doesn't; owner-drawing the view is the fallback.
- **Breadcrumb drop-downs:** popups lose focus under Xvfb, so tests count what opened rather than
  checking it is still open.
- **Windows drive labels:** `FileStore.name()` can block on a disconnected network drive; drives
  are labeled in the background.
- **Huge folders:** a virtual `Table` (`SWT.VIRTUAL`) keeps 100 000 entries usable.

## Assumptions to confirm

- **Delete** of local files goes to the Recycle Bin or Trash since step 7, through one native call
  per platform with the JDK's foreign function API (no AWT); Shift+Delete, and virtual
  filesystems, delete for good after a confirmation the application can replace
  (`setDeleteConfirmation`).
- **Group by** (by type, date, size; collapsible group headers) came in step 5: owner-drawn headers
  in the grid, and header rows in the Details table.

- The default `FileTransferHandler` copies and moves local to local only; virtual filesystems
  need the application's handler.

## Step 1 in detail

`zapthemes-files` holds the filesystem abstraction in `com.zapthemes.files` (no SWT in its API) and
the widgets in `com.zapthemes.files.ui`.

- **Abstraction:** `FilePath` (segments from the top, never `.` or `..`), `FileEntry` (a record
  with a builder: kind, label, size, modified, time precision, permissions, owner, group, hidden,
  link and target, free attributes), `EntryKind`, `Capability`, `FileSystem` (blocking calls,
  optional operations refused unless a capability names them), `Place`, `PathNames` (splitting
  and resolving typed paths), `FileTypes` (type names, icon categories, Explorer sizes) and
  `FileOrder` (folders first, natural name order, a comparator per column).
- **`LocalFileSystem`:** NIO; on Windows the top is "This PC" listing the drives with their
  volume names ("OS (C:)"), the DOS hidden attribute; POSIX permissions, owners and groups where
  the platform has them; `standardPlaces()` for the navigation pane.
- **`RootedFileSystem`:** any filesystem at one of its folders; refuses `..` above the top,
  separators in names, and full paths outside the root, and accepts full paths inside it.
- **`MemoryFileSystem`:** a builder-made tree with POSIX attributes, a delay per call and
  injected failures; full read, write, rename, create and delete.
- **`FileBrowser`:** the navigation row (`NavigationBar`, one canvas: Back, Forward, Up,
  Refresh; the breadcrumb with a drop-down per chevron, `«` for segments that don't fit, and the
  path field with a clear button), the navigation pane (`NavigationTree`: places with pins, a
  separator, the top with lazily listed folders, following the current folder), the Details view
  (`FileTable`: a virtual table, Permissions, Owner and Group columns when the filesystem has
  them, header sorting, a line of text when empty, loading or failed), and the status bar.
  Hidden files are off by default and shown with faded icons. Every call runs on a virtual
  thread (`FileLoader`); stale results are dropped by a generation number and the superseded call
  is interrupted. Failures go to an `ErrorHandler`, by default the status bar.
- **Icons** (`FileIcons`) are drawn in code on a 16-unit grid through an `ImageDataProvider`, so
  they are sharp at any zoom and on high-DPI screens; rendering on black and on white gives
  antialiased transparency. Tables and trees keep the size of their first image, so the icon and
  name of the first column are drawn (`ItemPainter`) rather than set as images, and rows grow
  with the zoom.
- **Theme types:** `FileBrowser`, `FileNavigationBar` and `FileStatusBar` (described in the
  catalog by `FileStylers`); keys a theme leaves out are derived from the bar's background and
  foreground, so the bundled themes need no new rules.
- **Tests:** `FileSystemsTest` (paths, order, types, sizes, memory, local, rooted over local and
  over memory) and `FileBrowserTest` (listing, hidden files, sorting, history, chevron and places
  drop-downs, the overflow chevron, editing the path, slow listings and stale results, failures,
  the rooted top, the tree, zoom, theme types, icon transparency).
- **Demo:** `FileBrowserDemo` (`runFileBrowser`, `smokeTestFileBrowser`, `screenshotsFileBrowser`)
  with three tabs: the local disks, a rooted sample folder, and a slow server with permissions.

Known limits, for later steps:

- On Windows, the sorted column of a native table is shaded, which Explorer doesn't do.
- The navigation pane and the Details view are a native `Tree` and `Table`; full-row hover and
  some colors follow what each platform's control does.

## Step 2 in detail

- **Commands** (`FileActions`): each browser owns its `Command`s (from `zapthemes-widgets`), not
  the shell's registry, since one window can hold several browsers. Their enabled state follows the
  selection, the filesystem's capabilities and the clipboard, so an application can bind them to a
  ribbon or menu (`getCommand(FileBrowser.COMMAND_COPY)`); `addCommand` puts its own commands on
  the command bar.
- **Command bar** (`CommandBar`, theme type `FileCommandBar`): New ▾, Cut, Copy, Paste, Rename,
  Share, Delete, Sort ▾, View ▾, "…" and the Details toggle (disabled until the details pane).
  When narrow, labels go first, then commands move into "…".
- **Menus** (native, through the `Menu` proxies): View (the layouts, with only Details enabled
  for now; Details and Preview pane, disabled; Show ▸ Navigation pane, File name extensions,
  Hidden items), Sort (columns, ascending or descending, Group by ▸ disabled), New (Folder,
  Text document), "…" (Select all, Select none, Invert selection, Copy as path, Refresh,
  Properties). The context menu of empty space is the condensed one (View, Sort by, Group by,
  Refresh, Paste, New, Properties), and of items: Open, Cut, Copy, Paste into folder, Rename,
  Delete, Copy as path, Share, Properties. Both end in "Show more options" (Shift+F10), which
  opens the classic menu: for items Open, Share, Cut, Copy, Paste into folder, Delete, Rename,
  Copy as path, Copy name, Select all, Invert selection, Properties; for empty space View, Sort
  by, Group by, Refresh, Paste, New, Select all, Hidden items, File name extensions, Copy folder
  path, Properties. Shift with a right click opens it at once. `addMenuContributor` adds the
  application's items to both.
  The navigation pane has the item menu too, for its folders, without opening them.
- **Rename in place** (`InlineEditor`): F2, Rename, or a slow second click on the name opens a
  field over it, with the name selected without its extension; Enter or a click elsewhere commits,
  Escape cancels. It works in the file view and in the navigation pane (not for places or the top).
- **New folder and text document** take the first free name ("New folder (2)"), are selected and
  opened for renaming, as in Explorer.
- **Clipboard:** Cut and Copy put the items on the system clipboard (as files for other
  applications when they are local, and as text paths) and remember them in the process, so
  another browser, of any filesystem, can paste them. Pasting files another application copied
  works too. Cut items are faded until they are pasted.
- **Transfers:** a paste becomes a `TransferRequest` for the `FileTransferHandler`. The default
  (`FileTransferHandler.direct()`, using `FileTransfers`) copies through the `FileSystem` API
  in the background, folders recursively, never overwriting: Explorer's names, "x - Copy" in the
  same folder and "x (2)" elsewhere. A move within one filesystem uses `FileSystem.move` (new,
  with `Capability.MOVE`); otherwise it copies, then deletes. zapscp will set its own handler
  that feeds its queue.
- **Keys:** Ctrl+X, C, V, A, Shift+C (copy as path), Shift+N (new folder); Del; F2; Alt+Enter.
- **Tests:** `FileOperationsTest` (command states, new folder, rename, F2 and the menu, delete
  and its confirmation, copy and paste in one folder, cut and paste into another, paste between
  browsers and filesystems, both context menus, the Sort menu, the Show toggles, the command bar
  folding into "…", shortcuts) and transfer tests in `FileSystemsTest`. The demo's smoke test
  creates, renames, copies, pastes and deletes in the rooted folder, and uploads a file from it
  to the slow server.

## Step 3 in detail

- **Locations** (`Location`, in the core): a filesystem, a folder of it, an optional label and an
  optional icon kind. Without a label it shows as its path, followed by its filesystem's name when
  a browser of another filesystem lists it ("/etc on deploy@build-01"); with one, as the label
  ("Projects", "Server home"), and typing the label goes there.
- **`LocationHistory`**: the recent locations, most recent first, each folder once (adding it
  again moves it up and keeps its label), at most 25 as in Explorer. Typed locations and those
  picked from the drop-down go in; clicks, the breadcrumb and the tree don't, as in Explorer.
  Applications add named places and remote folders. `FileBrowser.setLocationHistory` shares one
  list between browsers (two panes, the tabs of a window). It isn't saved: an application keeps
  `entries()` in its own terms and adds them back.
- **Other filesystems:** `navigate(Location)` opens a location of any filesystem. For another one
  the application's `setLocationHandler` decides first (the demo opens it in the tab of that
  filesystem; zapscp will open a remote folder in the remote pane); otherwise the browser switches
  to that filesystem. Back and Forward hold locations, so they cross filesystems too.
  `setFileSystem` still clears them.
- **The address field** (`AddressField`): the path, selected; the clear button; and the
  drop-down button at the right end of the bar (in both modes), which opens the recent locations,
  as F4 and Alt+Down do. While you type, a drop-down under the bar lists the recent locations that
  start with the text, then the subfolders whose names complete its last part (listed in the
  background, once per folder, hidden ones only when shown; a name alone completes in the current
  folder). Down and Up move through it, putting each in the field, and Up past the first gives
  back what was typed; Tab completes with the selected or first one; Enter goes to the selected
  one, else to the text; Escape closes the drop-down, then stops editing. The drop-down never
  takes the focus (`FolderPopup.suggest`, a `NO_FOCUS` shell), so typing goes on. A rooted
  browser suggests nothing above its top, since its `parse` refuses it.
- **Tests:** `LocationHistoryTest` (order, limit, labels kept, typing a label, matching, same
  folder) and `AddressBarTest` (what goes into the history, the drop-down button and F4, typed
  suggestions with Down, Up, Tab and Enter, the rooted top, a shared history across filesystems
  with Back and Forward and the location handler, Escape). The demo shares one history between its
  three tabs, with "Projects" and "Server home"; its smoke test types a path, picks a suggestion,
  and opens the server's home from the local tab's drop-down, which lands in the server's tab.

Known limits, for later steps:

- `FileSystem.parse` runs on the UI thread for each suggestion; it should not block (a zapscp
  adapter parses locally rather than asking the server).
- Drives are not suggested from an empty name on Windows ("C" doesn't suggest "C:\").

## Step 4 in detail

- **Dragging out** (`FileDragAndDrop`): the selected items of the file view, or the folder dragged
  in the navigation pane (not a place, the top or a drive). They go as a `FileEntryTransfer`
  (public, so an application's own controls, a transfer queue say, can take them), an in-process
  transfer whose data is a `Dragged` (a filesystem and its entries) that never leaves the
  process; and, when every item is on the local disk, as a `FileTransfer` too, so Explorer, the
  desktop or an editor take the files. When another application took them, the folder is listed
  again (it may have moved them).
- **Dropping in**, on the file view or the navigation pane: onto a folder row or tree item, into
  that folder, highlighted as the theme paints a row under the mouse; onto a file or empty space,
  into the folder shown. The tree expands a folder held over, and both scroll at their edges.
  Nothing can go onto "This PC" on Windows, a folder can't go into itself or below it, and a move
  into the folder the items are in does nothing.
- **Move or copy**, as in Explorer: within one volume a drop moves (the same filesystem, or on the
  disk the same drive, also between a rooted view and the whole disk); between drives or
  filesystems it copies; Ctrl copies, Shift moves. A move needs `MOVE` or `DELETE` of the source,
  else it copies. Files of another application are copied, or moved with Shift, the browser doing
  the move and reporting a copy, so the other application never deletes them itself.
- **Transfers:** a drop becomes a `TransferRequest` for the browser's `FileTransferHandler`, as a
  paste does ("Can't move: …" or "Can't copy: …" when it fails). Between two views of the disk the
  request is made on the local filesystem itself, so a move is a rename on disk. Afterwards both
  browsers list their folders again, the items of moved folders leave the source's navigation
  pane, and the target folder's subfolders there are listed again.
- **Tests:** `DragAndDropTest` (where a drop goes, from rows and tree items; move, copy, Ctrl and
  Shift, within one filesystem and between two; folders into themselves; the handler gets the
  request; a moved folder leaves the tree and the other browser; a copy to another filesystem
  keeps the original; the highlight; what can be dragged; two views of the disk move on disk).
  The demo's smoke test copies onto a folder with Ctrl, is refused a folder onto itself, moves a
  file into a folder of the rooted tab and back into the local tab, and uploads a file by dropping
  it on the server's tab; the `filebrowser-drop` screenshot shows the highlight.

Known limits, for later steps:

- The tests and the smoke test drive the browser's side of a drop; the native drag itself (the
  platform's drag loop, with the mouse) isn't automated, since it would take over the mouse.
- Virtual entries can't be dropped on other applications (they would need a download first,
  through the application's transfer handler).
- No drops on the breadcrumb's segments yet, nor spring-loaded folders in the file view.

## Step 5 in detail

- **Layouts** (`FileView`, `FileBrowser.setView`): Extra large, Large, Medium and Small icons,
  List, Details, Tiles and Content, as in Explorer's View menu, with its shortcuts
  (Ctrl+Shift+1 to 8). They're also in the status bar's Details and Large icons toggles.
  - Details stays the native virtual `Table`.
  - The other layouts are one owner-drawn view (`FileGrid`, a `Canvas`). It does selection with
    the mouse and keys (Ctrl, Shift, arrows, Home, End, Page Up and Down, typing a name's first
    letters), a rubber band on empty space, hover, rename in place, drag and drop, and basic
    accessibility.
  - Icon sizes at 100 %: 256, 96, 48 and 16 pixels; List 16, Tiles 48, Content 32. They follow
    the zoom.
  - Icons, List and Tiles flow in rows; List flows in columns and scrolls sideways when it isn't
    grouped.
- **Thumbnails:** image files show their picture in the icon layouts from Medium up, and in Tiles
  and Content.
  - They're read in the background, one at a time, only for items in view, and kept in a small
    cache.
  - Only on a filesystem with `READ`, and only for files up to 20 MB.
- **Group by** (`setGroupBy(key, ascending)`): Name, Date modified, Type, Size, and Permissions or
  Owner where the filesystem has them; ascending or descending. The grouping itself is
  `FileGroups`, with no SWT:
  - Name: 0–9, A–H, I–P, Q–Z, Other.
  - Date: Today, Yesterday, Earlier this week, Last week, Earlier this month, Last month, Earlier
    this year, A long time ago, Unknown.
  - Size: Empty, Tiny (under 16 KB), Small (under 1 MB), Medium (under 128 MB), Large (under
    1 GB), Huge (under 4 GB), Gigantic, Unspecified (folders).
  - Type, Permissions and Owner group by value.
  - Every layout shows group headers, Details included (header rows in the table). A header
    shows the group's name and item count. Its chevron collapses the group, and a click on the
    header selects the group's items. Items keep the sort order within their group.
- **Panes** at the right, behind a sash. Only one shows at a time, as in Explorer:
  - The details pane (Alt+Shift+P) shows a large icon or thumbnail, the name and type, then
    Size, Date modified, Permissions, Owner, Group and Link target as the entry has them. With
    several items selected it shows "N items selected" and their size; with none, the folder
    shown and its item count.
  - The preview pane (Alt+P) shows images scaled to fit, and text files (up to their first
    64 KB) in a read-only field. Other files show "No preview available."; with nothing
    selected it says "Select a file to preview."
  - Both read in the background, and a newer selection drops an older read.
- **Theming:** the grid and the panes read the Details table's colors (background, foreground,
  selection, hover), so a theme that styles `Table` styles them too. A `FileItemView` rule can
  override them, and is in the property catalog.
- **Tests:**
  - `FileGroupsTest`: the groups by name, date, size, type and owner, and their order.
  - `ViewsAndPanesTest`, 20 tests:
    - Every layout lists the same items and keeps the selection; the shortcuts, the View menu
      and the status bar toggles pick the layout; the layouts size their items and follow the
      zoom without resizing the window.
    - In the grid: clicks with Ctrl and Shift, the rubber band, the keys and typing, opening,
      rename in place, drops onto folders, and the context menus.
    - Groups in the grid and in Details: collapsing them, a header selecting its group, the
      Group by menu, and forgetting collapsed groups in another folder.
    - The details and preview panes, text read up to the limit, thumbnails, shrinking pictures,
      and names wrapping after hyphens and dots.
    - A hidden navigation pane or table stays hidden.
  - The demo's smoke test goes through every layout, waits for a thumbnail, groups by type in the
    grid and in Details, and checks both panes. New screenshots: each layout, Large icons in
    every theme, grouped icons and Details, the details pane, and image and text previews.
- **Fixed on the way:**
  - On Windows, turning redraw back on for a hidden table or tree shows it again. That put the
    Details table back over the grid, and would have brought back a hidden navigation pane.
  - On GTK, a virtual table that shrinks doesn't mark its dropped rows disposed, so theming one
    later crashed. `ThemeManager` now skips rows their table or tree no longer has.

Known limits, for later steps:

- Explorer keeps a layout per folder; the browser keeps one until the application changes it.
- The preview pane shows pictures and text only, with no handler for other types (PDF, Office)
  yet. Text is shown in the theme's font, without syntax colors.
- Group headers in Details are rows of the native table, as high as the other rows.
- Ctrl with the mouse wheel zooms the whole application, as it does elsewhere in ZapThemes, rather
  than going through the layouts as in Explorer.

## Step 7 in detail

- **Icon providers** (`IconProvider`, `FileBrowser.setIconProvider`): where icons and type names
  come from first. Off by default, so the browser looks the same everywhere; what a provider has
  nothing for keeps the browser's own icon and type name.
  - A provider gives a key per entry (`".txt"`), the image for a key at a size in pixels, and a
    type name. Images are made once per key and size, and asked for again at each zoom's pixels,
    so they are sharp at any zoom. Hidden and cut items fade, as the browser's own do.
  - `IconProvider.platform()` uses `Program.findProgram(ext)`, cached per extension, for local
    files (the disk, or a rooted view of it); `platformForAllFileSystems()` does it for every
    filesystem's files, by extension. Folders and drives keep the browser's icons.
  - Windows extracts a type's icon at any size, so the platform icons are sharp in every layout.
    Where a platform has only small ones, they are scaled up to twice their size; beyond that the
    browser's own icon is drawn rather than a blurred one.
  - Type names come from `Program.getName()` on Windows ("Text Document"). Elsewhere that is the
    application's name ("Text Editor"), so the browser's own type names stay.
  - The Details table, every grid layout, the tooltips and the details pane use them, and so do
    sorting and grouping by type (`FileOrder.by` and `FileGroups.group` take the type names).
- **Watching** (`FileSystem.watch`, `Capability.WATCH`): optional and refused by default. The
  listener gets the path of what changed, or the folder itself when it went away. Closing the
  returned `Watch` stops it.
  - `LocalFileSystem`: one `WatchService` and one daemon thread for all folders, started with the
    first watch and stopped after the last. On macOS the JDK polls (every few seconds).
  - On Windows a watched folder keeps its parents from being renamed or moved. So the
    filesystem's own rename, move, delete and trash pause the watches below what they change,
    then watch again what is still there, and tell the listeners of what is gone.
  - `RootedFileSystem` passes watches through, in its own paths. `MemoryFileSystem` tells its
    watches of its own changes (on the thread that made them), so tests can drive it.
  - `FileBrowser` (`FolderWatcher`) watches the folder shown, starting the watch in the
    background, and lists the folder again a moment after the last change of a burst (300 ms;
    at least every 2 s while changes keep coming).
    - It keeps the selection and the scroll position, and lists the folder's subfolders again in
      the navigation pane.
    - It waits while a listing loads or a rename field is open.
    - When the folder shown went away, the folder above it opens.
    - The watch moves with the browser, and stops when the browser shows another filesystem or
      is disposed. `setWatchFolders(false)` turns it off.
- **Trash** (`FileSystem.trash`, `Capability.TRASH`): Delete moves local items to the Recycle Bin
  or Trash without asking, as Explorer does. Shift+Delete (`deletePermanently()`) and filesystems
  without a trash delete for good, after the confirmation. What the trash can't take (a network
  drive on Windows) is offered to be deleted for good.
  - Java has no API for the trash without AWT (`Desktop.moveToTrash`), so `PlatformTrash` makes
    one native call through the JDK's foreign function API:
    - Windows: `SHFileOperationW` with `FOF_ALLOWUNDO`, for fixed drives only (`GetDriveTypeW`),
      since on other drives it would delete for good without a word. It warns before deleting
      something too big for the Recycle Bin.
    - Linux and the BSDs: GIO's `g_file_trash`, the freedesktop.org trash.
    - macOS: `NSFileManager trashItemAtURL:resultingItemURL:error:` through `objc_msgSend`.
  - Where the library doesn't load, there is no `TRASH`, and deletes stay permanent. The build
    already enables native access for SWT (`--enable-native-access=ALL-UNNAMED`); without it the
    JDK only warns.
- **Tests:**
  - `WatchAndTrashTest` (headless, 8 tests): memory watches and what they hear, a watched folder
    that goes away, rooted watches in rooted paths, refusing by default, local watches, renaming
    the parent of a watched folder, a paused watch that goes on, and the local trash.
  - `IconsAndWatchingTest`, 12 tests:
    - A provider's icons and type names in every place, at every size and zoom; its fallbacks
      and its cache; sorting and grouping by its type names; faded icons. The platform provider,
      on local files only. Scaling with transparency.
    - A watched folder listed again once per burst, keeping the selection and the scroll in
      Details and in the grid; the watch moving with the browser, turned off, and stopping with
      it; a folder that goes away; a rename in place not interrupted; a local folder.
    - Delete to a trash without asking; Shift+Delete, and a trash that refuses, asking first.
  - Really trashing files (in both test classes and in the demo) runs on CI only, or with
    `ZAPTHEMES_TRASH_TEST` set, so local runs don't fill the user's Recycle Bin.
  - The demo has a "Platform icons" check box. Its smoke test goes through every layout with the
    platform icons, has another program write and delete a file in the rooted tab's folder, and
    on CI deletes a file to the trash. New screenshots: platform icons in Details, Large icons,
    Tiles and at 150 %.

Known limits, for later steps:

- On Windows, another program can't rename a parent of the folder a browser shows, since the watch
  holds it open (Explorer's own watches don't). The browser's own operations pause it.
- The navigation pane doesn't watch the folders it shows, only the one in the file view.
- Platform icons are per extension: an `.exe` gets the generic program icon, not its own.
- The thumbnail of a picture changed on disk stays as it was until the folder is opened again.
