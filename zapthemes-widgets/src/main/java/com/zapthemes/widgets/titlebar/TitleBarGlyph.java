package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;

/**
 * Line glyphs a {@link TitleBarItem} can show instead of an image. They are drawn in the bar's
 * foreground at the bar's scale, so they follow the theme, the accent and the font zoom.
 */
public enum TitleBarGlyph {
    NEW, OPEN, SAVE, UNDO, REDO, PRINT, EMAIL, SEARCH, SHARE, COMMENT;

    /** Draws the glyph in the GC's foreground into the square at ({@code x}, {@code y}) of side {@code size}. */
    public void paint(GC gc, int x, int y, int size) {
        int lw = Math.max(1, Math.round(size / 16f));
        gc.setLineWidth(lw);
        gc.setLineCap(SWT.CAP_ROUND);
        gc.setLineJoin(SWT.JOIN_ROUND);
        float s = size / 16f;
        switch (this) {
            case NEW -> {
                gc.drawPolygon(points(x, y, s, 3, 1, 10, 1, 13, 4, 13, 15, 3, 15));
                gc.drawPolyline(points(x, y, s, 10, 1, 10, 4, 13, 4));
            }
            case OPEN -> {
                gc.drawPolyline(points(x, y, s, 1, 13, 1, 3, 6, 3, 7.5f, 5, 13, 5, 13, 7));
                gc.drawPolygon(points(x, y, s, 1, 13, 3.5f, 7, 15, 7, 12.5f, 13));
            }
            case SAVE -> {
                gc.drawPolygon(points(x, y, s, 2, 2, 12, 2, 14, 4, 14, 14, 2, 14));
                gc.drawPolyline(points(x, y, s, 5, 2, 5, 6, 11, 6, 11, 2));
                gc.drawRectangle(px(x, s, 5), px(y, s, 9), Math.round(6 * s), Math.round(5 * s));
            }
            case UNDO -> {
                gc.drawArc(px(x, s, 3), px(y, s, 4), Math.round(11 * s), Math.round(10 * s), -90, 180);
                gc.drawLine(px(x, s, 8.5f), px(y, s, 4), px(x, s, 4), px(y, s, 4));
                gc.drawLine(px(x, s, 8.5f), px(y, s, 14), px(x, s, 5), px(y, s, 14));
                gc.drawPolyline(points(x, y, s, 6.5f, 1.5f, 4, 4, 6.5f, 6.5f));
            }
            case REDO -> {
                gc.drawArc(px(x, s, 2), px(y, s, 4), Math.round(11 * s), Math.round(10 * s), 90, 180);
                gc.drawLine(px(x, s, 7.5f), px(y, s, 4), px(x, s, 12), px(y, s, 4));
                gc.drawLine(px(x, s, 7.5f), px(y, s, 14), px(x, s, 11), px(y, s, 14));
                gc.drawPolyline(points(x, y, s, 9.5f, 1.5f, 12, 4, 9.5f, 6.5f));
            }
            case PRINT -> {
                gc.drawPolyline(points(x, y, s, 4, 5, 4, 1, 12, 1, 12, 5));
                gc.drawPolygon(points(x, y, s, 1, 5, 15, 5, 15, 12, 1, 12));
                gc.drawRectangle(px(x, s, 4), px(y, s, 9), Math.round(8 * s), Math.round(6 * s));
            }
            case EMAIL -> {
                gc.drawRectangle(px(x, s, 1), px(y, s, 3), Math.round(14 * s), Math.round(10 * s));
                gc.drawPolyline(points(x, y, s, 1, 3, 8, 9, 15, 3));
            }
            case SEARCH -> {
                gc.drawOval(px(x, s, 2), px(y, s, 2), Math.round(9 * s), Math.round(9 * s));
                gc.drawLine(px(x, s, 10), px(y, s, 10), px(x, s, 14.5f), px(y, s, 14.5f));
            }
            case SHARE -> {
                gc.drawPolyline(points(x, y, s, 5, 7, 2, 7, 2, 15, 14, 15, 14, 7, 11, 7));
                gc.drawLine(px(x, s, 8), px(y, s, 1.5f), px(x, s, 8), px(y, s, 10));
                gc.drawPolyline(points(x, y, s, 5, 4.5f, 8, 1.5f, 11, 4.5f));
            }
            case COMMENT -> gc.drawPolygon(points(x, y, s, 1, 2, 15, 2, 15, 11, 7, 11, 4, 14, 4, 11, 1, 11));
            default -> throw new IllegalStateException(name());
        }
        gc.setLineWidth(1);
    }

    private static int px(int origin, float scale, float value) {
        return origin + Math.round(value * scale);
    }

    private static int[] points(int x, int y, float s, float... xy) {
        int[] result = new int[xy.length];
        for (int i = 0; i < xy.length; i += 2) {
            result[i] = px(x, s, xy[i]);
            result[i + 1] = px(y, s, xy[i + 1]);
        }
        return result;
    }
}
