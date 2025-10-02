package com.vertex.cv_app.java_fx.dialog;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONObject;

public class MaterialRegisterDialog extends Stage {

    private TextField usernameField;
    private PasswordField passwordField, confirmPasswordField;
    private Button registerButton, cancelButton;
    private Label statusLabel;
    private CV_APP parentApp;
    private String serverUrl;
    private ProgressIndicator progressIndicator;
    private CheckBox termsCheckBox;

    public MaterialRegisterDialog(Stage parent, String serverUrl, CV_APP app) {
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Create Account - CV Management System");
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

        Scene scene = new Scene(mainContainer, 450, 700);

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

        Label titleLabel = new Label("Create Account");
        titleLabel.getStyleClass().add("md-headline-medium");

        Label subtitleLabel = new Label("Join CV Management System today");
        subtitleLabel.getStyleClass().add("md-body-large");

        headerSection.getChildren().addAll(titleLabel, subtitleLabel);
        return headerSection;
    }

    private VBox createMaterialForm() {
        VBox formSection = new VBox();
        formSection.getStyleClass().addAll("md-spacing-16", "md-padding-16");
        formSection.setAlignment(Pos.CENTER);

        // Username field
        VBox usernameSection = createFieldSection("Username *", "Choose a unique username");
        usernameField = (TextField) usernameSection.getChildren().get(1);

        // Password field
        VBox passwordSection = createPasswordSection("Password *", "Choose a strong password (min 6 characters)");
        passwordField = (PasswordField) passwordSection.getChildren().get(1);

        // Confirm password field
        VBox confirmPasswordSection = createPasswordSection("Confirm Password *", "Confirm your password");
        confirmPasswordField = (PasswordField) confirmPasswordSection.getChildren().get(1);

        // Terms checkbox
        termsCheckBox = new CheckBox("I agree to the Terms and Conditions");
        termsCheckBox.getStyleClass().add("md-body-medium");

        // Status label for messages
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("md-body-small");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(350);

        formSection.getChildren().addAll(
                usernameSection, passwordSection, confirmPasswordSection,
                termsCheckBox, statusLabel
        );
        return formSection;
    }

    private VBox createFieldSection(String labelText, String promptText) {
        VBox section = new VBox();
        section.getStyleClass().add("md-spacing-8");

        Label label = new Label(labelText);
        label.getStyleClass().add("md-label-large");

        TextField textField = new TextField();
        textField.getStyleClass().add("md-text-field-outlined");
        textField.setPromptText(promptText);
        textField.setPrefWidth(350);

        section.getChildren().addAll(label, textField);
        return section;
    }

    private VBox createPasswordSection(String labelText, String promptText) {
        VBox section = new VBox();
        section.getStyleClass().add("md-spacing-8");

        Label label = new Label(labelText);
        label.getStyleClass().add("md-label-large");

        PasswordField passwordField = new PasswordField();
        passwordField.getStyleClass().add("md-text-field-outlined");
        passwordField.setPromptText(promptText);
        passwordField.setPrefWidth(350);

        section.getChildren().addAll(label, passwordField);
        return section;
    }

    private HBox createMaterialButtons() {
        HBox buttonSection = new HBox();
        buttonSection.getStyleClass().add("md-spacing-16");
        buttonSection.setAlignment(Pos.CENTER);

        cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("md-button", "md-button-outlined");
        cancelButton.setPrefWidth(150);

        registerButton = new Button("Create Account");
        registerButton.getStyleClass().addAll("md-button", "md-button-filled");
        registerButton.setPrefWidth(150);

        buttonSection.getChildren().addAll(cancelButton, registerButton);
        return buttonSection;
    }

