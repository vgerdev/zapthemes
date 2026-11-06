package com.zapthemes.samples;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonElement;
import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/**
 * A JFace {@link ApplicationWindow} on the forked JFace: no ZapThemes types in sight, because
 * zapthemes-jface is a drop-in {@code org.eclipse.jface} whose viewers, dialogs and managers create
 * proxies. A Theme menu switches themes; a {@link ThemeChangeListener} refreshes the table, which
 * lists the current theme's common names.
 */
public final class JFaceSample extends ApplicationWindow {

    private final ThemeManager themes;
    private final ThemeRegistry registry;
    private TableViewer viewer;

    private JFaceSample(ThemeManager themes, ThemeRegistry registry) {
        super(null);
        this.themes = themes;
        this.registry = registry;
        addMenuBar();
        addStatusLine();
    }

    public static void main(String[] args) {
        Display display = new Display();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager themes = ThemeManager.get(display);
        themes.setTheme(registry.resolve(BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme())));

        JFaceSample window = new JFaceSample(themes, registry);
        if (List.of(args).contains("--smoke")) {
            window.setBlockOnOpen(false);
            window.open();
            int exit = window.smokeTest();
            window.close();
            display.dispose();
            System.exit(exit);
        }
        window.setBlockOnOpen(true);
        window.open();
        display.dispose();
    }

    @Override
    protected MenuManager createMenuManager() {
        MenuManager bar = new MenuManager();
        MenuManager menu = new MenuManager("&Theme");
        for (String id : BundledThemes.IDS) {
            Theme theme = registry.resolve(id);
            Action action = new Action(theme.name(), IAction.AS_RADIO_BUTTON) {
                @Override
                public void run() {
                    if (isChecked()) {
                        themes.setTheme(theme);
                    }
                }
            };
            action.setChecked(id.equals(themes.getTheme().id()));
            menu.add(action);
        }
        bar.add(menu);
        return bar;
    }

    @Override
    protected Control createContents(Composite parent) {
        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        column("Common name", 160, entry -> entry.getKey());
        column("Value", 220, entry -> entry.getValue().toString());

        ThemeChangeListener refresh = (previous, current) -> showNames(current);
        themes.addThemeChangeListener(refresh);
        viewer.getTable().addDisposeListener(e -> themes.removeThemeChangeListener(refresh));
        showNames(themes.getTheme());
        return viewer.getControl();
    }

    private void column(String title, int width, java.util.function.Function<Map.Entry<String, JsonElement>, String> text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            @SuppressWarnings("unchecked")
            public String getText(Object element) {
                return text.apply((Map.Entry<String, JsonElement>) element);
            }
        });
    }

    private void showNames(Theme theme) {
        viewer.setInput(theme.names().entrySet().toArray());
        setStatus(theme.name() + ": " + theme.names().size() + " common names");
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ZapThemes — JFace sample");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(480, 420);
    }

    @Override
    protected void initializeBounds() {
        // Upstream JFace on Windows: the menu bar resizes the shell before it opens, and Window then
        // skips getInitialSize(). Forget that resize.
        resizeHasOccurred = false;
        super.initializeBounds();
    }

    private int smokeTest() {
        int exit = 0;
        for (String id : BundledThemes.IDS) {
            themes.setTheme(registry.resolve(id));
            RGBA expected = themes.styleOf(viewer.getTable()).color("background").orElseThrow();
            if (!viewer.getTable().getBackground().getRGBA().equals(expected) || viewer.getTable().getItemCount() == 0) {
                System.err.println("SMOKE FAILED: table in " + id + ": " + viewer.getTable().getBackground());
                exit = 1;
            }
        }
        System.out.println(exit == 0 ? "SMOKE OK: JFaceSample in " + BundledThemes.IDS.size() + " themes" : "SMOKE FAILED");
        return exit;
    }
}
