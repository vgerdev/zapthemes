package com.zapthemes.examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.examples.controlexample.ControlExample;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;

/**
 * Eclipse's SWT ControlExample and the JFace snippets in one window, on ZapThemes: a theme picker
 * over two tabs. Both upstream programs are unchanged but for the codemod that makes their widgets
 * ZapThemes proxies, as an existing application would be migrated.
 *
 * <p>{@code --smoke} selects every ControlExample tab in every bundled theme and checks that each
 * control is tagged; background colors that differ from the theme are listed, not failed, since the
 * example sets colors on purpose. {@code --screenshots <dir>} saves every tab as PNG.
 */
public final class ExamplesApp {

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available;
    private final Shell shell;
    private final CTabFolder folder;
    private final ControlExample controls;

    private ExamplesApp(Display display) {
        this.display = display;
        available = ThemeBar.start(display);
        themes = ThemeManager.get(display);
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setText("ZapThemes — SWT ControlExample and JFace snippets");
        GridLayoutFactory.fillDefaults().applyTo(shell);
        ThemeBar bar = new ThemeBar(shell, themes, available);
        GridDataFactory.fillDefaults().indent(8, 8).applyTo(bar);

        folder = new com.zapthemes.swt.custom.CTabFolder(shell, SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(folder);
        Composite controlPage = new com.zapthemes.swt.widgets.Composite(folder, SWT.NONE);
        controlPage.setLayout(new FillLayout());
        controls = new ControlExample(controlPage);
        tab("SWT Controls", controlPage);
        tab("JFace Snippets", new SnippetBrowser(folder, bar::current));
        folder.setSelection(0);
        shell.addDisposeListener(e -> controls.dispose());
    }

    private void tab(String text, Control page) {
        CTabItem item = new com.zapthemes.swt.custom.CTabItem(folder, SWT.NONE);
        item.setText(text);
        item.setControl(page);
    }

    public static void main(String[] args) throws Exception {
        List<String> options = List.of(args);
        Display display = new Display();
        ExamplesApp app = new ExamplesApp(display);
        app.shell.setSize(1100, 800);
        app.shell.open();
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            int exit = app.verify(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            display.dispose();
            System.exit(exit);
        }
        while (!app.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    private int verify(Path screenshots) throws Exception {
        TabFolder tabs = find(shell, TabFolder.class);
        List<String> untagged = new ArrayList<>();
        List<String> colored = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            folder.setSelection(0);
            for (int i = 0; i < tabs.getItemCount(); i++) {
                tabs.setSelection(i);
                settle();
                String page = theme.id() + " / " + tabs.getItem(i).getText();
                checked += check(page, tabs.getItem(i).getControl(), untagged, colored);
                screenshot(screenshots, "controls-" + theme.id() + "-" + tabs.getItem(i).getText().toLowerCase().replaceAll("[^a-z]+", ""));
            }
            folder.setSelection(1);
            settle();
            checked += check(theme.id() + " / JFace Snippets", folder.getItem(1).getControl(), untagged, colored);
            screenshot(screenshots, "snippets-" + theme.id());
        }
        colored.forEach(c -> System.out.println("NOTE: " + c));
        untagged.forEach(c -> System.out.println("SMOKE FAIL: " + c));
        int snippets = SnippetBrowser.snippets().values().stream().mapToInt(List::size).sum();
        if (untagged.isEmpty() && snippets > 0) {
            System.out.println("SMOKE OK: " + checked + " controls on " + tabs.getItemCount() + " ControlExample tabs tagged, in "
                    + available.size() + " themes (" + colored.size() + " with their own background); " + snippets + " snippets listed");
            return 0;
        }
        return 1;
    }

    private int check(String where, Control control, List<String> untagged, List<String> colored) {
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            untagged.add(where + ": " + control.getClass().getName() + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        themes.styleOf(control).color("background").ifPresent(expected -> {
            if (!control.getBackground().getRGB().equals(expected.rgb)) {
                colored.add(where + ": " + control.getClass().getSimpleName() + " " + control.getBackground().getRGB() + ", theme " + expected.rgb);
            }
        });
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(where, child, untagged, colored);
            }
        }
        return count;
    }

    private static <T> T find(Control root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void settle() {
        for (int i = 0; i < 20; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
            display.timerExec(25, () -> { });
            display.sleep();
        }
    }

    private void screenshot(Path dir, String name) throws Exception {
        if (dir == null) {
            return;
        }
        Files.createDirectories(dir);
        var area = shell.getClientArea();
        Image image = new Image(display, area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        loader.save(dir.resolve(name + ".png").toString(), SWT.IMAGE_PNG);
        image.dispose();
    }
}
