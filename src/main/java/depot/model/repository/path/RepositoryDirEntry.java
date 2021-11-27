package depot.model.repository.path;

import depot.model.base.BaseEditor;
import depot.util.FileUtil;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.text.Collator;
import java.util.*;

public class RepositoryDirEntry {
    private static final String TYPE_CODE_DIR = "DIR";
    private static final String TYPE_CODE_FILE = "FILE";
    private static final String TYPE_CODE_UNKNOWN = "UNKNOWN";

    private static final String TYPE_DIR = "文件夹";
    private static final String TYPE_FILE = "文件";
    private static final String TYPE_UNKNOWN = "未知";

    private static final String ICON_CLASS_DIR = "fas fa-folder";
    private static final String ICON_CLASS_FILE = "far fa-file";

    private static final String ICON_CLASS_FILE_WORD = "far fa-file-word";
    private static final String ICON_CLASS_FILE_EXCEL = "far fa-file-excel";
    private static final String ICON_CLASS_FILE_POWERPOINT = "far fa-file-powerpoint";
    private static final String ICON_CLASS_FILE_PDF = "far fa-file-pdf";
    private static final String ICON_CLASS_FILE_IMAGE = "far fa-file-image";
    private static final String ICON_CLASS_FILE_AUDIO = "far fa-file-audio";
    private static final String ICON_CLASS_FILE_VIDEO = "far fa-file-video";
    private static final String ICON_CLASS_FILE_CODE = "far fa-file-code";
    private static final String ICON_CLASS_FILE_ARCHIVE = "far fa-file-archive";

    private static final HashSet<String> EXT_WORD = extStringToSet("doc,docx");
    private static final HashSet<String> EXT_EXCEL = extStringToSet("xls,xlsx");
    private static final HashSet<String> EXT_POWERPOINT = extStringToSet("ppt,pptx");
    private static final HashSet<String> EXT_PDF = extStringToSet("pdf");
    private static final HashSet<String> EXT_IMAGE = extStringToSet("png,jpg,jpeg,gif");
    private static final HashSet<String> EXT_AUDIO = extStringToSet("mp3");
    private static final HashSet<String> EXT_VIDEO = extStringToSet("mp4");
    private static final HashSet<String> EXT_CODE = extStringToSet("java,py");
    private static final HashSet<String> EXT_ARCHIVE = extStringToSet("zip,rar");

    private static HashSet<String> extStringToSet(String extString) {
        return new HashSet<>(Arrays.asList(extString.split(",")));
    }

    private static String getFileIconClass(String ext) {
        if (ext.length() > 0) {
            if (EXT_WORD.contains(ext)) {
                return ICON_CLASS_FILE_WORD;
            } else if (EXT_EXCEL.contains(ext)) {
                return ICON_CLASS_FILE_EXCEL;
            } else if (EXT_POWERPOINT.contains(ext)) {
                return ICON_CLASS_FILE_POWERPOINT;
            } else if (EXT_PDF.contains(ext)) {
                return ICON_CLASS_FILE_PDF;
            } else if (EXT_IMAGE.contains(ext)) {
                return ICON_CLASS_FILE_IMAGE;
            } else if (EXT_AUDIO.contains(ext)) {
                return ICON_CLASS_FILE_AUDIO;
            } else if (EXT_VIDEO.contains(ext)) {
                return ICON_CLASS_FILE_VIDEO;
            } else if (EXT_CODE.contains(ext)) {
                return ICON_CLASS_FILE_CODE;
            } else if (EXT_ARCHIVE.contains(ext)) {
                return ICON_CLASS_FILE_ARCHIVE;
            }
        }
        return ICON_CLASS_FILE;
    }

    private final SVNDirEntry entry;

    public RepositoryDirEntry(SVNDirEntry entry) {
        this.entry = entry;
    }

    public String getTypeCode() {
        if (entry.getKind() == SVNNodeKind.DIR) {
            return TYPE_CODE_DIR;
        } else if (entry.getKind() == SVNNodeKind.FILE) {
            return TYPE_CODE_FILE;
        } else {
            return TYPE_CODE_UNKNOWN;
        }
    }

