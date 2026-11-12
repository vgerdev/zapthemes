package com.zapthemes.editor;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.FileDialog;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;

/**
 * Page 5: the theme's id, name and light or dark, where to save it, and its checks: errors
 * (unknown names, reference cycles, bad values) keep Finish off; contrast warnings do not. Finish
 * writes the full theme: every rule written out, with no {@code extends}.
 */
final class FinishPage extends EditorPage {

    /** Where themes are saved unless the user picks another place. */
    static final Path THEMES = Path.of(System.getProperty("user.home"), ".zapthemes", "themes");

    private Text id;
    private Text name;
    private Button dark;
    private Text file;
    private Button apply;
    private Table checks;
    private Label summary;
    private boolean updating;
    /** The file follows the id until the user picks one. */
    private boolean fileChosen;

    FinishPage(EditorModel model) {
        super("finish", "Save the theme", "Name the theme and choose where to save it. Finish writes the full theme, "
                + "every rule in it, so it does not depend on the library's themes.", model, null);
    }

    @Override
    protected void createContent(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        Group theme = group(parent, "Theme", 2);
        new Label(theme, SWT.NONE).setText("&Id:");
        id = new Text(theme, SWT.BORDER);
        id.setToolTipText("What applications use to pick the theme, such as ocean-dark");
        id.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        id.addListener(SWT.Modify, e -> identityEdited());
        new Label(theme, SWT.NONE).setText("&Name:");
        name = new Text(theme, SWT.BORDER);
        name.setToolTipText("What people see, such as Ocean Dark");
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        name.addListener(SWT.Modify, e -> identityEdited());
        new Label(theme, SWT.NONE);
        dark = new Button(theme, SWT.CHECK);
        dark.setText("A &dark theme");
        dark.setToolTipText("For tools that sort themes by light and dark; it changes no colors");
        dark.addListener(SWT.Selection, e -> identityEdited());

        Group save = group(parent, "Save as", 3);
        new Label(save, SWT.NONE).setText("&File:");
        file = new Text(save, SWT.BORDER);
        file.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        file.addListener(SWT.Modify, e -> {
            if (!updating) {
                fileChosen = true;
                validate();
            }
        });
        Button browse = new Button(save, SWT.PUSH);
        browse.setText("&Browse…");
        browse.addListener(SWT.Selection, e -> browse());
        new Label(save, SWT.NONE);
        apply = new Button(save, SWT.CHECK);
        apply.setText("&Use the theme in this application when the wizard closes");
        apply.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Group check = group(parent, "Checks", 1);
        check.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        summary = new Label(check, SWT.WRAP);
        summary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        checks = new Table(check, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        checks.setHeaderVisible(true);
        checks.setLinesVisible(true);
        checks.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (String title : new String[] { "", "Problem" }) {
            TableColumn column = new TableColumn(checks, SWT.NONE);
            column.setText(title);
            column.setWidth(title.isEmpty() ? 80 : 520);
        }
    }

    @Override
    protected void refresh() {
        updating = true;
        try {
            if (!id.isFocusControl() && !id.getText().equals(model.id())) {
                id.setText(model.id());
            }
            if (!name.isFocusControl() && !name.getText().equals(model.title())) {
                name.setText(model.title());
            }
            dark.setSelection(model.dark());
            String path = model.file().map(Path::toString).orElse(fileChosen ? file.getText() : defaultFile().toString());
            if (model.file().isPresent()) {
                fileChosen = true;
            }
            if (!file.isFocusControl() && !file.getText().equals(path)) {
                file.setText(path);
            }
        } finally {
            updating = false;
        }
        List<String> problems = model.problems();
        checks.removeAll();
        long errors = problems.stream().filter(p -> p.startsWith("Error: ")).count();
        for (String problem : problems) {
            TableItem item = new TableItem(checks, SWT.NONE);
            boolean error = problem.startsWith("Error: ");
            item.setText(new String[] { error ? "Error" : "Warning", problem.substring(problem.indexOf(": ") + 2) });
        }
        summary.setText(problems.isEmpty() ? "No problems: every name resolves, and every contrast pair reaches its minimum."
                : errors > 0 ? errors + " error(s) keep the theme from loading; fix them on the earlier pages."
                        : (problems.size()) + " contrast warning(s); the theme still loads.");
        summary.getParent().layout(true);
        validate();
    }

    /** Where Finish saves, as typed. */
    Path target() {
        return Path.of(file.getText().strip());
    }

    boolean applyWhenDone() {
        return apply.getSelection();
    }

    Text idField() {
        return id;
    }

    Text fileField() {
        return file;
    }

    Table checks() {
        return checks;
    }

    private Path defaultFile() {
        return THEMES.resolve((EditorModel.idProblem(id.getText()) == null ? id.getText() : model.id()) + ".json");
    }

    private void identityEdited() {
        if (updating) {
            return;
        }
        String problem = EditorModel.idProblem(id.getText().strip());
        if (problem == null) {
            model.setIdentity(id.getText().strip(), name.getText(), dark.getSelection());
            if (!fileChosen) {
                updating = true;
                file.setText(defaultFile().toString());
                updating = false;
            }
        }
        validate();
    }

    private void browse() {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[] { "*.json" });
        dialog.setOverwrite(true);
        Path current = target();
        if (current.getParent() != null) {
            dialog.setFilterPath(current.getParent().toString());
        }
        dialog.setFileName(current.getFileName() != null ? current.getFileName().toString() : model.id() + ".json");
        String chosen = dialog.open();
        if (chosen != null) {
            fileChosen = true;
            file.setText(chosen);
        }
    }

    private void validate() {
        String message = EditorModel.idProblem(id.getText().strip());
        if (message == null && file.getText().isBlank()) {
            message = "Choose where to save the theme.";
        }
        if (message == null && model.problems().stream().anyMatch(p -> p.startsWith("Error: "))) {
            message = "The theme has errors; see Checks.";
        }
        setErrorMessage(message);
        setPageComplete(message == null);
    }

    private static Group group(org.eclipse.swt.widgets.Composite parent, String title, int columns) {
        Group group = new Group(parent, SWT.NONE);
        group.setText(title);
        group.setLayout(new GridLayout(columns, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return group;
    }
}
