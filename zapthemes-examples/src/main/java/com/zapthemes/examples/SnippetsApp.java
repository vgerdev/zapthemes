package com.zapthemes.examples;

import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;

/**
 * The JFace snippets on their own: a {@link SnippetBrowser} with a theme picker. Starts in the theme
 * {@code -Dzapthemes.theme} names, or the one matching the desktop.
 */
public final class SnippetsApp {

    private SnippetsApp() {
    }

    public static void main(String[] args) {
        Display display = new Display();
        List<Theme> available = ThemeBar.start(display);
        Shell shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setText("ZapThemes — JFace snippets");
        GridLayoutFactory.fillDefaults().applyTo(shell);
        ThemeBar bar = new ThemeBar(shell, ThemeManager.get(display), available);
        GridDataFactory.fillDefaults().indent(8, 8).applyTo(bar);
        SnippetBrowser browser = new SnippetBrowser(shell, bar::current);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(browser);
        shell.setSize(520, 640);
        shell.open();
        if (List.of(args).contains("--smoke")) {
            int count = browser.snippetCount();
            System.out.println(count > 0 ? "SMOKE OK: SnippetsApp lists " + count + " snippets" : "SMOKE FAILED: no snippets");
            display.dispose();
            System.exit(count > 0 ? 0 : 1);
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }
}
