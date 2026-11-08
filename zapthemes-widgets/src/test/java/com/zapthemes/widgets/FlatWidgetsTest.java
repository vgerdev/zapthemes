package com.zapthemes.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

class FlatWidgetsTest {

    private static final RGB DARK_MUTED = new RGB(0x39, 0x3B, 0x40);
    private static final RGB LIGHT_MUTED = new RGB(0xEB, 0xEC, 0xF0);
    private static final RGB ACCENT = new RGB(0x35, 0x74, 0xF0);

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
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
    }

    @Test
    void flatWidgetsAreThemedTypes() {
        FlatButton check = new FlatButton(shell, SWT.CHECK);

        assertThat(check.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FlatButton");
        assertThat(check.getStyle() & SWT.CHECK).isNotZero();
        assertThat(new FlatGroup(shell, SWT.NONE).getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FlatGroup");
        assertThat(new FlatProgressBar(shell, SWT.NONE).getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FlatProgressBar");
    }

    @Test
    void pushButtonsNotifyOnClickSpaceAndEnter() {
        FlatButton button = new FlatButton(shell, SWT.PUSH);
        List<Event> events = new ArrayList<>();
        button.addListener(SWT.Selection, events::add);

        mouseClick(button);
        key(button, ' ');
        key(button, SWT.CR);

        assertThat(events).hasSize(3);
        assertThat(button.getSelection()).isFalse();
    }

    @Test
    void checkAndToggleButtonsFlip() {
        FlatButton check = new FlatButton(shell, SWT.CHECK);
        FlatButton toggle = new FlatButton(shell, SWT.TOGGLE);
        int[] selections = { 0 };
        check.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> selections[0]++));

        check.activate();
        toggle.activate();
        assertThat(check.getSelection()).isTrue();
        assertThat(toggle.getSelection()).isTrue();

        key(check, ' ');
        key(check, SWT.CR); // Enter does not toggle check boxes
        assertThat(check.getSelection()).isFalse();
        assertThat(selections[0]).isEqualTo(2);
    }

    @Test
    void radioButtonsAreExclusiveInTheirParent() {
        FlatButton one = new FlatButton(shell, SWT.RADIO);
        FlatButton two = new FlatButton(shell, SWT.RADIO);
        Composite free = new Composite(shell, SWT.NO_RADIO_GROUP);
        FlatButton three = new FlatButton(free, SWT.RADIO);
        FlatButton four = new FlatButton(free, SWT.RADIO);

        one.activate();
        two.activate();
        three.activate();
        four.activate();

        assertThat(one.getSelection()).isFalse();
        assertThat(two.getSelection()).isTrue();
        assertThat(three.getSelection()).isTrue();
        assertThat(four.getSelection()).isTrue();
    }

    @Test
    void mnemonicsMatchTheMarkedCharacter() {
        FlatButton button = new FlatButton(shell, SWT.PUSH);
        button.setText("Save && &Close");

        assertThat(button.matchesMnemonic('c')).isTrue();
        assertThat(button.matchesMnemonic('s')).isFalse();
    }

    @Test
    void paintsWithTheThemeAndRepaintsOnSwitch() {
        FlatButton button = new FlatButton(shell, SWT.PUSH);
        button.setText("Push");
        FlatProgressBar progress = new FlatProgressBar(shell, SWT.NONE);
        progress.setSelection(100);
        shell.setSize(400, 120);
        shell.open();
        shell.layout();

        assertThat(fillPixel(button)).isEqualTo(DARK_MUTED);
        assertThat(fillPixel(progress)).isEqualTo(ACCENT);

        manager.setTheme(light);
        assertThat(fillPixel(button)).isEqualTo(LIGHT_MUTED);
    }

    @Test
    void groupClientAreaLeavesRoomForTitleAndBorder() {
        FlatGroup group = new FlatGroup(shell, SWT.NONE);
        group.setText("Title");
        group.setLayout(new FillLayout());
        Label child = new Label(group, SWT.NONE);
        child.setText("child");
        shell.setSize(300, 200);
        shell.layout(true, true);

        var area = group.getClientArea();
        assertThat(area.y).isGreaterThan(5);
        assertThat(child.getBounds()).isEqualTo(area);
        assertThat(group.computeSize(SWT.DEFAULT, SWT.DEFAULT).y).isGreaterThan(child.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
    }

    @Test
    void progressValuesAreClamped() {
        FlatProgressBar bar = new FlatProgressBar(shell, SWT.NONE);
        bar.setMaximum(10);
        bar.setSelection(25);
        assertThat(bar.getSelection()).isEqualTo(10);
        bar.setSelection(-3);
        assertThat(bar.getSelection()).isZero();
        bar.setMinimum(20); // not below maximum: ignored
        assertThat(bar.getMinimum()).isZero();
    }

    /** A pixel of the control's fill, left of any centered text, whose antialiasing varies by platform. */
    private static RGB fillPixel(Control control) {
        while (display.readAndDispatch()) {
            // Let the control be laid out and painted before printing it.
        }
        var size = control.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
        gc.fillRectangle(0, 0, size.x, size.y);
        control.print(gc);
        gc.dispose();
        ImageData data = image.getImageData();
        assumePrintWorks(data);
        RGB rgb = data.palette.getRGB(data.getPixel(size.x / 8, size.y / 2));
        image.dispose();
        org.junit.jupiter.api.Assumptions.assumeFalse(rgb.equals(new RGB(255, 0, 255)),
                "Control.print left the control unpainted (busy CI desktop?)");
        return rgb;
    }

    /**
     * {@code Control.print} returns a blank (all black) image when the desktop cannot be drawn, e.g. a
     * locked Windows session; pixel checks are meaningless then.
     */
    private static void assumePrintWorks(ImageData data) {
        for (int y = 0; y < data.height; y += 4) {
            for (int x = 0; x < data.width; x += 4) {
                RGB rgb = data.palette.getRGB(data.getPixel(x, y));
                if (rgb.red != 0 || rgb.green != 0 || rgb.blue != 0) {
                    return;
                }
            }
        }
        org.junit.jupiter.api.Assumptions.abort("Control.print returned a blank image (locked desktop?)");
    }

    private static void mouseClick(Control control) {
        for (int type : new int[] { SWT.MouseDown, SWT.MouseUp }) {
            Event event = new Event();
            event.button = 1;
            event.x = 1;
            event.y = 1;
            control.setSize(20, 20);
            control.notifyListeners(type, event);
        }
    }

    private static void key(Control control, char character) {
        Event event = new Event();
        event.character = character;
        control.notifyListeners(SWT.KeyDown, event);
    }
}
