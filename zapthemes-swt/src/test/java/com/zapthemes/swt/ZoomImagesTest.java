package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;

/** Widget images and the trackpad pinch follow the zoom. */
class ZoomImagesTest {

    private final Display display = Display.getDefault();
    private ThemeManager manager;
    private Shell shell;
    private Image icon;

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(ThemeJson.read(new StringReader("{ \"id\": \"zoom-images\" }"), "zoom-images"));
        shell = new Shell(display);
        icon = square(16, new RGB(200, 0, 0));
    }

    @AfterEach
    void tearDown() {
        manager.setZoomImagesEnabled(true);
        manager.setZoomShortcutsEnabled(true);
        manager.resetZoom();
        for (var s : display.getShells()) {
            s.dispose();
        }
        icon.dispose();
    }

    // ---------------------------------------------------------------- images

    @Test
    void imagesGrowWithTheZoomAndComeBackAt100() {
        Label label = new Label(shell, SWT.NONE);
        label.setImage(icon);

        manager.setZoom(2);
        assertThat(label.getImage().getBounds().width).isEqualTo(32);
        assertThat(label.getImage()).isNotSameAs(icon);

        manager.setZoom(1.5);
        assertThat(label.getImage().getBounds().width).isEqualTo(24);

        manager.resetZoom();
        assertThat(label.getImage()).isSameAs(icon);
    }

    @Test
    void anImageWithAZoomVariantShowsTheVariant() {
        // Red at 100 %, blue in the 200 % variant: like icon.png and icon@2x.png.
        ImageDataProvider provider = zoom -> zoom >= 200 ? data(32, new RGB(0, 0, 255)) : data(16, new RGB(255, 0, 0));
        Image variants = new Image(display, provider);
        try {
            Label label = new Label(shell, SWT.NONE);
            label.setImage(variants);

            manager.setZoom(2);

            ImageData shown = label.getImage().getImageData(100);
            assertThat(shown.width).isEqualTo(32);
            assertThat(shown.palette.getRGB(shown.getPixel(5, 5))).isEqualTo(new RGB(0, 0, 255));
        } finally {
            shell.dispose();
            variants.dispose();
        }
    }

    @Test
    void anImageSetWhileZoomedIsTakenAsA100PercentImage() {
        Label label = new Label(shell, SWT.NONE);
        manager.setZoom(2);
        label.setImage(icon);

        manager.setZoom(1.5);
        assertThat(label.getImage().getBounds().width).isEqualTo(24);
    }

    @Test
    void widgetsCreatedWhileZoomedGetZoomedImages() {
        manager.setZoom(2);
        Label label = new Label(shell, SWT.NONE);
        label.setImage(icon);
        while (display.readAndDispatch()) {
            // The image pass runs once construction is over.
        }

        assertThat(label.getImage().getBounds().width).isEqualTo(32);
    }

    @Test
    void imagesCanBeLeftAlone() {
        Composite keep = new Composite(shell, SWT.NONE);
        keep.setData(ThemeKeys.ZOOM_IMAGES, Boolean.FALSE);
        Label kept = new Label(keep, SWT.NONE);
        kept.setImage(icon);
        Label zoomed = new Label(shell, SWT.NONE);
        zoomed.setImage(icon);

        manager.setZoom(2);
        assertThat(kept.getImage()).isSameAs(icon);
        assertThat(zoomed.getImage().getBounds().width).isEqualTo(32);

        manager.setZoomImagesEnabled(false);
        assertThat(zoomed.getImage()).isSameAs(icon);
    }

    @Test
    void zoomedCopiesAreBoundedAndDisposedWithTheirOriginal() {
        Image own = square(16, new RGB(0, 200, 0));
        Label label = new Label(shell, SWT.NONE);
        label.setImage(own);
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomIn();
            }
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomOut();
            }
        }
        // One copy per level but 100 %, however often it zoomed.
        assertThat(manager.zoomedImageCount()).isEqualTo(ThemeManager.ZOOM_LEVELS.size() - 1);

        label.dispose();
        own.dispose();
        manager.zoomIn();
        assertThat(manager.zoomedImageCount()).isZero();
    }

    // ---------------------------------------------------------------- pinch

    @Test
    void aPinchSnapsToChromesLevels() {
        Label label = new Label(shell, SWT.NONE);

        gesture(label, SWT.GESTURE_BEGIN, 0);
        gesture(label, SWT.GESTURE_MAGNIFY, 1.3);
        assertThat(manager.getZoom()).isEqualTo(1.25);
        gesture(label, SWT.GESTURE_MAGNIFY, 2.1); // relative to the start of the pinch
        assertThat(manager.getZoom()).isEqualTo(2.0);
        gesture(label, SWT.GESTURE_END, 0);

        gesture(label, SWT.GESTURE_BEGIN, 0);
        gesture(label, SWT.GESTURE_MAGNIFY, 0.5);
        assertThat(manager.getZoom()).isEqualTo(1.0);
        gesture(label, SWT.GESTURE_END, 0);
    }

    @Test
    void aPinchIsIgnoredWhenTheShortcutsAreOffOrTheControlKeepsThem() {
        Label label = new Label(shell, SWT.NONE);
        manager.setZoomShortcutsEnabled(false);
        gesture(label, SWT.GESTURE_BEGIN, 0);
        gesture(label, SWT.GESTURE_MAGNIFY, 2);
        assertThat(manager.getZoom()).isEqualTo(1);

        manager.setZoomShortcutsEnabled(true);
        label.setData(ThemeKeys.ZOOM_SHORTCUTS, Boolean.FALSE);
        gesture(label, SWT.GESTURE_BEGIN, 0);
        gesture(label, SWT.GESTURE_MAGNIFY, 2);
        assertThat(manager.getZoom()).isEqualTo(1);
    }

    private static void gesture(Label label, int detail, double magnification) {
        Event event = new Event();
        event.detail = detail;
        event.magnification = magnification;
        event.doit = true;
        label.notifyListeners(SWT.Gesture, event);
    }

    private Image square(int size, RGB rgb) {
        Image image = new Image(display, size, size);
        GC gc = new GC(image);
        gc.setBackground(new org.eclipse.swt.graphics.Color(rgb));
        gc.fillRectangle(0, 0, size, size);
        gc.dispose();
        return image;
    }

    private static ImageData data(int size, RGB rgb) {
        ImageData data = new ImageData(size, size, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        int pixel = data.palette.getPixel(rgb);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                data.setPixel(x, y, pixel);
            }
        }
        return data;
    }
}
