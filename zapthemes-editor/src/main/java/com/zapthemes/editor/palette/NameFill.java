package com.zapthemes.editor.palette;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;

/**
 * Fills the colors of the base common names from a palette, when the names page opens: driven by
 * contrast, with surfaces in small lightness steps from the background.
 *
 * <ul>
 *   <li>{@code background}: the darkest swatch of a dark palette, the lightest of a light one.</li>
 *   <li>{@code surface}, {@code raised}, {@code stripe}, {@code hover}, {@code chrome}, and the
 *       lines {@code border} and {@code gridLine}: OKLCH lightness steps from the background, in
 *       its hue.</li>
 *   <li>{@code foreground}: the swatch with the best contrast on the background, made lighter or
 *       darker to reach 7:1 (AAA) on it.</li>
 *   <li>{@code accent}: the most saturated swatch; {@code link}, {@code focus}, {@code badge} and
 *       {@code selection} follow from it, and {@code onAccent} is white or black.</li>
 *   <li>{@code error}, {@code warning}, {@code success}, {@code info}: the accent's chroma moved to
 *       red, amber, green and blue; {@code keyword}, {@code string}, {@code comment} and
 *       {@code number}: hues around the accent's.</li>
 * </ul>
 *
 * Every pair a base name declares reaches its {@link ContrastCheck#minimum}. A color the user set
 * is kept as it is, and the colors derived from it follow it.
 */
public final class NameFill {

    private NameFill() {
    }

    /** {@link #fill(Palette, Map)} with nothing set by the user. */
    public static Map<String, RGB> fill(Palette palette) {
        return fill(palette, Map.of());
    }

    /**
     * A color for every base name with a color part, in base-name order. {@code userSet} wins for
     * the names it has, which can include names that are not base names (they are passed through).
     */
    public static Map<String, RGB> fill(Palette palette, Map<String, RGB> userSet) {
        if (palette.swatches().isEmpty()) {
            throw new IllegalArgumentException("An empty palette fills nothing");
        }
        Filler f = new Filler(palette, userSet);
        return f.run();
    }

    private static final class Filler {
        private final Palette palette;
        private final Map<String, RGB> userSet;
        private final Map<String, RGB> filled = new LinkedHashMap<>();
        private final boolean dark;

        Filler(Palette palette, Map<String, RGB> userSet) {
            this.palette = palette;
            this.userSet = userSet;
            this.dark = palette.dark();
        }

