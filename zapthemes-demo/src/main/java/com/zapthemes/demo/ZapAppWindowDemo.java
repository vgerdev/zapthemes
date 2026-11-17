package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.jface.ribbon.ActionCommands;
import com.zapthemes.jface.ribbon.ZapAppWindow;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.widgets.command.Images;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonTab;

/**
 * An ordinary JFace application window, written with {@code addMenuBar()}, {@code addToolBar()},
 * {@code createMenuManager()} and {@code createToolBarManager()} as for {@code ApplicationWindow},
 * with its base class changed to {@link ZapAppWindow}: the menus and tool bar become the ribbon by
 * themselves, the File menu the backstage, and the status line stays. Nothing here builds a ribbon.
 *
 * <p>{@code --theme <id>} starts in a bundled theme ({@code zap-dark}); View › Next Theme (Ctrl+T)
 * switches. {@code --smoke} switches through every bundled theme, checks the tabs, the backstage, an
 * accelerator and the status line, and exits. {@code --screenshots <dir>} saves the window in every
 * theme and the backstage.
 */
public final class ZapAppWindowDemo extends ZapAppWindow {

    private static final RGB BLUE = new RGB(0x2B, 0x57, 0x9A);
    private static final RGB GREEN = new RGB(0x21, 0x73, 0x46);
    private static final RGB ORANGE = new RGB(0xC4, 0x3E, 0x1C);

    private final DemoIcons icons;
    private final List<Theme> themes;
    private final List<String> ran = new ArrayList<>();
    private Text editor;

    private final IAction newNote;
    private final IAction open;
    private final IAction save;
    private final IAction saveAll;
    private final IAction cut;
    private final IAction copy;
    private final IAction paste;
    private final IAction find;
    private final IAction replace;
    private final IAction wrap;
    private final IAction zoomIn;
    private final IAction zoomOut;

