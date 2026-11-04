package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.opengl.GLData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.CoolBar;
import com.zapthemes.swt.widgets.ExpandBar;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.TabFolder;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.ToolBar;
import com.zapthemes.swt.widgets.Tree;

/**
 * Every generated proxy: mirrors its original's public constructors, allows subclassing, tags
 * itself with its simple name, is themed on creation and re-themed by {@link ThemeManager#setTheme}.
 */
class ProxyContractTest {

    private static final RGB BG_A = new RGB(0x10, 0x20, 0x30);
    private static final RGB FG_A = new RGB(0xE0, 0xE0, 0xE0);
    private static final RGB BG_B = new RGB(0x40, 0x50, 0x60);

    private static final Theme THEME_A = theme("contract-a", "#102030", "#E0E0E0");
    private static final Theme THEME_B = theme("contract-b", "#405060", "#F0F0F0");

    private Display display;
    private ThemeManager manager;
    private Shell shell;

    private static Theme theme(String id, String background, String foreground) {
        return ThemeJson.read(new StringReader("""
                { "id": "%s", "defaults": { "background": "%s", "foreground": "%s" } }
                """.formatted(id, background, foreground)), id);
    }

    @BeforeEach
    void setUp() {
        display = Display.getDefault();
        manager = ThemeManager.get(display);
        manager.setTheme(THEME_A);
        shell = new Shell(display);
    }

    @AfterEach
    void tearDown() {
        for (var s : display.getShells()) {
            s.dispose();
        }
    }

    @TestFactory
    Stream<DynamicTest> proxiesMirrorTheirOriginal() {
        return SwtProxies.ALL.stream().map(proxy -> DynamicTest.dynamicTest(proxy.getSimpleName(), () -> {
            Class<?> original = proxy.getSuperclass();
            assertThat(original.getName()).startsWith("org.eclipse.swt.").endsWith("." + proxy.getSimpleName());
            assertThat(signatures(proxy.getConstructors())).isEqualTo(signatures(original.getConstructors()));
            Method checkSubclass = proxy.getDeclaredMethod("checkSubclass");
            assertThat(Modifier.isProtected(checkSubclass.getModifiers())).isTrue();
        }));
    }

    @TestFactory
    Stream<DynamicTest> proxiesAreTaggedAndThemedOnCreationAndOnSwitch() {
        // @BeforeEach runs once per factory, not per dynamic test: each test sets up its own shell.
        return SwtProxies.ALL.stream().map(proxy -> DynamicTest.dynamicTest(proxy.getSimpleName(), () -> {
            manager.setTheme(THEME_A);
            shell = new Shell(display);
            try {
                Widget widget = create(proxy);

                assertThat(widget.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo(proxy.getSimpleName());
                assertColors(widget, BG_A, FG_A);

                manager.setTheme(THEME_B);
                if (widget instanceof Control control && control.getShell() == shell || isItem(widget)) {
                    assertColors(widget, BG_B, null);
                }
            } finally {
                tearDown();
            }
        }));
    }

    private static boolean isItem(Widget widget) {
        return widget instanceof TableItem || widget instanceof TreeItem;
    }

    private static void assertColors(Widget widget, RGB background, RGB foreground) {
        String name = widget.getClass().getSimpleName();
        switch (widget) {
            case Control control -> {
                assertThat(control.getBackground().getRGB()).as(name + " background").isEqualTo(background);
                if (foreground != null) {
                    assertThat(control.getForeground().getRGB()).as(name + " foreground").isEqualTo(foreground);
                }
            }
            case TableItem item -> assertThat(item.getBackground().getRGB()).as(name).isEqualTo(background);
            case TreeItem item -> assertThat(item.getBackground().getRGB()).as(name).isEqualTo(background);
            case CTabItem item -> {
                if (foreground != null) {
                    assertThat(item.getForeground().getRGB()).as(name).isEqualTo(foreground);
                }
            }
            default -> {
                // Items without color API (columns, tool/menu/tab items, ToolTip): the tag is the contract.
            }
        }
    }

    /** Creates {@code proxy} with its simplest constructor and suitable parents. */
    private Widget create(Class<? extends Widget> proxy) throws Exception {
        Constructor<?> constructor = Arrays.stream(proxy.getConstructors())
                .min(Comparator.comparingInt(ProxyContractTest::cost))
                .orElseThrow();
        Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean styleSet = false;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) {
                args[i] = styleSet ? 0 : SWT.NONE;
                styleSet = true;
            } else {
                args[i] = parentFor(types[i]);
            }
        }
        try {
            return (Widget) constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SWTError error) {
                // e.g. Browser without a web engine, GLCanvas without OpenGL on a CI machine.
                Assumptions.abort(proxy.getSimpleName() + " unavailable here: " + error.getMessage());
            }
            throw e;
        }
    }

    /** Prefer (parent, style), then shorter, and constructors without exotic parameters. */
    private static int cost(Constructor<?> constructor) {
        int cost = constructor.getParameterCount() * 10;
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length == 2 && types[1] == int.class && Widget.class.isAssignableFrom(types[0])) {
            cost -= 15;
        }
        return cost;
    }

    private Object parentFor(Class<?> type) {
        if (type == Display.class) {
            return display;
        }
        if (type == GLData.class) {
            GLData data = new GLData();
            data.doubleBuffer = true;
            return data;
        }
        if (type.isInstance(shell)) {
            return shell;
        }
        return switch (type.getSimpleName()) {
            case "Canvas" -> new Canvas(shell, SWT.NONE);
            case "Table" -> new Table(shell, SWT.NONE);
            case "Tree" -> new Tree(shell, SWT.NONE);
            case "TreeItem" -> new com.zapthemes.swt.widgets.TreeItem(new Tree(shell, SWT.NONE), SWT.NONE);
            case "TabFolder" -> new TabFolder(shell, SWT.NONE);
            case "CTabFolder" -> new CTabFolder(shell, SWT.NONE);
            case "ToolBar" -> new ToolBar(shell, SWT.NONE);
            case "CoolBar" -> new CoolBar(shell, SWT.NONE);
            case "ExpandBar" -> new ExpandBar(shell, SWT.NONE);
            case "Menu" -> new Menu(shell, SWT.DROP_DOWN);
            case "MenuItem" -> new MenuItem(new Menu(shell, SWT.DROP_DOWN), SWT.CASCADE);
            default -> throw new IllegalArgumentException("No test parent for " + type.getName());
        };
    }

    private static java.util.List<String> signatures(Constructor<?>[] constructors) {
        return Arrays.stream(constructors).map(c -> Arrays.toString(c.getParameterTypes())).sorted().toList();
    }
}
