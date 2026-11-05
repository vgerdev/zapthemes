package com.zapthemes.jface.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.notifications.NotificationPopup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/**
 * The forked notification popup is built from proxies and takes its colors from the theme's
 * {@code notification} and {@code popup} classes instead of upstream's white content and
 * dark-shadow title, also after a theme switch.
 */
class NotificationPopupThemingTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(dark);
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        shell.open();
    }

    @AfterEach
    void tearDown() {
        for (Shell open : display.getShells()) {
            open.dispose();
        }
        manager.setTheme(dark);
    }

    @Test
    void popupTakesItsColorsFromTheThemeAcrossSwitches() {
        Shell popup = open(NotificationPopup.forShell(shell).title("Build finished", true).text("All tests passed").delay(0));

        assertThemed(popup, dark);
        manager.setTheme(light);
        assertThemed(popup, light);
    }

    @Test
    void everyControlIsAProxyAndTagged() {
        Shell popup = open(NotificationPopup.forShell(shell).title("Build finished", true).text("All tests passed").delay(0));

        List<Control> all = findAll(popup, Control.class);
        assertThat(all).hasSizeGreaterThan(5);
        assertThat(all).allSatisfy(c -> assertThat(c.getClass().getName()).startsWith("com.zapthemes."));
    }

    @Test
    void defaultTitleAreaHasTheCloseIcon() {
        Shell popup = open(NotificationPopup.forDisplay(display).text("No parent shell").delay(0));

        List<Label> withImage = findAll(popup, Label.class).stream().filter(l -> l.getImage() != null).toList();
        assertThat(withImage).as("close button image (icons found outside Eclipse)").isNotEmpty();
        Label title = findAll(popup, Label.class).stream().filter(l -> "Notification".equals(l.getText())).findFirst().orElseThrow();
        assertThat(title.getForeground().getRGB()).isEqualTo(palette(dark, "fg"));
    }

    @Test
    void withoutAThemeTheContentStaysUpstreamWhite() {
        manager.setTheme(Theme.EMPTY);
        Shell popup = open(NotificationPopup.forShell(shell).text("Plain").delay(0));

        Label text = content(popup);
        assertThat(text.getParent().getBackground().getRGB()).isEqualTo(new RGB(255, 255, 255));
        assertThat(popup.getData(ThemeKeys.THEME_CLASS)).isEqualTo(NotificationTheming.NOTIFICATION);
    }

    private void assertThemed(Shell popup, Theme theme) {
        assertThat(popup.getBackground().getRGB()).as("frame").isEqualTo(palette(theme, "border"));
        Label text = content(popup);
        assertThat(text.getBackground().getRGB()).as("content").isEqualTo(palette(theme, "surface"));
        assertThat(text.getParent().getBackground().getRGB()).as("content area").isEqualTo(palette(theme, "surface"));
        assertThat(text.getForeground().getRGB()).as("content text").isEqualTo(palette(theme, "fg"));
        Label title = findAll(popup, Label.class).stream().filter(l -> "Build finished".equals(l.getText())).findFirst().orElseThrow();
        assertThat(title.getForeground().getRGB()).as("title").isEqualTo(palette(theme, "fg"));
        assertThat(title.getBackground().getRGB()).as("title area").isEqualTo(palette(theme, "bg"));
    }

    private static Label content(Shell popup) {
        return findAll(popup, Label.class).stream()
                .filter(l -> l.getText().equals("All tests passed") || l.getText().equals("Plain"))
                .findFirst().orElseThrow();
    }

    private static Shell open(NotificationPopup.Builder builder) {
        Set<Shell> before = Set.of(display.getShells());
        builder.open();
        dispatch();
        for (Shell open : display.getShells()) {
            if (!before.contains(open)) {
                return open;
            }
        }
        throw new AssertionError("no notification shell");
    }

    private static RGB palette(Theme theme, String name) {
        String hex = theme.palette().get(name).getAsString().substring(1);
        int value = Integer.parseInt(hex, 16);
        return new RGB(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF);
    }

    private static <T> List<T> findAll(Control root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                found.addAll(findAll(child, type));
            }
        }
        return found;
    }

    private static void dispatch() {
        long end = System.currentTimeMillis() + 200;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }
}
