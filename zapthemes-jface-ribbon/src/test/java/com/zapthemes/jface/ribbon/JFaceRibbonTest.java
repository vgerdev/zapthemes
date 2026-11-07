package com.zapthemes.jface.ribbon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonItem;
import com.zapthemes.widgets.ribbon.RibbonTab;

class JFaceRibbonTest {

    private static Display display;
    private Shell shell;
    private Window window;

    @BeforeAll
    static void theme() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager.get(display).setTheme(registry.resolve("zap-light"));
    }

    @AfterEach
    void tearDown() {
        if (window != null) {
            window.close();
        }
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }

    private static ImageDescriptor square(int size) {
        ImageData data = new ImageData(size, size, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        return ImageDescriptor.createFromImageDataProvider(zoom -> zoom == 100 ? data : null);
    }

    @Test
    void actionsBecomeCommandsThatFollowThem() {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        CommandRegistry registry = CommandRegistry.of(shell);
        List<String> runs = new ArrayList<>();
        Action bold = new Action("&Bold\tCtrl+B", IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                runs.add("bold " + isChecked());
            }
        };
        bold.setId("home.bold");
        bold.setToolTipText("Make your text bold.");
        bold.setAccelerator(SWT.MOD1 | 'B');
        bold.setImageDescriptor(square(16));
        ActionCommands.setLargeImage(bold, square(32));

        Command command = ActionCommands.register(registry, bold, shell);
        assertThat(command.id()).isEqualTo("home.bold");
        assertThat(command.label()).isEqualTo("&Bold");
        assertThat(command.style()).isEqualTo(Command.Style.TOGGLE);
        assertThat(command.description()).isEqualTo("Make your text bold.");
        assertThat(command.shortcut()).isEqualTo(SWT.MOD1 | 'B');
        assertThat(command.images().image(16).getBounds().width).isEqualTo(16);
        assertThat(command.images().image(32).getBounds().width).isEqualTo(32);
        assertThat(ActionCommands.register(registry, bold, shell)).isSameAs(command);

        bold.setEnabled(false);
        assertThat(command.isEnabled()).isFalse();
        bold.setEnabled(true);
        bold.setText("Bold text");
        assertThat(command.label()).isEqualTo("Bold text");
        bold.setChecked(true);
        assertThat(command.isChecked()).isTrue();

        command.run(new CommandEvent(command, shell));
        assertThat(bold.isChecked()).isFalse();
        assertThat(runs).containsExactly("bold false");
    }

    @Test
    void actionsNeedAnId() {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        Action anonymous = new Action("Nameless") {
        };
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ActionCommands.register(CommandRegistry.of(shell), anonymous, shell))
                .isInstanceOf(IllegalArgumentException.class);
        anonymous.setActionDefinitionId("org.example.nameless");
        assertThat(ActionCommands.register(CommandRegistry.of(shell), anonymous, shell).id()).isEqualTo("org.example.nameless");
    }

    @Test
    void dropDownActionsAreSplitButtons() {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        Action paste = new Action("Paste", IAction.AS_DROP_DOWN_MENU) {
        };
        paste.setId("home.paste");
        paste.setMenuCreator(new org.eclipse.jface.action.IMenuCreator() {
            @Override
            public void dispose() {
            }

            @Override
            public org.eclipse.swt.widgets.Menu getMenu(Control parent) {
                return new com.zapthemes.swt.widgets.Menu(parent);
            }

            @Override
            public org.eclipse.swt.widgets.Menu getMenu(org.eclipse.swt.widgets.Menu parent) {
                return null;
            }
        });
        Command command = ActionCommands.register(CommandRegistry.of(shell), paste, shell);
        assertThat(command.style()).isEqualTo(Command.Style.SPLIT);
        assertThat(command.menu()).isInstanceOf(CommandMenu.Popup.class);
        assertThat(((CommandMenu.Popup) command.menu()).factory().apply(shell)).isNotNull();
    }

    @Test
    void contributionItemsFillGroupsAndRows() {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        Ribbon ribbon = new Ribbon(shell, SWT.NONE);
        RibbonGroup group = ribbon.addTab("home", "Home").addGroup("home.edit", "Edit");
        Action cut = action("home.cut", "Cut");
        Action copy = action("home.copy", "Copy");
        MenuManager more = new MenuManager("More", "home.more");
        more.add(action("home.select", "Select All"));
        RibbonContributions.fill(group, new ActionContributionItem(cut), new Separator(), more);
        RibbonContributions.fill(group.row(), new ActionContributionItem(copy), new Separator(), new ActionContributionItem(cut));
        assertThat(group.items()).hasSize(5).extracting(RibbonItem::id).filteredOn(id -> !id.startsWith("separator."))
                .containsExactly("home.cut", "home.more", "home.copy", "home.cut");
        Command menu = ribbon.commands().get("home.more");
        assertThat(menu.style()).isEqualTo(Command.Style.DROP_DOWN);
        assertThat(((CommandMenu.Popup) menu.menu()).factory().apply(ribbon).getItemCount()).isEqualTo(1);
    }

    private static Action action(String id, String text) {
        Action action = new Action(text) {
        };
        action.setId(id);
        return action;
    }

    /** A ribbon window as an app writes one. */
    static final class Window extends RibbonApplicationWindow {
        final Action save = action("file.save", "&Save");
        Control content;

        Window() {
            super(null);
            addRibbon();
            addStatusLine();
        }

        @Override
        protected void createRibbon(Ribbon ribbon) {
            RibbonTab home = ribbon.addTab("home", "Home");
            RibbonContributions.add(home.addGroup("home.file", "File"), save).large();
            ribbon.setFileTab("File", backstage -> backstage.addCommand("file.save"));
        }

        @Override
        protected Control createContents(Composite parent) {
            Label label = new Label(parent, SWT.NONE);
            label.setText("Content");
            content = label;
            return label;
        }
    }

    @Test
    void ribbonWindowPutsTheRibbonInTheToolBarsPlace() {
        window = new Window();
        window.setBlockOnOpen(false);
        window.open();
        while (display.readAndDispatch()) {
            // Settle.
        }
        Shell windowShell = window.getShell();
        Ribbon ribbon = window.getRibbon();
        assertThat(ribbon).isNotNull();
        assertThat(windowShell.getMenuBar()).isNull();
        assertThat(ribbon.getBounds().y).isZero();
        assertThat(ribbon.getBounds().width).isEqualTo(windowShell.getClientArea().width);
        Control content = window.content;
        assertThat(content.getBounds().y).isGreaterThanOrEqualTo(ribbon.getBounds().height);
        assertThat(ribbon.commands().get("file.save")).isNotNull();

        ribbon.openBackstage();
        while (display.readAndDispatch()) {
            // Settle.
        }
        assertThat(ribbon.getBackstage().getBounds()).isEqualTo(windowShell.getClientArea());
        assertThat(content.isVisible()).isFalse();
        ribbon.getBackstage().close();
        while (display.readAndDispatch()) {
            // Settle.
        }
        assertThat(content.isVisible()).isTrue();
        assertThat(content.getBounds().y).isGreaterThanOrEqualTo(ribbon.getBounds().height);
    }
}
