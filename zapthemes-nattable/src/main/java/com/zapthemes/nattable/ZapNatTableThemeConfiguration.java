package com.zapthemes.nattable;

import org.eclipse.nebula.widgets.nattable.painter.cell.BackgroundPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.TextPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.PaddingDecorator;
import org.eclipse.nebula.widgets.nattable.sort.painter.SortIconPainter;
import org.eclipse.nebula.widgets.nattable.sort.painter.SortableHeaderTextPainter;
import org.eclipse.nebula.widgets.nattable.style.BorderStyle;
import org.eclipse.nebula.widgets.nattable.style.BorderStyle.LineStyleEnum;
import org.eclipse.nebula.widgets.nattable.style.theme.ModernNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.tree.painter.IndentedTreeImagePainter;
import org.eclipse.nebula.widgets.nattable.tree.painter.TreeImagePainter;
import org.eclipse.nebula.widgets.nattable.ui.util.CellEdgeEnum;
import org.eclipse.nebula.widgets.nattable.util.GUIHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * NatTable's {@link ModernNatTableThemeConfiguration} with its colors and fonts taken from the
 * theme's {@code NatTable} rule. The bundled themes start that rule from their {@code Table} rule
 * ({@code "inherit": "Table"}), so the keys are the ones a Table takes, plus a few for what only
 * NatTable has:
 *
 * <table>
 *   <caption>Theme keys</caption>
 *   <tr><th>Key</th><th>NatTable</th></tr>
 *   <tr><td>{@code background}, {@code foreground}, {@code font}</td><td>body cells (even rows), the default style</td></tr>
 *   <tr><td>{@code alternateRowBackground}</td><td>odd rows</td></tr>
 *   <tr><td>{@code headerBackground}, {@code headerForeground}, {@code headerFont}</td><td>column and row headers, corner, group headers, sort headers</td></tr>
 *   <tr><td>{@code selectionBackground}, {@code selectionForeground}</td><td>selected cells and the headers of selected cells, rows and columns</td></tr>
 *   <tr><td>{@code focusCellBackground}, {@code focusCellForeground}</td><td>the selection anchor (its border and the selection's dotted outline in {@code focusCellBackground})</td></tr>
 *   <tr><td>{@code states.hover.background}</td><td>hovered body cells</td></tr>
 *   <tr><td>{@code gridLineColor}</td><td>grid lines</td></tr>
 *   <tr><td>{@code freezeSeparatorColor}</td><td>the freeze line (default {@code gridLineColor})</td></tr>
 *   <tr><td>{@code summaryRowBackground}, {@code filterRowBackground}</td><td>summary and filter rows (default {@code headerBackground})</td></tr>
 *   <tr><td>{@code errorForeground}</td><td>cells with conversion or validation errors</td></tr>
 *   <tr><td>{@code accentColor}</td><td>changed data, the fill handle, copy border</td></tr>
 * </table>
 *
 * Keys the theme does not set keep NatTable's modern defaults. On a dark {@code background} the
 * sort and tree icons are NatTable's inverted ones, as in {@code DarkNatTableThemeConfiguration}.
 */
public class ZapNatTableThemeConfiguration extends ModernNatTableThemeConfiguration {

    private final boolean dark;

    public ZapNatTableThemeConfiguration(ResolvedStyle style, ThemeContext context) {
        Color background = context.color(style, "background");
        Color foreground = context.color(style, "foreground");
        Color header = first(context.color(style, "headerBackground"), background);
        Color headerText = first(context.color(style, "headerForeground"), foreground);
        Color selection = context.color(style, "selectionBackground");
        Color selectionText = context.color(style, "selectionForeground");
        Color anchor = context.color(style, "focusCellBackground");
        Color anchorText = context.color(style, "focusCellForeground");
        Color grid = context.color(style, "gridLineColor");
        Color accent = context.color(style, "accentColor");
        Color error = context.color(style, "errorForeground");
        Color hover = style.states().contains("hover") ? context.color(style.state("hover"), "background") : null;
        Font font = context.font(style, "font");
        Font headerFont = first(context.font(style, "headerFont"), font);
        dark = background != null && luminance(background.getRGB()) < 0.5;

        if (background != null) {
            defaultBgColor = background;
            evenRowBgColor = background;
            oddRowBgColor = first(context.color(style, "alternateRowBackground"), background);
            treeBgColor = background;
        }
        if (foreground != null) {
            defaultFgColor = foreground;
            evenRowFgColor = foreground;
            oddRowFgColor = foreground;
            treeFgColor = foreground;
            hideIndicatorColor = foreground;
        }
        if (font != null) {
            defaultFont = font;
            defaultSelectionFont = font;
        }
        if (header != null) {
            cHeaderBgColor = header;
            rHeaderBgColor = header;
            cornerBgColor = header;
            cGroupHeaderBgColor = header;
            rGroupHeaderBgColor = header;
            sortHeaderBgColor = header;
            filterRowBgColor = first(context.color(style, "filterRowBackground"), header);
            summaryRowBgColor = first(context.color(style, "summaryRowBackground"), header);
        }
        if (headerText != null) {
            cHeaderFgColor = headerText;
            rHeaderFgColor = headerText;
            cornerFgColor = headerText;
            cGroupHeaderFgColor = headerText;
            rGroupHeaderFgColor = headerText;
            sortHeaderFgColor = headerText;
            filterRowFgColor = headerText;
            summaryRowFgColor = headerText;
        }
        if (headerFont != null) {
            cHeaderFont = headerFont;
            rHeaderFont = headerFont;
            cHeaderSelectionFont = headerFont;
            rHeaderSelectionFont = headerFont;
            cornerFont = headerFont;
        }
        if (selection != null) {
            defaultSelectionBgColor = selection;
            cHeaderSelectionBgColor = selection;
            rHeaderSelectionBgColor = selection;
            cHeaderFullSelectionBgColor = selection;
            rHeaderFullSelectionBgColor = selection;
            cornerSelectionBgColor = selection;
            selectedSortHeaderBgColor = selection;
            treeSelectionBgColor = selection;
            summaryRowSelectionBgColor = selection;
            selectionAnchorSelectionBgColor = first(anchor, selection);
        }
        if (selectionText != null) {
            defaultSelectionFgColor = selectionText;
            cHeaderSelectionFgColor = selectionText;
            rHeaderSelectionFgColor = selectionText;
            cHeaderFullSelectionFgColor = selectionText;
            rHeaderFullSelectionFgColor = selectionText;
            cornerSelectionFgColor = selectionText;
            selectedSortHeaderFgColor = selectionText;
            treeSelectionFgColor = selectionText;
            selectionAnchorSelectionFgColor = first(anchorText, selectionText);
        }
        if (anchor != null) {
            selectionAnchorBorderStyle = new BorderStyle(1, anchor, LineStyleEnum.SOLID);
            // The dotted outline around the selected region.
            selectionAnchorGridBorderStyle = new BorderStyle(1, anchor, LineStyleEnum.DOTTED);
        }
        if (anchorText != null) {
            selectionAnchorSelectionFgColor = anchorText;
        }
        if (hover != null) {
            bodyHoverBgColor = hover;
        }
        if (grid != null) {
            gridLineColor = grid;
        }
        Color freeze = first(context.color(style, "freezeSeparatorColor"), grid);
        if (freeze != null) {
            freezeSeparatorColor = freeze;
        }
        if (error != null) {
            conversionErrorFgColor = error;
            validationErrorFgColor = error;
        }
        if (accent != null) {
            dataChangeFgColor = accent;
            fillHandleColor = accent;
            fillHandleBorderStyle = new BorderStyle(2, accent, LineStyleEnum.SOLID);
            copyBorderStyle = new BorderStyle(1, accent, LineStyleEnum.DASHED);
        }
        Font base = font != null ? font : GUIHelper.DEFAULT_FONT;
        FontData bold = base.getFontData()[0];
        bold.setStyle(bold.getStyle() | SWT.BOLD);
        summaryRowFont = GUIHelper.getFont(bold);
        summaryRowSelectionFont = summaryRowFont;
    }

    /** Whether the body background is dark, so the icons are NatTable's inverted ones. */
    public boolean isDark() {
        return dark;
    }

    @Override
    public void createPainterInstances() {
        super.createPainterInstances();
        if (!dark) {
            return;
        }
        // As DarkNatTableThemeConfiguration: light icons on dark headers and rows.
        sortHeaderCellPainter = new BackgroundPainter(new PaddingDecorator(new SortableHeaderTextPainter(
                new TextPainter(false, false), CellEdgeEnum.RIGHT, new SortIconPainter(false, true), false, 0, false),
                0, 2, 0, 5, false));
        TreeImagePainter images = new TreeImagePainter(false, GUIHelper.getImage("right_inv"), //$NON-NLS-1$
                GUIHelper.getImage("right_down_inv"), null); //$NON-NLS-1$
        treeStructurePainter = new BackgroundPainter(new PaddingDecorator(
                new IndentedTreeImagePainter(10, null, CellEdgeEnum.LEFT, images, false, 2, true), 0, 5, 0, 5, false));
    }

    private static <T> T first(T value, T fallback) {
        return value != null ? value : fallback;
    }

    static double luminance(RGB rgb) {
        return (0.2126 * rgb.red + 0.7152 * rgb.green + 0.0722 * rgb.blue) / 255;
    }
}
