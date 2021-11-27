package depot.model.transfer.upload;

import depot.model.transfer.base.BaseTransferData;
import javafx.concurrent.Task;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.util.*;

public class UploadTransactionData extends BaseTransferData {

    private final List<File> dirList;
    private final List<File> fileList;
    private final Map<File, SVNNodeKind> kindMap = new HashMap<>();
    private final Map<File, Long> fileSizeMap = new HashMap<>();
    private final Map<File, Long> prevSizeMap = new HashMap<>();

    public UploadTransactionData(SVNRepository repository, List<File> dirList, List<File> fileList,
                                 Map<File, String> uploadPathMap, Task<Void> task) throws Exception {
        this.dirList = dirList != null ? dirList : new LinkedList<>();
        this.fileList = fileList != null ? fileList : new LinkedList<>();
        if (this.dirList.isEmpty() && this.fileList.isEmpty()) {
            throw new Exception("上传队列为空");
        }
        HashSet<String> uploadPaths = new HashSet<>();
        for (File dir : this.dirList) {
            if (task.isCancelled()) {
                throw new UploadCancelledException();
            }
            if (!dir.isDirectory() || !dir.canRead()) {
                throw new Exception("不能上传文件夹：" + dir.getCanonicalPath());
            }
            String uploadPath = uploadPathMap.get(dir);
            if (!uploadPaths.add(uploadPath)) {
                throw new Exception("多个相同的上传路径：" + uploadPath);
            }
            SVNNodeKind kind = repository.checkPath(uploadPath, -1);
            if (kind == SVNNodeKind.FILE) {
                throw new Exception("存在同名文件，不能上传文件夹：" + dir.getCanonicalPath());
            }
            kindMap.put(dir, kind);
        }
        totalSize = 0;
        for (File file : this.fileList) {
            if (task.isCancelled()) {
                throw new UploadCancelledException();
            }
            if (!file.isFile() || !file.canRead()) {
                throw new Exception("不能上传文件：" + file.getCanonicalPath());
            }
            String uploadPath = uploadPathMap.get(file);
            if (!uploadPaths.add(uploadPath)) {
                throw new Exception("多个相同的上传路径：" + uploadPath);
            }
            SVNNodeKind kind = repository.checkPath(uploadPath, -1);
            if (kind == SVNNodeKind.DIR) {
                throw new Exception("存在同名文件夹，不能上传文件：" + file.getCanonicalPath());
            }
            kindMap.put(file, kind);
            fileSizeMap.put(file, file.length());
            prevSizeMap.put(file, totalSize);
            totalSize += file.length();
        }
    }

    public List<File> dirList() {
        return dirList;
    }

    public List<File> fileList() {
        return fileList;
    }

    public int lengthOfDirs() {
        return dirList.size();
    }

    public int lengthOfFiles() {
        return fileList.size();
    }

    public int indexOfDir(File dir) {
        return dirList.indexOf(dir);
    }

    public int indexOfFile(File file) {
        return fileList.indexOf(file);
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