    public String getType() {
        switch (getTypeCode()) {
            case TYPE_CODE_DIR:
                return TYPE_DIR;
            case TYPE_CODE_FILE:
                return String.format("%s%s", getExt(), TYPE_FILE);
            default:
                return TYPE_UNKNOWN;
        }
    }

    public boolean isDir() {
        return TYPE_CODE_DIR.equals(getTypeCode());
    }

    public String getIconClass() {
        switch (getTypeCode()) {
            case TYPE_CODE_DIR:
                return ICON_CLASS_DIR;
            case TYPE_CODE_FILE:
                return getFileIconClass(getExt());
            default:
                return "";
        }
    }

    @Override
    public String toString() {
        return entry.getName();
    }

    public String getName() {
        return entry.getName();
    }

    public Date getDate() {
        return entry.getDate();
    }

    public String getMtime() {
        Date date = getDate();
        return String.format("%tF %tT", date, date);
    }

    public String getExt() {
        String name = getName();
        int rIdx = name.lastIndexOf('.');
        return rIdx >= 0 ? name.substring(rIdx + 1) : "";
    }

    public long getSize() {
        return entry.getSize();
    }

    public String getSizeString() {
        return TYPE_CODE_FILE.equals(getTypeCode()) ? FileUtil.getSizeString(getSize()) : "-";
    }

    /**
     * 元数据查询
     */
    private static class RepositoryRevision {
        private final String repositoryUUID;
        private final long revision;

        public RepositoryRevision(SVNRepository repository, long revision) throws SVNException {
            this.repositoryUUID = repository.getRepositoryUUID(true);
            this.revision = revision;
        }

        public String getRepositoryUUID() {
            return repositoryUUID;
        }

        public long getRevision() {
            return revision;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RepositoryRevision that = (RepositoryRevision) o;
            return revision == that.revision && Objects.equals(repositoryUUID, that.repositoryUUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repositoryUUID, revision);
        }
    }

    private static final Map<RepositoryRevision, Map<RepositoryPathNode, String>> CHECKSUM_MAP_CACHE = new HashMap<>();
    private static final Map<RepositoryRevision, Map<RepositoryPathNode, List<SVNDirEntry>>> TRAVERSE_CACHE = new HashMap<>();

