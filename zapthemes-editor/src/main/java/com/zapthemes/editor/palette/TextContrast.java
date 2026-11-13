package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.graphics.RGB;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.platform.Win32ThemeSupport;
import com.zapthemes.core.style.ThemeNames;

/**
 * The text colors of a theme's rules against the background they are drawn on: in every rule
 * ({@code defaults}, each control, its states, each class, each platform), a {@code foreground}
 * or {@code ...Foreground} key with the matching {@code background} or {@code ...Background} key
 * ({@code headerForeground} on {@code headerBackground}). A rule is read over the ones it builds
 * on ({@code defaults}, then the control's type), so a control that changes only its background is
 * checked against the foreground it keeps. Text needs 4.5:1; disabled text ({@code disabled...}
 * keys and the {@code disabled} state), which WCAG exempts, 3:1. The few {@code foreground} keys
 * that are not text, such as a progress bar's fill, are left out ({@link #NOT_TEXT}).
 *
 * <p>Also Windows' colored menu bar: SWT draws its text white whatever {@code menuBarForeground}
 * says, so a light {@code menuBarBackground} cannot be used there and the menu bar stays native.
 */
public final class TextContrast {

    private static final String FOREGROUND = "Foreground";
    private static final String STATES = "states";

    /**
     * {@code Type.key}s or {@code rule.key}s named like a text color that color something else:
     * fills, tracks and dots, and the frames (popups whose background shows only as the line
     * around their content).
     */
    static final Set<String> NOT_TEXT = Set.of("ProgressBar.foreground", "ProgressCircle.foreground", "RangeSlider.foreground",
            "Carousel.circleForeground", "*.frame.foreground", "*.notification.foreground");

    /** One pair below its minimum: {@code foreground}'s color on {@code background}'s, in {@code rule}. */
    public record Problem(String rule, String foreground, String background, RGB front, RGB back, double ratio, double minimum) {

        /** "Table: headerForeground #8A8A8A on headerBackground #9A9A9A is 1.2:1, below 4.5:1". */
        public String message() {
            return String.format(Locale.ROOT, "%s: %s %s on %s %s is %.1f:1, below %.1f:1", rule, foreground, hex(front), background,
                    hex(back), ratio, minimum);
        }
    }

    private TextContrast() {
    }

    /** Every text pair of {@code theme}'s rules below its minimum, then the Windows menu bar's. */
    public static List<Problem> check(Theme theme) {
        ThemeNames names = ThemeNames.of(theme);
        List<Problem> problems = new ArrayList<>();
        Map<String, JsonElement> defaults = theme.defaults();
        checkRule(names, "defaults", Map.of(), defaults, problems);
        theme.controls().forEach((type, rule) -> {
            Map<String, JsonElement> base = merged(defaults, typeRule(theme, type));
            checkRule(names, type, base, rule, problems);
        });
        theme.classes().forEach((selector, rule) -> {
            int dot = selector.indexOf('.');
            String type = dot > 0 ? selector.substring(0, dot) : "*";
            Map<String, JsonElement> base = merged(defaults, type.equals("*") ? Map.of() : theme.controls().getOrDefault(type, Map.of()));
            checkRule(names, selector, base, rule, problems);
        });
        theme.platform().forEach((platform, settings) -> checkPairs(names, "platform." + platform, settings, settings, problems));
        menuBar(names, theme).ifPresent(problems::add);
        return problems;
    }

    /** Windows' menu bar: SWT's white text on {@code platform.win32.menuBarBackground}. */
    private static Optional<Problem> menuBar(ThemeNames names, Theme theme) {
        Map<String, JsonElement> win32 = theme.platform().getOrDefault("win32", Map.of());
        Optional<RGB> back = color(names, win32.get("menuBarBackground"), "platform.win32.menuBarBackground");
        if (back.isEmpty() || Win32ThemeSupport.menuBarColorsReadable(back)) {
            return Optional.empty();
        }
        RGB white = new RGB(255, 255, 255);
        return Optional.of(new Problem("platform.win32 (SWT draws menu bar text white; the menu bar stays native)", "menu bar text", "menuBarBackground",
                white, back.get(), Contrast.ratio(white, back.get()), Win32ThemeSupport.MENU_BAR_TEXT_CONTRAST));
    }

