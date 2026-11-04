package com.zapthemes.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/** Helpers shared by the demos: bundled themes, letting the UI settle, screenshots. */
final class DemoSupport {

    private DemoSupport() {
    }

    /** The bundled light and dark themes, resolved. */
    static List<Theme> bundledThemes() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        return List.of(registry.resolve("zap-light"), registry.resolve("zap-dark"));
    }

    /** Runs pending events for a moment, so layouts and paints are done. */
    static void settle(Display display) {
        for (int i = 0; i < 20; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
            display.timerExec(25, () -> { });
            display.sleep();
        }
    }

    /** Saves the shell's client area as PNG; {@link org.eclipse.swt.widgets.Control#print} needs no screen access. */
    static void screenshot(Shell shell, Path dir, String name) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Rectangle area = shell.getClientArea();
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        String file = dir.resolve(name + ".png").toString();
        loader.save(file, SWT.IMAGE_PNG);
        image.dispose();
        System.out.println("Saved " + file);
    }
}
