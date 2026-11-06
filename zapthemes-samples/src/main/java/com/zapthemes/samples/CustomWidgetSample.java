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
import com.zapthemes.swt.widgets.Scale;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * A custom {@link Gauge} next to standard widgets. The app's themes {@code gauge-light} and
 * {@code gauge-dark} extend the bundled ones with a {@code "Gauge"} rule written against the
 * bundled common names, so the gauge follows every theme switch like the rest of the UI.
 */
public final class CustomWidgetSample {

    private static final List<String> THEMES = List.of("gauge-light", "gauge-dark");

    private CustomWidgetSample() {
    }

    public static void main(String[] args) {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        THEMES.forEach(id -> registry.loadResource(CustomWidgetSample.class.getClassLoader(), "com/zapthemes/samples/" + id + ".json"));

        Display display = new Display();
        ThemeManager themes = ThemeManager.get(display);
        themes.setTheme(registry.resolve(THEMES.getLast()));

        Shell shell = new Shell(display);
        shell.setText("Custom widget");
        shell.setLayout(new GridLayout(1, false));
        Gauge gauge = new Gauge(shell, SWT.NONE);
        gauge.setValue(64);
        gauge.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        Scale scale = new Scale(shell, SWT.HORIZONTAL);
        scale.setSelection(64);
        scale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scale.addListener(SWT.Selection, e -> gauge.setValue(scale.getSelection()));
        Button toggle = new Button(shell, SWT.PUSH);
        toggle.setText("Toggle theme");
        toggle.addListener(SWT.Selection, e -> {
            int next = (THEMES.indexOf(themes.getTheme().id()) + 1) % THEMES.size();
            themes.setTheme(registry.resolve(THEMES.get(next)));
        });

        shell.setSize(260, 280);
        shell.open();
        if (List.of(args).contains("--smoke")) {
            int exit = 0;
            for (String id : THEMES) {
                Theme theme = registry.resolve(id);
                themes.setTheme(theme);
                RGBA accent = themes.styleOf(gauge).color("needleColor").orElseThrow();
                if (!gauge.getNeedleColor().getRGBA().equals(accent) || gauge.getThickness() != 10) {
                    System.err.println("SMOKE FAILED: Gauge in " + id + ": " + gauge.getNeedleColor() + ", " + gauge.getThickness());
                    exit = 1;
                }
            }
            System.out.println(exit == 0 ? "SMOKE OK: CustomWidgetSample Gauge themed in " + THEMES.size() + " themes" : "SMOKE FAILED");
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
