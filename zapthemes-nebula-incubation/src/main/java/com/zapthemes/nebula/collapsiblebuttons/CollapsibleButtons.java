package com.zapthemes.nebula.collapsiblebuttons;

import org.eclipse.nebula.widgets.collapsiblebuttons.IColorManager;
import org.eclipse.nebula.widgets.collapsiblebuttons.ILanguageSettings;
import org.eclipse.nebula.widgets.collapsiblebuttons.ISettings;
import org.eclipse.swt.widgets.Composite;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.nebula.incubation.ThemedCollapsibleButtonsColors;
import com.zapthemes.nebula.incubation.ThemedCollapsibleButtonsSettings;

/**
 * Theme-aware {@link org.eclipse.nebula.widgets.collapsiblebuttons.CollapsibleButtons}: tags itself
 * with {@code THEME_CONTROL_TYPE = "CollapsibleButtons"} and applies the current theme on creation.
 *
 * <p>Written by hand instead of generated: upstream takes its colors from an {@link IColorManager}
 * and its text color from {@link ISettings}, both fixed at construction. Where the application gives
 * none (or {@code null}), this proxy passes {@link ThemedCollapsibleButtonsColors} and
 * {@link ThemedCollapsibleButtonsSettings}; an application's own are kept. They are created before
 * {@code super(...)} (flexible constructor bodies, Java 25) and bound to the widget after it.
 */
public class CollapsibleButtons extends org.eclipse.nebula.widgets.collapsiblebuttons.CollapsibleButtons {

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style) {
        Themed themed = new Themed(null, null);
        super(parent, style, IColorManager.SKIN_AUTO_DETECT, themed.colors(), themed.settings(), null);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, ILanguageSettings language) {
        Themed themed = new Themed(null, null);
        super(parent, style, IColorManager.SKIN_AUTO_DETECT, themed.colors(), themed.settings(), language);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, ISettings settings, ILanguageSettings language) {
        Themed themed = new Themed(null, settings);
        super(parent, style, IColorManager.SKIN_AUTO_DETECT, themed.colors(), themed.settings(), language);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, int theme) {
        Themed themed = new Themed(null, null);
        super(parent, style, theme, themed.colors(), themed.settings(), null);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, IColorManager colorManager) {
        Themed themed = new Themed(colorManager, null);
        super(parent, style, IColorManager.SKIN_AUTO_DETECT, themed.colors(), themed.settings(), null);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, int theme, IColorManager colorManager) {
        Themed themed = new Themed(colorManager, null);
        super(parent, style, theme, themed.colors(), themed.settings(), null);
        themed.bind(this);
    }

    @SuppressWarnings("this-escape")
    public CollapsibleButtons(Composite parent, int style, int theme, IColorManager colorManager, ISettings settings,
            ILanguageSettings language) {
        Themed themed = new Themed(colorManager, settings);
        super(parent, style, theme, themed.colors(), themed.settings(), language);
        themed.bind(this);
    }

    @Override
    protected void checkSubclass() {
        // SWT rejects subclasses by default; a proxy only adds theming.
    }

    /** The application's color manager and settings, or themed ones where it gave none. */
    private static final class Themed {
        private final IColorManager colors;
        private final ISettings settings;

        Themed(IColorManager colors, ISettings settings) {
            this.colors = colors != null ? colors : new ThemedCollapsibleButtonsColors();
            this.settings = settings != null ? settings : new ThemedCollapsibleButtonsSettings();
        }

        IColorManager colors() {
            return colors;
        }

        ISettings settings() {
            return settings;
        }

        void bind(CollapsibleButtons buttons) {
            if (colors instanceof ThemedCollapsibleButtonsColors themed) {
                themed.bind(buttons);
            }
            if (settings instanceof ThemedCollapsibleButtonsSettings themed) {
                themed.bind(buttons);
            }
            ThemeManager.applyOnCreate(buttons, "CollapsibleButtons");
        }
    }
}
