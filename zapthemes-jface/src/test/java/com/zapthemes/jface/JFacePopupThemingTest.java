package com.zapthemes.jface;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.FocusCellOwnerDrawHighlighter;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerFocusCellManager;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.SystemColors;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/**
 * JFace's tooltips, hovers, popups, status line and focus-cell highlight take their colors from
 * the theme (its {@code systemColors} and {@code colors} registries), also after a theme switch.
 */
class JFacePopupThemingTest {

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
        manager.setTheme(dark);
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
    }

    @AfterEach
    void tearDown() {
        for (Shell open : display.getShells()) {
            open.dispose();
        }
    }

    @Test
    void defaultToolTipUsesTheThemesInfoColors() {
        Label anchor = new com.zapthemes.swt.widgets.Label(shell, SWT.NONE);
        anchor.setText("anchor");
        shell.open();
        DefaultToolTip tip = new DefaultToolTip(anchor, ToolTip.RECREATE, true);
        tip.setText("A tooltip");

        Set<Shell> before = Set.of(display.getShells());
        tip.show(new Point(0, 0));
        // Right away: without a real mouse over the anchor, the tooltip hides on the next events.
        Shell popup = newShell(before);
        Control label = findAll(popup, org.eclipse.swt.custom.CLabel.class).getFirst();

        assertThat(label.getBackground().getRGB()).isEqualTo(system("COLOR_INFO_BACKGROUND"));
        assertThat(label.getForeground().getRGB()).isEqualTo(system("COLOR_INFO_FOREGROUND"));
    }

    @Test
    void controlDecorationHoverUsesTheThemesInfoColors() {
        Text text = new com.zapthemes.swt.widgets.Text(shell, SWT.BORDER);
        shell.open();
        ControlDecoration decoration = new ControlDecoration(text, SWT.TOP | SWT.LEFT);
        decoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage());

        Set<Shell> before = Set.of(display.getShells());
        decoration.showHoverText("Required");
        dispatch();
        Shell hover = newShell(before);

        assertThat(hover.getBackground().getRGB()).isEqualTo(system("COLOR_INFO_BACKGROUND"));
        assertThat(hover.getForeground().getRGB()).isEqualTo(system("COLOR_INFO_FOREGROUND"));
    }

    @Test
    void popupDialogKeepsTheInformationColorsAcrossThemeSwitches() {
        shell.open();
        PopupDialog popup = new PopupDialog(shell, PopupDialog.INFOPOPUP_SHELLSTYLE, false, false, false, false, false, "Title", "Info") {
            @Override
            protected Control createDialogArea(Composite parent) {
                Label label = new com.zapthemes.swt.widgets.Label(parent, SWT.NONE);
                label.setText("Popup content");
                return label;
            }
        };
        popup.open();
        dispatch();
        assertInformationColors(popup.getShell());

        manager.setTheme(light);
        dispatch();
        assertInformationColors(popup.getShell());
        popup.close();
    }

    @Test
    void statusLineErrorMessageKeepsTheErrorColorAcrossThemeSwitches() {
        var status = new org.eclipse.jface.action.StatusLineManager();
        Control statusLine = status.createControl(shell);
        shell.open();
        status.setErrorMessage("Something failed");
        dispatch();
        Control message = findAll(statusLine, org.eclipse.swt.custom.CLabel.class).getFirst();
        assertThat(message.getForeground().getRGB()).isEqualTo(jface(JFacePreferences.ERROR_COLOR));

        manager.setTheme(light);
        dispatch();
        assertThat(message.getForeground().getRGB()).isEqualTo(jface(JFacePreferences.ERROR_COLOR));
    }

    @Test
    void focusCellHighlightUsesTheThemesSelection() {
        TableViewer viewer = new TableViewer(shell, SWT.FULL_SELECTION | SWT.BORDER);
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setWidth(200);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                // No text: the pixel check samples the cell fill.
                return "";
            }
        });
        TableViewerFocusCellManager focus = new TableViewerFocusCellManager(viewer, new FocusCellOwnerDrawHighlighter(viewer));
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setInput(List.of("one", "two", "three"));
        viewer.getTable().setSelection(1);
        shell.open();
        // On focus the manager makes the selected row's cell the focus cell.
        viewer.getTable().setFocus();
        dispatch();
        // Like upstream, the focus cell exists and shows only once the table has focus; under Xvfb no
        // window manager activates the shell, and a CI desktop may not hand it focus in time.
        Assumptions.assumeTrue(viewer.getTable().isFocusControl(), "the table has no keyboard focus");
        assertThat(focus.getFocusCell()).as("focus cell").isNotNull();

        var row = viewer.getTable().getItem(1).getBounds(0);
        ImageData data = print(viewer.getTable());
        Assumptions.assumeTrue(printWorks(data), "Control.print returned a blank image");
        RGB fill = data.palette.getRGB(data.getPixel(row.x + row.width / 2, row.y + row.height / 2));

        assertThat(fill).isEqualTo(system("COLOR_LIST_SELECTION"));
    }

    private void assertInformationColors(Shell popup) {
        RGB background = jface(JFacePreferences.INFORMATION_BACKGROUND_COLOR);
        List<String> problems = new ArrayList<>();
        for (Control control : findAll(popup, Control.class)) {
            // Upstream leaves the title and info separators out of the popup colors.
            if ((control.getStyle() & SWT.SEPARATOR) != 0) {
                continue;
            }
            if (!control.getBackground().getRGB().equals(background)) {
                String text = control instanceof Label label ? " \"" + label.getText() + "\"" : "";
                problems.add(control.getClass().getSimpleName() + text + " (class " + control.getData(com.zapthemes.core.ThemeKeys.THEME_CLASS)
                        + ") background " + control.getBackground().getRGB() + " instead of " + background);
            }
        }
        assertThat(problems).isEmpty();
    }

    private RGB system(String name) {
        return manager.registry(SystemColors.REGISTRY).color(name).orElseThrow().rgb;
    }

    private static RGB jface(String key) {
        return JFaceResources.getColorRegistry().getRGB(key);
    }

    private static Shell newShell(Set<Shell> before) {
        for (Shell open : display.getShells()) {
            if (!before.contains(open)) {
                return open;
            }
        }
        throw new AssertionError("no new shell");
    }

    private static ImageData print(Control control) {
        var size = control.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        control.print(gc);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        return data;
    }

    private static boolean printWorks(ImageData data) {
        for (int y = 0; y < data.height; y += 4) {
            for (int x = 0; x < data.width; x += 4) {
                RGB rgb = data.palette.getRGB(data.getPixel(x, y));
                if (rgb.red != 0 || rgb.green != 0 || rgb.blue != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> List<T> findAll(Control root, Class<T> type) {
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
        long end = System.currentTimeMillis() + 200;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }
}
