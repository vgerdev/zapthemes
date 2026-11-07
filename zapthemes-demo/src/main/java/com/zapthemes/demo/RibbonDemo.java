package com.zapthemes.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.StyledText;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.CustomizationStore;
import com.zapthemes.widgets.command.Images;
import com.zapthemes.widgets.ribbon.MessageBar;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonColorButton;
import com.zapthemes.widgets.ribbon.RibbonComboBox;
import com.zapthemes.widgets.ribbon.RibbonContextualTabSet;
import com.zapthemes.widgets.ribbon.RibbonGallery;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonTab;
import com.zapthemes.widgets.ribbon.Size;

/**
 * The ribbon laid out like a word processor's: File, Home, Insert, ... with Clipboard, Font,
 * Paragraph, Styles and Editing groups, a styles gallery, color buttons, Comments and Share, a
 * contextual "Table Tools" set, the backstage, and a message bar. Icons are drawn in code.
 *
 * <p>{@code --smoke} narrows the window through every reduction stage, opens the backstage, the
 * KeyTips, the collapsed ribbon and the contextual tabs, in every bundled theme, checks them, and
 * exits non-zero on failure. {@code --screenshots <dir>} saves each stage.
 */
public final class RibbonDemo {

    static final RGB WORD_BLUE = new RGB(0x18, 0x5A, 0xBD);
    static final RGB SHEET_GREEN = new RGB(0x10, 0x7C, 0x41);
    private static final String[] STYLES = { "Normal", "No Spacing", "Heading 1", "Heading 2", "Title", "Subtitle", "Subtle Emphasis",
        "Emphasis", "Intense Emphasis", "Strong", "Quote", "Intense Quote", "Book Title", "List Paragraph" };

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private final DemoIcons icons;
    private Shell shell;
    private Ribbon ribbon;
    private CommandRegistry commands;
    private StyledText editor;
    private MessageBar messageBar;
    private Label status;
    private RibbonContextualTabSet tableTools;
    private final List<String> log = new ArrayList<>();

    private RibbonDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        this.icons = new DemoIcons(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) throws Exception {
        List<String> options = List.of(args);
        Display display = new Display();
        RibbonDemo demo = new RibbonDemo(display);
        Path store = null;
        if (options.contains("--smoke") || options.contains("--screenshots")) {
            store = Files.createTempFile("zapthemes-ribbon", ".json");
            Files.delete(store);
        }
        demo.open(store);
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            exit = demo.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            demo.shell.dispose();
        }
        while (!demo.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        demo.icons.dispose();
        display.dispose();
        if (store != null) {
            Files.deleteIfExists(store);
        }
        System.exit(exit);
    }

    // ------------------------------------------------------------------ the window

