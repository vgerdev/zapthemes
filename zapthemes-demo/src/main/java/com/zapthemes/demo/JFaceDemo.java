package com.zapthemes.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.notifications.NotificationPopup;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.FocusCellOwnerDrawHighlighter;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerFocusCellManager;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.editor.ThemeWizard;
import com.zapthemes.themes.BundledThemes;

/**
 * A JFace {@link ApplicationWindow} on the ZapThemes JFace fork: menu bar, tool bar, status line,
 * table and tree viewers, and the common JFace dialogs, with live theme switching and zoom (Ctrl +
 * mouse wheel, Ctrl+= / Ctrl+- / Ctrl+0, or the View menu; the tool bar shows the zoom). The app
 * code is plain JFace; its widgets are proxies because the fork creates them.
 *
 * <p>{@code --smoke} opens every dialog, a tooltip and a field-decoration hover, checks that each
 * control is tagged and has the theme's background in every bundled theme, zooms in and checks
 * that the window's and a dialog's fonts grew and the widgets still pass, and exits non-zero on
 * failure. {@code --screenshots <dir>} saves the window (focus cell and a status-line error shown),
 * the dialogs and the tooltip as PNG. {@code --screen-captures <dir>} also saves what the screen
 * shows at each step, through macOS {@code screencapture}: Control.print leaves Cocoa dialogs blank
 * and cannot capture hovers.
 */
public final class JFaceDemo extends ApplicationWindow {

    private record Row(String widget, String created) {
    }

    private static final List<Row> ROWS = List.of(
            new Row("Shell", "Window.createShell"),
            new Row("Menu / MenuItem", "MenuManager"),
            new Row("ToolBar / ToolItem", "ToolBarManager"),
            new Row("Button", "WidgetFactory.button"),
            new Row("Table / TableItem", "TableViewer"),
            new Row("Tree / TreeItem", "TreeViewer"));

    private final ThemeManager themes;
    private final List<Theme> available;
    private int current;
    private TableViewer table;
    private Path screenCaptures;

    private JFaceDemo(ThemeManager themes, List<Theme> available) {
        super(null);
        this.themes = themes;
        this.available = available;
        addMenuBar();
        addToolBar(SWT.FLAT | SWT.RIGHT);
        addStatusLine();
        themes.addZoomListener((previous, zoom) -> {
            zoomReset.setText(percent(zoom));
            setStatus("Zoom: " + percent(zoom) + "  (Ctrl + mouse wheel, Ctrl+= / Ctrl+- / Ctrl+0)");
        });
    }

