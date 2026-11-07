package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;

/**
 * The palette of a {@link RibbonColorButton}: <em>Automatic</em>, ten hues in five shades, a row
 * of standard colors, and <em>More Colors…</em> (the system color dialog). Arrows move, Enter
 * picks, Esc closes.
 */
final class ColorPalettePopup extends PopupShell {

    static final int COLUMNS = 10;
    private static final float[] HUES = { -1, 0, 30, 55, 120, 170, 210, 240, 275, 320 };
    private static final float[] LIGHTNESS = { 0.85f, 0.7f, 0.55f, 0.4f, 0.25f };
    static final RGB[] STANDARD = { new RGB(0xC0, 0, 0), new RGB(0xFF, 0, 0), new RGB(0xFF, 0xC0, 0), new RGB(0xFF, 0xFF, 0),
        new RGB(0x92, 0xD0, 0x50), new RGB(0, 0xB0, 0x50), new RGB(0, 0xB0, 0xF0), new RGB(0, 0x70, 0xC0), new RGB(0, 0x20, 0x60),
        new RGB(0x70, 0x30, 0xA0) };

    private final RibbonColorButton button;
    private Palette palette;

    ColorPalettePopup(RibbonSurface origin, RibbonColorButton button, Rectangle anchor) {
        super(origin, anchor);
        this.button = button;
    }

    @Override
    Control createContent() {
        palette = new Palette(shell);
        return palette;
    }

    Palette palette() {
        return palette;
    }

    /** Every color of the grid, row by row (the shades, then the standard row). */
    static List<RGB> colors() {
        List<RGB> colors = new ArrayList<>();
        for (float lightness : LIGHTNESS) {
            for (float hue : HUES) {
                colors.add(hue < 0 ? gray(lightness + 0.1f) : hsl(hue, 0.7f, lightness));
            }
        }
        for (RGB rgb : STANDARD) {
            colors.add(rgb);
        }
        return colors;
    }

    private static RGB gray(float level) {
        int value = Math.round(Math.max(0, Math.min(1, level)) * 255);
        return new RGB(value, value, value);
    }

