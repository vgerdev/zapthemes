package com.zapthemes.widgets;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.style.DefaultStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Registers the canvas widgets' stylers: the default colors and font, then a repaint, since the
 * widgets read the rest of their style (borders, states, accents) while painting.
 */
public final class FlatStylers implements StylerProvider {

    @Override
    public void register(StylerRegistry registry) {
        DefaultStyler repaint = new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                ((Control) widget).redraw();
            }
        };
        registry.register("FlatButton", repaint);
        registry.register("FlatGroup", repaint);
        registry.register("FlatProgressBar", repaint);
    }
}
