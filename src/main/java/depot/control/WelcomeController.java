package depot.control;

import depot.MainApp;
import depot.model.repository.config.RepositoryConfig;
import depot.util.AlertUtil;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class WelcomeController extends BaseController {
    private static final String TITLE = "Welcome to " + MainApp.APP_NAME;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final String HTML_PATH = "view/html/welcome.html";

    private final JavaApi javaApi = new JavaApi();

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
                getWindow().call("showTitle");
                getWindow().call("switchToSidebarTabWithDelay", "sidebar-repo-list");
            }
        });
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
         * 私有方法
         */
        private void hideProgressWithDelay() throws InterruptedException {
            Thread.sleep(100);
            Platform.runLater(() -> mainApp.hideProgress());
        }

        private void switchToRepositoryList() {
            getWindow().call("switchToSidebarTab", "sidebar-repo-list");
        }

        private boolean checkRepositoryConfig(RepositoryConfig repositoryConfig) throws Exception {
            if (RepositoryConfig.PROTOCOL_FILE.equals(repositoryConfig.getProtocol())) {
                File repositoryDir = new File(repositoryConfig.getPath());
                if (!repositoryDir.isDirectory()
                        || Optional.ofNullable(repositoryDir.list()).orElse(new String[0]).length <= 0) {
                    if (!confirm("仓库文件夹“" + repositoryDir.getAbsolutePath() + "”不存在，是否新建？")) {
                        throw new Exception("仓库文件夹不存在");
                    }
                    SVNRepositoryFactory.createLocalRepository(repositoryDir, true, false);
                    if (repositoryConfig.getRepositoryUUID() != null) {
                        RepositoryConfig.remove(repositoryConfig.getRepositoryUUID());
                        repositoryConfig.setRepositoryUUID(null);
                    }
                }
            }
            return repositoryConfig.getRepositoryUUID() == null;
        }

        /**
         * 私有服务类
         */
        private class OpenRepositoryService extends Service<Void> {

            private final RepositoryConfig repositoryConfig;
            private final boolean saveBeforeOpen;

            public OpenRepositoryService(RepositoryConfig repositoryConfig, boolean saveBeforeOpen) {
                this.repositoryConfig = repositoryConfig;
                this.saveBeforeOpen = saveBeforeOpen;
            }

            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() {
                        try {
                            Platform.runLater(() -> mainApp.setProgress(saveBeforeOpen ? 1.0 / 3 : 1.0 / 2, "尝试连接仓库"));
                            SVNRepository repository = repositoryConfig.getRepository();

                            if (saveBeforeOpen) {
                                Platform.runLater(() -> mainApp.setProgress(2.0 / 3, "保存仓库配置"));
                                repositoryConfig.save();
                            }

                            Platform.runLater(() -> mainApp.setProgress(1, "打开仓库"));
                            hideProgressWithDelay();
                            Platform.runLater(() -> mainApp.showInterface(repositoryConfig, repository));
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                AlertUtil.error("出现错误", e);
                                mainApp.hideProgress();
                                switchToRepositoryList();
                            });
                        }
                        return null;
                    }
                };
            }
        }

        /**
         * 公共方法 - 关于
         */
        public String getAppName() {
            return MainApp.APP_NAME;
        }

        public String getVersion() {
            return MainApp.VERSION;
        }

        /**
         * 公共方法 - 添加仓库
         */
        public void chooseRepoPath() {
            File repoPath = mainApp.chooseDirectory();
            if (repoPath != null) {
                getWindow().call("setRepoPath", repoPath.getAbsolutePath());
            }
        }

        public void addRepository(JSObject params) {
            startExclusiveService(new ExclusiveService() {
                @Override
                protected Service createService() throws Exception {
                    mainApp.showProgress(0, "初始化仓库配置");
                    RepositoryConfig repositoryConfig = new RepositoryConfig(params);
                    return new OpenRepositoryService(repositoryConfig, checkRepositoryConfig(repositoryConfig));
                }

                @Override
                protected void onCreationFailed(Exception e) {
                    AlertUtil.error("仓库添加失败", e);
                    mainApp.hideProgress();
                    switchToRepositoryList();
                }
            });
        }

        /**
         * 公共方法 - 仓库列表
         */
        public Object[] loadRepositoryConfigList() {
            return RepositoryConfig.loadAll().toArray();
        }

        public void openRepository(String uuid) {
            startExclusiveService(new ExclusiveService() {
                @Override
                protected Service createService() throws Exception {
                    mainApp.showProgress(0, "加载仓库配置");
                    RepositoryConfig repositoryConfig = RepositoryConfig.loadAndMoveFirst(uuid);
                    return new OpenRepositoryService(repositoryConfig, checkRepositoryConfig(repositoryConfig));
                }

                @Override
                protected void onCreationFailed(Exception e) {
                    AlertUtil.error("仓库打开失败", e);
                    mainApp.hideProgress();
                    switchToRepositoryList();
                }
            });
        }

        public void removeRepository(String uuid) {
            try {
                mainApp.showProgress(-1, "移除仓库");
                RepositoryConfig repositoryConfig = RepositoryConfig.remove(uuid);
                if (repositoryConfig != null
                        && RepositoryConfig.PROTOCOL_FILE.equals(repositoryConfig.getProtocol())) {
                    File repositoryDir = new File(repositoryConfig.getPath());
                    if (repositoryDir.isDirectory()
                            && confirm("是否同时删除本地仓库文件夹？")) {
                        Files.walk(repositoryDir.toPath())
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                }
            } catch (Exception e) {
                AlertUtil.error("仓库移除失败", e);
            } finally {
                mainApp.hideProgress();
                switchToRepositoryList();
            }
        }
    }
}
