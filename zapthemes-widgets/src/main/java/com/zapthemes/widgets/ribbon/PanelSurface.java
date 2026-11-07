package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

/**
 * The surface of a popup showing ribbon groups: one collapsed group at full size, or the selected
 * tab of a collapsed ribbon (with a pin button). Theme type {@code RibbonPopup}.
 */
final class PanelSurface extends RibbonSurface {

    private final Ribbon ribbon;
    private final PopupShell popup;
    private final RibbonGroup group;
    private final RibbonTab tab;
    private final int width;

    /** Shows {@code group} at its largest size. */
    PanelSurface(Composite parent, PopupShell popup, RibbonGroup group) {
        this(parent, popup, group.ribbon(), group, null, 0);
    }

    /** Shows {@code tab}, reduced to {@code width}, with a pin button. */
    PanelSurface(Composite parent, PopupShell popup, RibbonTab tab, int width) {
        this(parent, popup, tab.ribbon(), null, tab, width);
    }

    private PanelSurface(Composite parent, PopupShell popup, Ribbon ribbon, RibbonGroup group, RibbonTab tab, int width) {
        super(parent, SWT.NONE, "RibbonPopup");
        this.ribbon = ribbon;
        this.popup = popup;
        this.group = group;
        this.tab = tab;
        this.width = width;
    }

    @Override
    Ribbon ribbon() {
        return ribbon;
    }

    RibbonTab tab() {
        return tab;
    }

    RibbonGroup group() {
        return group;
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        return measure(m -> {
            int height = RibbonLayout.groupHeight(m) + m.px(4);
            if (tab != null) {
                return new Point(width, height);
            }
            int w = RibbonLayout.width(group, group.maxSize(), m) + m.px(4);
            return new Point(w, height);
        });
    }

    @Override
    List<Hot> computeHots(RibbonMetrics m) {
        List<Hot> hots = new ArrayList<>();
        int x = m.px(2);
        int top = m.px(1);
        if (tab != null) {
            int available = width - m.px(8) - m.collapseButton;
            Map<RibbonGroup, Size> sizes = RibbonLayout.reduce(tab, available, m);
            addPanelHots(m, RibbonLayout.layoutPanel(tab.groups(), sizes, m, x + m.px(2), top, available), 0, hots);
            Rectangle area = getClientArea();
            int labelTop = top + m.contentTop + m.contentHeight + m.px(2);
            hots.add(new Hot(Hot.Kind.PIN, tab, new Rectangle(area.width - m.collapseButton - m.px(4), labelTop, m.collapseButton,
                    m.groupLabelHeight - m.px(1))));
        } else {
            Map<RibbonGroup, Size> sizes = Map.of(group, group.maxSize());
            addPanelHots(m, RibbonLayout.layoutPanel(List.of(group), sizes, m, x, top, Integer.MAX_VALUE), 0, hots);
        }
        return hots;
    }

    @Override
    void paintSurface(GC gc, RibbonMetrics m, List<Hot> hots) {
        Rectangle area = getClientArea();
        Themed theme = Themed.of(this);
        RibbonColors colors = colors();
        gc.setBackground(theme.color("background", colors.background));
        gc.fillRectangle(area);
        paintPanel(gc, m, hots, area);
        for (Hot hot : hots) {
            if (hot.kind() == Hot.Kind.PIN) {
                Rectangle b = hot.bounds();
                if (isHovered(hot)) {
                    gc.setBackground(colors.hover);
                    gc.fillRoundRectangle(b.x, b.y, b.width, b.height, colors.arc, colors.arc);
                }
                gc.setForeground(colors.foreground);
                int cx = b.x + b.width / 2;
                int cy = b.y + b.height / 2;
                int s = m.px(4);
                gc.drawLine(cx - s, cy - s, cx - s, cy + s);
                gc.drawLine(cx - s, cy, cx + s + 1, cy);
                gc.drawRectangle(cx - s + 2, cy - s / 2 - 1, 2 * s - 2, s + 2);
            }
        }
        gc.setForeground(theme.color("borderColor", colors.border));
        gc.drawRectangle(0, 0, area.width - 1, area.height - 1);
    }

    @Override
    void activateOther(Hot hot, Event event) {
        if (hot.kind() == Hot.Kind.PIN) {
            ribbon.setCollapsed(false);
        }
    }

    @Override
    Hot initialFocus(List<Hot> hots) {
        return hots.stream().filter(Hot::focusable).findFirst().orElse(null);
    }

    @Override
    void escape() {
        popup.close();
        if (!popup.origin.isDisposed()) {
            popup.origin.setFocus();
        }
    }

    @Override
    String accessibleName() {
        return tab != null ? tab.label() : group.label();
    }
}
