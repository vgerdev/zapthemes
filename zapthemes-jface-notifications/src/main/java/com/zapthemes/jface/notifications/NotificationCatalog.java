package com.zapthemes.jface.notifications;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.ClassSpec;
import com.zapthemes.core.catalog.PropertyCatalog;

/** The style classes the notifications fork sets, for the {@link PropertyCatalog}. */
public final class NotificationCatalog implements CatalogProvider {

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.styleClass(ClassSpec.of(NotificationTheming.NOTIFICATION, "A notification popup's frame"));
    }
}
