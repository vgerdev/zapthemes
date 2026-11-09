package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.StyledText;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.CustomizationStore;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonTab;
import com.zapthemes.widgets.ribbon.Size;
import com.zapthemes.widgets.titlebar.TitleBar;
import com.zapthemes.widgets.titlebar.TitleBarGlyph;
import com.zapthemes.widgets.titlebar.TitleBarItem;
import com.zapthemes.widgets.titlebar.TitleBarProfile;

/**
 * Office-style title bars: a Word-like window (blue) and an Excel-like one (green), each with the
 * system menu, a quick access toolbar (AutoSave, Save, Undo, Redo and more in the customize menu),
 * the document title with its menu, a centered search box, an account avatar with a profile
 * pop-up, and the window buttons. Below each bar is a ribbon whose File tab opens the backstage
 * and whose items' context menu has <em>Add to Quick Access Toolbar</em>.
 *
 * <p>The Word window builds its quick access toolbar from the ribbon's {@link Command}s and keeps
 * both bars' customizations in one {@link CustomizationStore} file; the Excel window uses plain
 * title bar items and a Properties file, the other way an app can do it.
 *
 * <p>{@code --smoke} opens both windows, switches through every bundled theme, checks the bar's
 * parts, colors and profile pop-up and every widget's theme tag, and exits (non-zero on failure).
 * {@code --screenshots <dir>} also saves both windows in every theme.
 */
public final class TitleBarDemo {

    static final RGB WORD_BLUE = new RGB(0x18, 0x5A, 0xBD);
    static final RGB EXCEL_GREEN = new RGB(0x10, 0x7C, 0x41);
    static final RGB POWERPOINT_ORANGE = new RGB(0xC4, 0x3E, 0x1C);

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available;
    private final List<Window> windows = new ArrayList<>();
    private final List<Image> images = new ArrayList<>();
    private final DemoIcons icons;
    private Path preferencesDir = Path.of(System.getProperty("user.home"), ".zapthemes");

    private record Window(String name, org.eclipse.swt.widgets.Shell shell, TitleBar bar, RGB accent) {
    }

