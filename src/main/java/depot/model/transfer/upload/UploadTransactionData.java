package depot.model.transfer.upload;

import com.google.common.base.Preconditions;
import depot.model.repository.path.RepositoryDirEntry;
import depot.model.transfer.base.BaseTransferData;
import depot.util.FileUtil;
import depot.util.StringUtil;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.collections.CollectionUtils;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.util.*;

@NoArgsConstructor
@Accessors(chain = true)
public class UploadTransactionData extends BaseTransferData {

    @Setter
    private Task<Void> task;

    @Setter
    private SVNRepository repository;

    @Getter
    @Setter
    private List<File> dirList = new LinkedList<>();

    @Getter
    @Setter
    private List<File> fileList = new LinkedList<>();

    @Setter
    private Map<File, String> uploadPathMap;

    @Setter
    private ChecksumProgressHandler checksumProgressHandler;

    private final Map<File, SVNNodeKind> kindMap = new HashMap<>();
    private final Map<File, Long> fileSizeMap = new HashMap<>();
    private final Map<File, Long> prevSizeMap = new HashMap<>();

    public UploadTransactionData build() throws Exception {
        Preconditions.checkNotNull(repository);
        Preconditions.checkNotNull(uploadPathMap);

        HashSet<String> uploadPaths = new HashSet<>();
        if (CollectionUtils.isNotEmpty(dirList)) {
            for (Iterator<File> iterator = dirList.iterator(); iterator.hasNext(); ) {
                File dir = iterator.next();

                if (task != null && task.isCancelled()) {
                    throw new UploadCancelledException();
                }

                Preconditions.checkArgument(dir.isDirectory() && dir.canRead(), "不能上传文件夹：" + dir.getCanonicalPath());

                String uploadPath = uploadPathMap.get(dir);
                Preconditions.checkArgument(StringUtil.notEmpty(uploadPath));
                Preconditions.checkArgument(uploadPaths.add(uploadPath), "多个相同的上传路径：" + uploadPath);

                SVNNodeKind kind = repository.checkPath(uploadPath, -1);
                Preconditions.checkArgument(kind != SVNNodeKind.FILE, "存在同名文件，不能上传文件夹：" + dir.getCanonicalPath());

                if (kind == SVNNodeKind.DIR) {
                    // 已有文件夹不需要创建
                    iterator.remove();
                    continue;
                }

                kindMap.put(dir, kind);
            }
        }
        totalSize = 0;
        if (CollectionUtils.isNotEmpty(fileList)) {
            int fileIdx = 0, fileCount = fileList.size();
            for (Iterator<File> iterator = fileList.iterator(); iterator.hasNext(); fileIdx++) {
                File file = iterator.next();

                if (task != null && task.isCancelled()) {
                    throw new UploadCancelledException();
                }

                Preconditions.checkArgument(file.isFile() && file.canRead(), "不能上传文件：" + file.getCanonicalPath());

                String uploadPath = uploadPathMap.get(file);
                Preconditions.checkArgument(StringUtil.notEmpty(uploadPath));
                Preconditions.checkArgument(uploadPaths.add(uploadPath), "多个相同的上传路径：" + uploadPath);

                SVNDirEntry entry = RepositoryDirEntry.getEntry(repository, uploadPath);
                if (entry != null) {
                    Preconditions.checkArgument(entry.getKind() != SVNNodeKind.DIR, "存在同名文件夹，不能上传文件：" + file.getCanonicalPath());

                    if (entry.getKind() == SVNNodeKind.FILE) {
                        if (file.length() == entry.getSize()) {
                            // 相同大小文件，计算校验和，避免重复上传
                            String entryChecksum = RepositoryDirEntry.getChecksum(repository, uploadPath);
                            if (StringUtil.notEmpty(entryChecksum)) {
                                int itemIdx = fileIdx;
                                String fileChecksum = FileUtil.getChecksum(file, (pSize, tSize) -> {
                                    if (checksumProgressHandler != null) {
                                        checksumProgressHandler.handle(pSize, tSize,
                                                itemIdx, fileCount, file.getName());
                                    }
                                });
                                if (entryChecksum.equals(fileChecksum)) {
                                    // 重复文件不需要上传
                                    iterator.remove();
                                    continue;
                                }
                            }
                        }
                    }
                    kindMap.put(file, entry.getKind());
                }
                fileSizeMap.put(file, file.length());
                prevSizeMap.put(file, totalSize);
                totalSize += file.length();
            }
        }
        return this;
    }

    public interface ChecksumProgressHandler {
        void handle(long processedSize, long totalSize, int itemIdx, int itemCount, String itemName);
    }

    public boolean isEmpty() {
        return CollectionUtils.isEmpty(dirList) && CollectionUtils.isEmpty(fileList);
    }

    public SVNNodeKind getKind(File file) {
        return kindMap.getOrDefault(file, SVNNodeKind.NONE);
    }

    public long getSize(File file) {
        return fileSizeMap.getOrDefault(file, 0L);
    }

    public long getPrevSize(File file) {
        return prevSizeMap.getOrDefault(file, 0L);
    }
}
