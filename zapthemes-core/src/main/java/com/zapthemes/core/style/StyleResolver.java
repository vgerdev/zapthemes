package com.zapthemes.core.style;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;

import com.google.gson.JsonElement;
import com.zapthemes.core.model.Theme;

/**
 * Merges a theme's rules for one widget, later steps winning:
 * <ol>
 *   <li>{@code defaults}</li>
 *   <li>{@code controls[type]}, after its {@code "inherit"} chain</li>
 *   <li>{@code controls[type[BIT|BIT]]} for every style-bit rule whose bits are all set</li>
 *   <li>{@code classes[*.cls]} then {@code classes[type.cls]} for each class in the widget's
 *       {@code THEME_CLASS} (whitespace-separated, in order)</li>
 * </ol>
 * Results are cached per (type, style bits, classes), so re-theming large UIs stays cheap.
 */
public final class StyleResolver {

    static final String INHERIT = "inherit";

    private final Theme theme;
    private final Map<String, List<StyleBitRule>> bitRules = new HashMap<>();
    private final Map<CacheKey, ResolvedStyle> cache = new HashMap<>();
    private ResolvedStyle platform;

    public StyleResolver(Theme theme) {
        this.theme = theme;
        theme.controls().forEach((key, props) -> {
            int open = key.indexOf('[');
            if (open > 0 && key.endsWith("]")) {
                String type = key.substring(0, open);
                int bits = parseBits(key, key.substring(open + 1, key.length() - 1));
                bitRules.computeIfAbsent(type, t -> new ArrayList<>()).add(new StyleBitRule(bits, props));
            }
        });
    }

    public Theme theme() {
        return theme;
    }

    public ResolvedStyle resolve(String type, int style) {
        return resolve(type, style, null);
    }

    /** @param themeClasses the widget's {@code THEME_CLASS} value, or {@code null} */
    public ResolvedStyle resolve(String type, int style, String themeClasses) {
        List<StyleBitRule> rules = bitRules.getOrDefault(type, List.of());
        int relevantBits = 0;
        for (StyleBitRule rule : rules) {
            if ((style & rule.bits) == rule.bits) {
                relevantBits |= rule.bits;
            }
        }
        String classes = themeClasses == null ? "" : themeClasses.strip();
        return cache.computeIfAbsent(new CacheKey(type, relevantBits, classes), key -> {
            Map<String, JsonElement> props = new LinkedHashMap<>(theme.defaults());
            mergeType(type, props, new LinkedHashSet<>());
            for (StyleBitRule rule : rules) {
                if ((key.bits & rule.bits) == rule.bits) {
                    props.putAll(rule.props);
                }
            }
            if (!classes.isEmpty()) {
                for (String cls : classes.split("\\s+")) {
                    props.putAll(theme.classes().getOrDefault("*." + cls, Map.of()));
                    props.putAll(theme.classes().getOrDefault(type + "." + cls, Map.of()));
                }
            }
            props.remove(INHERIT);
            return new ResolvedStyle(props, theme.palette());
        });
    }

    /**
     * The platform settings: {@code platform["*"]} overlaid with {@code platform[<SWT.getPlatform()>]},
     * palette references resolved on read.
     */
    public ResolvedStyle platform() {
        if (platform == null) {
            Map<String, JsonElement> settings = new LinkedHashMap<>(theme.platformSettings("*"));
            settings.putAll(theme.platformSettings(SWT.getPlatform()));
            platform = new ResolvedStyle(settings, theme.palette());
        }
        return platform;
    }

    /**
     * The rule for {@code type} alone, with its {@code inherit} chain but without {@code defaults},
     * style bits or classes: for things that are not widgets, such as draw2d figures, which should
     * get only what the theme says for them. Empty when the theme has no rule for the type.
     */
    public ResolvedStyle rule(String type) {
        Map<String, JsonElement> props = new LinkedHashMap<>();
        mergeType(type, props, new LinkedHashSet<>());
        props.remove(INHERIT);
        return new ResolvedStyle(props, theme.palette());
    }

    /**
     * A {@code registries} block (e.g. {@code "colors"}, {@code "fonts"}) with palette references
     * resolved on read; empty if the theme has none.
     */
    public ResolvedStyle registry(String name) {
        return new ResolvedStyle(theme.registries().getOrDefault(name, Map.of()), theme.palette());
    }

    private void mergeType(String type, Map<String, JsonElement> into, Set<String> seen) {
        if (!seen.add(type)) {
            throw new IllegalStateException("Cyclic \"inherit\" between control rules: " + seen);
        }
        Map<String, JsonElement> rule = theme.controls().get(type);
        if (rule == null) {
            return;
        }
        JsonElement inherit = rule.get(INHERIT);
        if (inherit != null) {
            mergeType(inherit.getAsString(), into, seen);
        }
        into.putAll(rule);
    }

    /** Parses {@code "CHECK"} or {@code "CHECK|FLAT"} into SWT style bits. */
    static int parseBits(String key, String names) {
        int bits = 0;
        for (String name : names.split("\\|")) {
            try {
                Field field = SWT.class.getField(name.trim());
                bits |= field.getInt(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException("Unknown SWT style bit \"" + name + "\" in rule " + key, e);
            }
        }
        return bits;
    }

    private record StyleBitRule(int bits, Map<String, JsonElement> props) {
    }

    private record CacheKey(String type, int bits, String classes) {
    }
}
