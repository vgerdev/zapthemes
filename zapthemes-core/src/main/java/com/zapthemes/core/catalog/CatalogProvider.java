package com.zapthemes.core.catalog;

/**
 * Service-provider interface ({@link java.util.ServiceLoader},
 * {@code META-INF/services/com.zapthemes.core.catalog.CatalogProvider}) through which a module
 * describes the control types, style classes and registry entries it themes, for
 * {@link PropertyCatalog}. A module that registers stylers describes what they read.
 */
public interface CatalogProvider {

    void describe(PropertyCatalog.Builder catalog);
}
