package zapnotes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.notifications.NotificationPopup;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.ThemeWizard;
import com.zapthemes.themes.BundledThemes;

/**
 * ZapNotes: a small notes editor written as an ordinary JFace application, on ZapThemes. Notebooks
 * and notes in a navigator, a tab per open note, Properties and Tasks views, any number of windows
 * over one model, and the usual dialogs. Window › Theme and the preferences switch the theme live.
 *
 * <p>{@code --smoke} opens two windows, two editors, the field assists and every dialog in every
 * bundled theme, checks that each control is tagged and has its theme background, and exits non-zero
 * on failure. {@code --screenshots <dir>} also saves them as PNG.
 */
public final class ZapNotes {

    static final String THEME = "theme";
    static final String WRAP = "wrapLines";
    static final String AUTO = "auto";

    final Model model = Model.sample();
    final Display display;
    final ThemeManager themes;
    final PreferenceStore preferences = new PreferenceStore();
    private final ThemeRegistry registry = new ThemeRegistry();
    private final List<NotesWindow> windows = new ArrayList<>();
    private final List<IAction> themeActions = new ArrayList<>();
    private final List<MenuManager> themeMenus = new ArrayList<>();
    /** The user's own themes (made with the theme editor), by id, with their files. */
    private final Map<String, Path> userThemes = new LinkedHashMap<>();

    private ZapNotes(Display display, boolean smoke) {
        this.display = display;
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        if (!smoke) {
            loadUserThemes();
        }
        themes = ThemeManager.get(display);
        preferences.setDefault(THEME, AUTO);
        preferences.setDefault(WRAP, true);
        preferences.addPropertyChangeListener(e -> {
            if (e.getProperty().equals(THEME)) {
                applyTheme();
            }
        });
        applyTheme();
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display.setAppName("ZapNotes");
        Display display = new Display();
        // Data binding runs in the display's realm (NoteEditor binds its title).
        org.eclipse.core.databinding.observable.Realm.runWithDefault(
                org.eclipse.jface.databinding.swt.DisplayRealm.getRealm(display), () -> run(display, options));
    }