    private TitleBarDemo(Display display) {
        this.display = display;
        this.icons = new DemoIcons(display);
        this.themes = ThemeManager.get(display);
        this.available = DemoSupport.bundledThemes();
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        boolean smoke = options.contains("--smoke");
        int shots = options.indexOf("--screenshots");
        int captures = options.indexOf("--screen-captures");
        Display display = new Display();
        TitleBarDemo demo = new TitleBarDemo(display);
        if (smoke) {
            try {
                demo.preferencesDir = java.nio.file.Files.createTempDirectory("titlebar-demo");
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        demo.open();
        if (shots >= 0) {
            demo.screenshots(Path.of(options.get(shots + 1)));
        }
        if (captures >= 0) {
            demo.screenCaptures(Path.of(options.get(captures + 1)));
        }
        int exitCode = smoke ? demo.smokeTest() : 0;
        if (smoke) {
            demo.windows.forEach(w -> w.shell.dispose());
        }
        while (demo.windows.stream().anyMatch(w -> !w.shell.isDisposed())) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        demo.images.forEach(Image::dispose);
        demo.icons.dispose();
        display.dispose();
        if (smoke) {
            System.exit(exitCode);
        }
    }

    private void open() {
        Window word = window("word", "Document6 - AutoRecovered", WORD_BLUE, 'W', false);
        Window excel = window("excel", "Book1 - Excel", EXCEL_GREEN, 'X', true);
        word.shell.setBounds(60, 40, 1180, 760);
        excel.shell.setBounds(140, 120, 1180, 760);
        word.shell.open();
        excel.shell.open();
        windows.add(jfaceWindow());
        word.shell.forceActive();
    }

    // ------------------------------------------------------------------ the windows

    private Window window(String name, String title, RGB accent, char letter, boolean spreadsheet) {
        Shell shell = new Shell(display, TitleBar.shellStyle(SWT.SHELL_TRIM));
        shell.setImage(appIcon(accent, letter));
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        shell.setLayout(layout);

        TitleBar bar = new TitleBar(shell, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        bar.setAccent(accent);
        bar.setTitle(title);
        bar.setTitleMenu(documentMenu(shell, title));
        bar.setSearchHint("Search");
        Window window = new Window(name, shell, bar, accent);
        CommandRegistry commands = CommandRegistry.of(shell);
        defineCommands(commands, shell);
        // Users' changes to the quick access toolbar survive restarts: Word keeps them with the
        // ribbon's in one store, Excel in a Properties file of its own.
        CustomizationStore store = null;
        if (spreadsheet) {
            quickAccess(window);
            bar.setPreferences(preferencesDir.resolve("titlebar-demo-" + name + ".properties"));
        } else {
            commandQuickAccess(window, commands);
            store = CustomizationStore.open(storeFile(name));
            bar.setCustomizationStore(store);
        }
        if (spreadsheet) {
            new TitleBarItem(bar, SWT.PUSH | SWT.TRAIL).setText("Upgrade your plan");
        }
        bar.setProfile(new TitleBarProfile("Pat Doe", "pat.doe@example.com")
                .action("View account", () -> System.out.println("View account"))
                .action("Switch account", () -> System.out.println("Switch account"))
                .action("Sign out", () -> System.out.println("Sign out")));

        Ribbon ribbon = ribbon(shell, commands, accent);
        ribbon.setQuickAccessHook(bar::addToQuickAccess);
        if (store != null) {
            ribbon.setCustomizationStore(store);
        }
        if (spreadsheet) {
            sheet(shell);
        } else {
            page(shell);
        }
        Label status = new Label(shell, SWT.NONE);
        status.setText(spreadsheet ? "  Ready" : "  Page 1 of 3    848 words");
        status.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        themes.setThemeClass(status, "muted");
        windows.add(window);
        return window;
    }

    private void quickAccess(Window window) {
        TitleBar bar = window.bar;
        TitleBarItem autosave = new TitleBarItem(bar, SWT.CHECK);
        autosave.setId("autosave");
        autosave.setText("AutoSave");
        command(bar, SWT.PUSH, "save", "Save", TitleBarGlyph.SAVE, true);
        TitleBarItem undo = command(bar, SWT.DROP_DOWN, "undo", "Undo", TitleBarGlyph.UNDO, true);
        undo.addListener(SWT.Selection, e -> System.out.println(e.detail == SWT.ARROW ? "Undo history" : "Undo"));
        command(bar, SWT.PUSH, "redo", "Redo", TitleBarGlyph.REDO, true);
        command(bar, SWT.PUSH, "new", "New", TitleBarGlyph.NEW, false);
        command(bar, SWT.PUSH, "open", "Open", TitleBarGlyph.OPEN, false);
        command(bar, SWT.PUSH, "print", "Quick Print", TitleBarGlyph.PRINT, false);
        command(bar, SWT.PUSH, "email", "Email", TitleBarGlyph.EMAIL, false);
    }

    private static TitleBarItem command(TitleBar bar, int style, String id, String text, TitleBarGlyph glyph, boolean shown) {
        TitleBarItem item = new TitleBarItem(bar, style);
        item.setId(id);
        item.setText(text);
        item.setGlyph(glyph);
        item.setShown(shown);
        if (style == SWT.PUSH) {
            item.addListener(SWT.Selection, e -> System.out.println(text));
        }
        return item;
    }

    private static Menu documentMenu(Shell shell, String title) {
        Menu menu = new Menu(shell, SWT.POP_UP);
        for (String text : new String[] { "&Rename...", "Save a &Copy...", "Version &History", "", "Location: Documents" }) {
            if (text.isEmpty()) {
                new MenuItem(menu, SWT.SEPARATOR);
                continue;
            }
            MenuItem item = new MenuItem(menu, SWT.PUSH);
            item.setText(text);
            item.addListener(SWT.Selection, e -> System.out.println(title + ": " + text));
        }
        return menu;
    }

    Path storeFile(String name) {
        return preferencesDir.resolve("titlebar-demo-" + name + ".json");
    }

    /** Word's quick access toolbar: the same commands as its ribbon's, from the shell's registry. */
    private void commandQuickAccess(Window window, CommandRegistry commands) {
        TitleBar bar = window.bar;
        bar.addQuickAccess(commands.get("autosave"));
        glyph(bar.addQuickAccess(commands.get("save")), TitleBarGlyph.SAVE);
        glyph(bar.addQuickAccess(commands.get("undo")), TitleBarGlyph.UNDO);
        glyph(bar.addQuickAccess(commands.get("redo")), TitleBarGlyph.REDO);
        glyph(bar.addQuickAccess(commands.get("new"), false), TitleBarGlyph.NEW);
        glyph(bar.addQuickAccess(commands.get("open"), false), TitleBarGlyph.OPEN);
        glyph(bar.addQuickAccess(commands.get("print"), false), TitleBarGlyph.PRINT);
        glyph(bar.addQuickAccess(commands.get("email"), false), TitleBarGlyph.EMAIL);
    }

    private static void glyph(TitleBarItem item, TitleBarGlyph glyph) {
        item.setGlyph(glyph);
    }

    /**
     * The window's commands, shared by its ribbon and its quick access toolbar. The quick access
     * ones have no images, so the bar draws its own line glyphs for them.
     */
    private void defineCommands(CommandRegistry commands, Shell shell) {
        RGB blue = new RGB(0x2F, 0x6F, 0xD0);
        RGB slate = new RGB(0x50, 0x5A, 0x68);
        command(commands, "autosave", "AutoSave", null, null, "File").style(Command.Style.TOGGLE)
                .description("Save your changes as you go.");
        command(commands, "save", "&Save", null, null, "File").shortcut(SWT.MOD1 | 'S').description("Save this document.");
        command(commands, "undo", "&Undo", null, null, "Edit").style(Command.Style.SPLIT).shortcut(SWT.MOD1 | 'Z')
                .menu(CommandMenu.of(menu -> {
                    for (String step : new String[] { "Typing \"Time Required\"", "Bold", "Paste" }) {
                        MenuItem item = new MenuItem(menu, SWT.PUSH);
                        item.setText("Undo " + step);
                        item.addListener(SWT.Selection, e -> System.out.println("Undo " + step));
                    }
                }));
        command(commands, "redo", "&Redo", null, null, "Edit").shortcut(SWT.MOD1 | 'Y');
        command(commands, "new", "&New", null, null, "File").shortcut(SWT.MOD1 | 'N');
        command(commands, "open", "&Open", null, null, "File").shortcut(SWT.MOD1 | 'O');
        command(commands, "print", "Quick &Print", null, null, "File");
        command(commands, "email", "&Email", null, null, "File");
        command(commands, "file.close", "&Close", "X", slate, "File").onRun(e -> shell.close());
        command(commands, "home.paste", "&Paste", "P", slate, "Home").shortcut(SWT.MOD1 | 'V')
                .description("Add content from the clipboard.");
        command(commands, "home.cut", "Cu&t", "X", slate, "Home").shortcut(SWT.MOD1 | 'X');
        command(commands, "home.copy", "&Copy", "C", slate, "Home").shortcut(SWT.MOD1 | 'C');
        command(commands, "home.bold", "&Bold", "B", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'B');
        command(commands, "home.italic", "&Italic", "I", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'I');
        command(commands, "home.underline", "&Underline", "U", slate, "Home").style(Command.Style.TOGGLE)
                .shortcut(SWT.MOD1 | 'U');
        command(commands, "view.theme", "Switch &Theme", "T", blue, "View").description("Show the next bundled theme.")
                .onRun(e -> switchTheme());
    }

    private Command command(CommandRegistry commands, String id, String label, String letter, RGB color, String category) {
        Command command = new Command(id, label).category(category)
                .onRun(e -> System.out.println(Command.stripMnemonic(label) + (e.arrow() ? " (arrow)" : "")));
        if (letter != null) {
            command.images(icons.of(letter, color));
        }
        return commands.add(command);
    }

    /** A small ribbon: File (the backstage), Home and View, as in Office. */
    private Ribbon ribbon(Shell shell, CommandRegistry commands, RGB accent) {
        Ribbon ribbon = new Ribbon(shell, SWT.NONE, commands);
        ribbon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        ribbon.setAccentColor(accent);
        ribbon.setFileTab("File", backstage -> {
            backstage.addPage("info", "Info", null, page -> {
                Label text = new Label(page, SWT.WRAP);
                text.setText(shell.getText() + ": protect, inspect and manage versions of this document here.");
                text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            });
            backstage.addPage("new", "New", null, page -> new Label(page, SWT.NONE).setText("Blank document"));
            backstage.addPage("open", "Open", null, page -> new Label(page, SWT.NONE).setText("Recent documents"));
            backstage.addCommand("save");
            backstage.addPage("print", "Print", null, page -> new Label(page, SWT.NONE).setText("Printer: Default printer"));
            backstage.addCommand("file.close");
            backstage.addSeparator();
            backstage.addPage("account", "Account", null, page -> new Label(page, SWT.NONE).setText("Pat Doe"));
        });
        RibbonTab home = ribbon.addTab("home", "Home").setKeyTip("H");
        RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard").setImage(commands.get("home.paste").images());
        clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE);
        clipboard.add("home.cut");
        clipboard.add("home.copy");
        RibbonGroup font = home.addGroup("home.font", "Font").setImage(commands.get("home.bold").images());
        font.add("home.bold");
        font.add("home.italic");
        font.add("home.underline");
        RibbonTab view = ribbon.addTab("view", "View").setKeyTip("W");
        view.addGroup("view.themes", "Themes").setImage(commands.get("view.theme").images())
                .add("view.theme").sizes(Size.LARGE, Size.LARGE);
        return ribbon;
    }

    private void page(Shell shell) {
        StyledText text = new StyledText(shell, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        text.setMargins(120, 40, 120, 40);
        text.setText("""
                Time Required

                45 minutes each time you do this practice.

                How to Do It

                1. Identify someone with whom you'd like to become closer. It could be someone you know well \
                or someone you're just getting to know.

                2. Find a time when you and your partner have at least 20-45 minutes free and are able to meet.

                3. For the first third of the time you have set aside for the activity, take turns asking one \
                another the questions in Set I below.

                Set I

                1. Given the choice of anyone in the world, whom would you want as a dinner guest?
                """);
    }

    private void sheet(Shell shell) {
        Table table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        TableColumn rows = new TableColumn(table, SWT.RIGHT);
        rows.setWidth(44);
        for (char c = 'A'; c <= 'P'; c++) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(String.valueOf(c));
            column.setWidth(64);
        }
        for (int r = 1; r <= 40; r++) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(0, String.valueOf(r));
        }
    }

    private Image appIcon(RGB accent, char letter) {
        int size = 32;
        Image image = new Image(display, size, size);
        GC gc = new GC(image);
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(0, 0, size, size);
        gc.setBackground(new org.eclipse.swt.graphics.Color(display, accent));
        gc.fillRoundRectangle(2, 2, size - 4, size - 4, 8, 8);
        FontData data = display.getSystemFont().getFontData()[0];
        Font font = new Font(display, data.getName(), 15, SWT.BOLD);
        gc.setFont(font);
        gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
        Point extent = gc.textExtent(String.valueOf(letter));
        gc.drawText(String.valueOf(letter), (size - extent.x) / 2, (size - extent.y) / 2, true);
        gc.dispose();
        font.dispose();
        images.add(image);
        return image;
    }

    private void switchTheme() {
        int next = (available.indexOf(themes.getTheme()) + 1) % available.size();
        themes.setTheme(available.get(next));
    }

    /** The same bar from JFace: a {@link TitleBarWindow} whose quick access commands are actions. */
    private Window jfaceWindow() {
        org.eclipse.jface.action.Action save = action("save", "&Save@Ctrl+S", org.eclipse.jface.action.IAction.AS_PUSH_BUTTON);
        org.eclipse.jface.action.Action autosave = action("autosave", "AutoSave", org.eclipse.jface.action.IAction.AS_CHECK_BOX);
        org.eclipse.jface.action.Action undo = action("undo", "&Undo", org.eclipse.jface.action.IAction.AS_PUSH_BUTTON);
        org.eclipse.jface.action.Action redo = action("redo", "&Redo", org.eclipse.jface.action.IAction.AS_PUSH_BUTTON);
        org.eclipse.jface.action.Action present = action("present", "From &Beginning", org.eclipse.jface.action.IAction.AS_PUSH_BUTTON);
        Image icon = appIcon(POWERPOINT_ORANGE, 'P');
        com.zapthemes.jface.TitleBarWindow window = new com.zapthemes.jface.TitleBarWindow(null) {
            {
                addToolBar(SWT.FLAT | SWT.RIGHT);
                addStatusLine();
                addMenuBar();
                addQuickAccess(autosave);
                addQuickAccess(save);
                addQuickAccess(undo);
                addQuickAccess(redo);
                addQuickAccess(present, false);
            }

            @Override
            protected void createTitleBar(TitleBar bar) {
                bar.setAccent(POWERPOINT_ORANGE);
                bar.setSearchHint("Search");
                bar.setProfile(new TitleBarProfile("Pat Doe", "pat.doe@example.com")
                        .action("View account", () -> System.out.println("View account"))
                        .action("Sign out", () -> System.out.println("Sign out")));
            }

            @Override
            protected Path getTitleBarPreferences() {
                return preferencesDir.resolve("titlebar-demo-powerpoint.properties");
            }

            @Override
            protected org.eclipse.jface.action.MenuManager createMenuManager() {
                org.eclipse.jface.action.MenuManager menu = new org.eclipse.jface.action.MenuManager();
                menu.add(save);
                menu.add(present);
                return menu;
            }

            @Override
            protected org.eclipse.jface.action.ToolBarManager createToolBarManager(int style) {
                org.eclipse.jface.action.ToolBarManager manager = new org.eclipse.jface.action.ToolBarManager(style);
                manager.add(present);
                return manager;
            }

            @Override
            protected void configureShell(org.eclipse.swt.widgets.Shell shell) {
                super.configureShell(shell);
                shell.setText("Presentation1 - PowerPoint");
                shell.setImage(icon);
            }

            @Override
            protected org.eclipse.swt.graphics.Point getInitialSize() {
                return new org.eclipse.swt.graphics.Point(1000, 620);
            }

            @Override
            protected org.eclipse.swt.graphics.Point getInitialLocation(org.eclipse.swt.graphics.Point size) {
                return new org.eclipse.swt.graphics.Point(220, 200);
            }

            @Override
            protected Control createContents(org.eclipse.swt.widgets.Composite parent) {
                Label slide = new Label(parent, SWT.CENTER);
                slide.setText("Click to add title");
                themes.setThemeClass(slide, "strong");
                return slide;
            }
        };
        window.setBlockOnOpen(false);
        window.open();
        window.setStatus("Slide 1 of 1");
        TitleBar bar = window.getTitleBar();
        bar.getItem("save").setGlyph(TitleBarGlyph.SAVE);
        bar.getItem("undo").setGlyph(TitleBarGlyph.UNDO);
        bar.getItem("redo").setGlyph(TitleBarGlyph.REDO);
        return new Window("powerpoint", window.getShell(), bar, POWERPOINT_ORANGE);
    }

    private static org.eclipse.jface.action.Action action(String id, String text, int style) {
        org.eclipse.jface.action.Action action = new org.eclipse.jface.action.Action(text.replace('@', '	'), style) {
            @Override
            public void run() {
                System.out.println(id + (style == AS_CHECK_BOX ? " " + isChecked() : ""));
            }
        };
        action.setId(id);
        return action;
    }

    // ------------------------------------------------------------------ smoke test and screenshots

    private void screenshots(Path dir) {
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (Window window : windows) {
                window.shell.forceActive();
                DemoSupport.settle(display);
                capture(window.shell, dir, "titlebar-" + window.name + "-" + theme.id());
            }
        }
        // A narrow window: the search box collapses and the title shortens.
        Window word = windows.getFirst();
        Rectangle bounds = word.shell.getBounds();
        word.shell.setSize(620, bounds.height);
        word.shell.forceActive();
        themes.setTheme(available.getFirst());
        DemoSupport.settle(display);
        DemoSupport.screenshot(word.bar, dir, "titlebar-word-narrow");
        word.shell.setBounds(bounds);
        // The quick access toolbar on its own row below the bar.
        word.bar.setQuickAccessBelow(true);
        word.bar.getItem("print").setShown(true);
        DemoSupport.settle(display);
        capture(word.shell, dir, "titlebar-word-below");
        word.bar.resetCustomization();
        themes.setTheme(available.getFirst());
    }

