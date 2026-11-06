package com.zapthemes.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertyKind;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.themes.BundledThemes;

/**
 * The full property catalog, with every module the demos use: it describes everything the bundled
 * themes set, and its default map names base names. That every key a styler reads is described is
 * checked by {@code checkCatalog} after the smoke tests ({@link CatalogCheck}).
 */
class CatalogCoverageTest {

    private final PropertyCatalog catalog = PropertyCatalog.load();

    @Test
    void everyModuleDescribesItsTypes() {
        assertThat(catalog.types()).extracting(TypeSpec::group)
                .contains("SWT", "Flat widgets", "Nebula", "Nebula incubation", "NatTable", "Nebula Visualization");
        assertThat(catalog.property("CDateTime", "pickerTodayColor").orElseThrow().defaultName()).isEqualTo("accent");
        assertThat(catalog.property("Grid", "lineColor").orElseThrow().defaultName()).isEqualTo("gridLine");
        assertThat(catalog.property("NatTable", "headerBackground")).as("from Table").isPresent();
        assertThat(catalog.property("GaugeFigure", "foreground").orElseThrow().defaultName())
                .as("from AbstractScaledWidgetFigure").isEqualTo("foreground");
        assertThat(catalog.type("GaugeFigure").orElseThrow().inherits()).isEqualTo("AbstractRoundRampedFigure");
        assertThat(catalog.styleClass("ruler")).isPresent();
        assertThat(catalog.registry("fonts")).anyMatch(p -> p.key().equals("org.eclipse.jface.headerfont"));
        assertThat(catalog.registry("colors")).anyMatch(p -> p.key().equals("com.zapthemes.text.keyword"));
    }

    @Test
    void theCatalogDescribesEveryBundledTheme() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));

        for (String id : BundledThemes.IDS) {
            assertThat(catalog.undescribed(registry.resolve(id))).as(id).isEmpty();
        }
    }

    @Test
    void everyDefaultNameIsABaseNameWithThatPart() {
        for (TypeSpec type : catalog.types()) {
            check(type.type(), type.allProperties());
        }
        catalog.registryNames().forEach(r -> check("registries." + r, catalog.registry(r)));
        catalog.platformNames().forEach(ws -> check("platform." + ws, catalog.platform(ws)));
    }

    private static void check(String where, List<PropertySpec> specs) {
        for (PropertySpec p : specs) {
            if (p.defaultName() == null) {
                continue;
            }
            NameSpec name = BaseNames.get(p.defaultName()).orElse(null);
            assertThat(name).as(where + "." + p.key() + " defaults to unknown name " + p.defaultName()).isNotNull();
            boolean fits = p.kind() == PropertyKind.COLOR ? name.hasColor() : name.hasFont();
            assertThat(fits).as(where + "." + p.key() + ": " + p.defaultName() + " has no " + p.kind().label()).isTrue();
        }
    }
}
