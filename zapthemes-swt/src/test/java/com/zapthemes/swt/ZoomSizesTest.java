package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeColumn;

/** What follows the zoom besides theme fonts: column widths, theme sizes and fonts the app set. */
class ZoomSizesTest {

    private static final Theme THEME = theme("zoom-sizes", """
            "controls": {
              "Label": { "font": { "name": "Arial", "height": 10 } },
              "CTabFolder": { "selectionBarThickness": 3 }
            },
            "classes": { "*.big": { "font": { "name": "Arial", "height": 14, "style": "bold" } } }
            """);
    private static final Theme OTHER = theme("zoom-sizes-other", """
            "controls": { "Label": { "font": { "name": "Arial", "height": 8 } } }
            """);

    private final Display display = Display.getDefault();
    private ThemeManager manager;
    private Shell shell;

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(THEME);
        shell = new Shell(display);
    }

    @AfterEach
    void tearDown() {
        manager.resetZoom();
        for (var s : display.getShells()) {
            s.dispose();
        }
    }

    // ---------------------------------------------------------------- columns

    @Test
    void tableAndTreeColumnsScaleWithTheZoom() {
        TableColumn tableColumn = new TableColumn(new Table(shell, SWT.NONE), SWT.NONE);
        tableColumn.setWidth(100);
        TreeColumn treeColumn = new TreeColumn(new Tree(shell, SWT.NONE), SWT.NONE);
        treeColumn.setWidth(80);

        manager.setZoom(1.5);
        assertThat(tableColumn.getWidth()).isEqualTo(150);
        assertThat(treeColumn.getWidth()).isEqualTo(120);

        manager.resetZoom();
        assertThat(tableColumn.getWidth()).isEqualTo(100);
        assertThat(treeColumn.getWidth()).isEqualTo(80);
    }

    @Test
    void columnWidthsDoNotDriftAfterManySteps() {
        TableColumn column = new TableColumn(new Table(shell, SWT.NONE), SWT.NONE);
        column.setWidth(97);

        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomIn();
            }
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomOut();
            }
            manager.resetZoom();
        }

        assertThat(column.getWidth()).isEqualTo(97);
    }

    @Test
    void aWidthChangedWhileZoomedIsKeptInProportion() {
        TableColumn column = new TableColumn(new Table(shell, SWT.NONE), SWT.NONE);
        column.setWidth(100);
        manager.setZoom(2);

        column.setWidth(300); // the user drags it at 200 %

        manager.resetZoom();
        assertThat(column.getWidth()).isEqualTo(150);
        manager.setZoom(2);
        assertThat(column.getWidth()).isEqualTo(300);
    }

    // ---------------------------------------------------------------- sizes in the theme

    @Test
    void sizesInTheThemeFollowTheZoom() {
        int[] bar = { 0 }; // CTabFolder has no getter
        new CTabFolder(shell, SWT.NONE) {
            @Override
            public void setSelectionBarThickness(int thickness) {
                super.setSelectionBarThickness(thickness);
                bar[0] = thickness;
            }
        };
        assertThat(bar[0]).isEqualTo(3);

        manager.setZoom(2);
        assertThat(bar[0]).isEqualTo(6);

        manager.resetZoom();
        assertThat(bar[0]).isEqualTo(3);
    }

    @Test
    void theCatalogMarksPixelSizes() {
        PropertyCatalog catalog = PropertyCatalog.load();

        assertThat(catalog.property("CTabFolder", "selectionBarThickness").orElseThrow().size()).isTrue();
        assertThat(catalog.property("CTabFolder", "tabHeight").orElseThrow().size()).isTrue();
        assertThat(catalog.property("CTabFolder", "highlight").orElseThrow().size()).isFalse();
    }

    // ---------------------------------------------------------------- fonts the application set

    @Test
    void aFontTheAppSetIsKeptAndScaled() {
        Label label = new Label(shell, SWT.NONE);
        Font own = new Font(display, "Courier New", 11, SWT.ITALIC);
        try {
            label.setFont(own);
            manager.setZoom(2);
            assertThat(font(label)).extracting(FontData::getName, FontData::getHeight, FontData::getStyle)
                    .containsExactly("Courier New", 22, SWT.ITALIC);

            manager.setTheme(OTHER); // a theme switch keeps it too
            assertThat(font(label).getHeight()).isEqualTo(22);

            manager.resetZoom();
            assertThat(label.getFont()).isSameAs(own);
        } finally {
            label.dispose();
            own.dispose();
        }
    }

    @Test
    void aFontSetWhileZoomedIsTakenAtThatZoom() {
        Label label = new Label(shell, SWT.NONE);
        manager.setZoom(2);
        Font own = new Font(display, "Courier New", 20, SWT.NORMAL);
        try {
            label.setFont(own); // e.g. derived from a font that is already zoomed

            manager.resetZoom();
            assertThat(font(label).getHeight()).isEqualTo(10);
            manager.setZoom(1.5);
            assertThat(font(label).getHeight()).isEqualTo(15);
        } finally {
            label.dispose();
            own.dispose();
        }
    }

    @Test
    void aStyleClassOrFollowThemeFontGivesTheThemesFontBack() {
        Label label = new Label(shell, SWT.NONE);
        Font own = new Font(display, "Courier New", 11, SWT.NORMAL);
        try {
            label.setFont(own);
            manager.setZoom(2);
            assertThat(font(label).getName()).isEqualTo("Courier New");

            manager.setThemeClass(label, "big");
            assertThat(font(label)).extracting(FontData::getName, FontData::getHeight, FontData::getStyle)
                    .containsExactly("Arial", 28, SWT.BOLD);
            assertThat(label.getData(ThemeKeys.APP_FONT)).isNull();

            label.setFont(own);
            manager.zoomIn();
            assertThat(font(label).getName()).isEqualTo("Courier New");
            manager.followThemeFont(label);
            assertThat(font(label).getName()).isEqualTo("Arial");
        } finally {
            label.dispose();
            own.dispose();
        }
    }

    @Test
    void aFontLikeTheThemesFollowsTheTheme() {
        Label label = new Label(shell, SWT.NONE);
        Font same = new Font(display, "Arial", 10, SWT.NORMAL); // what the theme gives a Label
        try {
            label.setFont(same);
            manager.setTheme(OTHER);

            assertThat(font(label).getHeight()).isEqualTo(8);
            assertThat(label.getData(ThemeKeys.APP_FONT)).isNull();
        } finally {
            label.dispose();
            same.dispose();
        }
    }

    @Test
    void appFontsDoNotLeakWhenZooming() {
        Label label = new Label(shell, SWT.NONE);
        Font own = new Font(display, "Courier New", 11, SWT.NORMAL);
        try {
            label.setFont(own);
            cycle();
            int afterOneCycle = manager.resources().fontCount();
            for (int i = 0; i < 10; i++) {
                cycle();
            }
            assertThat(manager.resources().fontCount()).isEqualTo(afterOneCycle);
            assertThat(label.getFont()).isSameAs(own);
        } finally {
            label.dispose();
            own.dispose();
        }
    }

    private void cycle() {
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomIn();
        }
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomOut();
        }
        manager.resetZoom();
    }

    private static FontData font(Control control) {
        return control.getFont().getFontData()[0];
    }

    private static Theme theme(String id, String body) {
        return ThemeJson.read(new StringReader("{ \"id\": \"" + id + "\", " + body + " }"), id);
    }
}
