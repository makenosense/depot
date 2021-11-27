module depot {
    requires fontawesomefx;
    requires java.datatransfer;
    requires java.desktop;
    requires java.logging;
    requires java.xml.bind;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires jdk.jsobject;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires org.json;
    requires svnkit;

    opens depot.control;
    opens depot.model.base;
    opens depot.model.repository.config;
    opens depot.model.repository.content;
    opens depot.model.repository.log;
    opens depot.model.repository.path;
    opens depot.model.repository.sync;
    opens depot.model.transfer.base;
    opens depot.model.transfer.download;
    opens depot.model.transfer.upload;
    opens depot.util;
    opens depot.view.fxml;
    opens depot.view.html;
    opens depot;
    exports depot;
}