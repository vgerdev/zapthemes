package com.zapthemes.core.style;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertySpec;

/**
 * Which integer keys are sizes in pixels, and so follow the zoom: those the
 * {@link PropertyCatalog} describes with {@link PropertySpec#size(String, Integer, String)}. For
 * stylers that set integers generically, through setters.
 */
final class Sizes {

    private static final Map<String, Boolean> KNOWN = new ConcurrentHashMap<>();
    private static volatile PropertyCatalog catalog;

    private Sizes() {
    }

    static boolean isSize(String type, String key) {
        if (type == null) {
            return false;
        }
        return KNOWN.computeIfAbsent(type + "\t" + key,
                k -> catalog().property(type, key).map(PropertySpec::size).orElse(false));
    }

    private static PropertyCatalog catalog() {
        PropertyCatalog loaded = catalog;
        if (loaded == null) {
            loaded = PropertyCatalog.load();
            catalog = loaded;
        }
        return loaded;
    }
}
