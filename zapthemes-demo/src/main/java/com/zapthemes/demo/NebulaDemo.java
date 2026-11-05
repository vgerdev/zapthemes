package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.nebula.jface.gridviewer.GridTableViewer;
import org.eclipse.nebula.jface.gridviewer.GridViewerColumn;
import org.eclipse.nebula.widgets.cdatetime.CDT;
import org.eclipse.nebula.widgets.formattedtext.FormattedText;
import org.eclipse.nebula.widgets.formattedtext.NumberFormatter;
import org.eclipse.nebula.widgets.pagination.collections.PageResultLoaderList;
import org.eclipse.nebula.widgets.opal.duallist.DLItem;
import org.eclipse.nebula.widgets.segmentedbar.Segment;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.nebula.badgedlabel.BadgedLabel;
import com.zapthemes.nebula.calendarcombo.CalendarCombo;
import com.zapthemes.nebula.carousel.Carousel;
import com.zapthemes.nebula.collapsiblebuttons.CollapsibleButtons;
import com.zapthemes.nebula.datechooser.DateChooser;
import com.zapthemes.nebula.datechooser.DateChooserCombo;
import com.zapthemes.nebula.pagination.table.PageableTable;
import com.zapthemes.nebula.picture.PictureControl;
import com.zapthemes.nebula.radiogroup.RadioGroup;
import com.zapthemes.nebula.radiogroup.RadioItem;
import com.zapthemes.nebula.timeline.TimelineComposite;
import com.zapthemes.nebula.cdatetime.CDateTime;
import com.zapthemes.nebula.chips.Chips;
import com.zapthemes.nebula.ctreecombo.CTreeCombo;
import com.zapthemes.nebula.ctreecombo.CTreeComboItem;
import com.zapthemes.nebula.gallery.Gallery;
import com.zapthemes.nebula.gallery.GalleryItem;
import com.zapthemes.nebula.grid.Grid;
import com.zapthemes.nebula.opal.breadcrumb.Breadcrumb;
import com.zapthemes.nebula.opal.breadcrumb.BreadcrumbItem;
import com.zapthemes.nebula.opal.checkboxgroup.CheckBoxGroup;
import com.zapthemes.nebula.opal.duallist.DualList;
import com.zapthemes.nebula.opal.header.Header;
import com.zapthemes.nebula.opal.rangeslider.RangeSlider;
import com.zapthemes.nebula.opal.roundedtoolbar.RoundedToolItem;
import com.zapthemes.nebula.opal.roundedtoolbar.RoundedToolbar;
import com.zapthemes.nebula.opal.starrating.StarRating;
import com.zapthemes.nebula.opal.switchbutton.SwitchButton;
import com.zapthemes.nebula.opal.textassist.TextAssist;
import com.zapthemes.nebula.opal.titledseparator.TitledSeparator;
import com.zapthemes.nebula.passwordrevealer.PasswordRevealer;
import com.zapthemes.nebula.pgroup.PGroup;
import com.zapthemes.nebula.progresscircle.ProgressCircle;
import com.zapthemes.nebula.pshelf.PShelf;
import com.zapthemes.nebula.pshelf.PShelfItem;
import com.zapthemes.nebula.segmentedbar.SegmentedBar;
import com.zapthemes.nebula.splitbutton.SplitButton;
import com.zapthemes.nebula.tablecombo.TableCombo;
import com.zapthemes.nebula.tiles.Tiles;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.custom.CTabItem;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.TableItem;

/**
 * The Nebula released and incubation widgets, created through their ZapThemes proxies, with live
 * theme switching.
 * {@code --smoke} switches themes and checks that every widget is tagged and every Nebula widget has
 * the theme's background; {@code --screenshots <dir>} saves every tab in every theme.
 */
public final class NebulaDemo {

    private record Row(String widget, String bundle) {
    }

    private static final List<Row> ROWS = List.of(
            new Row("Grid", "grid"), new Row("CDateTime", "cdatetime"), new Row("Gallery", "gallery"),
            new Row("TableCombo", "tablecombo"), new Row("PGroup", "pgroup"), new Row("SwitchButton", "opal"));

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private final List<Image> images = new ArrayList<>();
    private Shell shell;
    private CTabFolder tabs;

    private NebulaDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        NebulaDemo demo = new NebulaDemo(display);
        demo.open();
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (shots >= 0) {
            demo.screenshots(Path.of(options.get(shots + 1)));
        }
        if (options.contains("--smoke")) {
            exit = demo.smokeTest();
            demo.shell.dispose();
        }
        while (!demo.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        demo.images.forEach(Image::dispose);
        display.dispose();
        System.exit(exit);
    }

