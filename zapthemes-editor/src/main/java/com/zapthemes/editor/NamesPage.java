package com.zapthemes.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import com.zapthemes.core.model.FontSpec;
import com.zapthemes.editor.EditorModel.NameInfo;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.EditorModel.Source;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;

/**
 * Page 2: the common names, grouped, with the color and font each one gets. Names you add can be
 * renamed in place (F2, Rename, or a second click on the selected name), given what they hold, and
 * removed once nothing refers to them. <em>Fill from palette</em> forgets the colors you or a
 * duplicated theme set, so that all of them follow the palette.
 */
final class NamesPage extends EditorPage {

    /** A group of names in the tree. */
    record GroupNode(String name) {
    }

    static final String[] HOLDS = { "Color", "Font", "Color and font" };

    private TreeViewer viewer;
    private Button rename;
    private Button remove;
    private Label about;
    private final ChipImages images = new ChipImages();
    private long selectedAt;
    private Object selectedElement;

    NamesPage(EditorModel model) {
        super("names", "Common names", "The meanings your colors and fonts get. Each is filled from the palette until "
                + "you set it; add names of your own for anything else.", model, Page.NAMES);
    }

    @Override
    protected void createContent(Composite parent) {
        parent.setLayout(new GridLayout(2, false));
        viewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        viewer.getTree().setHeaderVisible(true);
        viewer.getTree().setLinesVisible(true);
        viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.setContentProvider(new Content());
        viewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
        viewer.getTree().addDisposeListener(e -> images.dispose());

        column("Name", 130, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof GroupNode group ? group.name() : ((NameInfo) element).name();
            }
        }, new NameEditing());
        column("Holds", 95, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof NameInfo info ? holds(info.parts()) : "";
            }
        }, new HoldsEditing());
        column("Color", 90, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof NameInfo info ? model.color(info.name()).map(EditorModel::hex).orElse("") : "";
            }

            @Override
            public Image getImage(Object element) {
                return element instanceof NameInfo info ? model.color(info.name()).map(rgb -> images.of(rgb)).orElse(null) : null;
            }
        }, null);
        column("Font", 115, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof NameInfo info && info.hasFont() ? model.font(info.name()).map(NamesPage::describe).orElse("") : "";
            }
        }, null);
        column("From", 75, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof NameInfo info ? source(info) : "";
            }
        }, null);

        ColumnViewerEditorActivationStrategy activation = new ColumnViewerEditorActivationStrategy(viewer) {
            @Override
            protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
                return switch (event.eventType) {
                    case ColumnViewerEditorActivationEvent.PROGRAMMATIC -> true;
                    case ColumnViewerEditorActivationEvent.KEY_PRESSED -> event.keyCode == SWT.F2;
                    // A second, slow click on the selected row; the click that selects it does not edit.
                    case ColumnViewerEditorActivationEvent.MOUSE_CLICK_SELECTION -> ((MouseEvent) event.sourceEvent).button == 1
                            && event.getSource() instanceof ViewerCell cell && cell.getElement().equals(selectedElement)
                            && System.currentTimeMillis() - selectedAt > viewer.getTree().getDisplay().getDoubleClickTime();
                    default -> false;
                };
            }
        };
        TreeViewerEditor.create(viewer, activation, ColumnViewerEditor.KEYBOARD_ACTIVATION | ColumnViewerEditor.TABBING_HORIZONTAL);
        viewer.addSelectionChangedListener(e -> {
            // Whichever comes first, the click or the selection, a click edits only the row already selected.
            Object element = viewer.getStructuredSelection().getFirstElement();
            if (!java.util.Objects.equals(element, selectedElement)) {
                selectedElement = element;
                selectedAt = System.currentTimeMillis();
            }
            updateButtons();
        });

        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        GridLayout layout = new GridLayout(1, true);
        layout.marginWidth = 0;
        buttons.setLayout(layout);
        Button add = button(buttons, "Add name", e -> addName());
        add.setToolTipText("Adds a name of your own, holding a color, and starts renaming it");
        rename = button(buttons, "Rename", e -> selectedName().filter(NameInfo::added).ifPresent(info -> viewer.editElement(info, 0)));
        rename.setToolTipText("Renames the selected name you added (F2); whatever refers to it follows");
        remove = button(buttons, "Remove", e -> selectedName().ifPresent(info -> tryEdit(() -> model.removeName(info.name()))));
        remove.setToolTipText("Removes the selected name you added, once nothing refers to it");
        new Label(buttons, SWT.NONE);
        Button fill = button(buttons, "Fill from palette", e -> tryEdit(model::fillAllFromPalette));
        fill.setToolTipText("Forgets the colors you or a duplicated theme set, so that every name follows the palette");

        about = new Label(parent, SWT.WRAP);
        about.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    }

    @Override
    protected void refresh() {
        // While a name is renamed in place, the tree is left alone: refreshing it or setting its
        // selection would end the edit (on GTK, a selection set again ends it).
        if (viewer.isCellEditorActive()) {
            updateButtons();
            return;
        }
        Object selection = viewer.getStructuredSelection().getFirstElement();
        if (viewer.getInput() == null) {
            viewer.setInput(model);
        } else {
            viewer.refresh();
        }
        if (selection instanceof NameInfo info) {
            model.name(info.name()).filter(now -> !now.equals(info) || viewer.getStructuredSelection().getFirstElement() == null)
                    .ifPresent(now -> viewer.setSelection(new StructuredSelection(now), true));
        }
        updateButtons();
    }

    TreeViewer viewer() {
        return viewer;
    }

    /** Adds a color name and starts renaming it in place. */
    void addName() {
        String name = model.freeName("newName");
        if (tryEdit(() -> model.addName(name, EditorModel.COLOR))) {
            viewer.refresh();
            NameInfo info = model.name(name).orElseThrow();
            viewer.expandToLevel(new GroupNode(info.group()), 1);
            viewer.setSelection(new StructuredSelection(info), true);
            viewer.editElement(info, 0);
            if (!viewer.isCellEditorActive()) {
                // The new row's item may not exist yet (GTK expands and creates items later): try again then.
                viewer.getTree().getDisplay().asyncExec(() -> {
                    if (!viewer.getTree().isDisposed() && !viewer.isCellEditorActive() && model.name(name).isPresent()) {
                        viewer.editElement(model.name(name).orElseThrow(), 0);
                    }
                });
            }
        }
    }

    private Optional<NameInfo> selectedName() {
        return viewer.getStructuredSelection().getFirstElement() instanceof NameInfo info ? Optional.of(info) : Optional.empty();
    }

    private void updateButtons() {
        Optional<NameInfo> name = selectedName();
        rename.setEnabled(name.filter(NameInfo::added).isPresent());
        remove.setEnabled(name.filter(n -> n.added() && model.usedBy(n.name()).isEmpty()).isPresent());
        about.setText(name.map(n -> n.name() + ": " + n.description()
                + (model.usedBy(n.name()).isEmpty() ? "" : ". Used by " + String.join(", ", model.usedBy(n.name())) + "."))
                .orElse("Select a name to see what it is for."));
        about.getParent().layout(new org.eclipse.swt.widgets.Control[] { about });
    }

    private String source(NameInfo info) {
        List<String> parts = new ArrayList<>();
        if (info.hasColor()) {
            parts.add(switch (model.colorSource(info.name())) {
                case AUTO -> info.added() ? "accent" : "palette";
                case THEME -> "theme";
                case SET -> "you";
            });
        }
        if (info.hasFont() && model.fontSource(info.name()) == Source.SET) {
            parts.add("font: you");
        }
        return String.join(", ", parts);
    }

    static String holds(Set<String> parts) {
        return parts.size() == 2 ? HOLDS[2] : parts.contains("color") ? HOLDS[0] : HOLDS[1];
    }

    static String describe(FontSpec font) {
        String style = FontChoice.styleName(font.style());
        return (font.name() != null ? font.name() : "System") + (font.height() > 0 ? " " + font.height() : "")
                + ("normal".equals(style) ? "" : " " + style.replace('|', ' '));
    }

    private void column(String title, int width, ColumnLabelProvider labels, EditingSupport editing) {
        TreeViewerColumn column = new TreeViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(labels);
        if (editing != null) {
            column.setEditingSupport(editing);
        }
    }

    private static Button button(org.eclipse.swt.widgets.Composite parent, String text, org.eclipse.swt.widgets.Listener listener) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button.addListener(SWT.Selection, listener);
        return button;
    }

    /** Groups of names, in the base names' order, then the names you added. */
    private final class Content implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object input) {
            return groups().keySet().stream().map(GroupNode::new).toArray();
        }

        @Override
        public Object[] getChildren(Object parent) {
            return parent instanceof GroupNode group ? groups().getOrDefault(group.name(), List.of()).toArray() : new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            return element instanceof NameInfo info ? new GroupNode(info.group()) : null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof GroupNode;
        }

        private Map<String, List<NameInfo>> groups() {
            Map<String, List<NameInfo>> groups = new LinkedHashMap<>();
            for (NameInfo info : model.names()) {
                groups.computeIfAbsent(info.group(), g -> new ArrayList<>()).add(info);
            }
            return groups;
        }
    }

    /** Renames an added name in place; the page shows why a name is refused while it is typed. */
    private final class NameEditing extends EditingSupport {
        private final TextCellEditor editor;
        private String editing;

        NameEditing() {
            super(viewer);
            editor = new TextCellEditor(viewer.getTree());
            editor.setValidator(value -> model.nameProblem((String) value, editing));
            editor.addListener(new ICellEditorListener() {
                @Override
                public void editorValueChanged(boolean oldValidState, boolean newValidState) {
                    setErrorMessage(editor.getErrorMessage());
                }

                @Override
                public void applyEditorValue() {
                    setErrorMessage(null);
                }

                @Override
                public void cancelEditor() {
                    setErrorMessage(null);
                }
            });
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            editing = ((NameInfo) element).name();
            return editor;
        }

        @Override
        protected boolean canEdit(Object element) {
            return element instanceof NameInfo info && info.added();
        }

        @Override
        protected Object getValue(Object element) {
            return ((NameInfo) element).name();
        }

        @Override
        protected void setValue(Object element, Object value) {
            String from = ((NameInfo) element).name();
            String to = ((String) value).strip();
            if (value != null && !from.equals(to) && tryEdit(() -> model.renameName(from, to))) {
                viewer.refresh();
                model.name(to).ifPresent(info -> viewer.setSelection(new StructuredSelection(info), true));
            }
        }
    }

    /** What an added name holds, chosen in place. */
    private final class HoldsEditing extends EditingSupport {
        private final ComboBoxCellEditor editor;

        HoldsEditing() {
            super(viewer);
            editor = new ComboBoxCellEditor(viewer.getTree(), HOLDS, SWT.READ_ONLY);
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            return editor;
        }

        @Override
        protected boolean canEdit(Object element) {
            return element instanceof NameInfo info && info.added();
        }

        @Override
        protected Object getValue(Object element) {
            return List.of(HOLDS).indexOf(holds(((NameInfo) element).parts()));
        }

        @Override
        protected void setValue(Object element, Object value) {
            int index = (Integer) value;
            if (index < 0) {
                return;
            }
            Set<String> parts = switch (index) {
                case 0 -> EditorModel.COLOR;
                case 1 -> EditorModel.FONT;
                default -> EditorModel.BOTH;
            };
            String name = ((NameInfo) element).name();
            if (tryEdit(() -> model.setParts(name, parts))) {
                viewer.refresh();
            }
        }
    }
}
