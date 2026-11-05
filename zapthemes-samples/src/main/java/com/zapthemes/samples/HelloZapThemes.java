package com.zapthemes.samples;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;

/**
 * The smallest ZapThemes app. It loads the bundled themes, picks the one that matches the desktop
 * (high contrast, dark or light), and creates its widgets from {@code com.zapthemes.swt.widgets}
 * instead of {@code org.eclipse.swt.widgets}: each widget is themed as it is created, and the button
 * switches themes live. Layouts, events and graphics are plain SWT.
 */
public final class HelloZapThemes {

    private HelloZapThemes() {
    }

    public static void main(String[] args) {
        Display display = new Display();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager themes = ThemeManager.get(display);
        // Set the theme before creating widgets, so they are born in it.
        themes.setTheme(registry.resolve(BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme())));

        Shell shell = new Shell(display);
        shell.setText("Hello ZapThemes");
        shell.setLayout(new GridLayout(2, false));

        new Label(shell, SWT.NONE).setText("Name:");
        Text name = new Text(shell, SWT.BORDER);
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Table table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        for (String title : new String[] { "Widget", "Package" }) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(title);
            column.setWidth(160);
        }
        for (String widget : new String[] { "Button", "Table", "Text", "CTabFolder" }) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(new String[] { widget, widget.startsWith("C") ? "com.zapthemes.swt.custom" : "com.zapthemes.swt.widgets" });
        }

        Button next = new Button(shell, SWT.PUSH);
        next.setText("Next theme");
        next.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));
        next.addListener(SWT.Selection, e -> {
            int current = BundledThemes.IDS.indexOf(themes.getTheme().id());
            Theme theme = registry.resolve(BundledThemes.IDS.get((current + 1) % BundledThemes.IDS.size()));
            themes.setTheme(theme);
            shell.setText("Hello ZapThemes — " + theme.name());
        });

        shell.setSize(420, 320);
        shell.open();
        if (List.of(args).contains("--smoke")) {
            int exit = 0;
            for (String id : BundledThemes.IDS) {
                themes.setTheme(registry.resolve(id));
                RGBA expected = themes.styleOf(table).color("background").orElseThrow();
                if (!table.getBackground().getRGBA().equals(expected)) {
                    System.err.println("SMOKE FAILED: Table background in " + id + " is " + table.getBackground());
                    exit = 1;
                }
            }
            System.out.println(exit == 0 ? "SMOKE OK: HelloZapThemes in " + BundledThemes.IDS.size() + " themes" : "SMOKE FAILED");
            display.dispose();
            System.exit(exit);
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }
}
