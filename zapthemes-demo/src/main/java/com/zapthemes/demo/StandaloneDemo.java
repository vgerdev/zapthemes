package com.zapthemes.demo;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.CCombo;
import com.zapthemes.swt.custom.CLabel;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.custom.CTabItem;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.swt.custom.ScrolledComposite;
import com.zapthemes.swt.custom.StyledText;
import com.zapthemes.swt.custom.ViewForm;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.DateTime;
import com.zapthemes.swt.widgets.ExpandBar;
import com.zapthemes.swt.widgets.ExpandItem;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Link;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.swt.widgets.ProgressBar;
import com.zapthemes.swt.widgets.Scale;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Slider;
import com.zapthemes.swt.widgets.Spinner;
import com.zapthemes.swt.widgets.TabFolder;
import com.zapthemes.swt.widgets.TabItem;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.ToolBar;
import com.zapthemes.swt.widgets.ToolItem;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeColumn;
import com.zapthemes.swt.widgets.TreeItem;
import com.zapthemes.themes.BundledThemes;

/**
 * A standalone {@link Display}/{@link Shell} app (no JFace) built only from ZapThemes proxies, with
 * live switching between the bundled themes. {@code --smoke} builds the UI, switches to the dark
 * theme, checks that every widget is tagged and has the theme's background, and exits (non-zero on
 * failure). {@code --screenshots <dir>} additionally saves the window in every theme as PNG.
 *
 * <p>Proxies are imported for {@code new}; parameters and fields that accept any widget use the
 * original SWT types (a proxy {@code Shell} is an SWT {@code Composite}, not a proxy one).
 */
public final class StandaloneDemo {

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = new ArrayList<>();
    private Shell shell;

    private StandaloneDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        available.add(registry.resolve("zap-light"));
        available.add(registry.resolve("zap-dark"));
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        boolean smoke = options.contains("--smoke");
        int shots = options.indexOf("--screenshots");
        Display display = new Display();
        StandaloneDemo demo = new StandaloneDemo(display);
        Shell shell = demo.open();
        if (shots >= 0) {
            demo.screenshots(java.nio.file.Path.of(options.get(shots + 1)));
        }

        int exitCode = smoke ? demo.smokeTest() : 0;
        if (smoke) {
            shell.dispose();
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
        if (smoke) {
            System.exit(exitCode);
        }
    }

    private Shell open() {
        shell = new Shell(display);
        shell.setText("ZapThemes — standalone SWT demo");
        shell.setLayout(new GridLayout(1, false));
        createMenuBar();
        createThemeBar(shell);

        CTabFolder tabs = new CTabFolder(shell, SWT.BORDER);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tab(tabs, "Basics", this::createBasics);
        tab(tabs, "Lists && Tables", this::createLists);
        tab(tabs, "Text", this::createText);
        tab(tabs, "Containers", this::createContainers);
        tabs.setSelection(0);

        shell.setSize(820, 600);
        shell.open();
        return shell;
    }

    private void createMenuBar() {
        Menu bar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(bar);
        MenuItem file = new MenuItem(bar, SWT.CASCADE);
        file.setText("&File");
        Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
        file.setMenu(fileMenu);
        MenuItem exit = new MenuItem(fileMenu, SWT.PUSH);
        exit.setText("E&xit");
        exit.addListener(SWT.Selection, e -> shell.close());
    }

    private void createThemeBar(org.eclipse.swt.widgets.Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayout(new GridLayout(3, false));
        new Label(bar, SWT.NONE).setText("Theme:");
        Combo picker = new Combo(bar, SWT.READ_ONLY);
        available.forEach(theme -> picker.add(theme.name()));
        picker.select(0);
        picker.addListener(SWT.Selection, e -> themes.setTheme(available.get(picker.getSelectionIndex())));
        Button toggle = new Button(bar, SWT.PUSH);
        toggle.setText("Switch theme");
        themes.setThemeClass(toggle, "primary");
        toggle.addListener(SWT.Selection, e -> {
            picker.select((picker.getSelectionIndex() + 1) % available.size());
            themes.setTheme(available.get(picker.getSelectionIndex()));
        });
    }

