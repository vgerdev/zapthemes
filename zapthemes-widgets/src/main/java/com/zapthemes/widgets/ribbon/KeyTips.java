package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

/**
 * KeyTips: letter badges over the tabs (after Alt or F10), then over the items of the chosen tab,
 * then over the items of an opened collapsed group. Typing a KeyTip runs or opens its target;
 * Esc goes back a level. Two-letter KeyTips filter as their first letter is typed.
 */
final class KeyTips {

    enum Level { NONE, TOP, PANEL, POPUP }

    /** One badge: its letters, the surface it is drawn on, and its target. */
    record Entry(String tip, RibbonSurface surface, Hot hot) {
    }

    private final Ribbon ribbon;
    private Level level = Level.NONE;
    private List<Entry> entries = List.of();
    private String typed = "";

    KeyTips(Ribbon ribbon) {
        this.ribbon = ribbon;
    }

    boolean isShowing() {
        return level != Level.NONE;
    }

    Level level() {
        return level;
    }

    /** The badges shown now (filtered by what was typed). */
    List<Entry> visible() {
        return entries.stream().filter(e -> e.tip().startsWith(typed)).toList();
    }

    void show() {
        if (ribbon.isDisposed()) {
            return;
        }
        ribbon.closePopups();
        ribbon.toolTip().hide();
        ribbon.setKeyboardUsed(false);
        if (!ribbon.isFocusControl()) {
            ribbon.setFocus();
        }
        showTop();
    }

    private void showTop() {
        level = Level.TOP;
        typed = "";
        entries = assign(ribbon, topHots(ribbon));
        redraw();
    }

    void hide() {
        if (level == Level.NONE) {
            return;
        }
        level = Level.NONE;
        entries = List.of();
        typed = "";
        redraw();
    }

    /** Esc: back one level; {@code false} when no KeyTips show. */
    boolean back() {
        switch (level) {
            case NONE -> {
                return false;
            }
            case TOP -> {
                if (!typed.isEmpty()) {
                    typed = "";
                    redraw();
                } else {
                    hide();
                    ribbon.leave();
                }
            }
            case PANEL -> {
                if (!typed.isEmpty()) {
                    typed = "";
                    redraw();
                } else {
                    ribbon.closePopups();
                    ribbon.setFocus();
                    showTop();
                }
            }
            case POPUP -> {
                if (!typed.isEmpty()) {
                    typed = "";
                    redraw();
                } else {
                    List<PopupShell> popups = ribbon.popups();
                    if (!popups.isEmpty()) {
                        popups.getLast().close();
                    }
                    showPanel();
                }
            }
        }
        return true;
    }

    /** Handles a key while KeyTips show; {@code false} lets the surface handle it (and hides them). */
    boolean handleKey(RibbonSurface surface, Event event) {
        if (level == Level.NONE) {
            return false;
        }
        if (event.keyCode == SWT.ESC) {
            return false;
        }
        char c = Character.toUpperCase(event.character);
        if (!Character.isLetterOrDigit(c)) {
            if (event.keyCode == SWT.ALT || event.keyCode == SWT.F10 || event.keyCode == SWT.SHIFT || event.keyCode == SWT.CTRL) {
                return true;
            }
            hide();
            return false;
        }
        type(String.valueOf(c));
        return true;
    }

    /** Types KeyTip letters, as the keyboard does. */
    void type(String letters) {
        for (char c : letters.toUpperCase().toCharArray()) {
            if (level == Level.NONE) {
                return;
            }
            String next = typed + c;
            List<Entry> matches = entries.stream().filter(e -> e.tip().startsWith(next)).toList();
            if (matches.isEmpty()) {
                continue;
            }
            Entry exact = matches.stream().filter(e -> e.tip().equals(next)).findFirst().orElse(null);
            if (exact != null && matches.size() == 1) {
                typed = "";
                execute(exact);
            } else {
                typed = next;
                redraw();
            }
        }
    }

