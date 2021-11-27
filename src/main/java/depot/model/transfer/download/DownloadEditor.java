package depot.model.transfer.download;

import depot.MainApp;
import depot.model.base.AppSettings;
import depot.model.base.BaseEditor;
import depot.model.repository.path.RepositoryPathNode;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;

public class DownloadEditor extends BaseEditor {
    private static final String TEMP_SUFFIX = ("." + MainApp.APP_NAME + "downloading").toLowerCase(Locale.ROOT);

    private final SVNDeltaProcessor deltaProcessor = new SVNDeltaProcessor();
    private final RepositoryPathNode parentPathNode;
    private final File downloadParent;
    private final LinkedList<File> newEntries = new LinkedList<>();
    private String lastCheckSum;
    private long lastReceivedTotal;
    private ReceiveListener receiveListener;

    public DownloadEditor(RepositoryPathNode parentPathNode, File downloadParent) {
        this.parentPathNode = parentPathNode != null ? parentPathNode : new RepositoryPathNode();
        this.downloadParent = downloadParent != null ? downloadParent : AppSettings.load().getDownloadParent();
    }

    private File getDownloadTarget(String srcPathString) throws SVNException {
        Path parentPath = parentPathNode.getPath();
        Path srcPath = Paths.get("/").resolve(srcPathString);
        if (!srcPath.startsWith(parentPath) || parentPath.startsWith(srcPath)) {
            throwSVNException("目标路径不在下载范围内：" + srcPathString);
        }
        return new File(downloadParent, parentPathNode.relativize(srcPath).toString());
    }

    private File getTempDownloadTarget(String srcPathString) throws SVNException {
        File downloadTarget = getDownloadTarget(srcPathString);
        return new File(downloadTarget.getParent(), downloadTarget.getName() + TEMP_SUFFIX);
    }

    private String getAnotherFileName(String fileName, int num) {
        int lastDotIdx = fileName.lastIndexOf('.');
        String fileNameBody = lastDotIdx >= 0 ? fileName.substring(0, lastDotIdx) : fileName;
        String fileSuffix = lastDotIdx >= 0 ? fileName.substring(lastDotIdx) : "";
        if (fileNameBody.length() > 0) {
            fileNameBody += " ";
        }
        return String.format("%s(%d)%s", fileNameBody, num, fileSuffix);
    }

    private void throwSVNException(String msg) throws SVNException {
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CMD_ERR),
                new Exception(msg), SVNLogType.DEFAULT);
    }

    @Override
    public void openRoot(long revision) throws SVNException {
        if (!downloadParent.isDirectory()) {
            if (!downloadParent.mkdirs()) {
                throwSVNException("下载文件夹创建失败：" + downloadParent.getAbsolutePath());
            }
        }
    }

    @Override
    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        File newTempFile = getTempDownloadTarget(path);
        if (newTempFile.exists()) {
            throwSVNException("临时文件已存在：" + newTempFile.getAbsolutePath());
        }
        LinkedList<File> newDirs = new LinkedList<>();
        for (File parent = newTempFile.getParentFile();
             parent != null && !parent.equals(downloadParent) && !parent.isDirectory();
             parent = parent.getParentFile()) {
            newDirs.addFirst(parent);
        }
        newDirs.forEach(newEntries::addFirst);
        for (File newDir : newDirs) {
            if (!newDir.mkdir()) {
                throwSVNException("文件夹创建失败：" + newDir.getAbsolutePath());
            }
        }
        newEntries.addFirst(newTempFile);
        try {
            newTempFile.createNewFile();
        } catch (IOException e) {
            throwSVNException("临时文件创建失败：" + newTempFile.getAbsolutePath());
        }
    }

    @Override
    public void closeFile(String path, String textChecksum) throws SVNException {
        if (!Objects.equals(lastCheckSum, textChecksum)) {
            throwSVNException("下载文件校验失败：" + path);
        }
        File newTempFile = getTempDownloadTarget(path);
        File newFile = getDownloadTarget(path);
        if (!newTempFile.isFile()) {
            throwSVNException("临时文件不存在：" + newTempFile.getAbsolutePath());
        }
        if (newFile.exists()) {
            File parent = newFile.getParentFile();
            String fileName = newFile.getName();
            int num = 1;
            while (newFile.exists()) {
                newFile = new File(parent, getAnotherFileName(fileName, num++));
            }
        }
        newEntries.addFirst(newFile);
        if (!newTempFile.renameTo(newFile)) {
            throwSVNException("文件下载失败：" + path);
        }
        newEntries.remove(newTempFile);
    }

    @Override
    public SVNCommitInfo closeEdit() {
        return null;
    }

    @Override
    public void abortEdit() {
        for (File entry : newEntries) {
            if (entry.exists()) {
                File[] children = entry.listFiles();
                if (!(children != null && children.length > 0)) {
                    entry.delete();
                }
            }
        }
    }

    @Override
    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        lastReceivedTotal = 0;
        if (receiveListener != null) {
            receiveListener.handle(lastReceivedTotal);
        }
        deltaProcessor.applyTextDelta((File) null, getTempDownloadTarget(path), true);
    }

    @Override
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        lastReceivedTotal += diffWindow.getNewDataLength();
        if (receiveListener != null) {
            receiveListener.handle(lastReceivedTotal);
        }
        return deltaProcessor.textDeltaChunk(diffWindow);
    }

    @Override
    public void textDeltaEnd(String path) {
        lastCheckSum = deltaProcessor.textDeltaEnd();
    }

    public interface ReceiveListener {
        void handle(long newReceivedTotal);
    }

    public void setReceiveListener(ReceiveListener receiveListener) {
        this.receiveListener = receiveListener;
    }
}
