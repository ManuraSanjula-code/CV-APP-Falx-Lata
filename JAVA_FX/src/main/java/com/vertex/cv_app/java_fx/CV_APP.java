package com.vertex.cv_app.java_fx;

import com.vertex.cv_app.java_fx.dialog.MaterialLoginDialog;
import com.vertex.cv_app.java_fx.dialog.MaterialRegisterDialog;
import com.vertex.cv_app.java_fx.panels.MaterialSearchPanel;
import com.vertex.cv_app.java_fx.panels.MaterialUploadPanel;
import com.vertex.cv_app.java_fx.panels.MaterialAuditLogPanel;
import com.vertex.cv_app.java_fx.panels.ViewCVPanel;
import com.vertex.cv_app.java_fx.view.MaterialRawView;
import com.vertex.cv_app.utils.JWTTokenManager;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.awt.*;

public class CV_APP extends Application {

    public static final String SEARCH_VIEW = "SEARCH";
    public static final String UPLOAD_VIEW = "UPLOAD";
    public static final String VIEW_CV_VIEW = "VIEW_CV";
    public static final String RAW_TEXT_VIEW = "RAW_TEXT";
    public static final String AUDIT_LOG_VIEW = "AUDIT_LOG";

    private String serverUrl = "http://13.250.35.49:8000";
    private String jwtToken = null;
    private JWTTokenManager tokenManager;

    private TabPane mainTabPane;
    private MaterialSearchPanel searchPanel;
    private MaterialUploadPanel uploadPanel;
    private ViewCVPanel viewCVPanel;
    private MaterialRawView rawView;
    private MaterialAuditLogPanel auditLogPanel;

    private Stage primaryStage;
    private HBox appBar;

    // Tabs that need state management
    private Tab searchTab;
    private Tab uploadTab;
    private Tab viewCVTab;
    private Tab rawTextTab;
    private Tab auditLogTab;

    // Menu items that need state management
    private Button loginButton;
    private Button registerButton;
    private Button logoutButton;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("CV Management System");

        tokenManager = new JWTTokenManager();

        // Create Material Design root layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("material-app");

        // Create Material Design App Bar (Top)
        appBar = createMaterialAppBar();
        root.setTop(appBar);

        // Create TabPane for main content
        mainTabPane = createMaterialTabPane();
        root.setCenter(mainTabPane);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;

        // Create Material Design scene
        Scene scene = new Scene(root, height, width);

        // Load Material Design CSS
        try {
            String cssResource = getClass().getResource("/styles/material-design.css").toExternalForm();
            scene.getStylesheets().add(cssResource);
        } catch (NullPointerException e) {
            System.out.println("Warning: Material Design CSS file not found. Using default styles.");
        }

        primaryStage.setScene(scene);

        // Check for existing valid token
        if (hasValidToken()) {
            jwtToken = tokenManager.getToken("access_token");
            updateTokensInPanels();
        }

        // Update tab visibility based on login status
        updateTabVisibility();

        // Set initial tab selection to Search
        mainTabPane.getSelectionModel().select(searchTab);

        primaryStage.show();

