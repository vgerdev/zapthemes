package com.zapthemes.examples;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeExtension;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;

/**
 * Test-only: with {@code -Dzapthemes.examples.shot=<file.png>}, shortly after a snippet's first
 * widget it prints {@code SNIPPET <tagged>/<controls>} for every open shell, saves the largest one
 * as {@code file.png}, and closes them all, so the snippet's event loop ends. Used by
 * {@link SnippetShots}; without the property it does nothing.
 */
public final class SnippetShot implements ThemeExtension {

    static final String PROPERTY = "zapthemes.examples.shot";
    private static final int DELAY = 1500;

    @Override
    public void install(ThemeManager manager) {
        String file = System.getProperty(PROPERTY);
        if (file == null) {
            return;
        }
        Display display = Display.getCurrent();
        display.timerExec(DELAY, () -> shoot(display, file));
    }

    private static void shoot(Display display, String file) {
        // Let pending work finish first: widgets created without a proxy are themed from SWT.Skin
        // in an asyncExec, which this timer may otherwise overtake.
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
        List<Shell> shells = new ArrayList<>();
        for (Shell shell : display.getShells()) {
            if (shell.isVisible()) {
                shells.add(shell);
            }
        }
        int[] counts = new int[2];
        shells.forEach(shell -> count(shell, counts));
        System.out.println("SNIPPET " + counts[0] + "/" + counts[1] + " controls tagged in " + shells.size() + " shells");
        shells.stream().max((a, b) -> Integer.compare(area(a), area(b))).ifPresent(shell -> save(shell, file));
        for (Shell shell : display.getShells()) {
            shell.dispose();
        }
    }

    private static void count(Control control, int[] counts) {
        counts[1]++;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) != null) {
            counts[0]++;
        } else {
            System.out.println("UNTAGGED " + control.getClass().getName());
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                count(child, counts);
            }
        }
    }

    private static int area(Shell shell) {
        var size = shell.getSize();
        return size.x * size.y;
    }

    private static void save(Shell shell, String file) {
        var area = shell.getClientArea();
        if (area.width <= 0 || area.height <= 0) {
            return;
        }
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        loader.save(file, SWT.IMAGE_PNG);
        image.dispose();
    }
}