    private void execute(Entry entry) {
        RibbonSurface surface = entry.surface();
        Hot hot = entry.hot();
        switch (hot.kind()) {
            case TAB -> {
                surface.activate(hot, false, null);
                showPanel();
            }
            case FILE_TAB -> {
                hide();
                ribbon.openBackstage();
                Backstage backstage = ribbon.getBackstage();
                if (backstage != null && backstage.isOpen()) {
                    backstage.showKeyTips();
                }
            }
            case GROUP_BUTTON -> {
                surface.activate(hot, false, null);
                PopupShell popup = ribbon.popups().isEmpty() ? null : ribbon.popups().getLast();
                if (popup instanceof GroupPopup group && group.surface() != null) {
                    level = Level.POPUP;
                    entries = assign(group.surface(), panelHots(group.surface()));
                    group.surface().setFocus();
                    redraw();
                } else {
                    hide();
                }
            }
            case ITEM -> {
                RibbonItem item = hot.box().item();
                hide();
                surface.setFocusHot(hot);
                surface.activate(hot, item.hasPopup() && !(item instanceof RibbonComboBox combo && combo.isEditable()), null);
            }
            default -> {
                hide();
                surface.activate(hot, false, null);
            }
        }
    }

    private void showPanel() {
        RibbonSurface target = ribbon;
        GroupPopup overlay = ribbon.overlay();
        if (ribbon.isCollapsed() && overlay != null && overlay.surface() != null) {
            target = overlay.surface();
            target.setFocus();
        } else if (!ribbon.isFocusControl()) {
            ribbon.setFocus();
        }
        level = Level.PANEL;
        typed = "";
        entries = assign(target, panelHots(target));
        redraw();
    }

    private void redraw() {
        if (!ribbon.isDisposed()) {
            ribbon.repaintAll();
        }
    }

    // ------------------------------------------------------------------ assignment

    static List<Hot> topHots(Ribbon ribbon) {
        return ribbon.hots().stream().filter(Hot::inTabRow).toList();
    }

    static List<Hot> panelHots(RibbonSurface surface) {
        return surface.hots().stream().filter(h -> switch (h.kind()) {
            case ITEM -> h.box().item().isInteractive();
            case LAUNCHER, GROUP_BUTTON, COLLAPSE, PIN -> true;
            default -> false;
        }).toList();
    }

    private static String explicitTip(RibbonSurface surface, Hot hot) {
        return switch (hot.kind()) {
            case FILE_TAB -> "F";
            case TAB -> ((RibbonTab) hot.target()).keyTip();
            case ITEM -> hot.box().item().keyTip();
            case GROUP_BUTTON -> ((RibbonGroup) hot.target()).keyTip();
            default -> null;
        };
    }

    /** Assigns KeyTips: explicit ones first, then a free letter of each label, then two letters. */
    static List<Entry> assign(RibbonSurface surface, List<Hot> hots) {
        Map<Hot, String> tips = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Hot hot : hots) {
            String explicit = explicitTip(surface, hot);
            if (explicit != null && !explicit.isEmpty() && used.add(explicit)) {
                tips.put(hot, explicit);
            }
        }
        for (Hot hot : hots) {
            if (!tips.containsKey(hot)) {
                String tip = pick(surface.nameOf(hot), used);
                used.add(tip);
                tips.put(hot, tip);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Hot hot : hots) {
            entries.add(new Entry(tips.get(hot), surface, hot));
        }
        return entries;
    }

    private static boolean prefixTaken(String letter, Set<String> used) {
        for (String tip : used) {
            if (tip.length() > 1 && tip.startsWith(letter)) {
                return true;
            }
        }
        return false;
    }