    private interface Page {
        Control create(CTabFolder parent);
    }

    private static void tab(CTabFolder tabs, String title, Page page) {
        CTabItem item = new CTabItem(tabs, SWT.NONE);
        item.setText(title);
        item.setControl(page.create(tabs));
    }

    private Control createBasics(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(2, false));

        row(page, "Label");
        Composite labels = flow(page);
        new Label(labels, SWT.NONE).setText("Plain label");
        Label muted = new Label(labels, SWT.NONE);
        muted.setText("Muted (THEME_CLASS=muted)");
        themes.setThemeClass(muted, "muted");
        new CLabel(labels, SWT.NONE).setText("CLabel");
        new Link(labels, SWT.NONE).setText("A <a>link</a> in text");

        row(page, "Buttons");
        Composite buttons = flow(page);
        new Button(buttons, SWT.PUSH).setText("Push");
        Button primary = new Button(buttons, SWT.PUSH);
        primary.setText("Primary");
        themes.setThemeClass(primary, "primary strong");
        new Button(buttons, SWT.CHECK).setText("Check");
        new Button(buttons, SWT.RADIO).setText("Radio");
        new Button(buttons, SWT.TOGGLE).setText("Toggle");
        new Button(buttons, SWT.ARROW | SWT.RIGHT);
        Button disabled = new Button(buttons, SWT.PUSH);
        disabled.setText("Disabled");
        disabled.setEnabled(false);

        row(page, "Input");
        Composite inputs = flow(page);
        new Text(inputs, SWT.BORDER).setText("Text");
        Combo combo = new Combo(inputs, SWT.DROP_DOWN);
        combo.setItems("Combo", "Two", "Three");
        combo.select(0);
        CCombo ccombo = new CCombo(inputs, SWT.BORDER);
        ccombo.setItems(new String[] { "CCombo", "Two" });
        ccombo.select(0);
        new Spinner(inputs, SWT.BORDER).setSelection(42);
        new DateTime(inputs, SWT.DATE | SWT.DROP_DOWN);

