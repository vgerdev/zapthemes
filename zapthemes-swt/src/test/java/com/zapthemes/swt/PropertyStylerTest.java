package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.SystemColors;
import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.PropertyStyler;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.ThemeContext;
import com.zapthemes.swt.widgets.Shell;

/** Setter-based styling of third-party widgets, and themed system colors. */
class PropertyStylerTest {

    /** A widget in the style of Nebula: its look is set through setters, including a part's. */
    public static class Gauge extends Canvas {
        private Color needleColor;
        private int arc = 3;
        private boolean ticks;
        private final Part part = new Part();

        public Gauge(org.eclipse.swt.widgets.Composite parent) {
            super(parent, SWT.NONE);
            needleColor = parent.getDisplay().getSystemColor(SWT.COLOR_RED);
        }

        public Color getNeedleColor() {
            return needleColor;
        }

        public void setNeedleColor(Color color) {
            needleColor = color;
        }

        public int getArc() {
            return arc;
        }

        public void setArc(int arc) {
            this.arc = arc;
        }

        public boolean isTicks() {
            return ticks;
        }

        public void setTicks(boolean ticks) {
            this.ticks = ticks;
        }

        public Part getPart() {
            return part;
        }
    }

    public static class Part {
        private Color fill;

        public Color getFill() {
            return fill;
        }

        public void setFill(Color fill) {
            this.fill = fill;
        }
    }

    private static final Theme GAUGE = theme("""
            {
              "id": "gauge",
              "palette": { "accent": "#3574F0" },
              "controls": { "Gauge": { "needleColor": "$accent", "arc": 9, "ticks": true, "part.fill": "#102030",
                                       "unknownKey": "#FFFFFF" } },
              "registries": { "systemColors": { "COLOR_LIST_SELECTION": "#112233" } }
            }
            """);
    private static final Theme PLAIN = theme("{ \"id\": \"plain\" }");

    private final Display display = Display.getDefault();
    private final ThemeManager manager = ThemeManager.get(display);

    private static Theme theme(String json) {
        return ThemeJson.read(new StringReader(json), "test");
    }

    @AfterEach
    void tearDown() {
        manager.setTheme(PLAIN);
        for (var shell : display.getShells()) {
            shell.dispose();
        }
    }

    private void style(Gauge gauge, Theme theme) {
        manager.setTheme(theme);
        var style = new StyleResolver(theme).resolve("Gauge", SWT.NONE);
        new PropertyStyler().apply(gauge, style, new ThemeContext(manager.resources()));
    }

    @Test
    void setsPropertiesByNameIncludingParts() {
        Gauge gauge = new Gauge(new Shell(display));

        style(gauge, GAUGE);

        assertThat(gauge.getNeedleColor().getRGB()).isEqualTo(new RGB(0x35, 0x74, 0xF0));
        assertThat(gauge.getArc()).isEqualTo(9);
        assertThat(gauge.isTicks()).isTrue();
        assertThat(gauge.getPart().getFill().getRGB()).isEqualTo(new RGB(0x10, 0x20, 0x30));
    }

    @Test
    void restoresOriginalValuesWhenAThemeDropsAKey() {
        Gauge gauge = new Gauge(new Shell(display));
        RGB original = gauge.getNeedleColor().getRGB();

        style(gauge, GAUGE);
        style(gauge, PLAIN);

        assertThat(gauge.getNeedleColor().getRGB()).isEqualTo(original);
        assertThat(gauge.getArc()).isEqualTo(3);
        assertThat(gauge.isTicks()).isFalse();
    }

    @Test
    void systemColorsFollowTheThemeAndStayUsable() {
        manager.setTheme(GAUGE);
        Color themed = SystemColors.get(display, SWT.COLOR_LIST_SELECTION);
        assertThat(themed.getRGB()).isEqualTo(new RGB(0x11, 0x22, 0x33));
        assertThat(SystemColors.get(display, SWT.COLOR_LIST_FOREGROUND)).isEqualTo(display.getSystemColor(SWT.COLOR_LIST_FOREGROUND));

        manager.setTheme(PLAIN);
        assertThat(SystemColors.get(display, SWT.COLOR_LIST_SELECTION)).isEqualTo(display.getSystemColor(SWT.COLOR_LIST_SELECTION));
        // A widget may have kept the themed color in a field: it must still paint.
        assertThat(themed.isDisposed()).isFalse();
    }
}
