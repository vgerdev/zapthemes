package com.zapthemes.core.style;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Widget;

/**
 * Stylers for SWT controls whose theming needs more than the {@link DefaultStyler}.
 *
 * <table>
 *   <caption>Extra properties by control type</caption>
 *   <tr><th>Type</th><th>Properties</th></tr>
 *   <tr><td>Table, Tree</td><td>{@code headerBackground}, {@code headerForeground}</td></tr>
 *   <tr><td>CTabFolder</td><td>{@code selectionBackground}, {@code selectionForeground}</td></tr>
 *   <tr><td>StyledText</td><td>{@code selectionBackground}, {@code selectionForeground}, {@code marginColor}</td></tr>
 *   <tr><td>Link</td><td>{@code linkForeground}</td></tr>
 * </table>
 */
final class BuiltinStylers implements StylerProvider {

    @Override
    public void register(StylerRegistry registry) {
        registry.register("Table", extend((Table table, ResolvedStyle style, ThemeContext context) -> {
            table.setHeaderBackground(context.color(style, "headerBackground"));
            table.setHeaderForeground(context.color(style, "headerForeground"));
        }));
        registry.register("Tree", extend((Tree tree, ResolvedStyle style, ThemeContext context) -> {
            tree.setHeaderBackground(context.color(style, "headerBackground"));
            tree.setHeaderForeground(context.color(style, "headerForeground"));
        }));
        registry.register("CTabFolder", extend((CTabFolder folder, ResolvedStyle style, ThemeContext context) -> {
            folder.setSelectionBackground(context.color(style, "selectionBackground"));
            folder.setSelectionForeground(context.color(style, "selectionForeground"));
        }));
        registry.register("StyledText", extend((StyledText text, ResolvedStyle style, ThemeContext context) -> {
            text.setSelectionBackground(context.color(style, "selectionBackground"));
            text.setSelectionForeground(context.color(style, "selectionForeground"));
            text.setMarginColor(context.color(style, "marginColor"));
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