    private static void run(Display display, List<String> options) {
        ZapNotes app = new ZapNotes(display, options.contains("--smoke") || options.contains("--screenshots"));
        app.openWindow();
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            int exit;
            try {
                exit = app.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            display.dispose();
            System.exit(exit);
        }
        while (!app.windows.isEmpty()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    // ---------------------------------------------------------------- windows

    NotesWindow openWindow() {
        NotesWindow window = new NotesWindow(this);
        window.setBlockOnOpen(false);
        windows.add(window);
        window.open();
        return window;
    }

    void closed(NotesWindow window) {
        windows.remove(window);
    }

    void exit() {
        for (NotesWindow window : List.copyOf(windows)) {
            if (!window.close()) {
                return;
            }
        }
    }

    boolean wrapLines() {
        return preferences.getBoolean(WRAP);
    }

    // ---------------------------------------------------------------- themes

    private void applyTheme() {
        String id = preferences.getString(THEME);
        if (id.equals(AUTO)) {
            id = BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme());
        }
        themes.setTheme(registry.resolve(id));
        String chosen = preferences.getString(THEME);
        themeActions.forEach(a -> a.setChecked(chosen.equals(a.getId())));
    }

    /** Where the user's themes are: {@code ~/.zapthemes/themes}, where the theme editor saves them, or {@code -Dzapnotes.themes}. */
    static Path userThemesDirectory() {
        String dir = System.getProperty("zapnotes.themes");
        return dir != null ? Path.of(dir) : Path.of(System.getProperty("user.home"), ".zapthemes", "themes");
    }

    /** Loads every theme file in {@link #userThemesDirectory()}; a file that does not load is skipped. */
    private void loadUserThemes() {
        Path dir = userThemesDirectory();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try {
                    addUserTheme(file);
                } catch (RuntimeException e) {
                    System.err.println("ZapNotes: skipped the theme " + file + ": " + e.getMessage());
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("ZapNotes: could not read " + dir + ": " + e.getMessage());
        }
    }

    /** Registers the theme in {@code file} (again, after it was edited); a bundled theme's id is refused. */
    private String addUserTheme(Path file) {
        Theme theme = registry.loadFile(file);
        if (BundledThemes.IDS.contains(theme.id())) {
            throw new IllegalArgumentException("its id " + theme.id() + " is a bundled theme's");
        }
        boolean added = !userThemes.containsKey(theme.id());
        userThemes.put(theme.id(), file);
        if (added) {
            String[] choice = { registry.resolve(theme.id()).name(), theme.id() };
            themeMenus.removeIf(menu -> menu.getMenu() != null && menu.getMenu().isDisposed());
            themeMenus.forEach(menu -> {
                menu.add(themeAction(choice));
                menu.update(true);
            });
        }
        return theme.id();
    }

    /** The bundled themes, resolved: the ones the theme editor duplicates and builds on. */
    List<Theme> bundledThemes() {
        return BundledThemes.IDS.stream().map(registry::resolve).toList();
    }

    private List<String[]> themeChoices() {
        List<String[]> choices = new ArrayList<>();
        choices.add(new String[] { "&Automatic (follows the desktop)", AUTO });
        for (String id : BundledThemes.IDS) {
            choices.add(new String[] { registry.resolve(id).name(), id });
        }
        for (String id : userThemes.keySet()) {
            choices.add(new String[] { registry.resolve(id).name(), id });
        }
        return choices;
    }

    /** Window › Theme: one radio item per theme, shared by every window; themes made later are added. */
    MenuManager themeMenu() {
        MenuManager menu = new MenuManager("&Theme");
        for (String[] choice : themeChoices()) {
            menu.add(themeAction(choice));
        }
        themeMenus.add(menu);
        return menu;
    }

    private Action themeAction(String[] choice) {
        Action action = new Action(choice[0], IAction.AS_RADIO_BUTTON) {
            @Override
            public void run() {
                if (isChecked()) {
                    preferences.setValue(THEME, choice[1]);
                }
            }
        };
        action.setId(choice[1]);
        action.setChecked(choice[1].equals(preferences.getString(THEME)));
        themeActions.add(action);
        return action;
    }

    IAction nextThemeAction() {
        return new Action("Switch &Theme") {
            @Override
            public void run() {
                int next = (BundledThemes.IDS.indexOf(themes.getTheme().id()) + 1) % BundledThemes.IDS.size();
                preferences.setValue(THEME, BundledThemes.IDS.get(next));
            }
        };
    }

    // ---------------------------------------------------------------- dialogs

    WizardDialog newNoteWizard(NotesWindow window) {
        Wizard wizard = new Wizard() {
            private Text title;
            private ComboViewer notebook;

            {
                setWindowTitle("New Note");
            }

            @Override
            public void addPages() {
                addPage(new WizardPage("note", "Create a note", null) {
                    @Override
                    public void createControl(Composite parent) {
                        Composite page = WidgetFactory.composite(SWT.NONE).create(parent);
                        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(page);
                        WidgetFactory.label(SWT.NONE).text("&Title:").create(page);
                        title = WidgetFactory.text(SWT.BORDER).onModify(e -> validate()).create(page);
                        GridDataFactory.fillDefaults().grab(true, false).applyTo(title);
                        WidgetFactory.label(SWT.NONE).text("&Notebook:").create(page);
                        notebook = new ComboViewer(page, SWT.READ_ONLY);
                        notebook.setContentProvider(ArrayContentProvider.getInstance());
                        notebook.setInput(model.notebooks);
                        notebook.setSelection(new StructuredSelection(model.notebooks.getFirst()));
                        setControl(page);
                        setDescription("Give the note a title and pick its notebook.");
                        validate();
                    }

                    private void validate() {
                        boolean ok = !title.getText().isBlank();
                        setErrorMessage(ok || title.getText().isEmpty() ? null : "The title cannot be blank.");
                        setPageComplete(ok);
                    }
                });
            }

            @Override
            public boolean performFinish() {
                Model.Note note = model.add(title.getText().strip(), (String) notebook.getStructuredSelection().getFirstElement());
                window.select(note);
                window.open(note);
                return true;
            }
        };
        return new WizardDialog(window.getShell(), wizard);
    }

    /** Find in the open note's body. */
    Dialog findDialog(NotesWindow window) {
        return new Dialog(window.getShell()) {
            private Text text;
            private Button caseSensitive;

            @Override
            protected void configureShell(Shell shell) {
                super.configureShell(shell);
                shell.setText("Find");
            }

            @Override
            protected Control createDialogArea(Composite parent) {
                Composite area = (Composite) super.createDialogArea(parent);
                GridLayoutFactory.swtDefaults().numColumns(2).applyTo(area);
                WidgetFactory.label(SWT.NONE).text("F&ind:").create(area);
                text = WidgetFactory.text(SWT.BORDER).create(area);
                GridDataFactory.fillDefaults().grab(true, false).hint(260, SWT.DEFAULT).applyTo(text);
                caseSensitive = WidgetFactory.button(SWT.CHECK).text("&Case sensitive").create(area);
                GridDataFactory.fillDefaults().span(2, 1).applyTo(caseSensitive);
                return area;
            }

            @Override
            protected void createButtonsForButtonBar(Composite parent) {
                createButton(parent, IDialogConstants.OK_ID, "&Find", true);
                createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, false);
            }

            @Override
            protected void okPressed() {
                boolean found = window.activeEditor().map(e -> e.find(text.getText(), caseSensitive.getSelection())).orElse(false);
                window.statusError(found ? null : "\"" + text.getText() + "\" not found.");
            }
        };
    }