    /**
     * Saves the shell's client area, composed from each child's {@link Control#print}: printing a
     * whole {@code NO_TRIM} shell on Windows draws its children offset.
     */
    private void capture(org.eclipse.swt.widgets.Shell shell, Path dir, String name) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Rectangle area = shell.getClientArea();
        Image image = new Image(display, area.width, area.height);
        GC gc = new GC(image);
        gc.setBackground(shell.getBackground());
        gc.fillRectangle(0, 0, area.width, area.height);
        for (Control child : shell.getChildren()) {
            Rectangle bounds = child.getBounds();
            if (!child.getVisible() || bounds.isEmpty()) {
                continue;
            }
            Image part = new Image(display, bounds.width, bounds.height);
            GC partGc = new GC(part);
            child.print(partGc);
            partGc.dispose();
            gc.drawImage(part, bounds.x, bounds.y);
            part.dispose();
        }
        gc.dispose();
        org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();
        loader.data = new org.eclipse.swt.graphics.ImageData[] { image.getImageData() };
        String file = dir.resolve(name + ".png").toString();
        loader.save(file, SWT.IMAGE_PNG);
        image.dispose();
        System.out.println("Saved " + file);
    }

    /** The windows as the screen shows them, frame and native caption parts included (needs a desktop). */
    private void screenCaptures(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (Window window : windows) {
                window.shell.forceActive();
                DemoSupport.settle(display);
                Rectangle bounds = window.shell.getBounds();
                Image image = new Image(display, bounds.width, bounds.height);
                GC gc = new GC(display);
                gc.copyArea(image, bounds.x, bounds.y);
                gc.dispose();
                org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();
                loader.data = new org.eclipse.swt.graphics.ImageData[] { image.getImageData() };
                String file = dir.resolve("titlebar-" + window.name + "-" + theme.id() + ".png").toString();
                loader.save(file, SWT.IMAGE_PNG);
                image.dispose();
                System.out.println("Saved " + file);
            }
        }
        themes.setTheme(available.getFirst());
    }

    private int smokeTest() {
        DemoSupport.settle(display);
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            DemoSupport.settle(display);
            for (Window window : windows) {
                checkBar(window, theme, problems);
                checked += check(window.shell, problems);
            }
        }
        Window word = windows.getFirst();
        word.shell.forceActive();
        DemoSupport.settle(display);
        org.eclipse.swt.widgets.Shell popup = word.bar.openProfile();
        // Checked at once: under Xvfb without a window manager, late focus events can close it.
        if (popup == null || popup.isDisposed() || !popup.isVisible()) {
            problems.add("the profile pop-up did not open");
        } else {
            checked += check(popup, problems);
            popup.close();
        }
        DemoSupport.settle(display);
        word.bar.getItem("print").setShown(true);
        if (word.bar.getItem("print").getBounds().isEmpty()) {
            problems.add("a command shown from the customize menu has no bounds");
        }
        word.bar.getItem("print").setShown(false);
        // Customization survives a restart: a second bar reads the first one's store, including a
        // ribbon command a user added through the ribbon's Add to Quick Access Toolbar.
        word.bar.getItem("redo").setShown(false);
        word.bar.setQuickAccessBelow(true);
        word.bar.addToQuickAccess(CommandRegistry.of(word.shell).get("home.bold"));
        Window again = window("word-again", "Again", WORD_BLUE, 'W', false);
        again.bar.setCustomizationStore(CustomizationStore.open(storeFile("word")));
        if (again.bar.getItem("redo").isShown() || !again.bar.isQuickAccessBelow()) {
            problems.add("the quick access customization did not survive through the store");
        }
        if (again.bar.getItem("home.bold") == null || !again.bar.getItem("home.bold").isShown()) {
            problems.add("a ribbon command added to the quick access toolbar did not come back from the store");
        }
        again.shell.dispose();
        windows.remove(again);
        word.bar.resetCustomization();
        themes.setTheme(available.getFirst());
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: title bars (" + word.bar.getFrameMode() + " frame) in " + available.size()
                    + " themes, " + checked + " widgets checked");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private void checkBar(Window window, Theme theme, List<String> problems) {
        TitleBar bar = window.bar;
        String where = window.name + " in " + theme.id() + ": ";
        List<TitleBar.Part> expected = new ArrayList<>(List.of(TitleBar.Part.TITLE, TitleBar.Part.SEARCH, TitleBar.Part.AVATAR,
                TitleBar.Part.CUSTOMIZE));
        // macOS keeps its traffic lights: the bar draws no window buttons or icon there.
        boolean macNative = bar.getFrameMode() == TitleBar.FrameMode.NATIVE && "cocoa".equals(SWT.getPlatform());
        if (bar.getFrameMode() != TitleBar.FrameMode.EMBEDDED && !macNative) {
            expected.add(TitleBar.Part.CLOSE);
            expected.add(TitleBar.Part.MAXIMIZE);
            expected.add(TitleBar.Part.MINIMIZE);
            expected.add(TitleBar.Part.SYSTEM_MENU);
        }
        for (TitleBar.Part part : expected) {
            Rectangle bounds = bar.getPartBounds(part);
            if (bounds == null || bar.getPart(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2) != part) {
                problems.add(where + part + " missing or not hit-testable");
            }
        }
        for (String id : List.of("autosave", "save", "undo", "redo")) {
            if (bar.getItem(id) == null || bar.getItem(id).getBounds().isEmpty()) {
                problems.add(where + "quick access item " + id + " not shown");
            }
        }
        RGB background = bar.getBackground().getRGB();
        boolean accented = themes.styleOf(bar).bool("useAccent").orElse(true) && !display.getHighContrast();
        RGB themeBar = themes.styleOf(bar).color("background").map(c -> c.rgb).orElse(null);
        RGB themeInactive = themes.styleOf(bar).color("inactiveBackground").map(c -> c.rgb).orElse(null);
        if (accented && background.equals(themeBar) && !background.equals(window.accent)) {
            problems.add(where + "the accent " + window.accent + " was not applied: " + background);
        }
        if (!accented && !display.getHighContrast() && !background.equals(themeBar) && !background.equals(themeInactive)) {
            problems.add(where + "background " + background + ", theme says " + themeBar);
        }
    }

    private int check(Widget widget, List<String> problems) {
        int checked = 1;
        if (widget.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(widget + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (widget instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                checked += check(child, problems);
            }
        }
        if (widget instanceof org.eclipse.swt.widgets.Table table) {
            for (org.eclipse.swt.widgets.TableItem item : table.getItems()) {
                checked += check(item, problems);
            }
        }
        return checked;
    }
}