        row(page, "Ranges");
        Composite ranges = flow(page);
        new Scale(ranges, SWT.HORIZONTAL).setSelection(30);
        new Slider(ranges, SWT.HORIZONTAL).setSelection(50);
        new ProgressBar(ranges, SWT.HORIZONTAL).setSelection(60);
        return page;
    }

    private Control createLists(CTabFolder parent) {
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        com.zapthemes.swt.widgets.List list = new com.zapthemes.swt.widgets.List(sash, SWT.BORDER | SWT.MULTI);
        list.setItems("List", "of", "items");

        Table table = new Table(sash, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        for (String title : new String[] { "Widget", "Package" }) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(title);
            column.setWidth(120);
        }
        for (String[] row : new String[][] { { "Button", "widgets" }, { "StyledText", "custom" }, { "Table", "widgets" } }) {
            new TableItem(table, SWT.NONE).setText(row);
        }

        Tree tree = new Tree(sash, SWT.BORDER);
        tree.setHeaderVisible(true);
        new TreeColumn(tree, SWT.NONE).setText("Tree");
        tree.getColumn(0).setWidth(160);
        TreeItem root = new TreeItem(tree, SWT.NONE);
        root.setText("org.eclipse.swt");
        for (String child : new String[] { "widgets", "custom", "browser" }) {
            new TreeItem(root, SWT.NONE).setText(child);
        }
        root.setExpanded(true);
        sash.setWeights(1, 2, 2);
        return sash;
    }

    private Control createText(CTabFolder parent) {
        StyledText text = new StyledText(parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        text.setText("""
                StyledText takes its colors from the theme: background, foreground,
                selection colors and margin color.

                Select some text to see the selection colors.""");
        text.setMargins(8, 8, 8, 8);
        return text;
    }

    private Control createContainers(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(2, true));

        Group group = new Group(page, SWT.NONE);
        group.setText("Group");
        group.setLayout(new FillLayout());
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        new Label(group, SWT.WRAP).setText("Group titles keep the native color on Windows (owner-draw comes in Phase 3).");

        TabFolder folder = new TabFolder(page, SWT.TOP);
        folder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (String title : new String[] { "TabFolder", "Second" }) {
            TabItem item = new TabItem(folder, SWT.NONE);
            item.setText(title);
            Label label = new Label(folder, SWT.NONE);
            label.setText(title + " page");
            item.setControl(label);
        }

        ViewForm form = new ViewForm(page, SWT.BORDER);
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        ToolBar toolBar = new ToolBar(form, SWT.FLAT);
        new ToolItem(toolBar, SWT.PUSH).setText("Tool");
        new ToolItem(toolBar, SWT.CHECK).setText("Items");
        form.setTopLeft(toolBar);
        Label content = new Label(form, SWT.NONE);
        content.setText("ViewForm with a ToolBar");
        form.setContent(content);

        ScrolledComposite scrolled = new ScrolledComposite(page, SWT.BORDER | SWT.V_SCROLL);
        scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        ExpandBar expand = new ExpandBar(scrolled, SWT.NONE);
        ExpandItem item = new ExpandItem(expand, SWT.NONE);
        item.setText("ExpandBar in a ScrolledComposite");
        Label inside = new Label(expand, SWT.NONE);
        inside.setText("Expanded content");
        item.setControl(inside);
        item.setHeight(inside.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
        item.setExpanded(true);
        scrolled.setContent(expand);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);
        scrolled.setMinSize(expand.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        return page;
    }

    private static void row(org.eclipse.swt.widgets.Composite parent, String title) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(title);
        themes(label).setThemeClass(label, "strong");
    }

    private static ThemeManager themes(Widget widget) {
        return ThemeManager.get(widget.getDisplay());
    }

    private static Composite flow(org.eclipse.swt.widgets.Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        RowLayout layout = new RowLayout();
        layout.center = true;
        composite.setLayout(layout);
        return composite;
    }

    private void screenshots(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        shell.forceActive();
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (int i = 0; i < 20; i++) {
                while (display.readAndDispatch()) {
                    // Paint before capturing.
                }
                display.timerExec(25, () -> { });
                display.sleep();
            }
            // Control.print renders the window itself, so it works without access to the screen.
            Rectangle bounds = shell.getClientArea();
            Image image = new Image(display, bounds.width, bounds.height);
            GC gc = new GC(image);
            shell.print(gc);
            gc.dispose();
            ImageLoader loader = new ImageLoader();
            loader.data = new ImageData[] { image.getImageData() };
            String file = dir.resolve(theme.id() + ".png").toString();
            loader.save(file, SWT.IMAGE_PNG);
            image.dispose();
            System.out.println("Saved " + file);
        }
        themes.setTheme(available.getFirst());
    }

    private int smokeTest() {
        while (display.readAndDispatch()) {
            // Let pending Skin/layout events run.
        }
        Theme dark = available.get(1);
        themes.setTheme(dark);
        List<String> problems = new ArrayList<>();
        int[] checked = { 0 };
        check(shell, problems, checked);
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked[0] + " widgets tagged and themed with " + dark.id());
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private void check(Widget widget, List<String> problems, int[] checked) {
        checked[0]++;
        if (widget.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(widget + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (widget instanceof Control control) {
            themes.styleOf(control).color("background").ifPresent(expected -> {
                RGB actual = control.getBackground().getRGB();
                if (!actual.equals(expected.rgb)) {
                    problems.add(control + ": background " + actual + ", theme says " + expected.rgb);
                }
            });
        }
        switch (widget) {
            case org.eclipse.swt.widgets.Table table -> List.of(table.getItems()).forEach(i -> check(i, problems, checked));
            case org.eclipse.swt.widgets.Tree tree -> List.of(tree.getItems()).forEach(i -> check(i, problems, checked));
            default -> {
            }
        }
        if (widget instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                check(child, problems, checked);
            }
        }
    }
}
