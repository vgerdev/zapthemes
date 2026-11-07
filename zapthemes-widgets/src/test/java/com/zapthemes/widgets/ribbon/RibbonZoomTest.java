package com.zapthemes.widgets.ribbon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.Images;

/** The ribbon follows the zoom: its height, tabs, items, icons and popups scale; the window does not. */
class RibbonZoomTest {

    private static Display display;
    private static ThemeRegistry registry;
    private static Image icon16;
    private static Image icon32;
    private ThemeManager manager;
    private Shell shell;
    private Ribbon ribbon;
    private final List<Integer> asked = new ArrayList<>();

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        PopupShell.closeOnDeactivate = false;
        registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
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
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(registry.resolve("zap-light"));
        shell = new Shell(display);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        shell.setLayout(layout);
        ribbon = new Ribbon(shell, SWT.NONE);
        ribbon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label content = new Label(shell, SWT.NONE);
        content.setText("Document");
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Images images = size -> {
            asked.add(size);
            return size <= 24 ? icon16 : icon32;
        };
        ribbon.commands().add(new Command("home.paste", "&Paste").images(images));
        ribbon.commands().add(new Command("home.cut", "Cu&t").images(images));
        ribbon.commands().add(new Command("home.copy", "&Copy").images(images));
        RibbonTab home = ribbon.addTab("home", "Home");
        RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard");
        clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE);
        clipboard.add("home.cut");
        clipboard.add("home.copy");
        ribbon.addTab("insert", "Insert");
        shell.setSize(1600, 600);
        shell.open();
        settle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
        settle();
    }

    private void settle() {
        for (int i = 0; i < 5; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }

    private void zoom(double zoom) {
        manager.setZoom(zoom);
        settle();
    }

    private Rectangle bounds(Hot.Kind kind, Object target) {
        return ribbon.hots().stream().filter(h -> h.kind() == kind && (target == null || h.target() == target))
                .findFirst().orElseThrow().bounds();
    }

    private Rectangle itemBounds(String id) {
        return ribbon.hots().stream().filter(h -> h.kind() == Hot.Kind.ITEM && h.box().item().command() != null
                && id.equals(h.box().item().command().id())).findFirst().orElseThrow().bounds();
    }

    @Test
    void theRibbonGrowsAndShrinksWithTheZoom() {
        int at100 = ribbon.getSize().y;
        Rectangle tab100 = bounds(Hot.Kind.TAB, ribbon.getSelection());
        Rectangle paste100 = itemBounds("home.paste");
        Rectangle shell100 = shell.getBounds();

        zoom(2);
        int at200 = ribbon.getSize().y;
        Rectangle tab200 = bounds(Hot.Kind.TAB, ribbon.getSelection());
        Rectangle paste200 = itemBounds("home.paste");

        assertThat((double) at200 / at100).isCloseTo(2, within(0.25));
        assertThat((double) tab200.height / tab100.height).isCloseTo(2, within(0.3));
        assertThat((double) tab200.width / tab100.width).isCloseTo(2, within(0.3));
        assertThat((double) paste200.height / paste100.height).isCloseTo(2, within(0.25));
        assertThat(shell.getBounds()).isEqualTo(shell100);

        zoom(0.5);
        assertThat(ribbon.getSize().y).isLessThan(at100 * 3 / 4);
        assertThat(itemBounds("home.paste").height).isLessThan(paste100.height * 3 / 4);

        zoom(1);
        assertThat(ribbon.getSize().y).isEqualTo(at100);
        assertThat(itemBounds("home.paste")).isEqualTo(paste100);
    }

    @Test
    void iconsAreAskedForAtTheZoomedSize() {
        asked.clear();
        ribbon.redraw();
        ribbon.update();
        settle();
        assertThat(asked).contains(16, 32);

        asked.clear();
        zoom(2);
        ribbon.redraw();
        ribbon.update();
        settle();
        assertThat(asked).contains(32, 64).doesNotContain(16);

        asked.clear();
        zoom(0.5);
        ribbon.redraw();
        ribbon.update();
        settle();
        assertThat(asked).doesNotContain(32, 64);
    }

    @Test
    void popupsOpenedWhileZoomedAreZoomed() {
        ribbon.setCollapsed(true);
        settle();
        int tabRow100 = ribbon.getSize().y;
        zoom(2);
        assertThat((double) ribbon.getSize().y / tabRow100).isCloseTo(2, within(0.3));

        ribbon.openOverlay(ribbon.getSelection());
        settle();
        GroupPopup overlay = ribbon.overlay();
        assertThat(overlay).isNotNull();
        int overlay200 = overlay.shell.getSize().y;
        overlay.close();
        settle();

        zoom(1);
        ribbon.openOverlay(ribbon.getSelection());
        settle();
        int overlay100 = ribbon.overlay().shell.getSize().y;
        ribbon.overlay().close();
        settle();
        assertThat((double) overlay200 / overlay100).isCloseTo(2, within(0.3));
    }
    @Test
    void theBackstageAndTheMessageBarFollowTheZoom() {
        ribbon.setFileTab("File", backstage -> backstage.addPage("info", "Info", null, page -> {
            // An empty page.
        }));
        settle();
        Backstage backstage = ribbon.getBackstage();
        backstage.open();
        settle();
        int nav100 = backstage.nav().computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
        int margin100 = ((GridLayout) backstage.page("info").getLayout()).marginWidth;

        zoom(2);
        assertThat(backstage.nav().computeSize(SWT.DEFAULT, SWT.DEFAULT).x).isEqualTo(2 * nav100);
        assertThat(((GridLayout) backstage.page("info").getLayout()).marginWidth).isEqualTo(2 * margin100);
        backstage.close();
        settle();

        zoom(1);
        MessageBar bar = new MessageBar(shell, SWT.NONE, MessageBar.Kind.INFO);
        bar.setTitle("Recovered");
        bar.setMessage("A recovered file");
        int bar100 = bar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
        zoom(2);
        assertThat((double) bar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / bar100).isCloseTo(2, within(0.3));
    }
}
