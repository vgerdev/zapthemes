package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.Images;

/**
 * The File tab's page over the whole window, like Office's backstage: a navigation column (a back
 * arrow, pages, commands) and the selected page. Pages are created the first time they show.
 * Esc, the back arrow and running a command close it.
 *
 * <p>While it is open it takes its parent's whole client area: it swaps the parent's layout for
 * its own and hides its siblings, and puts both back when it closes, so it works with any layout,
 * JFace's {@code ApplicationWindow} included.
 *
 * <p>Theme type {@code RibbonBackstage}: {@code background}, {@code foreground},
 * {@code navBackground}, {@code navForeground}, {@code navHoverBackground},
 * {@code navSelectedBackground}, {@code navSelectedForeground}, {@code navFont}, {@code titleFont},
 * {@code useAppAccent}.
 */
public final class Backstage extends Composite {

    public static final String THEME_TYPE = "RibbonBackstage";

    private sealed interface Entry permits Page, CommandEntry, Separator {
    }

    private record Page(String id, String label, Images image, Consumer<Composite> factory) implements Entry {
    }

    private record CommandEntry(Command command) implements Entry {
    }

    private record Separator() implements Entry {
    }

    private final Ribbon ribbon;
    private final String title;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Composite> pages = new LinkedHashMap<>();
    private final Nav nav;
    private final Composite content;
    private final StackLayout stack = new StackLayout();
    private String selected;
    private boolean open;
    private Layout savedLayout;
    private final Map<Control, Boolean> savedVisibility = new HashMap<>();
    private final Listener escape = this::escapeFilter;
    private final List<Consumer<Boolean>> openListeners = new ArrayList<>();
    private final Map<String, String> keyTipsById = new HashMap<>();
    /** The KeyTips shown, by nav index; {@code null} when none show. */
    private Map<Integer, String> keyTips;
    private String typed = "";

    @SuppressWarnings("this-escape")
    Backstage(Ribbon ribbon, String title) {
        super(ribbon.getShell(), SWT.NONE);
        this.ribbon = ribbon;
        this.title = title == null ? "File" : Command.stripMnemonic(title);
        ThemeManager.applyOnCreate(this, THEME_TYPE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 0;
        setLayout(layout);
        nav = new Nav(this);
        nav.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        content = new com.zapthemes.swt.widgets.Composite(this, SWT.NONE);
        ThemeManager.get(getDisplay()).setThemeClass(content, "backstage");
        content.setLayout(stack);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        setVisible(false);
        addListener(SWT.Dispose, e -> getDisplay().removeFilter(SWT.Traverse, escape));
    }

    public Ribbon ribbon() {
        return ribbon;
    }

    /** Adds a page; {@code factory} fills its composite (a grid layout of one column) on first show. */
    public Backstage addPage(String id, String label, Images image, Consumer<Composite> factory) {
        checkWidget();
        entries.add(new Page(Objects.requireNonNull(id), Objects.requireNonNull(label), image == null ? Images.NONE : image,
                Objects.requireNonNull(factory)));
        if (selected == null) {
            selected = id;
        }
        nav.redraw();
        return this;
    }

    /** Adds an entry that runs a command (Save, Close) and closes the backstage. */
    public Backstage addCommand(String commandId) {
        checkWidget();
        entries.add(new CommandEntry(ribbon.commands().get(commandId)));
        nav.redraw();
        return this;
    }

    public Backstage addSeparator() {
        entries.add(new Separator());
        return this;
    }

    /** The page ids, in order. */
    public List<String> pageIds() {
        return entries.stream().filter(e -> e instanceof Page).map(e -> ((Page) e).id()).toList();
    }

    public boolean isOpen() {
        return open;
    }

    /** Nothing while closed, so layouts that measure every child (JFace's window) ignore it. */
    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        if (!open) {
            return new Point(wHint == SWT.DEFAULT ? 0 : wHint, hHint == SWT.DEFAULT ? 0 : hHint);
        }
        return super.computeSize(wHint, hHint, changed);
    }

    public String selectedPage() {
        return selected;
    }

    /** The composite of page {@code id} once it has been shown, else {@code null}. */
    public Composite page(String id) {
        return pages.get(id);
    }

    /** Told {@code true} when the backstage opens and {@code false} when it closes. */
    public void addOpenListener(Consumer<Boolean> listener) {
        openListeners.add(listener);
    }

    /** Opens it on the selected page (the first page at first). */
    public void open() {
        open(selected);
    }

