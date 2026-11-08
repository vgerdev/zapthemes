package com.zapthemes.widgets.ribbon;

import java.util.Objects;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.widgets.command.Command;

/**
 * A split button with a color bar under its icon, like Office's font color and highlight
 * buttons. The main part runs the command with the current color as the event's value (an
 * {@link RGB}, or {@code null} for <em>Automatic</em>); the arrow opens a palette, and picking a
 * color makes it current and runs the command.
 */
public class RibbonColorButton extends RibbonButton {

    private RGB color;
    private boolean automatic = true;

    public RibbonColorButton(Command command) {
        super(command);
    }

    /** The current color; {@code null} is <em>Automatic</em>. */
    public RGB color() {
        return color;
    }

    public RibbonColorButton color(RGB rgb) {
        if (!Objects.equals(rgb, color)) {
            color = rgb;
            repaint();
        }
        return this;
    }

    /** Whether the palette offers <em>Automatic</em> (no color). Default: yes. */
    public RibbonColorButton automatic(boolean offered) {
        automatic = offered;
        return this;
    }

    boolean offersAutomatic() {
        return automatic;
    }

    @Override
    boolean isSplit() {
        return true;
    }

    @Override
    boolean hasArrow() {
        return true;
    }

    @Override
    int accessibleRole() {
        return ACC.ROLE_SPLITBUTTON;
    }

    @Override
    Object value() {
        return color;
    }

    @Override
    void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event) {
        if (!isEnabled()) {
            return;
        }
        if (arrow) {
            surface.openColorPalette(this, box);
        } else {
            surface.run(command(), event, false, color);
        }
    }

    /** Picks {@code rgb} from the palette: makes it current and runs the command. */
    void pick(RibbonSurface surface, RGB rgb) {
        color(rgb);
        surface.run(command(), null, false, rgb);
    }

    @Override
    void paintLargeExtras(RibbonSurface surface, GC gc, RibbonMetrics m, int x, int y, int size) {
        bar(surface, gc, x, y + size - m.px(6), size, m.px(6));
    }

    @Override
    void paintSmallExtras(RibbonSurface surface, GC gc, RibbonMetrics m, int x, int y, int size) {
        bar(surface, gc, x, y + size - m.px(4), size, m.px(4));
    }

    private void bar(RibbonSurface surface, GC gc, int x, int y, int width, int height) {
        Color fill = color != null ? new Color(color) : surface.colors().foreground;
        gc.setBackground(fill);
        gc.fillRectangle(x, y, width, height);
    }

    @Override
    public String description() {
        String base = super.description();
        String current = color == null ? "Automatic" : String.format("#%02X%02X%02X", color.red, color.green, color.blue);
        return base == null ? "Current: " + current : base + "\nCurrent: " + current;
    }
}
