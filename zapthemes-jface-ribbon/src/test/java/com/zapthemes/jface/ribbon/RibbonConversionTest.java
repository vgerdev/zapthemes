package com.zapthemes.jface.ribbon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.themes.BundledThemes;
import com.zapthemes.widgets.FlatButton;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.ribbon.Backstage;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonItem;
import com.zapthemes.widgets.ribbon.RibbonTab;
import com.zapthemes.widgets.ribbon.Size;

/** A window written for {@code ApplicationWindow}, ZapNotes-style (actions without ids), on a ZapAppWindow. */
class RibbonConversionTest {

    private static Display display;
    private Notes window;

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

    private static ImageDescriptor square() {
        ImageData data = new ImageData(16, 16, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        return ImageDescriptor.createFromImageDataProvider(zoom -> zoom == 100 ? data : null);
    }

    static class Notes extends ZapAppWindow {
        final List<String> runs = new ArrayList<>();
        final IAction newNote = action("&New Note...@Ctrl+N");
        final IAction save = action("&Save@Ctrl+S");
        final IAction saveAll = action("Save A&ll@Ctrl+Shift+S");
        final IAction rename = action("&Rename...@F2");
        final IAction find = action("&Find...@Ctrl+F");
        final IAction switchTheme = action("Switch &Theme");
        final MenuManager edit = new MenuManager("&Edit");
        Text content;
        boolean withMenus = true;

        Notes() {
            this(true);
        }

        Notes(boolean withMenus) {
            super(null);
            this.withMenus = withMenus;
            switchTheme.setImageDescriptor(square());
            if (withMenus) {
                addMenuBar();
                addToolBar(SWT.FLAT);
            }
            addStatusLine();
        }

        IAction action(String text) {
            return new Action(text) {
                @Override
                public void run() {
                    runs.add(RibbonConversion.plain(text));
                }
            };
        }

        @Override
        protected MenuManager createMenuManager() {
            MenuManager bar = new MenuManager();
            MenuManager file = new MenuManager("&File");
            file.add(newNote);
            file.add(new Separator());
            file.add(save);
            file.add(saveAll);
            file.add(action("&Export..."));
            MenuManager recent = new MenuManager("&Recent");
            recent.add(action("notes.txt"));
            recent.add(action("todo.txt"));
            file.add(recent);
            file.add(new Separator());
            file.add(action("&Close Window"));
            file.add(action("E&xit"));

            edit.add(rename);
            edit.add(action("&Delete@Delete"));
            edit.add(new Separator("find"));
            edit.add(find);

            MenuManager windowMenu = new MenuManager("&Window");
            windowMenu.add(action("New &Window"));
            MenuManager theme = new MenuManager("&Theme");
            theme.setRemoveAllWhenShown(true);
            theme.addMenuListener(m -> {
                m.add(action("Light"));
                m.add(action("Dark"));
            });
            windowMenu.add(theme);
            windowMenu.add(new Separator());
            windowMenu.add(action("&Preferences..."));

            MenuManager view = new MenuManager("&View");
            view.add(new ContributionItem("openViews") {
                @Override
                public void fill(Menu menu, int index) {
                    new com.zapthemes.swt.widgets.MenuItem(menu, SWT.PUSH, index).setText("Navigator");
                    new com.zapthemes.swt.widgets.MenuItem(menu, SWT.PUSH, index + 1).setText("Tasks");
                }
            });

            MenuManager help = new MenuManager("&Help");
            help.add(action("&About ZapNotes"));

            bar.add(file);
            bar.add(edit);
            bar.add(windowMenu);
            bar.add(view);
            bar.add(help);
            return bar;
        }

        @Override
        protected ToolBarManager createToolBarManager(int style) {
            ToolBarManager toolBar = new ToolBarManager(style);
            toolBar.add(newNote);
            toolBar.add(save);
            toolBar.add(new Separator());
            toolBar.add(find);
            toolBar.add(new Separator());
            toolBar.add(new ControlContribution("search") {
                @Override
                protected Control createControl(Composite parent) {
                    return new Text(parent, SWT.SEARCH);
                }
            });
            toolBar.add(switchTheme);
            return toolBar;
        }

        @Override
        protected Control createContents(Composite parent) {
            content = new Text(parent, SWT.MULTI);
            return content;
        }
    }

    private Notes open(Notes notes) {
        window = notes;
        notes.setBlockOnOpen(false);
        notes.open();
        notes.getShell().setSize(1200, 700);
        settle();
        return notes;
    }

    private static List<String> ids(RibbonGroup group) {
        return group.items().stream().map(RibbonItem::id).toList();
    }

    @Test
    void menusBecomeTabsAfterAHomeTabFromTheToolBar() {
        Ribbon ribbon = open(new Notes()).getRibbon();
        assertThat(ribbon.tabs()).extracting(RibbonTab::id)
                .containsExactly("auto.home", "auto.edit", "auto.window", "auto.view", "auto.help");
        assertThat(ribbon.tabs()).extracting(RibbonTab::label).containsExactly("Home", "Edit", "Window", "View", "Help");
        assertThat(ribbon.tabs()).extracting(RibbonTab::keyTip).containsExactly("H", "E", "W", "V", null);
        assertThat(ribbon.getSelection().id()).isEqualTo("auto.home");
    }

    @Test
    void theToolBarsSectionsAreGroupsOfLargeButtons() {
        Ribbon ribbon = open(new Notes()).getRibbon();
        List<RibbonGroup> groups = ribbon.tab("auto.home").groups();
        assertThat(groups).extracting(RibbonGroup::label).containsExactly("Commands 1", "Commands 2", "Commands 3");
        assertThat(ids(groups.get(0))).containsExactly("auto.file.newNote", "auto.file.save");
        assertThat(ids(groups.get(2))).as("the control contribution is skipped").containsExactly("auto.home.switchTheme");
        assertThat(groups.get(2).items().getFirst().maxSize()).as("an image makes it large").isEqualTo(Size.LARGE);
        assertThat(groups.get(0).items().getFirst().maxSize()).as("no image: not large").isNotEqualTo(Size.LARGE);
    }

    @Test
    void menuSectionsAreGroupsAndNamedSeparatorsNameThem() {
        Ribbon ribbon = open(new Notes()).getRibbon();
        List<RibbonGroup> groups = ribbon.tab("auto.edit").groups();
        assertThat(groups).extracting(RibbonGroup::id).containsExactly("auto.edit.1", "auto.edit.find");
        assertThat(groups).extracting(RibbonGroup::label).containsExactly("Rename", "Find");
        assertThat(ids(groups.get(0))).containsExactly("auto.edit.rename", "auto.edit.delete");
        assertThat(groups.get(0).items()).extracting(RibbonItem::keyTip).containsExactly("R", "D");
        assertThat(ribbon.commands().get("auto.edit.rename").label()).isEqualTo("&Rename...");
        assertThat(ribbon.commands().get("auto.edit.rename").shortcut()).isEqualTo(SWT.F2);
    }

    @Test
    void submenusAndDynamicItemsAreDropDowns() {
        Ribbon ribbon = open(new Notes()).getRibbon();
        Command theme = ribbon.commands().get("auto.window.theme");
        assertThat(theme.style()).isEqualTo(Command.Style.DROP_DOWN);
        Menu themes = ((CommandMenu.Popup) theme.menu()).factory().apply(ribbon);
        themes.notifyListeners(SWT.Show, new Event());
        assertThat(themes.getItems()).extracting(MenuItem::getText).containsExactly("Light", "Dark");

        Command views = ribbon.commands().get("auto.view.openViews");
        assertThat(views.label()).isEqualTo("Open Views");
        Menu open = ((CommandMenu.Popup) views.menu()).factory().apply(ribbon);
        assertThat(open.getItems()).extracting(MenuItem::getText).containsExactly("Navigator", "Tasks");
    }

    @Test
    void theFileMenuIsTheBackstageWithAboutAndExitLast() {
        Notes notes = open(new Notes());
        Backstage backstage = notes.getRibbon().getBackstage();
        assertThat(backstage.entryIds()).containsExactly("auto.file.newNote", "|", "auto.file.save", "auto.file.saveAll",
                "auto.file.export", "auto.file.recent", "|", "auto.help.aboutZapNotes", "auto.file.closeWindow", "auto.file.exit");

        notes.getRibbon().openBackstage();
        backstage.select("auto.file.recent");
        settle();
        List<FlatButton> buttons = new ArrayList<>();
        for (Control child : backstage.page("auto.file.recent").getChildren()) {
            if (child instanceof FlatButton button) {
                buttons.add(button);
            }
        }
        assertThat(buttons).extracting(FlatButton::getText).containsExactly("notes.txt", "todo.txt");
        buttons.get(1).notifyListeners(SWT.Selection, new Event());
        assertThat(notes.runs).containsExactly("todo.txt");
        backstage.close();
    }

    @Test
    void everyMenuAcceleratorRunsEvenWhenTheRibbonDoesNotShowTheAction() {
        Notes notes = open(new Notes());
        Event key = new Event();
        key.type = SWT.KeyDown;
        key.widget = notes.content;
        key.keyCode = 's';
        key.stateMask = SWT.CTRL | SWT.SHIFT;
        notes.content.notifyListeners(SWT.KeyDown, key);
        assertThat(notes.runs).containsExactly("Save All");
    }

    @Test
    void createRibbonRunsAfterTheConversionAndRefreshKeepsItsWork() {
        Notes notes = new Notes() {
            @Override
            protected void createRibbon(Ribbon ribbon) {
                ribbon.removeTab(ribbon.tab("auto.view"));
                ribbon.tab("auto.edit").addGroup("mine", "Mine").add(command(find));
            }
        };
        Ribbon ribbon = open(notes).getRibbon();
        assertThat(ribbon.tab("auto.view")).isNull();
        assertThat(ribbon.tab("auto.edit").groups()).extracting(RibbonGroup::id).containsExactly("auto.edit.1", "auto.edit.find", "mine");

        notes.edit.add(notes.action("&Copy@Ctrl+C"));
        notes.refreshRibbon();
        assertThat(ribbon.tab("auto.edit").groups()).extracting(RibbonGroup::id).containsExactly("auto.edit.1", "auto.edit.find", "mine");
        assertThat(ids(ribbon.tab("auto.edit").group("auto.edit.find"))).containsExactly("auto.edit.find", "auto.edit.copy");
        assertThat(ribbon.tabs()).extracting(RibbonTab::id).as("refresh converts the View menu again")
                .containsExactly("auto.home", "auto.edit", "auto.window", "auto.view", "auto.help");
        assertThat(ribbon.commands().all()).extracting(Command::id).doesNotContain("auto.edit.rename2", "auto.view.openViews2");
        assertThat(ribbon.getBackstage().entryIds()).contains("auto.file.save");
    }

    @Test
    void withoutAFileMenuTheBackstageClosesTheWindow() {
        Notes notes = open(new Notes(false));
        Ribbon ribbon = notes.getRibbon();
        assertThat(ribbon.tabs()).isEmpty();
        assertThat(ribbon.getBackstage().entryIds()).containsExactly(ZapAppWindow.CLOSE_COMMAND);
        ribbon.commands().get(ZapAppWindow.CLOSE_COMMAND).run(new com.zapthemes.widgets.command.CommandEvent(
                ribbon.commands().get(ZapAppWindow.CLOSE_COMMAND), ribbon));
        assertThat(notes.getShell()).isNull();
    }

    @Test
    void aHandWrittenBackstageGetsNoCloseEntry() {
        Notes notes = new Notes() {
            @Override
            protected boolean convertMenus() {
                return false;
            }

            @Override
            protected void createBackstage(Backstage backstage) {
                backstage.addCommand(command(save).id());
            }
        };
        notes.save.setId("file.save");
        assertThat(open(notes).getRibbon().getBackstage().entryIds()).containsExactly("file.save");
        assertThat(notes.getRibbon().tabs()).as("nothing converted").isEmpty();
    }

    @Test
    void noFileTabWhenTheLabelIsNull() {
        Notes notes = new Notes() {
            @Override
            protected String getFileTabLabel() {
                return null;
            }
        };
        assertThat(open(notes).getRibbon().getBackstage()).isNull();
    }

    @Test
    void namesAndIds() {
        assertThat(RibbonConversion.plain("Save A&ll@Ctrl+Shift+S")).isEqualTo("Save All");
        assertThat(RibbonConversion.plain("&Find...\tCtrl+F")).isEqualTo("Find");
        assertThat(RibbonConversion.slug("Save All")).isEqualTo("saveAll");
        assertThat(RibbonConversion.slug("notes.txt")).isEqualTo("notesTxt");
        assertThat(RibbonConversion.humanize("fontStyles")).isEqualTo("Font Styles");
        assertThat(RibbonConversion.humanize("open-views")).isEqualTo("Open Views");
        assertThat(RibbonConversion.mnemonic("Save A&ll")).isEqualTo('l');
        assertThat(RibbonConversion.mnemonic("R&&D")).isEqualTo((char) 0);
    }
}
