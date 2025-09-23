package com.vertex.cv_app.java_fx.dialog;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MaterialLoginDialog extends Stage {

    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginButton, cancelButton;
    private Label statusLabel;
    private CV_APP parentApp;
    private String serverUrl;
    private ProgressIndicator progressIndicator;

    public MaterialLoginDialog(Stage parent, String serverUrl, CV_APP app) {
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Sign In - CV Management System");
        setResizable(false);

        initializeMaterialUI();
        setupEventHandlers();
    }

    private void initializeMaterialUI() {
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().addAll("md-dialog", "md-spacing-24");
        mainContainer.setAlignment(Pos.CENTER);

        // Material Design header
        VBox headerSection = createMaterialHeader();

        // Material Design form
        VBox formSection = createMaterialForm();

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("md-progress-circular");
        progressIndicator.setPrefSize(40, 40);
        progressIndicator.setVisible(false);

        // Material Design buttons
        HBox buttonSection = createMaterialButtons();

        mainContainer.getChildren().addAll(headerSection, formSection, progressIndicator, buttonSection);

        Scene scene = new Scene(mainContainer, 400, 500);

        // Load Material Design CSS
        try {
            String cssResource = getClass().getResource("/styles/material-design.css").toExternalForm();
            scene.getStylesheets().add(cssResource);
        } catch (NullPointerException e) {
            System.out.println("Warning: Material Design CSS file not found for dialog.");
        }

        setScene(scene);
    }

    private VBox createMaterialHeader() {
        VBox headerSection = new VBox();
        headerSection.getStyleClass().addAll("md-spacing-16");
        headerSection.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Welcome Back");
        titleLabel.getStyleClass().add("md-headline-medium");

        Label subtitleLabel = new Label("Sign in to your CV Management account");
        subtitleLabel.getStyleClass().add("md-body-large");

        headerSection.getChildren().addAll(titleLabel, subtitleLabel);
        return headerSection;
    }

    private VBox createMaterialForm() {
        VBox formSection = new VBox();
        formSection.getStyleClass().addAll("md-spacing-20", "md-padding-16");
        formSection.setAlignment(Pos.CENTER);

        // Username field with Material Design styling
        VBox usernameSection = new VBox();
        usernameSection.getStyleClass().add("md-spacing-8");

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("md-label-large");

        usernameField = new TextField();
        usernameField.getStyleClass().add("md-text-field-outlined");
        usernameField.setPromptText("Enter your username");
        usernameField.setPrefWidth(320);

        usernameSection.getChildren().addAll(usernameLabel, usernameField);

        // Password field with Material Design styling
        VBox passwordSection = new VBox();
        passwordSection.getStyleClass().add("md-spacing-8");

        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("md-label-large");

        passwordField = new PasswordField();
        passwordField.getStyleClass().add("md-text-field-outlined");
        passwordField.setPromptText("Enter your password");
        passwordField.setPrefWidth(320);

        passwordSection.getChildren().addAll(passwordLabel, passwordField);

        // Status label for messages
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("md-body-small");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(320);

        formSection.getChildren().addAll(usernameSection, passwordSection, statusLabel);
        return formSection;
    }

    private HBox createMaterialButtons() {
        HBox buttonSection = new HBox();
        buttonSection.getStyleClass().add("md-spacing-16");
        buttonSection.setAlignment(Pos.CENTER);

        cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("md-button", "md-button-outlined");
        cancelButton.setPrefWidth(140);

        loginButton = new Button("Sign In");
        loginButton.getStyleClass().addAll("md-button", "md-button-filled");
        loginButton.setPrefWidth(140);

        buttonSection.getChildren().addAll(cancelButton, loginButton);
        return buttonSection;
    }

    private void setupEventHandlers() {
        loginButton.setOnAction(e -> performMaterialLogin());
        cancelButton.setOnAction(e -> close());

        // Allow Enter key to trigger login
        passwordField.setOnAction(e -> performMaterialLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());
    }

    private void performMaterialLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        // Show Material Design loading state
        statusLabel.setText("Signing in...");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error");
        statusLabel.getStyleClass().add("md-status-info");
        loginButton.setDisable(true);
        cancelButton.setDisable(true);
        progressIndicator.setVisible(true);

        // Create background task for login
        Task<HttpClientUtil.LoginResult> loginTask = new Task<HttpClientUtil.LoginResult>() {
            @Override
            protected HttpClientUtil.LoginResult call() throws Exception {
                return HttpClientUtil.login(serverUrl, username, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            HttpClientUtil.LoginResult result = loginTask.getValue();

            if (result.success && result.token != null) {
                // Login successful
                statusLabel.setText("Welcome back!");
                statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
                statusLabel.getStyleClass().add("md-status-success");

                // Set token in parent app
                parentApp.setJwtToken(result.token);

                // Show Material Design success message and close dialog
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Sign In Successful");
                alert.setHeaderText("Welcome Back!");
                alert.setContentText("You have been signed in successfully. Welcome to CV Management System.");
                alert.getDialogPane().getStyleClass().add("md-dialog");

                // Style alert buttons
                alert.getDialogPane().lookupButton(ButtonType.OK)
                        .getStyleClass().addAll("md-button", "md-button-filled");

                alert.showAndWait();
                close();
            } else {
                // Login failed
                statusLabel.setText(result.errorMessage != null ? result.errorMessage : "Sign in failed. Please check your credentials and try again.");
                statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                statusLabel.getStyleClass().add("md-status-error");

                // Reset UI
                loginButton.setDisable(false);
                cancelButton.setDisable(false);
                progressIndicator.setVisible(false);

                // Clear password field for security
                passwordField.clear();
                passwordField.requestFocus();
            }
        });

        loginTask.setOnFailed(e -> {
            // Handle network errors or exceptions
            statusLabel.setText("Connection error. Please check your network connection and try again.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");

            loginButton.setDisable(false);
            cancelButton.setDisable(false);
            progressIndicator.setVisible(false);

            passwordField.clear();
            passwordField.requestFocus();
        });

        // Start the login task in a background thread
        new Thread(loginTask).start();
    }
}