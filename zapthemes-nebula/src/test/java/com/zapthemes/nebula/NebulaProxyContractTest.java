package com.zapthemes.nebula;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.nebula.grid.Grid;
import com.zapthemes.nebula.grid.GridColumn;
import com.zapthemes.nebula.grid.GridItem;
import com.zapthemes.swt.SwtProxies;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * Every generated Nebula proxy mirrors its original, is tagged and themed on creation, and survives
 * theme switches; items reached only through {@code getItems()} are re-themed too.
 */
class NebulaProxyContractTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private static final List<Class<? extends Widget>> ALL_PROXIES = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
        ALL_PROXIES.addAll(NebulaProxies.ALL);
        ALL_PROXIES.addAll(SwtProxies.ALL);
    }

    @TestFactory
    Stream<DynamicTest> nebulaProxies() {
        ThemeManager manager = ThemeManager.get(display);
        return NebulaProxies.ALL.stream().map(proxy -> DynamicTest.dynamicTest(proxy.getSimpleName(), () -> {
            assertThat(signatures(proxy.getConstructors())).isEqualTo(signatures(proxy.getSuperclass().getConstructors()));
            manager.setTheme(light);
            Shell shell = new Shell(display);
            shell.setLayout(new FillLayout());
            try {
                Widget widget = create(proxy, shell);
                prepare(widget);
                assertThat(widget.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo(proxy.getSimpleName());
                shell.setSize(300, 200);
                shell.open();
                manager.setTheme(dark);
                dispatch();
                manager.setTheme(light);
                dispatch();
                assertThat(widget.isDisposed()).isFalse();
            } finally {
                shell.dispose();
            }
        }));
    }

    @Test
    void gridItemsAreRethemedThroughGetItems() {
        ThemeManager manager = ThemeManager.get(display);
        manager.setTheme(light);
        Shell shell = new Shell(display);
        Grid grid = new Grid(shell, SWT.NONE);
        new GridColumn(grid, SWT.NONE).setWidth(100);
        GridItem item = new GridItem(grid, SWT.NONE);
        item.setText("row");
        try {
            manager.setTheme(dark);
            RGB surface = new RGB(0x2B, 0x2D, 0x30);
            assertThat(item.getBackground().getRGB()).isEqualTo(surface);
            assertThat(((Control) grid).getBackground().getRGB()).isEqualTo(surface);
            assertThat(grid.getLineColor().getRGB()).isEqualTo(new RGB(0x4A, 0x4D, 0x52));
        } finally {
            shell.dispose();
        }
    }

    /** Creates {@code proxy} with its simplest constructor, making suitable parents from proxies. */
    private static Widget create(Class<?> proxy, Shell shell) throws Exception {
        Constructor<?> constructor = Arrays.stream(proxy.getConstructors())
                .min(Comparator.comparingInt(NebulaProxyContractTest::cost))
                .orElseThrow();
        Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean styleSet = false;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) {
                args[i] = styleSet ? 0 : SWT.NONE;
                styleSet = true;
            } else if (types[i] == boolean.class) {
                args[i] = false;
            } else if (types[i].isInstance(shell)) {
                args[i] = shell;
            } else if (Widget.class.isAssignableFrom(types[i])) {
                args[i] = create(proxyFor(types[i]), shell);
            } else {
                args[i] = null;
            }
        }
        try {
            return (Widget) constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SWTError || e.getCause() instanceof UnsatisfiedLinkError) {
                Assumptions.abort(proxy.getSimpleName() + " unavailable here: " + e.getCause());
            }
            throw e;
        }
    }

    private static Class<?> proxyFor(Class<?> type) {
        return ALL_PROXIES.stream()
                .filter(p -> p.getSuperclass() == type)
                .findFirst()
                .orElseGet(() -> ALL_PROXIES.stream().filter(type::isAssignableFrom).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No proxy to use as " + type.getName())));
    }

    /** Upstream widgets that cannot paint without content get the minimum a real app gives them. */
    private static void prepare(Widget widget) {
        if (widget instanceof org.eclipse.nebula.widgets.badgedlabel.BadgedLabel label) {
            label.setImage(display.getSystemImage(SWT.ICON_INFORMATION));
            label.setText("label");
        } else if (widget instanceof org.eclipse.nebula.widgets.pgroup.PGroupToolItem item) {
            item.setText("tool");
        }
    }

    /**
     * Prefer (parent, style); avoid constructors with non-widget, non-primitive parameters and those
     * whose parent is the item's own kind (GridItem(GridItem, int)), which would recurse.
     */
    private static int cost(Constructor<?> constructor) {
        int cost = constructor.getParameterCount() * 10;
        for (Class<?> type : constructor.getParameterTypes()) {
            if (!type.isPrimitive() && !Widget.class.isAssignableFrom(type)) {
                cost += 100;
            }
            if (Widget.class.isAssignableFrom(type) && type.isAssignableFrom(constructor.getDeclaringClass())
                    && !type.isAssignableFrom(Shell.class)) {
                cost += 1000;
            }
        }
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length == 2 && types[1] == int.class && Widget.class.isAssignableFrom(types[0])) {
            cost -= 15;
        }
        return cost;
    }

    private static void dispatch() {
        while (display.readAndDispatch()) {
            // Paint.
        }
    }

    private static List<String> signatures(Constructor<?>[] constructors) {
        return Arrays.stream(constructors).map(c -> Arrays.toString(c.getParameterTypes())).sorted().toList();
    }
}