        // Show Material Design login dialog if not logged in
        if (!hasValidToken()) {
            showMaterialLoginDialog();
        }
    }

    private HBox createMaterialAppBar() {
        HBox appBar = new HBox();
        appBar.getStyleClass().add("md-app-bar");
        appBar.setAlignment(Pos.CENTER_LEFT);
        appBar.setPrefHeight(64);

        // App title with Material Design typography
        Label appTitle = new Label("CV Management System");
        appTitle.getStyleClass().add("md-app-bar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // User actions in app bar
        HBox userActions = createUserActions();

        appBar.getChildren().addAll(appTitle, spacer, userActions);
        return appBar;
    }

    private HBox createUserActions() {
        HBox userActions = new HBox();
        userActions.getStyleClass().add("md-spacing-8");
        userActions.setAlignment(Pos.CENTER_RIGHT);

        loginButton = new Button("Login");
        loginButton.getStyleClass().addAll("md-button", "md-button-text");
        loginButton.setOnAction(e -> showMaterialLoginDialog());

        registerButton = new Button("Register");
        registerButton.getStyleClass().addAll("md-button", "md-button-text");
        registerButton.setOnAction(e -> showMaterialRegisterDialog());

        logoutButton = new Button("Logout");
        logoutButton.getStyleClass().addAll("md-button", "md-button-outlined");
        logoutButton.setOnAction(e -> performMaterialLogout());

        Button aboutButton = new Button("About");
        aboutButton.getStyleClass().addAll("md-button", "md-button-text");
        aboutButton.setOnAction(e -> showMaterialAboutDialog());

        Button exitButton = new Button("Exit");
        exitButton.getStyleClass().addAll("md-button", "md-button-text");
        exitButton.setOnAction(e -> System.exit(0));

        userActions.getChildren().addAll(loginButton, registerButton, logoutButton, aboutButton, exitButton);
        return userActions;
    }

    private TabPane createMaterialTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("md-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Initialize Material Design panels
        searchPanel = new MaterialSearchPanel(this, serverUrl);
        uploadPanel = new MaterialUploadPanel(this, serverUrl);
        viewCVPanel = new ViewCVPanel(this, serverUrl);
        rawView = new MaterialRawView(this, serverUrl);
        auditLogPanel = new MaterialAuditLogPanel(this, serverUrl);

        // Create tabs
        searchTab = new Tab("🔍 Search CVs", searchPanel);
        searchTab.getStyleClass().add("md-tab");

        uploadTab = new Tab("⬆️ Upload CVs", uploadPanel);
        uploadTab.getStyleClass().add("md-tab");

        auditLogTab = new Tab("📋 Audit Logs", auditLogPanel);
        auditLogTab.getStyleClass().add("md-tab");

        // These tabs are initially hidden and shown programmatically
        viewCVTab = new Tab("📄 CV Details", viewCVPanel);
        viewCVTab.getStyleClass().add("md-tab");

        rawTextTab = new Tab("📝 Raw Text", rawView);
        rawTextTab.getStyleClass().add("md-tab");

        // Add main tabs (Search, Upload, Audit are always available)
        tabPane.getTabs().addAll(searchTab, uploadTab, auditLogTab);

        // Add selection change listener for audit log refresh
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == auditLogTab && auditLogPanel != null) {
                auditLogPanel.refreshLogs();
            }
        });

        return tabPane;
    }

    private void updateTabVisibility() {
        boolean loggedIn = isLoggedIn();

        // Update button visibility
        loginButton.setVisible(!loggedIn);
        loginButton.setManaged(!loggedIn);
        registerButton.setVisible(!loggedIn);
        registerButton.setManaged(!loggedIn);
        logoutButton.setVisible(loggedIn);
        logoutButton.setManaged(loggedIn);

        // Update tab availability
        auditLogTab.setDisable(!loggedIn);
        uploadTab.setDisable(!loggedIn);
    }

    public boolean isLoggedIn() {
        return jwtToken != null && !jwtToken.isEmpty();
    }

    public void showView(String viewName) {
        switch (viewName) {
            case SEARCH_VIEW:
                mainTabPane.getSelectionModel().select(searchTab);
                break;
            case UPLOAD_VIEW:
                mainTabPane.getSelectionModel().select(uploadTab);
                break;
            case AUDIT_LOG_VIEW:
                if (auditLogPanel != null) {
                    auditLogPanel.refreshLogs();
                }
                mainTabPane.getSelectionModel().select(auditLogTab);
                break;
            case VIEW_CV_VIEW:
                showCVDetailsTab();
                break;
            case RAW_TEXT_VIEW:
                showRawTextTab();
                break;
        }
    }

    public void showAuditLogView() {
        if (auditLogPanel != null) {
            auditLogPanel.refreshLogs();
        }
        mainTabPane.getSelectionModel().select(auditLogTab);
    }

    public void showCVDetails(String cvId) {
        if (viewCVPanel != null) {
            viewCVPanel.loadCV(cvId);
        }
        showCVDetailsTab();
    }

    private void showCVDetailsTab() {
        // Add tab if not already present
        if (!mainTabPane.getTabs().contains(viewCVTab)) {
            // Insert before the last permanent tab (audit log)
            int insertIndex = mainTabPane.getTabs().size();
            mainTabPane.getTabs().add(insertIndex, viewCVTab);

            // Make the tab closable for this dynamic tab
            viewCVTab.setOnClosed(e -> {
                // Clean up when tab is closed
                mainTabPane.getTabs().remove(viewCVTab);
            });

            // Add close button functionality
            viewCVTab.setClosable(true);
        }
        mainTabPane.getSelectionModel().select(viewCVTab);
    }

    public void showRawTextView(String cvId, ViewCVPanel sourcePanel) {
        if (rawView != null) {
            rawView.loadRawText(cvId);
        }
        showRawTextTab();
    }

    private void showRawTextTab() {
        // Add tab if not already present
        if (!mainTabPane.getTabs().contains(rawTextTab)) {
            // Insert before the last permanent tab (audit log)
            int insertIndex = mainTabPane.getTabs().size();
            mainTabPane.getTabs().add(insertIndex, rawTextTab);

            // Make the tab closable for this dynamic tab
            rawTextTab.setOnClosed(e -> {
                // Clean up when tab is closed
                mainTabPane.getTabs().remove(rawTextTab);
            });

            // Add close button functionality
            rawTextTab.setClosable(true);
        }
        mainTabPane.getSelectionModel().select(rawTextTab);
    }

    private void showMaterialLoginDialog() {
        MaterialLoginDialog loginDialog = new MaterialLoginDialog(primaryStage, serverUrl, this);
        loginDialog.showAndWait();
    }

    private void showMaterialRegisterDialog() {
        MaterialRegisterDialog registerDialog = new MaterialRegisterDialog(primaryStage, serverUrl, this);
        registerDialog.showAndWait();
    }

    private void showMaterialAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About CV Management System");
        alert.setHeaderText("CV Management System v3.0");
        alert.setContentText(
                "A modern Material Design JavaFX application for managing CVs\n\n" +
                        "Features:\n" +
                        "• Material Design 3 interface with intuitive tabs\n" +
                        "• Advanced search and filtering\n" +
                        "• Secure file upload (PDF & DOCX)\n" +
                        "• Comprehensive audit logging\n" +
                        "• Modern authentication system\n" +
                        "• Responsive tabbed interface\n\n" +
                        "Built with JavaFX & Material Design\n" +
                        "© 2024 Vertex Systems"
        );

        alert.getDialogPane().getStyleClass().add("md-dialog");
        alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().addAll("md-button", "md-button-filled");
        alert.showAndWait();
    }

    private void performMaterialLogout() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Logout");
        confirmation.setHeaderText("Sign Out");
        confirmation.setContentText("Are you sure you want to sign out of your account?");
        confirmation.getDialogPane().getStyleClass().add("md-dialog");

        confirmation.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().addAll("md-button", "md-button-filled");
        confirmation.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().addAll("md-button", "md-button-outlined");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                jwtToken = null;
                tokenManager.clearAllTokens();
                updateTabVisibility();

                // Remove any dynamic tabs and go back to search
                mainTabPane.getTabs().removeAll(viewCVTab, rawTextTab);
                mainTabPane.getSelectionModel().select(searchTab);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Signed Out");
                alert.setHeaderText("Successfully Signed Out");
                alert.setContentText("You have been signed out successfully. Thank you for using CV Management System.");
                alert.getDialogPane().getStyleClass().add("md-dialog");
                alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().addAll("md-button", "md-button-filled");
                alert.showAndWait();
            }
        });
    }

    private boolean hasValidToken() {
        return tokenManager != null && tokenManager.hasActiveToken("access_token");
    }

    public void setJwtToken(String token) {
        this.jwtToken = token;
        if (token != null && tokenManager != null) {
            tokenManager.saveNewToken("access_token", token,
                    java.time.LocalDateTime.now().plusHours(24).toString());
        }
        updateTokensInPanels();
        updateTabVisibility();
    }

    public String getJwtToken() {
        return jwtToken;
    }

    private void updateTokensInPanels() {
        if (uploadPanel != null) uploadPanel.setToken(jwtToken);
        if (viewCVPanel != null) viewCVPanel.setToken(jwtToken);
        if (auditLogPanel != null) auditLogPanel.setToken(jwtToken);
        if (searchPanel != null) searchPanel.setToken(jwtToken);
    }

    public static void main(String[] args) {
        launch(args);
    }
}