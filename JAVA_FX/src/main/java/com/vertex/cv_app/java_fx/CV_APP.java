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
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class CV_APP extends Application {

    public static final String SEARCH_VIEW = "SEARCH";
    public static final String UPLOAD_VIEW = "UPLOAD";
    public static final String VIEW_CV_VIEW = "VIEW_CV";
    public static final String RAW_TEXT_VIEW = "RAW_TEXT";
    public static final String AUDIT_LOG_VIEW = "AUDIT_LOG";

    private String serverUrl = "http://13.250.35.49:8000";
    private String jwtToken = null;
    private JWTTokenManager tokenManager;

    private StackPane mainContainer;
    private MaterialSearchPanel searchPanel;
    private MaterialUploadPanel uploadPanel;
    private ViewCVPanel viewCVPanel;
    private MaterialRawView rawView;
    private MaterialAuditLogPanel auditLogPanel;

    private Stage primaryStage;
    private HBox appBar;
    private VBox navigationRail;

    // Menu items that need state management
    private Button loginButton;
    private Button registerButton;
    private Button logoutButton;
    private Button auditLogButton;

    // Current view indicator
    private Label currentViewLabel;

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

        // Create Material Design Navigation Rail (Left)
        navigationRail = createMaterialNavigationRail();
        root.setLeft(navigationRail);

        // Create main content area with Material Design
        mainContainer = new StackPane();
        mainContainer.getStyleClass().add("md-card");
        mainContainer.setPadding(new Insets(0));

        // Initialize Material Design panels
        searchPanel = new MaterialSearchPanel(this, serverUrl);
        uploadPanel = new MaterialUploadPanel(this, serverUrl);
        viewCVPanel = new ViewCVPanel(this, serverUrl);
        rawView = new MaterialRawView(this, serverUrl);
        auditLogPanel = new MaterialAuditLogPanel(this, serverUrl);

        // Add all panels to main container
        mainContainer.getChildren().addAll(
                searchPanel, uploadPanel, viewCVPanel, rawView, auditLogPanel
        );

        root.setCenter(mainContainer);

        // Create Material Design scene
        Scene scene = new Scene(root, 1400, 900);

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

        // Update navigation state
        updateNavigationUI();

        // Set initial navigation selection AFTER the rail is fully initialized
        if (navigationRail != null && !navigationRail.getChildren().isEmpty()) {
            // Find the search button (first button in the rail)
            for (javafx.scene.Node node : navigationRail.getChildren()) {
                if (node instanceof Button && node != navigationRail.getChildren().get(navigationRail.getChildren().size() - 1)) {
                    Button searchButton = (Button) node;
                    if (searchButton.getTooltip() != null && "Search".equals(searchButton.getTooltip().getText())) {
                        updateNavSelection(searchButton);
                        break;
                    }
                }
            }
        }

        showView(SEARCH_VIEW);
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

        // Current view indicator
        currentViewLabel = new Label("Search");
        currentViewLabel.getStyleClass().addAll("md-body-medium", "md-spacing-16");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // User actions in app bar
        HBox userActions = createUserActions();

        appBar.getChildren().addAll(appTitle, currentViewLabel, spacer, userActions);
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

        userActions.getChildren().addAll(loginButton, registerButton, logoutButton, aboutButton);
        return userActions;
    }

    private VBox createMaterialNavigationRail() {
        VBox navRail = new VBox();
        navRail.getStyleClass().addAll("md-nav-rail", "md-spacing-8");
        navRail.setPrefWidth(80);
        navRail.setAlignment(Pos.TOP_CENTER);

        // Navigation items
        Button searchNavButton = createNavButton("Search", "🔍");
        searchNavButton.setOnAction(e -> {
            showView(SEARCH_VIEW);
            updateNavSelection(searchNavButton);
        });

        Button uploadNavButton = createNavButton("Upload", "📤");
        uploadNavButton.setOnAction(e -> {
            showView(UPLOAD_VIEW);
            updateNavSelection(uploadNavButton);
        });

        auditLogButton = createNavButton("Audit", "📊");
        auditLogButton.setOnAction(e -> {
            showAuditLogView();
            updateNavSelection(auditLogButton);
        });

        Button exitButton = createNavButton("Exit", "🚪");
        exitButton.setOnAction(e -> System.exit(0));

        navRail.getChildren().addAll(
                searchNavButton, uploadNavButton, auditLogButton,
                new Region(), // Spacer
                exitButton
        );

        VBox.setVgrow(navRail.getChildren().get(3), Priority.ALWAYS); // Make spacer grow

        return navRail;
    }

    private Button createNavButton(String tooltip, String icon) {
        Button button = new Button(icon);
        button.getStyleClass().addAll("md-button", "md-button-icon");
        button.setTooltip(new Tooltip(tooltip));
        button.setPrefSize(56, 56);
        return button;
    }

    private void updateNavSelection(Button selectedButton) {
        if (navigationRail == null) return;

        // Remove selection from all nav buttons
        for (javafx.scene.Node node : navigationRail.getChildren()) {
            if (node instanceof Button) {
                Button btn = (Button) node;
                btn.getStyleClass().removeAll("md-button-icon-filled");
                if (!btn.getStyleClass().contains("md-button-icon")) {
                    btn.getStyleClass().add("md-button-icon");
                }
            }
        }

        // Add selection to clicked button
        selectedButton.getStyleClass().removeAll("md-button-icon");
        selectedButton.getStyleClass().add("md-button-icon-filled");
    }

    private void updateNavigationUI() {
        boolean loggedIn = isLoggedIn();
        loginButton.setVisible(!loggedIn);
        loginButton.setManaged(!loggedIn);
        registerButton.setVisible(!loggedIn);
        registerButton.setManaged(!loggedIn);
        logoutButton.setVisible(loggedIn);
        logoutButton.setManaged(loggedIn);
        if (auditLogButton != null) {
            auditLogButton.setDisable(!loggedIn);
        }
    }

    public boolean isLoggedIn() {
        return jwtToken != null && !jwtToken.isEmpty();
    }

    public void showView(String viewName) {
        // Hide all panels first
        if (searchPanel != null) searchPanel.setVisible(false);
        if (uploadPanel != null) uploadPanel.setVisible(false);
        if (viewCVPanel != null) viewCVPanel.setVisible(false);
        if (rawView != null) rawView.setVisible(false);
        if (auditLogPanel != null) auditLogPanel.setVisible(false);

        // Show the requested panel and update app bar
        switch (viewName) {
            case SEARCH_VIEW:
                if (searchPanel != null) {
                    searchPanel.setVisible(true);
                    searchPanel.toFront();
                }
                currentViewLabel.setText("Search CVs");
                break;
            case UPLOAD_VIEW:
                if (uploadPanel != null) {
                    uploadPanel.setVisible(true);
                    uploadPanel.toFront();
                }
                currentViewLabel.setText("Upload CVs");
                break;
            case VIEW_CV_VIEW:
                if (viewCVPanel != null) {
                    viewCVPanel.setVisible(true);
                    viewCVPanel.toFront();
                }
                currentViewLabel.setText("CV Details");
                break;
            case RAW_TEXT_VIEW:
                if (rawView != null) {
                    rawView.setVisible(true);
                    rawView.toFront();
                }
                currentViewLabel.setText("Raw Text");
                break;
            case AUDIT_LOG_VIEW:
                if (auditLogPanel != null) {
                    auditLogPanel.setVisible(true);
                    auditLogPanel.toFront();
                }
                currentViewLabel.setText("Audit Logs");
                break;
        }
    }

    public void showAuditLogView() {
        if (auditLogPanel != null) {
            auditLogPanel.refreshLogs();
        }
        showView(AUDIT_LOG_VIEW);
    }

    public void showCVDetails(String cvId) {
        if (viewCVPanel != null) {
            viewCVPanel.loadCV(cvId);
        }
        showView(VIEW_CV_VIEW);
    }

    public void showRawTextView(String cvId, ViewCVPanel sourcePanel) {
        if (rawView != null) {
            rawView.loadRawText(cvId);
        }
        showView(RAW_TEXT_VIEW);
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
                        "• Material Design 3 interface\n" +
                        "• Advanced search and filtering\n" +
                        "• Secure file upload (PDF & DOCX)\n" +
                        "• Comprehensive audit logging\n" +
                        "• Modern authentication system\n" +
                        "• Responsive design patterns\n\n" +
                        "Built with JavaFX & Material Design\n" +
                        "© 2024 Vertex Systems"
        );

        alert.getDialogPane().getStyleClass().add("md-dialog");

        // Style dialog buttons
        alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().addAll("md-button", "md-button-filled");

        alert.showAndWait();
    }

    private void performMaterialLogout() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Logout");
        confirmation.setHeaderText("Sign Out");
        confirmation.setContentText("Are you sure you want to sign out of your account?");
        confirmation.getDialogPane().getStyleClass().add("md-dialog");

        // Style dialog buttons
        confirmation.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().addAll("md-button", "md-button-filled");
        confirmation.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().addAll("md-button", "md-button-outlined");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                jwtToken = null;
                tokenManager.clearAllTokens();
                updateNavigationUI();
                showView(SEARCH_VIEW);

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
        updateNavigationUI();
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