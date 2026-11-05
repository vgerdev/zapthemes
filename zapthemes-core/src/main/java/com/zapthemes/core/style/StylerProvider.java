package com.zapthemes.core.style;

/**
 * Service-provider interface ({@link java.util.ServiceLoader}) through which modules such as
 * {@code zapthemes-nebula} register stylers for their control types.
 */
public interface StylerProvider {

    void register(StylerRegistry registry);
}
