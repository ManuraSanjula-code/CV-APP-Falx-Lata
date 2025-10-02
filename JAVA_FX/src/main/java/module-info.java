module com.vertex.cv_app.java_fx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    //requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires Shared;
    requires org.json;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires java.desktop;
    requires java.net.http;

    opens com.vertex.cv_app.java_fx.dialog to javafx.fxml;
    opens com.vertex.cv_app.java_fx.panels to javafx.fxml, javafx.base;

    opens com.vertex.cv_app.java_fx to javafx.fxml;
    exports com.vertex.cv_app.java_fx;
}