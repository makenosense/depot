package depot;

import depot.control.*;
import depot.model.base.BaseDirTreeItem;
import depot.model.repository.config.ComparableRepositoryConfig;
import depot.model.repository.config.RepositoryConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.Dialog;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.*;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.*;
import java.awt.MenuItem;
import java.awt.*;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.desktop.AppReopenedListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class MainApp extends Application {
    public static final String APP_NAME = "Depot";
    public static final String VERSION;
    public static URL LOGO_URL = Objects.requireNonNull(MainApp.class.getResource("view/html/img/logo.png"));

    private Stage primaryStage;
    private Stage progressStage;
    private ProgressController progressController;

    static {
        String version = "0.0.0";
        try {
            Properties properties = new Properties();
            properties.load(MainApp.class.getClassLoader().getResourceAsStream("application.properties"));
            version = properties.getProperty("app.version");
        } catch (IOException e) {
            e.printStackTrace();
        }
        VERSION = version;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        Platform.setImplicitExit(false);
        Desktop desktop = Desktop.getDesktop();
        desktop.addAppEventListener((AppReopenedListener) e -> {
            if (!primaryStage.isShowing()) {
                Platform.runLater(primaryStage::show);
            }
        });
        desktop.addAppEventListener(new AppForegroundListener() {
            @Override
            public void appRaisedToForeground(AppForegroundEvent e) {
                if (!primaryStage.isShowing()) {
                    Platform.runLater(primaryStage::show);
                }
            }

            @Override
            public void appMovedToBackground(AppForegroundEvent e) {
            }
        });

        if (System.getProperty("os.name").toLowerCase().contains("win")
                && !"true".equalsIgnoreCase(System.getenv().get("DEBUG"))) {
            Platform.setImplicitExit(false);
            initSystemTray();
        }

        progressStage = new Stage();
        progressStage.setResizable(false);
        progressStage.initModality(Modality.APPLICATION_MODAL);

        SVNRepositoryFactoryImpl.setup();

        showWelcome();
    }

    /**
     * ??????????????????
     */
    private void initSystemTray() {
        if (SystemTray.isSupported()) {
            PopupMenu popup = new PopupMenu();
            MenuItem exitItem = new MenuItem("??????");
            exitItem.addActionListener(e -> exit());
            popup.add(exitItem);

            ImageIcon imageIcon = new ImageIcon(LOGO_URL);
            TrayIcon trayIcon = new TrayIcon(imageIcon.getImage());
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip(APP_NAME);
            trayIcon.setPopupMenu(popup);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        if (primaryStage.isShowing()) {
                            Platform.runLater(() -> primaryStage.hide());
                        } else {
                            Platform.runLater(() -> primaryStage.show());
                        }
                    }
                }
            });

            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ????????????
     */
    private void initAndShowStage(Stage stage, String fxmlPath) {
        try {
            stage.hide();
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlPath));
            Parent parent = loader.load();
            BaseController controller = loader.getController();
            controller.setMainApp(this);
            stage.setTitle(controller.getTitle());
            stage.setWidth(controller.getWidth());
            stage.setHeight(controller.getHeight());
            stage.setMinWidth(controller.getWidth());
            stage.setMinHeight(controller.getHeight());
            Scene scene = new Scene(parent, controller.getWidth(), controller.getHeight());
            KeyCombination keyCombination = new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN);
            scene.getAccelerators().put(keyCombination, this::exit);
            scene.setUserData(controller);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showWelcome() {
        initAndShowStage(primaryStage, "view/fxml/Welcome.fxml");
    }

    public void showInterface(RepositoryConfig repositoryConfig, SVNRepository repository) {
        initAndShowStage(primaryStage, "view/fxml/Interface.fxml");
        ((InterfaceController) primaryStage.getScene().getUserData()).setRepositoryConfig(repositoryConfig);
        ((InterfaceController) primaryStage.getScene().getUserData()).setRepository(repository);
        primaryStage.setTitle(APP_NAME + " - " + repositoryConfig.getTitle() + " (" + repositoryConfig.getUrl() + ")");
    }

    public void showRepositoryCompare(ComparableRepositoryConfig sourceConfig, ComparableRepositoryConfig targetConfig) {
        Stage compareStage = new Stage();
        compareStage.initOwner(primaryStage);
        compareStage.initModality(Modality.WINDOW_MODAL);
        initAndShowStage(compareStage, "view/fxml/RepositoryCompare.fxml");
        ((RepositoryCompareController) compareStage.getScene().getUserData()).setSourceConfig(sourceConfig);
        ((RepositoryCompareController) compareStage.getScene().getUserData()).setTargetConfig(targetConfig);
        compareStage.setTitle(sourceConfig.getTitle() + " (" + sourceConfig.getUrl() + ") - "
                + targetConfig.getTitle() + " (" + targetConfig.getUrl() + ")");
    }

    /**
     * ?????????????????????
     */
    public File chooseDirectory() {
        return chooseDirectory("??????????????????");
    }

    public File chooseDirectory(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        return directoryChooser.showDialog(primaryStage);
    }

    public List<File> chooseMultipleFiles() {
        return chooseMultipleFiles("???????????????????????????");
    }

    public List<File> chooseMultipleFiles(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);
        return files != null ? files : new LinkedList<>();
    }

    public static String chooseRepositoryDirectory(BaseDirTreeItem treeRoot, String operation) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setResizable(true);
        dialog.setTitle("????????????????????????");
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(
                Objects.requireNonNull(MainApp.class.getResource("view/html/css/target-parent-dialog.css"))
                        .toExternalForm());

        ButtonType confirmButtonType = new ButtonType(operation, ButtonData.OK_DONE);
        dialogPane.getButtonTypes().setAll(confirmButtonType, ButtonType.CANCEL);
        Node confirmButton = dialogPane.lookupButton(confirmButtonType);
        confirmButton.setDisable(true);

        treeRoot.setExpanded(true);
        TreeView<String> treeView = new TreeView<>(treeRoot);
        treeView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> confirmButton.setDisable(newValue == null));
        dialogPane.setContent(treeView);

        dialog.setResultConverter(buttonType -> {
            try {
                if (buttonType == confirmButtonType) {
                    TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItems().get(0);
                    return ((BaseDirTreeItem) selectedItem).getPathNode().toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
     * ?????????
     */
    public void showProgress(double value, String text) {
        initAndShowStage(progressStage, "view/fxml/Progress.fxml");
        progressStage.setOnCloseRequest(Event::consume);
        progressController = (ProgressController) progressStage.getScene().getUserData();
        progressController.setProgress(value);
        progressController.setText(text);
    }

    public void showProgress(double value, String text, double subValue, String subText) {
        initAndShowStage(progressStage, "view/fxml/DoubleProgress.fxml");
        progressStage.setOnCloseRequest(Event::consume);
        progressController = (ProgressController) progressStage.getScene().getUserData();
        progressController.setProgress(value);
        progressController.setText(text);
        ((DoubleProgressController) progressController).setSubProgress(subValue);
        ((DoubleProgressController) progressController).setSubText(subText);
    }

    public void setProgress(double value, String text) {
        progressController.setProgress(value);
        progressController.setText(text);
    }

    public void setProgress(double value, String text, double subValue, String subText) {
        setProgress(value, text);
        ((DoubleProgressController) progressController).setSubProgress(subValue);
        ((DoubleProgressController) progressController).setSubText(subText);
    }

    public void setProgressTitle(String title) {
        progressStage.setTitle(title);
    }

    public void setOnProgressCloseRequest(EventHandler<WindowEvent> handler) {
        progressStage.setOnCloseRequest(handler);
    }

    public void hideProgress() {
        progressStage.hide();
    }

    /**
     * ????????????
     */
    public void exit() {
        System.exit(0);
    }
}
