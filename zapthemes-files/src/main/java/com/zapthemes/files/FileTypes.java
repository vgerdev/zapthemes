package com.zapthemes.files;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Type names ("File folder", "Word document", "ZIP archive") and icon categories by file name
 * extension, the same on every platform, plus Explorer-style sizes ("1,042 KB").
 */
public final class FileTypes {

    /** The icon a file gets. */
    public enum Category {
        FOLDER, DRIVE, FILE, TEXT, DOCUMENT, SPREADSHEET, PRESENTATION, PDF, IMAGE, AUDIO, VIDEO, ARCHIVE, CODE, EXECUTABLE
    }

    private record Type(String name, Category category) {
    }

    private static final Map<String, Type> BY_EXTENSION = new HashMap<>();

    static {
        add(Category.TEXT, "Text document", "txt");
        add(Category.TEXT, "Log file", "log");
        add(Category.TEXT, "Markdown file", "md", "markdown");
        add(Category.TEXT, "CSV file", "csv");
        add(Category.DOCUMENT, "Word document", "docx", "doc", "dotx");
        add(Category.DOCUMENT, "Rich text document", "rtf");
        add(Category.DOCUMENT, "OpenDocument text", "odt");
        add(Category.SPREADSHEET, "Excel worksheet", "xlsx", "xls", "xlsm");
        add(Category.SPREADSHEET, "OpenDocument spreadsheet", "ods");
        add(Category.PRESENTATION, "PowerPoint presentation", "pptx", "ppt");
        add(Category.PRESENTATION, "OpenDocument presentation", "odp");
        add(Category.PDF, "PDF document", "pdf");
        add(Category.IMAGE, "PNG image", "png");
        add(Category.IMAGE, "JPEG image", "jpg", "jpeg");
        add(Category.IMAGE, "GIF image", "gif");
        add(Category.IMAGE, "WebP image", "webp");
        add(Category.IMAGE, "SVG image", "svg");
        add(Category.IMAGE, "Bitmap image", "bmp");
        add(Category.IMAGE, "Icon", "ico");
        add(Category.IMAGE, "TIFF image", "tif", "tiff");
        add(Category.AUDIO, "MP3 audio", "mp3");
        add(Category.AUDIO, "WAV audio", "wav");
        add(Category.AUDIO, "FLAC audio", "flac");
        add(Category.AUDIO, "AAC audio", "m4a", "aac");
        add(Category.AUDIO, "Ogg audio", "ogg");
        add(Category.VIDEO, "MP4 video", "mp4", "m4v");
        add(Category.VIDEO, "Matroska video", "mkv");
        add(Category.VIDEO, "AVI video", "avi");
        add(Category.VIDEO, "QuickTime movie", "mov");
        add(Category.VIDEO, "WebM video", "webm");
        add(Category.ARCHIVE, "ZIP archive", "zip");
        add(Category.ARCHIVE, "Compressed archive", "7z", "rar", "gz", "tgz", "bz2", "xz", "zst");
        add(Category.ARCHIVE, "TAR archive", "tar");
        add(Category.ARCHIVE, "Java archive", "jar", "war");
        add(Category.ARCHIVE, "Disc image", "iso", "img", "dmg");
        add(Category.CODE, "Java source file", "java");
        add(Category.CODE, "Kotlin source file", "kt", "kts");
        add(Category.CODE, "Python file", "py");
        add(Category.CODE, "JavaScript file", "js", "mjs");
        add(Category.CODE, "TypeScript file", "ts", "tsx");
        add(Category.CODE, "C source file", "c", "h");
        add(Category.CODE, "C++ source file", "cpp", "hpp", "cc");
        add(Category.CODE, "C# source file", "cs");
        add(Category.CODE, "Go source file", "go");
        add(Category.CODE, "Rust source file", "rs");
        add(Category.CODE, "HTML document", "html", "htm");
        add(Category.CODE, "CSS file", "css");
        add(Category.CODE, "JSON file", "json");
        add(Category.CODE, "XML file", "xml");
        add(Category.CODE, "YAML file", "yml", "yaml");
        add(Category.CODE, "TOML file", "toml");
        add(Category.CODE, "Properties file", "properties");
        add(Category.CODE, "Shell script", "sh", "bash", "zsh");
        add(Category.CODE, "Windows batch file", "bat", "cmd");
        add(Category.CODE, "PowerShell script", "ps1");
        add(Category.CODE, "SQL file", "sql");
        add(Category.EXECUTABLE, "Application", "exe", "app");
        add(Category.EXECUTABLE, "Windows installer package", "msi");
        add(Category.EXECUTABLE, "Application extension", "dll", "so", "dylib");
    }

    private FileTypes() {
    }

    private static void add(Category category, String name, String... extensions) {
        for (String extension : extensions) {
            BY_EXTENSION.put(extension, new Type(name, category));
        }
    }

    /** The extension of {@code name} in lower case, without the dot, or {@code ""}. A leading dot alone is not one. */
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "File folder", "Local disk", "Word document"; "XYZ file" for an unknown extension, "File" for none. */
    public static String typeName(FileEntry entry) {
        return switch (entry.kind()) {
            case DIRECTORY -> "File folder";
            case DRIVE -> "Local disk";
            case OTHER -> entry.link() ? "Shortcut" : "System file";
            case FILE -> {
                String extension = extension(entry.name());
                Type type = BY_EXTENSION.get(extension);
                yield type != null ? type.name : extension.isEmpty() ? "File" : extension.toUpperCase(Locale.ROOT) + " file";
            }
        };
    }

    /** The icon category of {@code entry}. */
    public static Category category(FileEntry entry) {
        return switch (entry.kind()) {
            case DIRECTORY -> Category.FOLDER;
            case DRIVE -> Category.DRIVE;
            case OTHER -> Category.FILE;
            case FILE -> {
                Type type = BY_EXTENSION.get(extension(entry.name()));
                yield type != null ? type.category : Category.FILE;
            }
        };
    }

    /**
     * The size as Explorer's Details view shows it: whole kilobytes, rounded up, with grouping
     * ({@code "1,042 KB"}, {@code "0 KB"} for an empty file); {@code ""} if unknown.
     */
    public static String detailsSize(long bytes) {
        if (bytes < 0) {
            return "";
        }
        long kb = (bytes + 1023) / 1024;
        return NumberFormat.getIntegerInstance().format(kb) + " KB";
    }

    /** A short size: {@code "512 bytes"}, {@code "1.5 KB"}, {@code "3.2 MB"}, {@code "1.1 GB"}. */
    public static String shortSize(long bytes) {
        if (bytes < 1024) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        String[] units = { "KB", "MB", "GB", "TB", "PB" };
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(value < 10 ? 1 : 0);
        return format.format(value) + " " + units[unit];
    }
}
