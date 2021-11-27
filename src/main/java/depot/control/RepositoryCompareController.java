package depot.control;

import depot.MainApp;
import depot.model.repository.config.ComparableRepositoryConfig;
import depot.model.repository.sync.CompareCancelledException;
import depot.model.repository.sync.RepositoryCompareResult;
import depot.util.AlertUtil;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.WindowEvent;
import netscape.javascript.JSObject;

import java.util.Objects;

public class RepositoryCompareController extends BaseController {
    private static final String TITLE = MainApp.APP_NAME;
    private static final int WIDTH = 700;
    private static final int HEIGHT = 600;
    private static final String HTML_PATH = "view/html/repository-compare.html";

    private final JavaApi javaApi = new JavaApi();

    private ComparableRepositoryConfig sourceConfig;
    private ComparableRepositoryConfig targetConfig;
    private RepositoryCompareResult compareResult;

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
                javaApi.renewCompareResult();
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

        private void serviceCleanup() {
            mainApp.hideProgress();
            webView.setDisable(false);
        }

        private ExclusiveService buildNonInteractiveService(Service service, String creationFailedMsg) {
            return new ExclusiveService() {
                @Override
                protected Service createService() {
                    webView.setDisable(true);
                    return service;
                }

                @Override
                protected void onCreationFailed(Exception e) {
                    AlertUtil.error(creationFailedMsg, e);
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

        public void renewCompareResult() {
            String errorMsg = "仓库比较失败";
            startExclusiveService(buildNonInteractiveService(new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() {
                            try {
                                Platform.runLater(() -> {
                                    mainApp.showProgress(-1, "正在比较仓库");
                                    mainApp.setOnProgressCloseRequest(event -> cancelExclusiveService(event, null));
                                });
                                compareResult = new RepositoryCompareResult(sourceConfig, targetConfig,
                                        pathNode -> {
                                            if (isCancelled()) {
                                                throw new CompareCancelledException();
                                            }
                                            Platform.runLater(() -> mainApp.setProgress(-1, "正在比较：" + pathNode));
                                        });
                                if (compareResult.getChangedEntries().size() > 0) {
                                    Platform.runLater(() -> getWindow().call("createCompareTree"));
                                } else {
                                    Platform.runLater(() -> {
                                        webView.getScene().getWindow().hide();
                                        AlertUtil.info("源仓库与目标仓库完全相同");
                                    });
                                }
                            } catch (Exception e) {
                                Platform.runLater(() -> AlertUtil.error(errorMsg, e));
                            } finally {
                                Platform.runLater(JavaApi.this::serviceCleanup);
                            }
                            return null;
                        }
                    };
                }
            }, errorMsg));
        }

        public Object[] getCompareTreeNodeArray() {
            return compareResult.buildCompareTreeNodeArray();
        }
    }

    public void setSourceConfig(ComparableRepositoryConfig sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public void setTargetConfig(ComparableRepositoryConfig targetConfig) {
        this.targetConfig = targetConfig;
    }
}
