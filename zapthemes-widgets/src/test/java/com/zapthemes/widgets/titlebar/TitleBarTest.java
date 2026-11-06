package com.zapthemes.widgets.titlebar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Link;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.titlebar.TitleBar.Part;

class TitleBarTest {

    private static final RGB DARK_BAR = new RGB(0x2B, 0x2D, 0x30);
    private static final RGB LIGHT_BAR = new RGB(0x35, 0x74, 0xF0);
    private static final RGB EXCEL_GREEN = new RGB(0x10, 0x7C, 0x41);

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private static Theme highContrast;
    private ThemeManager manager;
    private Shell shell;
    private TitleBar bar;
    private TitleBarItem autosave;
    private TitleBarItem save;
    private TitleBarItem undo;
    private TitleBarItem redo;
    private TitleBarItem upgrade;
    private Menu titleMenu;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
        highContrast = registry.resolve("zap-high-contrast");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(dark);
        shell = new Shell(display, SWT.NO_TRIM);
        bar = new TitleBar(shell, SWT.NONE);
        autosave = item(SWT.CHECK, "autosave", "AutoSave", null);
        save = item(SWT.PUSH, "save", "Save", TitleBarGlyph.SAVE);
        undo = item(SWT.DROP_DOWN, "undo", "Undo", TitleBarGlyph.UNDO);
        redo = item(SWT.PUSH, "redo", "Redo", TitleBarGlyph.REDO);
        upgrade = item(SWT.PUSH | SWT.TRAIL, "upgrade", "Upgrade your plan", null);
        titleMenu = new com.zapthemes.swt.widgets.Menu(shell, SWT.POP_UP);
        bar.setTitle("Document6 - AutoRecovered");
        bar.setTitleMenu(titleMenu);
        bar.setSearchHint("Search");
        bar.setProfile(new TitleBarProfile("Paul S Davenport III", "paul@example.com").action("Sign out", () -> { }));
        Label slotLabel = new Label(bar.getSlot(), SWT.NONE);
        slotLabel.setText("Comments");
        layOut(1200);
    }

    @AfterEach
    void tearDown() {
        for (org.eclipse.swt.widgets.Shell s : display.getShells()) {
            s.dispose();
        }
        if (!display.getHighContrast()) {
            manager.setTheme(dark);
        }
    }

    private TitleBarItem item(int style, String id, String text, TitleBarGlyph glyph) {
        TitleBarItem item = new TitleBarItem(bar, style);
        item.setId(id);
        item.setText(text);
        item.setGlyph(glyph);
        return item;
    }

    private void layOut(int width) {
        int height = bar.computeSize(width, SWT.DEFAULT).y;
        shell.setSize(width, height + 200);
        bar.setBounds(0, 0, width, height);
        bar.computeLayout();
    }

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private Part partAt(Rectangle r) {
        Point c = center(r);
        return bar.getPart(c.x, c.y);
    }

    // ------------------------------------------------------------------ frame and layout

    @Test
    void noTrimShellsGetTheDrawnFrame() {
        assertThat(bar.getFrameMode()).isEqualTo(TitleBar.FrameMode.DRAWN);
        assertThat(bar.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("TitleBar");
        assertThat(TitleBar.shellStyle(SWT.SHELL_TRIM) & SWT.NO_TRIM).isEqualTo(WindowFrame.nativeSupported() ? 0 : SWT.NO_TRIM);
    }

    @Test
    void trimmedShellsKeepTheirCaptionWithoutANativeFrame() {
        Assumptions.assumeFalse(WindowFrame.nativeSupported());
        Shell trimmed = new Shell(display, SWT.SHELL_TRIM);
        TitleBar embedded = new TitleBar(trimmed, SWT.NONE);
        embedded.setBounds(0, 0, 800, embedded.computeSize(800, SWT.DEFAULT).y);
        embedded.computeLayout();

        assertThat(embedded.getFrameMode()).isEqualTo(TitleBar.FrameMode.EMBEDDED);
        assertThat(embedded.getPartBounds(Part.CLOSE)).isNull();
        assertThat(embedded.getPartBounds(Part.SYSTEM_MENU)).isNull();
    }

    @Test
    void hitTestingFindsEveryPart() {
        for (TitleBarItem item : List.of(autosave, save, undo, redo, upgrade)) {
            assertThat(item.getBounds().isEmpty()).as(item.getText()).isFalse();
            assertThat(partAt(item.getBounds())).as(item.getText()).isEqualTo(Part.ITEM);
        }
        for (Part part : List.of(Part.SYSTEM_MENU, Part.CUSTOMIZE, Part.TITLE, Part.SEARCH, Part.SLOT, Part.AVATAR,
                Part.MINIMIZE, Part.MAXIMIZE, Part.CLOSE)) {
            Rectangle bounds = bar.getPartBounds(part);
            assertThat(bounds).as(part.name()).isNotNull();
            assertThat(partAt(bounds)).as(part.name()).isEqualTo(part);
        }
        Rectangle customize = bar.getPartBounds(Part.CUSTOMIZE);
        assertThat(bar.getPart(customize.x + customize.width + 3, bar.getSize().y / 2)).isEqualTo(Part.CAPTION);
        assertThat(bar.getPart(-1, 5)).isEqualTo(Part.NONE);
        assertThat(bar.getPart(5, bar.getSize().y + 1)).isEqualTo(Part.NONE);
    }

    @Test
    void partsRunLeftToRightAsInOffice() {
        List<Integer> xs = new ArrayList<>();
        xs.add(bar.getPartBounds(Part.SYSTEM_MENU).x);
        for (TitleBarItem item : List.of(autosave, save, undo, redo)) {
            xs.add(item.getBounds().x);
        }
        for (Part part : List.of(Part.CUSTOMIZE, Part.TITLE, Part.SEARCH, Part.SLOT)) {
            xs.add(bar.getPartBounds(part).x);
        }
        xs.add(upgrade.getBounds().x);
        for (Part part : List.of(Part.AVATAR, Part.MINIMIZE, Part.MAXIMIZE, Part.CLOSE)) {
            xs.add(bar.getPartBounds(part).x);
        }
        assertThat(xs).isSorted();
        Rectangle close = bar.getPartBounds(Part.CLOSE);
        assertThat(close.x + close.width).isEqualTo(bar.getSize().x);
        // The search box is centered on the window when there is room.
        layOut(1600);
        Rectangle search = bar.getPartBounds(Part.SEARCH);
        assertThat(Math.abs(search.x + search.width / 2 - bar.getSize().x / 2)).isLessThan(4);
        assertThat(bar.getSearchField().getBounds().intersects(search)).isTrue();
    }

    @Test
    void narrowWindowsCollapseSearchThenDropTheTitleAndItems() {
        layOut(560);
        Rectangle search = bar.getPartBounds(Part.SEARCH);
        assertThat(search).isNotNull();
        assertThat(search.width).isLessThan(Math.round(100 * bar.unit()));

        layOut(360);
        assertThat(bar.getPartBounds(Part.TITLE)).isNull();
        assertThat(redo.getBounds().isEmpty()).isTrue();
        assertThat(bar.getPartBounds(Part.CLOSE)).isNotNull();
    }

    @Test
    void theBarFollowsTheThemeFontSize() {
        int height = bar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
        assertThat(height).isBetween(24, 80);
        assertThat(bar.unit()).isEqualTo(height / 32f);
    }

    @Test
    void drawnFrameEdgesAndResizing() {
        Rectangle bounds = new Rectangle(100, 100, 400, 300);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(101, 250), 5)).isEqualTo(SWT.LEFT);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(498, 250), 5)).isEqualTo(SWT.RIGHT);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(300, 398), 5)).isEqualTo(SWT.BOTTOM);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(300, 101), 5)).isEqualTo(SWT.TOP);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(102, 105), 5)).isEqualTo(SWT.TOP | SWT.LEFT);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(300, 250), 5)).isEqualTo(DrawnFrame.NONE);
        assertThat(DrawnFrame.edgesAt(bounds, new Point(50, 250), 5)).isEqualTo(DrawnFrame.NONE);

        assertThat(DrawnFrame.resized(bounds, SWT.LEFT | SWT.TOP, -20, 10, new Point(0, 0)))
                .isEqualTo(new Rectangle(80, 110, 420, 290));
        assertThat(DrawnFrame.resized(bounds, SWT.RIGHT, -1000, 0, new Point(200, 0)).width).isEqualTo(200);
    }

    // ------------------------------------------------------------------ actions

    @Test
    void windowButtonsMinimizeMaximizeAndClose() {
        shell.open();
        int[] closes = { 0 };
        shell.addListener(SWT.Close, e -> {
            closes[0]++;
            e.doit = false;
        });

        // Cocoa doesn't maximize (zoom) a borderless window; macOS gets the native frame instead.
        boolean zooms = !"cocoa".equals(SWT.getPlatform());
        click(bar.getPartBounds(Part.MAXIMIZE));
        assertThat(shell.getMaximized()).isEqualTo(zooms);
        click(bar.getPartBounds(Part.MAXIMIZE));
        assertThat(shell.getMaximized()).isFalse();

        click(bar.getPartBounds(Part.CLOSE));
        assertThat(closes[0]).isEqualTo(1);
        assertThat(shell.isDisposed()).isFalse();

        click(bar.getPartBounds(Part.MINIMIZE));
        assertThat(shell.getMinimized()).isTrue();
        shell.setMinimized(false);
    }

    @Test
    void doubleClickOnTheCaptionMaximizes() {
        Assumptions.assumeFalse("cocoa".equals(SWT.getPlatform()), "Cocoa doesn't zoom borderless windows");
        shell.open();
        Rectangle customize = bar.getPartBounds(Part.CUSTOMIZE);
        Event event = mouse(customize.x + customize.width + 3, bar.getSize().y / 2);
        event.count = 2;
        bar.notifyListeners(SWT.MouseDoubleClick, event);
        assertThat(shell.getMaximized()).isTrue();
        shell.setMaximized(false);
    }

    @Test
    void itemsNotifyAndCheckItemsFlip() {
        List<Event> events = new ArrayList<>();
        for (TitleBarItem item : List.of(autosave, save, undo)) {
            item.addListener(SWT.Selection, events::add);
        }

        click(save.getBounds());
        click(autosave.getBounds());
        Rectangle undoBounds = undo.getBounds();
        click(new Rectangle(undoBounds.x + undoBounds.width - 4, undoBounds.y, 4, undoBounds.height));
        click(new Rectangle(undoBounds.x, undoBounds.y, 6, undoBounds.height));

        assertThat(events).extracting(e -> e.widget).containsExactly(save, autosave, undo, undo);
        assertThat(events.get(2).detail).isEqualTo(SWT.ARROW);
        assertThat(events.get(3).detail).isZero();
        assertThat(autosave.getSelection()).isTrue();

        save.setEnabled(false);
        click(save.getBounds());
        assertThat(events).hasSize(4);
    }

    @Test
    void customizeMenuShowsAndHidesCommands() {
        Menu menu = bar.createCustomizeMenu();
        List<MenuItem> checks = Arrays.stream(menu.getItems()).filter(i -> (i.getStyle() & SWT.CHECK) != 0).toList();
        assertThat(checks).extracting(MenuItem::getText).containsExactly("AutoSave", "Save", "Undo", "Redo");

        checks.get(1).setSelection(false);
        checks.get(1).notifyListeners(SWT.Selection, new Event());

        assertThat(save.isShown()).isFalse();
        assertThat(save.getBounds().isEmpty()).isTrue();
        assertThat(undo.getBounds().x).isLessThan(autosave.getBounds().x + autosave.getBounds().width + Math.round(40 * bar.unit()));
        menu.dispose();
    }

    @Test
    void quickAccessOrderCanBeChanged() {
        bar.setQuickAccessOrder(List.of("redo", "save"));
        assertThat(bar.getQuickAccessItems()).containsExactly(redo, save, autosave, undo);
        assertThat(redo.getBounds().x).isLessThan(save.getBounds().x);
        assertThat(bar.getItem("undo")).isSameAs(undo);
        assertThat(bar.getItem("upgrade")).isNull();
    }

    @Test
    void systemMenuEnablesByWindowState() {
        Menu menu = bar.createSystemMenu();
        assertThat(texts(menu)).containsExactly("&Restore", "&Move", "&Size", "Mi&nimize", "Ma&ximize", "", "&Close\tAlt+F4");
        assertThat(menu.getItems()[0].getEnabled()).isFalse();
        assertThat(menu.getItems()[4].getEnabled()).isTrue();
        if ("win32".equals(SWT.getPlatform())) {
            // Only Windows menus have a default item.
            assertThat(menu.getDefaultItem().getText()).startsWith("&Close");
        }
        menu.dispose();

        Assumptions.assumeFalse("cocoa".equals(SWT.getPlatform()), "Cocoa doesn't zoom borderless windows");
        shell.open();
        shell.setMaximized(true);
        menu = bar.createSystemMenu();
        assertThat(menu.getItems()[0].getEnabled()).isTrue();
        assertThat(menu.getItems()[1].getEnabled()).isFalse();
        assertThat(menu.getItems()[4].getEnabled()).isFalse();
        menu.dispose();
        shell.setMaximized(false);
    }

    @Test
    void keyboardMovesBetweenPartsAndActivates() {
        List<Event> events = new ArrayList<>();
        save.addListener(SWT.Selection, events::add);
        shell.open();
        assertThat(bar.setFocus()).as("not a tab stop").isFalse();
        bar.focusBar();
        bar.notifyListeners(SWT.FocusIn, new Event());
        List<TitleBar.Hit> parts = bar.focusables();
        assertThat(parts.getFirst().part()).isEqualTo(Part.SYSTEM_MENU);
        int saveIndex = parts.indexOf(parts.stream().filter(h -> h.item() == save).findFirst().orElseThrow());

        for (int i = 0; i < saveIndex; i++) {
            key(SWT.ARROW_RIGHT, (char) 0);
        }
        key(0, SWT.CR);
        assertThat(events).hasSize(1);
        key(SWT.END, (char) 0);
        key(SWT.ARROW_RIGHT, (char) 0); // wraps around to the system menu
        key(SWT.ARROW_RIGHT, (char) 0);
        key(0, ' ');
        // The second part is AutoSave: Space flips it, and Save got no second event.
        assertThat(autosave.getSelection()).isTrue();
        assertThat(events).hasSize(1);
    }

    @Test
    void partsHaveScreenReaderNamesAndRoles() {
        List<TitleBar.Hit> parts = bar.focusables();
        List<String> names = parts.stream().map(bar::accessibleName).toList();
        assertThat(names).contains("System menu", "AutoSave", "Save", "Undo", "Redo", "Document6 - AutoRecovered, document menu",
                "Search", "Upgrade your plan", "Account: Paul S Davenport III", "Minimize", "Maximize", "Close");
        assertThat(bar.getAccessible()).isNotNull();
        assertThat(ACC.ROLE_TOOLBAR).isPositive();
    }

    // ------------------------------------------------------------------ theming

    @Test
    void colorsComeFromTheThemeAndSwitchLive() {
        Assumptions.assumeFalse(display.getHighContrast());
        shell.open();
        bar.computeLayout();
        assertThat(colors(true).background()).isEqualTo(DARK_BAR);
        assertThat(bar.getBackground().getRGB()).isIn(DARK_BAR, colors(false).background());

        manager.setTheme(light);
        assertThat(colors(true).background()).isEqualTo(LIGHT_BAR);
        assertThat(bar.getBackground().getRGB()).isIn(LIGHT_BAR, colors(false).background());
        assertThat(colors(true).closeHover()).isEqualTo(TitleBarColors.CLOSE_RED);

        manager.setTheme(highContrast);
        assertThat(colors(true).background()).isEqualTo(new RGB(0, 0, 0));
        assertThat(colors(true).foreground()).isEqualTo(new RGB(255, 255, 255));
    }

    @Test
    void anAccentColorsTheBarInThemesThatAllowIt() {
        Assumptions.assumeFalse(display.getHighContrast());
        manager.setTheme(light);
        bar.setAccent(EXCEL_GREEN);
        TitleBarColors active = colors(true);
        assertThat(active.background()).isEqualTo(EXCEL_GREEN);
        assertThat(active.foreground()).isEqualTo(TitleBarColors.WHITE);
        assertThat(active.hover()).isNotEqualTo(EXCEL_GREEN);
        assertThat(bar.getBackground().getRGB()).isIn(EXCEL_GREEN, colors(false).background());

        TitleBarColors inactive = colors(false);
        assertThat(inactive.background()).isNotEqualTo(EXCEL_GREEN);
        assertThat(TitleBarColors.luminance(inactive.background())).isGreaterThan(TitleBarColors.luminance(EXCEL_GREEN));

        manager.setTheme(dark);
        assertThat(colors(true).background()).isEqualTo(DARK_BAR);
        bar.setAccent(null);
        manager.setTheme(light);
        assertThat(colors(true).background()).isEqualTo(LIGHT_BAR);
    }

    @Test
    void theAvatarStaysVisibleOnAnAccentOfItsOwnColor() {
        Assumptions.assumeFalse(display.getHighContrast());
        manager.setTheme(light);
        bar.setAccent(new RGB(0xC4, 0x3E, 0x1C));
        TitleBarColors colors = colors(true);
        assertThat(TitleBarColors.distance(colors.avatarBackground(), colors.background())).isGreaterThan(60);
        bar.setAccent(EXCEL_GREEN);
        assertThat(colors(true).avatarBackground()).isEqualTo(new RGB(0xCA, 0x50, 0x10));
    }

    @Test
    void inactiveWindowsDimTheBar() {
        Assumptions.assumeFalse(display.getHighContrast());
        TitleBarColors active = colors(true);
        TitleBarColors inactive = colors(false);
        assertThat(inactive.background()).isEqualTo(new RGB(0x25, 0x26, 0x2A));
        assertThat(inactive.foreground()).isNotEqualTo(active.foreground());
    }

    @Test
    void paintsTheBarBackground() {
        Assumptions.assumeFalse(display.getHighContrast());
        manager.setTheme(light);
        bar.setAccent(EXCEL_GREEN);
        shell.open();
        shell.forceActive();
        settle();
        Rectangle customize = bar.getPartBounds(Part.CUSTOMIZE);
        RGB pixel = pixel(bar, customize.x + customize.width + 4, 3);
        assertThat(pixel).isIn(colors(true).background(), colors(false).background());
    }

    @Test
    void theCatalogDescribesTheTitleBar() {
        PropertyCatalog catalog = PropertyCatalog.load();
        assertThat(catalog.type("TitleBar")).isPresent();
        assertThat(catalog.type("TitleBarSearch")).isPresent();
        assertThat(catalog.type("TitleBarSlot")).isPresent();
        assertThat(BaseNames.get("titleBar")).isPresent();
        assertThat(BaseNames.get("titleBarInactiveForeground")).isPresent();
        for (Theme theme : List.of(light, dark, highContrast)) {
            assertThat(catalog.undescribed(theme)).as(theme.id()).noneMatch(s -> s.contains("TitleBar"));
        }
    }

    // ------------------------------------------------------------------ profile

    @Test
    void initialsSkipNameSuffixes() {
        assertThat(TitleBarProfile.initialsOf("Paul S Davenport III")).isEqualTo("PD");
        assertThat(TitleBarProfile.initialsOf("ada")).isEqualTo("A");
        assertThat(TitleBarProfile.initialsOf("Grace Brewster Hopper")).isEqualTo("GH");
        assertThat(new TitleBarProfile("x y", null).initials("ZZ").initials()).isEqualTo("ZZ");
    }

    @Test
    void theAvatarOpensTheProfilePopup() {
        boolean[] signedOut = { false };
        bar.setProfile(new TitleBarProfile("Paul S Davenport III", "paul@example.com")
                .action("View account", () -> { })
                .action("Sign out", () -> signedOut[0] = true));
        shell.open();
        click(bar.getPartBounds(Part.AVATAR));

        org.eclipse.swt.widgets.Shell popup = Arrays.stream(shell.getShells())
                .filter(s -> s.getData("zapthemes.titleBar.profile") != null).findFirst().orElseThrow();
        assertThat(popup.isVisible()).isTrue();
        assertThat(popup.getData(ThemeKeys.THEME_CLASS)).isEqualTo("popup");
        List<String> labels = Arrays.stream(popup.getChildren()).filter(c -> c instanceof Label)
                .map(c -> ((Label) c).getText()).filter(t -> !t.isEmpty()).toList();
        assertThat(labels).containsExactly("Paul S Davenport III", "paul@example.com");
        List<Link> links = Arrays.stream(popup.getChildren()).filter(c -> c instanceof Link).map(c -> (Link) c).toList();
        assertThat(links).extracting(Link::getText).containsExactly("<a>View account</a>", "<a>Sign out</a>");
        Rectangle avatar = bar.getPartBounds(Part.AVATAR);
        Point anchor = bar.toDisplay(avatar.x + avatar.width, avatar.y + avatar.height);
        assertThat(popup.getBounds().x + popup.getBounds().width).isLessThanOrEqualTo(anchor.x + 1);

        links.get(1).notifyListeners(SWT.Selection, new Event());
        assertThat(signedOut[0]).isTrue();
        assertThat(popup.isDisposed()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private TitleBarColors colors(boolean active) {
        return TitleBarColors.of(bar, bar.getAccent(), active);
    }

    private static List<String> texts(Menu menu) {
        return Arrays.stream(menu.getItems()).map(MenuItem::getText).toList();
    }

    private void click(Rectangle bounds) {
        Point c = center(bounds);
        for (int type : new int[] { SWT.MouseDown, SWT.MouseUp }) {
            bar.notifyListeners(type, mouse(c.x, c.y));
        }
    }

    private static Event mouse(int x, int y) {
        Event event = new Event();
        event.button = 1;
        event.count = 1;
        event.x = x;
        event.y = y;
        return event;
    }

    private void key(int keyCode, char character) {
        Event event = new Event();
        event.keyCode = keyCode;
        event.character = character;
        bar.notifyListeners(SWT.KeyDown, event);
    }

    private static void settle() {
        for (int i = 0; i < 10; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }

    private static RGB pixel(Control control, int x, int y) {
        Point size = control.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        control.print(gc);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        boolean blank = true;
        for (int py = 0; py < data.height && blank; py += 3) {
            for (int px = 0; px < data.width && blank; px += 3) {
                RGB rgb = data.palette.getRGB(data.getPixel(px, py));
                blank = rgb.red == 0 && rgb.green == 0 && rgb.blue == 0;
            }
        }
        Assumptions.assumeFalse(blank, "Control.print returned a blank image (locked desktop?)");
        return data.palette.getRGB(data.getPixel(x, y));
    }
}