    private void open(Path storeFile) {
        shell = new Shell(display);
        shell.setText("ZapThemes — Ribbon");
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        shell.setLayout(layout);
        commands = CommandRegistry.of(shell);
        defineCommands();

        ribbon = new Ribbon(shell, SWT.NONE, commands);
        ribbon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        ribbon.setAccentColor(WORD_BLUE);
        buildRibbon();

        messageBar = new MessageBar(shell, SWT.NONE, MessageBar.Kind.WARNING);
        messageBar.setTitle("Recovered unsaved file");
        messageBar.setMessage("This is a recovered file that is temporarily stored on your computer.");
        messageBar.addButton("Save", () -> run("file.save"));
        messageBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        editor = new StyledText(shell, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        editor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        editor.setMargins(48, 24, 48, 24);
        editor.setText(SAMPLE);
        editor.setStyleRange(new StyleRange(0, SAMPLE.indexOf('\n'), null, null, SWT.BOLD));

        status = new Label(shell, SWT.NONE);
        status.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        status.setText("  Page 1 of 1    " + SAMPLE.split("\\s+").length + " words");

        if (storeFile != null) {
            ribbon.setCustomizationStore(CustomizationStore.open(storeFile));
        }
        shell.setSize(1400, 760);
        shell.open();
        editor.setFocus();
    }

    private Command cmd(String id, String label, String glyph, RGB color, String category) {
        return commands.add(new Command(id, label).images(icons.of(glyph, color)).category(category).onRun(e -> {
            log.add(id);
            status.setText("  Ran " + Command.stripMnemonic(label) + (e.value() != null ? " (" + e.value() + ")" : ""));
        }));
    }

    private void run(String id) {
        commands.get(id).run(new com.zapthemes.widgets.command.CommandEvent(commands.get(id), shell));
    }

    private void defineCommands() {
        RGB blue = new RGB(0x2F, 0x6F, 0xD0);
        RGB slate = new RGB(0x50, 0x5A, 0x68);
        RGB green = new RGB(0x2E, 0x8B, 0x57);
        RGB red = new RGB(0xC2, 0x3B, 0x3B);
        RGB purple = new RGB(0x7A, 0x4F, 0xC0);
        RGB orange = new RGB(0xD0, 0x7A, 0x20);
        // File
        cmd("file.save", "&Save", "S", blue, "File").shortcut(SWT.MOD1 | 'S').description("Save this document.");
        cmd("file.close", "&Close", "X", slate, "File");
        // Clipboard
        cmd("home.paste", "&Paste", "P", slate, "Home").style(Command.Style.SPLIT).shortcut(SWT.MOD1 | 'V')
                .description("Add content from the clipboard to your document.")
                .menu(CommandMenu.of("home.pasteKeep", "home.pasteText", CommandMenu.SEPARATOR, "home.pasteSpecial"));
        cmd("home.pasteKeep", "&Keep Source Formatting", "K", slate, "Home");
        cmd("home.pasteText", "Keep &Text Only", "T", slate, "Home");
        cmd("home.pasteSpecial", "Paste &Special...", "PS", slate, "Home");
        cmd("home.cut", "Cu&t", "X", slate, "Home").shortcut(SWT.MOD1 | 'X').description("Remove the selection and put it on the clipboard.");
        cmd("home.copy", "&Copy", "C", slate, "Home").shortcut(SWT.MOD1 | 'C').description("Put a copy of the selection on the clipboard.");
        cmd("home.formatPainter", "&Format Painter", "FP", orange, "Home").style(Command.Style.TOGGLE)
                .description("Copy formatting from one place and apply it to another.");
        cmd("home.clipboard.dialog", "Clipboard Options", "C", slate, "Home").description("Show the clipboard task pane.");
        // Font
        cmd("home.grow", "&Grow Font", "A+", slate, "Home").shortcut(SWT.MOD1 | SWT.SHIFT | '>').description("Make your text a bit bigger.");
        cmd("home.shrink", "Shrin&k Font", "A-", slate, "Home").shortcut(SWT.MOD1 | SWT.SHIFT | '<').description("Make your text a bit smaller.");
        cmd("home.case", "Change &Case", "Aa", slate, "Home").style(Command.Style.DROP_DOWN)
                .menu(CommandMenu.of("home.caseSentence", "home.caseLower", "home.caseUpper"));
        cmd("home.caseSentence", "&Sentence case.", "Sc", slate, "Home");
        cmd("home.caseLower", "&lowercase", "a", slate, "Home");
        cmd("home.caseUpper", "&UPPERCASE", "A", slate, "Home");
        cmd("home.clear", "Clear All F&ormatting", "Cl", red, "Home").description("Remove all formatting from the selection.");
        cmd("home.bold", "&Bold", "B", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'B').description("Make your text bold.");
        cmd("home.italic", "&Italic", "I", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'I').description("Italicize your text.");
        cmd("home.underline", "&Underline", "U", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'U')
                .description("Underline your text.");
        cmd("home.strike", "Stri&kethrough", "S", slate, "Home").style(Command.Style.TOGGLE).description("Cross something out.");
        cmd("home.subscript", "Su&bscript", "x2", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | '=');
        cmd("home.superscript", "Su&perscript", "x2", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | SWT.SHIFT | '+');
        cmd("home.highlight", "Text &Highlight Color", "H", orange, "Home").description("Make your text stand out by highlighting it.");
        cmd("home.fontColor", "Font &Color", "A", red, "Home").description("Change the color of your text.");
        cmd("home.font.dialog", "Font Settings", "F", slate, "Home").shortcut(SWT.MOD1 | 'D').description("Font settings for the selection.");
        // Paragraph
        cmd("home.bullets", "&Bullets", "••", slate, "Home").style(Command.Style.SPLIT).menu(CommandMenu.of("home.bulletsNone"))
                .description("Create a bulleted list.");
        cmd("home.bulletsNone", "&None", "-", slate, "Home");
        cmd("home.numbering", "&Numbering", "12", slate, "Home").style(Command.Style.SPLIT).menu(CommandMenu.of("home.bulletsNone"))
                .description("Create a numbered list.");
        cmd("home.outdent", "&Decrease Indent", "<", slate, "Home");
        cmd("home.indent", "&Increase Indent", ">", slate, "Home");
        cmd("home.sort", "&Sort", "AZ", slate, "Home").description("Sort the selection alphabetically or numerically.");
        cmd("home.marks", "Show/&Hide ¶", "¶", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | '*')
                .description("Show paragraph marks and other hidden formatting symbols.");
        cmd("home.alignLeft", "Align &Left", "L", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'L');
        cmd("home.center", "&Center", "C", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'E');
        cmd("home.alignRight", "Align &Right", "R", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'R');
        cmd("home.justify", "&Justify", "J", slate, "Home").style(Command.Style.TOGGLE).shortcut(SWT.MOD1 | 'J');
        cmd("home.spacing", "Line and Paragraph &Spacing", "≡", slate, "Home").style(Command.Style.DROP_DOWN)
                .menu(CommandMenu.of(menu -> {
                    for (String value : new String[] { "1.0", "1.15", "1.5", "2.0" }) {
                        com.zapthemes.swt.widgets.MenuItem item = new com.zapthemes.swt.widgets.MenuItem(menu, SWT.RADIO);
                        item.setText(value);
                    }
                }));
        cmd("home.shading", "S&hading", "Sh", orange, "Home");
        cmd("home.paragraph.dialog", "Paragraph Settings", "P", slate, "Home");
        // Styles
        cmd("home.styles.new", "Create a &Style", "+", green, "Home");
        cmd("home.styles.clear", "C&lear Formatting", "Cl", red, "Home");
        cmd("home.styles.dialog", "Styles", "S", slate, "Home").shortcut(SWT.MOD1 | SWT.ALT | SWT.SHIFT | 'S');
        // Editing
        cmd("home.find", "&Find", "F", slate, "Home").style(Command.Style.SPLIT).shortcut(SWT.MOD1 | 'F')
                .menu(CommandMenu.of("home.findAdvanced", "home.goTo")).description("Find text or other content in the document.");
        cmd("home.findAdvanced", "&Advanced Find...", "AF", slate, "Home");
        cmd("home.goTo", "&Go To...", "G", slate, "Home").shortcut(SWT.MOD1 | 'G');
        cmd("home.replace", "&Replace", "R", slate, "Home").shortcut(SWT.MOD1 | 'H').description("Search for text and replace it.");
        cmd("home.select", "S&elect", "Se", slate, "Home").style(Command.Style.DROP_DOWN).menu(CommandMenu.of("home.selectAll"));
        cmd("home.selectAll", "Select &All", "A", slate, "Home").shortcut(SWT.MOD1 | 'A');
        // Large buttons
        cmd("tools.pdf", "Create a PDF", "Pd", red, "Tools").description("Save the document as a PDF file.");
        cmd("tools.dictate", "&Dictate", "D", blue, "Tools").style(Command.Style.SPLIT).menu(CommandMenu.of("tools.dictateSettings"))
                .description("Use your voice to type.");
        cmd("tools.dictateSettings", "&Settings", "S", slate, "Tools");
        cmd("tools.editor", "&Editor", "Ed", purple, "Tools").description("See suggestions for your writing.");
        cmd("tools.addins", "Add-&ins", "+", green, "Tools").description("Get add-ins for this document.");
        // Right side
        cmd("comments", "Comments", "C", slate, "Review").description("See the comments in this document.");
        cmd("share", "Share", "S", blue, "File").style(Command.Style.DROP_DOWN).menu(CommandMenu.of("share.link", "share.copy"));
        cmd("share.link", "&Send a Link", "L", blue, "File");
        cmd("share.copy", "&Send a Copy", "C", blue, "File");
        // Insert, Layout, View
        cmd("insert.table", "&Table", "T", blue, "Insert").description("Add a table; the Table Tools tabs show.");
        cmd("insert.picture", "&Pictures", "Pi", green, "Insert");
        cmd("insert.shapes", "&Shapes", "Sh", orange, "Insert");
        cmd("insert.link", "&Link", "Li", blue, "Insert").shortcut(SWT.MOD1 | 'K');
        cmd("insert.comment", "&Comment", "Co", slate, "Insert");
        cmd("insert.header", "&Header", "He", slate, "Insert");
        cmd("insert.footer", "&Footer", "Fo", slate, "Insert");
        cmd("insert.pageNumber", "Page &Number", "#", slate, "Insert");
        cmd("layout.margins", "&Margins", "Ma", slate, "Layout");
        cmd("layout.orientation", "&Orientation", "Or", slate, "Layout");
        cmd("layout.size", "Si&ze", "Sz", slate, "Layout");
        cmd("layout.columns", "&Columns", "Co", slate, "Layout");
        cmd("view.read", "&Read Mode", "R", slate, "View");
        cmd("view.print", "&Print Layout", "P", slate, "View");
        cmd("view.web", "&Web Layout", "W", slate, "View");
        cmd("view.ruler", "R&uler", "Ru", slate, "View").style(Command.Style.TOGGLE);
        cmd("view.gridlines", "&Gridlines", "Gr", slate, "View").style(Command.Style.TOGGLE);
        cmd("view.zoom", "&Zoom", "Z", slate, "View");
        cmd("view.wordBlue", "Blue Accent", "Bl", WORD_BLUE, "View").onRun(e -> ribbon.setAccentColor(WORD_BLUE));
        cmd("view.sheetGreen", "Green Accent", "Gn", SHEET_GREEN, "View").onRun(e -> ribbon.setAccentColor(SHEET_GREEN));
        cmd("view.themeAccent", "Theme Accent", "Th", slate, "View").onRun(e -> ribbon.setAccentColor(null));
        cmd("view.nextTheme", "&Next Theme", "Nx", purple, "View").description("Switch to the next bundled theme.")
                .onRun(e -> themes.setTheme(available.get((available.indexOf(themes.getTheme()) + 1) % available.size())));
        cmd("view.customize", "Customize the &Ribbon...", "Cu", slate, "View").onRun(e -> ribbon.openCustomizeDialog());
        // Table Tools
        cmd("table.headerRow", "&Header Row", "HR", blue, "Table").style(Command.Style.TOGGLE);
        cmd("table.banded", "&Banded Rows", "BR", blue, "Table").style(Command.Style.TOGGLE);
        cmd("table.borders", "&Borders", "Bo", slate, "Table").style(Command.Style.SPLIT).menu(CommandMenu.of("table.bordersNone"));
        cmd("table.bordersNone", "&No Border", "-", slate, "Table");
        cmd("table.insertAbove", "Insert &Above", "IA", green, "Table");
        cmd("table.insertBelow", "Insert &Below", "IB", green, "Table");
        cmd("table.delete", "&Delete", "De", red, "Table");
        cmd("table.done", "Close Table Tools", "X", slate, "Table").onRun(e -> tableTools.setVisible(false));
        commands.get("insert.table").onRun(e -> tableTools.setVisible(true, true));
    }

    private void buildRibbon() {
        ribbon.setFileTab("File", backstage -> {
            backstage.addPage("info", "Info", icons.of("i", new RGB(0x2F, 0x6F, 0xD0)), page -> {
                Label text = new Label(page, SWT.WRAP);
                text.setText("Document6 — a recovered file. Protect, inspect and manage versions of this document here.");
                text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            });
            backstage.addPage("new", "New", null, page -> new Label(page, SWT.NONE).setText("Blank document"));
            backstage.addPage("open", "Open", null, page -> new Label(page, SWT.NONE).setText("Recent documents"));
            backstage.addCommand("file.save");
            backstage.addPage("saveAs", "Save As", null, page -> new Label(page, SWT.NONE).setText("Save a copy"));
            backstage.addPage("print", "Print", null, page -> {
                new Label(page, SWT.NONE).setText("Printer: Default printer");
                Button print = new Button(page, SWT.PUSH);
                print.setText("Print");
            });
            backstage.addPage("export", "Export", null, page -> new Label(page, SWT.NONE).setText("Create a PDF"));
            backstage.addCommand("file.close");
            backstage.addSeparator();
            backstage.addPage("account", "Account", null, page -> new Label(page, SWT.NONE).setText("Signed out"));
            backstage.addPage("options", "Options", null, page -> {
                Button customize = new Button(page, SWT.PUSH);
                customize.setText("Customize the Ribbon...");
                customize.addListener(SWT.Selection, e -> ribbon.openCustomizeDialog());
            });
        });

        RibbonTab home = ribbon.addTab("home", "Home").setKeyTip("H");
        RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard").setImage(commands.get("home.paste").images());
        clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE).keyTip("V");
        clipboard.add("home.cut").keyTip("X");
        clipboard.add("home.copy").keyTip("C");
        clipboard.add("home.formatPainter").keyTip("FP");
        clipboard.setDialogLauncher("home.clipboard.dialog");

        RibbonGroup font = home.addGroup("home.font", "Font").setImage(commands.get("home.fontColor").images());
        RibbonComboBox fontName = new RibbonComboBox("home.fontName", "Font").width(130).description("Pick a new font for your text.")
                .items(fontNames()).text("Aptos");
        RibbonComboBox fontSize = new RibbonComboBox("home.fontSize", "Font Size").width(34).description("Change the size of your text.")
                .items("8", "9", "10", "10.5", "11", "12", "14", "16", "18", "20", "24", "28", "36", "48", "72").text("12");
        fontName.onChange(name -> status.setText("  Font: " + name));
        fontSize.onChange(size -> status.setText("  Font size: " + size));
        font.row().add(fontName.keyTip("FF")).add(fontSize.keyTip("FS")).add("home.grow").add("home.shrink").separator().add("home.case")
                .add("home.clear");
        font.row().add("home.bold").add("home.italic").add("home.underline").add("home.strike").add("home.subscript").add("home.superscript")
                .separator().add(new RibbonColorButton(commands.get("home.highlight")).color(new RGB(0xFF, 0xFF, 0)))
                .add(new RibbonColorButton(commands.get("home.fontColor")).color(new RGB(0xE0, 0x20, 0x20)));
        font.setDialogLauncher("home.font.dialog");

        RibbonGroup paragraph = home.addGroup("home.paragraph", "Paragraph").setImage(commands.get("home.alignLeft").images());
        paragraph.row().add("home.bullets").add("home.numbering").separator().add("home.outdent").add("home.indent").separator()
                .add("home.sort").separator().add("home.marks");
        paragraph.row().add("home.alignLeft").add("home.center").add("home.alignRight").add("home.justify").separator().add("home.spacing")
                .separator().add(new RibbonColorButton(commands.get("home.shading")).color(new RGB(0xD9, 0xE7, 0xFD)));
        paragraph.setDialogLauncher("home.paragraph.dialog");
        commands.get("home.alignLeft").setChecked(true);

        RibbonGroup styles = home.addGroup("home.styles", "Styles").setImage(commands.get("home.styles.dialog").images());
        RibbonGallery gallery = new RibbonGallery("home.styles.gallery", "Styles", this::paintStyle).items(STYLES).itemSize(76, 54)
                .columns(5, 3).popupColumns(5).popupCommands("home.styles.new", "home.styles.clear", "home.styles.dialog")
                .image(commands.get("home.styles.dialog").images()).description("Format your document with a style.");
        gallery.setSelection(0);
        gallery.onSelect(index -> status.setText("  Style: " + STYLES[index]));
        styles.add(gallery).keyTip("L");
        styles.setDialogLauncher("home.styles.dialog");
        styles.setPriority(-1);

        RibbonGroup editing = home.addGroup("home.editing", "Editing").setImage(commands.get("home.find").images());
        editing.add("home.find").sizes(Size.MEDIUM, Size.SMALL).keyTip("FD");
        editing.add("home.replace").sizes(Size.MEDIUM, Size.SMALL);
        editing.add("home.select").sizes(Size.MEDIUM, Size.SMALL).keyTip("SL");
        editing.setPriority(1);

        home.addGroup("home.pdf", "PDF").setImage(commands.get("tools.pdf").images()).add("tools.pdf").large();
        home.addGroup("home.voice", "Voice").setImage(commands.get("tools.dictate").images()).add("tools.dictate").large();
        home.addGroup("home.editor", "Editor").setImage(commands.get("tools.editor").images()).add("tools.editor").large();
        home.addGroup("home.addins", "Add-ins").setImage(commands.get("tools.addins").images()).add("tools.addins").large();

        RibbonTab insert = ribbon.addTab("insert", "Insert").setKeyTip("N");
        RibbonGroup tables = insert.addGroup("insert.tables", "Tables");
        tables.add("insert.table").large();
        RibbonGroup illustrations = insert.addGroup("insert.illustrations", "Illustrations");
        illustrations.add("insert.picture").large();
        illustrations.add("insert.shapes").large();
        RibbonGroup links = insert.addGroup("insert.links", "Links");
        links.add("insert.link").large();
        links.add("insert.comment").large();
        RibbonGroup headers = insert.addGroup("insert.headers", "Header & Footer");
        headers.add("insert.header");
        headers.add("insert.footer");
        headers.add("insert.pageNumber");

        for (String[] tab : new String[][] { { "design", "Design", "D" }, { "layout", "Layout", "P" }, { "references", "References", "S" },
            { "mailings", "Mailings", "M" }, { "review", "Review", "R" } }) {
            RibbonTab added = ribbon.addTab(tab[0], tab[1]).setKeyTip(tab[2]);
            if (tab[0].equals("layout")) {
                RibbonGroup setup = added.addGroup("layout.setup", "Page Setup");
                setup.add("layout.margins").large();
                setup.add("layout.orientation").large();
                setup.add("layout.size").large();
                setup.add("layout.columns").large();
            } else if (tab[0].equals("review")) {
                added.addGroup("review.comments", "Comments").add("insert.comment").large();
            } else {
                added.addGroup(tab[0] + ".tools", tab[1]).add("tools.editor").large();
            }
        }
        RibbonTab view = ribbon.addTab("view", "View").setKeyTip("W");
        RibbonGroup views = view.addGroup("view.views", "Views");
        views.add("view.read").large();
        views.add("view.print").large();
        views.add("view.web").large();
        RibbonGroup show = view.addGroup("view.show", "Show");
        show.add("view.ruler");
        show.add("view.gridlines");
        view.addGroup("view.zoomGroup", "Zoom").add("view.zoom").large();
        RibbonGroup look = view.addGroup("view.look", "Theme");
        look.add("view.nextTheme").large();
        look.add("view.wordBlue");
        look.add("view.sheetGreen");
        look.add("view.themeAccent");
        view.addGroup("view.ribbon", "Ribbon").add("view.customize").large();
        ribbon.addTab("help", "Help").setKeyTip("Y").addGroup("help.help", "Help").add("tools.addins").large();

        tableTools = ribbon.addContextualTabSet("table", "Table Tools", 2);
        RibbonTab design = tableTools.addTab("table.design", "Table Design").setKeyTip("JT");
        RibbonGroup options = design.addGroup("table.options", "Table Style Options");
        options.add("table.headerRow");
        options.add("table.banded");
        design.addGroup("table.bordersGroup", "Borders").add("table.borders").large();
        design.addGroup("table.close", "Close").add("table.done").large();
        RibbonTab tableLayout = tableTools.addTab("table.layout", "Layout").setKeyTip("JL");
        RibbonGroup rows = tableLayout.addGroup("table.rows", "Rows & Columns");
        rows.add("table.insertAbove").large();
        rows.add("table.insertBelow").large();
        rows.add("table.delete").large();

        ribbon.addRightCommand("comments");
        ribbon.addRightCommand("share", true);
    }

