package com.zapthemes.nattable;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.style.theme.DefaultNatTableThemeConfiguration;
import org.eclipse.nebula.widgets.nattable.style.theme.IThemeExtension;
import org.eclipse.nebula.widgets.nattable.style.theme.ThemeConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.core.style.DefaultStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Themes every {@link NatTable}: a styler for the control type {@code NatTable} (registered through
 * {@link StylerProvider}) turns the theme's {@code NatTable} rule into a
 * {@link ZapNatTableThemeConfiguration} and gives it to {@link NatTable#setTheme}, when the table is
 * created and on every theme switch. Nothing to call: putting {@code zapthemes-nattable} on the
 * class path is enough.
 *
 * <ul>
 *   <li>{@link #addExtension} adds NatTable {@link IThemeExtension}s (styling of your own labels)
 *       to every configuration the bridge builds for a table.</li>
 *   <li>{@link #MANUAL} ({@code natTable.setData(NatTableTheming.MANUAL, true)}) leaves a table's
 *       NatTable theme to the application.</li>
 *   <li>A theme without {@code background} or {@code headerBackground} for NatTable (no theme at
 *       all, for one) leaves a table that was never themed alone, and gives a table the bridge
 *       themed before NatTable's {@code DefaultNatTableThemeConfiguration}.</li>
 * </ul>
 *
 * The bridge applies the theme again on the table's first paint, so it survives an application
 * that calls {@code configure()} on a table created with {@code autoConfigure} off after the theme
 * reached it.
 */
public final class NatTableTheming implements StylerProvider, CatalogProvider {

    /** Data key: {@code Boolean.TRUE} leaves the table's NatTable theme to the application. */
    public static final String MANUAL = "zapthemes.nattable.manual";

    private static final String EXTENSIONS = "zapthemes.nattable.extensions";
    private static final String THEMED = "zapthemes.nattable.themed";
    private static final String FIRST_PAINT = "zapthemes.nattable.firstPaint";
    private static final String CURRENT = "zapthemes.nattable.configuration";

    @Override
    public void register(StylerRegistry registry) {
        registry.register("NatTable", new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                if (widget instanceof NatTable table) {
                    theme(table, style, context);
                }
            }
        });
    }

    /** Adds {@code extension} to the configurations the bridge builds for {@code table}, from the next theme on. */
    public static void addExtension(NatTable table, IThemeExtension extension) {
        extensions(table).add(extension);
    }

    /** The configuration the bridge builds for a table from {@code style}, the table's resolved theme style. */
    public static ThemeConfiguration configuration(ResolvedStyle style, ThemeContext context) {
        return new ZapNatTableThemeConfiguration(style, context);
    }

    static void theme(NatTable table, ResolvedStyle style, ThemeContext context) {
        if (Boolean.TRUE.equals(table.getData(MANUAL))) {
            return;
        }
        ThemeConfiguration configuration;
        if (style.has("background") || style.has("headerBackground")) {
            configuration = configuration(style, context);
            table.setData(THEMED, Boolean.TRUE);
        } else if (Boolean.TRUE.equals(table.getData(THEMED))) {
            configuration = new DefaultNatTableThemeConfiguration();
            table.setData(THEMED, null);
        } else {
            return;
        }
        for (IThemeExtension extension : extensions(table)) {
            configuration.addThemeExtension(extension);
        }
        table.setData(CURRENT, configuration);
        table.setTheme(configuration);
        reapplyOnFirstPaint(table);
    }

    private static void reapplyOnFirstPaint(NatTable table) {
        if (table.getData(FIRST_PAINT) != null) {
            return;
        }
        table.setData(FIRST_PAINT, Boolean.TRUE);
        Listener[] once = new Listener[1];
        once[0] = event -> {
            table.removeListener(SWT.Paint, once[0]);
            // configure() may have registered NatTable's default styles over the theme meanwhile.
            table.getDisplay().asyncExec(() -> {
                if (!table.isDisposed() && table.getData(CURRENT) instanceof ThemeConfiguration current) {
                    table.setTheme(current);
                }
            });
        };
        table.addListener(SWT.Paint, once[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<IThemeExtension> extensions(NatTable table) {
        if (!(table.getData(EXTENSIONS) instanceof List<?>)) {
            table.setData(EXTENSIONS, new ArrayList<IThemeExtension>());
        }
        return (List<IThemeExtension>) table.getData(EXTENSIONS);
    }

    /** NatTable takes a Table's keys (its rule inherits Table's), plus what only NatTable has. */
    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.type(TypeSpec.of("NatTable", "NatTable", TypeSpec.Base.COMPOSITE).inheriting("Table")
                .with(PropertySpec.font("headerFont", null, "Column and row header font; the font when not set"),
                        PropertySpec.color("filterRowBackground", null, "The filter row; the header background when not set"),
                        PropertySpec.color("summaryRowBackground", null, "The summary row; the header background when not set"),
                        PropertySpec.color("freezeSeparatorColor", "border", "The line after frozen rows and columns"),
                        PropertySpec.color("errorForeground", "error", "Cells failing validation"),
                        PropertySpec.color("accentColor", "accent", "Sort, filter and tree icons' accent"))
                .withStates("hover")
                .described("Painted entirely by NatTable, from a ThemeConfiguration built from this rule"));
    }
}
