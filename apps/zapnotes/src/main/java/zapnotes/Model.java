package zapnotes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Notebooks, notes and their tasks, shared by every window; listeners hear about each change. */
final class Model {

    static final class Note {
        String title;
        String notebook;
        String tags;
        String body;
        final List<Task> tasks = new ArrayList<>();

        Note(String title, String notebook, String tags, String body) {
            this.title = title;
            this.notebook = notebook;
            this.tags = tags;
            this.body = body;
        }

        int words() {
            String text = body.strip();
            return text.isEmpty() ? 0 : text.split("\\s+").length;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    static final class Task {
        final Note note;
        final String text;
        boolean done;

        Task(Note note, String text, boolean done) {
            this.note = note;
            this.text = text;
            this.done = done;
        }
    }

    final List<String> notebooks = new ArrayList<>();
    final List<Note> notes = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    void changed() {
        listeners.forEach(Runnable::run);
    }

    List<Note> notesIn(String notebook) {
        return notes.stream().filter(n -> n.notebook.equals(notebook)).toList();
    }

    List<Task> tasks() {
        return notes.stream().flatMap(n -> n.tasks.stream()).toList();
    }

    Note add(String title, String notebook) {
        Note note = new Note(title, notebook, "", "");
        notes.add(note);
        changed();
        return note;
    }

    void remove(Note note) {
        notes.remove(note);
        changed();
    }

    static Model sample() {
        Model model = new Model();
        model.notebooks.addAll(List.of("Work", "Personal", "Reading"));
        Note release = note(model, "Release checklist", "Work", "release, ci",
                "# Before 1.0\n\nEverything that must be green before 1.0 ships: CI on Windows, Linux and macOS, the samples, the guide.\n\n"
                        + "> Run `./gradlew smokeTest` on every platform first.\n",
                "Run the smoke tests", true, "Review the macOS screenshots", true, "Verify the namespace", false);
        note(model, "Theme ideas", "Work", "design, themes",
                "A solarized pair, a sepia reading theme, and a theme that follows the desktop accent color.",
                "Sketch the sepia palette", false);
        note(model, "Groceries", "Personal", "home",
                "Coffee beans, oat milk, rye bread, lemons.", "Buy coffee", false, "Buy lemons", true);
        note(model, "Weekend", "Personal", "home, outdoors", "Hike if it does not rain; otherwise the museum.");
        note(model, "SWT book notes", "Reading", "swt, books",
                "Chapter 4: layouts. GridLayout covers most forms; FormLayout for the rest.", "Finish chapter 5", false);
        release.tags = "release, ci, 1.0";
        return model;
    }

    private static Note note(Model model, String title, String notebook, String tags, String body, Object... tasks) {
        Note note = new Note(title, notebook, tags, body);
        for (int i = 0; i < tasks.length; i += 2) {
            note.tasks.add(new Task(note, (String) tasks[i], (Boolean) tasks[i + 1]));
        }
        model.notes.add(note);
        return note;
    }
}
