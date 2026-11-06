package com.zapthemes.examples;

import java.util.List;

import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.themes.SystemPropertyTheme;

/** A "Theme:" picker over the bundled themes; it follows switches made elsewhere too. */
final class ThemeBar extends com.zapthemes.swt.widgets.Composite {

    private final ThemeManager themes;
    private final List<Theme> available;

    ThemeBar(Composite parent, ThemeManager themes, List<Theme> available) {
        super(parent, SWT.NONE);
        this.themes = themes;
        this.available = available;
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(this);
        WidgetFactory.label(SWT.NONE).text("Theme:").create(this);
        Combo picker = new com.zapthemes.swt.widgets.Combo(this, SWT.READ_ONLY);
        available.forEach(theme -> picker.add(theme.name()));
        picker.select(Math.max(0, available.indexOf(themes.getTheme())));
        picker.addListener(SWT.Selection, e -> themes.setTheme(available.get(picker.getSelectionIndex())));
        ThemeChangeListener follow = (previous, current) -> picker.select(available.indexOf(current));
        themes.addThemeChangeListener(follow);
        picker.addDisposeListener(e -> themes.removeThemeChangeListener(follow));
    }

    /** The {@code -Dzapthemes.theme} value for the theme in use: its id, or the file it came from. */
    String current() {
        String id = themes.getTheme().id();
        return BundledThemes.IDS.contains(id) ? id : System.getProperty(SystemPropertyTheme.PROPERTY, id);
    }

    /**
     * The bundled themes, resolved, with the display's manager on the one {@code -Dzapthemes.theme}
     * names ({@code auto} when it is not set).
     */
    static List<Theme> start(Display display) {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        List<Theme> available = BundledThemes.IDS.stream().map(registry::resolve).toList();
        ThemeManager themes = ThemeManager.get(display);
        if (System.getProperty(SystemPropertyTheme.PROPERTY) == null) {
            themes.setTheme(registry.resolve(BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme())));
        }
        // A theme file from the property is not one of the bundled ones: offer it too.
        if (!available.contains(themes.getTheme())) {
            available = java.util.stream.Stream.concat(available.stream(), java.util.stream.Stream.of(themes.getTheme())).toList();
        }
        return available;
    }
}
