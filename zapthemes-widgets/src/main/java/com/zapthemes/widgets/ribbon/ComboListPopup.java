package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.swt.widgets.List;

/** The list of a {@link RibbonComboBox}: a click or Enter picks, Esc closes. */
final class ComboListPopup extends PopupShell {

    private final RibbonComboBox combo;
    private final int width;
    private List list;

    ComboListPopup(RibbonSurface origin, RibbonComboBox combo, Rectangle field) {
        super(origin, field);
        this.combo = combo;
        this.width = field.width;
    }

    @Override
    Control createContent() {
        list = new List(shell, SWT.SINGLE | SWT.V_SCROLL);
        combo.items().forEach(list::add);
        int index = combo.items().indexOf(combo.text());
        if (index >= 0) {
            list.select(index);
            list.showSelection();
        }
        list.addListener(SWT.MouseUp, e -> commit());
        list.addListener(SWT.DefaultSelection, e -> commit());
        list.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                e.doit = false;
                close();
                origin.setFocus();
            } else if (e.detail == SWT.TRAVERSE_RETURN) {
                e.doit = false;
                commit();
            }
        });
        return list;
    }

    List list() {
        return list;
    }

    @Override
    Point size() {
        int visible = Math.min(12, Math.max(1, combo.items().size()));
        Point preferred = list.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        int height = list.getItemHeight() * visible + 2 * list.getBorderWidth() + 4;
        return new Point(Math.max(width, preferred.x + 4), Math.min(preferred.y + 4, height));
    }

    void commit() {
        int index = list.getSelectionIndex();
        close();
        if (index >= 0) {
            if (!origin.isDisposed()) {
                origin.setFocus();
            }
            combo.commit(combo.items().get(index));
        }
    }
}
