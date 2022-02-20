package depot.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class AlertUtil {
    public static final String TITLE_ERROR = "错误";
    public static final String TITLE_WARN = "警告";
    public static final String TITLE_INFO = "信息";
    public static final String TITLE_CONFIRM = "确认";
    public static final String TITLE_YES_OR_NO = "选择";

    public static void error(String contentText) {
        error(null, contentText);
    }

    public static void error(String headerText, String contentText) {
        error(TITLE_ERROR, headerText, contentText);
    }

    public static void error(String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    public static void error(String headerText, Exception e) {
        error(headerText, e.getMessage(), e);
    }

    public static void error(String headerText, String contentText, Exception e) {
        error(TITLE_ERROR, headerText, contentText, e);
    }

    public static void error(String title, String headerText, String contentText, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        String errorString = stringWriter.toString();

        TextArea textArea = new TextArea(errorString);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setMaxWidth(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane errorContent = new GridPane();
        errorContent.setMaxWidth(Double.MAX_VALUE);
        errorContent.add(textArea, 0, 0);

        alert.getDialogPane().setExpandableContent(errorContent);

        alert.showAndWait();
    }

    public static void warn(String contentText) {
        warn(null, contentText);
    }

    public static void warn(String headerText, String contentText) {
        warn(TITLE_WARN, headerText, contentText);
    }

    public static void warn(String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    public static void info(String contentText) {
        info(null, contentText);
    }

    public static void info(String headerText, String contentText) {
        info(TITLE_INFO, headerText, contentText);
    }

    public static void info(String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    public static boolean confirm(String contentText) {
        return confirm(null, contentText);
    }

    public static boolean confirm(String headerText, String contentText) {
        return confirm(TITLE_CONFIRM, headerText, contentText);
    }

    public static boolean confirm(String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static boolean yesOrNo(String contentText) {
        return yesOrNo(null, contentText);
    }

    public static boolean yesOrNo(String headerText, String contentText) {
        return yesOrNo(TITLE_YES_OR_NO, headerText, contentText);
    }

    public static boolean yesOrNo(String title, String headerText, String contentText) {
        return yesOrNo(title, headerText, contentText, null, null);
    }

    public static boolean yesOrNo(String title, String headerText, String contentText, String yesText, String noText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        ButtonType yesButton = ButtonType.YES;
        ButtonType noButton = ButtonType.NO;
        if (StringUtil.notEmpty(yesText)) {
            yesButton = new ButtonType(yesText, ButtonBar.ButtonData.YES);
        }
        if (StringUtil.notEmpty(noText)) {
            noButton = new ButtonType(noText, ButtonBar.ButtonData.NO);
        }
        alert.getButtonTypes().setAll(noButton, yesButton);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yesButton;
    }
}
