package com.zapthemes.editor.palette;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;

/**
 * The text on selected and highlighted items against the highlight it is painted on, as the
 * painters read them: the rule of each control type over {@code defaults} and its {@code inherit}
 * chain, so a per-control override counts and a property the rule does not set falls back the way
 * the painter does.
 *
 * <table>
 *   <caption>The pairs, by control type</caption>
 *   <tr><th>Type</th><th>Item</th><th>Text</th><th>On</th></tr>
 *   <tr><td>List (also a CCombo's drop-down list)</td><td>selected items</td>
 *       <td>{@code selectionForeground}, else {@code foreground}</td><td>{@code selectionBackground}</td></tr>
 *   <tr><td>Table, Tree</td><td>selected rows</td>
 *       <td>{@code selectionForeground}, else {@code foreground}</td><td>{@code selectionBackground}</td></tr>
 *   <tr><td>Table, Tree</td><td>the focus cell</td>
 *       <td>{@code focusCellForeground}, else {@code foreground}</td><td>{@code focusCellBackground}</td></tr>
 *   <tr><td>Table, Tree</td><td>the row under the mouse</td>
 *       <td>{@code foreground}</td><td>{@code states.hover.background}</td></tr>
 * </table>
 *
 * A pair whose highlight the rule does not set is not checked: the platform paints it in its own
 * colors. Text needs 4.5:1, as on the colors page. Rules for style bits and classes are not read.
 */
public final class SelectionContrast {

    /** The types whose selected or highlighted items the theme paints. */
    public static final List<String> TYPES = List.of("List", "Table", "Tree");

    /**
     * One painted pair: in {@code type}'s rule, the text color of {@code textKey} on
     * {@code backKey}'s ({@code states.hover.background} for the row under the mouse).
     * {@code text} and {@code on} are the common names (or swatches) the keys refer to, or
     * {@code "Type.key"} for a color of the rule's own.
     */
    public record Pair(String type, String item, String textKey, String backKey, String text, String on, RGB front, RGB back,
            double ratio) {

        public double minimum() {
            return Contrast.TEXT;
        }

        public boolean ok() {
            return ratio >= minimum();
        }

        /** Where the pair is painted: "Table: selected rows". */
        public String where() {
            return type + ": " + item;
        }

        /** "Table: selected rows: selectionForeground #8A8A8A on selectionBackground #9A9A9A is 1.2:1, below 4.5:1". */
        public String message() {
            return String.format(Locale.ROOT, "%s: %s %s on %s %s is %.1f:1, below %.1f:1", where(), textKey, hex(front), backKey,
                    hex(back), ratio, minimum());
        }

        /**
         * Whether {@link TextContrast} reports this pair as {@code problem}: the same rule (the
         * hover state's for the row under the mouse), keys and colors.
         */
        public boolean reportedBy(TextContrast.Problem problem) {
            return problem.equals(asProblem());
        }

        /** As {@link TextContrast} would report it: in the type's rule, or its hover state's. */
        public TextContrast.Problem asProblem() {
            boolean hover = backKey.startsWith("states.hover.");
            return new TextContrast.Problem(hover ? type + ":hover" : type, textKey,
                    hover ? backKey.substring("states.hover.".length()) : backKey, front, back, ratio, minimum());
        }

        /** As a pair of the colors page. */
        public ContrastCheck.Pair asNamePair() {
            return new ContrastCheck.Pair(text, on, ratio, minimum(), where());
        }
    }

    private SelectionContrast() {
    }

    /** Every pair of {@link #TYPES} that {@code theme} paints, in type order. */
    public static List<Pair> check(Theme theme) {
        StyleResolver resolver = new StyleResolver(theme);
        List<Pair> pairs = new ArrayList<>();
        for (String type : TYPES) {
            ResolvedStyle style = resolver.resolve(type, 0);
            Map<String, JsonElement> raw = raw(theme, type);
            boolean rows = !type.equals("List");
            pair(type, rows ? "selected rows" : "selected items", style, "selectionForeground", style, "selectionBackground",
                    "selectionBackground", raw).ifPresent(pairs::add);
            if (rows) {
                pair(type, "the focus cell", style, "focusCellForeground", style, "focusCellBackground", "focusCellBackground", raw)
                        .ifPresent(pairs::add);
                if (style.states().contains("hover")) {
                    Map<String, JsonElement> hover = new LinkedHashMap<>(raw);
                    if (raw.get("states") instanceof JsonObject states && states.get("hover") instanceof JsonObject state) {
                        hover.put("states.hover.background", state.get("background"));
                    }
                    pair(type, "the row under the mouse", style, "foreground", style.state("hover"), "background",
                            "states.hover.background", hover).ifPresent(pairs::add);
                }
            }
        }
        return pairs;
    }

    /** The pairs of {@link #check} below their minimum. */
    public static List<Pair> failures(Theme theme) {
        return check(theme).stream().filter(pair -> !pair.ok()).toList();
    }

    /**
     * {@code textKey} (else {@code foreground}) on {@code backKey}, which is shown as
     * {@code shownBackKey}; empty when the highlight is not set or cannot be read.
     */
    private static Optional<Pair> pair(String type, String item, ResolvedStyle textStyle, String textKey, ResolvedStyle backStyle,
            String backKey, String shownBackKey, Map<String, JsonElement> raw) {
        if (!backStyle.has(backKey)) {
            return Optional.empty();
        }
        String shownKey = textStyle.has(textKey) ? textKey : "foreground";
        try {
            Optional<RGBA> front = textStyle.color(shownKey);
            Optional<RGBA> back = backStyle.color(backKey);
            if (front.isEmpty() || back.isEmpty()) {
                return Optional.empty();
            }
            RGB f = front.get().rgb;
            RGB b = back.get().rgb;
            return Optional.of(new Pair(type, item, shownKey, shownBackKey, side(type, shownKey, raw.get(shownKey)),
                    side(type, shownBackKey, raw.get(shownBackKey)), f, b, Contrast.ratio(f, b)));
        } catch (RuntimeException e) {
            // An unknown name, a cycle or a bad value: an error the validator reports.
            return Optional.empty();
        }
    }

    /** The name or swatch {@code value} refers to, else {@code "Type.key"}. */
    private static String side(String type, String key, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().startsWith("$")) {
            return value.getAsString().substring(1);
        }
        return type + "." + key;
    }

    /** {@code type}'s rule as written, over {@code defaults} and its {@code inherit} chain, as the resolver merges it. */
    private static Map<String, JsonElement> raw(Theme theme, String type) {
        Map<String, JsonElement> merged = new LinkedHashMap<>(theme.defaults());
        Deque<Map<String, JsonElement>> chain = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (String at = type; at != null && seen.add(at);) {
            Map<String, JsonElement> rule = theme.controls().get(at);
            if (rule == null) {
                break;
            }
            chain.push(rule);
            JsonElement inherit = rule.get("inherit");
            at = inherit != null && inherit.isJsonPrimitive() ? inherit.getAsString() : null;
        }
        chain.forEach(merged::putAll);
        return merged;
    }

    private static String hex(RGB rgb) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
    }
}
