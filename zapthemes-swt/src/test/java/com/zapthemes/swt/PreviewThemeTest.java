package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.SystemColors;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;

/**
 * {@link ThemeManager#preview}: a subtree themed with a theme of its own, apart from the display's,
 * as a theme editor's preview pane needs.
 */
class PreviewThemeTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private static Theme highContrast;

    private ThemeManager manager;
    private Shell shell;
    private Composite pane;

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
        manager.setTheme(light);
        shell = new Shell(display);
        pane = new Composite(shell, SWT.NONE);
    }

    @AfterEach
    void tearDown() {
        for (var s : display.getShells()) {
            s.dispose();
        }
        manager.setTheme(light);
    }

    @Test
    void onlyThePreviewedSubtreeTakesThePreviewTheme() {
        Label outside = new Label(shell, SWT.NONE);
        Composite inner = new Composite(pane, SWT.NONE);
        Label inside = new Label(inner, SWT.NONE);
        Text text = new Text(inner, SWT.BORDER);
        Table table = new Table(inner, SWT.NONE);
        TableItem item = new TableItem(table, SWT.NONE);

        manager.preview(pane, dark);

        assertThat(rgb(shell.getBackground())).isEqualTo(color(light, "background"));
        assertThat(rgb(outside.getBackground())).isEqualTo(color(light, "background"));
        for (var control : List.of(pane, inner, inside)) {
            assertThat(rgb(control.getBackground())).as(control.toString()).isEqualTo(color(dark, "background"));
        }
        assertThat(rgb(text.getBackground())).isEqualTo(color(dark, "surface"));
        assertThat(rgb(item.getBackground())).as("items too").isEqualTo(color(dark, "surface"));
        assertThat(manager.themeOf(item)).isSameAs(dark);
        assertThat(manager.themeOf(outside)).isSameAs(light);
        assertThat(manager.styleOf(inside).color("background")).map(c -> c.rgb).contains(color(dark, "background"));
        assertThat(manager.previewOf(pane)).isSameAs(dark);
        assertThat(manager.previewOf(inner)).isNull();
    }

    @Test
    void anEditedPreviewRethemesTheSameWidgetsInPlace() {
        Label label = new Label(pane, SWT.NONE);
        Button button = new Button(pane, SWT.PUSH);
        manager.preview(pane, dark);
        List<Object> disposed = new ArrayList<>();
        label.addListener(SWT.Dispose, e -> disposed.add(e.widget));
        button.addListener(SWT.Dispose, e -> disposed.add(e.widget));

        Theme edited = withBackground(dark, "#123456");
        manager.preview(pane, edited);

        assertThat(rgb(label.getBackground())).isEqualTo(new RGB(0x12, 0x34, 0x56));
        assertThat(rgb(pane.getBackground())).isEqualTo(new RGB(0x12, 0x34, 0x56));
        assertThat(disposed).isEmpty();
        assertThat(label.isDisposed()).isFalse();
        assertThat(manager.themeOf(label)).isSameAs(edited);
    }

    @Test
    void widgetsAddedToThePreviewLaterGetItsTheme() {
        manager.preview(pane, dark);

        Label proxy = new Label(pane, SWT.NONE);
        var raw = new org.eclipse.swt.widgets.Label(pane, SWT.NONE); // zapthemes:allow-raw
        var rawOutside = new org.eclipse.swt.widgets.Label(shell, SWT.NONE); // zapthemes:allow-raw
        while (display.readAndDispatch()) {
            // SWT.Skin themes the raw labels.
        }

        assertThat(rgb(proxy.getBackground())).isEqualTo(color(dark, "background"));
        assertThat(rgb(raw.getBackground())).isEqualTo(color(dark, "background"));
        assertThat(rgb(rawOutside.getBackground())).isEqualTo(color(light, "background"));
    }

    @Test
    void theDisplaysThemeSwitchesAroundAPreview() {
        Label outside = new Label(shell, SWT.NONE);
        Label inside = new Label(pane, SWT.NONE);
        manager.preview(pane, light);

        manager.setTheme(dark);

        assertThat(rgb(outside.getBackground())).isEqualTo(color(dark, "background"));
        assertThat(rgb(inside.getBackground())).as("the preview keeps its theme").isEqualTo(color(light, "background"));
        assertThat(manager.getTheme()).isSameAs(dark);
    }

    @Test
    void endingThePreviewGivesTheSubtreeTheDisplaysThemeBack() {
        Label inside = new Label(pane, SWT.NONE);
        manager.preview(pane, dark);

        manager.endPreview(pane);

        assertThat(rgb(inside.getBackground())).isEqualTo(color(light, "background"));
        assertThat(rgb(pane.getBackground())).isEqualTo(color(light, "background"));
        assertThat(manager.previewOf(pane)).isNull();
        assertThat(manager.themeOf(inside)).isSameAs(light);
    }

    @Test
    void aPreviewInsideAPreviewThemesItsOwnSubtree() {
        Composite nested = new Composite(pane, SWT.NONE);
        Label inNested = new Label(nested, SWT.NONE);
        Label inPane = new Label(pane, SWT.NONE);

        manager.preview(pane, dark);
        manager.preview(nested, highContrast);

        assertThat(rgb(inPane.getBackground())).isEqualTo(color(dark, "background"));
        assertThat(rgb(inNested.getBackground())).isEqualTo(color(highContrast, "background"));

        manager.endPreview(nested);
        assertThat(rgb(inNested.getBackground())).isEqualTo(color(dark, "background"));
    }

    @Test
    void disposingThePreviewRootForgetsIt() {
        Label outside = new Label(shell, SWT.NONE);
        manager.preview(pane, dark);

        pane.dispose();

        Composite again = new Composite(shell, SWT.NONE);
        assertThat(manager.themeOf(again)).isSameAs(light);
        assertThat(manager.themeOf(outside)).isSameAs(light);
        assertThat(manager.ambientRegistry(SystemColors.REGISTRY).color("COLOR_WIDGET_BACKGROUND"))
                .map(c -> c.rgb).contains(color(light, "background"));
    }

    @Test
    void aShellCannotBePreviewed() {
        assertThatThrownBy(() -> manager.preview(shell, dark)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void widgetListenersHearOfTheThemeTheirWidgetTakes() {
        Label inside = new Label(pane, SWT.NONE);
        Label outside = new Label(shell, SWT.NONE);
        List<String> heard = new ArrayList<>();
        manager.addThemeChangeListener(inside, (previous, current) -> heard.add("inside " + current.id()));
        manager.addThemeChangeListener(outside, (previous, current) -> heard.add("outside " + current.id()));

        manager.preview(pane, dark);
        assertThat(heard).containsExactly("inside zap-dark");

        heard.clear();
        manager.setTheme(highContrast);
        assertThat(heard).as("the preview did not change").containsExactly("outside zap-high-contrast");

        heard.clear();
        manager.endPreview(pane);
        assertThat(heard).containsExactly("inside zap-high-contrast");

        heard.clear();
        inside.dispose();
        manager.setTheme(light);
        assertThat(heard).as("removed with its widget").containsExactly("outside zap-light");
    }

    @Test
    void systemColorsFollowTheWidgetBeingPainted() {
        Label inside = new Label(pane, SWT.NONE);
        Label outside = new Label(shell, SWT.NONE);
        manager.preview(pane, dark);
        Map<String, RGB> painted = new java.util.HashMap<>();
        inside.addListener(SWT.Paint, e -> painted.put("inside", systemBackground()));
        outside.addListener(SWT.Paint, e -> painted.put("outside", systemBackground()));

        inside.notifyListeners(SWT.Paint, new Event());
        outside.notifyListeners(SWT.Paint, new Event());

        assertThat(painted).containsEntry("inside", color(dark, "background")).containsEntry("outside", color(light, "background"));
    }

    private static RGB systemBackground() {
        Color color = SystemColors.get(display, SWT.COLOR_WIDGET_BACKGROUND);
        return color.getRGB();
    }

    private static Theme withBackground(Theme theme, String hex) {
        var names = Map.of("background", com.google.gson.JsonParser.parseString("{\"color\": \"" + hex + "\"}"));
        return new Theme(Theme.FORMAT_VERSION, theme.id(), theme.name(), null, null, null, names, null, null, null, null,
                null, null).over(theme);
    }

    private static RGB color(Theme theme, String name) {
        return ThemeNames.of(theme).color(name).orElseThrow().rgb;
    }

    private static RGB rgb(Color color) {
        return color.getRGB();
    }
}
