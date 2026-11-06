# ZapNotes

A small notes editor written as an ordinary JFace application, built the way an outside user
builds against ZapThemes: a separate Gradle build that depends only on the published
`com.zapthemes` artifacts and Maven Central, following
[Add the library](../../docs/theme-authoring.md#1-add-the-library).

- **Views:** a navigator of notebooks and notes, an editor area with a tab per open note (dirty
  `*`, close asks about unsaved changes), Properties and Tasks (checkbox) views, IDE-style title
  bars.
- **Editor form:** a title bound with JFace data binding (a validator, and
  `ControlDecorationSupport` showing its message), a notebook combo, tags with content assist
  (Ctrl+Space), and a `StyledText` body.
- **Windows and dialogs:** any number of windows over one model (Window › New Window), a new-note
  wizard, Find, Rename, Delete, About, Preferences (theme and line wrapping) and an Export with a
  progress dialog.
- **Themes:** Window › Theme, the toolbar's Switch Theme, or Preferences › Appearance switch every
  window live. "Automatic" follows the desktop.

## Run it

Until 1.0 is on Maven Central, the artifacts come from the staged repository:

```bash
./gradlew publishToStaging            # in the repository root: build/staging-deploy
./gradlew -p apps/zapnotes run
./gradlew -p apps/zapnotes smokeTest  # 2 windows, field assists, 7 dialogs, every theme
```

`-PzapthemesVersion=…` picks another version. The SWT fragment for the build machine is chosen in
[build.gradle.kts](build.gradle.kts), replacing the platform-neutral placeholder the POMs name.