    PreferenceDialog preferences(Shell parent) {
        PreferenceManager pages = new PreferenceManager();
        pages.addToRoot(new PreferenceNode("appearance", new AppearancePage()));
        pages.addToRoot(new PreferenceNode("editor", new FieldEditorPreferencePage("Editor", FieldEditorPreferencePage.GRID) {
            {
                setPreferenceStore(preferences);
            }

            @Override
            protected void createFieldEditors() {
                addField(new BooleanFieldEditor(WRAP, "&Wrap long lines (notes opened from now on)", getFieldEditorParent()));
            }
        }));
        return new PreferenceDialog(parent, pages);
    }

    /**
     * The themes: Automatic, the bundled ones and the user's own. New Theme… and Edit Theme… open
     * the theme editor (zapthemes-editor); a theme it saves is added, selected and applied at once.
     */
    private final class AppearancePage extends PreferencePage {
        private TableViewer list;
        private Button edit;

        AppearancePage() {
            super("Appearance");
            setPreferenceStore(preferences);
            setDescription("The theme applies to every window at once. Your own themes are saved in " + userThemesDirectory() + ".");
        }

        @Override
        protected Control createContents(Composite parent) {
            Composite page = WidgetFactory.composite(SWT.NONE).create(parent);
            GridLayoutFactory.fillDefaults().numColumns(2).applyTo(page);
            list = new TableViewer(page, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL);
            list.setContentProvider(ArrayContentProvider.getInstance());
            list.setLabelProvider(new LabelProvider() {
                @Override
                public String getText(Object element) {
                    String[] choice = (String[]) element;
                    return choice[0].replace("&", "") + (userThemes.containsKey(choice[1]) ? "  (yours)" : "");
                }
            });
            GridDataFactory.fillDefaults().grab(true, true).hint(260, 160).applyTo(list.getTable());
            list.addSelectionChangedListener(e -> edit.setEnabled(selected().filter(userThemes::containsKey).isPresent()));
            Composite buttons = WidgetFactory.composite(SWT.NONE).create(page);
            GridLayoutFactory.fillDefaults().applyTo(buttons);
            GridDataFactory.fillDefaults().align(SWT.FILL, SWT.TOP).applyTo(buttons);
            Button create = WidgetFactory.button(SWT.PUSH).text("&New Theme...").onSelect(e -> newTheme()).create(buttons);
            GridDataFactory.fillDefaults().applyTo(create);
            edit = WidgetFactory.button(SWT.PUSH).text("&Edit Theme...").onSelect(e -> editTheme()).create(buttons);
            edit.setToolTipText("Changes one of your own themes; bundled themes can be duplicated in New Theme...");
            GridDataFactory.fillDefaults().applyTo(edit);
            fill(preferences.getString(THEME));
            return page;
        }

