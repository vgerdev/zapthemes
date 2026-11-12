package com.zapthemes.files.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.ui.FileIcons.Glyph;
import com.zapthemes.swt.widgets.Text;

/**
 * The browser's navigation row, drawn on one canvas: Back, Forward, Up and Refresh, then the address
 * bar. The address bar is a breadcrumb (the top's icon, then a segment per folder with a chevron
 * after each); a chevron drops down the subfolders of its folder, and the one after the icon the
 * places. Clicking empty space in the bar turns it into a text field with the path selected and a
 * clear button.
 *
 * <p>Theme type {@code FileNavigationBar}: {@code background}, {@code foreground}, {@code font},
 * {@code hoverBackground}, {@code pressedBackground}, {@code fieldBackground},
 * {@code fieldHoverBackground}, {@code borderColor}, {@code focusColor}, {@code disabledForeground},
 * {@code arc}.
 */
final class NavigationBar extends Canvas {

    // Sizes at 100 %; px() gives them at the current zoom.
    private static final int HEIGHT = 40;
    private static final int MARGIN = 6;
    private static final int BUTTON = 32;
    private static final int BUTTON_GAP = 2;
    private static final int BOX_HEIGHT = 30;
    private static final int BOX_GAP = 8;
    private static final int GLYPH = 16;
    private static final int ICON = 16;
    private static final int CHEVRON = 18;
    private static final int CRUMB_PAD = 6;

    enum Kind {
        BACK, FORWARD, UP, REFRESH, ICON, CHEVRON, CRUMB, OVERFLOW, BOX, CLEAR
    }

    /** A clickable part: a button, the icon, a chevron or a segment (index = its depth), the empty box. */
    record Part(Kind kind, int index, Rectangle bounds) {
    }

    private final FileBrowser browser;
    private Part hover;
    private Part pressed;
    /** The chevron whose drop-down is open (its index, -1 for the places), or null. */
    private Integer openChevron;
    private FolderPopup popup;
    private long popupClosedAt;
    private Integer popupClosedChevron;
    private Text editor;

