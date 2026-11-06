package zapnotes;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.zapthemes.core.ThemeManager;

/** An IDE-style view: a title bar (bold title, optional actions) over its content. */
final class View extends com.zapthemes.swt.widgets.Composite {

    private final ToolBarManager actions = new ToolBarManager(SWT.FLAT | SWT.RIGHT);
    private Control content;

    View(Composite parent, String title, IAction... toolbar) {
        super(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).spacing(0, 0).applyTo(this);
        CLabel label = new com.zapthemes.swt.custom.CLabel(this, SWT.NONE);
        label.setText(title);
        label.setFont(org.eclipse.jface.resource.JFaceResources.getBannerFont());
        // Title bars use the bundled themes' "banner" class, like JFace's dialog banners.
        ThemeManager.get(getDisplay()).setThemeClass(label, "banner");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(label);
        for (IAction action : toolbar) {
            actions.add(action);
        }
        var bar = actions.createControl(this);
        ThemeManager.get(getDisplay()).setThemeClass(bar, "banner");
        GridDataFactory.fillDefaults().applyTo(bar);
        Label separator = new com.zapthemes.swt.widgets.Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridDataFactory.fillDefaults().span(2, 1).applyTo(separator);
    }

    /** Sets the view's content, a child of this view created by the caller. */
    <T extends Control> T content(T control) {
        content = control;
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).applyTo(control);
        layout();
        return control;
    }

    Control content() {
        return content;
    }
}