        private void fill(String select) {
            List<String[]> choices = themeChoices();
            list.setInput(choices);
            choices.stream().filter(c -> c[1].equals(select)).findFirst().ifPresent(c -> list.setSelection(new StructuredSelection((Object) c)));
            edit.setEnabled(selected().filter(userThemes::containsKey).isPresent());
        }

        private java.util.Optional<String> selected() {
            return list.getStructuredSelection().getFirstElement() instanceof String[] choice ? java.util.Optional.of(choice[1])
                    : java.util.Optional.empty();
        }

        private void newTheme() {
            ThemeWizard wizard = new ThemeWizard(bundledThemes());
            if (ThemeWizard.dialog(getShell(), wizard).open() == Window.OK) {
                adopt(wizard.savedFile());
            }
        }

        private void editTheme() {
            selected().map(userThemes::get).ifPresent(file -> {
                ThemeWizard wizard = ThemeWizard.edit(bundledThemes(), file);
                if (ThemeWizard.dialog(getShell(), wizard).open() == Window.OK) {
                    adopt(wizard.savedFile());
                }
            });
        }

        /** Loads the saved theme, lists it, selects it and applies it. */
        private void adopt(Path file) {
            try {
                String id = addUserTheme(file);
                fill(id);
                if (id.equals(preferences.getString(THEME))) {
                    applyTheme();
                } else {
                    preferences.setValue(THEME, id);
                }
            } catch (RuntimeException e) {
                setErrorMessage("Could not use the theme in " + file + ": " + e.getMessage());
            }
        }

        @Override
        protected void performDefaults() {
            fill(AUTO);
            super.performDefaults();
        }

        @Override
        public boolean performOk() {
            selected().ifPresent(id -> preferences.setValue(THEME, id));
            return true;
        }
    }

    MessageDialog about(Shell parent) {
        return new MessageDialog(parent, "About ZapNotes", null,
                "ZapNotes, a JFace application on ZapThemes.\n\nTheme: " + themes.getTheme().name(),
                MessageDialog.INFORMATION, 0, IDialogConstants.OK_LABEL);
    }

    InputDialog renameDialog(Shell parent, Model.Note note) {
        return new InputDialog(parent, "Rename Note", "New title:", note.title,
                value -> value.isBlank() ? "The title cannot be blank." : null);
    }

    MessageDialog deleteDialog(Shell parent, Model.Note note) {
        return new MessageDialog(parent, "Delete Note", null,
                "Delete \"" + note.title + "\" and its " + note.tasks.size() + " tasks?",
                MessageDialog.QUESTION, 0, "&Delete", IDialogConstants.CANCEL_LABEL);
    }

