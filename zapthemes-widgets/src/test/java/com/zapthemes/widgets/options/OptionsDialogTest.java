package com.zapthemes.widgets.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
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
        assertThat(shell.getBounds()).as("half the main window, centered on it")
                .isEqualTo(new org.eclipse.swt.graphics.Rectangle(275, 200, 450, 300));
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

    /** A page that needs {@code width} x {@code height}. */
    private static OptionsPage sized(int width, int height) {
        return parent -> {
            parent.setLayout(new org.eclipse.swt.layout.GridLayout());
            Label label = new Label(parent, SWT.NONE);
            org.eclipse.swt.layout.GridData data = new org.eclipse.swt.layout.GridData();
            data.widthHint = width;
            data.heightHint = height;
            label.setLayoutData(data);
        };
    }

    @Test
    void theWindowIsBigEnoughForItsPagesButNotTheMonitor() {
        Rectangle screen = owner.getMonitor().getClientArea();
        OptionsDialog dialog = new OptionsDialog(owner, "Word Options")
                .addPage("small", "Small", null, sized(50, 50))
                .addPage("big", "Big", null, sized(800, 500))
                .addPage("huge", "Huge", null, sized(screen.width * 2, screen.height * 2));

        // Half the main window when the page fits in that.
        org.eclipse.swt.widgets.Shell shell = dialog.create("small");
        assertThat(shell.getSize()).isEqualTo(new org.eclipse.swt.graphics.Point(450, 300));

        // A bigger page, shown later, grows the window, still centered on the main window.
        dialog.select("big");
        Rectangle bounds = shell.getBounds();
        assertThat(bounds.width).isGreaterThan(800);
        assertThat(bounds.height).isGreaterThan(500);
        Rectangle main = owner.getBounds();
        if (bounds.x > screen.x && bounds.y > screen.y) {
            assertThat(bounds.x + bounds.width / 2).isCloseTo(main.x + main.width / 2, org.assertj.core.data.Offset.offset(1));
            assertThat(bounds.y + bounds.height / 2).isCloseTo(main.y + main.height / 2, org.assertj.core.data.Offset.offset(1));
        }
        shell.dispose();

        // Never larger than the monitor, and on it.
        org.eclipse.swt.widgets.Shell huge = new OptionsDialog(owner, "Word Options").addPage("huge", "Huge", null, sized(screen.width * 2, screen.height * 2))
                .create("huge");
        Rectangle hugeBounds = huge.getBounds();
        assertThat(hugeBounds.width).isLessThanOrEqualTo(screen.width);
        assertThat(hugeBounds.height).isLessThanOrEqualTo(screen.height);
        assertThat(screen.contains(hugeBounds.x, hugeBounds.y)).isTrue();
        huge.dispose();
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
