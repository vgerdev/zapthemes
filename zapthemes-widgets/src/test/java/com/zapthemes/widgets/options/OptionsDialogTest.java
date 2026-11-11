package com.zapthemes.widgets.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;

class OptionsDialogTest {

    private Display display;
    private Shell owner;
    private final List<String> events = new ArrayList<>();

    /** A page that records when it is built and applied. */
    private final class Page implements OptionsPage {
        private final String name;

        Page(String name) {
            this.name = name;
        }

        @Override
        public void create(Composite parent) {
            events.add("create " + name);
            parent.setLayout(new org.eclipse.swt.layout.FillLayout());
            new Label(parent, SWT.NONE).setText(name + " contents");
        }

        @Override
        public void apply() {
            events.add("apply " + name);
        }
    }

    @BeforeEach
    void setUp() {
        display = Display.getDefault();
        owner = new Shell(display);
        owner.setBounds(50, 50, 900, 600);
    }

    @AfterEach
    void tearDown() {
        owner.dispose();
    }

    private OptionsDialog dialog() {
        return new OptionsDialog(owner, "Word Options")
                .addPage("general", "General", "General options for working with Word.", new Page("general"))
                .addPage("ribbon", "Customize Ribbon", "Customize the Ribbon.", new Page("ribbon"))
                .addPage("qat", "Quick Access Toolbar", null, new Page("qat"));
    }

    private static List<String> texts(Composite parent) {
        List<String> texts = new ArrayList<>();
        for (Control child : parent.getChildren()) {
            if (child instanceof org.eclipse.swt.widgets.Label label && label.getVisible()) {
                texts.add(label.getText());
            } else if (child instanceof Composite composite && composite.getVisible()) {
                texts.addAll(texts(composite));
            }
        }
        return texts;
    }

    @Test
    void pagesAreListedOnTheLeftAndShownOnTheRight() {
        OptionsDialog dialog = dialog();
        org.eclipse.swt.widgets.Shell shell = dialog.create("ribbon");
        assertThat(shell.getText()).isEqualTo("Word Options");
        assertThat(dialog.navigation().getItems()).containsExactly("General", "Customize Ribbon", "Quick Access Toolbar");
        assertThat(dialog.navigation().getSelection()).containsExactly("Customize Ribbon");
        assertThat(texts(shell)).contains("Customize the Ribbon.", "ribbon contents").doesNotContain("general contents");
        assertThat(events).as("pages are built when first shown").containsExactly("create ribbon");

        // A click in the navigation list shows that page in the same window.
        dialog.navigation().select(0);
        dialog.navigation().notifyListeners(SWT.Selection, new Event());
        assertThat(texts(shell)).contains("General options for working with Word.", "general contents").doesNotContain("ribbon contents");
        dialog.select("ribbon");
        assertThat(events).as("and built once").containsExactly("create ribbon", "create general");
        assertThat(display.getShells()).as("one window, no pop-ups").hasSize(2);
        shell.dispose();
    }

    @Test
    void okAppliesTheShownPagesAndCancelNone() {
        OptionsDialog dialog = dialog();
        org.eclipse.swt.widgets.Shell shell = dialog.create(null);
        assertThat(dialog.navigation().getSelection()).as("General first").containsExactly("General");
        dialog.select("qat");
        dialog.pressOk();
        assertThat(shell.isDisposed()).isTrue();
        assertThat(events).containsExactly("create general", "create qat", "apply general", "apply qat");

        events.clear();
        OptionsDialog cancelled = dialog();
        org.eclipse.swt.widgets.Shell other = cancelled.create("ribbon");
        other.close();
        assertThat(events).containsExactly("create ribbon");
    }
}