    @SuppressWarnings("this-escape")
    NavigationBar(FileBrowser browser) {
        super(browser, SWT.DOUBLE_BUFFERED);
        this.browser = browser;
        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseMove, e -> setHover(partAt(e.x, e.y)));
        addListener(SWT.MouseExit, e -> setHover(null));
        addListener(SWT.MouseDown, this::mouseDown);
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.Resize, e -> {
            layoutEditor();
            redraw();
        });
        addListener(SWT.Dispose, e -> closePopup());
        ThemeManager.applyOnCreate(this, "FileNavigationBar");
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        int width = wHint != SWT.DEFAULT ? wHint : px(4 * BUTTON + 3 * BUTTON_GAP + BOX_GAP + 2 * MARGIN + 320);
        return new Point(width, hHint != SWT.DEFAULT ? hHint : px(HEIGHT));
    }

    private int px(int pixels) {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(pixels);
    }

    // ------------------------------------------------------------------ layout

    Rectangle boxBounds() {
        Rectangle area = getClientArea();
        int x = px(MARGIN) + 4 * px(BUTTON) + 3 * px(BUTTON_GAP) + px(BOX_GAP);
        int height = px(BOX_HEIGHT);
        return new Rectangle(x, (area.height - height) / 2, Math.max(0, area.width - x - px(MARGIN)), height);
    }

    /** Every part, left to right, at the current size, path and font. */
    List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        Rectangle area = getClientArea();
        int button = px(BUTTON);
        int y = (area.height - button) / 2;
        int x = px(MARGIN);
        for (Kind kind : List.of(Kind.BACK, Kind.FORWARD, Kind.UP, Kind.REFRESH)) {
            parts.add(new Part(kind, 0, new Rectangle(x, y, button, button)));
            x += button + px(BUTTON_GAP);
        }
        Rectangle box = boxBounds();
        if (editor != null) {
            int clear = px(BUTTON - 4);
            parts.add(new Part(Kind.CLEAR, 0, new Rectangle(box.x + box.width - clear - px(2), box.y + (box.height - clear) / 2, clear, clear)));
            return parts;
        }
        int inner = box.height - px(4);
        int top = box.y + px(2);
        int bx = box.x + px(4);
        int iconWidth = px(ICON) + px(8);
        parts.add(new Part(Kind.ICON, 0, new Rectangle(bx, top, iconWidth, inner)));
        bx += iconWidth;
        List<String> labels = browser.crumbLabels();
        int n = labels.size();
        int[] widths = new int[n];
        GC gc = new GC(this);
        gc.setFont(getFont());
        for (int i = 0; i < n; i++) {
            widths[i] = gc.textExtent(labels.get(i)).x + 2 * px(CRUMB_PAD);
        }
        gc.dispose();
        int chevron = px(CHEVRON);
        int available = box.x + box.width - px(4) - bx;
        int hidden = 0;
        int total = chevron;
        for (int width : widths) {
            total += width + chevron;
        }
        while (total > available && hidden < n - 1) {
            total -= widths[hidden] + chevron;
            hidden++;
        }
        parts.add(new Part(hidden > 0 ? Kind.OVERFLOW : Kind.CHEVRON, hidden > 0 ? hidden : -1, new Rectangle(bx, top, chevron, inner)));
        bx += chevron;
        int right = box.x + box.width - px(4);
        for (int i = hidden; i < n; i++) {
            int width = Math.min(widths[i], Math.max(0, right - chevron - bx));
            parts.add(new Part(Kind.CRUMB, i, new Rectangle(bx, top, width, inner)));
            bx += width;
            parts.add(new Part(Kind.CHEVRON, i, new Rectangle(bx, top, chevron, inner)));
            bx += chevron;
        }
        return parts;
    }

    Part partAt(int x, int y) {
        for (Part part : parts()) {
            if (part.bounds().contains(x, y)) {
                return part;
            }
        }
        Rectangle box = boxBounds();
        return box.contains(x, y) ? new Part(Kind.BOX, 0, box) : null;
    }

    /** The first part of {@code kind} with {@code index}, or null (tests). */
    Part part(Kind kind, int index) {
        return parts().stream().filter(p -> p.kind() == kind && p.index() == index).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ painting

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        BarColors colors = BarColors.of(this);
        Rectangle area = getClientArea();
        gc.setBackground(colors.background());
        gc.fillRectangle(area);
        int arc = px(4);
        int glyph = px(GLYPH);
        for (Part part : parts()) {
            Kind kind = part.kind();
            if (kind != Kind.BACK && kind != Kind.FORWARD && kind != Kind.UP && kind != Kind.REFRESH) {
                continue; // Inside the box, painted over it.
            }
            Rectangle r = part.bounds();
            boolean enabled = enabled(kind);
            if (enabled && (same(part, pressed) || same(part, hover))) {
                gc.setBackground(same(part, pressed) && same(part, hover) ? colors.pressed() : colors.hover());
                gc.fillRoundRectangle(r.x, r.y, r.width, r.height, arc * 2, arc * 2);
            }
            FileIcons.draw(gc, glyph(kind), r.x + (r.width - glyph) / 2, r.y + (r.height - glyph) / 2, glyph,
                    enabled ? colors.foreground() : colors.disabled());
        }
        paintBox(gc, colors);
    }

    private void paintBox(GC gc, BarColors colors) {
        Rectangle box = boxBounds();
        int arc = ThemeManager.get(getDisplay()).styleOf(this).integer("arc").map(a -> px(a)).orElse(px(6));
        boolean boxHover = hover != null && hover.kind() != Kind.BACK && hover.kind() != Kind.FORWARD && hover.kind() != Kind.UP
                && hover.kind() != Kind.REFRESH;
        gc.setBackground(editor != null || !boxHover ? colors.field() : colors.fieldHover());
        gc.fillRoundRectangle(box.x, box.y, box.width - 1, box.height - 1, arc, arc);
        gc.setForeground(editor != null ? colors.focus() : colors.border());
        gc.drawRoundRectangle(box.x, box.y, box.width - 1, box.height - 1, arc, arc);
        if (editor != null) {
            gc.drawLine(box.x + arc / 2, box.y + box.height - 2, box.x + box.width - 1 - arc / 2, box.y + box.height - 2);
        }
        int inner = px(4);
        int glyph = px(GLYPH);
        Rectangle clip = gc.getClipping();
        gc.setClipping(box.x + 1, box.y + 1, box.width - 2, box.height - 2);
        for (Part part : parts()) {
            Rectangle r = part.bounds();
            boolean open = openChevron != null
                    && (part.kind() == Kind.CHEVRON && openChevron == part.index() || part.kind() == Kind.OVERFLOW && openChevron == -2);
            switch (part.kind()) {
                case ICON -> {
                    var image = browser.topIcon(px(ICON));
                    gc.drawImage(image, r.x + (r.width - image.getBounds().width) / 2, r.y + (r.height - image.getBounds().height) / 2);
                }
                case CRUMB, CHEVRON, OVERFLOW, CLEAR -> {
                    if (open || same(part, hover)) {
                        gc.setBackground(open || same(part, pressed) ? colors.pressed() : colors.hover());
                        gc.fillRoundRectangle(r.x, r.y, r.width, r.height, inner, inner);
                    }
                    if (part.kind() == Kind.CRUMB) {
                        String label = browser.crumbLabels().get(part.index());
                        int textHeight = gc.getFontMetrics().getHeight();
                        gc.setForeground(colors.foreground());
                        gc.setClipping(r.intersection(new Rectangle(box.x + 1, box.y + 1, box.width - 2, box.height - 2)));
                        gc.drawText(label, r.x + px(CRUMB_PAD), r.y + (r.height - textHeight) / 2, SWT.DRAW_TRANSPARENT);
                        gc.setClipping(box.x + 1, box.y + 1, box.width - 2, box.height - 2);
                    } else {
                        Glyph g = part.kind() == Kind.CLEAR ? Glyph.CLOSE : part.kind() == Kind.OVERFLOW ? Glyph.OVERFLOW
                                : open ? Glyph.CHEVRON_DOWN : Glyph.CHEVRON_RIGHT;
                        int size = part.kind() == Kind.CHEVRON ? px(12) : glyph * 3 / 4;
                        FileIcons.draw(gc, g, r.x + (r.width - size) / 2, r.y + (r.height - size) / 2, size, colors.foreground());
                    }
                }
                default -> {
                }
            }
        }
        gc.setClipping(clip);
    }

    private static boolean same(Part a, Part b) {
        return a != null && b != null && a.kind() == b.kind() && a.index() == b.index();
    }

    private static Glyph glyph(Kind kind) {
        return switch (kind) {
            case BACK -> Glyph.BACK;
            case FORWARD -> Glyph.FORWARD;
            case UP -> Glyph.UP;
            default -> Glyph.REFRESH;
        };
    }

    private boolean enabled(Kind kind) {
        return switch (kind) {
            case BACK -> browser.canGoBack();
            case FORWARD -> browser.canGoForward();
            case UP -> browser.canGoUp();
            default -> true;
        };
    }

    // ------------------------------------------------------------------ input

    private void setHover(Part part) {
        if (same(part, hover) && (part == null) == (hover == null)) {
            return;
        }
        hover = part;
        setToolTipText(part == null ? null : switch (part.kind()) {
            case BACK -> "Back (Alt+Left Arrow)";
            case FORWARD -> "Forward (Alt+Right Arrow)";
            case UP -> "Up to \"" + browser.parentLabel() + "\" (Alt+Up Arrow)";
            case REFRESH -> "Refresh (F5)";
            case CLEAR -> "Clear";
            case OVERFLOW -> "Show earlier folders";
            default -> null;
        });
        redraw();
    }

    private void mouseDown(Event event) {
        if (event.button != 1) {
            return;
        }
        Part part = partAt(event.x, event.y);
        if (part != null && (part.kind() == Kind.CHEVRON || part.kind() == Kind.OVERFLOW)) {
            // A click on the chevron of the drop-down that just closed (it closed on this click) only closes it.
            int index = part.kind() == Kind.OVERFLOW ? -2 : part.index();
            if (popupClosedChevron != null && popupClosedChevron == index && System.currentTimeMillis() - popupClosedAt < 300) {
                popupClosedChevron = null;
                return;
            }
        }
        pressed = part;
        redraw();
    }

    private void mouseUp(Event event) {
        if (event.button != 1 || pressed == null) {
            return;
        }
        Part part = pressed;
        pressed = null;
        redraw();
        if (same(part, partAt(event.x, event.y))) {
            activate(part);
        }
    }

    /** Does what a click on {@code part} does. */
    void activate(Part part) {
        switch (part.kind()) {
            case BACK -> browser.back();
            case FORWARD -> browser.forward();
            case UP -> browser.up();
            case REFRESH -> browser.refresh();
            case ICON -> browser.navigate(FilePath.TOP);
            case CRUMB -> browser.navigate(browser.getCurrentPath().prefix(part.index()));
            case CHEVRON -> openChevron(part.index());
            case OVERFLOW -> openOverflow(part.index());
            case BOX -> startEditing();
            case CLEAR -> {
                if (editor != null) {
                    editor.setText("");
                    editor.setFocus();
                }
            }
        }
    }

    // ------------------------------------------------------------------ drop-downs

    /** Opens the drop-down of chevron {@code index}: the subfolders at that depth, or the places for -1. */
    void openChevron(int index) {
        closePopup();
        openChevron = index;
        redraw();
        Part part = part(Kind.CHEVRON, index);
        if (index < 0) {
            showPopup(index, part, browser.placeItems());
            return;
        }
        FilePath folder = browser.getCurrentPath().prefix(index);
        browser.listFolders(folder, items -> {
            if (isDisposed() || openChevron == null || openChevron != index) {
                return;
            }
            if (items.isEmpty()) {
                openChevron = null;
                redraw();
                return;
            }
            showPopup(index, part(Kind.CHEVRON, index), items);
        });
    }

    private void openOverflow(int hidden) {
        closePopup();
        openChevron = -2;
        redraw();
        List<FolderPopup.Item> items = new ArrayList<>();
        List<String> labels = browser.crumbLabels();
        for (int i = hidden - 1; i >= 0; i--) {
            FilePath path = browser.getCurrentPath().prefix(i);
            items.add(new FolderPopup.Item(labels.get(i), browser.folderIcon(path), () -> browser.navigate(path)));
        }
        showPopup(-2, part(Kind.OVERFLOW, hidden), items);
    }

    private void showPopup(int index, Part part, List<FolderPopup.Item> items) {
        if (part == null) {
            openChevron = null;
            redraw();
            return;
        }
        popup = FolderPopup.open(this, part.bounds(), items, () -> {
            popupClosedAt = System.currentTimeMillis();
            popupClosedChevron = index;
            popup = null;
            openChevron = null;
            if (!isDisposed()) {
                redraw();
            }
        });
    }

    /** The open drop-down, or null. */
    FolderPopup popup() {
        return popup;
    }

    void closePopup() {
        if (popup != null) {
            popup.close();
        }
        popup = null;
        openChevron = null;
    }

    // ------------------------------------------------------------------ editing the path

    /** Turns the address bar into a text field holding the full path, selected. */
    void startEditing() {
        closePopup();
        if (editor == null) {
            editor = new Text(this, SWT.SINGLE);
            editor.addListener(SWT.DefaultSelection, e -> commitEditing());
            editor.addListener(SWT.Traverse, e -> {
                if (e.detail == SWT.TRAVERSE_ESCAPE) {
                    e.doit = false;
                    stopEditing();
                } else if (e.detail == SWT.TRAVERSE_RETURN) {
                    e.doit = false;
                    commitEditing();
                }
            });
            editor.addListener(SWT.FocusOut, e -> getDisplay().asyncExec(() -> {
                if (editor != null && !editor.isDisposed() && !editor.isFocusControl()) {
                    stopEditing();
                }
            }));
        }
        editor.setText(browser.formattedPath());
        editor.setBackground(BarColors.of(this).field());
        layoutEditor();
        editor.setVisible(true);
        editor.selectAll();
        editor.setFocus();
        redraw();
    }

    private void layoutEditor() {
        if (editor == null) {
            return;
        }
        Rectangle box = boxBounds();
        int height = editor.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
        int clear = px(BUTTON - 4) + px(4);
        editor.setBounds(box.x + px(8), box.y + (box.height - height) / 2, Math.max(0, box.width - px(8) - clear - px(4)), height);
    }

    private void commitEditing() {
        if (editor == null) {
            return;
        }
        String text = editor.getText();
        if (browser.navigateText(text)) {
            stopEditing();
        } else {
            editor.selectAll();
        }
    }

    void stopEditing() {
        if (editor != null && !editor.isDisposed()) {
            Text field = editor;
            editor = null;
            field.dispose();
        }
        editor = null;
        if (!isDisposed()) {
            redraw();
        }
    }

    /** The path field while editing, else null. */
    Text editor() {
        return editor;
    }

    /** Repaints after the browser's state changed. */
    void update(boolean pathChanged) {
        if (pathChanged) {
            closePopup();
            if (editor != null) {
                stopEditing();
            }
        }
        redraw();
    }
}