    public static String getChecksum(SVNRepository repository, RepositoryPathNode pathNode) {
        try {
            SVNProperties properties = new SVNProperties();
            repository.getFile(pathNode.toString(), -1, properties, null);
            return properties.getStringValue(SVNProperty.CHECKSUM);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map<RepositoryPathNode, String> getChecksumMap(SVNRepository repository) throws Exception {
        long revision = repository.getLatestRevision();
        RepositoryRevision repositoryRevision = new RepositoryRevision(repository, revision);
        if (CHECKSUM_MAP_CACHE.containsKey(repositoryRevision)) {
            return CHECKSUM_MAP_CACHE.get(repositoryRevision);
        }

        Map<RepositoryPathNode, String> checksumMap = new HashMap<>();
        repository.status(revision, "", true, reporter -> {
            reporter.setPath("", null, revision, SVNDepth.INFINITY, true);
            reporter.finishReport();
        }, new BaseEditor() {
            @Override
            public void closeFile(String path, String textChecksum) {
                checksumMap.put(new RepositoryPathNode().resolve(path), textChecksum);
            }
        });

        CHECKSUM_MAP_CACHE.put(repositoryRevision, checksumMap);
        for (RepositoryRevision item : CHECKSUM_MAP_CACHE.keySet()) {
            if (Objects.equals(item.getRepositoryUUID(), repository.getRepositoryUUID(true))
                    && item.getRevision() < revision) {
                CHECKSUM_MAP_CACHE.remove(item);
            }
        }

        return checksumMap;
    }

    public interface DirEntryReceiver {
        void receive(RepositoryPathNode pathNode, SVNDirEntry entry) throws Exception;
    }

    public static void traverse(SVNRepository repository, RepositoryPathNode pathNode, DirEntryReceiver receiver) throws Exception {
        long revision = repository.getLatestRevision();
        RepositoryRevision repositoryRevision = new RepositoryRevision(repository, revision);

        final List<SVNDirEntry> entryList = new LinkedList<>();
        if (TRAVERSE_CACHE.containsKey(repositoryRevision)
                && TRAVERSE_CACHE.get(repositoryRevision).containsKey(pathNode)) {
            entryList.addAll(TRAVERSE_CACHE.get(repositoryRevision).get(pathNode));
        } else {
            SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
            svnOperationFactory.setAuthenticationManager(repository.getAuthenticationManager());
            SvnList svnList = svnOperationFactory.createList();
            svnList.setDepth(SVNDepth.INFINITY);
            svnList.setSingleTarget(SvnTarget.fromURL(repository.getRepositoryRoot(true).appendPath(pathNode.toString(), true)));
            svnList.setReceiver((target, object) -> entryList.add(object));
            svnList.run();
        }

        entryList.forEach(entry -> {
            RepositoryPathNode currentPathNode = new RepositoryPathNode()
                    .resolve(entry.getURL().toDecodedString()
                            .substring(entry.getRepositoryRoot().toDecodedString().length()));
            entry.setName(currentPathNode.getName());
            try {
                receiver.receive(currentPathNode, entry);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        TRAVERSE_CACHE.computeIfAbsent(repositoryRevision, k -> new HashMap<>())
                .putIfAbsent(pathNode, entryList);
        for (RepositoryRevision item : TRAVERSE_CACHE.keySet()) {
            if (Objects.equals(item.getRepositoryUUID(), repository.getRepositoryUUID(true))
                    && item.getRevision() < revision) {
                TRAVERSE_CACHE.remove(item);
            }
        }
    }

    /**
     * 比较器
     */
    private static final String STARTS_WITH_LETTER_OR_DIGIT = "^\\w";
    private static final Collator CHINESE_COMPARATOR = Collator.getInstance(Locale.CHINA);

    private static int nameCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.getName().matches(STARTS_WITH_LETTER_OR_DIGIT)
                || o2.getName().matches(STARTS_WITH_LETTER_OR_DIGIT)) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
        return CHINESE_COMPARATOR.compare(o1.getName(), o2.getName());
    }

    private static int mtimeCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        return o1.getMtime().compareTo(o2.getMtime());
    }

    private static int typeCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        return o1.getType().compareTo(o2.getType());
    }

    public static int entryNameCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? nameCompare(o1, o2) : -1;
        } else {
            return !o2.isDir() ? nameCompare(o1, o2) : 1;
        }
    }

    public static int entryMtimeCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? mtimeCompare(o1, o2) : -1;
        } else {
            return !o2.isDir() ? mtimeCompare(o1, o2) : 1;
        }
    }

    public static int entryTypeCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (typeCompare(o1, o2) == 0) {
            return nameCompare(o1, o2);
        }
        return typeCompare(o1, o2);
    }

    public static int entrySizeCompare(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? nameCompare(o1, o2) : -1;
        } else {
            return !o2.isDir() ? Long.compare(o1.getSize(), o2.getSize()) : 1;
        }
    }

    public static int entryNameCompareRev(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? nameCompare(o2, o1) : -1;
        } else {
            return !o2.isDir() ? nameCompare(o2, o1) : 1;
        }
    }

    public static int entryMtimeCompareRev(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? mtimeCompare(o2, o1) : -1;
        } else {
            return !o2.isDir() ? mtimeCompare(o2, o1) : 1;
        }
    }

    public static int entryTypeCompareRev(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (typeCompare(o1, o2) == 0) {
            return nameCompare(o1, o2);
        }
        return typeCompare(o2, o1);
    }

    public static int entrySizeCompareRev(RepositoryDirEntry o1, RepositoryDirEntry o2) {
        if (o1.isDir()) {
            return o2.isDir() ? nameCompare(o1, o2) : -1;
        } else {
            return !o2.isDir() ? Long.compare(o2.getSize(), o1.getSize()) : 1;
        }
    }
}
