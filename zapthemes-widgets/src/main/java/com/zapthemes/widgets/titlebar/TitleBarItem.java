package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;

/**
 * A command on a {@link TitleBar}: in the quick access toolbar, or with {@link SWT#TRAIL} a text
 * button among the right-side slots ("Upgrade your plan").
 *
 * <p>Styles: {@link SWT#PUSH} (default), {@link SWT#CHECK} (drawn as a labeled switch, like
 * Office's AutoSave), {@link SWT#DROP_DOWN} (an arrow part, whose selection event has detail
 * {@link SWT#ARROW}), and {@link SWT#TRAIL}.
 *
 * <p>Every quick-access item is a command users can show or hide from the customize menu; the
 * {@link #getId() id} names it in the preferences file. {@link #getText() text} is its name in menus,
 * its tooltip unless {@link #setToolTipText(String)} says otherwise, and its screen-reader name. An
 * item shows its {@link #setImage(Image) image}, else its {@link #setGlyph(TitleBarGlyph) glyph}
 * (drawn in the bar's foreground, so it follows the theme), else its text.
 */
public class TitleBarItem extends Item {

    private static final int KINDS = SWT.PUSH | SWT.CHECK | SWT.DROP_DOWN;

    private final TitleBar parent;
    private String id;
    private String toolTipText;
    private TitleBarGlyph glyph;
    private boolean enabled = true;
    private boolean selection;
    private boolean shown = true;
    final Rectangle bounds = new Rectangle(0, 0, 0, 0);
    int arrowWidth;

    @SuppressWarnings("this-escape")
    public TitleBarItem(TitleBar parent, int style) {
        super(parent, checkStyle(style));
        this.parent = parent;
        parent.addItem(this);
    }

    private static int checkStyle(int style) {
        int kind = style & KINDS;
        if (kind == 0 || Integer.bitCount(kind) > 1) {
            kind = (style & SWT.CHECK) != 0 ? SWT.CHECK : (style & SWT.DROP_DOWN) != 0 ? SWT.DROP_DOWN : SWT.PUSH;
        }
        return kind | (style & SWT.TRAIL);
    }

    @Override
    protected void checkSubclass() {
        // Items of a canvas widget: subclassing is fine.
    }

    public TitleBar getParent() {
        checkWidget();
        return parent;
    }

    /** The command's id in the preferences file; the text when not set. */
    public String getId() {
        checkWidget();
        return id != null ? id : getText();
    }

    public void setId(String id) {
        checkWidget();
        this.id = id;
        parent.itemChanged(this, true);
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        parent.itemChanged(this, true);
    }

    @Override
    public void setImage(Image image) {
        super.setImage(image);
        parent.itemChanged(this, true);
    }

    public TitleBarGlyph getGlyph() {
        checkWidget();
        return glyph;
    }

    /** A glyph drawn in the bar's foreground, used when the item has no image. */
    public void setGlyph(TitleBarGlyph glyph) {
        checkWidget();
        this.glyph = glyph;
        parent.itemChanged(this, true);
    }

    public String getToolTipText() {
        checkWidget();
        return toolTipText;
    }

    public void setToolTipText(String toolTipText) {
        checkWidget();
        this.toolTipText = toolTipText;
    }

    /** The tooltip shown: the tooltip text, else the text without mnemonics. */
    String tooltip() {
        String tip = toolTipText != null ? toolTipText : getText().replaceAll("&(.)", "$1");
        return tip.isEmpty() ? null : tip;
    }

    public boolean getEnabled() {
        checkWidget();
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        checkWidget();
        this.enabled = enabled;
        parent.itemChanged(this, false);
    }

    /** Whether a {@link SWT#CHECK} item is on. */
    public boolean getSelection() {
        checkWidget();
        return selection;
    }

    public void setSelection(boolean selection) {
        checkWidget();
        if ((getStyle() & SWT.CHECK) != 0) {
            this.selection = selection;
            parent.itemChanged(this, true);
        }
    }

    /** Whether the item shows on the bar; a hidden quick-access item stays in the customize menu. */
    public boolean isShown() {
        checkWidget();
        return shown;
    }

    public void setShown(boolean shown) {
        checkWidget();
        if (this.shown != shown) {
            this.shown = shown;
            parent.itemShownChanged(this);
        }
    }

    /** Bounds in the bar, empty while the item is hidden or doesn't fit. */
    public Rectangle getBounds() {
        checkWidget();
        return new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    boolean isTrail() {
        return (getStyle() & SWT.TRAIL) != 0;
    }

    boolean isCheck() {
        return (getStyle() & SWT.CHECK) != 0;
    }

    boolean isDropDown() {
        return (getStyle() & SWT.DROP_DOWN) != 0;
    }

    /** Text without mnemonics, for menus and screen readers. */
    String plainText() {
        return getText().replaceAll("&(.)", "$1");
    }

    public void addSelectionListener(SelectionListener listener) {
        checkWidget();
        if (listener == null) {
            SWT.error(SWT.ERROR_NULL_ARGUMENT);
        }
        addTypedListener(listener, SWT.Selection);
    }

    public void removeSelectionListener(SelectionListener listener) {
        checkWidget();
        removeTypedListener(SWT.Selection, listener);
    }

    /**
     * Does what a click does: a check item flips, and listeners get {@link SWT#Selection} (detail
     * {@link SWT#ARROW} when {@code arrow}, for the arrow of a drop-down item).
     */
    public void activate(boolean arrow) {
        checkWidget();
        if (!enabled) {
            return;
        }
        if (isCheck()) {
            selection = !selection;
            parent.itemChanged(this, true);
        }
        Event event = new Event();
        event.detail = arrow && isDropDown() ? SWT.ARROW : 0;
        event.x = bounds.x;
        event.y = bounds.y + bounds.height;
        notifyListeners(SWT.Selection, event);
    }

    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        super.dispose();
        if (!parent.isDisposed()) {
            parent.removeItem(this);
        }
    }
}
