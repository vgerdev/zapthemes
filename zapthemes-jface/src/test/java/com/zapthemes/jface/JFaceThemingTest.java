package com.zapthemes.jface;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/** The JFace fork creates proxies, and the theme reaches JFace windows, dialogs and registries. */
class JFaceThemingTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private ThemeManager manager;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(light);
    }

    @AfterEach
    void tearDown() {
        for (Shell shell : display.getShells()) {
            shell.dispose();
        }
    }

    @Test
    void widgetFactoriesCreateProxies() {
        Shell shell = new com.zapthemes.swt.widgets.Shell(display);

        var button = WidgetFactory.button(SWT.PUSH).text("OK").create(shell);
        var label = WidgetFactory.label(SWT.NONE).create(shell);

        assertThat(button).isInstanceOf(com.zapthemes.swt.widgets.Button.class);
        assertThat(label).isInstanceOf(com.zapthemes.swt.widgets.Label.class);
        assertThat(button.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Button");
    }

    @Test
    void windowsAndViewersUseProxies() {
        ApplicationWindow window = new ApplicationWindow(null) {
            {
                addMenuBar();
                addToolBar(SWT.FLAT);
                addStatusLine();
            }

            @Override
            protected Control createContents(Composite parent) {
                TableViewer viewer = new TableViewer(parent);
                viewer.setContentProvider(ArrayContentProvider.getInstance());
                viewer.setLabelProvider(new LabelProvider());
                viewer.setInput(List.of("one", "two"));
                return viewer.getControl();
            }
        };
        window.setBlockOnOpen(false);
        window.open();
        dispatch();

        Shell shell = window.getShell();
        assertThat(shell).isInstanceOf(com.zapthemes.swt.widgets.Shell.class);
        var table = findAll(shell, org.eclipse.swt.widgets.Table.class).getFirst();
        assertThat(table).isInstanceOf(com.zapthemes.swt.widgets.Table.class);
        assertThat(table.getItems()).allMatch(i -> i instanceof com.zapthemes.swt.widgets.TableItem);
        assertThemed(shell);

        manager.setTheme(dark);
        assertThemed(shell);
        window.close();
    }

    @Test
    void dialogsAreThemed() {
        MessageDialog dialog = new MessageDialog(null, "Title", null, "Message", MessageDialog.INFORMATION, 0, "OK", "Cancel");
        dialog.setBlockOnOpen(false);
        dialog.open();
        dispatch();

        assertThat(dialog.getShell()).isInstanceOf(com.zapthemes.swt.widgets.Shell.class);
        assertThemed(dialog.getShell());
        dialog.close();
    }

    @Test
    void jfaceRegistriesFollowTheThemeAndAreRestored() {
        var colors = JFaceResources.getColorRegistry();
        manager.setTheme(dark);
        assertThat(colors.getRGB(JFacePreferences.HYPERLINK_COLOR)).isEqualTo(new RGB(0x59, 0x92, 0xFE));
        assertThat(colors.getRGB(JFacePreferences.ERROR_COLOR)).isEqualTo(new RGB(0xF0, 0x71, 0x78));
        assertThat(JFaceResources.getDialogFont().getFontData()[0].getHeight()).isEqualTo(9);
        assertThat(JFaceResources.getBannerFont().getFontData()[0].getStyle() & SWT.BOLD).isNotZero();

        // A key JFace had a value for gets it back once no theme sets it.
        String key = "com.zapthemes.test.restored";
        colors.put(key, new RGB(0x44, 0x55, 0x66));
        manager.setTheme(ThemeJson.read(new StringReader(
                "{ \"id\": \"keyed\", \"registries\": { \"colors\": { \"" + key + "\": \"#112233\" } } }"), "keyed"));
        assertThat(colors.getRGB(key)).isEqualTo(new RGB(0x11, 0x22, 0x33));
        manager.setTheme(ThemeJson.read(new StringReader("{ \"id\": \"bare\" }"), "bare"));
        assertThat(colors.getRGB(key)).isEqualTo(new RGB(0x44, 0x55, 0x66));
    }

    /** Every control's background is the one its theme rule asks for. */
    private void assertThemed(Shell shell) {
        List<String> problems = new ArrayList<>();
        for (Control control : findAll(shell, Control.class)) {
            if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
                problems.add(control + " untagged");
            }
            manager.styleOf(control).color("background").ifPresent(expected -> {
                if (!control.getBackground().getRGB().equals(expected.rgb)) {
                    problems.add(control + " (" + control.getClass().getName() + ") background "
                            + control.getBackground().getRGB() + " instead of " + expected.rgb);
                }
            });
        }
        assertThat(problems).isEmpty();
    }

    private static <T extends Widget> List<T> findAll(Control root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                found.addAll(findAll(child, type));
            }
        }
        return found;
    }

    private static void dispatch() {
        while (display.readAndDispatch()) {
            // Run pending Skin, layout and paint events.
        }
    }
}