    private static RGB hsl(float hue, float saturation, float lightness) {
        float c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = lightness - c / 2;
        float r;
        float g;
        float b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return new RGB(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    void pick(RGB rgb) {
        close();
        button.pick(origin.isDisposed() ? ribbon : origin, rgb);
    }

    /** The palette canvas: cell index -1 is Automatic, {@code colors().size()} is More Colors. */
    final class Palette extends Canvas {

        private final List<RGB> colors = colors();
        private int hover = Integer.MIN_VALUE;
        private int focus;

        Palette(Composite parent) {
            super(parent, SWT.DOUBLE_BUFFERED);
            ThemeManager.applyOnCreate(this, "RibbonPopup");
            focus = button.offersAutomatic() ? -1 : 0;
            addListener(SWT.Paint, this::paint);
            addListener(SWT.MouseMove, e -> {
                hover = cellAt(e.x, e.y);
                redraw();
            });
            addListener(SWT.MouseUp, e -> {
                int cell = cellAt(e.x, e.y);
                if (cell != Integer.MIN_VALUE) {
                    choose(cell);
                }
            });
            addListener(SWT.KeyDown, this::key);
            addListener(SWT.Traverse, e -> {
                if (e.detail == SWT.TRAVERSE_ESCAPE) {
                    e.doit = false;
                    close();
                    origin.setFocus();
                } else if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                    e.doit = false;
                }
            });
            getAccessible().addAccessibleListener(new AccessibleAdapter() {
                @Override
                public void getName(AccessibleEvent e) {
                    e.result = nameOf(focus);
                }
            });
        }

        private int cell() {
            return Math.max(14, Math.round(14 * getFont().getFontData()[0].getHeight() / 9f));
        }

        private int rowHeight() {
            GC gc = new GC(this);
            int height = gc.getFontMetrics().getHeight() + 8;
            gc.dispose();
            return height;
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            int cell = cell();
            int width = 8 + COLUMNS * (cell + 4);
            int rows = LIGHTNESS.length + 1;
            int height = 4 + (button.offersAutomatic() ? rowHeight() : 0) + rows * (cell + 4) + 8 + rowHeight() + 4;
            return new Point(width, height);
        }

        Rectangle bounds(int index) {
            int cell = cell();
            int top = 4 + (button.offersAutomatic() ? rowHeight() : 0);
            int width = getClientArea().width;
            if (index == -1) {
                return new Rectangle(4, 4, width - 8, rowHeight());
            }
            if (index == colors.size()) {
                int y = top + (LIGHTNESS.length + 1) * (cell + 4) + 8;
                return new Rectangle(4, y, width - 8, rowHeight());
            }
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            int y = top + row * (cell + 4) + (row == LIGHTNESS.length ? 8 : 0);
            return new Rectangle(6 + column * (cell + 4), y + 2, cell, cell);
        }

        int cellAt(int x, int y) {
            for (int i = button.offersAutomatic() ? -1 : 0; i <= colors.size(); i++) {
                if (bounds(i).contains(x, y)) {
                    return i;
                }
            }
            return Integer.MIN_VALUE;
        }

        String nameOf(int index) {
            if (index == -1) {
                return "Automatic";
            }
            if (index == colors.size()) {
                return "More Colors...";
            }
            RGB rgb = colors.get(index);
            return String.format("#%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
        }

        void choose(int index) {
            if (index == -1) {
                pick(null);
            } else if (index == colors.size()) {
                ColorDialog dialog = new ColorDialog(ribbon.getShell());
                dialog.setRGB(button.color() != null ? button.color() : new RGB(0, 0, 0));
                close();
                RGB chosen = dialog.open();
                if (chosen != null) {
                    button.pick(ribbon, chosen);
                }
            } else {
                pick(colors.get(index));
            }
        }

        private void key(Event e) {
            int min = button.offersAutomatic() ? -1 : 0;
            int next = focus;
            switch (e.keyCode) {
                case SWT.ARROW_RIGHT -> next = focus < 0 ? 0 : Math.min(colors.size(), focus + 1);
                case SWT.ARROW_LEFT -> next = Math.max(min, focus - 1);
                case SWT.ARROW_DOWN -> next = focus < 0 ? 0 : focus + COLUMNS >= colors.size() ? colors.size() : focus + COLUMNS;
                case SWT.ARROW_UP -> next = focus == colors.size() ? colors.size() - COLUMNS : focus - COLUMNS < 0 ? min : focus - COLUMNS;
                case SWT.CR, SWT.KEYPAD_CR -> {
                    choose(focus);
                    return;
                }
                default -> {
                    if (e.character == ' ') {
                        choose(focus);
                    }
                    return;
                }
            }
            focus = next;
            getAccessible().setFocus(ACC.CHILDID_SELF);
            redraw();
        }

        private void paint(Event e) {
            GC gc = e.gc;
            RibbonColors ribbonColors = ribbon.colors();
            Themed theme = Themed.of(this);
            Rectangle area = getClientArea();
            gc.setBackground(theme.color("background", ribbonColors.background));
            gc.fillRectangle(area);
            gc.setForeground(theme.color("borderColor", ribbonColors.border));
            gc.drawRectangle(0, 0, area.width - 1, area.height - 1);
            for (int i = button.offersAutomatic() ? -1 : 0; i <= colors.size(); i++) {
                Rectangle b = bounds(i);
                boolean hot = i == hover || (i == focus && isFocusControl());
                if (i == -1 || i == colors.size()) {
                    if (hot) {
                        gc.setBackground(ribbonColors.hover);
                        gc.fillRectangle(b);
                    }
                    int swatch = b.height - 8;
                    if (i == -1) {
                        gc.setBackground(ribbonColors.foreground);
                        gc.fillRectangle(b.x + 4, b.y + 4, swatch, swatch);
                    }
                    gc.setForeground(ribbonColors.foreground);
                    gc.drawText(nameOf(i), b.x + 8 + swatch, b.y + (b.height - gc.getFontMetrics().getHeight()) / 2, SWT.DRAW_TRANSPARENT);
                    continue;
                }
                gc.setBackground(new org.eclipse.swt.graphics.Color(colors.get(i)));
                gc.fillRectangle(b);
                gc.setForeground(hot ? ribbonColors.checkedBorder : ribbonColors.border);
                gc.drawRectangle(b.x - (hot ? 1 : 0), b.y - (hot ? 1 : 0), b.width + (hot ? 1 : -1), b.height + (hot ? 1 : -1));
            }
        }

        int focusCell() {
            return focus;
        }
    }
}
