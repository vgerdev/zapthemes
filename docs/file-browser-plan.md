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
- **Change events** (later): `FileSystem.watch(FilePath, Consumer<FilePath>)`, optional, the local
  one on `WatchService`; the control refreshes the open folder.

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
| 3 | **Address editing** | The editable path field, clear button, completion of folder names, and the drop-down of recent and typed locations. A history entry is a *location* (a filesystem, a path and a label), not a string, so it can name places of any filesystem: plain paths (`C:\opt`), named places ("OneDrive", "Recycle Bin" style entries an application adds), remote folders. |
| 4 | **Drag and drop** | Drag out (`FileTransfer` for local files, an in-process `FileEntryTransfer` for virtual ones), drop in from the OS and between browsers, handed to the `FileTransferHandler`; drop highlighting on folders. |
| 5 | **More views and panes** | Icon sizes, List, Tiles, Content; Group by; details pane; preview pane (images, text). |
| 6 | **zapscp fit** | Permissions, Owner, Group columns; show hidden and mask filter; bookmarks into the tree; the adapter written and tested against zapscp's providers once it is merged. |
| 7 | **Platform icons and watching** | Platform icons and type names (if wanted); `watch` for local folders. |

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

- **Delete** is permanent, after a confirmation the application can replace
  (`setDeleteConfirmation`). Moving local files to the platform trash needs native calls on
  Windows (no Java API without AWT); it is a step 7 item.
- **Group by** (by type, date, size; collapsible group headers) comes with the owner-drawn views in
  step 5, since a native `Table` has no groups.

- Delete goes to the platform trash for local files where SWT or the JDK offers it
  (`Desktop.moveToTrash` needs AWT, so possibly a small native call per platform), and is
  permanent for virtual filesystems, after a confirmation the application can replace.
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