    private void open() {
        shell = new Shell(display);
        shell.setText("ZapThemes — Nebula widgets");
        shell.setLayout(new GridLayout(1, false));

        Composite bar = new Composite(shell, SWT.NONE);
        bar.setLayout(new GridLayout(2, false));
        Combo picker = new Combo(bar, SWT.READ_ONLY);
        available.forEach(theme -> picker.add(theme.name()));
        picker.select(0);
        picker.addListener(SWT.Selection, e -> themes.setTheme(available.get(picker.getSelectionIndex())));
        // Follow switches made elsewhere (the button, screenshots).
        ThemeChangeListener follow = (previous, current) -> picker.select(available.indexOf(current));
        themes.addThemeChangeListener(follow);
        picker.addDisposeListener(e -> themes.removeThemeChangeListener(follow));
        new Label(bar, SWT.NONE).setText("Every widget below is a ZapThemes proxy of a Nebula widget.");

        tabs = new CTabFolder(shell, SWT.BORDER);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tab("Data", this::createData);
        tab("Inputs", this::createInputs);
        tab("Display", this::createDisplay);
        tab("Incubation", this::createIncubation);
        tabs.setSelection(0);

        shell.setSize(980, 640);
        shell.open();
    }

    private void tab(String title, Function<CTabFolder, Control> page) {
        CTabItem item = new CTabItem(tabs, SWT.NONE);
        item.setText(title);
        item.setControl(page.apply(tabs));
    }

    // ------------------------------------------------------------------ pages

    private Control createData(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(2, true));

        GridTableViewer viewer = new GridTableViewer(new Grid(page, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL));
        Grid grid = (Grid) viewer.getGrid();
        grid.setHeaderVisible(true);
        grid.setLinesVisible(true);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        column(viewer, "Widget", Row::widget);
        column(viewer, "Bundle", Row::bundle);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setInput(ROWS);
        grid.setSelection(1);

        Gallery gallery = new Gallery(page, SWT.V_SCROLL | SWT.BORDER);
        gallery.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GalleryItem group = new GalleryItem(gallery, SWT.NONE);
        group.setText("Swatches");
        group.setExpanded(true);
        for (int i = 0; i < 6; i++) {
            GalleryItem item = new GalleryItem(group, SWT.NONE);
            item.setText("Item " + (i + 1));
            item.setImage(swatch(i));
        }
        gallery.setSelection(new org.eclipse.nebula.widgets.gallery.GalleryItem[] { group.getItem(1) });

        TableCombo tableCombo = new TableCombo(page, SWT.BORDER | SWT.READ_ONLY);
        tableCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (Row row : ROWS) {
            new TableItem(tableCombo.getTable(), SWT.NONE).setText(row.widget());
        }
        tableCombo.select(0);

        CTreeCombo treeCombo = new CTreeCombo(page, SWT.BORDER | SWT.READ_ONLY);
        treeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        CTreeComboItem root = new CTreeComboItem(treeCombo, SWT.NONE);
        root.setText("org.eclipse.nebula");
        for (String child : new String[] { "grid", "gallery", "opal" }) {
            new CTreeComboItem(root, SWT.NONE).setText(child);
        }

