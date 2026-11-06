package com.zapthemes.themes;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeExtension;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;

/**
 * Themes an application that never calls {@link ThemeManager#setTheme}, chosen on the command line:
 * {@code -Dzapthemes.theme=zap-dark}. The value is a bundled theme id, {@code auto} (the bundled
 * theme matching the desktop's dark and high-contrast settings, see {@link BundledThemes#idFor}), or
 * the path of a theme file, which may extend a bundled theme. Without the property nothing changes.
 */
public final class SystemPropertyTheme implements ThemeExtension {

    public static final String PROPERTY = "zapthemes.theme";

    @Override
    public void install(ThemeManager manager) {
    }

    @Override
    public Theme initialTheme(Display display) {
        String value = System.getProperty(PROPERTY);
        return value == null || value.isBlank() ? null : resolve(value.strip(), display);
    }

    static Theme resolve(String value, Display display) {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        if (value.equals("auto")) {
            return registry.resolve(BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme()));
        }
        Path file = Path.of(value);
        if (value.endsWith(".json") && Files.isRegularFile(file)) {
            return registry.resolve(registry.loadFile(file).id());
        }
        if (!BundledThemes.IDS.contains(value)) {
            throw new IllegalArgumentException(PROPERTY + "=" + value + ": expected auto, one of " + BundledThemes.IDS
                    + ", or an existing .json theme file");
        }
        return registry.resolve(value);
    }
}