    public void open(String pageId) {
        checkWidget();
        if (!open) {
            open = true;
            Composite parent = getParent();
            savedLayout = parent.getLayout();
            savedVisibility.clear();
            for (Control sibling : parent.getChildren()) {
                if (sibling != this) {
                    savedVisibility.put(sibling, sibling.getVisible());
                    sibling.setVisible(false);
                }
            }
            parent.setLayout(new FillAll());
            setVisible(true);
            moveAbove(null);
            getDisplay().addFilter(SWT.Traverse, escape);
            parent.layout(true, true);
            openListeners.forEach(l -> l.accept(true));
        }
        if (pageId != null) {
            select(pageId);
        }
        nav.setFocus();
        ribbon.redraw();
    }

    public void close() {
        checkWidget();
        if (!open) {
            return;
        }
        open = false;
        keyTips = null;
        typed = "";
        getDisplay().removeFilter(SWT.Traverse, escape);
        Composite parent = getParent();
        setVisible(false);
        savedVisibility.forEach((control, visible) -> {
            if (!control.isDisposed()) {
                control.setVisible(visible);
            }
        });
        savedVisibility.clear();
        parent.setLayout(savedLayout);
        savedLayout = null;
        parent.layout(true, true);
        if (!ribbon.isDisposed()) {
            ribbon.redraw();
            Control back = ribbon.lastFocus();
            if (back != null && !back.isDisposed() && back.isVisible()) {
                back.setFocus();
            }
        }
        openListeners.forEach(l -> l.accept(false));
    }

    /** Shows page {@code id}, creating it the first time. */
    public void select(String id) {
        checkWidget();
        Page page = entries.stream().filter(e -> e instanceof Page p && p.id().equals(id)).map(Page.class::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No page " + id));
        selected = id;
        Composite composite = pages.computeIfAbsent(id, key -> createPage(page));
        stack.topControl = composite;
        content.layout(true, true);
        nav.redraw();
    }

    private Composite createPage(Page page) {
        Composite composite = new com.zapthemes.swt.widgets.Composite(content, SWT.NONE);
        ThemeManager.get(getDisplay()).setThemeClass(composite, "backstage");
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 32;
        layout.marginHeight = 20;
        layout.verticalSpacing = 12;
        composite.setLayout(layout);
        Label heading = new Label(composite, SWT.NONE);
        ThemeManager.get(getDisplay()).setThemeClass(heading, "backstageTitle");
        heading.setText(page.label());
        Font titleFont = Themed.of(this).font("titleFont", null);
        if (titleFont != null) {
            heading.setFont(titleFont);
        }
        page.factory().accept(composite);
        return composite;
    }

    // ------------------------------------------------------------------ KeyTips

    /**
     * The KeyTip of page or command {@code id}; others get a free letter of their label. KeyTips
     * show over the navigation column after Alt or F10, and when the File tab is reached by its
     * KeyTip.
     */
    public Backstage setKeyTip(String id, String tip) {
        checkWidget();
        if (tip == null || tip.isBlank()) {
            keyTipsById.remove(id);
        } else {
            keyTipsById.put(id, tip.toUpperCase());
        }
        if (keyTips != null) {
            keyTips = assignKeyTips();
            nav.redraw();
        }
        return this;
    }

    boolean isShowingKeyTips() {
        return keyTips != null;
    }

    void showKeyTips() {
        if (!open) {
            return;
        }
        keyTips = assignKeyTips();
        typed = "";
        nav.setFocus();
        nav.redraw();
    }

    void hideKeyTips() {
        if (keyTips != null) {
            keyTips = null;
            typed = "";
            nav.redraw();
        }
    }

    void toggleKeyTips() {
        if (keyTips != null) {
            hideKeyTips();
        } else {
            showKeyTips();
        }
    }

    /** The KeyTips shown now (filtered by what was typed), by entry label. */
    Map<String, String> visibleKeyTips() {
        Map<String, String> visible = new LinkedHashMap<>();
        if (keyTips != null) {
            keyTips.forEach((index, tip) -> {
                if (tip.startsWith(typed)) {
                    visible.put(nav.label(entries.get(index - 1)), tip);
                }
            });
        }
        return visible;
    }

    /** Types KeyTip letters: a page's selects it and moves into it, a command's runs it. */
    void typeKeyTips(String letters) {
        for (char c : letters.toUpperCase().toCharArray()) {
            if (keyTips == null) {
                return;
            }
            String next = typed + c;
            List<Map.Entry<Integer, String>> matches = keyTips.entrySet().stream().filter(e -> e.getValue().startsWith(next)).toList();
            if (matches.isEmpty()) {
                continue;
            }
            if (matches.size() == 1 && matches.getFirst().getValue().equals(next)) {
                int index = matches.getFirst().getKey();
                hideKeyTips();
                Entry entry = entries.get(index - 1);
                nav.activate(index);
                if (entry instanceof Page && open) {
                    Composite page = pages.get(selected);
                    if (page != null && !page.setFocus()) {
                        nav.setFocus();
                    }
                }
            } else {
                typed = next;
                nav.redraw();
            }
        }
    }

    /** Explicit KeyTips first, then a free letter of each label; keyed by nav index. */
    private Map<Integer, String> assignKeyTips() {
        Map<Integer, String> tips = new LinkedHashMap<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String explicit = keyTipsById.get(idOf(entries.get(i)));
            if (explicit != null && used.add(explicit)) {
                tips.put(i + 1, explicit);
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!(entry instanceof Separator) && !tips.containsKey(i + 1)) {
                String tip = KeyTips.pick(nav.label(entry), used);
                used.add(tip);
                tips.put(i + 1, tip);
            }
        }
        return new java.util.TreeMap<>(tips);
    }

