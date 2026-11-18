package com.zapthemes.widgets.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandKeyBindingsTest {

    private static Display display;
    private Shell shell;
    private Text text;
    private CommandRegistry registry;
    private CommandKeyBindings bindings;
    private final List<String> ran = new ArrayList<>();

    @BeforeAll
    static void display() {
        display = Display.getDefault();
    }

    @BeforeEach
    void setUp() {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        text = new com.zapthemes.swt.widgets.Text(shell, SWT.BORDER);
        registry = CommandRegistry.of(shell);
        bindings = CommandKeyBindings.install(shell);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
    }

    private Command command(String id, int shortcut) {
        return registry.add(new Command(id, id).shortcut(shortcut).onRun(e -> ran.add(id)));
    }

    private Event key(int keyCode, char character, int stateMask) {
        Event event = new Event();
        event.type = SWT.KeyDown;
        event.widget = text;
        event.display = display;
        event.keyCode = keyCode;
        event.character = character;
        event.stateMask = stateMask;
        event.doit = true;
        return event;
    }

    @Test
    void installingTwiceKeepsOneBinding() {
        assertThat(CommandKeyBindings.install(shell)).isSameAs(bindings);
        assertThat(CommandKeyBindings.install(shell, new CommandRegistry())).isNotSameAs(bindings);
    }

    @Test
    void aShortcutRunsItsCommandAndConsumesTheKey() {
        command("file.save", SWT.MOD1 | 'S');
        Event event = key('s', (char) 0x13, SWT.MOD1);
        assertThat(bindings.handle(event)).isTrue();
        assertThat(ran).containsExactly("file.save");
        assertThat(event.doit).isFalse();
        assertThat(event.type).isEqualTo(SWT.None);
    }

    @Test
    void lettersMatchWithoutCaseAndModifiersMustMatch() {
        command("file.saveAll", SWT.MOD1 | SWT.SHIFT | 's');
        command("file.save", SWT.MOD1 | 'S');
        assertThat(bindings.handle(key('s', (char) 0x13, SWT.MOD1 | SWT.SHIFT))).isTrue();
        assertThat(bindings.handle(key('s', (char) 0x13, SWT.MOD1))).isTrue();
        assertThat(bindings.handle(key('s', 's', 0))).isFalse();
        assertThat(ran).containsExactly("file.saveAll", "file.save");
    }

    @Test
    void functionAndDeleteKeys() {
        command("edit.rename", SWT.F2);
        command("edit.delete", SWT.DEL);
        assertThat(bindings.handle(key(SWT.F2, (char) 0, 0))).isTrue();
        assertThat(bindings.handle(key(SWT.DEL, SWT.DEL, 0))).isTrue();
        assertThat(ran).containsExactly("edit.rename", "edit.delete");
    }

    @Test
    void disabledAndHiddenCommandsDoNotRunAndLeaveTheKey() {
        Command save = command("file.save", SWT.MOD1 | 'S');
        save.setEnabled(false);
        Event event = key('s', (char) 0x13, SWT.MOD1);
        assertThat(bindings.handle(event)).isFalse();
        assertThat(event.doit).isTrue();
        save.setEnabled(true);
        save.setVisible(false);
        assertThat(bindings.handle(key('s', (char) 0x13, SWT.MOD1))).isFalse();
        assertThat(ran).isEmpty();
    }

    @Test
    void theFirstOfTwoCommandsOnOneShortcutWins() {
        command("first", SWT.MOD1 | 'K');
        command("second", SWT.MOD1 | 'K');
        assertThat(bindings.find(SWT.MOD1 | 'k')).map(Command::id).contains("first");
        assertThat(bindings.bindings()).hasSize(1);
        registry.get("first").setEnabled(false);
        assertThat(bindings.find(SWT.MOD1 | 'K')).map(Command::id).contains("second");
    }

    @Test
    void togglesFlipLikeCheckMenuItems() {
        Command bold = command("home.bold", SWT.MOD1 | 'B').style(Command.Style.TOGGLE);
        bindings.handle(key('b', (char) 2, SWT.MOD1));
        assertThat(bold.isChecked()).isTrue();
    }

    @Test
    void keysOutsideTheShellAndModifiersAloneAreIgnored() {
        command("file.save", SWT.MOD1 | 'S');
        Shell dialog = new com.zapthemes.swt.widgets.Shell(shell);
        try {
            Event event = key('s', (char) 0x13, SWT.MOD1);
            event.widget = dialog;
            assertThat(bindings.handle(event)).isFalse();
        } finally {
            dialog.dispose();
        }
        assertThat(bindings.handle(key(SWT.CTRL, (char) 0, 0))).isFalse();
        bindings.setEnabled(false);
        assertThat(bindings.handle(key('s', (char) 0x13, SWT.MOD1))).isFalse();
        assertThat(ran).isEmpty();
    }

    @Test
    void aMenuBarItemWithTheSameAcceleratorKeepsIt() {
        command("file.save", SWT.MOD1 | 'S');
        command("file.open", SWT.MOD1 | 'O');
        Menu bar = new com.zapthemes.swt.widgets.Menu(shell, SWT.BAR);
        shell.setMenuBar(bar);
        MenuItem file = new com.zapthemes.swt.widgets.MenuItem(bar, SWT.CASCADE);
        Menu fileMenu = new com.zapthemes.swt.widgets.Menu(shell, SWT.DROP_DOWN);
        file.setMenu(fileMenu);
        new com.zapthemes.swt.widgets.MenuItem(fileMenu, SWT.PUSH).setAccelerator(SWT.MOD1 | 'S');
        assertThat(bindings.handle(key('s', (char) 0x13, SWT.MOD1))).isFalse();
        assertThat(bindings.handle(key('o', (char) 0x0F, SWT.MOD1))).isTrue();
        assertThat(ran).containsExactly("file.open");
    }

    @Test
    void theFilterRunsCommandsForRealEvents() {
        command("file.save", SWT.MOD1 | 'S');
        Event event = key('s', (char) 0x13, SWT.MOD1);
        text.notifyListeners(SWT.KeyDown, event);
        assertThat(ran).containsExactly("file.save");
    }
}