    static String pick(String label, Set<String> used) {
        String upper = label == null ? "" : label.toUpperCase();
        List<Character> candidates = new ArrayList<>();
        boolean wordStart = true;
        for (char c : upper.toCharArray()) {
            if (Character.isLetterOrDigit(c) && wordStart) {
                candidates.add(c);
            }
            wordStart = !Character.isLetterOrDigit(c);
        }
        for (char c : upper.toCharArray()) {
            if (Character.isLetterOrDigit(c) && !candidates.contains(c)) {
                candidates.add(c);
            }
        }
        for (char c : candidates) {
            String tip = String.valueOf(c);
            if (!used.contains(tip) && !prefixTaken(tip, used)) {
                return tip;
            }
        }
        List<Character> firsts = new ArrayList<>(candidates);
        for (char c = 'Y'; c <= 'Z'; c++) {
            firsts.add(c);
        }
        for (char first : firsts) {
            if (used.contains(String.valueOf(first))) {
                continue;
            }
            List<Character> seconds = new ArrayList<>(candidates);
            for (char d = '1'; d <= '9'; d++) {
                seconds.add(d);
            }
            for (char second : seconds) {
                String tip = "" + first + second;
                if (!used.contains(tip)) {
                    return tip;
                }
            }
        }
        for (int i = 1;; i++) {
            String tip = "Z" + i;
            if (!used.contains(tip)) {
                return tip;
            }
        }
    }

    /** The keys that reach {@code hot}: {@code "Alt, H, B"}; {@code null} when none. */
    String pathOf(RibbonSurface surface, Hot hot) {
        if (!ribbon.isKeyTipsEnabled()) {
            return null;
        }
        if (surface == ribbon && hot.inTabRow()) {
            return "Alt, " + tipOf(assign(ribbon, topHots(ribbon)), hot);
        }
        if (surface == ribbon) {
            RibbonTab tab = ribbon.getSelection();
            Hot tabHot = topHots(ribbon).stream().filter(h -> h.target() == tab).findFirst().orElse(null);
            String item = tipOf(assign(ribbon, panelHots(ribbon)), hot);
            if (tabHot == null || item == null) {
                return null;
            }
            return "Alt, " + tipOf(assign(ribbon, topHots(ribbon)), tabHot) + ", " + item;
        }
        return null;
    }

    private static String tipOf(List<Entry> entries, Hot hot) {
        for (Entry entry : entries) {
            if (entry.hot().sameAs(hot)) {
                return entry.tip();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ painting

    void paint(RibbonSurface surface, GC gc, RibbonMetrics m) {
        if (level == Level.NONE) {
            return;
        }
        RibbonColors colors = ribbon.colors();
        gc.setFont(m.groupFont);
        int height = gc.getFontMetrics().getHeight() + m.px(2);
        for (Entry entry : visible()) {
            if (entry.surface() != surface) {
                continue;
            }
            Rectangle b = entry.hot().bounds();
            Point extent = gc.textExtent(entry.tip());
            int width = Math.max(height, extent.x + m.px(6));
            int cx;
            int y;
            if (entry.hot().inTabRow()) {
                cx = b.x + b.width / 2;
                y = b.y + b.height - height / 2;
            } else if (entry.hot().kind() == Hot.Kind.ITEM && entry.hot().box().size() != Size.LARGE
                    && entry.hot().box().bounds().height <= m.row) {
                cx = b.x + Math.min(b.width / 2, m.px(12));
                y = b.y + b.height - height + m.px(3);
            } else {
                cx = b.x + b.width / 2;
                y = b.y + b.height - height + m.px(2);
            }
            int x = cx - width / 2;
            gc.setBackground(colors.keyTipBackground);
            gc.fillRoundRectangle(x, y, width, height, m.px(3), m.px(3));
            gc.setForeground(colors.keyTipBorder);
            gc.drawRoundRectangle(x, y, width - 1, height - 1, m.px(3), m.px(3));
            gc.setForeground(colors.keyTipForeground);
            gc.drawText(entry.tip(), x + (width - extent.x) / 2, y + (height - extent.y) / 2, SWT.DRAW_TRANSPARENT);
        }
        gc.setFont(m.font);
    }
}
