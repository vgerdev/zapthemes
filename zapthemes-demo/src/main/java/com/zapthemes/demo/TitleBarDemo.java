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
import org.eclipse.swt.layout.RowLayout;
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
import com.zapthemes.swt.widgets.ToolBar;
import com.zapthemes.swt.widgets.ToolItem;
import com.zapthemes.widgets.FlatButton;
import com.zapthemes.widgets.titlebar.TitleBar;
import com.zapthemes.widgets.titlebar.TitleBarGlyph;
import com.zapthemes.widgets.titlebar.TitleBarItem;
import com.zapthemes.widgets.titlebar.TitleBarProfile;

/**
 * Office-style title bars: a Word-like window (blue) and an Excel-like one (green), each with the
 * system menu, a quick access toolbar (AutoSave, Save, Undo, Redo and more in the customize menu),
 * the document title with its menu, a centered search box, an account avatar with a profile
 * pop-up, and the window buttons. Comments and Share sit on the row below, as in Office.
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
    private Path preferencesDir = Path.of(System.getProperty("user.home"), ".zapthemes");

    private record Window(String name, org.eclipse.swt.widgets.Shell shell, TitleBar bar, RGB accent) {
    }

    private TitleBarDemo(Display display) {
        this.display = display;
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
        quickAccess(window);
        // Users' changes to the quick access toolbar survive restarts.
        bar.setPreferences(preferencesDir.resolve("titlebar-demo-" + name + ".properties"));
        if (spreadsheet) {
            new TitleBarItem(bar, SWT.PUSH | SWT.TRAIL).setText("Upgrade your plan");
        }
        bar.setProfile(new TitleBarProfile("Pat Doe", "pat.doe@example.com")
                .action("View account", () -> System.out.println("View account"))
                .action("Switch account", () -> System.out.println("Switch account"))
                .action("Sign out", () -> System.out.println("Sign out")));

        ribbonRow(shell);
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

    /** The ribbon's tab row (the ribbon itself is out of scope), with Comments and Share at the right. */
    private void ribbonRow(Shell shell) {
        Composite row = new Composite(shell, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 10;
        layout.marginHeight = 4;
        row.setLayout(layout);

        Composite tabs = new Composite(row, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        RowLayout tabLayout = new RowLayout();
        tabLayout.spacing = 14;
        tabLayout.center = true;
        tabs.setLayout(tabLayout);
        for (String tab : new String[] { "File", "Home", "Insert", "Draw", "Layout", "References", "Review", "View", "Help" }) {
            Label label = new Label(tabs, SWT.NONE);
            label.setText(tab);
            if (tab.equals("Home")) {
                themes.setThemeClass(label, "strong");
            }
        }
        FlatButton comments = new FlatButton(row, SWT.PUSH);
        comments.setText("Comments");
        FlatButton share = new FlatButton(row, SWT.PUSH);
        share.setText("Share");
        themes.setThemeClass(share, "primary");
        share.addListener(SWT.Selection, e -> switchTheme());

        ToolBar ribbon = new ToolBar(shell, SWT.FLAT | SWT.RIGHT);
        ribbon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        for (String text : new String[] { "Paste", "Cut", "Copy", "|", "Bold", "Italic", "Underline", "|", "Switch theme" }) {
            if (text.equals("|")) {
                new ToolItem(ribbon, SWT.SEPARATOR);
                continue;
            }
            ToolItem item = new ToolItem(ribbon, SWT.PUSH);
            item.setText(text);
            if (text.equals("Switch theme")) {
                item.addListener(SWT.Selection, e -> switchTheme());
            }
        }
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
        // Customization survives a restart: a second bar reads the first one's preferences file.
        word.bar.getItem("redo").setShown(false);
        word.bar.setQuickAccessBelow(true);
        Window again = window("word-again", "Again", WORD_BLUE, 'W', false);
        again.bar.setPreferences(preferencesDir.resolve("titlebar-demo-word.properties"));
        if (again.bar.getItem("redo").isShown() || !again.bar.isQuickAccessBelow()) {
            problems.add("the quick access customization did not survive through the preferences file");
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
