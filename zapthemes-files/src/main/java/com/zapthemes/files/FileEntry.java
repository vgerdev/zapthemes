package com.zapthemes.files;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One file, folder or drive as a {@link FileSystem} lists it. Build one with {@link #builder}.
 *
 * @param path          where it is
 * @param kind          what it is; a link has its target's kind
 * @param label         the name to show, when it differs from the path's last segment ("OS (C:)"
 *                      for {@code C:\}); else {@code null}
 * @param size          the size in bytes, or -1 if unknown (folders)
 * @param modified      the last modification, or {@code null}
 * @param timePrecision how exact {@code modified} is (FTP listings give minutes or days), or
 *                      {@code null} if exact
 * @param permissions   POSIX permissions as {@code "rwxr-xr-x"}, or {@code null}
 * @param owner         the owner's name or number, or {@code null}
 * @param group         the group's name or number, or {@code null}
 * @param hidden        whether it is hidden (a DOS attribute on Windows, a leading dot elsewhere)
 * @param link          whether it is a symbolic link
 * @param linkTarget    where the link points, or {@code null}
 * @param attributes    anything else a filesystem adds (an S3 storage class, say); never {@code null}
 */
public record FileEntry(FilePath path, EntryKind kind, String label, long size, Instant modified, Duration timePrecision,
        String permissions, String owner, String group, boolean hidden, boolean link, String linkTarget,
        Map<String, Object> attributes) {

    public FileEntry {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** A builder for an entry at {@code path} of {@code kind}. */
    public static Builder builder(FilePath path, EntryKind kind) {
        return new Builder(path, kind);
    }

    /** The path's last segment. */
    public String name() {
        return path.name();
    }

    /** What to show for it: the label, else the name. */
    public String displayName() {
        return label != null ? label : path.name();
    }

    /** Whether it opens like a folder. */
    public boolean isContainer() {
        return kind.isContainer();
    }

    /** A builder that starts from this entry. */
    public Builder toBuilder() {
        return new Builder(path, kind).label(label).size(size).modified(modified).timePrecision(timePrecision)
                .permissions(permissions).owner(owner).group(group).hidden(hidden).link(link).linkTarget(linkTarget)
                .attributes(attributes);
    }

    /** This entry at another path (a {@link RootedFileSystem} maps paths). */
    public FileEntry withPath(FilePath other) {
        return new FileEntry(other, kind, label, size, modified, timePrecision, permissions, owner, group, hidden, link,
                linkTarget, attributes);
    }

    /** Builds a {@link FileEntry}. */
    public static final class Builder {
        private final FilePath path;
        private final EntryKind kind;
        private String label;
        private long size = -1;
        private Instant modified;
        private Duration timePrecision;
        private String permissions;
        private String owner;
        private String group;
        private boolean hidden;
        private boolean link;
        private String linkTarget;
        private Map<String, Object> attributes = Map.of();

        private Builder(FilePath path, EntryKind kind) {
            this.path = path;
            this.kind = kind;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder modified(Instant modified) {
            this.modified = modified;
            return this;
        }

        public Builder timePrecision(Duration timePrecision) {
            this.timePrecision = timePrecision;
            return this;
        }

        public Builder permissions(String permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder link(boolean link) {
            this.link = link;
            return this;
        }

        public Builder linkTarget(String linkTarget) {
            this.linkTarget = linkTarget;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public FileEntry build() {
            return new FileEntry(path, kind, label, size, modified, timePrecision, permissions, owner, group, hidden, link,
                    linkTarget, attributes);
        }
    }
}