    private ZapAppWindowDemo(DemoIcons icons, List<Theme> themes) {
        super(null);
        this.icons = icons;
        this.themes = themes;
        newNote = action("&New Note...@Ctrl+N", "N", BLUE, "Start an empty note.");
        open = action("&Open...@Ctrl+O", "O", BLUE, "Open a note from a file.");
        save = action("&Save@Ctrl+S", "S", BLUE, "Save the note.");
        saveAll = action("Save A&ll@Ctrl+Shift+S", null, null, "Save every open note.");
        cut = action("Cu&t@Ctrl+X", "X", GREEN, "Cut the selection to the clipboard.");
        copy = action("&Copy@Ctrl+C", "C", GREEN, "Copy the selection to the clipboard.");
        paste = action("&Paste@Ctrl+V", "P", GREEN, "Paste from the clipboard.");
        find = action("&Find...@Ctrl+F", "F", ORANGE, "Find text in the note.");
        replace = action("&Replace...@Ctrl+H", "R", ORANGE, "Replace text in the note.");
        wrap = new Action("&Word Wrap", IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                ran.add("Word Wrap");
                setStatus(isChecked() ? "Lines wrap" : "Lines don't wrap");
            }
        };
        wrap.setToolTipText("Wrap long lines at the window's edge.");
        zoomIn = action("Zoom &In@Ctrl+=", null, null, "Make the text larger.");
        zoomOut = action("Zoom &Out@Ctrl+-", null, null, "Make the text smaller.");
        ActionCommands.setLargeImage(paste, descriptor(icons.of("P", GREEN), 32));
        addMenuBar();
        addToolBar(SWT.FLAT | SWT.RIGHT);
        addStatusLine();
    }

    private IAction action(String text, String letter, RGB color, String description) {
        Action action = new Action(text) {
            @Override
            public void run() {
                String name = com.zapthemes.widgets.command.Command.stripMnemonic(text.substring(0, text.indexOf('@') < 0 ? text.length() : text.indexOf('@')));
                ran.add(name);
                setStatus("Ran " + name);
            }
        };
        action.setToolTipText(description);
        if (letter != null) {
            action.setImageDescriptor(descriptor(icons.of(letter, color), 16));
        }
        return action;
    }

    private static ImageDescriptor descriptor(Images images, int size) {
        return ImageDescriptor.createFromImage(images.image(size));
    }

    // ------------------------------------------------------------------ as for ApplicationWindow

    @Override
    protected MenuManager createMenuManager() {
        MenuManager bar = new MenuManager();
        MenuManager file = new MenuManager("&File", "file");
        file.add(newNote);
        file.add(open);
        MenuManager recent = new MenuManager("Open &Recent");
        recent.add(action("notes.txt", null, null, null));
        recent.add(action("todo.txt", null, null, null));
        recent.add(action("ideas.md", null, null, null));
        file.add(recent);
        file.add(new Separator());
        file.add(save);
        file.add(saveAll);
        file.add(new Separator());
        file.add(new Action("E&xit") {
            @Override
            public void run() {
                close();
            }
        });

        MenuManager edit = new MenuManager("&Edit", "edit");
        edit.add(new Separator("clipboard"));
        edit.add(paste);
        edit.add(cut);
        edit.add(copy);
        edit.add(new Separator("editing"));
        edit.add(find);
        edit.add(replace);

        MenuManager view = new MenuManager("&View", "view");
        view.add(new Separator("zoom"));
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(new Separator("show"));
        view.add(wrap);
        view.add(new Separator("themes"));
        view.add(new Action("Next &Theme@Ctrl+T") {
            @Override
            public void run() {
                ThemeManager manager = ThemeManager.get(getShell().getDisplay());
                int current = themes.indexOf(manager.getTheme());
                Theme next = themes.get((current + 1) % themes.size());
                manager.setTheme(next);
                setStatus("Theme: " + next.id());
            }
        });

        MenuManager help = new MenuManager("&Help", "help");
        help.add(action("&About the Demo", null, null, null));

        bar.add(file);
        bar.add(edit);
        bar.add(view);
        bar.add(help);
        return bar;
    }

    @Override
    protected ToolBarManager createToolBarManager(int style) {
        ToolBarManager toolBar = new ToolBarManager(style);
        toolBar.add(new Separator("notes"));
        toolBar.add(newNote);
        toolBar.add(open);
        toolBar.add(save);
        toolBar.add(new Separator("clipboard"));
        toolBar.add(paste);
        toolBar.add(cut);
        toolBar.add(copy);
        return toolBar;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ZapAppWindow demo");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(1100, 700);
    }

    @Override
    protected void initializeBounds() {
        resizeHasOccurred = false;
        super.initializeBounds();
    }

    @Override
    protected Control createContents(Composite parent) {
        editor = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        editor.setText("""
                This window is written as a plain JFace ApplicationWindow: addMenuBar(), addToolBar(),
                addStatusLine(), createMenuManager() and createToolBarManager().

                Its base class is ZapAppWindow, so the tool bar became the Home tab, the Edit, View and Help
                menus became tabs, and the File menu became the backstage. Every accelerator still works
                (Ctrl+S, Ctrl+Shift+S, Ctrl+F) and Alt+E opens the Edit tab as it opened the Edit menu.
                """);
        setStatus("Ready");
        return editor;
    }

    // ------------------------------------------------------------------ run, smoke test, screenshots

    public static void main(String[] args) {
        List<String> options = List.of(args);
        boolean smoke = options.contains("--smoke");
        int shots = options.indexOf("--screenshots");
        Display display = new Display();
        List<Theme> themes = DemoSupport.bundledThemes();
        int named = options.indexOf("--theme");
        String wanted = named >= 0 && named + 1 < options.size() ? options.get(named + 1) : null;
        Theme first = themes.stream().filter(t -> t.id().equals(wanted)).findFirst().orElse(themes.getFirst());
        if (wanted != null && !first.id().equals(wanted)) {
            System.err.println("No bundled theme " + wanted + "; using " + first.id() + ". Themes: "
                    + themes.stream().map(Theme::id).toList());
        }
        ThemeManager.get(display).setTheme(first);
        System.out.println("Theme: " + first.id());
        DemoIcons icons = new DemoIcons(display);
        ZapAppWindowDemo demo = new ZapAppWindowDemo(icons, themes);
        demo.setBlockOnOpen(false);
        demo.open();
        DemoSupport.settle(display);
        if (shots >= 0) {
            demo.screenshots(themes, Path.of(options.get(shots + 1)));
        }
        int exitCode = smoke ? demo.smokeTest(themes) : 0;
        if (smoke) {
            demo.close();
        }
        while (demo.getShell() != null && !demo.getShell().isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        icons.dispose();
        display.dispose();
        if (smoke) {
            System.exit(exitCode);
        }
    }

    private void screenshots(List<Theme> themes, Path dir) {
        Display display = getShell().getDisplay();
        for (Theme theme : themes) {
            ThemeManager.get(display).setTheme(theme);
            DemoSupport.settle(display);
            DemoSupport.screenshotByChildren(getShell(), dir, "zapappwindow-" + theme.id());
        }
        ThemeManager.get(display).setTheme(themes.getFirst());
        getRibbon().openBackstage();
        getRibbon().getBackstage().select(getRibbon().getBackstage().pageIds().getFirst());
        DemoSupport.settle(display);
        DemoSupport.screenshotByChildren(getShell(), dir, "zapappwindow-backstage-" + themes.getFirst().id());
        getRibbon().getBackstage().close();
        DemoSupport.settle(display);
    }

    private int smokeTest(List<Theme> themes) {
        Display display = getShell().getDisplay();
        List<String> failures = new ArrayList<>();
        Ribbon ribbon = getRibbon();
        List<String> tabs = ribbon.tabs().stream().map(RibbonTab::label).toList();
        check(failures, tabs.equals(List.of("Home", "Edit", "View", "Help")), "tabs " + tabs);
        check(failures, getShell().getMenuBar() == null, "no native menu bar");
        org.eclipse.swt.graphics.Rectangle area = getShell().getClientArea();
        org.eclipse.swt.graphics.Rectangle title = getTitleBar().getBounds();
        org.eclipse.swt.graphics.Rectangle bar = ribbon.getBounds();
        org.eclipse.swt.graphics.Rectangle text = editor.getBounds();
        org.eclipse.swt.graphics.Rectangle status = getStatusLineManager().getControl().getBounds();
        check(failures, title.y == area.y && bar.y == title.y + title.height && text.y >= bar.y + bar.height
                && status.y >= text.y + text.height && status.y + status.height == area.y + area.height,
                "title bar, ribbon, contents, status line: " + title + " " + bar + " " + text + " " + status);
        for (Theme theme : themes) {
            ThemeManager.get(display).setTheme(theme);
            DemoSupport.settle(display);
            check(failures, ribbon.getBounds().height > 0, "ribbon shown in " + theme.id());
        }
        ThemeManager.get(display).setTheme(themes.getFirst());
        DemoSupport.settle(display);

        Event key = new Event();
        key.keyCode = 's';
        key.stateMask = SWT.CTRL | SWT.SHIFT;
        key.doit = true;
        editor.notifyListeners(SWT.KeyDown, key);
        check(failures, ran.contains("Save All"), "Ctrl+Shift+S ran Save All: " + ran);
        check(failures, "Ran Save All".equals(statusText()), "status line: " + statusText());

        ribbon.openBackstage();
        DemoSupport.settle(display);
        List<String> entries = ribbon.getBackstage().entryIds();
        check(failures, entries.size() >= 6 && entries.getLast().endsWith("exit"), "backstage " + entries);
        ribbon.getBackstage().close();
        DemoSupport.settle(display);

        // KeyTips are off by default on macOS, which has no Alt convention.
        if (ribbon.isKeyTipsEnabled()) {
            Event chord = new Event();
            chord.keyCode = 'e';
            chord.stateMask = SWT.ALT;
            chord.doit = true;
            editor.notifyListeners(SWT.KeyDown, chord);
            DemoSupport.settle(display);
            check(failures, "Edit".equals(ribbon.getSelection().label()), "Alt+E selects Edit: " + ribbon.getSelection().label());
            Map<String, String> tips = ribbon.visibleKeyTips();
            check(failures, !tips.isEmpty(), "Edit's KeyTips show");
            ribbon.hideKeyTips();
        }

        failures.forEach(f -> System.err.println("FAILED: " + f));
        System.out.println(failures.isEmpty() ? "ZapAppWindow demo smoke test passed" : failures.size() + " failures");
        return failures.isEmpty() ? 0 : 1;
    }

    private String statusText() {
        for (Control child : ((Composite) getStatusLineManager().getControl()).getChildren()) {
            if (child instanceof org.eclipse.swt.custom.CLabel label) {
                return label.getText();
            }
        }
        return null;
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }
}
