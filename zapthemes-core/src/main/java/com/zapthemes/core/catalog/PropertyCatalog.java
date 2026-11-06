package com.zapthemes.core.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Everything a theme can set, as the modules on the class path describe it: control types and
 * their properties ({@link TypeSpec}), style classes ({@link ClassSpec}), the entries of the
 * {@code registries} blocks and the {@code platform} settings. Each property may name the common
 * name it takes by default ({@link PropertySpec#defaultName()}), which together make the default
 * map.
 *
 * <p>Tools such as the theme editor list it; tests check that it covers every key the stylers read
 * and every key the bundled themes set. It needs no {@code Display}.
 */
public final class PropertyCatalog {

    private final Map<String, TypeSpec> types;
    private final Map<String, ClassSpec> classes;
    private final Map<String, List<PropertySpec>> registries;
    private final Map<String, List<PropertySpec>> platform;

    private PropertyCatalog(Builder builder) {
        this.types = Map.copyOf(builder.types);
        this.classes = Map.copyOf(builder.classes);
        this.registries = copy(builder.registries);
        this.platform = copy(builder.platform);
    }

    /** The core catalog and that of every {@link CatalogProvider} on the class path. */
    public static PropertyCatalog load() {
        return load(PropertyCatalog.class.getClassLoader());
    }

    public static PropertyCatalog load(ClassLoader loader) {
        Builder builder = new Builder();
        new CoreCatalog().describe(builder);
        ServiceLoader.load(CatalogProvider.class, loader).forEach(provider -> provider.describe(builder));
        return builder.build();
    }

    /** Every type, by group then name. */
    public List<TypeSpec> types() {
        List<TypeSpec> list = new ArrayList<>(types.values());
        list.sort(Comparator.comparing(TypeSpec::group).thenComparing(TypeSpec::type));
        return list;
    }

    public Optional<TypeSpec> type(String type) {
        return Optional.ofNullable(types.get(type));
    }

    /**
     * The property {@code key} of {@code type}: its own or base property, else one of the type it
     * {@link TypeSpec#inherits}.
     */
    public Optional<PropertySpec> property(String type, String key) {
        Set<String> seen = new HashSet<>();
        for (TypeSpec spec = types.get(type); spec != null && seen.add(spec.type()); spec = spec.inherits() == null ? null
                : types.get(spec.inherits())) {
            for (PropertySpec p : spec.allProperties()) {
                if (p.key().equals(key)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /** {@code type}'s properties with those of the types it inherits, nearest first. */
    public List<PropertySpec> properties(String type) {
        Map<String, PropertySpec> byKey = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (TypeSpec spec = types.get(type); spec != null && seen.add(spec.type()); spec = spec.inherits() == null ? null
                : types.get(spec.inherits())) {
            spec.allProperties().forEach(p -> byKey.putIfAbsent(p.key(), p));
        }
        return List.copyOf(byKey.values());
    }

    public Collection<ClassSpec> classes() {
        return classes.values().stream().sorted(Comparator.comparing(ClassSpec::name)).toList();
    }

    public Optional<ClassSpec> styleClass(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    /** The known entries of a registry block ({@code "colors"}, {@code "fonts"}, {@code "systemColors"}). */
    public List<PropertySpec> registry(String name) {
        return registries.getOrDefault(name, List.of());
    }

    public Set<String> registryNames() {
        return registries.keySet();
    }

    /** The settings of a platform block ({@code "*"}, {@code "win32"}, {@code "gtk"}, {@code "cocoa"}). */
    public List<PropertySpec> platform(String ws) {
        return platform.getOrDefault(ws, List.of());
    }

    public Set<String> platformNames() {
        return platform.keySet();
    }

    /**
     * Whether a styler reading {@code key} of {@code owner} reads a described property: owner is a
     * control type, {@code registries.<name>} or {@code platform} (any platform block), as
     * {@link com.zapthemes.core.style.KeyRecorder} records them.
     */
    public boolean describesRead(String owner, String key) {
        if (owner.equals("platform")) {
            return platform.values().stream().anyMatch(block -> has(block, key));
        }
        if (owner.startsWith("registries.")) {
            return has(registry(owner.substring("registries.".length())), key);
        }
        return property(owner, key).isPresent();
    }

    /**
     * What {@code theme} sets that this catalog does not describe, one message per key with its
     * path: unknown types, style classes, properties, states, registry entries and platform
     * settings. Empty when the catalog covers the whole theme.
     */
    public List<String> undescribed(com.zapthemes.core.model.Theme theme) {
        List<String> problems = new ArrayList<>();
        theme.defaults().keySet().forEach(key -> {
            if (types.values().stream().noneMatch(t -> property(t.type(), key).isPresent())) {
                problems.add("defaults." + key + ": no type has this property");
            }
        });
        theme.controls().forEach((rule, props) -> {
            String type = rule.contains("[") ? rule.substring(0, rule.indexOf('[')) : rule;
            if (!types.containsKey(type)) {
                problems.add("controls." + rule + ": unknown type \"" + type + "\"");
                return;
            }
            checkRule("controls." + rule, type, props, problems);
        });
        theme.classes().forEach((rule, props) -> {
            int dot = rule.lastIndexOf('.');
            String type = rule.substring(0, Math.max(dot, 0));
            String cls = rule.substring(dot + 1);
            if (!classes.containsKey(cls)) {
                problems.add("classes." + rule + ": unknown style class \"" + cls + "\"");
            }
            if (type.equals("*")) {
                props.keySet().forEach(key -> {
                    if (!key.equals(com.zapthemes.core.style.ResolvedStyle.STATES)
                            && types.values().stream().noneMatch(t -> property(t.type(), key).isPresent())) {
                        problems.add("classes." + rule + "." + key + ": no type has this property");
                    }
                });
            } else if (!types.containsKey(type)) {
                problems.add("classes." + rule + ": unknown type \"" + type + "\"");
            } else {
                checkRule("classes." + rule, type, props, problems);
            }
        });
        theme.registries().forEach((name, entries) -> entries.keySet().forEach(key -> {
            if (!has(registry(name), key)) {
                problems.add("registries." + name + "." + key + ": not a known entry");
            }
        }));
        theme.platform().forEach((ws, settings) -> settings.keySet().forEach(key -> {
            if (!has(platform(ws), key)) {
                problems.add("platform." + ws + "." + key + ": not a known setting");
            }
        }));
        return problems;
    }

    private void checkRule(String path, String type, Map<String, com.google.gson.JsonElement> props, List<String> problems) {
        Set<String> keys = new HashSet<>();
        properties(type).forEach(p -> keys.add(p.key()));
        for (Map.Entry<String, com.google.gson.JsonElement> e : props.entrySet()) {
            String key = e.getKey();
            if (key.equals("inherit")) {
                continue;
            }
            if (key.equals(com.zapthemes.core.style.ResolvedStyle.STATES) && e.getValue().isJsonObject()) {
                List<String> states = states(type);
                e.getValue().getAsJsonObject().entrySet().forEach(state -> {
                    if (!states.contains(state.getKey())) {
                        problems.add(path + ".states." + state.getKey() + ": " + type + " does not paint this state");
                    } else if (state.getValue().isJsonObject()) {
                        state.getValue().getAsJsonObject().keySet().stream().filter(k -> !keys.contains(k))
                                .forEach(k -> problems.add(path + ".states." + state.getKey() + "." + k + ": not a property of " + type));
                    }
                });
            } else if (!keys.contains(key)) {
                problems.add(path + "." + key + ": not a property of " + type);
            }
        }
    }

    /** The states {@code type} paints, with those of the types it inherits. */
    public List<String> states(String type) {
        List<String> states = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TypeSpec spec = types.get(type); spec != null && seen.add(spec.type()); spec = spec.inherits() == null ? null
                : types.get(spec.inherits())) {
            spec.states().stream().filter(s -> !states.contains(s)).forEach(states::add);
        }
        return states;
    }

    private static boolean has(List<PropertySpec> specs, String key) {
        return specs.stream().anyMatch(p -> p.key().equals(key));
    }

    private static Map<String, List<PropertySpec>> copy(Map<String, Map<String, PropertySpec>> blocks) {
        Map<String, List<PropertySpec>> copy = new LinkedHashMap<>();
        blocks.forEach((name, specs) -> copy.put(name, List.copyOf(specs.values())));
        return Map.copyOf(copy);
    }

    /** Collects descriptions; a type, class or entry described twice is merged. */
    public static final class Builder {

        private final Map<String, TypeSpec> types = new LinkedHashMap<>();
        private final Map<String, ClassSpec> classes = new LinkedHashMap<>();
        private final Map<String, Map<String, PropertySpec>> registries = new LinkedHashMap<>();
        private final Map<String, Map<String, PropertySpec>> platform = new LinkedHashMap<>();

        public Builder type(TypeSpec spec) {
            types.merge(spec.type(), spec, TypeSpec::mergedWith);
            return this;
        }

        public Builder styleClass(ClassSpec spec) {
            classes.merge(spec.name(), spec, ClassSpec::mergedWith);
            return this;
        }

        public Builder registry(String name, PropertySpec... entries) {
            Map<String, PropertySpec> block = registries.computeIfAbsent(name, n -> new LinkedHashMap<>());
            for (PropertySpec entry : entries) {
                block.merge(entry.key(), entry, PropertySpec::mergedWith);
            }
            return this;
        }

        public Builder platform(String ws, PropertySpec... settings) {
            Map<String, PropertySpec> block = platform.computeIfAbsent(ws, n -> new LinkedHashMap<>());
            for (PropertySpec setting : settings) {
                block.merge(setting.key(), setting, PropertySpec::mergedWith);
            }
            return this;
        }

        public PropertyCatalog build() {
            return new PropertyCatalog(this);
        }
    }
}
