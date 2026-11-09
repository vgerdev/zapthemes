package com.zapthemes.widgets.titlebar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.eclipse.swt.graphics.Image;

/**
 * Who is signed in, for the avatar at the right of a {@link TitleBar} and the profile pop-up it
 * opens: a name, an email, an avatar image or initials, and actions such as "View account",
 * "Switch account" and "Sign out". The app owns the image.
 */
public final class TitleBarProfile {

    /** An action in the profile pop-up. */
    public record Action(String text, Runnable run) {
        public Action {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(run, "run");
        }
    }

    private final String name;
    private final String email;
    private Image image;
    private String initials;
    private final List<Action> actions = new ArrayList<>();

    public TitleBarProfile(String name, String email) {
        this.name = Objects.requireNonNull(name, "name");
        this.email = email == null ? "" : email;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public Image image() {
        return image;
    }

    /** The avatar picture, clipped to a circle; initials are shown without one. */
    public TitleBarProfile image(Image image) {
        this.image = image;
        return this;
    }

    /** The initials on the avatar circle; by default the first letters of the name's first and last words. */
    public String initials() {
        return initials != null ? initials : initialsOf(name);
    }

    public TitleBarProfile initials(String initials) {
        this.initials = initials;
        return this;
    }

    public List<Action> actions() {
        return List.copyOf(actions);
    }

    public TitleBarProfile action(String text, Runnable run) {
        actions.add(new Action(text, run));
        return this;
    }

    static String initialsOf(String name) {
        List<String> words = new ArrayList<>(List.of(name.trim().split("\\s+")));
        // "Paul S Davenport III" is PD: name suffixes aren't initials.
        while (words.size() > 1 && words.getLast().matches("(?i)(jr|sr|ii|iii|iv|v|phd|md)\\.?,?")) {
            words.removeLast();
        }
        if (words.isEmpty() || words.getFirst().isEmpty()) {
            return "?";
        }
        String first = words.getFirst().substring(0, 1);
        String last = words.size() > 1 ? words.getLast().substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
