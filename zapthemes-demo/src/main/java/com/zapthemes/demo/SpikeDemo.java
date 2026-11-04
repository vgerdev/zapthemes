package com.zapthemes.demo;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;

/**
 * Phase 0 spike: a standalone {@link Display}/{@link Shell} app built only from proxies, with a
 * button that switches between the bundled light and dark themes. {@code --smoke} builds the UI,
 * switches themes, checks every control and exits (non-zero on failure).
 */
public final class SpikeDemo {

    private SpikeDemo() {
    }

    public static void main(String[] args) {
        boolean smoke = List.of(args).contains("--smoke");
        Display display = new Display();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        Theme light = registry.resolve("zap-light");
        Theme dark = registry.resolve("zap-dark");
        ThemeManager themes = ThemeManager.get(display);
        themes.setTheme(light);

        Shell shell = new Shell(display);
        shell.setText("ZapThemes — Phase 0 spike");
        shell.setLayout(new GridLayout(1, false));

        Composite bar = new Composite(shell, SWT.NONE);
        bar.setLayout(new GridLayout(3, false));
        new Label(bar, SWT.NONE).setText("Theme:");
        Combo picker = new Combo(bar, SWT.READ_ONLY);
        picker.setItems(light.name(), dark.name());
        picker.select(0);
        Button toggle = new Button(bar, SWT.PUSH);
        toggle.setText("Switch theme");

        CTabFolder tabs = new CTabFolder(shell, SWT.BORDER);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        CTabItem formTab = new CTabItem(tabs, SWT.NONE); // zapthemes:allow-raw (no CTabItem proxy in the spike yet)
        formTab.setText("Form");
        CTabItem tableTab = new CTabItem(tabs, SWT.NONE); // zapthemes:allow-raw
        tableTab.setText("Table");

        Group form = new Group(tabs, SWT.NONE);
        form.setText("Person");
        form.setLayout(new GridLayout(2, false));
        new Label(form, SWT.NONE).setText("Name");
        new Text(form, SWT.BORDER).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(form, SWT.NONE).setText("Subscribed");
        new Button(form, SWT.CHECK).setText("Newsletter");
        formTab.setControl(form);

        Table table = new Table(tabs, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        for (String title : new String[] { "Widget", "Type" }) {
            TableColumn column = new TableColumn(table, SWT.NONE); // zapthemes:allow-raw
            column.setText(title);
            column.setWidth(160);
        }
        for (String name : new String[] { "Button", "Text", "Table", "CTabFolder" }) {
            new TableItem(table, SWT.NONE).setText(new String[] { name, "proxy" });
        }
        tableTab.setControl(table);
        tabs.setSelection(0);

        Runnable apply = () -> themes.setTheme(picker.getSelectionIndex() == 0 ? light : dark);
        picker.addListener(SWT.Selection, e -> apply.run());
        toggle.addListener(SWT.Selection, e -> {
            picker.select(1 - picker.getSelectionIndex());
            apply.run();
        });

        shell.setSize(520, 360);
        shell.open();

        int exitCode = 0;
        if (smoke) {
            exitCode = smokeTest(display, shell, themes, dark);
            shell.dispose();
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
        if (smoke) {
            System.exit(exitCode);
        }
    }

    private static int smokeTest(Display display, Shell shell, ThemeManager themes, Theme dark) {
        while (display.readAndDispatch()) {
            // Let pending Skin/layout events run.
        }
        themes.setTheme(dark);
        List<String> problems = new ArrayList<>();
        check(shell, problems);
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: every control tagged and themed with " + dark.id());
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private static void check(Control control, List<String> problems) {
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        var bg = control.getBackground().getRGB();
        if (bg.red > 0x40 || bg.green > 0x40 || bg.blue > 0x40) {
            problems.add(control + " is not dark: " + bg);
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                check(child, problems);
            }
        }
    }
}
