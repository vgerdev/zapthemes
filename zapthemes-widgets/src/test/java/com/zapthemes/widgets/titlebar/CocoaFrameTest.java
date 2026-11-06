package com.zapthemes.widgets.titlebar;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.titlebar.TitleBar.Part;

/** The native macOS frame: full-size content view and traffic lights. macOS only (CI). */
class CocoaFrameTest {

    private Display display;
    private Shell shell;
    private TitleBar bar;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue("cocoa".equals(SWT.getPlatform()) && CocoaFrame.available());
        display = Display.getDefault();
        shell = new Shell(display, TitleBar.shellStyle(SWT.SHELL_TRIM));
        GridLayout layout = new GridLayout();
        layout.marginWidth = layout.marginHeight = 0;
        shell.setLayout(layout);
        bar = new TitleBar(shell, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        bar.setTitle("Native");
        bar.setSearchHint("Search");
        TitleBarItem save = new TitleBarItem(bar, SWT.PUSH);
        save.setText("Save");
        save.setGlyph(TitleBarGlyph.SAVE);
        new Label(shell, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        shell.setBounds(100, 100, 900, 500);
        shell.open();
        for (int i = 0; i < 10; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }

    @AfterEach
    void tearDown() {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }

    @Test
    void theTrafficLightsStayAndTheBarLeavesRoomForThem() {
        assertThat(bar.getFrameMode()).isEqualTo(TitleBar.FrameMode.NATIVE);
        assertThat(bar.getPartBounds(Part.CLOSE)).isNull();
        assertThat(bar.getPartBounds(Part.SYSTEM_MENU)).isNull();
        assertThat(save().getBounds().x).isGreaterThanOrEqualTo(70);
        assertThat(bar.getPartBounds(Part.SEARCH)).isNotNull();
    }

    @Test
    void theContentViewCoversTheTitleBar() {
        // With a full-size content view the client area starts at the window's top.
        assertThat(shell.toDisplay(0, 0).y - shell.getBounds().y).isLessThan(4);
    }

    private TitleBarItem save() {
        return bar.getItems()[0];
    }
}
