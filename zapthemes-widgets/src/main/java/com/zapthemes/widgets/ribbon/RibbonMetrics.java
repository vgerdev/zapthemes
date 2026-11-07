package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeManager;

import com.zapthemes.widgets.command.Command;

/**
 * The ribbon's measurements, all derived from the theme font's height and the display's zoom
 * ({@link ThemeManager#getZoom}), so zoom and platform font sizes scale the whole ribbon together.
 * At 100 % (a 15 px font) icons are 16 and 32 px, a row is 22 px and the tab row 27 px; at 200 %
 * icons are 32 and 64 px.
 */
final class RibbonMetrics {

    final GC gc;
    final Font font;
    final Font tabFont;
    final Font groupFont;
    final int fontHeight;
    final double scale;
    final int smallIcon;
    final int largeIcon;
    final int row;
    final int contentTop;
    final int contentHeight;
    final int groupLabelHeight;
    final int panelHeight;
    final int tabRowHeight;
    final int arrow;
    final int groupPad;
    final int groupGap;
    final int collapseButton;

    RibbonMetrics(GC gc, Font font, Font tabFont, Font groupFont) {
        this.gc = gc;
        this.font = font;
        this.tabFont = tabFont;
        this.groupFont = groupFont;
        gc.setFont(font);
        fontHeight = gc.getFontMetrics().getHeight();
        // The font is already zoomed. Icons come in 16 / 32 px steps for the platform's own font size
        // (the system font, which the zoom leaves alone) and are then exactly zoomed.
        double zoom = 1;
        double platform = fontHeight / 15.0;
        if (gc.getDevice() instanceof Display display) {
            zoom = ThemeManager.get(display).getZoom();
            gc.setFont(display.getSystemFont());
            platform = gc.getFontMetrics().getHeight() / 15.0;
            gc.setFont(font);
        }
        scale = Math.max(0.8 * zoom, fontHeight / 15.0);
        smallIcon = Math.max(1, (int) Math.round(Math.max(16, Math.round(16 * platform / 4) * 4) * zoom));
        largeIcon = 2 * smallIcon;
        row = Math.max(smallIcon + (int) Math.round(6 * zoom), fontHeight + (int) Math.round(7 * zoom));
        contentTop = px(3);
        contentHeight = 3 * row;
        gc.setFont(groupFont);
        groupLabelHeight = gc.getFontMetrics().getHeight() + px(4);
        gc.setFont(tabFont);
        tabRowHeight = Math.max(gc.getFontMetrics().getHeight(), fontHeight) + px(12);
        gc.setFont(font);
        panelHeight = contentTop + contentHeight + px(2) + groupLabelHeight + px(1);
        arrow = Math.max(5, px(7));
        groupPad = px(4);
        groupGap = px(7);
        collapseButton = px(22);
    }

    int px(int at100) {
        return (int) Math.round(at100 * scale);
    }

    int text(String label) {
        gc.setFont(font);
        return gc.textExtent(Command.stripMnemonic(label), SWT.DRAW_TRANSPARENT).x;
    }

    int tabText(String label) {
        gc.setFont(tabFont);
        int width = gc.textExtent(Command.stripMnemonic(label), SWT.DRAW_TRANSPARENT).x;
        gc.setFont(font);
        return width;
    }

    int groupText(String label) {
        gc.setFont(groupFont);
        int width = gc.textExtent(Command.stripMnemonic(label), SWT.DRAW_TRANSPARENT).x;
        gc.setFont(font);
        return width;
    }

    /**
     * A large item's label in one or two lines: two when it has a space and is much wider than the
     * icon, or when it has an arrow to fit after it; the split keeps the lines balanced.
     */
    String[] lines(String label, boolean withArrow) {
        String plain = Command.stripMnemonic(label);
        int space = plain.indexOf(' ');
        if (space < 0) {
            return withArrow ? new String[] { plain, "" } : new String[] { plain };
        }
        if (!withArrow && text(plain) <= largeIcon * 1.7) {
            return new String[] { plain };
        }
        String[] best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (int i = plain.indexOf(' '); i >= 0; i = plain.indexOf(' ', i + 1)) {
            String first = plain.substring(0, i);
            String second = plain.substring(i + 1);
            int width = Math.max(text(first), text(second) + (withArrow ? arrow + px(4) : 0));
            if (width < bestWidth) {
                bestWidth = width;
                best = new String[] { first, second };
            }
        }
        return best;
    }
}