        DualList dualList = new DualList(page, SWT.NONE);
        GridData dualData = new GridData(SWT.FILL, SWT.FILL, true, true);
        dualData.horizontalSpan = 2;
        dualList.setLayoutData(dualData);
        for (String name : new String[] { "Breadcrumb", "Chips", "Header", "Tiles", "Carousel" }) {
            dualList.add(new DLItem(name));
        }
        return page;
    }

    private interface Text {
        String of(Row row);
    }

    private static void column(GridTableViewer viewer, String title, Text text) {
        GridViewerColumn column = new GridViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(160);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return text.of((Row) element);
            }
        });
    }

    private Control createInputs(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(2, false));

        row(page, "CDateTime");
        CDateTime date = new CDateTime(page, CDT.BORDER | CDT.DROP_DOWN | CDT.DATE_MEDIUM);
        date.setSelection(new Date());
        row(page, "CDateTime (inline)");
        CDateTime inline = new CDateTime(page, CDT.BORDER | CDT.SIMPLE | CDT.DATE_SHORT);
        inline.setSelection(new Date());
        row(page, "CalendarCombo");
        new CalendarCombo(page, SWT.READ_ONLY).setDate(java.util.Calendar.getInstance());
        row(page, "TextAssist");
        new TextAssist(page, SWT.BORDER).setText("Type to complete");
        row(page, "PasswordRevealer");
        new PasswordRevealer(page, SWT.BORDER | SWT.PASSWORD).setText("secret");
        row(page, "RangeSlider");
        RangeSlider range = new RangeSlider(page, SWT.HORIZONTAL);
        range.setLayoutData(new GridData(240, SWT.DEFAULT));
        range.setMinimum(0);
        range.setMaximum(100);
        range.setSelection(20, 70);
        row(page, "StarRating");
        new StarRating(page, SWT.NONE).setCurrentNumberOfStars(3);
        row(page, "SwitchButton");
        SwitchButton toggle = new SwitchButton(page, SWT.NONE);
        toggle.setText("Dark mode");
        toggle.setSelection(true);
        row(page, "CheckBoxGroup");
        CheckBoxGroup group = new CheckBoxGroup(page, SWT.NONE);
        group.setText("Options");
        group.getContent().setLayout(new GridLayout(1, false));
        new Button(group.getContent(), SWT.CHECK).setText("Inside a check box group");
        row(page, "Chips");
        Composite chips = new Composite(page, SWT.NONE);
        chips.setLayout(new FillLayout(SWT.HORIZONTAL));
        for (String text : new String[] { "theming", "nebula", "swt" }) {
            new Chips(chips, SWT.CLOSE).setText(text);
        }
        row(page, "SplitButton");
        new SplitButton(page, SWT.NONE).setText("Split");
        return page;
    }

    private Control createDisplay(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(2, true));

        Header header = new Header(page, SWT.NONE);
        header.setTitle("Header");
        header.setDescription("Opal Header with title, description and gradient from the theme.");
        header.setLayoutData(span(new GridData(SWT.FILL, SWT.CENTER, true, false)));

        PGroup pgroup = new PGroup(page, SWT.SMOOTH);
        pgroup.setText("PGroup");
        pgroup.setLayout(new FillLayout());
        pgroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        new Label(pgroup, SWT.WRAP).setText("A collapsible PGroup.");

        PShelf shelf = new PShelf(page, SWT.BORDER);
        shelf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (String title : new String[] { "PShelf one", "PShelf two" }) {
            PShelfItem item = new PShelfItem(shelf, SWT.NONE);
            item.setText(title);
            item.getBody().setLayout(new FillLayout());
            new Label(item.getBody(), SWT.NONE).setText(title + " body");
        }

        Composite row = new Composite(page, SWT.NONE);
        row.setLayout(new GridLayout(3, false));
        row.setLayoutData(span(new GridData(SWT.FILL, SWT.CENTER, true, false)));
        BadgedLabel badged = new BadgedLabel(row, SWT.NONE);
        badged.setText("Inbox");
        badged.setBadgeValue("7");
        ProgressCircle circle = new ProgressCircle(row, SWT.NONE);
        circle.setMaximum(100);
        circle.setSelection(65);
        circle.setLayoutData(new GridData(64, 64));
        Breadcrumb breadcrumb = new Breadcrumb(row, SWT.BORDER);
        for (String text : new String[] { "Home", "Widgets", "Nebula" }) {
            new BreadcrumbItem(breadcrumb, SWT.TOGGLE).setText(text);
        }

        RoundedToolbar toolbar = new RoundedToolbar(page, SWT.NONE);
        for (String text : new String[] { "Bold", "Italic", "Underline" }) {
            RoundedToolItem item = new RoundedToolItem(toolbar, SWT.TOGGLE);
            item.setText(text);
        }
        TitledSeparator separator = new TitledSeparator(page, SWT.NONE);
        separator.setText("TitledSeparator");
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        SegmentedBar segments = new SegmentedBar(page, SWT.NONE);
        segments.setLayoutData(span(new GridData(SWT.FILL, SWT.CENTER, true, false)));
        int[][] colors = { { 0x35, 0x74, 0xF0 }, { 0x2E, 0xA0, 0x43 }, { 0xD9, 0x8C, 0x1E } };
        for (int i = 0; i < colors.length; i++) {
            segments.addSegment(new Segment().setValue(10d * (i + 2)).setText("Part " + (i + 1))
                    .setBackground(new Color(colors[i][0], colors[i][1], colors[i][2])));
        }

        Carousel carousel = new Carousel(page, SWT.NONE);
        carousel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (int i = 0; i < 3; i++) {
            carousel.addImage(swatch(i));
        }
        Tiles<String> tiles = new Tiles<>(page, SWT.NONE);
        tiles.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tiles.setItems(List.of("One", "Two", "Three", "Four"));
        return page;
    }

    /** The incubation widgets (CompositeTable needs app-defined row classes; see the contract test). */
    private Control createIncubation(CTabFolder parent) {
        Composite page = new Composite(parent, SWT.NONE);
        page.setLayout(new GridLayout(3, false));

        CollapsibleButtons buttons = new CollapsibleButtons(page, SWT.NONE);
        GridData buttonsData = new GridData(SWT.FILL, SWT.END, false, true, 1, 3);
        buttonsData.widthHint = 170;
        buttons.setLayoutData(buttonsData);
        for (String name : new String[] { "Mail", "Calendar", "Contacts" }) {
            buttons.addButton(name, name, swatch(name.length()), null);
        }

        Composite fields = new Composite(page, SWT.NONE);
        fields.setLayout(new GridLayout(2, false));
        row(fields, "DateChooserCombo");
        DateChooserCombo combo = new DateChooserCombo(fields, SWT.BORDER);
        combo.setValue(new Date());
        row(fields, "FormattedText");
        FormattedText amount = new FormattedText(fields, SWT.BORDER);
        amount.setFormatter(new NumberFormatter("#,##0.00"));
        amount.setValue(1234.5);
        row(fields, "RadioGroup");
        RadioGroup group = new RadioGroup(fields, SWT.NONE);
        for (String text : new String[] { "Light", "Dark", "System" }) {
            new RadioItem(group, SWT.NONE).setText(text);
        }
        group.setSelection(group.getItems()[1]);
        row(fields, "PictureControl");
        new PictureControl(fields).setDefaultImage(swatch(3));

        DateChooser chooser = new DateChooser(page, SWT.BORDER);
        chooser.setSelectedDate(new Date());

        PageableTable pageable = new PageableTable(page, SWT.BORDER, SWT.BORDER | SWT.FULL_SELECTION, 4);
        pageable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        TableViewer viewer = pageable.getViewer();
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new LabelProvider());
        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            rows.add("Pageable row " + i);
        }
        pageable.setPageLoader(new PageResultLoaderList<>(rows));
        pageable.setCurrentPage(0);

        TimelineComposite timeline = new TimelineComposite(page, SWT.NONE);
        timeline.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        return page;
    }

    private static GridData span(GridData data) {
        data.horizontalSpan = 2;
        return data;
    }

    private static void row(org.eclipse.swt.widgets.Composite parent, String title) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(title);
        ThemeManager.get(label.getDisplay()).setThemeClass(label, "strong");
    }

    /** A small colored image (an app's own content, not themed). */
    private Image swatch(int index) {
        int[][] colors = { { 0x35, 0x74, 0xF0 }, { 0x2E, 0xA0, 0x43 }, { 0xD9, 0x8C, 0x1E },
                { 0xC6, 0x28, 0x28 }, { 0x8E, 0x44, 0xAD }, { 0x16, 0xA0, 0x85 } };
        int[] c = colors[index % colors.length];
        Image image = new Image(display, 96, 64);
        GC gc = new GC(image);
        gc.setBackground(new Color(c[0], c[1], c[2]));
        gc.fillRectangle(0, 0, 96, 64);
        gc.dispose();
        images.add(image);
        return image;
    }

    // ------------------------------------------------------------------ verification

    private void screenshots(Path dir) {
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (int i = 0; i < tabs.getItemCount(); i++) {
                tabs.setSelection(i);
                DemoSupport.settle(display);
                DemoSupport.screenshot(shell, dir, "nebula-" + theme.id() + "-" + tabs.getItem(i).getText().toLowerCase());
            }
        }
        tabs.setSelection(0);
        themes.setTheme(available.getFirst());
    }

    private int smokeTest() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (int i = 0; i < tabs.getItemCount(); i++) {
                tabs.setSelection(i);
                DemoSupport.settle(display);
            }
            checked = check(shell, problems);
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls tagged; Nebula widgets themed, in " + available.size() + " themes");
            return 0;
        }
        problems.stream().distinct().forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private int check(Control control, List<String> problems) {
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (isNebula(control)) {
            themes.styleOf(control).color("background").ifPresent(expected -> {
                if (!control.getBackground().getRGB().equals(expected.rgb)) {
                    problems.add(control.getClass().getName() + " background " + control.getBackground().getRGB()
                            + ", theme says " + expected.rgb);
                }
            });
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(child, problems);
            }
        }
        return count;
    }

    private static boolean isNebula(Widget widget) {
        return widget.getClass().getName().startsWith("com.zapthemes.nebula.");
    }
}