    private void setupEventHandlers() {
        registerButton.setOnAction(e -> performMaterialRegistration());
        cancelButton.setOnAction(e -> close());

        // Enable/disable register button based on terms checkbox
        termsCheckBox.setOnAction(e -> updateRegisterButtonState());

        // Real-time validation
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> validateInput());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateInput());
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateInput());


        passwordField.setOnAction(e -> confirmPasswordField.requestFocus());
        confirmPasswordField.setOnAction(e -> {
            if (!registerButton.isDisabled()) {
                performMaterialRegistration();
            }
        });
    }

    private void validateInput() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.length() > 0 && username.length() < 3) {
            statusLabel.setText("Username must be at least 3 characters long.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-warning");
        } else if (password.length() > 0 && password.length() < 6) {
            statusLabel.setText("Password must be at least 6 characters long.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-warning");
        } else if (confirmPassword.length() > 0 && !password.equals(confirmPassword)) {
            statusLabel.setText("Passwords do not match.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-warning");
        } else {
            statusLabel.setText("");
            statusLabel.getStyleClass().removeAll("md-status-warning", "md-status-error", "md-status-info");
        }

        updateRegisterButtonState();
    }

    private void updateRegisterButtonState() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        boolean isValid = !username.isEmpty() &&
                username.length() >= 3 &&
                !password.isEmpty() &&
                password.length() >= 6 &&
                password.equals(confirmPassword) &&
                termsCheckBox.isSelected();

        registerButton.setDisable(!isValid);
    }

    private void performMaterialRegistration() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Final validation
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password are required.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        if (username.length() < 3) {
            statusLabel.setText("Username must be at least 3 characters long.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        if (password.length() < 6) {
            statusLabel.setText("Password must be at least 6 characters long.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        if (!password.equals(confirmPasswordField.getText())) {
            statusLabel.setText("Passwords do not match.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        if (!termsCheckBox.isSelected()) {
            statusLabel.setText("You must agree to the Terms and Conditions.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");
            return;
        }

        // Show Material Design loading state
        statusLabel.setText("Creating your account...");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error", "md-status-warning");
        statusLabel.getStyleClass().add("md-status-info");
        registerButton.setDisable(true);
        cancelButton.setDisable(true);
        progressIndicator.setVisible(true);

        // Create background task for registration
        Task<RegisterResult> registerTask = new Task<RegisterResult>() {
            @Override
            protected RegisterResult call() throws Exception {
                return performRegistrationRequest(username, password);
            }
        };

        registerTask.setOnSucceeded(e -> {
            RegisterResult result = registerTask.getValue();

            if (result.success) {
                statusLabel.setText("Account created successfully!");
                statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info", "md-status-warning");
                statusLabel.getStyleClass().add("md-status-success");

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Registration Successful");
                alert.setHeaderText("Welcome to CV Management System!");
                alert.setContentText("Your account has been created successfully!\n\n" +
                        "Username: " + username + "\n" +
                        "You can now sign in with your credentials.");

                alert.getDialogPane().getStyleClass().add("md-dialog");
                alert.getDialogPane().lookupButton(ButtonType.OK)
                        .getStyleClass().addAll("md-button", "md-button-filled");

                alert.showAndWait();
                close();
            } else {
                statusLabel.setText(result.errorMessage);
                statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
                statusLabel.getStyleClass().add("md-status-error");

                registerButton.setDisable(false);
                cancelButton.setDisable(false);
                progressIndicator.setVisible(false);
            }
        });

        registerTask.setOnFailed(e -> {
            statusLabel.setText("Connection error. Please check your network and try again.");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info", "md-status-warning");
            statusLabel.getStyleClass().add("md-status-error");

            registerButton.setDisable(false);
            cancelButton.setDisable(false);
            progressIndicator.setVisible(false);
        });

        new Thread(registerTask).start();
    }

    private RegisterResult performRegistrationRequest(String username, String password) {
        String registerUrl = serverUrl + "/register";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost registerRequest = new HttpPost(registerUrl);
            registerRequest.setHeader("Content-Type", "application/json");

            // Create JSON payload
            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("username", username);
            jsonPayload.put("password", password);

            StringEntity entity = new StringEntity(jsonPayload.toString(), ContentType.APPLICATION_JSON);
            registerRequest.setEntity(entity);

            ClassicHttpResponse response = httpClient.execute(registerRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200 || statusCode == 201) {
                try {
                    JSONObject jsonResponse = new JSONObject(responseString);
                    String message = jsonResponse.optString("message", "Registration successful!");
                    return new RegisterResult(true, message);
                } catch (Exception ex) {
                    return new RegisterResult(true, "Registration successful!");
                }
            } else {
                String errorMsg = "Registration failed (HTTP " + statusCode + ")";

                try {
                    JSONObject errorJson = new JSONObject(responseString);
                    if (errorJson.has("message")) {
                        errorMsg = errorJson.getString("message");
                    } else if (errorJson.has("error")) {
                        errorMsg = errorJson.getString("error");
                    }
                } catch (Exception ex) {
                    errorMsg = errorMsg + ": " + responseString;
                }

                return new RegisterResult(false, errorMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new RegisterResult(false, "Network error: " + e.getMessage());
        }
    }

    // Inner class for registration result
    private static class RegisterResult {
        public boolean success;
        public String errorMessage;

        public RegisterResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}