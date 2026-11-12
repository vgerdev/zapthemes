package com.zapthemes.widgets.titlebar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.CustomizationStore;

/** The quick access toolbar on the shared command model: items, the registry and the store. */
class CommandTitleBarItemTest {

    @TempDir
    Path dir;

    private Display display;
    private Shell shell;
    private CommandRegistry commands;
    private final List<String> ran = new ArrayList<>();

    @BeforeEach
    void setUp() {
        display = Display.getDefault();
    }

    @AfterEach
    void tearDown() {
        for (org.eclipse.swt.widgets.Shell s : display.getShells()) {
            s.dispose();
        }
    }

    /** A shell whose registry has the commands; the bar shows save, undo and autosave. */
    private TitleBar bar() {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
        shell = new Shell(display, SWT.NO_TRIM);
        commands = CommandRegistry.of(shell);
        command("save", "&Save").shortcut(SWT.MOD1 | 'S').description("Save this document.");
        command("undo", "&Undo");
        command("autosave", "AutoSave").style(Command.Style.TOGGLE);
        command("print", "&Print");
        command("bold", "&Bold").style(Command.Style.TOGGLE);
        command("paste", "&Paste").style(Command.Style.SPLIT).menu(CommandMenu.of("paste.text", "|", "paste.special"));
        command("paste.text", "Keep &Text Only");
        command("paste.special", "Paste &Special...");
        TitleBar bar = new TitleBar(shell, SWT.NONE);
        bar.addQuickAccess(commands.get("autosave"));
        bar.addQuickAccess(commands.get("save"));
        bar.addQuickAccess(commands.get("undo"));
        bar.setBounds(0, 0, 1000, bar.computeSize(1000, SWT.DEFAULT).y);
        return bar;
    }

    private Command command(String id, String label) {
        return commands.add(new Command(id, label).onRun(e -> ran.add(id + (e.arrow() ? " arrow" : ""))));
    }

    private static List<String> shown(TitleBar bar) {
        return bar.getQuickAccessItems().stream().filter(TitleBarItem::isShown).map(TitleBarItem::getId).toList();
    }

    @Test
    void itemsFollowTheirCommands() {
        TitleBar bar = bar();
        TitleBarItem save = bar.getItem("save");
        assertThat(save.getText()).isEqualTo("&Save");
        assertThat(save.getToolTipText()).startsWith("Save (").endsWith(")\nSave this document.");
        assertThat(CommandTitleBarItem.of(save).command()).isSameAs(commands.get("save"));

        commands.get("save").setEnabled(false);
        assertThat(save.getEnabled()).isFalse();
        commands.get("save").setEnabled(true);
        commands.get("save").label("Save &All");
        assertThat(save.getText()).isEqualTo("Save &All");
        commands.get("autosave").setChecked(true);
        assertThat(bar.getItem("autosave").getSelection()).isTrue();
        assertThat(bar.getItem("autosave").getStyle() & SWT.CHECK).isNotZero();
    }

    @Test
    void clickingRunsTheCommandAndTogglesFlipOnce() {
        TitleBar bar = bar();
        bar.getItem("save").activate(false);
        bar.getItem("autosave").activate(false);
        assertThat(ran).containsExactly("save", "autosave");
        assertThat(commands.get("autosave").isChecked()).isTrue();
        assertThat(bar.getItem("autosave").getSelection()).isTrue();

        commands.get("undo").setEnabled(false);
        bar.getItem("undo").activate(false);
        assertThat(ran).containsExactly("save", "autosave");
    }

    @Test
    void splitCommandsRunFromTheButtonAndOpenTheirMenuFromTheArrow() {
        TitleBar bar = bar();
        TitleBarItem paste = bar.addQuickAccess(commands.get("paste"));
        bar.setBounds(0, 0, 1000, bar.computeSize(1000, SWT.DEFAULT).y);
        assertThat(paste.getStyle() & SWT.DROP_DOWN).isNotZero();
        paste.activate(false);
        assertThat(ran).containsExactly("paste");

        // The menu, built from the command's ids in the shell's registry.
        Menu menu = CommandTitleBarItem.of(paste).createMenu();
        assertThat(menu.getItemCount()).isEqualTo(3);
        assertThat(menu.getItem(0).getText()).isEqualTo("Keep &Text Only");
        assertThat(menu.getItem(1).getStyle() & SWT.SEPARATOR).isNotZero();
        MenuItem special = menu.getItem(2);
        special.notifyListeners(SWT.Selection, new Event());
        assertThat(ran).containsExactly("paste", "paste.special");
        menu.dispose();

        // The arrow opens the menu instead of running the command (a popup factory shows nothing here).
        List<String> opened = new ArrayList<>();
        commands.get("paste").menu(new CommandMenu.Popup(control -> {
            opened.add("menu");
            return null;
        }));
        paste.activate(true);
        assertThat(opened).containsExactly("menu");
        assertThat(ran).containsExactly("paste", "paste.special");
    }

    @Test
    void theRibbonsHookAddsACommandAsAUserChoice() {
        TitleBar bar = bar();
        List<String> settings = new ArrayList<>();
        bar.addListener(SWT.Settings, e -> settings.add("changed"));
        bar.addToQuickAccess(commands.get("bold"));
        assertThat(shown(bar)).containsExactly("autosave", "save", "undo", "bold");
        assertThat(settings).hasSize(1);

        bar.getItem("save").setShown(false);
        bar.addToQuickAccess(commands.get("save"));
        assertThat(shown(bar)).containsExactly("autosave", "save", "undo", "bold");
        bar.addToQuickAccess(commands.get("save"));
        assertThat(bar.getQuickAccessItems()).hasSize(4);
    }

