package com.zapthemes.files.ui;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.TransferData;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;

/**
 * Drags entries of any {@link FileSystem} within this process: what a {@link FileBrowser} offers
 * besides {@code FileTransfer} (which only local files can use), so a remote folder can be dropped
 * on another browser, or on an application's own control (a transfer queue, say). The data is a
 * {@link Dragged}; the entries themselves never leave the process: another process reads nothing.
 */
public final class FileEntryTransfer extends ByteArrayTransfer {

    /** What is dragged: entries of one filesystem. */
    public record Dragged(FileSystem fileSystem, List<FileEntry> entries) {

        public Dragged {
            Objects.requireNonNull(fileSystem, "fileSystem");
            entries = List.copyOf(entries);
        }
    }

    private static final String TYPE_NAME = "zapthemes-file-entries";
    private static final int TYPE_ID = registerType(TYPE_NAME);
    private static final FileEntryTransfer INSTANCE = new FileEntryTransfer();

    /** Tells this process's drags from another's. */
    private static final String PROCESS = UUID.randomUUID().toString();

    private static Dragged current;
    private static long serial;

    private FileEntryTransfer() {
    }

    public static FileEntryTransfer getInstance() {
        return INSTANCE;
    }

    @Override
    protected String[] getTypeNames() {
        return new String[] { TYPE_NAME };
    }

    @Override
    protected int[] getTypeIds() {
        return new int[] { TYPE_ID };
    }

    @Override
    protected boolean validate(Object object) {
        return object instanceof Dragged;
    }

    @Override
    protected void javaToNative(Object object, TransferData transferData) {
        if (!validate(object) || !isSupportedType(transferData)) {
            return;
        }
        current = (Dragged) object;
        serial++;
        super.javaToNative(token(serial).getBytes(StandardCharsets.UTF_8), transferData);
    }

    @Override
    protected Object nativeToJava(TransferData transferData) {
        if (!isSupportedType(transferData) || !(super.nativeToJava(transferData) instanceof byte[] bytes)) {
            return null;
        }
        String token = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString().trim();
        // Some platforms pad the data with zeros (trim drops them); anything but this process's
        // latest drag reads as nothing.
        return token.equals(token(serial)) ? current : null;
    }

    private static String token(long number) {
        return PROCESS + ":" + number;
    }
}