        Map<String, RGB> run() {
            List<RGB> colors = palette.colors();
            RGB bg = put("background", () -> colors.stream()
                    .min(Comparator.comparingDouble(c -> (dark ? 1 : -1) * Oklch.of(c).l())).orElseThrow());
            Oklch b = Oklch.of(bg);
            RGB surface = put("surface", () -> step(b, dark ? b.l() + 0.045 : b.l() + (1 - b.l()) * 0.7));
            RGB raised = put("raised", () -> step(b, dark ? b.l() + 0.09 : b.l() - 0.045));
            put("stripe", () -> {
                Oklch s = Oklch.of(surface);
                return step(s, dark ? s.l() + 0.02 : s.l() - 0.02);
            });
            put("hover", () -> step(b, dark ? b.l() + 0.13 : b.l() - 0.075));
            put("chrome", () -> step(b, dark ? b.l() - 0.025 : b.l() - 0.03));

            RGB fg = put("foreground", () -> {
                RGB best = colors.stream().max(Comparator.comparingDouble(c -> Contrast.ratio(c, bg))).orElseThrow();
                return Contrast.atLeast(Contrast.atLeast(best, Contrast.ENHANCED, bg), Contrast.TEXT, bg, surface, raised);
            });
            RGB accent = put("accent", () -> accentFrom(colors, bg, fg));
            Oklch a = Oklch.of(accent);
            RGB selection = put("selection", () -> new Oklch(dark ? 0.40 : 0.88, Math.min(a.c(), 0.12) * 0.6, a.h()).toRgb());

            put("muted", () -> Contrast.atLeast(mix(fg, bg, 0.38), Contrast.TEXT, bg, surface));
            put("disabled", () -> Contrast.atLeast(mix(fg, bg, 0.6), Contrast.LARGE, bg, surface));
            put("onAccent", () -> onColor(accent));
            put("link", () -> Contrast.atLeast(accent, Contrast.TEXT, bg, surface));
            put("selectionForeground", () -> Contrast.atLeast(fg, Contrast.TEXT, selection));
            RGB badge = put("badge", () -> accent);
            put("onBadge", () -> onColor(badge));

            for (String heading : new String[] { "heading1", "heading2", "heading3", "strong" }) {
                put(heading, () -> Contrast.atLeast(fg, Contrast.TEXT, bg));
            }
            put("caption", () -> Contrast.atLeast(filled.get("muted"), Contrast.TEXT, bg));

            put("border", () -> step(b, dark ? b.l() + 0.17 : b.l() - 0.16));
            put("gridLine", () -> step(b, dark ? b.l() + 0.10 : b.l() - 0.09));
            put("focus", () -> Contrast.atLeast(accent, Contrast.LARGE, bg));

            double chroma = Math.clamp(a.c(), 0.11, 0.17);
            status("error", 27, chroma, bg);
            status("warning", 75, chroma, bg);
            status("success", 148, chroma, bg);
            status("info", 250, chroma, bg);

            RGB titleBar = put("titleBar", () -> filled.get("chrome"));
            put("titleBarForeground", () -> Contrast.atLeast(fg, Contrast.TEXT, titleBar));
            RGB inactive = put("titleBarInactive", () -> bg);
            put("titleBarInactiveForeground", () -> Contrast.atLeast(filled.get("muted"), Contrast.TEXT, inactive));

            code("keyword", a.h(), surface);
            code("string", a.h() + 150, surface);
            code("number", a.h() + 60, surface);
            put("comment", () -> Contrast.atLeast(new Oklch(Oklch.of(filled.get("muted")).l(), 0.03, a.h() + 150).toRgb(),
                    Contrast.TEXT, surface));

            Map<String, RGB> ordered = new LinkedHashMap<>();
            for (NameSpec spec : BaseNames.ALL) {
                if (filled.containsKey(spec.name())) {
                    ordered.put(spec.name(), filled.get(spec.name()));
                }
            }
            userSet.forEach(ordered::putIfAbsent);
            return ordered;
        }

        private RGB put(String name, Supplier<RGB> computed) {
            RGB value = userSet.containsKey(name) ? userSet.get(name) : computed.get();
            filled.put(name, value);
            return value;
        }

        private void status(String name, double hue, double chroma, RGB bg) {
            put(name, () -> Contrast.atLeast(new Oklch(dark ? 0.72 : 0.55, chroma, hue).toRgb(), Contrast.TEXT, bg));
        }

        private void code(String name, double hue, RGB surface) {
            put(name, () -> Contrast.atLeast(new Oklch(dark ? 0.75 : 0.50, 0.13, hue).toRgb(), Contrast.TEXT, surface));
        }

        /** A neutral at {@code lightness}, in the background's hue, with little chroma. */
        private static RGB step(Oklch from, double lightness) {
            return new Oklch(Math.clamp(lightness, 0, 1), Math.min(from.c(), 0.03), from.h()).toRgb();
        }
    }

    /** The most saturated swatch other than the background and foreground; a blue if all are gray. */
    private static RGB accentFrom(List<RGB> colors, RGB bg, RGB fg) {
        return colors.stream().filter(c -> !c.equals(bg) && !c.equals(fg))
                .max(Comparator.comparingDouble(c -> Oklch.of(c).c()))
                .filter(c -> Oklch.of(c).c() >= 0.03)
                .orElse(new Oklch(0.6, 0.15, 255).toRgb());
    }

    /** White or black, whichever reads better on {@code color} (one of them always reaches 4.5:1). */
    static RGB onColor(RGB color) {
        RGB white = new RGB(255, 255, 255);
        RGB black = new RGB(0, 0, 0);
        return Contrast.ratio(white, color) >= Contrast.TEXT || Contrast.ratio(white, color) >= Contrast.ratio(black, color)
                ? white : black;
    }

    /** {@code from} moved {@code amount} (0 to 1) of the way to {@code to} in OKLCH lightness. */
    private static RGB mix(RGB from, RGB to, double amount) {
        Oklch f = Oklch.of(from);
        double lightness = f.l() + (Oklch.of(to).l() - f.l()) * amount;
        return f.withL(lightness).toRgb();
    }
}
