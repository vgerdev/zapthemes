package com.zapthemes.core.platform;

import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.style.ResolvedStyle;

/**
 * GTK 3: menu colors through an application CSS provider for the whole screen. SWT has no color
 * API for menus, and GTK's {@code gtk-application-prefer-dark-theme} (what
 * {@link org.eclipse.swt.widgets.Shell#setDarkThemePreferred} sets) leaves the Adwaita menu bar light.
 *
 * <table>
 *   <caption>{@code platform.gtk} settings</caption>
 *   <tr><th>Key</th><th>CSS</th></tr>
 *   <tr><td>{@code menuBarBackground}, {@code menuBarForeground} (colors)</td><td>{@code menubar} and its items</td></tr>
 *   <tr><td>{@code menuBackground}, {@code menuForeground} (colors)</td><td>drop-down and pop-up {@code menu}s</td></tr>
 *   <tr><td>{@code menuSelectionBackground}, {@code menuSelectionForeground} (colors)</td><td>the hovered
 *       {@code menuitem}</td></tr>
 * </table>
 *
 * The CSS is replaced on every theme switch, and GTK restyles open windows itself. It goes through
 * SWT's internal {@code GTK}, {@code GTK3} and {@code GDK} classes, found by reflection. If they are
 * missing (another SWT version, or GTK 4) menus keep the GTK theme, and a warning is logged once.
 */
public final class GtkThemeSupport extends PlatformThemeSupport {

    private static final System.Logger LOG = System.getLogger(GtkThemeSupport.class.getName());

    private static Native nativeApi;
    private static boolean nativeLookupDone;

    @Override
    public void beforeTheme(Display display, ResolvedStyle settings, ThemeResources resources) {
        String css = css(settings);
        Native api = css.isEmpty() && nativeApi == null ? null : nativeApi();
        if (api != null) {
            api.load(css);
        }
    }

    /** The menu CSS for {@code settings}; empty when the theme sets no menu colors. */
    static String css(ResolvedStyle settings) {
        StringBuilder css = new StringBuilder();
        rule(css, "menubar", settings.color("menuBarBackground"), settings.color("menuBarForeground"));
        rule(css, "menubar > menuitem", Optional.empty(), settings.color("menuBarForeground"));
        rule(css, "menu, .menu, .context-menu", settings.color("menuBackground"), settings.color("menuForeground"));
        rule(css, "menu menuitem, .menu menuitem", Optional.empty(), settings.color("menuForeground"));
        rule(css, "menubar > menuitem:hover, menu menuitem:hover, .menu menuitem:hover",
                settings.color("menuSelectionBackground"), settings.color("menuSelectionForeground"));
        return css.toString();
    }

    private static void rule(StringBuilder css, String selector, Optional<RGBA> background, Optional<RGBA> foreground) {
        if (background.isEmpty() && foreground.isEmpty()) {
            return;
        }
        css.append(selector).append(" {");
        // background-image too: Adwaita paints some menu states with an image.
        background.ifPresent(c -> css.append(" background-color: ").append(rgba(c)).append("; background-image: none;"));
        foreground.ifPresent(c -> css.append(" color: ").append(rgba(c)).append(';'));
        css.append(" }\n");
    }

    private static String rgba(RGBA c) {
        return "rgba(" + c.rgb.red + "," + c.rgb.green + "," + c.rgb.blue + "," + c.alpha / 255.0 + ")";
    }

    private static synchronized Native nativeApi() {
        if (!nativeLookupDone) {
            nativeLookupDone = true;
            try {
                nativeApi = new Native();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.log(Level.WARNING, "SWT's GTK 3 CSS API is not available; menus keep the GTK theme", e);
            }
        }
        return nativeApi;
    }

    /** One screen-wide CSS provider, through SWT's internal GTK bindings. */
    private static final class Native {
        private final Method loadFromData;
        private final long provider;

        Native() throws ReflectiveOperationException {
            Class<?> gtk = Class.forName("org.eclipse.swt.internal.gtk.GTK");
            if (gtk.getField("GTK4").getBoolean(null)) {
                throw new UnsupportedOperationException("GTK 4");
            }
            Class<?> gtk3 = Class.forName("org.eclipse.swt.internal.gtk3.GTK3");
            Class<?> gdk = Class.forName("org.eclipse.swt.internal.gtk.GDK");
            loadFromData = gtk3.getMethod("gtk_css_provider_load_from_data", long.class, byte[].class, long.class, long[].class);
            provider = (long) gtk.getMethod("gtk_css_provider_new").invoke(null);
            long screen = (long) gdk.getMethod("gdk_screen_get_default").invoke(null);
            int priority = gtk.getField("GTK_STYLE_PROVIDER_PRIORITY_APPLICATION").getInt(null);
            gtk3.getMethod("gtk_style_context_add_provider_for_screen", long.class, long.class, int.class)
                    .invoke(null, screen, provider, priority);
        }

        void load(String css) {
            byte[] data = (css + '\0').getBytes(StandardCharsets.UTF_8);
            try {
                loadFromData.invoke(null, provider, data, -1L, null);
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not load the menu CSS", e);
            }
        }
    }
}
