package com.zapthemes.jface;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.swt.widgets.Label;
import com.zapthemes.widgets.titlebar.TitleBar;
import com.zapthemes.widgets.titlebar.TitleBarItem;
import com.zapthemes.widgets.titlebar.TitleBarProfile;

class TitleBarWindowTest {

    @TempDir
    Path dir;

    private TitleBarWindow window;
    private final List<String> ran = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (window != null) {
            window.close();
        }
    }

    private final IAction save = new Action("&Save\tCtrl+S") {
        @Override
        public void run() {
            ran.add("save");
        }
    };

    private final IAction autosave = new Action("AutoSave", IAction.AS_CHECK_BOX) {
        @Override
        public void run() {
            ran.add("autosave " + isChecked());
        }
    };

    private final IAction print = new Action("Print") {
        @Override
        public void run() {
            ran.add("print");
        }
    };

    private TitleBarWindow open(boolean menuBar) {
        save.setId("save");
        autosave.setId("autosave");
        print.setId("print");
        window = new TitleBarWindow(null) {
            {
                addToolBar(SWT.FLAT);
                addStatusLine();
                if (menuBar) {
                    addMenuBar();
                }
                addQuickAccess(autosave);
                addQuickAccess(save);
                addQuickAccess(print, false);
            }

            @Override
            protected void createTitleBar(TitleBar bar) {
                bar.setSearchHint("Search");
                bar.setProfile(new TitleBarProfile("Pat Doe", "pat@example.com").action("Sign out", () -> ran.add("signout")));
            }

            @Override
            protected Path getTitleBarPreferences() {
                return dir.resolve("titlebar.properties");
            }

            @Override
            protected ToolBarManager createToolBarManager(int style) {
                ToolBarManager manager = new ToolBarManager(style);
                manager.add(print);
                return manager;
            }

            @Override
            protected MenuManager createMenuManager() {
                MenuManager bar = new MenuManager();
                MenuManager file = new MenuManager("&File");
                file.add(save);
                bar.add(file);
                return bar;
            }

            @Override
            protected void configureShell(org.eclipse.swt.widgets.Shell shell) {
                super.configureShell(shell);
                shell.setText("Report.docx");
                shell.setSize(900, 500);
            }

            @Override
            protected Control createContents(Composite parent) {
                Label label = new Label(parent, SWT.NONE);
                label.setText("contents");
                return label;
            }
        };
        window.setBlockOnOpen(false);
        window.open();
        settle();
        return window;
    }

    private TitleBarItem item(String id) {
        return window.getTitleBar().getItem(id);
    }

    @Test
    void theBarIsAboveTheToolBarAndContents() {
        open(false);
        TitleBar bar = window.getTitleBar();
        Control[] children = window.getShell().getChildren();
        assertThat(children[0]).isSameAs(bar);
        assertThat(bar.getBounds().y).isZero();
        assertThat(bar.getBounds().width).isEqualTo(window.getShell().getClientArea().width);
        int below = bar.getBounds().y + bar.getBounds().height;
        for (Control child : children) {
            if (child != bar) {
                assertThat(child.getBounds().y).as(child.toString()).isGreaterThanOrEqualTo(below);
            }
        }
        assertThat(bar.getTitle()).isEqualTo("Report.docx");
        assertThat(bar.getPartBounds(TitleBar.Part.TITLE)).isNotNull();
        assertThat(bar.getPartBounds(TitleBar.Part.SEARCH)).isNotNull();
    }

    @Test
    void quickAccessItemsFollowTheirActions() {
        open(false);
        TitleBarItem saveItem = item("save");
        assertThat(saveItem.getText()).isEqualTo("&Save");
        assertThat(item("autosave").getStyle() & SWT.CHECK).isNotZero();
        assertThat(item("print").isShown()).isFalse();

        save.setEnabled(false);
        assertThat(saveItem.getEnabled()).isFalse();
        save.setEnabled(true);
        save.setText("Save &All");
        assertThat(saveItem.getText()).isEqualTo("Save &All");
        autosave.setChecked(true);
        assertThat(item("autosave").getSelection()).isTrue();

        saveItem.activate(false);
        item("autosave").activate(false);
        assertThat(ran).containsExactly("save", "autosave false");
        assertThat(autosave.isChecked()).isFalse();
    }

    @Test
    void disposingTheItemStopsListening() {
        open(false);
        TitleBarItem saveItem = item("save");
        saveItem.dispose();
        save.setEnabled(false);
        assertThat(window.getTitleBar().getItem("save")).isNull();
    }

    @Test
    void theMenuBarBecomesTheTitleMenu() {
        open(true);
        assertThat(window.getShell().getMenuBar()).isNull();
        assertThat(window.getTitleBar().getTitleMenu()).isNotNull();
        Rectangle title = window.getTitleBar().getPartBounds(TitleBar.Part.TITLE);
        assertThat(title).isNotNull();
    }

    @Test
    void customizationsGoToTheWindowsPreferencesFile() throws Exception {
        open(false);
        item("print").setShown(true);
        window.close();
        open(false);
        assertThat(item("print").isShown()).isTrue();
        assertThat(java.nio.file.Files.readString(dir.resolve("titlebar.properties"))).contains("quickAccess.shown=autosave,save,print");
    }

    @Test
    void profileActionsCanBeJFaceActions() {
        TitleBarProfile.Action action = TitleBarWindow.profileAction(print);
        assertThat(action.text()).isEqualTo("Print");
        action.run().run();
        assertThat(ran).containsExactly("print");
    }

    private static void settle() {
        Display display = Display.getDefault();
        for (int i = 0; i < 10; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }
}
