package com.zapthemes.core.style;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Widget;

/** Stylers for SWT controls whose theming needs more than the {@link DefaultStyler}. */
final class BuiltinStylers implements StylerProvider {

    @Override
    public void register(StylerRegistry registry) {
        registry.register("Table", new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                Table table = (Table) widget;
                table.setHeaderBackground(context.color(style, "headerBackground"));
                table.setHeaderForeground(context.color(style, "headerForeground"));
            }
        });
        registry.register("CTabFolder", new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                CTabFolder folder = (CTabFolder) widget;
                folder.setSelectionBackground(context.color(style, "selectionBackground"));
                folder.setSelectionForeground(context.color(style, "selectionForeground"));
            }
        });
    }
}
