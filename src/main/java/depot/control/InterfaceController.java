package depot.control;

import com.google.common.base.Preconditions;
import depot.MainApp;
import depot.model.base.AppSettings;
import depot.model.repository.config.BaiduPanConfig;
import depot.model.repository.config.ComparableRepositoryConfig;
import depot.model.repository.config.RepositoryConfig;
import depot.model.repository.content.RepositoryContentData;
import depot.model.repository.log.RepositoryLogData;
import depot.model.repository.path.*;
import depot.model.repository.sync.SyncCancelledException;
import depot.model.transfer.download.DownloadCancelledException;
import depot.model.transfer.download.DownloadTask;
import depot.model.transfer.download.DownloadTransactionData;
import depot.model.transfer.upload.UploadCancelledException;
import depot.model.transfer.upload.UploadTransactionData;
import depot.util.FileUtil;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.WindowEvent;
import netscape.javascript.JSObject;
import org.apache.commons.collections.CollectionUtils;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.replicator.ISVNReplicationHandler;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryDump;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryLoad;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryVerify;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.FileInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InterfaceController extends BaseController {
    private static final String TITLE = MainApp.APP_NAME;
    private static final int WIDTH = 900;
    private static final int HEIGHT = 640;
    private static final String HTML_PATH = "view/html/interface.html";

    private final JavaApi javaApi = new JavaApi();
    private final RepositoryPath path = new RepositoryPath();

    private RepositoryConfig repositoryConfig;
    private SVNRepository repository;

    @FXML
    private WebView webView;

    private WebEngine webEngine;

    @FXML
    public void initialize() {
        webEngine = webView.getEngine();
        webEngine.load(Objects.requireNonNull(MainApp.class.getResource(HTML_PATH)).toExternalForm());
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                getWindow().setMember("javaApi", javaApi);
                getWindow().call("switchToSidebarTabWithDelay", "sidebar-repo-home");
            }
        });

        webView.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                getWindow().call("dragOver", (int) event.getX(), (int) event.getY());
            } else {
                event.consume();
            }
        });
        webView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                JSObject target = (JSObject) getWindow().call("getDropTarget", (int) event.getX(), (int) event.getY());
                String parentPathString = (String) target.getMember("pathString");
                if (parentPathString != null) {
                    javaApi.uploadItems(db.getFiles(), path.resolve(parentPathString).getPathNode());
                }
            }
            getWindow().call("dragEnd");
            event.setDropCompleted(success);
            event.consume();
        });
        webView.setOnMouseReleased(event -> getWindow().call("dragEnd"));
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    private JSObject getWindow() {
        return (JSObject) webEngine.executeScript("window");
    }

    public class JavaApi extends BaseJavaApi {

        /**
         * ????????????
         */
        private final RepositoryContentData repositoryContentData = new RepositoryContentData();
        private RepositoryLogData repositoryLogData;
        private UploadTransactionData uploadTransactionData;
        private ExecutorService compressExecutor;

        /**
         * ????????????
         */
        private LinkedList<String> convertJSStringArray(JSObject array, int length) {
            LinkedList<String> list = new LinkedList<>();
            for (int idx = 0; idx < length; ) {
                list.add(String.valueOf(array.getSlot(idx++)));
            }
            return list;
        }

        private void serviceCleanup() {
            uploadTransactionData = null;
            if (compressExecutor != null) {
                compressExecutor.shutdownNow();
                try {
                    if (!compressExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        warn("????????????????????? [??????]");
                    }
                } catch (InterruptedException e) {
                    warn("????????????????????? [??????]");
                    e.printStackTrace();
                } finally {
                    compressExecutor = null;
                }
            }
            mainApp.hideProgress();
            webView.setDisable(false);
            getWindow().call("switchRepoNavOps", "repo-nav-ops-refresh", true);
        }

        private ExclusiveService buildNonInteractiveService(Service<?> service, String creationFailedMsg) {
            return new ExclusiveService() {
                @Override
                protected Service<?> createService() {
                    webView.setDisable(true);
                    return service;
                }

                @Override
                protected void onCreationFailed(Exception e) {
                    error(creationFailedMsg, e);
                    serviceCleanup();
                }
            };
        }

        private void cancelExclusiveService(WindowEvent event, String confirmMsg) {
            event.consume();
            if (service != null && service.isRunning()
                    && (confirmMsg == null || confirm(confirmMsg))) {
                service.cancel();
            }
        }

        /**
         * ???????????????
         */
        private class LoadRepositoryContentService extends Service<Void> {

            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() {
                        try {
                            repositoryContentData.pathNodeList = path.getPathNodeList();
                            if (repository.checkPath(path.toString(), -1) == SVNNodeKind.DIR) {
                                ArrayList<SVNDirEntry> entryList = new ArrayList<>();
                                repository.getDir(path.toString(), -1, null, entryList);
                                repositoryContentData.entryList = entryList.stream()
                                        .map(RepositoryDirEntry::new)
                                        .collect(Collectors.toList());
                                Platform.runLater(() -> getWindow().call("clearSearchPattern"));
                            } else {
                                RepositoryPathNode pathNode = path.getPathNode();
                                if (pathNode.isSearch()) {
                                    String searchPattern = pathNode.getSearchPattern();
                                    ArrayList<RepositoryDirEntry> searchEntryList = new ArrayList<>();
                                    RepositoryDirEntry.traverse(repository, new RepositoryPathNode(), (currentPathNode, entry) -> {
                                        if (currentPathNode.getParent() != null) {
                                            if (Pattern.compile(".*" + searchPattern + ".*",
                                                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                                                    .matcher(entry.getName()).matches()) {
                                                searchEntryList.add(new RepositorySearchEntry(currentPathNode.getParent(), entry));
                                            }
                                        }
                                    });
                                    repositoryContentData.entryList = searchEntryList;
                                } else {
                                    throw new Exception("??????????????????");
                                }
                            }
                            Platform.runLater(() -> {
                                getWindow().setMember("currentParentPath", path.toString());
                                getWindow().setMember("currentParentName", path.getPathNode().getName());
                                getWindow().call("updateRepoNav");
                                getWindow().call("sortEntryList");
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> error("??????????????????", e));
                        } finally {
                            Platform.runLater(JavaApi.this::serviceCleanup);
                        }
                        return null;
                    }
                };
            }
        }

        private abstract class EditingService extends Service<Void> {

            protected final String logMessage;
            protected final String errorMessage;

            protected EditingService(String logMessage, String errorMessage) {
                this.logMessage = logMessage;
                this.errorMessage = errorMessage;
            }

            protected void beforeEditing(Task<Void> task) throws Exception {
            }

            protected abstract void doEditing(ISVNEditor editor, Task<Void> task) throws Exception;

            protected void onEditingFailed(Exception e) {
                Platform.runLater(() -> error(errorMessage, e));
            }

            protected void onEditingSuccess() {
            }

            protected void onEditingComplete() {
            }

            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() {
                        ISVNEditor editor = null;
                        try {
                            beforeEditing(this);
                            editor = repository.getCommitEditor(logMessage, null);
                            editor.openRoot(-1);
                            doEditing(editor, this);
                            editor.closeDir();
                            editor.closeEdit();
                            onEditingSuccess();
                        } catch (UploadCancelledException e) {
                            try {
                                if (editor != null) {
                                    editor.abortEdit();
                                }
                            } catch (Exception ignored) {
                            } finally {
                                Platform.runLater(() -> info(e.getMessage()));
                            }
                        } catch (Exception e) {
                            try {
                                if (editor != null) {
                                    editor.abortEdit();
                                }
                            } catch (Exception ignored) {
                            } finally {
                                onEditingFailed(e);
                            }
                        } finally {
                            onEditingComplete();
                        }
                        return null;
                    }
                };
            }
        }

        private abstract class EditingWithRefreshingService extends EditingService {

            protected EditingWithRefreshingService(String logMessage, String errorMessage) {
                super(logMessage, errorMessage);
            }

            @Override
            protected void onEditingComplete() {
                Platform.runLater(() -> {
                    serviceCleanup();
                    getWindow().call("loadRepoContent");
                });
            }
        }

        private class LoadRepositoryLogService extends Service<Void> {

            private boolean rebuild;

            public LoadRepositoryLogService(boolean rebuild) {
                this.rebuild = rebuild;
            }

            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() {
                        try {
                            if (repositoryLogData == null) {
                                rebuild = true;
                                repositoryLogData = RepositoryLogData.load(repository);
                            }
                            long latestRevision = repository.getLatestRevision();
                            long youngestInCache = repositoryLogData.getYoungestRevision();
                            if (latestRevision < youngestInCache) {
                                repositoryLogData.dumpCache();
                                rebuild = true;
                                repositoryLogData = RepositoryLogData.load(repository);
                                youngestInCache = repositoryLogData.getYoungestRevision();
                            }
                            if (latestRevision > youngestInCache) {
                                LinkedList<SVNLogEntry> newLogEntries = new LinkedList<>();
                                repository.log(new String[]{""}, newLogEntries, youngestInCache + 1, latestRevision, true, true);
                                if (!newLogEntries.isEmpty()) {
                                    rebuild = true;
                                    for (SVNLogEntry newLogEntry : newLogEntries) {
                                        if (newLogEntry.getRevision() != repositoryLogData.getYoungestRevision() + 1) {
                                            throw new Exception("??????????????????");
                                        }
                                        repositoryLogData.pushLogEntry(newLogEntry);
                                        repositoryLogData.setLastChangeTime(System.currentTimeMillis());
                                    }
                                    repositoryLogData.save();
                                }
                            }
                            if (rebuild) {
                                Platform.runLater(() -> {
                                    getWindow().call("createLogTree");
                                    getWindow().call("setLogCacheRefreshingTime", repositoryLogData.getLastChangeTimeString());
                                });
                            }
                        } catch (Exception e) {
                            Platform.runLater(() -> error("??????????????????????????????", e));
                        } finally {
                            Platform.runLater(JavaApi.this::serviceCleanup);
                        }
                        return null;
                    }
                };
            }
        }

        /**
         * ???????????? - ????????????
         */
        public void closeRepository() {
            mainApp.showWelcome();
        }

        /**
         * ???????????? - ?????? - ??????
         */
        public void loadRepositoryContent() {
            startExclusiveService(buildNonInteractiveService(
                    new LoadRepositoryContentService(), "??????????????????"));
        }

        public Object[] getPathNodeArray() {
            return repositoryContentData.getPathNodeArray();
        }

        public Object[] getEntryArray() {
            return repositoryContentData.getEntryArray();
        }

        public void sortEntryList(String sortKey, String direction) {
            direction = "up".equals(direction) ? "up" : "down";
            Comparator<RepositoryDirEntry> comparator;
            switch (sortKey) {
                case "mtime":
                    comparator = "up".equals(direction) ?
                            RepositoryDirEntry::entryMtimeCompare : RepositoryDirEntry::entryMtimeCompareRev;
                    break;
                case "type":
                    comparator = "up".equals(direction) ?
                            RepositoryDirEntry::entryTypeCompare : RepositoryDirEntry::entryTypeCompareRev;
                    break;
                case "size":
                    comparator = "up".equals(direction) ?
                            RepositoryDirEntry::entrySizeCompare : RepositoryDirEntry::entrySizeCompareRev;
                    break;
                case "name":
                default:
                    sortKey = "name";
                    comparator = "up".equals(direction) ?
                            RepositoryDirEntry::entryNameCompare : RepositoryDirEntry::entryNameCompareRev;
            }
            repositoryContentData.entryList.sort(comparator);
            getWindow().call("fillRepoContentTable");
            getWindow().call("showSortIcon", sortKey, direction);
        }

        public boolean hasPrevious() {
            return path.hasPrevious();
        }

        public boolean hasNext() {
            return path.hasNext();
        }

        public boolean hasParent() {
            return path.hasParent();
        }

        public void goPrevious() {
            if (path.goPrevious()) {
                getWindow().call("loadRepoContent");
            }
        }

        public void goNext() {
            if (path.goNext()) {
                getWindow().call("loadRepoContent");
            }
        }

        public void goParent() {
            if (path.goParent()) {
                getWindow().call("loadRepoContent");
            }
        }

        public void goPath(String pathString) {
            if (path.goPath(pathString)) {
                getWindow().call("loadRepoContent");
            }
        }

        public void goSearch(String pattern) {
            if (pattern != null) {
                pattern = pattern.trim();
                if (pattern.length() > 0) {
                    RepositoryPathNode searchPathNode = RepositoryPathNode.getSearchPathNode(pattern);
                    if (searchPathNode != null && path.goPath(searchPathNode.toString())) {
                        getWindow().call("loadRepoContent");
                    }
                }
            }
        }

        /**
         * ???????????? - ?????? - ??????
         */
        public void uploadDir() {
            File dir = mainApp.chooseDirectory();
            if (dir != null) {
                uploadItems(Collections.singletonList(dir), path.getPathNode());
            }
        }

        public void uploadFiles() {
            List<File> files = mainApp.chooseMultipleFiles();
            uploadItems(files, path.getPathNode());
        }

        private void uploadItems(List<File> items, RepositoryPathNode parentPathNode) {
            List<File> dirs = new LinkedList<>();
            List<File> files = new LinkedList<>();
            Map<File, String> uploadPathMap = new HashMap<>();

            mainApp.showProgress(-1, "??????????????????");
            items.forEach(item ->
                    collectUploadItems(item, parentPathNode.resolve(item.getName()), dirs, files, uploadPathMap));
            mainApp.hideProgress();

            if (!dirs.isEmpty() || !files.isEmpty()) {
                upload(dirs, files, uploadPathMap);
            }
        }

        private void collectUploadItems(File item, RepositoryPathNode itemPathNode,
                                        List<File> dirs, List<File> files, Map<File, String> uploadPathMap) {
            if (item.exists() && !FileUtil.shouldIgnore(item)) {
                if (item.isFile()) {
                    files.add(item);
                    uploadPathMap.put(item, itemPathNode.toString());
                } else if (item.isDirectory()) {
                    dirs.add(item);
                    uploadPathMap.put(item, itemPathNode.toString());
                    File[] children = item.listFiles();
                    children = children != null ? children : new File[0];
                    Arrays.asList(children).forEach(
                            child -> collectUploadItems(child, itemPathNode.resolve(child.getName()),
                                    dirs, files, uploadPathMap));
                }
            }
        }

        private void upload(List<File> dirs, List<File> files, Map<File, String> uploadPathMap) {
            if (uploadTransactionData == null) {
                String errorMsg = "????????????";
                String progressTitle = "????????????";
                String uploadPreCheckProgressText = "???????????????";
                String dirUploadProgressText = "???????????????";
                String dirUploadProgressTextTpl = dirUploadProgressText + "???%d/%d??????%s";
                String fileUploadProgressText = "????????????";
                String fileUploadProgressTextTpl = "[%6s] [%s / %s] ????????????%d / %d \t| ???????????????%s";
                String fileUploadSubProgressTextTpl = "[%6s] [%s / %s] ???????????????%s";
                String fileChecksumCalProgressTextTpl = "[%6s] ????????????%d / %d";
                String fileChecksumCalSubProgressTextTpl = "[%6s] [%s / %s] ????????????????????????%s";
                String uploadCompleteProgressText = "????????????";
                String cancelConfirmMsg = "????????????????????????";
                startExclusiveService(buildNonInteractiveService(new EditingWithRefreshingService("??????", errorMsg) {
                    @Override
                    protected void beforeEditing(Task<Void> task) throws Exception {
                        Platform.runLater(() -> {
                            mainApp.showProgress(-1, uploadPreCheckProgressText, -1, uploadPreCheckProgressText);
                            mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                        });
                        uploadTransactionData = new UploadTransactionData()
                                .setTask(task)
                                .setRepository(repository)
                                .setDirList(dirs)
                                .setFileList(files)
                                .setUploadPathMap(uploadPathMap)
                                .setChecksumProgressHandler(this::updateChecksumProgress)
                                .build();
                        if (uploadTransactionData.isEmpty()) {
                            throw new UploadCancelledException("?????????????????????");
                        }
                    }

                    @Override
                    protected void doEditing(ISVNEditor editor, Task<Void> task) throws Exception {
                        if (CollectionUtils.isNotEmpty(uploadTransactionData.getDirList())) {
                            /*?????????????????????*/
                            Platform.runLater(() -> {
                                mainApp.showProgress(0, dirUploadProgressText);
                                mainApp.setProgressTitle(progressTitle);
                                mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                            });

                            /*???????????????*/
                            for (File dir : uploadTransactionData.getDirList()) {
                                Preconditions.checkArgument(uploadTransactionData.getKind(dir) != SVNNodeKind.DIR);
                                if (task.isCancelled()) {
                                    throw new UploadCancelledException();
                                }
                                editor.addDir(uploadPathMap.get(dir), null, -1);
                                editor.closeDir();
                                updateProgress(dir);
                            }
                        }

                        if (CollectionUtils.isNotEmpty(uploadTransactionData.getFileList())) {
                            /*??????????????????*/
                            Platform.runLater(() -> {
                                mainApp.showProgress(0, fileUploadProgressText, 0, fileUploadProgressText);
                                mainApp.setProgressTitle(progressTitle);
                                mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                            });

                            /*????????????*/
                            for (File file : uploadTransactionData.getFileList()) {
                                /*??????????????????*/
                                Preconditions.checkArgument(file.length() == uploadTransactionData.getSize(file));

                                long sent = 0;
                                updateProgress(file, sent);

                                String uploadFilePath = uploadPathMap.get(file);
                                if (uploadTransactionData.getKind(file) == SVNNodeKind.FILE) {
                                    editor.openFile(uploadFilePath, -1);
                                } else {
                                    editor.addFile(uploadFilePath, null, -1);
                                }
                                editor.applyTextDelta(uploadFilePath, null);
                                SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
                                String checksum;
                                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                                    byte[] targetBuffer = new byte[64 * 1024];
                                    MessageDigest digest = null;
                                    try {
                                        digest = MessageDigest.getInstance("MD5");
                                    } catch (NoSuchAlgorithmException e) {
                                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                                                "MD5 implementation not found: {0}", e.getLocalizedMessage());
                                        SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
                                    }
                                    boolean windowSent = false;
                                    while (true) {
                                        if (task.isCancelled()) {
                                            throw new UploadCancelledException();
                                        }
                                        int targetLength = fileInputStream.read(targetBuffer);
                                        if (targetLength <= 0) {
                                            if (!windowSent) {
                                                editor.textDeltaChunk(uploadFilePath, SVNDiffWindow.EMPTY);
                                            }
                                            /*?????????????????????*/
                                            Preconditions.checkArgument(sent == uploadTransactionData.getSize(file));
                                            break;
                                        }
                                        if (digest != null) {
                                            digest.update(targetBuffer, 0, targetLength);
                                        }
                                        deltaGenerator.sendDelta(uploadFilePath, targetBuffer, targetLength, editor);
                                        windowSent = true;
                                        sent += targetLength;
                                        updateProgress(file, sent);
                                    }
                                    editor.textDeltaEnd(uploadFilePath);
                                    checksum = SVNFileUtil.toHexDigest(digest);
                                }
                                editor.closeFile(uploadFilePath, checksum);

                                updateProgress(file, sent);
                            }
                        }

                        /*????????????*/
                        Platform.runLater(() -> {
                            if (CollectionUtils.isNotEmpty(uploadTransactionData.getFileList())) {
                                mainApp.setProgress(1, uploadCompleteProgressText, 1, uploadCompleteProgressText);
                            } else {
                                mainApp.setProgress(1, uploadCompleteProgressText);
                            }
                        });
                    }

                    private void updateChecksumProgress(long processedSize, long totalSize, int itemIdx, int itemCount, String itemName) {
                        double progressValue = 1. * (itemIdx + 1) / itemCount;
                        String progressPercent = String.format("%.1f%%", 100 * progressValue);

                        double subProgressValue = 1. * processedSize / totalSize;
                        String subProgressPercent = String.format("%.1f%%", 100 * subProgressValue);
                        String processedSizeString = FileUtil.getSizeString(processedSize, 0);
                        String totalSizeString = FileUtil.getSizeString(totalSize, 0);
                        Platform.runLater(() -> mainApp.setProgress(
                                progressValue, String.format(fileChecksumCalProgressTextTpl,
                                        progressPercent, (itemIdx + 1), itemCount),
                                subProgressValue, String.format(fileChecksumCalSubProgressTextTpl,
                                        subProgressPercent, processedSizeString, totalSizeString, itemName)));
                    }

                    private void updateProgress(File dir) {
                        int dirIdx = uploadTransactionData.getDirList().indexOf(dir);
                        int lengthOfDirs = uploadTransactionData.getDirList().size();
                        double progressValue = 1. * (dirIdx + 1) / lengthOfDirs;
                        String dirName = dir.getName();
                        Platform.runLater(() -> mainApp.setProgress(
                                progressValue, String.format(dirUploadProgressTextTpl,
                                        dirIdx + 1, lengthOfDirs, dirName)));
                    }

                    private void updateProgress(File file, long sent) {
                        long totalSent = uploadTransactionData.getPrevSize(file) + sent;
                        long totalSize = Math.max(uploadTransactionData.getTotalSize(), 1);
                        double progressValue = 1. * totalSent / totalSize;
                        String progressPercent = String.format("%.1f%%", 100 * progressValue);
                        String totalSentString = FileUtil.getSizeString(totalSent, 0);
                        String totalSizeString = FileUtil.getSizeString(totalSize, 0);
                        int fileIdx = uploadTransactionData.getFileList().indexOf(file);
                        int lengthOfFiles = uploadTransactionData.getFileList().size();
                        String remainingTimeString = uploadTransactionData.getRemainingTimeString(totalSent);

                        long fileSize = Math.max(uploadTransactionData.getSize(file), 1);
                        double subProgressValue = 1. * sent / fileSize;
                        String subProgressPercent = String.format("%.1f%%", 100 * subProgressValue);
                        String sentString = FileUtil.getSizeString(sent, 0);
                        String fileSizeString = FileUtil.getSizeString(fileSize, 0);
                        String fileName = file.getName();
                        Platform.runLater(() -> mainApp.setProgress(
                                progressValue, String.format(fileUploadProgressTextTpl,
                                        progressPercent, totalSentString, totalSizeString,
                                        fileIdx + 1, lengthOfFiles, remainingTimeString),
                                subProgressValue, String.format(fileUploadSubProgressTextTpl,
                                        subProgressPercent, sentString, fileSizeString, fileName)));
                    }
                }, errorMsg));
            }
        }

        /**
         * ???????????? - ?????? - ???????????????
         */
        public void createDir(String name) {
            String errorMsg = "?????????????????????";
            startExclusiveService(buildNonInteractiveService(
                    new EditingWithRefreshingService("???????????????", errorMsg) {
                        @Override
                        protected void doEditing(ISVNEditor editor, Task<Void> task) throws Exception {
                            editor.addDir(path.resolve(name).toString(), null, -1);
                            editor.closeDir();
                        }
                    }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ??????
         */
        public void downloadEntry(JSObject pathArray, int length) {
            LinkedList<String> pathList = convertJSStringArray(pathArray, length);
            String errorMsg = "????????????";
            String progressTitle = "????????????";
            String collectDownloadTasksProgressText = "??????????????????";
            String downloadProgressText = "????????????";
            String downloadProgressTextTpl = "[%6s] [%s / %s] ????????????%d / %d \t| ???????????????%s";
            String downloadSubProgressTextTpl = "[%6s] [%s / %s] ???????????????%s";
            String downloadCompleteProgressText = "????????????";
            String cancelConfirmMsg = "????????????????????????";
            startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        private DownloadTransactionData downloadTransactionData;

                        @Override
                        protected Void call() {
                            try {
                                /*??????????????????*/
                                Platform.runLater(() -> {
                                    mainApp.showProgress(-1, collectDownloadTasksProgressText);
                                    mainApp.setOnProgressCloseRequest(event ->
                                            cancelExclusiveService(event, cancelConfirmMsg));
                                });
                                LinkedList<DownloadTask> downloadTasks = new LinkedList<>();
                                for (String srcPathString : pathList) {
                                    RepositoryPathNode srcPathNode = path.resolve(srcPathString).getPathNode();
                                    RepositoryPathNode parentPathNode = srcPathNode.getParent();
                                    RepositoryDirEntry.traverse(repository, srcPathNode, (currentPathNode, entry) -> {
                                        if (entry.getKind() == SVNNodeKind.FILE) {
                                            downloadTasks.add(new DownloadTask(currentPathNode, entry, parentPathNode));
                                        }
                                    });
                                }

                                if (!downloadTasks.isEmpty()) {
                                    /*????????????*/
                                    Platform.runLater(() -> {
                                        mainApp.showProgress(0, downloadProgressText,
                                                0, downloadProgressText);
                                        mainApp.setProgressTitle(progressTitle);
                                        mainApp.setOnProgressCloseRequest(event ->
                                                cancelExclusiveService(event, cancelConfirmMsg));
                                    });
                                    downloadTransactionData = new DownloadTransactionData(downloadTasks, this);

                                    /*????????????*/
                                    for (DownloadTask downloadTask : downloadTransactionData.downloadTasks()) {
                                        if (isCancelled()) {
                                            throw new DownloadCancelledException();
                                        }
                                        downloadTask.getEditor().setReceiveListener(newReceivedTotal ->
                                                updateProgress(downloadTask, newReceivedTotal));
                                        downloadTask.execute(repository);
                                    }

                                    /*????????????*/
                                    Platform.runLater(() -> mainApp.setProgress(1, downloadCompleteProgressText,
                                            1, downloadCompleteProgressText));
                                }
                            } catch (Exception e) {
                                Platform.runLater(() -> error(errorMsg, e));
                            } finally {
                                Platform.runLater(JavaApi.this::serviceCleanup);
                            }
                            return null;
                        }

                        private void updateProgress(DownloadTask downloadTask, long received) {
                            long totalReceived = downloadTransactionData.getPrevSize(downloadTask) + received;
                            long totalSize = Math.max(downloadTransactionData.getTotalSize(), 1);
                            double progressValue = 1. * totalReceived / totalSize;
                            String progressPercent = String.format("%.1f%%", 100 * progressValue);
                            String totalReceivedString = FileUtil.getSizeString(totalReceived, 0);
                            String totalSizeString = FileUtil.getSizeString(totalSize, 0);
                            int taskIdx = downloadTransactionData.indexOfTask(downloadTask);
                            int lengthOfTasks = downloadTransactionData.lengthOfTasks();
                            String remainingTimeString = downloadTransactionData.getRemainingTimeString(totalReceived);

                            long size = Math.max(downloadTask.getSize(), 1);
                            double subProgressValue = 1. * received / size;
                            String subProgressPercent = String.format("%.1f%%", 100 * subProgressValue);
                            String receivedString = FileUtil.getSizeString(received, 0);
                            String sizeString = FileUtil.getSizeString(size, 0);
                            String fileName = downloadTask.getName();
                            Platform.runLater(() -> mainApp.setProgress(
                                    progressValue, String.format(downloadProgressTextTpl,
                                            progressPercent, totalReceivedString, totalSizeString,
                                            taskIdx + 1, lengthOfTasks, remainingTimeString),
                                    subProgressValue, String.format(downloadSubProgressTextTpl,
                                            subProgressPercent, receivedString, sizeString, fileName)));
                        }
                    };
                }
            }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ??????
         */
        public void deleteEntry(JSObject pathArray, int length) {
            LinkedList<String> pathList = convertJSStringArray(pathArray, length);
            String errorMsg = "????????????";
            startExclusiveService(buildNonInteractiveService(
                    new EditingWithRefreshingService("??????", errorMsg) {
                        @Override
                        protected void doEditing(ISVNEditor editor, Task<Void> task) throws Exception {
                            for (String p : pathList.stream()
                                    .sorted(Comparator.reverseOrder())
                                    .collect(Collectors.toList())) {
                                editor.deleteEntry(path.resolve(p).toString(), -1);
                            }
                        }
                    }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ?????????
         */
        public void renameEntry(String pathString, String newName) {
            RepositoryPathNode oldPathNode = path.resolve(pathString).getPathNode();
            RepositoryPathNode newPathNode = oldPathNode.getParent().resolve(newName);
            String oldPath = oldPathNode.toString();
            String newPath = newPathNode.toString();
            String errorMsg = "???????????????";
            startExclusiveService(buildNonInteractiveService(
                    new EditingWithRefreshingService("?????????", errorMsg) {
                        private long revision;
                        private SVNNodeKind kind;

                        @Override
                        protected void beforeEditing(Task<Void> task) throws Exception {
                            if (newPathNode.equals(oldPathNode)) {
                                throw new Exception("???????????????????????????");
                            }
                            revision = repository.getLatestRevision();
                            kind = repository.checkPath(oldPath, revision);
                            if (kind != SVNNodeKind.DIR && kind != SVNNodeKind.FILE) {
                                throw new Exception("???????????????");
                            }
                            if (repository.checkPath(newPath, -1) != SVNNodeKind.NONE) {
                                throw new Exception("??????????????????");
                            }
                        }

                        @Override
                        protected void doEditing(ISVNEditor editor, Task<Void> task) throws Exception {
                            if (kind == SVNNodeKind.DIR) {
                                editor.addDir(newPath, oldPath, revision);
                                editor.closeDir();
                            } else {
                                editor.addFile(newPath, oldPath, revision);
                                editor.closeFile(newPath, null);
                            }
                            editor.deleteEntry(oldPath, -1);
                        }
                    }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ??????/??????
         */
        public void copyEntry(JSObject pathArray, int length, boolean deleteAfterCopy) {
            LinkedList<String> pathList = convertJSStringArray(pathArray, length);
            String operation = deleteAfterCopy ? "??????" : "??????";
            String errorMsg = String.format("%s??????", operation);
            String newParentPath = MainApp.chooseRepositoryDirectory(new RepositoryDirTreeItem(repository), operation);
            if (newParentPath == null) {
                return;
            }
            RepositoryPathNode newParentPathNode = new RepositoryPathNode(Paths.get(newParentPath));
            startExclusiveService(buildNonInteractiveService(
                    new EditingWithRefreshingService(operation, errorMsg) {
                        private long revision;
                        private final HashMap<RepositoryPathNode, SVNNodeKind> kindMap = new HashMap<>();
                        private final HashSet<RepositoryPathNode> newPathNodes = new HashSet<>();

                        private RepositoryPathNode getNewPathNode(RepositoryPathNode pathNode) {
                            return newParentPathNode.resolve(pathNode.getName());
                        }

                        private String getNewPath(RepositoryPathNode pathNode) {
                            return getNewPathNode(pathNode).toString();
                        }

                        @Override
                        protected void beforeEditing(Task<Void> task) throws Exception {
                            revision = repository.getLatestRevision();
                            if (repository.checkPath(newParentPathNode.toString(), -1) != SVNNodeKind.DIR) {
                                throw new Exception("????????????????????????");
                            }
                            for (String pathString : pathList) {
                                RepositoryPathNode oldPathNode = path.resolve(pathString).getPathNode();
                                RepositoryPathNode newPathNode = getNewPathNode(oldPathNode);
                                if (newPathNode.equals(oldPathNode)) {
                                    throw new Exception("?????????????????????????????????" + newPathNode);
                                }
                                if (!newPathNodes.add(newPathNode)) {
                                    throw new Exception("??????????????????????????????" + newPathNode);
                                }
                                SVNNodeKind kind = repository.checkPath(oldPathNode.toString(), revision);
                                if (kind != SVNNodeKind.DIR && kind != SVNNodeKind.FILE) {
                                    throw new Exception("???????????????");
                                }
                                if (repository.checkPath(newPathNode.toString(), -1) != SVNNodeKind.NONE) {
                                    throw new Exception("??????????????????");
                                }
                                kindMap.put(oldPathNode, kind);
                            }
                        }

                        @Override
                        protected void doEditing(ISVNEditor editor, Task<Void> task) throws Exception {
                            for (RepositoryPathNode oldPathNode : kindMap.keySet()) {
                                String oldPath = oldPathNode.toString();
                                String newPath = getNewPath(oldPathNode);
                                if (kindMap.get(oldPathNode) == SVNNodeKind.DIR) {
                                    editor.addDir(newPath, oldPath, revision);
                                    editor.closeDir();
                                } else {
                                    editor.addFile(newPath, oldPath, revision);
                                    editor.closeFile(newPath, null);
                                }
                            }
                            if (deleteAfterCopy) {
                                for (String oldPath : kindMap.keySet().stream()
                                        .map(RepositoryPathNode::toString)
                                        .sorted(Comparator.reverseOrder())
                                        .collect(Collectors.toList())) {
                                    editor.deleteEntry(oldPath, -1);
                                }
                            }
                        }
                    }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ????????????
         */
        public void checkEntryDetail(JSObject pathArray, int length) {
            LinkedList<String> pathList = convertJSStringArray(pathArray, length);
            String errorMsg = "????????????????????????";
            startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        private final HashMap<RepositoryPathNode, SVNDirEntry> dirs = new HashMap<>();
                        private final HashMap<RepositoryPathNode, SVNDirEntry> files = new HashMap<>();

                        @Override
                        protected Void call() {
                            try {
                                Platform.runLater(() -> mainApp.showProgress(-1, "????????????"));
                                for (String pathString : pathList) {
                                    RepositoryPathNode pathNode = path.resolve(pathString).getPathNode();
                                    Platform.runLater(() -> mainApp.setProgress(-1, "???????????????" + pathNode));
                                    RepositoryDirEntry.traverse(repository, pathNode, (currentPathNode, entry) -> {
                                        if (entry.getKind() == SVNNodeKind.FILE) {
                                            files.put(currentPathNode, entry);
                                        } else if (entry.getKind() == SVNNodeKind.DIR) {
                                            dirs.put(currentPathNode, entry);
                                        }
                                    });
                                }
                                Platform.runLater(this::showEntryDetail);
                            } catch (Exception e) {
                                Platform.runLater(() -> error(errorMsg, e));
                            } finally {
                                Platform.runLater(JavaApi.this::serviceCleanup);
                            }
                            return null;
                        }

                        private void showEntryDetail() {
                            if (dirs.isEmpty() && files.isEmpty()) {
                                warn(errorMsg);
                                return;
                            }
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("????????????");
                            DialogPane dialogPane = alert.getDialogPane();
                            GridPane detailPane = new GridPane();
                            detailPane.getStyleClass().add("detail-pane");
                            detailPane.getStylesheets().add(
                                    Objects.requireNonNull(MainApp.class.getResource("view/html/css/entry-detail-alert.css"))
                                            .toExternalForm());
                            long totalSize = files.values().stream()
                                    .mapToLong(SVNDirEntry::getSize).sum();
                            if (pathList.size() > 1) {
                                alert.setHeaderText(String.format("%d???????????????%d?????????", dirs.size(), files.size()));
                                detailPane.addRow(0, new Label("?????????"), new Label(FileUtil.getSizeString(totalSize, 2)));
                            } else {
                                RepositoryPathNode pathNode = path.resolve(pathList.peek()).getPathNode();
                                RepositoryDirEntry dirEntry = new RepositoryDirEntry(dirs.getOrDefault(pathNode, files.get(pathNode)));
                                alert.setHeaderText(dirEntry.getName());
                                int row = 0;
                                detailPane.addRow(row++, new Label("?????????"), new Label(dirEntry.getType()));
                                detailPane.addRow(row++, new Label("?????????"), new Label(pathNode.getParent().toString()));
                                detailPane.addRow(row++, new Label("?????????"), new Label(FileUtil.getSizeString(totalSize, 2)));
                                if (dirEntry.isDir()) {
                                    detailPane.addRow(row++, new Label("?????????"), new Label(
                                            String.format("%d???????????????%d?????????", dirs.size() - 1, files.size())));
                                }
                                detailPane.addRow(row++, new Label("???????????????"), new Label(dirEntry.getMtime()));
                                if (!dirEntry.isDir()) {
                                    detailPane.addRow(row, new Label("????????????"), new Label(RepositoryDirEntry.getChecksum(repository, pathNode)));
                                }
                            }
                            dialogPane.setContent(detailPane);
                            alert.showAndWait();
                        }
                    };
                }
            }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ???????????? - ??????
         */
        public void repoAdminCompress() throws Exception {
            // check & confirm
            if (!RepositoryConfig.PROTOCOL_FILE.equals(repositoryConfig.getProtocol())) {
                warn("???????????????????????????");
                return;
            }
            if (repository.getLatestRevision() <= 1
                    && !yesOrNo("???????????????????????????????????????????????????????????????????????????????????????",
                    "??????", "??????")) {
                return;
            }
            File repoRootFile = new File(repositoryConfig.getPath());
            long oldSize = FileUtil.getUsedSize(repoRootFile);
            long usableSize = repoRootFile.getUsableSpace();
            if (oldSize > usableSize) {
                warn("????????????????????????");
                return;
            }
            if (!yesOrNo("?????????????????????" + FileUtil.getSizeString(oldSize)
                            + "\n?????????????????????" + FileUtil.getSizeString(usableSize)
                            + "\n??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????",
                    "??????", "??????")) {
                return;
            }

            // do preparation
            File newRepoRootFile = Paths.get(repoRootFile.getParent(),
                    repoRootFile.getName() + ("." + MainApp.APP_NAME + "compressing").toLowerCase()).toFile();
            if (newRepoRootFile.exists()) {
                if (!yesOrNo("?????????" + newRepoRootFile + "???????????????????????????"
                        , "??????", "??????")) {
                    return;
                }
                if (newRepoRootFile.isDirectory()) {
                    Files.walk(newRepoRootFile.toPath())
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } else {
                    newRepoRootFile.delete();
                }
            }

            String errorMsg = "????????????";
            String successTextTpl = "????????????" +
                    "\n??????????????????" + FileUtil.getSizeString(oldSize)
                    + "\n??????????????????%s";
            String compressProgressText = "????????????";
            String cancelConfirmMsg = "????????????????????????";
            startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            try {
                                // create new repository
                                SVNRepositoryFactory.createLocalRepository(newRepoRootFile, true, false);
                                RepositoryConfig newRepositoryConfig = RepositoryConfig.newFileRepositoryConfig(newRepoRootFile.getPath(), null, null);
                                SVNRepository newRepository = newRepositoryConfig.getRepository();

                                // do compression
                                Platform.runLater(() -> {
                                    mainApp.showProgress(-1, compressProgressText);
                                    mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                                });
                                try (PipedOutputStream outputStream = new PipedOutputStream();
                                     PipedInputStream inputStream = new PipedInputStream(outputStream)) {
                                    (compressExecutor = Executors.newSingleThreadExecutor()).submit(() -> {
                                        try {
                                            SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
                                            svnOperationFactory.setAuthenticationManager(repository.getAuthenticationManager());
                                            SvnRepositoryDump dump = svnOperationFactory.createRepositoryDump();
                                            dump.setRepositoryRoot(repoRootFile);
                                            dump.setStartRevision(SVNRevision.HEAD);
                                            dump.setOut(outputStream);
                                            dump.run();
                                            outputStream.close();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
                                    svnOperationFactory.setAuthenticationManager(newRepository.getAuthenticationManager());
                                    SvnRepositoryLoad load = svnOperationFactory.createRepositoryLoad();
                                    load.setRepositoryRoot(newRepoRootFile);
                                    load.setDumpStream(inputStream);
                                    load.run();
                                }

                                // replace repository & config
                                RepositoryConfig.remove(repositoryConfig.getRepositoryUUID());
                                Files.walk(Paths.get(repositoryConfig.getPath()))
                                        .sorted(Comparator.reverseOrder())
                                        .map(Path::toFile)
                                        .forEach(File::delete);
                                newRepoRootFile.renameTo(repoRootFile);
                                repositoryConfig = RepositoryConfig.newFileRepositoryConfig(repositoryConfig.getPath(), null, null);
                                repository = repositoryConfig.getRepository();
                                repositoryConfig.save();

                                // success
                                Platform.runLater(() -> info(
                                        String.format(successTextTpl, FileUtil.getSizeString(FileUtil.getUsedSize(repoRootFile)))));
                            } catch (Exception e) {
                                Platform.runLater(() -> error(errorMsg, e));
                            } finally {
                                Platform.runLater(JavaApi.this::serviceCleanup);
                            }
                            return null;
                        }
                    };
                }
            }, errorMsg));
        }

        /**
         * ???????????? - ?????? - ???????????? - ??????
         */
        public void repoAdminVerify() {
            String errorMsg = "????????????";
            String successText = "????????????";
            String verifyProgressText = "????????????";
            String progressTextTpl = verifyProgressText + "???%d / %d";
            String cancelConfirmMsg = "????????????????????????";

            if (!RepositoryConfig.PROTOCOL_FILE.equals(repositoryConfig.getProtocol())) {
                warn("???????????????????????????");
                return;
            }

            startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        private long latestRevision;
                        private long currentRevision;

                        @Override
                        protected Void call() throws Exception {
                            try {
                                latestRevision = repository.getLatestRevision();
                                Platform.runLater(() -> {
                                    mainApp.showProgress(-1, verifyProgressText);
                                    mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                                });
                                SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
                                svnOperationFactory.setAuthenticationManager(repository.getAuthenticationManager());
                                SvnRepositoryVerify verify = svnOperationFactory.createRepositoryVerify();
                                verify.setRepositoryRoot(new File(repositoryConfig.getPath()));
                                verify.setReceiver((target, svnAdminEvent) -> updateProgress(svnAdminEvent.getRevision()));
                                verify.run();
                                Platform.runLater(() -> info(successText));
                            } catch (Exception e) {
                                Platform.runLater(() -> error(
                                        Math.min(currentRevision + 1, latestRevision) + "???" + errorMsg, e));
                            } finally {
                                Platform.runLater(JavaApi.this::serviceCleanup);
                            }
                            return null;
                        }

                        private void updateProgress(long revision) {
                            currentRevision = revision;
                            Platform.runLater(() -> mainApp.setProgress(
                                    (currentRevision + 1.0) / (latestRevision + 1.0),
                                    String.format(progressTextTpl, currentRevision, latestRevision)));
                        }
                    };
                }
            }, errorMsg));
        }

        /**
         * ???????????? - ????????????
         */
        public void loadRepositoryLog(boolean rebuild) {
            startExclusiveService(buildNonInteractiveService(
                    new LoadRepositoryLogService(rebuild), "??????????????????????????????"));
        }

        public Object[] getLogTreeNodeChildrenArray(String parentId) {
            return repositoryLogData.getLogTreeNodeChildrenArray(parentId);
        }

        /**
         * ???????????? - ??????
         */
        public Object loadBaiduPanConfig() {
            return BaiduPanConfig.load();
        }

        public void baiduPanSignIn() {
            BaiduPanConfig baiduPanConfig = BaiduPanConfig.signIn();
            if (baiduPanConfig != null) {
                getWindow().call("loadSyncRepoList");
            }
        }

        public void baiduPanChangeRootPath() throws Exception {
            BaiduPanConfig baiduPanConfig = BaiduPanConfig.load();
            if (baiduPanConfig.changeRootPath()
                    && baiduPanConfig.isComplete()) {
                baiduPanConfig.save();
                getWindow().call("loadSyncRepoList");
            }
        }

        public void baiduPanSignOut() {
            BaiduPanConfig.clearCache();
            getWindow().call("loadSyncRepoList");
        }

        public Object[] loadSyncRepoConfigList() {
            return RepositoryConfig.loadAll().toArray();
        }

        public void syncRepository(String sourceUUID, String targetUUID) {
            String errorMsg = "????????????";
            try {
                String syncProgressText = "????????????";
                String progressTextTpl = syncProgressText + "???%d / %d??????%d";
                String syncCompleteText = "????????????";
                String cancelConfirmMsg = "????????????????????????";

                mainApp.showProgress(-1, "???????????????");
                SVNRepository syncSource = RepositoryConfig.load(sourceUUID).getRepository();

                mainApp.showProgress(-1, "??????????????????");
                SVNRepository syncTarget = RepositoryConfig.load(targetUUID).getRepository();

                mainApp.hideProgress();
                long srcRevision = syncSource.getLatestRevision();
                long tgtRevision = syncTarget.getLatestRevision();
                if (srcRevision <= tgtRevision) {
                    throw new Exception("???????????????????????????????????????" + srcRevision + "?????????????????????" + tgtRevision + "???");
                }
                for (long revision = Math.max(tgtRevision - 100, 1); revision <= tgtRevision; revision++) {
                    SVNPropertyValue srcLog = syncSource.getRevisionPropertyValue(revision, SVNRevisionProperty.LOG);
                    SVNPropertyValue tgtLog = syncTarget.getRevisionPropertyValue(revision, SVNRevisionProperty.LOG);
                    if (!Objects.equals(srcLog, tgtLog)) {
                        throw new Exception("??????????????????????????????" + revision + "????????????");
                    }
                }
                if (!confirm("???????????????\n" +
                        "????????????" + syncSource.getRepositoryRoot(true) + "\n" +
                        "???????????????" + syncTarget.getRepositoryRoot(true))) {
                    throw new SyncCancelledException();
                }

                startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                    @Override
                    protected Task<Void> createTask() {
                        return new Task<Void>() {
                            @Override
                            protected Void call() {
                                try {
                                    SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
                                    replicator.setReplicationHandler(new ISVNReplicationHandler() {
                                        @Override
                                        public void revisionReplicating(SVNRepositoryReplicator source, SVNLogEntry logEntry) {
                                            updateProgress(logEntry.getRevision());
                                        }

                                        @Override
                                        public void revisionReplicated(SVNRepositoryReplicator source, SVNCommitInfo commitInfo) {
                                            // Do nothing
                                        }

                                        @Override
                                        public void checkCancelled() {
                                            // Do nothing
                                        }
                                    });
                                    Platform.runLater(() -> {
                                        mainApp.showProgress(-1, syncProgressText);
                                        mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, cancelConfirmMsg));
                                    });
                                    replicator.replicateRepository(syncSource, syncTarget, true);
                                    Platform.runLater(() -> info(syncCompleteText));
                                } catch (Exception e) {
                                    Platform.runLater(() -> error(errorMsg, e));
                                } finally {
                                    Platform.runLater(JavaApi.this::serviceCleanup);
                                }
                                return null;
                            }

                            private void updateProgress(long revision) {
                                long revisionIdx = revision - tgtRevision;
                                long totalLength = srcRevision - tgtRevision;
                                Platform.runLater(() -> mainApp.setProgress(
                                        -1, String.format(progressTextTpl, revisionIdx, totalLength, revision)));
                            }
                        };
                    }
                }, errorMsg));
            } catch (Exception e) {
                if (!(e instanceof SyncCancelledException)) {
                    error(errorMsg, e);
                }
                serviceCleanup();
            }
        }

        public void compareRepository(String sourceUUID, String targetUUID) throws Exception {
            ComparableRepositoryConfig sourceConfig = BaiduPanConfig.TITLE.equals(sourceUUID) ? BaiduPanConfig.load() : RepositoryConfig.load(sourceUUID);
            ComparableRepositoryConfig targetConfig = BaiduPanConfig.TITLE.equals(targetUUID) ? BaiduPanConfig.load() : RepositoryConfig.load(targetUUID);
            if (sourceConfig instanceof BaiduPanConfig && !((BaiduPanConfig) sourceConfig).isComplete()
                    || targetConfig instanceof BaiduPanConfig && !((BaiduPanConfig) targetConfig).isComplete()) {
                throw new Exception("???????????????????????????????????????????????????");
            }
            mainApp.showRepositoryCompare(sourceConfig, targetConfig);
        }

        /**
         * ???????????? - ??????
         */
        public Object loadAppSettings() {
            return AppSettings.load();
        }

        public void saveAppSettings(JSObject params) {
            try {
                AppSettings settings = AppSettings.load();
                File newDownloadParent = new File(String.valueOf(params.getMember("downloadParent")));
                if (!settings.getDownloadParent().equals(newDownloadParent)) {
                    settings.setDownloadParent(newDownloadParent);
                    settings.save();
                }
                getWindow().call("loadAppSettings");
            } catch (Exception e) {
                error("??????????????????", e);
            }
        }

        public void chooseDownloadParent() {
            File newDownloadParent = mainApp.chooseDirectory();
            if (newDownloadParent != null) {
                boolean enableSave = !AppSettings.load().getDownloadParent().equals(newDownloadParent);
                getWindow().call("setDownloadParent", newDownloadParent.getAbsolutePath(), enableSave);
            }
        }
    }

    public void setRepositoryConfig(RepositoryConfig repositoryConfig) {
        this.repositoryConfig = repositoryConfig;
    }

    public void setRepository(SVNRepository repository) {
        this.repository = repository;
    }
}
