package depot.model.repository.sync;

import depot.model.repository.path.RepositoryPathNode;
import depot.util.FileUtil;
import org.tmatesoft.svn.core.SVNNodeKind;

import java.util.Date;
import java.util.Objects;

public class RepositoryCompareEntry {

    private final RepositoryPathNode parentPathNode;
    private final String name;
    private EntryInfo sourceEntryInfo;
    private EntryInfo targetEntryInfo;

    public static class EntryInfo {

        private final SVNNodeKind kind;
        private final Date date;
        private final long size;
        private final String checksum;

        protected EntryInfo(SVNNodeKind kind, Date date, long size, String checksum) {
            this.kind = kind;
            this.date = date;
            this.size = size;
            this.checksum = checksum;
        }

        public static EntryInfo newFileEntryInfo(Date date, long size, String checksum) {
            return new EntryInfo(SVNNodeKind.FILE, date, size, checksum);
        }

        public static EntryInfo newDirEntryInfo(Date date) {
            return new EntryInfo(SVNNodeKind.DIR, date, -1, null);
        }

        public SVNNodeKind getKind() {
            return kind;
        }

        public Date getDate() {
            return date;
        }

        public String getMtime() {
            return String.format("%tF %tT", date, date);
        }

        public long getSize() {
            return size;
        }

        public String getSizeString() {
            return size >= 0 ? String.format("%d B (%s)", size, FileUtil.getSizeString(size)) : "-";
        }

        public String getChecksum() {
            return checksum;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, size, checksum);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EntryInfo
                    && ((EntryInfo) obj).kind == kind
                    && ((EntryInfo) obj).size == size
                    && Objects.equals(((EntryInfo) obj).checksum, checksum);
        }
    }

    public RepositoryCompareEntry(RepositoryPathNode parentPathNode, String name) {
        this.parentPathNode = parentPathNode;
        this.name = name;
    }

    public RepositoryPathNode getParentPathNode() {
        return parentPathNode;
    }

    public String getName() {
        return name;
    }

    public EntryInfo getSourceEntryInfo() {
        return sourceEntryInfo;
    }

    public void setSourceEntryInfo(EntryInfo sourceEntryInfo) {
        this.sourceEntryInfo = sourceEntryInfo;
    }

    public EntryInfo getTargetEntryInfo() {
        return targetEntryInfo;
    }

    public void setTargetEntryInfo(EntryInfo targetEntryInfo) {
        this.targetEntryInfo = targetEntryInfo;
    }
}
