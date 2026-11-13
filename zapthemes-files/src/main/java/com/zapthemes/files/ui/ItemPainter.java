package com.zapthemes.files.ui;

import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;

import com.zapthemes.core.ThemeManager;

/**
 * Draws the icon and name in the first column of a table or tree, instead of {@code setImage}: the
 * native image list of a table or tree keeps the size of its first image, so icons set as images
 * would not follow the zoom. Rows are made tall enough for the icon at the current zoom; the native
 * background, selection and hover stay.
 */
final class ItemPainter {

    private static final int GAP = 6;
    private static final int PAD = 2;

    private final Control control;
    private final Function<Item, Image> imageOf;
    private final Function<Item, String> textOf;

    private ItemPainter(Control control, Function<Item, Image> imageOf, Function<Item, String> textOf) {
        this.control = control;
        this.imageOf = imageOf;
        this.textOf = textOf;
        control.addListener(SWT.MeasureItem, this::measure);
        control.addListener(SWT.EraseItem, this::erase);
        control.addListener(SWT.PaintItem, this::paint);
    }

    /** Draws {@code imageOf(item)} and the item's text in column 0 of {@code control} (a Table or Tree). */
    static void install(Control control, Function<Item, Image> imageOf) {
        new ItemPainter(control, imageOf, ItemPainter::text);
    }

    /** As {@link #install(Control, Function)}, with the text from {@code textOf} instead of the item's. */
    static void install(Control control, Function<Item, Image> imageOf, Function<Item, String> textOf) {
        new ItemPainter(control, imageOf, textOf);
    }

    private int px(int pixels) {
        return ThemeManager.get(control.getDisplay()).resources().zoomedPixels(pixels);
    }

    private static String text(Item item) {
        return item instanceof TableItem row ? row.getText(0) : item instanceof TreeItem node ? node.getText(0) : item.getText();
    }

    private static org.eclipse.swt.graphics.Font font(Item item) {
        return item instanceof TableItem row ? row.getFont(0) : item instanceof TreeItem node ? node.getFont(0) : null;
    }

    private void measure(Event event) {
        if (event.index != 0) {
            return;
        }
        event.gc.setFont(font((Item) event.item));
        Image image = imageOf.apply((Item) event.item);
        Point text = event.gc.textExtent(textOf.apply((Item) event.item));
        int icon = image != null ? image.getBounds().width + px(GAP) : 0;
        event.width = px(PAD) + icon + text.x + px(PAD);
        int iconHeight = image != null ? image.getBounds().height : 0;
        event.height = Math.max(event.height, Math.max(text.y, iconHeight) + px(6));
    }

    private void erase(Event event) {
        if (event.index == 0) {
            event.detail &= ~SWT.FOREGROUND;
        }
    }

    private void paint(Event event) {
        if (event.index != 0) {
            return;
        }
        Item item = (Item) event.item;
        GC gc = event.gc;
        int x = event.x + px(PAD);
        Image image = imageOf.apply(item);
        if (image != null) {
            Rectangle bounds = image.getBounds();
            gc.drawImage(image, x, event.y + (event.height - bounds.height) / 2);
            x += bounds.width + px(GAP);
        }
        String text = textOf.apply(item);
        // GTK hands over the GC with the font of whatever it drew last.
        gc.setFont(font(item));
        if (!text.isEmpty()) {
            Point extent = gc.textExtent(text);
            gc.drawText(text, x, event.y + (event.height - extent.y) / 2, true);
        }
    }
}