    /** "Exports" every note with a progress dialog, then says so in a notification; nothing is written. */
    void export(Shell parent) {
        int[] exported = { 0 };
        try {
            new ProgressMonitorDialog(parent).run(true, true, monitor -> {
                monitor.beginTask("Exporting notes", model.notes.size());
                for (Model.Note note : List.copyOf(model.notes)) {
                    if (monitor.isCanceled()) {
                        break;
                    }
                    monitor.subTask(note.title);
                    Thread.sleep(250);
                    monitor.worked(1);
                    exported[0]++;
                }
                monitor.done();
            });
            exportedNotification(parent, exported[0]).open();
        } catch (java.lang.reflect.InvocationTargetException e) {
            MessageDialog.openError(parent, "Export failed", e.getCause().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    NotificationPopup exportedNotification(Shell parent, int count) {
        return NotificationPopup.forShell(parent).title("Export finished", true)
                .text(count + (count == 1 ? " note" : " notes") + " exported.").build();
    }

    // ---------------------------------------------------------------- smoke test

    private int smokeTest(Path screenshots) throws Exception {
        NotesWindow first = windows.getFirst();
        Model.Note release = model.notes.getFirst();
        first.select(release);
        first.open(model.notes.get(1));
        NoteEditor editor = first.open(release);
        NotesWindow second = openWindow();
        second.getShell().setLocation(first.getShell().getLocation().x + 60, first.getShell().getLocation().y + 60);
        second.select(model.notes.get(2));
        second.open(model.notes.get(2));

        Map<String, Supplier<Window>> dialogs = new LinkedHashMap<>();
        dialogs.put("wizard", () -> newNoteWizard(first));
        dialogs.put("find", () -> findDialog(first));
        dialogs.put("preferences", () -> preferences(first.getShell()));
        dialogs.put("theme wizard", () -> ThemeWizard.dialog(first.getShell(), new ThemeWizard(bundledThemes())));
        dialogs.put("rename", () -> renameDialog(first.getShell(), release));
        dialogs.put("delete", () -> deleteDialog(first.getShell(), release));
        dialogs.put("about", () -> about(first.getShell()));
        dialogs.put("progress", () -> new ProgressMonitorDialog(first.getShell()));
        dialogs.put("notification", () -> exportedNotification(first.getShell(), model.notes.size()));

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String id : BundledThemes.IDS) {
            preferences.setValue(THEME, id);
            settle();
            checked += check(id + " window 1", first.getShell(), problems);
            checked += check(id + " window 2", second.getShell(), problems);
            screenshot(first.getShell(), screenshots, "window-" + id);
            screenshot(second.getShell(), screenshots, "window2-" + id);

            editor.showAssists();
            Shell popup = newestShell(Set.of(first.getShell(), second.getShell()));
            if (popup == null) {
                problems.add(id + ": the tag suggestions did not open");
            } else {
                checked += check(id + " tag suggestions", popup, problems);
                screenshot(popup, screenshots, "assist-" + id);
                popup.close();
            }
            screenshot(first.getShell(), screenshots, "validation-" + id);
            editor.reload();

            for (var entry : dialogs.entrySet()) {
                Window dialog = entry.getValue().get();
                dialog.setBlockOnOpen(false);
                dialog.open();
                settle();
                checked += check(id + " " + entry.getKey(), dialog.getShell(), problems);
                screenshot(dialog.getShell(), screenshots, entry.getKey() + "-" + id);
                dialog.close();
            }
        }
        preferences.setValue(THEME, AUTO);
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls in 2 windows, the field assists and " + dialogs.size()
                    + " dialogs tagged and themed, in " + BundledThemes.IDS.size() + " themes");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private Shell newestShell(Set<Shell> except) {
        Shell found = null;
        for (Shell shell : display.getShells()) {
            if (shell.isVisible() && !except.contains(shell)) {
                found = shell;
            }
        }
        return found;
    }

    private int check(String where, Control control, List<String> problems) {
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(where + ": " + control.getClass().getName() + " is not tagged");
        }
        themes.styleOf(control).color("background").ifPresent(expected -> {
            if (!control.getBackground().getRGB().equals(expected.rgb)) {
                problems.add(where + ": " + control.getClass().getName() + " background " + control.getBackground().getRGB()
                        + ", theme says " + expected.rgb);
            }
        });
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(where, child, problems);
            }
        }
        return count;
    }

    private void settle() {
        for (int i = 0; i < 20; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
            display.timerExec(25, () -> { });
            display.sleep();
        }
    }

    private static void screenshot(Shell shell, Path dir, String name) throws Exception {
        if (dir == null) {
            return;
        }
        Files.createDirectories(dir);
        var area = shell.getClientArea();
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        loader.save(dir.resolve(name + ".png").toString(), SWT.IMAGE_PNG);
        image.dispose();
    }
}
