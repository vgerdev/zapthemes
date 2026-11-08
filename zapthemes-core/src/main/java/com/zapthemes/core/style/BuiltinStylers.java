package com.zapthemes.core.style;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Widget;

/**
 * Stylers for SWT controls whose theming needs more than the {@link DefaultStyler}.
 *
 * <table>
 *   <caption>Extra properties by control type</caption>
 *   <tr><th>Type</th><th>Properties</th></tr>
 *   <tr><td>Table, Tree</td><td>{@code headerBackground}, {@code headerForeground}; owner-drawn
 *       {@code selectionBackground}, {@code selectionForeground}, {@code states.hover.background},
 *       {@code alternateRowBackground} (Table), {@code gridLineColor} (see {@link ItemPainter})</td></tr>
 *   <tr><td>CTabFolder</td><td>{@code selectionBackground}, {@code selectionForeground},
 *       {@code selectionBarThickness}, {@code highlight}, {@code tabHeight}</td></tr>
 *   <tr><td>StyledText</td><td>{@code selectionBackground}, {@code selectionForeground}, {@code marginColor}</td></tr>
 *   <tr><td>Link</td><td>{@code linkForeground}</td></tr>
 * </table>
 *
 * <p>ToolBar gets no extra properties, but on Windows its items are measured again when its font
 * changes (a theme switch, a zoom): the native tool bar sizes a button with the font it had when the
 * button was added, so after a new font the text no longer fits.
 */
final class BuiltinStylers implements StylerProvider {

    private static final boolean WIN32 = "win32".equals(SWT.getPlatform());
    /** The font a ToolBar's items were last measured with. */
    private static final String TOOL_BAR_FONT = "zapthemes.toolBarFont";

    @Override
    public void register(StylerRegistry registry) {
        registry.register("Table", extend((Table table, ResolvedStyle style, ThemeContext context) -> {
            table.setHeaderBackground(context.color(style, "headerBackground"));
            table.setHeaderForeground(context.color(style, "headerForeground"));
            ItemPainter.install(table);
            ItemPainter.updateLines(table, style);
        }));
        registry.register("Tree", extend((Tree tree, ResolvedStyle style, ThemeContext context) -> {
            tree.setHeaderBackground(context.color(style, "headerBackground"));
            tree.setHeaderForeground(context.color(style, "headerForeground"));
            ItemPainter.install(tree);
            ItemPainter.updateLines(tree, style);
        }));
        registry.register("CTabFolder", extend((CTabFolder folder, ResolvedStyle style, ThemeContext context) -> {
            folder.setSelectionBackground(context.color(style, "selectionBackground"));
            folder.setSelectionForeground(context.color(style, "selectionForeground"));
            // Only set when the theme says so: the application may have chosen these itself.
            context.size(style, "selectionBarThickness").ifPresent(folder::setSelectionBarThickness);
            style.bool("highlight").ifPresent(folder::setHighlightEnabled);
            context.size(style, "tabHeight").ifPresent(folder::setTabHeight);
        }));
        registry.register("StyledText", extend((StyledText text, ResolvedStyle style, ThemeContext context) -> {
            text.setSelectionBackground(context.color(style, "selectionBackground"));
            text.setSelectionForeground(context.color(style, "selectionForeground"));
            text.setMarginColor(context.color(style, "marginColor"));
        }));
        registry.register("ToolBar", extend((ToolBar bar, ResolvedStyle style, ThemeContext context) -> {
            Font font = bar.getFont();
            if (WIN32 && bar.getData(TOOL_BAR_FONT) != null && !font.equals(bar.getData(TOOL_BAR_FONT))) {
                for (ToolItem item : bar.getItems()) {
                    String text = item.getText();
                    if (!text.isEmpty()) {
                        item.setText("");
                        item.setText(text);
                    }
                }
            }
            bar.setData(TOOL_BAR_FONT, font);
        }));
        registry.register("Link", extend((Link link, ResolvedStyle style, ThemeContext context) ->
                link.setLinkForeground(context.color(style, "linkForeground"))));
    }

    /** Typed extra step run after the {@link DefaultStyler}. */
    @FunctionalInterface
    interface Extra<T extends Widget> {
        void apply(T widget, ResolvedStyle style, ThemeContext context);
    }

    private static <T extends Widget> ControlStyler extend(Extra<T> extra) {
        return new DefaultStyler() {
            @Override
            @SuppressWarnings("unchecked")
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                extra.apply((T) widget, style, context);
            }
        };
    }
}
