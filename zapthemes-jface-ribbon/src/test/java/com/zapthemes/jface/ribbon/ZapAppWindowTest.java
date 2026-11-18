package com.zapthemes.jface.ribbon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.command.CommandKeyBindings;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonTab;
import com.zapthemes.widgets.titlebar.TitleBar;

class ZapAppWindowTest {

    private static Display display;
    private ZapAppWindow window;

    @BeforeAll
    static void theme() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager.get(display).setTheme(registry.resolve("zap-light"));
    }

    @AfterEach
    void tearDown() {
        if (window != null && window.getShell() != null) {
            window.close();
        }
    }

    private static void settle() {
        while (display.readAndDispatch()) {
            // Settle.
        }
    }

    private static Action action(String id, String text, List<String> runs) {
        Action action = new Action(text) {
            @Override
            public void run() {
                runs.add(id);
            }
        };
        action.setId(id);
        return action;
    }

    /** An application window as apps write them, with the base class swapped. */
    static class Editor extends ZapAppWindow {
        final List<String> runs = new ArrayList<>();
        final Action save = action("file.save", "&Save@Ctrl+S", runs);
        final Action find = action("edit.find", "&Find...@Ctrl+F", runs);
        Text content;
        Path ribbonPreferences;
        Path titleBarPreferences;

        Editor(boolean coolBar) {
            super(null);
            addMenuBar();
            if (coolBar) {
                addCoolBar(SWT.FLAT);
            } else {
                addToolBar(SWT.FLAT | SWT.RIGHT);
            }
            addStatusLine();
        }

        @Override
        protected MenuManager createMenuManager() {
            MenuManager bar = new MenuManager();
            MenuManager file = new MenuManager("&File", "file");
            file.add(save);
            file.add(new Separator());
            file.add(new Action("E&xit") {
            });
            MenuManager edit = new MenuManager("&Edit", "edit");
            edit.add(find);
            bar.add(file);
            bar.add(edit);
            return bar;
        }

        @Override
        protected ToolBarManager createToolBarManager(int style) {
            ToolBarManager toolBar = new ToolBarManager(style);
            toolBar.add(save);
            return toolBar;
        }

        @Override
        protected void createRibbon(Ribbon ribbon) {
            RibbonTab home = ribbon.addTab("home", "Home");
            RibbonContributions.add(home.addGroup("home.file", "File"), save).large();
            RibbonContributions.add(home.addGroup("home.edit", "Editing"), find);
            ribbon.setFileTab("File", backstage -> backstage.addCommand("file.save"));
        }

        @Override
        protected Control createContents(Composite parent) {
            content = new Text(parent, SWT.MULTI);
            return content;
        }

        @Override
        protected Path getRibbonPreferences() {
            return ribbonPreferences;
        }

        @Override
        protected Path getTitleBarPreferences() {
            return titleBarPreferences;
        }

        org.eclipse.jface.action.StatusLineManager statusLine() {
            return getStatusLineManager();
        }
    }

    private Editor open(Editor editor) {
        window = editor;
        editor.setBlockOnOpen(false);
        editor.open();
        editor.getShell().setSize(900, 600);
        settle();
        return editor;
    }

    @Test
    void theRibbonReplacesTheMenuBarAndTheToolBarUnderTheTitleBar() {
        Editor editor = open(new Editor(false));
        Shell shell = editor.getShell();
        assertThat(shell.getMenuBar()).isNull();
        assertThat(shell.getChildren()).noneMatch(c -> c instanceof ToolBar || c instanceof CoolBar);

        TitleBar titleBar = editor.getTitleBar();
        Ribbon ribbon = editor.getRibbon();
        assertThat(titleBar).isNotNull();
        assertThat(ribbon).isNotNull();
        assertThat(titleBar.getTitleMenu()).isNull();
        Rectangle area = shell.getClientArea();
        assertThat(titleBar.getBounds().y).isEqualTo(area.y);
        assertThat(ribbon.getBounds().y).isEqualTo(titleBar.getBounds().y + titleBar.getBounds().height);
        assertThat(ribbon.getBounds().width).isEqualTo(area.width);
        Control status = editor.statusLine().getControl();
        assertThat(editor.content.getBounds().y).isGreaterThanOrEqualTo(ribbon.getBounds().y + ribbon.getBounds().height);
        assertThat(status.getBounds().y).isGreaterThanOrEqualTo(editor.content.getBounds().y + editor.content.getBounds().height);
        assertThat(status.getBounds().y + status.getBounds().height).isEqualTo(area.y + area.height);

        editor.setStatus("Ready");
        assertThat(editor.statusLine().getControl().isVisible()).isTrue();
    }

    @Test
    void theManagersAreKeptForTheAppsCode() {
        Editor editor = open(new Editor(false));
        MenuManager menus = editor.getMenuBarManager();
        assertThat(menus).isNotNull();
        assertThat(menus.find("file")).isInstanceOf(MenuManager.class);
        assertThat(menus.getMenu()).isNull();
        assertThat(editor.getToolBarManager()).isNotNull();
        assertThat(editor.getToolBarManager().getItems()).hasSize(1);
        assertThat(editor.getToolBarManager().getControl()).isNull();
        assertThat(editor.getCoolBarManager()).isNull();
    }

    @Test
    void aCoolBarGetsNoControlEither() {
        Editor editor = open(new Editor(true));
        assertThat(editor.getCoolBarManager()).isNotNull();
        assertThat(editor.getCoolBarManager().getControl()).isNull();
        assertThat(editor.getToolBarManager()).isNull();
        assertThat(editor.getShell().getChildren()).noneMatch(c -> c instanceof ToolBar || c instanceof CoolBar);
        assertThat(editor.getRibbon().getBounds().height).isPositive();
    }

    @Test
    void theBackstageLeavesTheTitleBarShowing() {
        Editor editor = open(new Editor(false));
        TitleBar titleBar = editor.getTitleBar();
        Ribbon ribbon = editor.getRibbon();
        ribbon.openBackstage();
        settle();
        Rectangle area = editor.getShell().getClientArea();
        Rectangle bar = titleBar.getBounds();
        assertThat(titleBar.isVisible()).isTrue();
        assertThat(ribbon.isVisible()).isFalse();
        assertThat(editor.content.isVisible()).isFalse();
        assertThat(ribbon.getBackstage().getBounds())
                .isEqualTo(new Rectangle(area.x, bar.y + bar.height, area.width, area.height - bar.height));
        ribbon.getBackstage().close();
        settle();
        assertThat(ribbon.isVisible()).isTrue();
        assertThat(editor.content.isVisible()).isTrue();
        assertThat(ribbon.getBounds().y).isEqualTo(bar.y + bar.height);
    }

    private static Event key(Control widget, int keyCode, int stateMask) {
        Event event = new Event();
        event.type = SWT.KeyDown;
        event.widget = widget;
        event.keyCode = keyCode;
        event.stateMask = stateMask;
        event.doit = true;
        return event;
    }

    @Test
    void acceleratorsRunTheActionsWithoutAMenuBar() {
        Editor editor = open(new Editor(false));
        assertThat(editor.getRibbon().commands().get("file.save").label()).isEqualTo("&Save");
        editor.content.notifyListeners(SWT.KeyDown, key(editor.content, 's', SWT.CTRL));
        editor.content.notifyListeners(SWT.KeyDown, key(editor.content, 'f', SWT.CTRL));
        assertThat(editor.runs).containsExactly("file.save", "edit.find");
    }

    @Test
    void runningAnOperationTurnsTheRibbonAndTheShortcutsOff() throws Exception {
        Editor editor = open(new Editor(false));
        Ribbon ribbon = editor.getRibbon();
        CommandKeyBindings bindings = CommandKeyBindings.install(editor.getShell(), ribbon.commands());
        boolean[] during = new boolean[2];
        editor.run(false, false, monitor -> {
            during[0] = ribbon.getEnabled();
            during[1] = bindings.isEnabled();
            editor.content.notifyListeners(SWT.KeyDown, key(editor.content, 's', SWT.CTRL));
        });
        assertThat(during).containsExactly(false, false);
        assertThat(editor.runs).isEmpty();
        assertThat(ribbon.getEnabled()).isTrue();
        assertThat(bindings.isEnabled()).isTrue();
    }

    @Test
    void theRibbonAndTheTitleBarShareOnePreferencesFile(@TempDir Path dir) {
        Editor editor = new Editor(false);
        editor.ribbonPreferences = dir.resolve("ui.json");
        editor.titleBarPreferences = dir.resolve("ui.json");
        open(editor);
        assertThat(editor.getRibbon().customization().store()).isSameAs(editor.getTitleBar().getCustomizationStore());
        assertThat(editor.getTitleBar().getPreferences()).isNull();
        editor.close();

        Editor ribbonOnly = new Editor(false);
        ribbonOnly.ribbonPreferences = dir.resolve("ribbon.json");
        open(ribbonOnly);
        assertThat(ribbonOnly.getRibbon().customization().store().file()).isEqualTo(dir.resolve("ribbon.json"));
        assertThat(ribbonOnly.getTitleBar().getCustomizationStore()).isSameAs(ribbonOnly.getRibbon().customization().store());
        ribbonOnly.close();

        Editor separate = new Editor(false);
        separate.ribbonPreferences = dir.resolve("ribbon.json");
        separate.titleBarPreferences = dir.resolve("toolbar.properties");
        open(separate);
        assertThat(separate.getTitleBar().getPreferences()).isEqualTo(dir.resolve("toolbar.properties"));
        assertThat(separate.getTitleBar().getCustomizationStore()).isNull();
    }

    @Test
    void commandsAreRegisteredOnlyOnceTheRibbonExists() {
        ZapAppWindow bare = new ZapAppWindow(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bare.command(new Action("x") {
        })).isInstanceOf(IllegalStateException.class);
    }
}
