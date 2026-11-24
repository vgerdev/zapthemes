package com.zapthemes.files.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.FileTypes;
import com.zapthemes.files.ui.FileIcons.Icon;
import com.zapthemes.swt.custom.StyledText;

/**
 * The pane at the right of the file view, as in Explorer: the details pane (a large icon or the
 * picture, the name, type and properties of the selection, or of the folder shown) or the preview
 * pane (an image scaled to fit, or the start of a text file). It follows the selection a moment
 * after it settles, reading in the background.
 */
final class SidePane extends Composite {

    /** What the pane shows. */
    enum Mode {
        DETAILS, PREVIEW
    }

    /** Text files are previewed up to this many bytes. */
    static final int TEXT_BYTES = 64 * 1024;
    private static final int PREVIEW_IMAGE_BYTES = 50 * 1024 * 1024;

    private final FileBrowser browser;
    private final Canvas canvas;
    private final StyledText text;
    private final StackLayout stack = new StackLayout();
    private Mode mode = Mode.DETAILS;
    private List<FileEntry> shown = List.of();
    private int generation;
    private Image preview;
    private String previewMessage;
    private Font bold;

    @SuppressWarnings("this-escape")
    SidePane(FileBrowser browser, Composite parent) {
        super(parent, SWT.NONE);
        this.browser = browser;
        setLayout(stack);
        canvas = new com.zapthemes.swt.widgets.Canvas(this, SWT.DOUBLE_BUFFERED | SWT.NO_BACKGROUND);
        canvas.addListener(SWT.Paint, this::paint);
        canvas.addListener(SWT.Resize, e -> {
            if (mode == Mode.PREVIEW) {
                canvas.redraw();
            }
        });
        text = new StyledText(this, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        text.setMargins(8, 8, 8, 8);
        stack.topControl = canvas;
        addListener(SWT.Dispose, e -> {
            disposePreview();
            if (bold != null) {
                bold.dispose();
            }
        });
        ThemeManager.applyOnCreate(this, "FileItemView");
        ThemeManager.applyOnCreate(canvas, "FileItemView");
    }

    private int px(int pixels) {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(pixels);
    }

    void setMode(Mode mode) {
        if (mode != this.mode) {
            this.mode = mode;
            shown = null;
            update(browser.getSelectedEntries());
        }
    }

    Mode mode() {
        return mode;
    }

    /** The preview's text field (tests). */
    StyledText text() {
        return text;
    }

    /** Whether the preview shows a picture (tests). */
    boolean showsPicture() {
        return preview != null && stack.topControl == canvas;
    }

    /** The line shown instead of a preview, or null (tests). */
    String previewMessage() {
        return mode == Mode.PREVIEW && stack.topControl == canvas && preview == null ? previewMessage : null;
    }

    /** What the details pane lists: lines of label and value, the first two the name and type (tests too). */
    List<String[]> details() {
        List<FileEntry> selection = browser.getSelectedEntries();
        List<String[]> lines = new ArrayList<>();
        if (selection.isEmpty()) {
            FileEntry folder = browser.currentEntry();
            if (folder != null) {
                int count = browser.getVisibleEntries().size();
                lines.add(new String[] { null, folder.displayName() });
                lines.add(new String[] { null, count + (count == 1 ? " item" : " items") });
            }
            return lines;
        }
        if (selection.size() > 1) {
            lines.add(new String[] { null, selection.size() + " items selected" });
            long bytes = 0;
            boolean files = false;
            for (FileEntry entry : selection) {
                if (!entry.isContainer() && entry.size() >= 0) {
                    bytes += entry.size();
                    files = true;
                }
            }
            lines.add(new String[] { null, "" });
            if (files) {
                lines.add(new String[] { "Size", FileTypes.shortSize(bytes) });
            }
            return lines;
        }
        FileEntry entry = selection.getFirst();
        lines.add(new String[] { null, browser.displayName(entry) });
        lines.add(new String[] { null, browser.typeName(entry) });
        if (!entry.isContainer() && entry.size() >= 0) {
            lines.add(new String[] { "Size", FileTypes.shortSize(entry.size()) });
        }
        if (entry.modified() != null) {
            lines.add(new String[] { "Date modified", FileTable.formatDate(entry.modified()) });
        }
        if (entry.permissions() != null) {
            lines.add(new String[] { "Permissions", entry.permissions() });
        }
        if (entry.owner() != null) {
            lines.add(new String[] { "Owner", entry.owner() });
        }
        if (entry.group() != null) {
            lines.add(new String[] { "Group", entry.group() });
        }
        if (entry.linkTarget() != null) {
            lines.add(new String[] { "Link target", entry.linkTarget() });
        }
        if (entry.hidden()) {
            lines.add(new String[] { "Attributes", "Hidden" });
        }
        return lines;
    }

    /** Shows {@code selection} (of the browser's folder); read again only if it changed. */
    void update(List<FileEntry> selection) {
        if (!isVisible()) {
            shown = null;
            return;
        }
        if (mode == Mode.PREVIEW && selection.equals(shown)) {
            return;
        }
        shown = List.copyOf(selection);
        int current = ++generation;
        if (mode == Mode.DETAILS) {
            stack.topControl = canvas;
            layout();
            canvas.redraw();
            return;
        }
        disposePreview();
        previewMessage = null;
        stack.topControl = canvas;
        layout();
        if (selection.size() != 1 || selection.getFirst().isContainer()) {
            previewMessage = selection.isEmpty() || selection.getFirst().isContainer() && selection.size() == 1 ? "Select a file to preview."
                    : "No preview available.";
            canvas.redraw();
            return;
        }
        FileEntry entry = selection.getFirst();
        FileSystem fs = browser.getFileSystem();
        FileTypes.Category category = FileTypes.category(entry);
        if (fs == null || !fs.has(Capability.READ)) {
            previewMessage = "No preview available.";
            canvas.redraw();
            return;
        }
        previewMessage = "Loading…";
        canvas.redraw();
        // A moment after the selection settles: arrowing through a folder doesn't read every file.
        getDisplay().timerExec(150, () -> {
            if (isDisposed() || current != generation) {
                return;
            }
            if (category == FileTypes.Category.IMAGE && entry.size() <= PREVIEW_IMAGE_BYTES) {
                int zoom = Math.max(100, getDisplay().getPrimaryMonitor().getZoom());
                int pixels = Math.min(2048, Math.max(256, Math.max(getSize().x, getSize().y)) * zoom / 100);
                browser.loader().run(() -> Thumbnails.read(fs, entry.path(), pixels), (data, failure) -> {
                    if (current != generation || isDisposed()) {
                        return;
                    }
                    if (data == null) {
                        previewMessage = "No preview available.";
                    } else {
                        ImageData picture = data;
                        preview = new Image(getDisplay(), (ImageDataProvider) z -> picture);
                        previewMessage = null;
                    }
                    canvas.redraw();
                });
            } else {
                browser.loader().run(() -> readText(fs, entry), (content, failure) -> {
                    if (current != generation || isDisposed()) {
                        return;
                    }
                    if (content == null) {
                        previewMessage = failure != null ? "Can't preview: " + FileBrowser.describe(failure) : "No preview available.";
                        canvas.redraw();
                    } else {
                        text.setText(content);
                        text.setTopIndex(0);
                        stack.topControl = text;
                        layout();
                    }
                });
            }
        });
    }

    /** The start of {@code entry} as text, or null if it isn't text (a NUL in it, or not UTF-8 nor Latin-1 text). */
    static String readText(FileSystem fs, FileEntry entry) throws IOException {
        byte[] bytes;
        try (InputStream in = fs.openRead(entry.path())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while (out.size() < TEXT_BYTES && (n = in.read(buffer, 0, Math.min(buffer.length, TEXT_BYTES - out.size()))) > 0) {
                out.write(buffer, 0, n);
            }
            bytes = out.toByteArray();
        }
        for (byte b : bytes) {
            if (b == 0) {
                return null;
            }
        }
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        String text = utf8(bytes, start, bytes.length);
        // Cut at the limit in the middle of a character: without its first bytes.
        for (int cut = 1; text == null && bytes.length >= TEXT_BYTES && cut <= 3; cut++) {
            text = utf8(bytes, start, bytes.length - cut);
        }
        if (text == null) {
            // Not UTF-8: Latin-1 reads any bytes.
            text = new String(bytes, start, bytes.length - start, StandardCharsets.ISO_8859_1);
        }
        return text.replace("\r\n", "\n");
    }

    private static String utf8(byte[] bytes, int from, int to) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, from, Math.max(0, to - from))).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private void disposePreview() {
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
    }

    private Font bold() {
        if (bold == null || bold.isDisposed()) {
            FontData[] data = getFont().getFontData();
            for (FontData d : data) {
                d.setStyle(SWT.BOLD);
                d.setHeight(d.getHeight() + 2);
            }
            bold = new Font(getDisplay(), data);
        }
        return bold;
    }

    /** The font changed (a theme or the zoom): the name's bold font follows. */
    void fontChanged() {
        if (bold != null) {
            bold.dispose();
            bold = null;
        }
        canvas.redraw();
    }

    private void paint(Event event) {
        GC gc = event.gc;
        FileGrid.Colors colors = browser.fileGrid().colors();
        Rectangle area = canvas.getClientArea();
        gc.setBackground(colors.background());
        gc.fillRectangle(area);
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setInterpolation(SWT.HIGH);
        gc.setForeground(colors.line());
        gc.drawLine(0, 0, 0, area.height);
        gc.setFont(getFont());
        if (mode == Mode.PREVIEW) {
            paintPreview(gc, colors, area);
        } else {
            paintDetails(gc, colors, area);
        }
    }

    private void paintPreview(GC gc, FileGrid.Colors colors, Rectangle area) {
        int margin = px(12);
        if (preview != null) {
            Rectangle b = preview.getBounds();
            int width = area.width - 2 * margin;
            int height = area.height - 2 * margin;
            double scale = Math.min(1.0 * ThemeManager.get(getDisplay()).resources().zoomedPixels(100) / 100,
                    Math.min((double) width / b.width, (double) height / b.height));
            int w = Math.max(1, (int) (b.width * scale));
            int h = Math.max(1, (int) (b.height * scale));
            gc.drawImage(preview, 0, 0, b.width, b.height, area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h);
            return;
        }
        if (previewMessage != null) {
            gc.setForeground(colors.muted());
            List<String> lines = FileGrid.wrap(gc, previewMessage, area.width - 2 * margin, 3);
            int y = area.height / 2 - lines.size() * gc.getFontMetrics().getHeight() / 2;
            for (String line : lines) {
                int w = gc.textExtent(line).x;
                gc.drawText(line, (area.width - w) / 2, y, true);
                y += gc.getFontMetrics().getHeight();
            }
        }
    }

    private void paintDetails(GC gc, FileGrid.Colors colors, Rectangle area) {
        List<String[]> lines = details();
        if (lines.isEmpty()) {
            return;
        }
        int margin = px(16);
        int width = area.width - 2 * margin;
        int y = margin;
        int size = px(96);
        List<FileEntry> selection = browser.getSelectedEntries();
        Image icon;
        Image picture = null;
        if (selection.size() == 1) {
            FileEntry entry = selection.getFirst();
            picture = browser.thumbnails().get(entry, px(192), canvas::redraw);
            icon = browser.icons().get(entry, size);
        } else if (selection.isEmpty()) {
            FileEntry folder = browser.currentEntry();
            icon = folder != null && folder.path().isTop() ? browser.topIcon(size) : browser.icons().get(Icon.FOLDER, size);
        } else {
            icon = browser.icons().get(Icon.FILE, size);
        }
        if (picture != null) {
            Rectangle b = picture.getBounds();
            int box = Math.min(width, px(192));
            double scale = Math.min((double) box / b.width, (double) box / b.height);
            int w = Math.max(1, (int) (b.width * scale));
            int h = Math.max(1, (int) (b.height * scale));
            gc.drawImage(picture, 0, 0, b.width, b.height, (area.width - w) / 2, y, w, h);
            y += h + px(16);
        } else {
            gc.drawImage(icon, (area.width - size) / 2, y);
            y += size + px(16);
        }
        gc.setFont(bold());
        gc.setForeground(colors.foreground());
        for (String line : FileGrid.wrap(gc, lines.get(0)[1], width, 3)) {
            gc.drawText(line, margin, y, true);
            y += gc.getFontMetrics().getHeight();
        }
        gc.setFont(getFont());
        int lineHeight = gc.getFontMetrics().getHeight();
        if (lines.size() > 1 && !lines.get(1)[1].isEmpty()) {
            gc.setForeground(colors.muted());
            gc.drawText(FileGrid.ellipsize(gc, lines.get(1)[1], width), margin, y, true);
            y += lineHeight;
        }
        if (lines.size() > 2) {
            y += px(12);
            gc.setForeground(colors.line());
            gc.drawLine(margin, y, area.width - margin, y);
            y += px(12);
            gc.setForeground(colors.foreground());
            gc.drawText("Properties", margin, y, true);
            y += lineHeight + px(8);
        }
        for (String[] line : lines.subList(Math.min(2, lines.size()), lines.size())) {
            gc.setForeground(colors.muted());
            gc.drawText(FileGrid.ellipsize(gc, line[0], width), margin, y, true);
            y += lineHeight;
            gc.setForeground(colors.foreground());
            for (String part : FileGrid.wrap(gc, line[1], width, 3)) {
                gc.drawText(part, margin, y, true);
                y += lineHeight;
            }
            y += px(8);
        }
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        return new Point(wHint == SWT.DEFAULT ? px(260) : wHint, hHint == SWT.DEFAULT ? px(200) : hHint);
    }
}