    private static List<String> fontNames() {
        return List.of("Aptos", "Arial", "Calibri", "Cambria", "Consolas", "Courier New", "Georgia", "Segoe UI", "Tahoma", "Times New Roman",
                "Verdana");
    }

    /** A style preview: "AaBbCc" in the style's look, with its name under it. */
    private void paintStyle(GC gc, Rectangle bounds, int index, boolean selected, boolean hovered) {
        Color background = display.getSystemColor(SWT.COLOR_WHITE);
        gc.setBackground(background);
        gc.fillRectangle(bounds);
        String name = STYLES[index];
        boolean heading = name.startsWith("Heading") || name.equals("Title") || name.equals("Subtitle");
        FontData base = editor != null ? editor.getFont().getFontData()[0] : display.getSystemFont().getFontData()[0];
        int height = name.equals("Title") ? base.getHeight() + 5 : heading ? base.getHeight() + 2 : base.getHeight();
        int style = name.contains("Strong") || name.startsWith("Heading") ? SWT.BOLD : name.contains("Emphasis") || name.contains("Quote")
                ? SWT.ITALIC : SWT.NORMAL;
        Font font = new Font(display, base.getName(), height, style);
        gc.setFont(font);
        gc.setForeground(new Color(heading ? new RGB(0x1F, 0x4E, 0x99) : new RGB(0x20, 0x20, 0x20)));
        gc.setClipping(bounds);
        gc.drawText("AaBbCc", bounds.x + 4, bounds.y + 3, true);
        font.dispose();
        gc.setFont(display.getSystemFont());
        gc.setForeground(new Color(new RGB(0x40, 0x40, 0x40)));
        int textHeight = gc.getFontMetrics().getHeight();
        gc.drawText(name, bounds.x + 4, bounds.y + bounds.height - textHeight - 2, true);
        gc.setClipping((Rectangle) null);
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int[] widths = { 1400, 1180, 1000, 860, 720, 600, 480, 360 };
        for (Theme theme : available) {
            themes.setTheme(theme);
            shell.setSize(1400, 760);
            DemoSupport.settle(display);
            check(shell, problems);
            Map<String, Size> previous = null;
            for (int width : widths) {
                shell.setSize(width, 760);
                DemoSupport.settle(display);
                Map<String, Size> sizes = ribbon.groupSizes();
                if (previous != null) {
                    for (Map.Entry<String, Size> entry : sizes.entrySet()) {
                        Size before = previous.get(entry.getKey());
                        if (before != null && entry.getValue().compareTo(before) < 0) {
                            problems.add(theme.id() + ": " + entry.getKey() + " grew from " + before + " to " + entry.getValue() + " at " + width);
                        }
                    }
                }
                previous = sizes;
                if (screenshots != null && (theme == available.getFirst() || width == widths[0])) {
                    DemoSupport.screenshot(ribbon, screenshots, "ribbon-" + theme.id() + "-" + width);
                }
            }
            if (!previous.containsValue(Size.COLLAPSED)) {
                problems.add(theme.id() + ": no group collapsed at " + widths[widths.length - 1] + " px: " + previous);
            }
            shell.setSize(1400, 760);
            DemoSupport.settle(display);
            if (screenshots != null) {
                // The whole window as the screen shows it (ribbon, message bar, document).
                shell.forceActive();
                shell.redraw(0, 0, 1400, 760, true);
                shell.update();
                DemoSupport.settle(display);
                DemoSupport.settle(display);
                DemoSupport.screenCopy(shell, screenshots, "ribbon-" + theme.id() + "-window");
            }
            Map<String, Size> wide = ribbon.groupSizes();
            // Windows keeps a window within the screen, so on a narrow screen (CI) 1400 px isn't reached.
            int reached = ribbon.getSize().x;
            if (reached < 1300) {
                System.out.println("SMOKE NOTE: " + theme.id() + ": the screen allows only " + reached + " px; Clipboard (" + wide.get("home.clipboard") + ") not checked at full width");
            } else if (!Size.LARGE.equals(wide.get("home.clipboard"))) {
                problems.add(theme.id() + ": Clipboard is " + wide.get("home.clipboard") + " at " + reached + " px");
            }

            // KeyTips: Alt shows the tabs' badges, H the Home tab's, Esc goes back.
            ribbon.showKeyTips();
            DemoSupport.settle(display);
            if (!"H".equals(ribbon.visibleKeyTips().get("Home"))) {
                problems.add(theme.id() + ": Home's KeyTip is " + ribbon.visibleKeyTips().get("Home"));
            }
            shot(screenshots, theme, "keytips");
            ribbon.typeKeyTips("H");
            DemoSupport.settle(display);
            if (!"V".equals(ribbon.visibleKeyTips().get("Paste"))) {
                problems.add(theme.id() + ": Paste's KeyTip is " + ribbon.visibleKeyTips().get("Paste") + " in " + ribbon.visibleKeyTips());
            }
            shot(screenshots, theme, "keytips-home");
            ribbon.hideKeyTips();

            // Backstage.
            ribbon.openBackstage();
            DemoSupport.settle(display);
            if (!ribbon.getBackstage().isOpen() || editor.isVisible()) {
                problems.add(theme.id() + ": the backstage did not cover the window");
            }
            shot(screenshots, theme, "backstage", ribbon.getBackstage());
            ribbon.getBackstage().close();
            DemoSupport.settle(display);
            if (!editor.isVisible()) {
                problems.add(theme.id() + ": the editor did not come back after the backstage");
            }

            // Contextual tabs.
            run("insert.table");
            DemoSupport.settle(display);
            if (ribbon.getSelection() == null || !"table.design".equals(ribbon.getSelection().id())) {
                problems.add(theme.id() + ": inserting a table did not select Table Design");
            }
            shot(screenshots, theme, "contextual");
            tableTools.setVisible(false);
            DemoSupport.settle(display);
            if (!"home".equals(ribbon.getSelection().id())) {
                problems.add(theme.id() + ": hiding Table Tools selected " + ribbon.getSelection().id());
            }

            // Collapse, overlay, expand.
            int expanded = ribbon.getSize().y;
            ribbon.setCollapsed(true);
            DemoSupport.settle(display);
            if (ribbon.getSize().y >= expanded) {
                problems.add(theme.id() + ": collapsing did not shrink the ribbon");
            }
            shot(screenshots, theme, "collapsed");
            ribbon.setCollapsed(false);
            DemoSupport.settle(display);
            if (ribbon.getSize().y != expanded) {
                problems.add(theme.id() + ": expanding gave " + ribbon.getSize().y + " px, not " + expanded);
            }
            messageBar.setKind(Boolean.TRUE.equals(theme.dark()) ? MessageBar.Kind.INFO : MessageBar.Kind.WARNING);
        }
        zoomCheck(problems);
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: the ribbon reduces through " + widths.length + " widths, KeyTips, backstage, contextual tabs,"
                    + " collapse and zoom work, in " + available.size() + " themes");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /** At 200 % the ribbon is about twice as tall, the window keeps its size, and 100 % gives the same ribbon back. */
    private void zoomCheck(List<String> problems) {
        int at100 = ribbon.getSize().y;
        Rectangle window = shell.getBounds();
        themes.setZoom(2);
        DemoSupport.settle(display);
        int at200 = ribbon.getSize().y;
        if (Math.abs((double) at200 / at100 - 2) > 0.25) {
            problems.add("zoom: the ribbon is " + at200 + " px at 200 %, " + at100 + " px at 100 %");
        }
        if (!shell.getBounds().equals(window)) {
            problems.add("zoom: the window changed from " + window + " to " + shell.getBounds());
        }
        themes.resetZoom();
        DemoSupport.settle(display);
        if (ribbon.getSize().y != at100) {
            problems.add("zoom: the ribbon is " + ribbon.getSize().y + " px after the reset, not " + at100);
        }
    }

    private void shot(Path screenshots, Theme theme, String name) {
        if (screenshots != null) {
            shot(screenshots, theme, name, ribbon);
        }
    }

    private void shot(Path screenshots, Theme theme, String name, Control control) {
        if (screenshots != null) {
            // Control.print draws a whole Windows shell with its children misplaced; print the control itself.
            DemoSupport.screenshot(control, screenshots, "ribbon-" + theme.id() + "-" + name);
        }
    }

    private void check(Control control, List<String> problems) {
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                check(child, problems);
            }
        }
    }

    private static final String SAMPLE = """
            Time Required
            About 45 minutes, each time you do this exercise.

            How to Do It
            1. Pick someone you would like to know better. It can be a colleague, a friend or someone you have just met.
            2. Find a quiet time when you both have at least half an hour free.
            3. Take turns asking each other the questions of the first set, then move on to the next set.
            4. Each set asks a little more than the one before, so give each set about the same amount of time.

            Set I
            1. If you could invite anyone to dinner, who would it be?
            2. What would make a day perfect for you?
            """;
}
