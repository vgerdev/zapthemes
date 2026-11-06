package com.zapthemes.widgets.ribbon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.CustomizationStore;
import com.zapthemes.widgets.command.Images;

class RibbonTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private static Theme highContrast;
    private static Image icon16;
    private static Image icon32;
    private Shell shell;
    private Ribbon ribbon;
    private Label content;
    private final List<String> ran = new ArrayList<>();

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
        highContrast = registry.resolve("zap-high-contrast");
        icon16 = square(16);
        icon32 = square(32);
    }

    @AfterAll
    static void disposeImages() {
        icon16.dispose();
        icon32.dispose();
    }

    private static Image square(int size) {
        Image image = new Image(display, size, size);
        GC gc = new GC(image);
        gc.setBackground(display.getSystemColor(SWT.COLOR_DARK_BLUE));
        gc.fillRectangle(0, 0, size, size);
        gc.dispose();
        return image;
    }

    @BeforeEach
    void setUp() {
        ThemeManager.get(display).setTheme(light);
        shell = new Shell(display);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        shell.setLayout(layout);
        ribbon = new Ribbon(shell, SWT.NONE);
        ribbon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        content = new Label(shell, SWT.NONE);
        content.setText("Document");
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        build();
        shell.setSize(1600, 500);
        shell.open();
        settle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        settle();
    }

    private void settle() {
        for (int i = 0; i < 5; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }

    private Command command(String id, String label) {
        return ribbon.commands().add(new Command(id, label).images(Images.of(icon16, icon32)).category(id.substring(0, id.indexOf('.')))
                .onRun(e -> ran.add(id + (e.value() != null ? "=" + e.value() : ""))));
    }

    private RibbonGallery gallery;
    private RibbonComboBox fontName;
    private RibbonColorButton fontColor;
    private RibbonContextualTabSet table;

    private void build() {
        command("home.paste", "&Paste").style(Command.Style.SPLIT).menu(CommandMenu.of("home.pasteText"));
        command("home.pasteText", "Keep &Text Only");
        command("home.cut", "Cu&t");
        command("home.copy", "&Copy");
        command("home.bold", "&Bold").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'B').description("Make your text bold.");
        command("home.italic", "&Italic").style(Command.Style.TOGGLE);
        command("home.underline", "&Underline").style(Command.Style.TOGGLE);
        command("home.color", "Font &Color");
        command("home.font.dialog", "Font Settings");
        command("home.find", "&Find");
        command("home.replace", "&Replace");
        command("home.select", "S&elect").style(Command.Style.DROP_DOWN).menu(CommandMenu.of("home.replace"));
        command("home.new", "New Style");
        command("insert.table", "&Table");
        command("insert.picture", "&Pictures");
        command("table.headerRow", "&Header Row").style(Command.Style.TOGGLE);
        command("share.share", "Share");

        RibbonTab home = ribbon.addTab("home", "Home").setKeyTip("H");
        RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard");
        clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE).keyTip("V");
        clipboard.add("home.cut");
        clipboard.add("home.copy");
        RibbonGroup font = home.addGroup("home.font", "Font");
        fontName = new RibbonComboBox("home.fontName", "Font").width(120).items("Arial", "Georgia", "Verdana").text("Arial");
        font.row().add(fontName).add("home.bold");
        fontColor = new RibbonColorButton(ribbon.commands().get("home.color")).color(new RGB(255, 0, 0));
        font.row().add("home.italic").add("home.underline").separator().add(fontColor);
        font.setDialogLauncher("home.font.dialog");
        RibbonGroup styles = home.addGroup("home.styles", "Styles");
        gallery = new RibbonGallery("home.gallery", "Styles", (gc, bounds, index, selected, hovered) -> {
            gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
            gc.fillRectangle(bounds);
        }).items("Normal", "No Spacing", "Heading 1", "Heading 2", "Title", "Subtitle", "Quote", "Strong", "Emphasis", "Book Title", "List",
                "Caption").columns(4, 2).popupCommands("home.new");
        styles.add(gallery);
        RibbonGroup editing = home.addGroup("home.editing", "Editing");
        editing.add("home.find");
        editing.add("home.replace");
        editing.add("home.select");

        RibbonTab insert = ribbon.addTab("insert", "Insert").setKeyTip("N");
        insert.addGroup("insert.tables", "Tables").add("insert.table").large();
        insert.addGroup("insert.illustrations", "Illustrations").add("insert.picture").large();

        table = ribbon.addContextualTabSet("table", "Table Tools", 2);
        table.addTab("table.design", "Table Design").addGroup("table.options", "Options").add("table.headerRow");

        ribbon.addRightCommand("share.share", true);
    }

    private void width(int width) {
        shell.setSize(width, 500);
        settle();
    }

    // ------------------------------------------------------------------ layout and reduction

    @Test
    void ribbonIsAThemedType() {
        assertThat(ribbon.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Ribbon");
        assertThat(ribbon.getSelection().id()).isEqualTo("home");
        assertThat(ribbon.showingTabs()).extracting(RibbonTab::id).containsExactly("home", "insert");
    }

    @Test
    void wideRibbonShowsEveryGroupLarge() {
        assertThat(ribbon.groupSizes()).containsEntry("home.clipboard", Size.LARGE).containsEntry("home.styles", Size.LARGE)
                .containsEntry("home.editing", Size.LARGE);
    }

    @Test
    void groupsShrinkStepByStepAsTheRibbonNarrows() {
        Map<String, Size> previous = ribbon.groupSizes();
        boolean sawMedium = false;
        boolean sawSmall = false;
        boolean sawCollapsed = false;
        for (int w = 1600; w >= 200; w -= 40) {
            width(w);
            Map<String, Size> sizes = ribbon.groupSizes();
            for (Map.Entry<String, Size> entry : sizes.entrySet()) {
                assertThat(entry.getValue()).as(entry.getKey() + " at " + w).isGreaterThanOrEqualTo(previous.get(entry.getKey()));
                sawMedium |= entry.getValue() == Size.MEDIUM;
                sawSmall |= entry.getValue() == Size.SMALL;
                sawCollapsed |= entry.getValue() == Size.COLLAPSED;
            }
            previous = sizes;
        }
        assertThat(sawMedium).isTrue();
        assertThat(sawSmall).isTrue();
        assertThat(sawCollapsed).isTrue();
        assertThat(previous.values()).allMatch(s -> s.compareTo(Size.SMALL) >= 0);
        assertThat(previous).containsEntry("home.font", Size.COLLAPSED);
    }

    @Test
    void layoutFitsWheneverItCan() {
        for (int w : new int[] { 1200, 900, 700, 500 }) {
            width(w);
            int right = ribbon.hots().stream().filter(h -> h.kind() == Hot.Kind.GROUP).mapToInt(h -> h.bounds().x + h.bounds().width).max()
                    .orElse(0);
            int collapse = ribbon.hotsOf(Hot.Kind.COLLAPSE).getFirst().bounds().x;
            boolean scrolls = !ribbon.hotsOf(Hot.Kind.SCROLL_RIGHT).isEmpty();
            assertThat(right <= collapse || scrolls).as("fits or scrolls at " + w).isTrue();
        }
    }

    @Test
    void lowerPriorityShrinksFirstAndMinimumSizesHold() {
        RibbonTab review = ribbon.addTab("review", "Review");
        RibbonGroup left = review.addGroup("review.left", "Left");
        RibbonGroup right = review.addGroup("review.right", "Right");
        for (String id : new String[] { "home.cut", "home.copy", "home.find" }) {
            left.add(new RibbonButton(ribbon.commands().get(id))).large();
        }
        for (String id : new String[] { "home.replace", "insert.picture", "insert.table" }) {
            right.add(new RibbonButton(ribbon.commands().get(id))).large();
        }
        int full = ribbon.measure(m -> RibbonLayout.width(left, Size.LARGE, m) + RibbonLayout.width(right, Size.LARGE, m) + m.groupGap);
        Map<RibbonGroup, Size> sizes = ribbon.measure(m -> RibbonLayout.reduce(review, full - 2, m));
        assertThat(sizes).as("right to left by default").containsEntry(left, Size.LARGE).containsEntry(right, Size.MEDIUM);
        left.setPriority(-1);
        sizes = ribbon.measure(m -> RibbonLayout.reduce(review, full - 2, m));
        assertThat(sizes).as("lower priority first").containsEntry(left, Size.MEDIUM).containsEntry(right, Size.LARGE);
        left.setSizes(Size.LARGE, Size.MEDIUM);
        sizes = ribbon.measure(m -> RibbonLayout.reduce(review, 10, m));
        assertThat(sizes).containsEntry(left, Size.MEDIUM).containsEntry(right, Size.COLLAPSED);
    }

    @Test
    void appReductionOrderComesFirst() {
        RibbonTab home = ribbon.getSelection();
        home.setReductionOrder(RibbonTab.step(home.group("home.clipboard"), Size.COLLAPSED));
        width(narrowestAllLarge() - 10);
        Map<String, Size> sizes = ribbon.groupSizes();
        assertThat(sizes.get("home.clipboard")).isEqualTo(Size.COLLAPSED);
        assertThat(sizes.get("home.styles")).isEqualTo(Size.LARGE);
    }

    private int narrowestAllLarge() {
        int w = 1600;
        while (w > 200) {
            width(w - 10);
            if (!ribbon.groupSizes().values().stream().allMatch(s -> s == Size.LARGE)) {
                return w;
            }
            w -= 10;
        }
        return w;
    }

    @Test
    void itemsResolveTheirOwnLimits() {
        width(200);
        RibbonGroup clipboard = ribbon.getSelection().group("home.clipboard");
        assertThat(clipboard.items().getFirst().resolve(Size.SMALL)).isEqualTo(Size.LARGE);
        assertThat(clipboard.items().get(1).resolve(Size.LARGE)).isEqualTo(Size.MEDIUM);
        assertThat(clipboard.items().get(1).resolve(Size.SMALL)).isEqualTo(Size.SMALL);
    }

    @Test
    void layoutIsAPureFunctionOfTheWidth() {
        RibbonTab home = ribbon.getSelection();
        Map<RibbonGroup, Size> a = ribbon.measure(m -> RibbonLayout.reduce(home, 700, m));
        Map<RibbonGroup, Size> b = ribbon.measure(m -> RibbonLayout.reduce(home, 700, m));
        assertThat(a).isEqualTo(b);
        int wide = ribbon.measure(m -> RibbonLayout.width(home.group("home.font"), Size.LARGE, m));
        int collapsed = ribbon.measure(m -> RibbonLayout.width(home.group("home.font"), Size.COLLAPSED, m));
        assertThat(collapsed).isLessThan(wide);
    }

    @Test
    void collapsedGroupOpensAsAPopup() {
        width(200);
        Hot button = ribbon.hotsOf(Hot.Kind.GROUP_BUTTON).getFirst();
        ribbon.activate(button, false, null);
        settle();
        assertThat(ribbon.popups()).hasSize(1);
        GroupPopup popup = (GroupPopup) ribbon.popups().getFirst();
        assertThat(popup.surface().hotsOf(Hot.Kind.ITEM)).isNotEmpty();
        assertThat(popup.surface().getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("RibbonPopup");
        Hot item = popup.surface().hotsOf(Hot.Kind.ITEM).stream().filter(h -> h.box().item().command() != null
                && h.box().item().command().style() == Command.Style.PUSH).findFirst().orElseThrow();
        popup.surface().activate(item, false, null);
        assertThat(ran).hasSize(1);
        assertThat(ribbon.popups()).isEmpty();
    }

    // ------------------------------------------------------------------ commands

    @Test
    void togglesFollowAndFlipTheirCommand() {
        Command bold = ribbon.commands().get("home.bold");
        Hot hot = ribbon.hotOf(ribbon.getSelection().group("home.font").items().get(1));
        ribbon.activate(hot, false, null);
        assertThat(bold.isChecked()).isTrue();
        assertThat(ran).containsExactly("home.bold");
        bold.setEnabled(false);
        ribbon.activate(hot, false, null);
        assertThat(ran).hasSize(1);
        assertThat(((RibbonButton) hot.box().item()).isEnabled()).isFalse();
    }

    @Test
    void commandChangesRelayoutTheRibbon() {
        Command cut = ribbon.commands().get("home.cut");
        int before = ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().get(1)).bounds().width;
        cut.label("Cut the selection out of the document");
        int after = ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().get(1)).bounds().width;
        assertThat(after).isGreaterThan(before);
        cut.setVisible(false);
        assertThat(ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().get(1))).isNull();
    }

    @Test
    void splitButtonsRunTheirMainPart() {
        Hot paste = ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().getFirst());
        assertThat(paste.arrow()).isNotNull();
        assertThat(paste.arrow().y).isGreaterThan(paste.bounds().y);
        ribbon.activate(paste, false, null);
        assertThat(ran).containsExactly("home.paste");
    }

    @Test
    void colorButtonsCarryTheirColor() {
        Hot hot = ribbon.hotOf(fontColor);
        ribbon.activate(hot, false, null);
        assertThat(ran).containsExactly("home.color=RGB {255, 0, 0}");
        ribbon.activate(hot, true, null);
        settle();
        assertThat(ribbon.popups()).hasSize(1).first().isInstanceOf(ColorPalettePopup.class);
        ColorPalettePopup palette = (ColorPalettePopup) ribbon.popups().getFirst();
        palette.palette().choose(0);
        assertThat(fontColor.color()).isEqualTo(ColorPalettePopup.colors().getFirst());
        assertThat(ribbon.popups()).isEmpty();
        assertThat(ran).hasSize(2);
    }

    @Test
    void comboBoxesEditAndPick() {
        List<String> values = new ArrayList<>();
        fontName.onChange(values::add);
        Hot hot = ribbon.hotOf(fontName);
        ribbon.activate(hot, false, null);
        assertThat(ribbon.editor()).isNotNull();
        ribbon.editor().setText("Tahoma");
        ribbon.editor().notifyListeners(SWT.DefaultSelection, new Event());
        assertThat(values).containsExactly("Tahoma");
        assertThat(ribbon.editor()).isNull();
        ribbon.activate(hot, true, null);
        settle();
        ComboListPopup list = (ComboListPopup) ribbon.popups().getFirst();
        list.list().select(2);
        list.commit();
        assertThat(values).containsExactly("Tahoma", "Verdana");
        assertThat(fontName.text()).isEqualTo("Verdana");
    }

    @Test
    void sharedRegistryIsPerShell() {
        assertThat(CommandRegistry.of(shell)).isSameAs(ribbon.commands());
        assertThat(ribbon.commands().categories()).contains("home", "insert", "table");
    }

    // ------------------------------------------------------------------ galleries

    @Test
    void galleryScrollsAndSelects() {
        List<Integer> picked = new ArrayList<>();
        gallery.onSelect(picked::add);
        Hot down = ribbon.hotsOf(Hot.Kind.GALLERY_DOWN).getFirst();
        int firstCell = ribbon.hotsOf(Hot.Kind.GALLERY_CELL).getFirst().index();
        ribbon.activate(down, false, null);
        assertThat(gallery.firstRow()).isEqualTo(1);
        int shifted = ribbon.hotsOf(Hot.Kind.GALLERY_CELL).getFirst().index();
        assertThat(shifted).isEqualTo(firstCell + gallery.columns(Size.LARGE));
        for (int i = 0; i < 10; i++) {
            ribbon.activate(ribbon.hotsOf(Hot.Kind.GALLERY_DOWN).getFirst(), false, null);
        }
        int rows = ribbon.measure(gallery::rows);
        assertThat(gallery.firstRow()).isEqualTo(gallery.rowCount(Size.LARGE) - rows);
        ribbon.activate(ribbon.hotsOf(Hot.Kind.GALLERY_UP).getFirst(), false, null);
        assertThat(gallery.firstRow()).isEqualTo(gallery.rowCount(Size.LARGE) - rows - 1);

        Hot cell = ribbon.hotsOf(Hot.Kind.GALLERY_CELL).get(1);
        ribbon.activate(cell, false, null);
        assertThat(gallery.selection()).isEqualTo(cell.index());
        assertThat(picked).containsExactly(cell.index());
    }

    @Test
    void galleryExpandsIntoAPopupGrid() {
        ribbon.activate(ribbon.hotsOf(Hot.Kind.GALLERY_MORE).getFirst(), false, null);
        settle();
        GalleryPopup popup = (GalleryPopup) ribbon.popups().getFirst();
        assertThat(popup.grid().rowCount()).isEqualTo(3);
        popup.grid().choose(5);
        assertThat(gallery.selection()).isEqualTo(5);
        assertThat(ribbon.popups()).isEmpty();
        ribbon.activate(ribbon.hotsOf(Hot.Kind.GALLERY_MORE).getFirst(), false, null);
        settle();
        ((GalleryPopup) ribbon.popups().getFirst()).grid().choose(gallery.itemCount());
        assertThat(ran).containsExactly("home.new");
    }

    @Test
    void galleryBecomesADropDownWhenSmall() {
        width(200);
        ribbon.getSelection().group("home.styles").setSizes(Size.LARGE, Size.SMALL);
        width(200);
        assertThat(ribbon.groupSizes().get("home.styles")).isEqualTo(Size.SMALL);
        assertThat(ribbon.hotsOf(Hot.Kind.GALLERY_CELL)).isEmpty();
        ribbon.activate(ribbon.hotOf(gallery), false, null);
        settle();
        assertThat(ribbon.popups()).hasSize(1).first().isInstanceOf(GalleryPopup.class);
    }

    // ------------------------------------------------------------------ KeyTips

    @Test
    void keyTipsWalkFromTabsToItems() {
        ribbon.showKeyTips();
        assertThat(ribbon.isShowingKeyTips()).isTrue();
        assertThat(ribbon.visibleKeyTips()).containsEntry("Home", "H").containsEntry("Insert", "N").containsKey("Share");
        ribbon.typeKeyTips("H");
        assertThat(ribbon.keyTips().level()).isEqualTo(KeyTips.Level.PANEL);
        Map<String, String> tips = ribbon.visibleKeyTips();
        assertThat(tips).containsEntry("Paste", "V");
        assertThat(tips.values()).doesNotHaveDuplicates();
        ribbon.typeKeyTips(tips.get("Bold"));
        assertThat(ran).containsExactly("home.bold");
        assertThat(ribbon.isShowingKeyTips()).isFalse();
    }

    @Test
    void escapeGoesBackALevel() {
        ribbon.showKeyTips();
        ribbon.typeKeyTips("N");
        assertThat(ribbon.getSelection().id()).isEqualTo("insert");
        assertThat(ribbon.keyTips().back()).isTrue();
        assertThat(ribbon.keyTips().level()).isEqualTo(KeyTips.Level.TOP);
        assertThat(ribbon.keyTips().back()).isTrue();
        assertThat(ribbon.isShowingKeyTips()).isFalse();
        assertThat(ribbon.keyTips().back()).isFalse();
    }

    @Test
    void twoLetterKeyTipsFilterOnTheFirstLetter() {
        Command a = command("home.alpha", "Alpha");
        Command b = command("home.another", "Another");
        RibbonGroup group = ribbon.getSelection().addGroup("home.extra", "Extra");
        group.add(a).keyTip("AA");
        group.add(b).keyTip("AB");
        ribbon.showKeyTips();
        ribbon.typeKeyTips("H");
        ribbon.typeKeyTips("A");
        assertThat(ribbon.visibleKeyTips()).containsOnlyKeys("Alpha", "Another");
        ribbon.typeKeyTips("B");
        assertThat(ran).containsExactly("home.another");
    }

    @Test
    void keyTipAssignmentIsDeterministicAndUnique() {
        java.util.Set<String> used = new java.util.HashSet<>();
        List<String> tips = new ArrayList<>();
        for (String label : new String[] { "Cut", "Copy", "Clear", "Center", "Color", "Columns", "Comments" }) {
            String tip = KeyTips.pick(label, used);
            used.add(tip);
            tips.add(tip);
        }
        assertThat(tips).doesNotHaveDuplicates();
        assertThat(tips.getFirst()).isEqualTo("C");
        assertThat(tips.get(1)).isEqualTo("O");
        for (String tip : tips) {
            if (tip.length() == 2) {
                assertThat(used).doesNotContain(tip.substring(0, 1));
            }
        }
    }

    @Test
    void keyTipsOpenCollapsedGroups() {
        width(200);
        ribbon.showKeyTips();
        ribbon.typeKeyTips("H");
        String font = ribbon.visibleKeyTips().get("Font");
        assertThat(font).isNotNull();
        ribbon.typeKeyTips(font);
        settle();
        assertThat(ribbon.keyTips().level()).isEqualTo(KeyTips.Level.POPUP);
        assertThat(ribbon.visibleKeyTips()).containsKey("Bold");
        ribbon.keyTips().back();
        assertThat(ribbon.keyTips().level()).isEqualTo(KeyTips.Level.PANEL);
        assertThat(ribbon.popups()).isEmpty();
    }

    @Test
    void accessibilityPathsNameTheKeys() {
        Hot paste = ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().getFirst());
        assertThat(ribbon.keyTips().pathOf(ribbon, paste)).isEqualTo("Alt, H, V");
    }

    // ------------------------------------------------------------------ collapse and pin

    @Test
    void collapseLeavesTheTabRowAndGivesTheSpaceBack() {
        int expanded = ribbon.getSize().y;
        int contentTop = content.getBounds().y;
        ribbon.setCollapsed(true);
        settle();
        assertThat(ribbon.getSize().y).isLessThan(expanded);
        assertThat(content.getBounds().y).isLessThan(contentTop);
        assertThat(ribbon.groupSizes()).isEmpty();
        ribbon.setCollapsed(false);
        settle();
        assertThat(ribbon.getSize().y).isEqualTo(expanded);
    }

    @Test
    void collapsedTabsShowAsAnOverlayThatPins() {
        ribbon.setCollapsed(true);
        settle();
        Hot insert = ribbon.hotsOf(Hot.Kind.TAB).get(1);
        ribbon.activate(insert, false, null);
        settle();
        assertThat(ribbon.isOverlayOpen()).isTrue();
        assertThat(ribbon.getSelection().id()).isEqualTo("insert");
        PanelSurface overlay = ribbon.overlay().surface();
        assertThat(overlay.hotsOf(Hot.Kind.ITEM)).isNotEmpty();
        overlay.activate(overlay.hotsOf(Hot.Kind.ITEM).getFirst(), false, null);
        assertThat(ran).containsExactly("insert.table");
        assertThat(ribbon.isOverlayOpen()).isFalse();
        assertThat(ribbon.isCollapsed()).isTrue();

        ribbon.activate(ribbon.hotsOf(Hot.Kind.TAB).get(1), false, null);
        settle();
        PanelSurface again = ribbon.overlay().surface();
        again.activate(again.hotsOf(Hot.Kind.PIN).getFirst(), false, null);
        assertThat(ribbon.isCollapsed()).isFalse();
        assertThat(ribbon.isOverlayOpen()).isFalse();
    }

    @Test
    void ctrlF1AltAndDoubleClickWork() {
        ribbon.filter(key(SWT.KeyDown, SWT.F1, SWT.CTRL));
        assertThat(ribbon.isCollapsed()).isTrue();
        ribbon.tabDoubleClicked(ribbon.hotsOf(Hot.Kind.TAB).getFirst());
        assertThat(ribbon.isCollapsed()).isFalse();
        ribbon.filter(key(SWT.KeyDown, SWT.ALT, 0));
        ribbon.filter(key(SWT.KeyUp, SWT.ALT, SWT.ALT));
        assertThat(ribbon.isShowingKeyTips()).isTrue();
        ribbon.filter(key(SWT.KeyDown, SWT.F10, 0));
        assertThat(ribbon.isShowingKeyTips()).isFalse();
        ribbon.filter(key(SWT.KeyDown, SWT.ALT, 0));
        ribbon.filter(key(SWT.KeyDown, 'f', SWT.ALT));
        ribbon.filter(key(SWT.KeyUp, SWT.ALT, SWT.ALT));
        assertThat(ribbon.isShowingKeyTips()).as("Alt+F is a shortcut, not Alt alone").isFalse();
    }

    private Event key(int type, int keyCode, int stateMask) {
        Event event = new Event();
        event.type = type;
        event.keyCode = keyCode;
        event.stateMask = stateMask;
        event.widget = content;
        event.doit = true;
        return event;
    }

    // ------------------------------------------------------------------ contextual tabs

    @Test
    void contextualTabsShowAndHide() {
        table.setVisible(true);
        assertThat(ribbon.showingTabs()).extracting(RibbonTab::id).containsExactly("home", "insert", "table.design");
        assertThat(ribbon.getSelection().id()).isEqualTo("home");
        table.setVisible(false);
        table.setVisible(true, true);
        assertThat(ribbon.getSelection().id()).isEqualTo("table.design");
        assertThat(ribbon.nameOf(ribbon.hotsOf(Hot.Kind.TAB).get(2))).isEqualTo("Table Design (Table Tools)");
        table.setVisible(false);
        assertThat(ribbon.getSelection().id()).isEqualTo("home");
        assertThat(ribbon.showingTabs()).hasSize(2);
    }

    @Test
    void hiddenTabsAreSkipped() {
        ribbon.tab("home").setHidden(true);
        assertThat(ribbon.getSelection().id()).isEqualTo("insert");
        assertThat(ribbon.showingTabs()).extracting(RibbonTab::id).containsExactly("insert");
    }

    // ------------------------------------------------------------------ backstage and message bar

    @Test
    void backstageCoversTheWindowAndGivesItBack() {
        List<String> built = new ArrayList<>();
        command("file.save", "&Save");
        ribbon.setFileTab("File", backstage -> {
            backstage.addPage("info", "Info", null, page -> built.add("info"));
            backstage.addPage("open", "Open", null, page -> built.add("open"));
            backstage.addCommand("file.save");
        });
        settle();
        ribbon.activate(ribbon.hotsOf(Hot.Kind.FILE_TAB).getFirst(), false, null);
        settle();
        Backstage backstage = ribbon.getBackstage();
        assertThat(backstage.isOpen()).isTrue();
        assertThat(backstage.getBounds()).isEqualTo(shell.getClientArea());
        assertThat(ribbon.isVisible()).isFalse();
        assertThat(built).containsExactly("info");
        backstage.select("open");
        assertThat(built).containsExactly("info", "open");
        assertThat(backstage.page("open")).isNotNull();
        backstage.close();
        settle();
        assertThat(ribbon.isVisible()).isTrue();
        assertThat(content.isVisible()).isTrue();
        assertThat(shell.getLayout()).isInstanceOf(GridLayout.class);
        backstage.open();
        backstage.runEntry(ribbon.commands().get("file.save"));
        assertThat(backstage.isOpen()).isFalse();
        assertThat(ran).containsExactly("file.save");
    }

    @Test
    void messageBarIsThemedPerKind() {
        MessageBar bar = new MessageBar(shell, SWT.NONE, MessageBar.Kind.WARNING);
        bar.setTitle("Recovered");
        bar.setMessage("A recovered file");
        boolean[] saved = { false };
        bar.addButton("Save", () -> saved[0] = true).notifyListeners(SWT.Selection, new Event());
        assertThat(saved[0]).isTrue();
        assertThat(bar.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("RibbonMessageBar");
        RGB warning = Themed.of(bar, "warning").color("background", null).getRGB();
        RGB error = Themed.of(bar, "error").color("background", null).getRGB();
        assertThat(warning).isNotEqualTo(error);
    }

    // ------------------------------------------------------------------ customization

    @Test
    void customizationRoundTripsThroughTheStoreFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("prefs.json");
        ribbon.setCustomizationStore(CustomizationStore.open(file));
        RibbonCustomization customization = ribbon.customization();
        List<RibbonCustomization.TabNode> state = RibbonCustomization.copy(customization.snapshot());
        assertThat(state).isEqualTo(customization.defaults());

        state.getFirst().label = "Start";
        state.get(1).hidden = true;
        RibbonCustomization.GroupNode clipboard = state.getFirst().groups.getFirst();
        clipboard.items = new ArrayList<>(List.of("home.copy", "home.paste"));
        RibbonCustomization.TabNode mine = new RibbonCustomization.TabNode("custom.tab.1", "Mine", false, true);
        mine.groups.add(new RibbonCustomization.GroupNode("custom.group.1", "Favorites", true, List.of("home.bold", "insert.table")));
        state.add(0, mine);
        state.getFirst().groups.add(new RibbonCustomization.GroupNode("home.editing", null, false, null));
        state.get(1).groups.removeIf(g -> g.id.equals("home.editing"));
        customization.apply(state);
        ribbon.setCollapsed(true);

        assertThat(ribbon.tabs()).extracting(RibbonTab::id).containsExactly("custom.tab.1", "home", "insert");
        assertThat(ribbon.tab("home").label()).isEqualTo("Start");
        assertThat(ribbon.tab("insert").isHidden()).isTrue();
        assertThat(ribbon.tab("custom.tab.1").groups()).extracting(RibbonGroup::id).containsExactly("custom.group.1", "home.editing");
        assertThat(ribbon.tab("home").group("home.clipboard").items()).extracting(RibbonItem::id).containsExactly("home.copy", "home.paste");
        assertThat(Files.readString(file)).contains("\"ribbon\"", "custom.tab.1", "\"collapsed\": true");
        List<RibbonCustomization.TabNode> saved = customization.snapshot();

        // A new window of the same app, on the same file.
        shell.dispose();
        settle();
        setUp();
        ribbon.setCustomizationStore(CustomizationStore.open(file));
        assertThat(ribbon.customization().snapshot()).isEqualTo(saved);
        assertThat(ribbon.isCollapsed()).isTrue();
        assertThat(ribbon.tab("custom.tab.1").groups().getFirst().items()).extracting(RibbonItem::id).containsExactly("home.bold",
                "insert.table");

        ribbon.customization().reset();
        assertThat(ribbon.customization().snapshot()).isEqualTo(ribbon.customization().defaults());
        assertThat(ribbon.tab("home").group("home.font").elements()).hasSize(2);
        assertThat(CustomizationStore.open(file).section(RibbonCustomization.SECTION).getAsJsonObject().get("tabs").getAsJsonArray()).hasSize(2);
    }

    @Test
    void storedStateSurvivesAppUpdates(@TempDir Path dir) {
        Path file = dir.resolve("prefs.json");
        ribbon.setCustomizationStore(CustomizationStore.open(file));
        List<RibbonCustomization.TabNode> state = RibbonCustomization.copy(ribbon.customization().snapshot());
        state.getFirst().groups.getFirst().items = new ArrayList<>(List.of("home.cut", "gone.command"));
        ribbon.customization().apply(state);

        // The next version of the app adds a tab and a group, and drops a command.
        shell.dispose();
        settle();
        setUp();
        command("view.zoom", "Zoom");
        ribbon.addTab("view", "View").addGroup("view.zoom", "Zoom").add("view.zoom");
        ribbon.tab("home").addGroup("home.voice", "Voice").add("insert.picture");
        ribbon.setCustomizationStore(CustomizationStore.open(file));
        assertThat(ribbon.tabs()).extracting(RibbonTab::id).containsExactly("home", "insert", "view");
        assertThat(ribbon.tab("home").groups()).extracting(RibbonGroup::id).endsWith("home.voice");
        assertThat(ribbon.tab("home").group("home.clipboard").items()).extracting(RibbonItem::id).containsExactly("home.cut");
    }

    @Test
    void customizeDialogEditsAndSaves(@TempDir Path dir) {
        Path file = dir.resolve("prefs.json");
        ribbon.setCustomizationStore(CustomizationStore.open(file));
        CustomizeRibbonDialog dialog = new CustomizeRibbonDialog(ribbon);
        dialog.create();
        dialog.selectNode("home");
        dialog.newTab();
        dialog.selectCommand("home.bold");
        dialog.add();
        dialog.rename("My Group");
        dialog.selectNode("insert");
        dialog.move(-1);
        dialog.ok();
        assertThat(ribbon.tabs()).extracting(RibbonTab::label).containsExactly("Home", "Insert", "New Tab");
        assertThat(ribbon.tab("custom.tab.1").groups().getFirst().items()).extracting(RibbonItem::id).containsExactly("home.bold");
        assertThat(CustomizationStore.open(file).section(RibbonCustomization.SECTION).toString()).contains("custom.tab.1");
    }

    // ------------------------------------------------------------------ theming and accessibility

    @Test
    void themeSwitchRecolorsLive() {
        RGB lightBackground = ribbon.colors().background.getRGB();
        ThemeManager.get(display).setTheme(dark);
        settle();
        RGB darkBackground = ribbon.colors().background.getRGB();
        assertThat(darkBackground).isNotEqualTo(lightBackground);
        assertThat(darkBackground).isEqualTo(ThemeManager.get(display).styleOf(ribbon).color("background").orElseThrow().rgb);
        assertThat(ribbon.hotsOf(Hot.Kind.ITEM)).isNotEmpty();
    }

    @Test
    void appAccentAppliesUnlessTheThemeRefuses() {
        RGB green = new RGB(0x10, 0x7C, 0x41);
        ribbon.setAccentColor(green);
        assertThat(ribbon.colors().fileTab.getRGB()).isEqualTo(green);
        ThemeManager.get(display).setTheme(highContrast);
        settle();
        assertThat(ribbon.colors().fileTab.getRGB()).isNotEqualTo(green);
    }

    @Test
    void everyPartIsAccessible() {
        int[] count = { 0 };
        org.eclipse.swt.accessibility.AccessibleControlEvent e = new org.eclipse.swt.accessibility.AccessibleControlEvent(ribbon);
        List<Hot> hots = ribbon.hots();
        assertThat(hots).isNotEmpty();
        for (Hot hot : hots) {
            assertThat(ribbon.nameOf(hot)).as(hot.kind() + " " + hot.target()).isNotNull();
            count[0]++;
        }
        Hot bold = ribbon.hotOf(ribbon.getSelection().group("home.font").items().get(1));
        assertThat(ribbon.descriptionOf(bold)).isEqualTo("Make your text bold.");
        assertThat(bold.box().item().accessibleRole()).isEqualTo(ACC.ROLE_CHECKBUTTON);
        assertThat(ribbon.hotOf(fontName).box().item().accessibleRole()).isEqualTo(ACC.ROLE_COMBOBOX);
        assertThat(ribbon.toolTip().title(bold)).isEqualTo("Bold (" + Command.acceleratorText(SWT.MOD1 | 'B') + ")");
        assertThat(e).isNotNull();
    }

    @Test
    void keyboardMovesThroughTabsAndItems() {
        ribbon.setFocus();
        settle();
        assertThat(ribbon.focusHot().kind()).isEqualTo(Hot.Kind.TAB);
        ribbon.moveFocus(1);
        assertThat(ribbon.focusHot().target()).isEqualTo(ribbon.tab("insert"));
        assertThat(ribbon.getSelection().id()).isEqualTo("insert");
        ribbon.moveFocus(-1);
        ribbon.arrowDown(new Event());
        assertThat(ribbon.focusHot().inTabRow()).isFalse();
        ribbon.escape();
        assertThat(ribbon.focusHot().kind()).isEqualTo(Hot.Kind.TAB);
    }

    @Test
    void commandRunsCarryTheirWidget() {
        Command copy = ribbon.commands().get("home.copy");
        CommandEvent[] seen = { null };
        copy.onRun(e -> seen[0] = e);
        ribbon.activate(ribbon.hotOf(ribbon.getSelection().group("home.clipboard").items().get(2)), false, null);
        assertThat(seen[0].widget()).isSameAs(ribbon);
        assertThat(seen[0].command()).isSameAs(copy);
    }
}