    public static void main(String[] args) throws Exception {
        List<String> options = List.of(args);
        Display display = new Display();
        ThemeManager themes = ThemeManager.get(display);
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        List<Theme> available = BundledThemes.IDS.stream().map(registry::resolve).toList();
        themes.setTheme(available.getFirst());

        JFaceDemo demo = new JFaceDemo(themes, available);
        int shots = options.indexOf("--screenshots");
        int captures = options.indexOf("--screen-captures");
        demo.screenCaptures = captures >= 0 ? Path.of(options.get(captures + 1)) : null;
        if (options.contains("--smoke") || shots >= 0 || captures >= 0) {
            demo.setBlockOnOpen(false);
            demo.open();
            int exit = demo.verify(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            demo.close();
            display.dispose();
            System.exit(exit);
        }
        demo.setBlockOnOpen(true);
        demo.open();
        display.dispose();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ZapThemes — JFace ApplicationWindow demo");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(900, 600);
    }

    @Override
    protected void initializeBounds() {
        // Upstream JFace quirk on Windows (official jar too): setMenuBar sends WM_SIZE before the
        // window opens, so Window thinks the app sized the shell and skips getInitialSize().
        resizeHasOccurred = false;
        super.initializeBounds();
    }

    // ---------------------------------------------------------------- actions

    private final Action switchTheme = action("Switch &theme", this::nextTheme);

    private void nextTheme() {
        current = (current + 1) % available.size();
        themes.setTheme(available.get(current));
        setStatus("Theme: " + available.get(current).name());
    }

    private final Action zoomIn = action("Zoom &In", () -> themes().zoomIn());
    private final Action zoomOut = action("Zoom &Out", () -> themes().zoomOut());
    /** Shows the zoom in the tool bar; resets it when clicked. */
    private final Action zoomReset = action("100 %", () -> themes().resetZoom());

    /** For the actions above: field initializers run before the constructor sets {@code themes}. */
    private ThemeManager themes() {
        return themes;
    }

    private static String percent(double zoom) {
        return Math.round(zoom * 100) + " %";
    }

    private final Map<String, Supplier<Window>> dialogs = new LinkedHashMap<>();
    {
        dialogs.put("Message", () -> new MessageDialog(getShell(), "Message", null,
                "MessageDialog uses the theme too.", MessageDialog.INFORMATION, 0, "OK", "Cancel"));
        dialogs.put("Input", () -> new InputDialog(getShell(), "Input", "Your name:", "ZapThemes", null));
        dialogs.put("TitleArea", () -> new DemoTitleAreaDialog(getShell()));
        dialogs.put("Wizard", () -> new WizardDialog(getShell(), new DemoWizard()));
        dialogs.put("Preferences", () -> preferenceDialog(getShell()));
        dialogs.put("Popup", () -> new DemoPopup(getShell()));
        dialogs.put("Notification", () -> NotificationPopup.forShell(getShell()).title("ZapThemes", true)
                .text("NotificationPopup uses the theme's notification and popup classes.").build());
        dialogs.put("Theme Wizard", this::themeWizard);
    }

    /** The theme editor (zapthemes-editor); Finish saves the new theme and makes it the window's. */
    private Window themeWizard() {
        ThemeWizard wizard = new ThemeWizard(available);
        WizardDialog dialog = new WizardDialog(getShell(), wizard) {
            @Override
            protected void finishPressed() {
                super.finishPressed();
                if (wizard.result() != null) {
                    themes.setTheme(wizard.result());
                    setStatus("Theme: " + wizard.result().name() + ", saved in " + wizard.savedFile());
                }
            }
        };
        dialog.setPageSize(ThemeWizard.PAGE_SIZE);
        return dialog;
    }

    private static Action action(String text, Runnable run) {
        return new Action(text) {
            @Override
            public void run() {
                run.run();
            }
        };
    }

    @Override
    protected MenuManager createMenuManager() {
        MenuManager bar = new MenuManager();
        MenuManager file = new MenuManager("&File");
        file.add(switchTheme);
        file.add(new Separator());
        file.add(action("E&xit", this::close));
        MenuManager open = new MenuManager("&Dialogs");
        dialogs.forEach((name, dialog) -> open.add(action(name + "...", () -> dialog.get().open())));
        // No accelerator text: ThemeManager handles the zoom keys, a menu accelerator would zoom twice.
        MenuManager view = new MenuManager("&View");
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(action("&Reset Zoom", () -> themes.resetZoom()));
        bar.add(file);
        bar.add(view);
        bar.add(open);
        return bar;
    }

    @Override
    protected ToolBarManager createToolBarManager(int style) {
        ToolBarManager toolBar = new ToolBarManager(style);
        toolBar.add(switchTheme);
        toolBar.add(new Separator());
        toolBar.add(action("A−", () -> themes.zoomOut()));
        toolBar.add(zoomReset);
        toolBar.add(action("A+", () -> themes.zoomIn()));
        toolBar.add(new Separator());
        dialogs.forEach((name, dialog) -> toolBar.add(action(name, () -> dialog.get().open())));
        return toolBar;
    }

    // ---------------------------------------------------------------- contents

    @Override
    protected Control createContents(Composite parent) {
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);

        table = new TableViewer(sash, SWT.BORDER | SWT.FULL_SELECTION);
        table.getTable().setHeaderVisible(true);
        table.getTable().setLinesVisible(true);
        column(table, "Widget", 160, Row::widget);
        column(table, "Created by", 200, Row::created);
        table.setContentProvider(ArrayContentProvider.getInstance());
        table.setInput(ROWS);
        // The status line exists by now (StatusLineManager drops messages set before that).
        setStatus("Every widget here was created by JFace; the fork made them ZapThemes proxies.");
        // Arrow keys move a focus cell; the theme paints it (focusCellBackground).
        new TableViewerFocusCellManager(table, new FocusCellOwnerDrawHighlighter(table));

        TreeViewer tree = new TreeViewer(sash, SWT.BORDER);
        tree.setContentProvider(new ITreeContentProvider() {
            @Override
            public Object[] getElements(Object input) {
                return new Object[] { "org.eclipse.jface" };
            }

            @Override
            public Object[] getChildren(Object parent) {
                return parent.equals("org.eclipse.jface")
                        ? new Object[] { "action", "dialogs", "viewers", "window", "wizard" } : new Object[0];
            }

            @Override
            public Object getParent(Object element) {
                return null;
            }

            @Override
            public boolean hasChildren(Object element) {
                return getChildren(element).length > 0;
            }
        });
        tree.setLabelProvider(new LabelProvider());
        tree.setInput("root");
        tree.expandAll();
        sash.setWeights(3, 2);
        return sash;
    }

