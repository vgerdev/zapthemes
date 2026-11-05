package com.zapthemes.core.style;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/** Maps a {@code THEME_CONTROL_TYPE} to its {@link ControlStyler}; unknown types use the default. */
public final class StylerRegistry {

    private final Map<String, ControlStyler> stylers = new ConcurrentHashMap<>();
    private final ControlStyler fallback = new DefaultStyler();

    /** A registry with the built-in stylers and every {@link StylerProvider} on the classpath. */
    public static StylerRegistry withDefaults() {
        StylerRegistry registry = new StylerRegistry();
        new BuiltinStylers().register(registry);
        ServiceLoader.load(StylerProvider.class).forEach(provider -> provider.register(registry));
        return registry;
    }

    public void register(String type, ControlStyler styler) {
        stylers.put(type, styler);
    }

    public ControlStyler get(String type) {
        return stylers.getOrDefault(type, fallback);
    }
}
