package com.zapthemes.widgets.titlebar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.titlebar.TitleBar.Part;

class TitleBarCustomizationTest {

    private static final List<String> IDS = List.of("autosave", "save", "undo", "redo", "print");

    @TempDir
    Path dir;

    private Display display;
    private Shell shell;

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

    /** A bar as an app makes it: every command, Print hidden by default. */
    private TitleBar bar(List<String> ids) {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
        shell = new Shell(display, SWT.NO_TRIM);
        TitleBar bar = new TitleBar(shell, SWT.NONE);
        for (String id : ids) {
            TitleBarItem item = new TitleBarItem(bar, id.equals("autosave") ? SWT.CHECK : SWT.PUSH);
            item.setId(id);
            item.setText(Character.toUpperCase(id.charAt(0)) + id.substring(1));
            item.setShown(!id.equals("print"));
        }
        bar.setBounds(0, 0, 1000, bar.computeSize(1000, SWT.DEFAULT).y);
        return bar;
    }

    private static List<String> shown(TitleBar bar) {
        return bar.getQuickAccessItems().stream().filter(TitleBarItem::isShown).map(TitleBarItem::getId).toList();
    }

    @Test
    void customizationRoundTripsThroughThePreferencesFile() throws IOException {
        Path file = dir.resolve("prefs/titlebar.properties");
        TitleBar bar = bar(IDS);
        bar.setPreferences(file);
        assertThat(Files.exists(file)).as("nothing customized yet").isFalse();

        bar.getItem("save").setShown(false);
        bar.getItem("print").setShown(true);
        bar.setQuickAccessOrder(List.of("redo", "undo"));
        bar.setQuickAccessBelow(true);
        assertThat(Files.readString(file)).contains("quickAccess.shown=redo,undo,autosave,print", "quickAccess.below=true");

        TitleBar again = bar(IDS);
        again.setPreferences(file);
        assertThat(shown(again)).containsExactly("redo", "undo", "autosave", "print");
        assertThat(again.isQuickAccessBelow()).isTrue();
        assertThat(again.getItem("save").isShown()).isFalse();
    }

    @Test
    void newCommandsKeepTheirDefaultsAndUnknownIdsAreIgnored() throws IOException {
        Path file = dir.resolve("titlebar.properties");
        Files.writeString(file, """
                quickAccess.shown=redo,gone,save
                quickAccess.known=autosave,save,undo,redo,gone
                quickAccess.below=false
                """);
        TitleBar bar = bar(List.of("autosave", "save", "undo", "redo", "share"));
        bar.setPreferences(file);
        // share is new since the file was saved: shown, as the app made it; undo was hidden by the user.
        assertThat(shown(bar)).containsExactly("redo", "save", "share");
        assertThat(bar.getItem("undo").isShown()).isFalse();
    }

    @Test
    void otherPropertiesInTheFileAreKept() throws IOException {
        Path file = dir.resolve("app.properties");
        Files.writeString(file, "window.width=900\n");
        TitleBar bar = bar(IDS);
        bar.setPreferences(file);
        bar.getItem("redo").setShown(false);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        assertThat(properties.getProperty("window.width")).isEqualTo("900");
        assertThat(properties.getProperty(TitleBarCustomization.SHOWN)).isEqualTo("autosave,save,undo");
    }

    @Test
    void propertiesRoundTrip() {
        TitleBarCustomization state = new TitleBarCustomization(List.of("b", "a", "b"), Set.of("a", "b", "c"), true);
        Properties properties = new Properties();
        state.store(properties);
        assertThat(TitleBarCustomization.load(properties)).isEqualTo(new TitleBarCustomization(List.of("b", "a"), Set.of("a", "b", "c"), true));
        assertThat(TitleBarCustomization.load(new Properties()).shown()).isEmpty();
    }