    /** {@code type}'s base rule: {@code Button[CHECK]} builds on {@code Button}. */
    private static Map<String, JsonElement> typeRule(Theme theme, String type) {
        int bracket = type.indexOf('[');
        return bracket > 0 ? theme.controls().getOrDefault(type.substring(0, bracket), Map.of()) : Map.of();
    }

    /** {@code rule} over {@code base}, and each of its states over that. */
    private static void checkRule(ThemeNames names, String where, Map<String, JsonElement> base, Map<String, JsonElement> rule,
            List<Problem> problems) {
        Map<String, JsonElement> effective = merged(base, rule);
        checkPairs(names, where, effective, rule, problems);
        if (rule.get(STATES) instanceof JsonObject states) {
            for (Map.Entry<String, JsonElement> state : states.entrySet()) {
                if (state.getValue() instanceof JsonObject props) {
                    Map<String, JsonElement> own = props.asMap();
                    checkPairs(names, where + ":" + state.getKey(), merged(effective, own), own, problems);
                }
            }
        }
    }

    /** The pairs of {@code effective} that {@code own} sets a part of, so each is reported where it is made. */
    private static void checkPairs(ThemeNames names, String where, Map<String, JsonElement> effective, Map<String, JsonElement> own,
            List<Problem> problems) {
        String type = where.split("[.:\\[]", 2)[0];
        String rule = where.split(":", 2)[0];
        boolean disabledState = where.endsWith(":disabled");
        for (String key : effective.keySet()) {
            if (NOT_TEXT.contains(type + "." + key) || NOT_TEXT.contains(rule + "." + key)) {
                continue;
            }
            String background = backgroundFor(key);
            if (background == null || !effective.containsKey(background) || !(own.containsKey(key) || own.containsKey(background))) {
                continue;
            }
            Optional<RGB> front = color(names, effective.get(key), where + "." + key);
            Optional<RGB> back = color(names, effective.get(background), where + "." + background);
            if (front.isEmpty() || back.isEmpty()) {
                continue;
            }
            double ratio = Contrast.ratio(front.get(), back.get());
            double minimum = key.startsWith("disabled") || disabledState ? Contrast.LARGE : Contrast.TEXT;
            if (ratio < minimum) {
                problems.add(new Problem(where, key, background, front.get(), back.get(), ratio, minimum));
            }
        }
    }

    /** {@code foreground} → {@code background}, {@code headerForeground} → {@code headerBackground}; null for other keys. */
    static String backgroundFor(String key) {
        if (key.equals("foreground")) {
            return "background";
        }
        if (key.endsWith(FOREGROUND) && key.length() > FOREGROUND.length()) {
            return key.substring(0, key.length() - FOREGROUND.length()) + "Background";
        }
        return null;
    }

    private static Map<String, JsonElement> merged(Map<String, JsonElement> base, Map<String, JsonElement> over) {
        Map<String, JsonElement> merged = new LinkedHashMap<>(base);
        merged.putAll(over);
        merged.remove(STATES);
        return merged;
    }

    /** A plain color, or a reference to one; empty for anything else (the validator reports bad values). */
    private static Optional<RGB> color(ThemeNames names, JsonElement value, String where) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            JsonElement resolved = names.resolve(value, ThemeNames.Part.COLOR, where);
            if (resolved.isJsonPrimitive() && resolved.getAsJsonPrimitive().isString()) {
                String text = resolved.getAsString();
                if (text.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
                    return Optional.of(new RGB(Integer.parseInt(text.substring(1, 3), 16), Integer.parseInt(text.substring(3, 5), 16),
                            Integer.parseInt(text.substring(5, 7), 16)));
                }
            }
        } catch (RuntimeException e) {
            // An unknown name or a cycle: an error the validator reports.
        }
        return Optional.empty();
    }

    private static String hex(RGB rgb) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
    }
}
