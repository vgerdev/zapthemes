package com.zapthemes.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.swt.graphics.RGBA;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertyKind;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.themes.BundledThemes;

/**
 * What every bundled theme resolves to, compared with a reviewed snapshot: for each type in the
 * catalog or the theme, its style (what differs from {@code defaults}; a figure's own rule), every
 * style-bit and class rule, every state, the registries and the platform settings, with every
 * reference followed to a color, a font or a value.
 * <p>
 * A theme can be reorganized freely (names, swatches, {@code extends}, {@code inherit}) as long as
 * this does not change. When a change to a bundled theme is meant to change how it looks, the test
 * fails and writes the new snapshot to {@code build/bundled-theme-styles.txt}: review the
 * difference, then copy it over {@code src/test/resources/com/zapthemes/demo/bundled-theme-styles.txt}.
 */
class BundledThemeStylesTest {

    private static final String SNAPSHOT = "bundled-theme-styles.txt";

    private final PropertyCatalog catalog = PropertyCatalog.load();

    @Test
    void bundledThemesResolveAsInTheSnapshot() throws IOException {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        StringBuilder out = new StringBuilder();
        for (String id : BundledThemes.IDS) {
            snapshot(registry.resolve(id), out);
        }
        String actual = out.toString();

        Path written = Path.of("build", SNAPSHOT);
        Files.createDirectories(written.getParent());
        Files.writeString(written, actual, StandardCharsets.UTF_8);
        String expected;
        try (InputStream in = getClass().getResourceAsStream(SNAPSHOT)) {
            expected = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        assertThat(actual).as("How the bundled themes resolve changed. If that is intended, review "
                + written.toAbsolutePath() + " and copy it to src/test/resources/com/zapthemes/demo/" + SNAPSHOT)
                .isEqualTo(expected);
    }

    private void snapshot(Theme theme, StringBuilder out) {
        StyleResolver resolver = new StyleResolver(theme);
        out.append("# ").append(theme.id()).append('\n');
        out.append("dark = ").append(theme.dark()).append('\n');

        Map<String, String> defaults = flatten(resolver.resolve("*", 0), "*");
        section(out, "defaults", defaults);

        Set<String> types = new TreeSet<>();
        catalog.types().forEach(t -> types.add(t.type()));
        theme.controls().keySet().forEach(key -> types.add(typeOf(key)));
        for (String type : types) {
            boolean figure = catalog.type(type).map(t -> t.base() == TypeSpec.Base.FIGURE).orElse(false);
            Map<String, String> style = flatten(figure ? resolver.rule(type) : resolver.resolve(type, 0), type);
            if (!figure) {
                style.entrySet().removeIf(e -> e.getValue().equals(defaults.get(e.getKey())));
            }
            section(out, type, style);
        }
        new TreeSet<>(theme.controls().keySet()).forEach(key -> {
            if (!key.equals(typeOf(key))) {
                section(out, key, flatten(resolver.rule(key), typeOf(key)));
            }
        });
        new TreeSet<>(theme.classes().keySet()).forEach(key -> {
            String type = key.substring(0, key.indexOf('.'));
            section(out, "classes." + key, flatten(classRule(resolver, key), type));
        });
        new TreeSet<>(theme.registries().keySet()).forEach(r -> section(out, "registries." + r, flatten(resolver.registry(r), "registries." + r)));
        Set<String> platforms = new TreeSet<>(theme.platform().keySet());
        platforms.addAll(catalog.platformNames());
        for (String ws : platforms) {
            Map<String, JsonElement> settings = new LinkedHashMap<>(theme.platformSettings("*"));
            settings.putAll(theme.platformSettings(ws));
            Map<String, String> flat = new TreeMap<>();
            ThemeNames names = resolver.names();
            settings.forEach((key, value) -> flat.put(key, names.resolve(value, ThemeNames.Part.ANY, key).toString()));
            section(out, "platform." + ws, flat);
        }
        out.append('\n');
    }

    /** A class rule ({@code "*.popup"}, {@code "Table.popup"}) on its own, with references followed. */
    private static ResolvedStyle classRule(StyleResolver resolver, String key) {
        // A rule of only this class: resolve it over a type no rule names, then drop the defaults.
        Theme theme = resolver.theme();
        Theme alone = new Theme(Theme.FORMAT_VERSION, theme.id(), theme.name(), null, theme.dark(), theme.swatches(),
                theme.names(), Map.of(), Map.of(), Map.of("*.only", theme.classes().get(key)), Map.of(), Map.of(), Map.of());
        return new StyleResolver(alone).resolve("(class)", 0, "only");
    }

    private static String typeOf(String key) {
        int open = key.indexOf('[');
        return open > 0 ? key.substring(0, open) : key;
    }

    /** Every key of {@code style} with its value resolved, states as {@code state:key}. */
    private Map<String, String> flatten(ResolvedStyle style, String type) {
        Map<String, String> flat = new TreeMap<>();
        for (String key : style.keys()) {
            if (key.equals(ResolvedStyle.STATES)) {
                for (String state : style.states()) {
                    ResolvedStyle overrides = style.overridesOf(state);
                    for (String own : overrides.keys()) {
                        flat.put(state + ":" + own, read(overrides, type, own));
                    }
                }
            } else {
                flat.put(key, read(style, type, key));
            }
        }
        return flat;
    }

    private String read(ResolvedStyle style, String type, String key) {
        PropertyKind kind = kind(type, key);
        if (kind == PropertyKind.FONT) {
            FontSpec font = style.font(key).orElseThrow();
            return "font " + font.names() + " " + font.height() + " " + font.style();
        }
        if (kind == PropertyKind.COLOR) {
            RGBA c = style.color(key).orElseThrow();
            return String.format("#%02X%02X%02X%02X", c.rgb.red, c.rgb.green, c.rgb.blue, c.alpha);
        }
        return style.value(key).orElseThrow().toString();
    }

    private PropertyKind kind(String type, String key) {
        if (type.startsWith("registries.")) {
            String name = type.substring("registries.".length());
            return catalog.registry(name).stream().filter(p -> p.key().equals(key)).map(p -> p.kind()).findFirst()
                    .orElse(name.equals("fonts") ? PropertyKind.FONT : PropertyKind.COLOR);
        }
        return catalog.property(type, key).map(p -> p.kind()).orElseGet(() -> byName(key));
    }

    /** The format's rule for keys no type describes, such as those of {@code defaults} and {@code *.class}. */
    private static PropertyKind byName(String key) {
        if (key.equals("font") || key.endsWith("Font")) {
            return PropertyKind.FONT;
        }
        if (key.equals("background") || key.equals("foreground") || key.endsWith("Color") || key.endsWith("Background")
                || key.endsWith("Foreground")) {
            return PropertyKind.COLOR;
        }
        return null;
    }

    private static void section(StringBuilder out, String title, Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        values.forEach((key, value) -> lines.add("  " + key + " = " + value));
        out.append(title).append('\n');
        lines.forEach(line -> out.append(line).append('\n'));
    }
}
