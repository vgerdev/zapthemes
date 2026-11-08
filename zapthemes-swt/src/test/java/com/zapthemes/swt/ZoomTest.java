package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ZoomListener;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Text;

/** The Chrome-style zoom of {@link ThemeManager}: theme fonts, steps, listeners and shortcuts. */
class ZoomTest {

    private static final Theme FONTS = theme("zoom-fonts", """
            "Label": { "font": { "name": "Arial", "height": 10 } },
            "Button": { "font": { "style": "bold" } },
            "Text": { "font": { "name": "Courier New", "height": 12, "style": "italic" } }
            """);
    private static final Theme OTHER = theme("zoom-other", """
            "Label": { "font": { "name": "Arial", "height": 8 } }
            """);
    private static final Theme PLAIN = theme("zoom-plain", "");

    private final Display display = Display.getDefault();
    private ThemeManager manager;
    private Shell shell;
    private Label label;
    private Button button;
    private Text text;

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(FONTS);
        shell = new Shell(display);
        label = new Label(shell, SWT.NONE);
        button = new Button(shell, SWT.PUSH);
        text = new Text(shell, SWT.BORDER);
    }

    @AfterEach
    void tearDown() {
        manager.setZoomShortcutsEnabled(true);
        manager.resetZoom();
        manager.setTheme(PLAIN);
        for (var s : display.getShells()) {
            s.dispose();
        }
    }

    @Test
    void zoomScalesThemeFontsIncludingFontsWithoutHeight() {
        int system = systemHeight();

        manager.setZoom(1.5);

        assertThat(font(label)).extracting(FontData::getName, FontData::getHeight, FontData::getStyle)
                .containsExactly("Arial", 15, SWT.NORMAL);
        assertThat(font(button).getHeight()).isEqualTo((int) Math.round(system * 1.5));
        assertThat(font(button).getStyle()).isEqualTo(SWT.BOLD);
        assertThat(font(text)).extracting(FontData::getName, FontData::getHeight, FontData::getStyle)
                .containsExactly("Courier New", 18, SWT.ITALIC);
    }

    @Test
    void controlsWithoutThemeFontFollowTheZoomToo() {
        manager.setTheme(PLAIN);
        Label plain = new Label(shell, SWT.NONE);

        manager.setZoom(2);

        assertThat(font(plain).getHeight()).isEqualTo(systemHeight() * 2);
    }

    @Test
    void zoomSurvivesThemeSwitchesAndZoomKeepsTheTheme() {
        manager.setZoom(2);
        manager.setTheme(OTHER);

        assertThat(manager.getZoom()).isEqualTo(2);
        assertThat(font(label).getHeight()).isEqualTo(16);

        manager.zoomOut();

        assertThat(manager.getTheme()).isSameAs(OTHER);
        assertThat(font(label).getHeight()).isEqualTo(14); // 8 pt at 175 %
    }

    @Test
    void widgetsCreatedLaterUseTheCurrentZoom() {
        manager.setZoom(1.25);
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM);
        Label later = new Label(dialog, SWT.NONE);

        assertThat(font(later).getHeight()).isEqualTo(13); // 10 pt at 125 %, rounded
    }

    @Test
    void stepsLandOnChromeLevels() {
        List<Double> up = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            manager.zoomIn();
            up.add(manager.getZoom());
        }
        assertThat(up).containsExactly(1.1, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0, 5.0, 5.0, 5.0, 5.0);

        manager.resetZoom();
        List<Double> down = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            manager.zoomOut();
            down.add(manager.getZoom());
        }
        assertThat(down).containsExactly(0.9, 0.8, 0.75, 0.67, 0.5, 0.33, 0.25, 0.25, 0.25);

        manager.setZoom(1.3);
        manager.zoomIn();
        assertThat(manager.getZoom()).isEqualTo(1.5);
        manager.setZoom(1.3);
        manager.zoomOut();
        assertThat(manager.getZoom()).isEqualTo(1.25);
    }

    @Test
    void setZoomClampsToChromesRange() {
        manager.setZoom(10);
        assertThat(manager.getZoom()).isEqualTo(ThemeManager.MAX_ZOOM);
        manager.setZoom(0.01);
        assertThat(manager.getZoom()).isEqualTo(ThemeManager.MIN_ZOOM);
    }

    @Test
    void resetGoesBackToTheThemeHeights() {
        manager.setZoom(3);
        manager.resetZoom();

        assertThat(manager.getZoom()).isEqualTo(1);
        assertThat(font(label).getHeight()).isEqualTo(10);
        assertThat(font(text).getHeight()).isEqualTo(12);
    }

    @Test
    void repeatedZoomingDoesNotLeakFonts() {
        // Every font both themes need at every level, once.
        cycleThroughAllLevels();
        manager.setTheme(OTHER);
        cycleThroughAllLevels();
        manager.setTheme(FONTS);
        int afterOneCycle = manager.resources().fontCount();

        for (int i = 0; i < 20; i++) {
            cycleThroughAllLevels();
            manager.setTheme(i % 2 == 0 ? OTHER : FONTS);
        }

        assertThat(manager.resources().fontCount()).isEqualTo(afterOneCycle);
    }

    @Test
    void listenersHearEachChangeOnce() {
        List<String> heard = new ArrayList<>();
        ZoomListener listener = (previous, current) -> heard.add(previous + "->" + current
                + " label " + font(label).getHeight());
        manager.addZoomListener(listener);
        try {
            manager.zoomIn();
            manager.setZoom(1.1); // unchanged: no event
            manager.resetZoom();
        } finally {
            manager.removeZoomListener(listener);
        }

        assertThat(heard).containsExactly("1.0->1.1 label 11", "1.1->1.0 label 10");
    }

    @Test
    void ctrlWheelAndKeysZoom() {
        wheel(label, SWT.MOD1, 3);
        assertThat(manager.getZoom()).isEqualTo(1.1);
        wheel(label, SWT.MOD1, -3);
        wheel(label, SWT.MOD1, -3);
        assertThat(manager.getZoom()).isEqualTo(0.9);

        key(text, SWT.MOD1, '=');
        key(text, SWT.MOD1, '=');
        assertThat(manager.getZoom()).isEqualTo(1.1);
        key(text, SWT.MOD1 | SWT.SHIFT, '=');
        assertThat(manager.getZoom()).isEqualTo(1.25);
        key(text, SWT.MOD1, SWT.KEYPAD_SUBTRACT);
        assertThat(manager.getZoom()).isEqualTo(1.1);
        key(text, SWT.MOD1, '0');
        assertThat(manager.getZoom()).isEqualTo(1);
    }

    @Test
    void wheelWithoutCtrlOrWithOtherModifiersDoesNotZoom() {
        wheel(label, SWT.NONE, 3);
        wheel(label, SWT.MOD1 | SWT.SHIFT, 3);
        key(text, SWT.MOD1 | SWT.ALT, '=');
        key(text, SWT.NONE, '-');

        assertThat(manager.getZoom()).isEqualTo(1);
    }

    @Test
    void ctrlWheelIsIgnoredWhenTheShortcutsAreTurnedOff() {
        List<Event> received = new ArrayList<>();
        label.addListener(SWT.MouseWheel, received::add);
        manager.setZoomShortcutsEnabled(false);

        wheel(label, SWT.MOD1, 3);
        key(text, SWT.MOD1, '=');

        assertThat(manager.getZoom()).isEqualTo(1);
        assertThat(received).hasSize(1);
    }

    @Test
    void aControlCanKeepCtrlWheelForItself() {
        Composite chart = new Composite(shell, SWT.NONE);
        Label plot = new Label(chart, SWT.NONE);
        chart.setData(ThemeKeys.ZOOM_SHORTCUTS, Boolean.FALSE);
        List<Event> received = new ArrayList<>();
        plot.addListener(SWT.MouseWheel, received::add);

        wheel(plot, SWT.MOD1, 3);
        assertThat(manager.getZoom()).isEqualTo(1);
        assertThat(received).hasSize(1);

        wheel(label, SWT.MOD1, 3); // elsewhere the shortcut still zooms
        assertThat(manager.getZoom()).isEqualTo(1.1);
    }

    @Test
    void consumedWheelEventsDoNotReachTheWidget() {
        List<Event> received = new ArrayList<>();
        label.addListener(SWT.MouseWheel, received::add);

        wheel(label, SWT.MOD1, 3);

        assertThat(received).isEmpty();
    }

    private void cycleThroughAllLevels() {
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomIn();
        }
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomOut();
        }
        manager.resetZoom();
    }

    private static void wheel(Control control, int stateMask, int count) {
        Event event = new Event();
        event.stateMask = stateMask;
        event.count = count;
        event.doit = true;
        control.notifyListeners(SWT.MouseWheel, event);
    }

    private static void key(Control control, int stateMask, int keyCode) {
        Event event = new Event();
        event.stateMask = stateMask;
        event.keyCode = keyCode;
        event.character = keyCode < 0x10000 ? (char) keyCode : 0;
        event.doit = true;
        control.notifyListeners(SWT.KeyDown, event);
    }

    private int systemHeight() {
        return display.getSystemFont().getFontData()[0].getHeight();
    }

    private static FontData font(Control control) {
        return control.getFont().getFontData()[0];
    }

    private static Theme theme(String id, String controls) {
        return ThemeJson.read(new StringReader("{ \"id\": \"" + id + "\", \"controls\": {" + controls + "} }"), id);
    }
}
