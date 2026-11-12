package com.zapthemes.widgets.options;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;

/**
 * An app's Options window, like Office's <em>Word Options</em>: the pages are listed down the
 * left, and the selected page fills the right, in the one window. OK applies every page that was
 * shown, then closes; Cancel closes without applying.
 *
 * <pre>{@code
 * new OptionsDialog(shell, "Word Options")
 *         .addPage("general", "General", "General options for working with Word.", generalPage)
 *         .addPage("ribbon", "Customize Ribbon", "Customize the Ribbon.", new CustomizeRibbonDialog(ribbon))
 *         .addPage("qat", "Quick Access Toolbar", "Customize the Quick Access Toolbar.", bar.quickAccessPage())
 *         .open("ribbon");
 * }</pre>
 *
 * <p>Pages are built when they are first shown, so a window with many pages opens quickly.
 */
public final class OptionsDialog {

    private record Page(String id, String label, String description, OptionsPage page) {
    }

    private final org.eclipse.swt.widgets.Shell parent;
    private final String title;
    private final List<Page> pages = new ArrayList<>();
    private final Set<Page> created = new LinkedHashSet<>();
    private com.zapthemes.swt.widgets.Shell shell;
    private com.zapthemes.swt.widgets.List navigation;
    private Composite pageArea;
    private StackLayout stack;
    private final List<Composite> pageComposites = new ArrayList<>();
    private boolean applied;

    public OptionsDialog(org.eclipse.swt.widgets.Shell parent, String title) {
        this.parent = parent;
        this.title = title;
    }

    /**
     * Adds a page under {@code label} in the navigation list; {@code description}, if not null,
     * heads the page, as Office's do ("Customize the Ribbon.").
     */
    public OptionsDialog addPage(String id, String label, String description, OptionsPage page) {
        pages.add(new Page(id, label, description, page));
        return this;
    }

    /** Opens the window on the first page and waits until it closes; returns whether OK was pressed. */
    public boolean open() {
        return open(null);
    }

    /** Opens the window on page {@code id} (the first page when null or unknown) and waits until it closes. */
    public boolean open(String id) {
        create(id);
        shell.open();
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch()) {
                shell.getDisplay().sleep();
            }
        }
        return applied;
    }

    /** Builds the window on page {@code id} without opening it (tests). */
    Shell create(String id) {
        shell = new com.zapthemes.swt.widgets.Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText(title);
        shell.setLayout(new GridLayout(2, false));

        navigation = new com.zapthemes.swt.widgets.List(shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
        GridData navigationData = new GridData(SWT.FILL, SWT.FILL, false, true);
        navigationData.widthHint = 150;
        navigation.setLayoutData(navigationData);
        pages.forEach(p -> navigation.add(p.label()));
        navigation.addListener(SWT.Selection, e -> show(navigation.getSelectionIndex()));

        pageArea = new Composite(shell, SWT.NONE);
        stack = new StackLayout();
        pageArea.setLayout(stack);
        pageArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (int i = 0; i < pages.size(); i++) {
            pageComposites.add(null);
        }

        Composite buttons = new Composite(shell, SWT.NONE);
        GridLayout buttonLayout = new GridLayout(2, true);
        buttonLayout.marginWidth = 0;
        buttons.setLayout(buttonLayout);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        Button ok = button(buttons, "OK", this::ok);
        button(buttons, "Cancel", shell::close);
        shell.setDefaultButton(ok);

        int first = Math.max(0, indexOf(id));
        if (!pages.isEmpty()) {
            navigation.select(first);
            show(first);
        }
        Rectangle owner = parent.getBounds();
        fit(new Point(owner.width / 2, owner.height / 2));
        return shell;
    }

    /**
     * Sizes the window to at least {@code minimum} and at least what its contents need, but no
     * larger than the monitor's work area, centered on the main window and kept on the monitor.
     */
    private void fit(Point minimum) {
        Point needed = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        Rectangle screen = parent.getMonitor().getClientArea();
        int width = Math.min(Math.max(minimum.x, needed.x), screen.width);
        int height = Math.min(Math.max(minimum.y, needed.y), screen.height);
        Rectangle owner = parent.getBounds();
        int x = owner.x + (owner.width - width) / 2;
        int y = owner.y + (owner.height - height) / 2;
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - width));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - height));
        shell.setBounds(x, y, width, height);
    }

    private int indexOf(String id) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Shows page {@code index} on the right, building it the first time. */
    private void show(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        Page page = pages.get(index);
        Composite composite = pageComposites.get(index);
        if (composite == null) {
            composite = new Composite(pageArea, SWT.NONE);
            GridLayout layout = new GridLayout(1, false);
            layout.marginWidth = 8;
            layout.marginHeight = 4;
            composite.setLayout(layout);
            if (page.description() != null) {
                Label heading = new Label(composite, SWT.WRAP);
                heading.setText(page.description());
                heading.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            }
            Composite contents = new Composite(composite, SWT.NONE);
            contents.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            page.page().create(contents);
            pageComposites.set(index, composite);
            created.add(page);
            stack.topControl = composite;
            if (shell.getSize().x > 0) {
                // A page built after the window was sized: grow the window if the page needs it.
                fit(shell.getSize());
            }
        }
        stack.topControl = composite;
        pageArea.layout(true, true);
        if (navigation.getSelectionIndex() != index) {
            navigation.select(index);
        }
    }

    private void ok() {
        for (Page page : created) {
            page.page().apply();
        }
        applied = true;
        shell.close();
    }

    private static Button button(Composite parent, String text, Runnable run) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.widthHint = 80;
        button.setLayoutData(data);
        button.addListener(SWT.Selection, e -> run.run());
        return button;
    }

    // ------------------------------------------------------------------ for tests

    Shell shell() {
        return shell;
    }

    com.zapthemes.swt.widgets.List navigation() {
        return navigation;
    }

    /** Selects page {@code id} as a click in the navigation list would (tests). */
    void select(String id) {
        int index = indexOf(id);
        navigation.select(index);
        show(index);
    }

    /** The page now showing (tests). */
    OptionsPage current() {
        int index = pageComposites.indexOf(stack.topControl);
        return index < 0 ? null : pages.get(index).page();
    }

    /** Presses OK (tests). */
    void pressOk() {
        ok();
    }
}
