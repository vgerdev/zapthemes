package com.zapthemes.jface.ribbon;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.widgets.FlatButton;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandKeyBindings;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.Images;
import com.zapthemes.widgets.ribbon.Backstage;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonComboBox;
import com.zapthemes.widgets.ribbon.RibbonContextualTabSet;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonTab;
import com.zapthemes.widgets.ribbon.Size;

/** Compiles the calls the conversion skill's references name; not run. */
abstract class SkillSnippetsCompileCheck extends ZapAppWindow {
    IAction save = new Action("&Save@Ctrl+S") { };
    IAction rename = new Action("&Rename...@F2") { };
    IAction exit = new Action("E&xit") { };
    Image zoom16, zoom32;

    SkillSnippetsCompileCheck() {
        super(null);
        save.setId("file.save");
        addStatusLine();
        addQuickAccess(save);
    }

    @Override
    protected boolean convertMenus() {
        return false;
    }

    @Override
    protected void createRibbon(Ribbon ribbon) {
        RibbonTab home = ribbon.addTab("home", "Home").setKeyTip("H");
        RibbonContributions.add(home.addGroup("home.notes", "Notes"), save).large();
        RibbonContributions.add(home.addGroup("home.editing", "Editing"), rename);
        command(exit);
        RibbonTab view = ribbon.addTab(1, "view", "View");
        RibbonGroup group = view.addGroup(0, "view.font", "Font");
        group.setDialogLauncher("file.save");
        group.setImage(Images.of(zoom16, zoom32));
        group.setSizes(Size.LARGE, Size.SMALL);
        group.add(command(save)).sizes(Size.LARGE, Size.SMALL).keyTip("P");
        group.add("file.save");
        RibbonContributions.fill(group, new MenuManager("&Theme", "window.theme"));
        RibbonContributions.fill(group.row(), new org.eclipse.jface.action.ActionContributionItem(save));
        RibbonComboBox size = new RibbonComboBox("format.fontSize", "Font Size")
                .width(48).items("8", "10", "12", "14").text("10").editable(true)
                .description("Change the font size.")
                .onChange(value -> Integer.parseInt(value));
        group.row().add(size);
        group.row().add(command(save)).separator();
        Command zoom = ribbon.commands().add(new Command("view.zoom", "&Zoom")
                .description("Zoom in or out.").images(Images.of(zoom16, zoom32))
                .style(Command.Style.DROP_DOWN).menu(CommandMenu.of("view.zoomIn", "view.zoomOut"))
                .shortcut(SWT.MOD1 | '=').onRun(e -> { }));
        MenuManager menuManager = new MenuManager();
        zoom.menu(new CommandMenu.Popup(control -> menuManager.createContextMenu(control)));
        view.removeGroup(view.group("view.font"));
        ribbon.removeTab(ribbon.tab("view"));
        ribbon.tabs();
        ribbon.setAccentColor(new RGB(1, 2, 3));
        ribbon.addRightCommand("view.zoom");
        RibbonContextualTabSet set = ribbon.addContextualTabSet("table", "Table Tools", 2);
        set.addTab("table.design", "Table Design");
        set.setVisible(true);
        ActionCommands.setLargeImage(save, null);
        CommandKeyBindings.install(getShell()).bindings();
        CommandKeyBindings.install(getShell()).find(SWT.MOD1 | 'S').ifPresent(c -> { });
        RibbonContributions.add(group, save).large().keyTip("S");
        java.util.List<com.zapthemes.widgets.ribbon.RibbonButton> buttons = RibbonContributions.fill(group);
        ribbon.commands().find("file.save").ifPresent(c -> { });
        ribbon.commands().get("file.save");
        ribbon.commands().all();
        ActionCommands.setRadioGroup(save, rename);
        if (ribbon.tab("missing") == null) {
            command(save);
        }
        refreshRibbon();
        getRibbon();
    }

    /** The screenshot code in ribbon-api.md, "Screenshots". */
    static void screenshot(org.eclipse.swt.widgets.Shell shell, String file) {
        org.eclipse.swt.graphics.Rectangle area = shell.getClientArea();
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(image);
        for (org.eclipse.swt.widgets.Control child : shell.getChildren()) {
            org.eclipse.swt.graphics.Rectangle b = child.getBounds();
            if (!child.getVisible() || b.isEmpty()) {
                continue;
            }
            Image part = new Image(shell.getDisplay(), b.width, b.height);
            org.eclipse.swt.graphics.GC partGc = new org.eclipse.swt.graphics.GC(part);
            child.print(partGc);
            partGc.dispose();
            gc.drawImage(part, b.x - area.x, b.y - area.y);
            part.dispose();
        }
        gc.dispose();
        org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();
        loader.data = new org.eclipse.swt.graphics.ImageData[] { image.getImageData() };
        loader.save(file, SWT.IMAGE_PNG);
        image.dispose();
    }

    @Override
    protected void createBackstage(Backstage backstage) {
        backstage.addCommand(command(save).id());
        backstage.addSeparator();
        backstage.addPage("file.recent", "Recent", null, parent -> {
            com.zapthemes.swt.widgets.Label title = new com.zapthemes.swt.widgets.Label(parent, SWT.NONE);
            ThemeManager.get(parent.getDisplay()).setThemeClass(title, "backstageTitle");
            new FlatButton(parent, SWT.PUSH).setText("notes.txt");
        });
        backstage.setKeyTip("file.recent", "R");
        backstage.addOpenListener(open -> { });
        backstage.addCommand(command(exit).id());
    }
}
