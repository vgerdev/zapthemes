package com.zapthemes.samples;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeValidator;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * An application theme on top of a bundled one: {@code acme-dark.json} extends {@code zap-dark},
 * changes the accent, and adds style classes that widgets opt into with
 * {@link ThemeManager#setThemeClass}. The theme is checked with {@link ThemeValidator} as it loads.
 *
 * <p>Pass a path to a theme file to try your own ({@code ./gradlew :zapthemes-samples:runCustomTheme
 * --args=my-theme.json}); it may extend any bundled theme.
 */
public final class CustomThemeSample {

    private CustomThemeSample() {
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        boolean smoke = options.contains("--smoke");

        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        Theme loaded = options.stream().filter(a -> !a.startsWith("--")).findFirst()
                .map(file -> registry.loadFile(Path.of(file)))
                .orElseGet(() -> registry.loadResource(CustomThemeSample.class.getClassLoader(), "com/zapthemes/samples/acme-dark.json"));
        Theme theme = registry.resolve(loaded.id());
        List<String> problems = ThemeValidator.validate(theme);
        problems.forEach(problem -> System.err.println(loaded.id() + ": " + problem));

        Display display = new Display();
        ThemeManager themes = ThemeManager.get(display);
        themes.setTheme(theme);

        Shell shell = new Shell(display);
        shell.setText(theme.name());
        shell.setLayout(new GridLayout(2, false));

        Label headline = new Label(shell, SWT.NONE);
        headline.setText("Delete project?");
        headline.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        themes.setThemeClass(headline, "headline");

        // Matched by the style-bit rule "Label[WRAP]".
        Label detail = new Label(shell, SWT.WRAP);
        detail.setText("Style classes come from the theme: this app's code only names them.");
        detail.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Button cancel = new Button(shell, SWT.PUSH);
        cancel.setText("Cancel");
        cancel.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        Button delete = new Button(shell, SWT.PUSH);
        delete.setText("Delete");
        themes.setThemeClass(delete, "danger");
        cancel.addListener(SWT.Selection, e -> shell.close());
        delete.addListener(SWT.Selection, e -> shell.close());

        shell.pack();
        shell.open();
        if (smoke) {
            boolean ok = problems.isEmpty() && delete.getBackground().getRGB().equals(new RGB(0xC6, 0x28, 0x28))
                    && headline.getForeground().getRGB().equals(new RGB(0xE8, 0x59, 0x0C));
            System.out.println(ok ? "SMOKE OK: CustomThemeSample classes applied" : "SMOKE FAILED: " + problems
                    + ", delete " + delete.getBackground() + ", headline " + headline.getForeground());
            display.dispose();
            System.exit(ok ? 0 : 1);
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }
}