    @Test
    void theStoreKeepsTheToolbarNextToTheRibbonsSection() throws Exception {
        Path file = dir.resolve("customizations.json");
        Files.writeString(file, "{\"formatVersion\": 1, \"ribbon\": {\"collapsed\": true}}");
        TitleBar bar = bar();
        bar.setCustomizationStore(CustomizationStore.open(file));
        bar.addToQuickAccess(commands.get("print"));
        bar.getItem("undo").setShown(false);
        bar.setQuickAccessOrder(List.of("print", "save"));
        bar.setQuickAccessBelow(true);

        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertThat(json.getAsJsonObject("ribbon").get("collapsed").getAsBoolean()).as("the ribbon's section is kept").isTrue();
        JsonObject quick = json.getAsJsonObject(TitleBarCustomization.SECTION);
        assertThat(quick.get("commands").toString()).isEqualTo("[\"print\",\"save\",\"autosave\"]");
        assertThat(quick.get("belowRibbon").getAsBoolean()).isTrue();

        // A new window: the bar makes only its defaults; print comes back from the registry.
        TitleBar again = bar();
        again.setCustomizationStore(CustomizationStore.open(file));
        assertThat(shown(again)).containsExactly("print", "save", "autosave");
        assertThat(again.isQuickAccessBelow()).isTrue();
        assertThat(CommandTitleBarItem.of(again.getItem("print")).command()).isSameAs(commands.get("print"));
    }

    @Test
    void unknownIdsAndSeparatorsInTheStoreAreIgnored() {
        TitleBar bar = bar();
        CustomizationStore store = CustomizationStore.inMemory();
        store.put(TitleBarCustomization.SECTION, JsonParser.parseString(
                "{\"commands\": [\"undo\", \"|\", \"gone\", \"save\", 7], \"belowRibbon\": \"yes\"}"));
        bar.setCustomizationStore(store);
        // autosave isn't in the store, so it keeps the app's default (shown), after the stored ones.
        assertThat(shown(bar)).containsExactly("undo", "save", "autosave");
        assertThat(bar.isQuickAccessBelow()).isFalse();
        assertThat(bar.getItem("gone")).isNull();
    }

    @Test
    void storedCommandsRegisteredLaterComeBackInTheirPlace() throws Exception {
        // As in the title bar demo: the bar reads the store before the ribbon registers File > Options.
        Path file = dir.resolve("customizations.json");
        TitleBar bar = bar();
        bar.setCustomizationStore(CustomizationStore.open(file));
        Command options = new Command("file.options", "Options");
        CommandRegistry.of(shell).add(options);
        bar.addToQuickAccess(options);
        bar.setQuickAccessOrder(List.of("autosave", "save", "file.options", "undo"));
        assertThat(shown(bar)).containsExactly("autosave", "save", "file.options", "undo");

        // A new window: the store is read first, then the command is registered.
        shell.dispose();
        TitleBar again = bar();
        again.setCustomizationStore(CustomizationStore.open(file));
        assertThat(again.getItem("file.options")).isNull();
        CommandRegistry.of(shell).add(new Command("file.options", "Options"));
        assertThat(shown(again)).containsExactly("autosave", "save", "file.options", "undo");
        assertThat(JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                .getAsJsonObject(TitleBarCustomization.SECTION).get("commands").toString())
                .as("nothing was saved without it").contains("file.options");

        // Once users change the toolbar first, a command still missing is no longer waited for.
        TitleBar third = bar();
        third.setCustomizationStore(CustomizationStore.inMemory());
        third.setCustomization(new TitleBarCustomization(List.of("file.options", "save"), java.util.Set.of(), false));
        CustomizationStore store = CustomizationStore.inMemory();
        store.put(TitleBarCustomization.SECTION, JsonParser.parseString("{\"commands\": [\"file.options\", \"save\"]}"));
        third.setCustomizationStore(store);
        third.getItem("undo").setShown(false);
        CommandRegistry.of(shell).add(new Command("file.options", "Options"));
        assertThat(third.getItem("file.options")).isNull();
    }

    @Test
    void moreCommandsOffersTheShellsOtherCommands() {
        TitleBar bar = bar();
        QuickAccessDialog.Model model = new QuickAccessDialog.Model(bar, bar.getCustomization());
        assertThat(model.available()).containsExactly("print", "bold", "paste", "paste.text", "paste.special");
        assertThat(model.names.get("print")).isEqualTo("Print");
        model.add("print", -1);
        bar.setCustomization(model.result());
        assertThat(shown(bar)).containsExactly("autosave", "save", "undo", "print");
        bar.getItem("print").activate(false);
        assertThat(ran).containsExactly("print");
    }

    @Test
    void disposingTheItemStopsListening() {
        TitleBar bar = bar();
        bar.getItem("save").dispose();
        commands.get("save").setEnabled(false);
        commands.get("save").label("Changed");
        assertThat(bar.getItem("save")).isNull();
    }

    private void settle() {
        for (int i = 0; i < 10; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }
}
