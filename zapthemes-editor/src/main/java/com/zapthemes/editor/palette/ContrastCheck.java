package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;

/**
 * The contrast badges of the colors page: every pair a base name declares
 * ({@link NameSpec#contrastWith()}, such as {@code foreground} on {@code background}), with its
 * WCAG ratio and level. Text needs 4.5:1; {@code disabled} text, which WCAG exempts, 3:1.
 */
public final class ContrastCheck {

    /** One declared pair: {@code name}'s color in front of {@code against}'s. */
    public record Pair(String name, String against, double ratio, double minimum) {

        public Contrast.Level level() {
            return Contrast.Level.of(ratio);
        }

        /** Whether the ratio reaches the pair's minimum. */
        public boolean ok() {
            return ratio >= minimum;
        }
    }

    private ContrastCheck() {
    }

    /** The minimum ratio for {@code name} on the names it declares. */
    public static double minimum(String name) {
        return "disabled".equals(name) ? Contrast.LARGE : Contrast.TEXT;
    }

    /** Every declared pair of which both names have a color in {@code colors}, in base-name order. */
    public static List<Pair> check(Map<String, RGB> colors) {
        List<Pair> pairs = new ArrayList<>();
        for (NameSpec spec : BaseNames.ALL) {
            RGB front = colors.get(spec.name());
            if (front == null) {
                continue;
            }
            for (String against : spec.contrastWith()) {
                RGB back = colors.get(against);
                if (back != null) {
                    pairs.add(new Pair(spec.name(), against, Contrast.ratio(front, back), minimum(spec.name())));
                }
            }
        }
        return pairs;
    }

    /** The pairs of {@link #check} below their minimum. */
    public static List<Pair> failures(Map<String, RGB> colors) {
        return check(colors).stream().filter(pair -> !pair.ok()).toList();
    }
}
