package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;

class ThemeManagerTest {

    private static final RGB LIGHT_BG = new RGB(0xF7, 0xF8, 0xFA);
    private static final RGB DARK_BG = new RGB(0x1E, 0x1F, 0x22);
    private static final RGB DARK_SURFACE = new RGB(0x2B, 0x2D, 0x30);

    private static Display display;
    private static Theme light;
    private static Theme dark;

    private ThemeManager manager;
    private Shell shell;

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
        shell = new Shell(display);
    }

    @AfterEach
    void tearDown() {
        for (var s : display.getShells()) {
            s.dispose();
        }
    }

    @Test
    void proxiesAreTaggedAndThemedOnCreation() {
        Button button = new Button(shell, SWT.PUSH);
        Table table = new Table(shell, SWT.BORDER);

        assertThat(button.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Button");
        assertThat(table.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Table");
        assertThat(shell.getBackground().getRGB()).isEqualTo(LIGHT_BG);
        assertThat(table.getHeaderBackground().getRGB()).isEqualTo(new RGB(0xEB, 0xEC, 0xF0));
    }

    @Test
    void setThemeRethemesEveryWidgetOfEveryShell() {
        Composite composite = new Composite(shell, SWT.NONE);
        Label label = new Label(composite, SWT.NONE);
        Text text = new Text(composite, SWT.BORDER);
        Table table = new Table(composite, SWT.NONE);
        TableItem item = new TableItem(table, SWT.NONE);
        CTabFolder folder = new CTabFolder(shell, SWT.NONE);
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM);
        Label dialogLabel = new Label(dialog, SWT.NONE);

        manager.setTheme(dark);

        for (Control control : List.of(shell, composite, label, dialog, dialogLabel, folder)) {
            assertThat(control.getBackground().getRGB()).as(control.toString()).isEqualTo(DARK_BG);
        }
        assertThat(text.getBackground().getRGB()).isEqualTo(DARK_SURFACE);
        assertThat(item.getBackground().getRGB()).isEqualTo(DARK_SURFACE);
        assertThat(folder.getSelectionBackground().getRGB()).isEqualTo(DARK_SURFACE);
    }

    @Test
    void widgetsCreatedWithoutProxiesAreThemedThroughSkinEvents() {
        var raw = new org.eclipse.swt.widgets.Label(shell, SWT.NONE); // zapthemes:allow-raw
        assertThat(raw.getData(ThemeKeys.THEME_CONTROL_TYPE)).isNull();

        while (display.readAndDispatch()) {
            // SWT sends SWT.Skin for new widgets while dispatching.
        }

        assertThat(raw.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Label");
        assertThat(raw.getBackground().getRGB()).isEqualTo(LIGHT_BG);
    }

    @Test
    void subclassWhoseSetterFailsDuringConstructionIsThemedAfterwards() {
        FragileView view = new FragileView(shell);

        while (display.readAndDispatch()) {
            // The ThemeManager retries from asyncExec.
        }

        assertThat(view.getBackground().getRGB()).isEqualTo(LIGHT_BG);
        assertThat(view.applied).containsExactly(LIGHT_BG);
    }

    @Test
    void repeatedThemeSwitchesDoNotLeakFonts() {
        int before = manager.resources().fontCount();
        new Label(shell, SWT.NONE);
        for (int i = 0; i < 50; i++) {
            manager.setTheme(i % 2 == 0 ? dark : light);
        }
        // Fonts are cached per display, so other tests' fonts may be there already.
        assertThat(manager.resources().fontCount() - before).isLessThanOrEqualTo(1);
    }

    @Test
    void userSubclassesKeepTheirProxyType() {
        Widget anonymous = new Composite(shell, SWT.NONE) {
        };
        assertThat(anonymous.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Composite");
    }

    /** Overrides a setter that reads a field its own constructor has not initialized yet. */
    static final class FragileView extends Composite {
        private final List<RGB> applied;

        FragileView(org.eclipse.swt.widgets.Composite parent) {
            super(parent, SWT.NONE);
            applied = new ArrayList<>();
        }

        @Override
        public void setBackground(org.eclipse.swt.graphics.Color color) {
            super.setBackground(color);
            applied.add(color.getRGB()); // NPE while super(...) runs: 'applied' is still null
        }
    }

    /** A font's family list takes the first one installed; a single family is used as written. */
    @Test
    void aFontFamilyListTakesTheFirstInstalled() {
        String installed = display.getFontList(null, true)[0].getName();
        var resources = manager.resources();

        var listed = resources.font(new com.zapthemes.core.model.FontSpec(
                List.of("No Such Font ZapThemes", installed), 11, SWT.NORMAL));
        var none = resources.font(new com.zapthemes.core.model.FontSpec(
                List.of("No Such Font ZapThemes", "Nor This One"), 11, SWT.NORMAL));

        assertThat(listed.getFontData()[0].getName()).isEqualTo(installed);
        assertThat(none).as("none installed: the first, as the platform substitutes it").isNotNull();
    }
}