    private interface Text {
        String of(Row row);
    }

    private static void column(TableViewer viewer, String title, int width, Text text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return text.of((Row) element);
            }
        });
    }

    // ---------------------------------------------------------------- dialogs

    /** An info popup (no focus), as hovers and quick views use. */
    private static final class DemoPopup extends PopupDialog {
        DemoPopup(Shell parent) {
            super(parent, PopupDialog.INFOPOPUP_SHELLSTYLE, false, false, false, false, false, "PopupDialog",
                    "Info text in the theme's muted color");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            return WidgetFactory.label(SWT.WRAP).text("PopupDialog content uses the theme's popup colors.").create(parent);
        }

        @Override
        protected Point getDefaultSize() {
            return new Point(360, 140);
        }
    }

    private static final class DemoTitleAreaDialog extends TitleAreaDialog {
        DemoTitleAreaDialog(Shell parent) {
            super(parent);
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite area = (Composite) super.createDialogArea(parent);
            setTitle("TitleAreaDialog");
            setMessage("The banner, message and fields follow the theme.");
            Composite body = WidgetFactory.composite(SWT.NONE).layout(GridLayoutFactory.swtDefaults().numColumns(2).create())
                    .layoutData(GridDataFactory.fillDefaults().grab(true, true).create()).create(area);
            WidgetFactory.label(SWT.NONE).text("Project:").create(body);
            WidgetFactory.text(SWT.BORDER).text("zapthemes").layoutData(GridDataFactory.fillDefaults().grab(true, false).create()).create(body);
            return area;
        }
    }

    private static final class DemoWizard extends Wizard {
        DemoWizard() {
            setWindowTitle("Wizard");
        }

        @Override
        public void addPages() {
            addPage(new WizardPage("first", "First page", null) {
                @Override
                public void createControl(Composite parent) {
                    Composite page = WidgetFactory.composite(SWT.NONE).layout(GridLayoutFactory.swtDefaults().create()).create(parent);
                    WidgetFactory.label(SWT.NONE).text("WizardDialog pages, buttons and progress area are themed.").create(page);
                    WidgetFactory.button(SWT.CHECK).text("Remember me").create(page);
                    setControl(page);
                }
            });
            addPage(new WizardPage("second", "Second page", null) {
                @Override
                public void createControl(Composite parent) {
                    setControl(WidgetFactory.text(SWT.BORDER | SWT.MULTI).text("Second page").create(parent));
                }
            });
        }

        @Override
        public boolean performFinish() {
            return true;
        }
    }

    private static PreferenceDialog preferenceDialog(Shell parent) {
        PreferenceStore store = new PreferenceStore();
        store.setDefault("name", "ZapThemes");
        store.setDefault("theme", "light");
        PreferenceManager manager = new PreferenceManager();
        manager.addToRoot(new PreferenceNode("general", new FieldEditorPreferencePage("General", FieldEditorPreferencePage.GRID) {
            @Override
            protected void createFieldEditors() {
                addField(new StringFieldEditor("name", "Name:", getFieldEditorParent()));
                addField(new BooleanFieldEditor("live", "Live theme reload", getFieldEditorParent()));
                addField(new RadioGroupFieldEditor("theme", "Theme", 1,
                        new String[][] { { "Light", "light" }, { "Dark", "dark" } }, getFieldEditorParent(), true));
            }
        }));
        PreferenceDialog dialog = new PreferenceDialog(parent, manager);
        dialog.setPreferenceStore(store);
        return dialog;
    }

    // ---------------------------------------------------------------- smoke test

    /** Checks the window and every dialog in every theme; returns the process exit code. */
    private int verify(Path screenshots) throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (int i = 0; i < available.size(); i++) {
            Theme theme = available.get(i);
            current = i;
            themes.setTheme(theme);
            table.getTable().setSelection(1);
            table.getTable().setFocus();
            getStatusLineManager().setErrorMessage("StatusLine errors use the theme's error color.");
            dispatch();
            checked += check("window", getShell(), problems);
            screenshot(getShell(), screenshots, "jface-window-" + theme.id());
            capture(getShell(), "jface-window-" + theme.id());
            getStatusLineManager().setErrorMessage(null);
            checked += hovers(theme, screenshots, problems);
            for (var entry : dialogs.entrySet()) {
                Window dialog = entry.getValue().get();
                dialog.setBlockOnOpen(false);
                dialog.open();
                dispatch();
                checked += check(entry.getKey(), dialog.getShell(), problems);
                screenshot(dialog.getShell(), screenshots, "jface-" + entry.getKey().toLowerCase() + "-" + theme.id());
                capture(dialog.getShell(), "jface-" + entry.getKey().toLowerCase() + "-" + theme.id());
                dialog.close();
            }
        }
        checked += verifyZoom(screenshots, problems);
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls in the window and " + dialogs.size()
                    + " dialogs tagged and themed, in " + available.size() + " themes and zoomed");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /**
     * Zooms in three steps (to 150 %): the table's font, a dialog's font and JFace's dialog font must
     * grow, the first column must be 1.5 times as wide, the widgets must still pass {@link #check}, the
     * tool bar must show the zoom, and a reset must bring the fonts and the column back.
     */
    private int verifyZoom(Path screenshots, List<String> problems) throws Exception {
        Control control = table.getControl();
        int before = control.getFont().getFontData()[0].getHeight();
        int dialogFontBefore = JFaceResources.getDialogFont().getFontData()[0].getHeight();
        int columnBefore = table.getTable().getColumn(0).getWidth();
        for (int i = 0; i < 3; i++) {
            themes.zoomIn();
        }
        dispatch();
        int checked = 0;
        int zoomed = control.getFont().getFontData()[0].getHeight();
        if (themes.getZoom() != 1.5 || zoomed <= before) {
            problems.add("zoom: at " + themes.getZoom() + " the table font is " + zoomed + " pt, was " + before);
        }
        if (JFaceResources.getDialogFont().getFontData()[0].getHeight() <= dialogFontBefore) {
            problems.add("zoom: JFace's dialog font did not grow");
        }
        int column = table.getTable().getColumn(0).getWidth();
        if (column != Math.round(columnBefore * 1.5)) {
            problems.add("zoom: at 150 % the first column is " + column + " px wide, was " + columnBefore);
        }
        if (!zoomReset.getText().equals("150 %")) {
            problems.add("zoom: the tool bar shows " + zoomReset.getText());
        }
        checked += check("zoomed window", getShell(), problems);
        screenshot(getShell(), screenshots, "jface-window-zoom-150");
        Window dialog = dialogs.get("TitleArea").get();
        dialog.setBlockOnOpen(false);
        dialog.open();
        dispatch();
        checked += check("zoomed TitleArea", dialog.getShell(), problems);
        screenshot(dialog.getShell(), screenshots, "jface-titlearea-zoom-150");
        int dialogFont = dialog.getShell().getFont().getFontData()[0].getHeight();
        if (dialogFont <= before) {
            problems.add("zoom: a dialog opened at 150 % has a " + dialogFont + " pt font");
        }
        dialog.close();
        Window message = dialogs.get("Message").get();
        message.setBlockOnOpen(false);
        message.open();
        dispatch();
        checked += check("zoomed Message", message.getShell(), problems);
        iconFits(message.getShell(), problems);
        message.close();
        themes.resetZoom();
        dispatch();
        int reset = control.getFont().getFontData()[0].getHeight();
        if (reset != before) {
            problems.add("zoom: after a reset the table font is " + reset + " pt, was " + before);
        }
        if (table.getTable().getColumn(0).getWidth() != columnBefore) {
            problems.add("zoom: after a reset the first column is " + table.getTable().getColumn(0).getWidth()
                    + " px wide, was " + columnBefore);
        }
        return checked;
    }

    /** The dialog's icon label is at least as large as its (zoomed) image, so the icon is not cut off. */
    private static void iconFits(Control control, List<String> problems) {
        if (control instanceof org.eclipse.swt.widgets.Label label && label.getImage() != null) {
            var image = label.getImage().getBounds();
            var size = label.getSize();
            if (size.x < image.width || size.y < image.height) {
                problems.add("zoom: a " + image.width + "x" + image.height + " icon in a " + size.x + "x" + size.y + " label");
            }
            // The 100 % image ZapThemes keeps for the label (24 px on GTK, 32 px on Windows).
            if (!(label.getData("zapthemes.originalImage") instanceof org.eclipse.swt.graphics.Image original)
                    || image.width < original.getBounds().width * 1.4) {
                problems.add("zoom: the dialog icon is " + image.width + " px wide at 150 %, not zoomed");
            }
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                iconFits(child, problems);
            }
        }
    }

    /**
     * A DefaultToolTip and a ControlDecoration hover on the table, checked like the dialogs. Only the
     * tooltip is saved: Control.print cannot capture the hover, a shaped shell that paints its text.
     */
    private int hovers(Theme theme, Path screenshots, List<String> problems) throws Exception {
        Control anchor = table.getControl();
        DefaultToolTip tip = new DefaultToolTip(anchor, ToolTip.RECREATE, true);
        tip.setText("DefaultToolTip uses the theme's info colors.");
        // Captured right away: without a real mouse over the anchor, hovers close on the next events.
        Shell tipShell = opened(() -> tip.show(new Point(20, 40)));
        int checked = check("ToolTip", tipShell, problems);
        screenshot(tipShell, screenshots, "jface-tooltip-" + theme.id());
        capture(tipShell, "jface-tooltip-" + theme.id());
        tip.hide();

        ControlDecoration decoration = new ControlDecoration(anchor, SWT.TOP | SWT.LEFT);
        decoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage());
        Shell hover = opened(() -> decoration.showHoverText("ControlDecoration hovers too."));
        checked += check("Decoration", hover, problems);
        capture(hover, "jface-decoration-" + theme.id());
        decoration.dispose();
        return checked;
    }

    /** The shell {@code open} creates. */
    private static Shell opened(Runnable open) {
        Display display = Display.getCurrent();
        Set<Shell> before = Set.of(display.getShells());
        open.run();
        for (Shell shell : display.getShells()) {
            if (!before.contains(shell)) {
                return shell;
            }
        }
        throw new IllegalStateException("nothing opened");
    }

    private int check(String where, Control control, List<String> problems) {
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(where + ": " + control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        themes.styleOf(control).color("background").ifPresent(expected -> {
            if (!control.getBackground().getRGB().equals(expected.rgb)) {
                problems.add(where + ": " + control.getClass().getName() + " background "
                        + control.getBackground().getRGB() + ", theme says " + expected.rgb);
            }
        });
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(where, child, problems);
            }
        }
        return count;
    }

    /** Runs pending Skin, layout and paint events; GTK restyles some parts (table headers) only after a frame. */
    private static void dispatch() {
        DemoSupport.settle(Display.getCurrent());
    }

    /**
     * With {@code --screen-captures}, saves the whole screen with {@code shell} painted, through
     * macOS {@code screencapture}; elsewhere it does nothing.
     */
    private void capture(Shell shell, String name) throws Exception {
        if (screenCaptures == null || !SWT.getPlatform().equals("cocoa")) {
            return;
        }
        Files.createDirectories(screenCaptures);
        // The window server shows new drawing only once the event loop has run.
        Display display = shell.getDisplay();
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
        if (shell.isDisposed() || !shell.isVisible()) {
            System.out.println("Not captured, " + name + " closed while the screen updated");
            return;
        }
        String file = screenCaptures.resolve(name + ".png").toString();
        int exit = new ProcessBuilder("screencapture", "-x", file).inheritIO().start().waitFor();
        if (exit != 0) {
            System.out.println("screencapture failed (" + exit + ") for " + file);
        }
    }

    /** Saves {@code shell}'s client area via {@link Control#print}, which needs no screen access. */
    private static void screenshot(Shell shell, Path dir, String name) throws Exception {
        if (dir == null) {
            return;
        }
        Files.createDirectories(dir);
        var area = shell.getClientArea();
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        loader.save(dir.resolve(name + ".png").toString(), SWT.IMAGE_PNG);
        image.dispose();
    }
}