    private static String idOf(Entry entry) {
        return switch (entry) {
            case Page page -> page.id();
            case CommandEntry command -> command.command().id();
            case Separator separator -> null;
        };
    }

    void redrawAll() {
        nav.redraw();
    }

    Canvas nav() {
        return nav;
    }

    private void escapeFilter(Event event) {
        if (event.detail == SWT.TRAVERSE_ESCAPE && open && event.widget instanceof Control control && isAncestorOf(control)) {
            event.doit = false;
            event.detail = SWT.TRAVERSE_NONE;
            if (keyTips != null) {
                // Esc hides the KeyTips first, then closes the backstage.
                hideKeyTips();
            } else {
                close();
            }
        }
    }

    private boolean isAncestorOf(Control control) {
        for (Control c = control; c != null; c = c.getParent()) {
            if (c == this) {
                return true;
            }
        }
        return false;
    }

    /** Runs a command entry: closes the backstage, then runs it. */
    void runEntry(Command command) {
        close();
        if (command.isEnabled()) {
            command.run(new com.zapthemes.widgets.command.CommandEvent(command, ribbon));
        }
    }

    /** Gives the backstage its parent's whole client area. */
    private final class FillAll extends Layout {
        @Override
        protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
            return Backstage.this.computeSize(wHint, hHint, flushCache);
        }

