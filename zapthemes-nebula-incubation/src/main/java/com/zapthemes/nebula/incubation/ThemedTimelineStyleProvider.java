package com.zapthemes.nebula.incubation;

import java.util.function.Supplier;

import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.nebula.widgets.timeline.jface.DefaultTimelineStyleProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * Timeline's look comes from a style provider whose default returns fixed draw2d colors (black
 * background). This one reads the timeline's theme rule instead, falling back to upstream's values:
 * {@code background}, {@code gridColor}, {@code overviewSelectionBackgroundColor},
 * {@code cursorColor}, {@code selectedCursorColor}.
 */
public class ThemedTimelineStyleProvider extends DefaultTimelineStyleProvider {

    private final Widget timeline;

    public ThemedTimelineStyleProvider(ResourceManager resourceManager, Widget timeline) {
        super(resourceManager);
        this.timeline = timeline;
    }

    @Override
    public Color getBackgroundColor() {
        return themed("background", super::getBackgroundColor);
    }

    @Override
    public Color getGridColor() {
        return themed("gridColor", super::getGridColor);
    }

    @Override
    public Color getOverviewSelectionBackgroundColor() {
        return themed("overviewSelectionBackgroundColor", super::getOverviewSelectionBackgroundColor);
    }

    @Override
    public Color getCursorColor() {
        return themed("cursorColor", super::getCursorColor);
    }

    @Override
    public Color getSelectedCursorColor() {
        return themed("selectedCursorColor", super::getSelectedCursorColor);
    }

    private Color themed(String key, Supplier<Color> fallback) {
        if (timeline.isDisposed()) {
            return fallback.get();
        }
        ThemeManager manager = ThemeManager.get(timeline.getDisplay());
        ResolvedStyle style = manager.styleOf(timeline);
        Color color = new ThemeContext(manager.resources()).color(style, key);
        return color != null ? color : fallback.get();
    }
}