    @Test
    void theAppCanKeepTheStateItselfThroughSettingsEvents() {
        TitleBar bar = bar(IDS);
        int[] events = { 0 };
        bar.addListener(SWT.Settings, e -> events[0]++);
        bar.getItem("undo").setShown(false);
        assertThat(events[0]).isEqualTo(1);
        TitleBarCustomization state = bar.getCustomization();

        TitleBar other = bar(IDS);
        other.setCustomization(state);
        assertThat(shown(other)).isEqualTo(state.shown()).containsExactly("autosave", "save", "redo");
    }

    @Test
    void theCustomizeMenuOffersMoreCommandsAndThePosition() {
        TitleBar bar = bar(IDS);
        Menu menu = bar.createCustomizeMenu();
        List<String> texts = Arrays.stream(menu.getItems()).map(MenuItem::getText).toList();
        assertThat(texts).contains(TitleBar.MORE_COMMANDS_TEXT, TitleBar.SHOW_BELOW_TEXT);
        MenuItem below = Arrays.stream(menu.getItems()).filter(i -> i.getText().equals(TitleBar.SHOW_BELOW_TEXT)).findFirst().orElseThrow();
        below.notifyListeners(SWT.Selection, new Event());
        assertThat(bar.isQuickAccessBelow()).isTrue();
        menu.dispose();
        assertThat(Arrays.stream(bar.createCustomizeMenu().getItems()).map(MenuItem::getText)).contains(TitleBar.SHOW_ABOVE_TEXT);
    }

    @Test
    void belowTheBarTheToolbarHasItsOwnRow() {
        TitleBar bar = bar(IDS);
        int oneRow = bar.computeSize(1000, SWT.DEFAULT).y;
        bar.setQuickAccessBelow(true);
        int twoRows = bar.computeSize(1000, SWT.DEFAULT).y;
        assertThat(twoRows).isGreaterThan(oneRow);
        bar.setBounds(0, 0, 1000, twoRows);
        bar.computeLayout();
        TitleBarItem save = bar.getItem("save");
        assertThat(save.getBounds().y).isGreaterThanOrEqualTo(oneRow);
        assertThat(bar.getPart(save.getBounds().x + 2, save.getBounds().y + 2)).isEqualTo(Part.ITEM);
        assertThat(bar.getPart(900, oneRow + 10)).isEqualTo(Part.TOOLBAR);
        assertThat(Win32Frame.hitCode(Part.TOOLBAR, true, false)).isEqualTo(Win32Frame.HTCLIENT);
        // The caption row still has the window buttons and room for the caption right after the icon.
        assertThat(bar.getPartBounds(Part.CLOSE).y).isZero();
        assertThat(bar.getPart(bar.getPartBounds(Part.SYSTEM_MENU).width + 20, oneRow / 2)).isEqualTo(Part.CAPTION);
    }

    @Test
    void theDialogAddsRemovesMovesAndResets() throws IOException {
        Path file = dir.resolve("titlebar.properties");
        TitleBar bar = bar(IDS);
        bar.setPreferences(file);
        QuickAccessDialog dialog = new QuickAccessDialog(bar);
        dialog.create();
        QuickAccessDialog.Model model = dialog.model;
        assertThat(model.shown).containsExactly("autosave", "save", "undo", "redo");
        assertThat(model.available()).containsExactly("print");
        assertThat(dialog.availableList.getItems()).containsExactly("Print");

        model.add("print", 0);
        assertThat(model.shown).containsExactly("autosave", "print", "save", "undo", "redo");
        assertThat(model.move("redo", -10)).isZero();
        model.remove("save");
        model.below = true;
        dialog.ok.notifyListeners(SWT.Selection, new Event());

        assertThat(dialog.shell.isDisposed()).isTrue();
        assertThat(shown(bar)).containsExactly("redo", "autosave", "print", "undo");
        assertThat(bar.isQuickAccessBelow()).isTrue();
        assertThat(TitleBarCustomization.load(file).shown()).containsExactly("redo", "autosave", "print", "undo");

        bar.resetCustomization();
        assertThat(shown(bar)).containsExactly("autosave", "save", "undo", "redo");
        assertThat(bar.isQuickAccessBelow()).isFalse();
    }
}