        @Override
        protected void layout(Composite composite, boolean flushCache) {
            Backstage.this.setBounds(composite.getClientArea());
        }
    }

    // ------------------------------------------------------------------ the navigation column

    /** The navigation column: back arrow, then entries. Index 0 is the back arrow. */
    final class Nav extends Canvas {

        private int hover = -1;
        private int focus;

        Nav(Composite parent) {
            super(parent, SWT.DOUBLE_BUFFERED);
            ThemeManager.applyOnCreate(this, THEME_TYPE);
            addListener(SWT.Paint, this::paint);
            addListener(SWT.MouseMove, e -> {
                int index = indexAt(e.y);
                if (index != hover) {
                    hover = index;
                    redraw();
                }
            });
            addListener(SWT.MouseExit, e -> {
                hover = -1;
                redraw();
            });
            addListener(SWT.MouseUp, e -> {
                int index = indexAt(e.y);
                if (index >= 0 && e.button == 1) {
                    activate(index);
                }
            });
            addListener(SWT.KeyDown, this::key);
            addListener(SWT.Traverse, e -> {
                if (e.detail == SWT.TRAVERSE_RETURN) {
                    e.doit = false;
                } else if (e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                    e.doit = true;
                }
            });
            addListener(SWT.FocusIn, e -> redraw());
            addListener(SWT.FocusOut, e -> redraw());
            initAccessible();
        }

        private int rowHeight(GC gc) {
            return gc.getFontMetrics().getHeight() + 18;
        }

        private Font navFont() {
            return Themed.of(Backstage.this).font("navFont", getFont());
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            GC gc = new GC(this);
            try {
                gc.setFont(navFont());
                int width = 160;
                for (Entry entry : entries) {
                    width = Math.max(width, gc.textExtent(label(entry)).x + 64);
                }
                int height = rowHeight(gc) * (entries.size() + 2);
                return new Point(wHint == SWT.DEFAULT ? width : wHint, hHint == SWT.DEFAULT ? height : hHint);
            } finally {
                gc.dispose();
            }
        }

        private String label(Entry entry) {
            return switch (entry) {
                case Page page -> page.label();
                case CommandEntry command -> command.command().plainLabel();
                case Separator separator -> "";
            };
        }

        Rectangle bounds(GC gc, int index) {
            int row = rowHeight(gc);
            int width = getClientArea().width;
            if (index == 0) {
                return new Rectangle(0, 8, width, row);
            }
            int y = 8 + row + 8;
            for (int i = 0; i < index - 1; i++) {
                y += entries.get(i) instanceof Separator ? 17 : row;
            }
            return new Rectangle(0, y, width, entries.get(index - 1) instanceof Separator ? 17 : row);
        }

        int indexAt(int y) {
            GC gc = new GC(this);
            try {
                gc.setFont(navFont());
                for (int i = 0; i <= entries.size(); i++) {
                    Rectangle b = bounds(gc, i);
                    if (y >= b.y && y < b.y + b.height) {
                        return i > 0 && entries.get(i - 1) instanceof Separator ? -1 : i;
                    }
                }
                return -1;
            } finally {
                gc.dispose();
            }
        }

        void activate(int index) {
            if (index == 0) {
                close();
                return;
            }
            Entry entry = entries.get(index - 1);
            focus = index;
            if (entry instanceof Page page) {
                select(page.id());
            } else if (entry instanceof CommandEntry command) {
                runEntry(command.command());
            }
        }

        private void key(Event e) {
            if (keyTips != null) {
                char c = Character.toUpperCase(e.character);
                if (Character.isLetterOrDigit(c)) {
                    typeKeyTips(String.valueOf(c));
                    return;
                }
                if (e.keyCode == SWT.ALT || e.keyCode == SWT.F10 || e.keyCode == SWT.SHIFT || e.keyCode == SWT.CTRL) {
                    return;
                }
                hideKeyTips();
            }
            int next = focus;
            switch (e.keyCode) {
                case SWT.ARROW_DOWN -> next = step(1);
                case SWT.ARROW_UP -> next = step(-1);
                case SWT.HOME -> next = 0;
                case SWT.END -> next = entries.size();
                case SWT.CR, SWT.KEYPAD_CR -> {
                    activate(focus);
                    return;
                }
                default -> {
                    if (e.character == ' ') {
                        activate(focus);
                    }
                    return;
                }
            }
            focus = next;
            getAccessible().setFocus(focus);
            redraw();
        }

        private int step(int delta) {
            int next = focus;
            do {
                next = Math.floorMod(next + delta, entries.size() + 1);
            } while (next > 0 && entries.get(next - 1) instanceof Separator && next != focus);
            return next;
        }

        private void paint(Event event) {
            GC gc = event.gc;
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            Themed theme = Themed.of(Backstage.this);
            RibbonColors ribbonColors = ribbon.colors();
            boolean appAccent = ribbon.getAccentColor() != null && theme.bool("useAppAccent", true)
                    && Themed.of(ribbon).bool("useAppAccent", true);
            Color navBackground = appAccent ? ribbonColors.fileTab : theme.color("navBackground", ribbonColors.fileTab);
            Color navForeground = theme.color("navForeground", ribbonColors.fileTabForeground);
            Color hoverColor = theme.color("navHoverBackground", RibbonColors.mix(navBackground, navForeground, 0.15));
            Color selectedColor = theme.color("navSelectedBackground", RibbonColors.mix(navBackground, navForeground, 0.28));
            Color selectedForeground = theme.color("navSelectedForeground", navForeground);
            Rectangle area = getClientArea();
            gc.setBackground(navBackground);
            gc.fillRectangle(area);
            gc.setFont(navFont());
            int height = gc.getFontMetrics().getHeight();
            for (int i = 0; i <= entries.size(); i++) {
                Rectangle b = bounds(gc, i);
                Entry entry = i == 0 ? null : entries.get(i - 1);
                if (entry instanceof Separator) {
                    gc.setForeground(RibbonColors.mix(navBackground, navForeground, 0.35));
                    gc.drawLine(16, b.y + b.height / 2, area.width - 16, b.y + b.height / 2);
                    continue;
                }
                boolean isSelected = entry instanceof Page page && page.id().equals(selected);
                if (isSelected) {
                    gc.setBackground(selectedColor);
                    gc.fillRectangle(b);
                } else if (i == hover) {
                    gc.setBackground(hoverColor);
                    gc.fillRectangle(b);
                }
                Color foreground = isSelected ? selectedForeground : navForeground;
                if (entry instanceof CommandEntry command && !command.command().isEnabled()) {
                    foreground = RibbonColors.mix(navBackground, navForeground, 0.5);
                }
                gc.setForeground(foreground);
                int textY = b.y + (b.height - height) / 2;
                if (i == 0) {
                    int cy = b.y + b.height / 2;
                    int r = height / 2;
                    gc.setLineWidth(2);
                    gc.drawOval(16, cy - r, 2 * r, 2 * r);
                    gc.drawLine(16 + r / 2, cy, 16 + r + r / 2 + 1, cy);
                    gc.drawPolyline(new int[] { 16 + r, cy - r / 2, 16 + r / 2, cy, 16 + r, cy + r / 2 });
                    gc.setLineWidth(1);
                    continue;
                }
                int x = 20;
                Images images = entry instanceof Page page ? page.image()
                        : entry instanceof CommandEntry command ? command.command().images() : Images.NONE;
                org.eclipse.swt.graphics.Image image = images.image(16);
                if (image != null) {
                    Rectangle ib = image.getBounds();
                    gc.drawImage(image, 0, 0, ib.width, ib.height, x, b.y + (b.height - 16) / 2, 16, 16);
                }
                x += 26;
                gc.drawText(label(entry), x, textY, SWT.DRAW_TRANSPARENT);
                if (i == focus && isFocusControl() && keyTips == null) {
                    gc.setForeground(navForeground);
                    gc.drawFocus(b.x + 2, b.y + 1, b.width - 4, b.height - 2);
                }
            }
            paintKeyTips(gc, ribbonColors);
        }

        /** KeyTip badges over the entries' icons, as the ribbon draws them. */
        private void paintKeyTips(GC gc, RibbonColors colors) {
            if (keyTips == null) {
                return;
            }
            gc.setFont(colors.groupLabelFont);
            int height = gc.getFontMetrics().getHeight() + 2;
            for (Map.Entry<Integer, String> tip : keyTips.entrySet()) {
                if (!tip.getValue().startsWith(typed)) {
                    continue;
                }
                gc.setFont(navFont());
                Rectangle b = bounds(gc, tip.getKey());
                gc.setFont(colors.groupLabelFont);
                Point extent = gc.textExtent(tip.getValue());
                int width = Math.max(height, extent.x + 6);
                int x = 28 - width / 2;
                int y = b.y + b.height - height - 1;
                gc.setBackground(colors.keyTipBackground);
                gc.fillRoundRectangle(x, y, width, height, 3, 3);
                gc.setForeground(colors.keyTipBorder);
                gc.drawRoundRectangle(x, y, width - 1, height - 1, 3, 3);
                gc.setForeground(colors.keyTipForeground);
                gc.drawText(tip.getValue(), x + (width - extent.x) / 2, y + (height - extent.y) / 2, SWT.DRAW_TRANSPARENT);
            }
            gc.setFont(navFont());
        }

        private void initAccessible() {
            getAccessible().addAccessibleListener(new AccessibleAdapter() {
                @Override
                public void getName(AccessibleEvent e) {
                    if (e.childID == ACC.CHILDID_SELF) {
                        e.result = title;
                    } else if (e.childID == 0) {
                        e.result = "Back";
                    } else if (e.childID <= entries.size()) {
                        e.result = label(entries.get(e.childID - 1));
                    }
                }
            });
            getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
                @Override
                public void getChildCount(AccessibleControlEvent e) {
                    e.detail = entries.size() + 1;
                }

                @Override
                public void getRole(AccessibleControlEvent e) {
                    if (e.childID == ACC.CHILDID_SELF) {
                        e.detail = ACC.ROLE_TABFOLDER;
                    } else if (e.childID > 0 && e.childID <= entries.size() && entries.get(e.childID - 1) instanceof Page) {
                        e.detail = ACC.ROLE_TABITEM;
                    } else {
                        e.detail = ACC.ROLE_PUSHBUTTON;
                    }
                }

                @Override
                public void getFocus(AccessibleControlEvent e) {
                    e.childID = isFocusControl() ? focus : ACC.CHILDID_NONE;
                }

                @Override
                public void getState(AccessibleControlEvent e) {
                    int state = ACC.STATE_FOCUSABLE;
                    if (e.childID == focus && isFocusControl()) {
                        state |= ACC.STATE_FOCUSED;
                    }
                    if (e.childID > 0 && e.childID <= entries.size() && entries.get(e.childID - 1) instanceof Page page
                            && page.id().equals(selected)) {
                        state |= ACC.STATE_SELECTED;
                    }
                    e.detail = state;
                }
            });
        }
    }
}
