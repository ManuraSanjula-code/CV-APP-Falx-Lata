package com.vertex.cv_app.java_fx.panels;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

class DownloadResult {
    public boolean success;
    public String message;
    public byte[] fileData;
    public String contentType;

    public DownloadResult(boolean success, String message, byte[] data, String type) {
        this.success = success;
        this.message = message;
        this.fileData = data;
        this.contentType = type;
    }

    public static DownloadResult downloadPdfData(String serverUrl, String cvId) {
        String downloadUrl = serverUrl + "/api/download_pdf/" + cvId;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet downloadRequest = new HttpGet(downloadUrl);
            ClassicHttpResponse response = httpClient.execute(downloadRequest);
            int statusCode = response.getCode();
            if (statusCode == 200) {
                byte[] fileData = EntityUtils.toByteArray(response.getEntity());
                Header contentTypeHeader = response.getFirstHeader("Content-Type");
                String contentType = contentTypeHeader != null ? contentTypeHeader.getValue() : "application/octet-stream";
                return new DownloadResult(true, "PDF downloaded successfully", fileData, contentType);
            } else {
                String errorMessage = EntityUtils.toString(response.getEntity());
                return new DownloadResult(false, "Server Error (" + statusCode + "): " + errorMessage, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new DownloadResult(false, "Network Error: " + e.getMessage(), null, null);
        }
    }

    public static DownloadResult downloadPdfToFile(String serverUrl, String cvId, String localFilePath) {
        String downloadUrl = serverUrl + "/api/download_pdf/" + cvId;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet downloadRequest = new HttpGet(downloadUrl);
            ClassicHttpResponse response = httpClient.execute(downloadRequest);
            int statusCode = response.getCode();
            if (statusCode == 200) {
                try (java.io.InputStream inputStream = response.getEntity().getContent()) {
                    Path targetPath = Paths.get(localFilePath);
                    Files.copy(inputStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return new DownloadResult(true, "PDF saved to " + localFilePath, null, null);
            } else {
                String errorMessage = EntityUtils.toString(response.getEntity());
                return new DownloadResult(false, "Server Error (" + statusCode + "): " + errorMessage, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new DownloadResult(false, "Download/Error: " + e.getMessage(), null, null);
        }
    }
}

public class ViewCVPanel extends VBox {

    private CV_APP parentApp;
    private String serverUrl;
    private String currentCvId;
    private JSONObject currentCvData;
    private String token;

    // UI Components
    private Label cvIdLabel;
    private Button backButton, editButton, saveButton, deleteButton, viewRawTextButton;
    private Button viewPdfButton, downloadPdfButton, fullViewButton;
    private WebView personalInfoView;
    private TextArea skillsArea, educationArea, experienceArea, otherInfoArea;
    private ScrollPane mainScrollPane;
    private VBox viewModePanel, editModePanel;
    private StackPane contentContainer;
    private boolean isEditMode = false;

    // Edit mode fields
    private TextField nameField, emailField, phoneField, addressField, githubField, linkedinField;
    private TextField customTypeField;
    private ComboBox<String> genderCombo, typeCombo;
    private TextArea skillsEditArea, educationEditArea, experienceEditArea;

    public ViewCVPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initializeUI();
        setupEventHandlers();

        // Apply Material Design styling
        getStyleClass().add("md-card");
        setPadding(new Insets(0));
    }

    public void setToken(String token) {
        this.token = token;
    }

    private void initializeUI() {
        setSpacing(16);
        setPadding(new Insets(16));

        // Top control bar
        HBox topBar = createMaterialTopBar();

        // Content container with Material Design
        contentContainer = new StackPane();
        contentContainer.getStyleClass().add("md-card-elevated");

        viewModePanel = createMaterialViewModePanel();
        editModePanel = createMaterialEditModePanel();

        viewModePanel.setVisible(true);
        editModePanel.setVisible(false);

        contentContainer.getChildren().addAll(viewModePanel, editModePanel);

        VBox.setVgrow(contentContainer, Priority.ALWAYS);
        getChildren().addAll(topBar, contentContainer);
    }

    private HBox createMaterialTopBar() {
        HBox topBar = new HBox(16);
        topBar.getStyleClass().addAll("md-card", "md-padding-16");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(16));

        backButton = createMaterialButton("← Back", "md-button-outlined");
        cvIdLabel = new Label("CV ID: ");
        cvIdLabel.getStyleClass().addAll("md-title-medium", "md-spacing-16");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        viewPdfButton = createMaterialButton("View PDF", "md-button-filled");
        downloadPdfButton = createMaterialButton("Download PDF", "md-button-filled");
        fullViewButton = createMaterialButton("Full View", "md-button-tonal");
        editButton = createMaterialButton("Edit", "md-button-tonal");
        viewRawTextButton = createMaterialButton("View Raw Text", "md-button-outlined");
        saveButton = createMaterialButton("Save", "md-button-filled");
        deleteButton = createMaterialButton("Delete", "md-button-filled");

        // Style delete button with error color
        deleteButton.setStyle("-fx-background-color: #BA1A1A; -fx-text-fill: white;");

        saveButton.setVisible(false);

        HBox buttonContainer = new HBox(8, viewPdfButton, downloadPdfButton, fullViewButton, editButton, viewRawTextButton, saveButton, deleteButton);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);

        topBar.getChildren().addAll(backButton, cvIdLabel, spacer, buttonContainer);
        return topBar;
    }

    private VBox createMaterialViewModePanel() {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(16));

        // Personal Info with WebView for HTML rendering
        VBox personalInfoBox = new VBox(8);
        personalInfoBox.getStyleClass().addAll("md-card", "md-padding-16");

        Label personalInfoLabel = new Label("Personal Information");
        personalInfoLabel.getStyleClass().add("md-headline-small");

        personalInfoView = new WebView();
        personalInfoView.setPrefHeight(250); // Increased height
        personalInfoView.setStyle("-fx-background-color: transparent;");

        personalInfoBox.getChildren().addAll(personalInfoLabel, personalInfoView);

        // Skills, Education, Experience sections with Material Design and expand buttons
        VBox skillsBox = createMaterialDisplaySectionWithExpand("Skills", skillsArea = new TextArea(), 200);
        VBox educationBox = createMaterialDisplaySectionWithExpand("Education", educationArea = new TextArea(), 250);
        VBox experienceBox = createMaterialDisplaySectionWithExpand("Experience", experienceArea = new TextArea(), 300);
        VBox otherInfoBox = createMaterialDisplaySection("Other Information", otherInfoArea = new TextArea(), 150);

        panel.getChildren().addAll(personalInfoBox, skillsBox, educationBox, experienceBox, otherInfoBox);

        ScrollPane scrollPane = new ScrollPane(panel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.getStyleClass().add("md-scroll-bar");

        VBox container = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return container;
    }

    private VBox createMaterialDisplaySection(String title, TextArea textArea, double prefHeight) {
        VBox container = new VBox(8);
        container.getStyleClass().addAll("md-card", "md-padding-16");

        Label label = new Label(title);
        label.getStyleClass().add("md-title-medium");

        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(prefHeight);
        textArea.getStyleClass().addAll("md-text-field", "expandable-text-area");
        textArea.setStyle("-fx-control-inner-background: #f8f8f8; -fx-font-size: 14px;");

        container.getChildren().addAll(label, textArea);
        return container;
    }

    private VBox createMaterialDisplaySectionWithExpand(String title, TextArea textArea, double prefHeight) {
        VBox container = new VBox(8);
        container.getStyleClass().addAll("md-card", "md-padding-16");

        // Header with title and expand button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(title);
        label.getStyleClass().add("md-title-medium");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button expandButton = createMaterialButton("🔍 Expand", "md-button-text");
        expandButton.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");

        // Set up expand button action
        expandButton.setOnAction(e -> showExpandedView(title, textArea.getText()));

        headerBox.getChildren().addAll(label, spacer, expandButton);

        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(prefHeight);
        textArea.getStyleClass().addAll("md-text-field", "expandable-text-area");
        textArea.setStyle("-fx-control-inner-background: #f8f8f8; -fx-font-size: 14px;");

        container.getChildren().addAll(headerBox, textArea);
        return container;
    }

    private void showExpandedView(String title, String content) {
        Stage expandedStage = new Stage();
        expandedStage.setTitle("Full View - " + title);
        expandedStage.initModality(Modality.APPLICATION_MODAL);

        // Create expanded content
        VBox expandedContent = new VBox(16);
        expandedContent.setPadding(new Insets(24));
        expandedContent.getStyleClass().add("md-card");

        // Title
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("md-headline-medium");
        titleLabel.setStyle("-fx-text-fill: #6750A4; -fx-font-weight: bold;");

        // Expanded text area
        TextArea expandedTextArea = new TextArea(content);
        expandedTextArea.setEditable(false);
        expandedTextArea.setWrapText(true);
        expandedTextArea.setPrefHeight(500);
        expandedTextArea.setPrefWidth(700);
        expandedTextArea.getStyleClass().add("md-text-field");
        expandedTextArea.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-family: 'Segoe UI', 'Roboto', Arial, sans-serif; " +
                        "-fx-control-inner-background: #fafafa; " +
                        "-fx-padding: 16px;"
        );

        // Close button
        Button closeButton = createMaterialButton("Close", "md-button-filled");
        closeButton.setOnAction(e -> expandedStage.close());

        HBox buttonBox = new HBox(closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        expandedContent.getChildren().addAll(titleLabel, expandedTextArea, buttonBox);

        ScrollPane expandedScrollPane = new ScrollPane(expandedContent);
        expandedScrollPane.setFitToWidth(true);
        expandedScrollPane.setStyle("-fx-background-color: transparent;");

        Scene expandedScene = new Scene(expandedScrollPane, 800, 600);

        // Apply CSS if available
        try {
            String cssResource = getClass().getResource("/styles/material-design.css").toExternalForm();
            expandedScene.getStylesheets().add(cssResource);
        } catch (Exception ex) {
            // CSS not found, continue without styling
        }

        expandedStage.setScene(expandedScene);
        expandedStage.show();
    }

    private void showFullViewDialog() {
        if (currentCvData == null) {
            showMaterialError("No CV data available for full view");
            return;
        }

        Stage fullViewStage = new Stage();
        fullViewStage.setTitle("Full CV View - " + currentCvId);
        fullViewStage.initModality(Modality.APPLICATION_MODAL);

        TabPane fullViewTabPane = new TabPane();
        fullViewTabPane.getStyleClass().add("md-tab-pane");
        fullViewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Personal Info Tab
        Tab personalTab = new Tab("👤 Personal Info");
        WebView personalWebView = new WebView();
        personalWebView.setPrefHeight(400);
        personalWebView.getEngine().loadContent(generatePersonalInfoHTML());
        personalTab.setContent(personalWebView);

        // Skills Tab
        Tab skillsTab = new Tab("🛠️ Skills");
        TextArea fullSkillsArea = createFullViewTextArea(skillsArea.getText());
        skillsTab.setContent(new ScrollPane(fullSkillsArea));

        // Education Tab
        Tab educationTab = new Tab("🎓 Education");
        TextArea fullEducationArea = createFullViewTextArea(educationArea.getText());
        educationTab.setContent(new ScrollPane(fullEducationArea));

        // Experience Tab
        Tab experienceTab = new Tab("💼 Experience");
        TextArea fullExperienceArea = createFullViewTextArea(experienceArea.getText());
        experienceTab.setContent(new ScrollPane(fullExperienceArea));

        // Other Info Tab
        Tab otherTab = new Tab("📋 Other Info");
        TextArea fullOtherArea = createFullViewTextArea(otherInfoArea.getText());
        otherTab.setContent(new ScrollPane(fullOtherArea));

        fullViewTabPane.getTabs().addAll(personalTab, skillsTab, educationTab, experienceTab, otherTab);

        // Create close button
        VBox fullViewContainer = new VBox(16);
        fullViewContainer.setPadding(new Insets(16));

        Button closeFullViewButton = createMaterialButton("Close Full View", "md-button-filled");
        closeFullViewButton.setOnAction(e -> fullViewStage.close());

        HBox closeButtonBox = new HBox(closeFullViewButton);
        closeButtonBox.setAlignment(Pos.CENTER_RIGHT);
        closeButtonBox.setPadding(new Insets(16, 0, 0, 0));

        fullViewContainer.getChildren().addAll(fullViewTabPane, closeButtonBox);
        VBox.setVgrow(fullViewTabPane, Priority.ALWAYS);

        Scene fullViewScene = new Scene(fullViewContainer, 1000, 700);

        // Apply CSS if available
        try {
            String cssResource = getClass().getResource("/styles/material-design.css").toExternalForm();
            fullViewScene.getStylesheets().add(cssResource);
        } catch (Exception ex) {
            // CSS not found, continue without styling
        }

        fullViewStage.setScene(fullViewScene);
        fullViewStage.show();
    }

    private TextArea createFullViewTextArea(String content) {
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(500);
        textArea.getStyleClass().add("md-text-field");
        textArea.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-family: 'Segoe UI', 'Roboto', Arial, sans-serif; " +
                        "-fx-control-inner-background: #fafafa; " +
                        "-fx-padding: 20px;"
        );
        return textArea;
    }

    private String generatePersonalInfoHTML() {
        StringBuilder html = new StringBuilder("<html><body style='font-family: Roboto, Arial, sans-serif; padding: 24px; color: #1C1B1F; font-size: 16px; line-height: 1.6;'>");

        if (currentCvData != null && currentCvData.has("personal_info")) {
            JSONObject info = currentCvData.getJSONObject("personal_info");
            html.append("<h2 style='color: #6750A4; margin-bottom: 24px; font-size: 28px;'>Personal Information</h2>");
            html.append("<table style='width: 100%; border-collapse: collapse; font-size: 16px;'>");

            String name = info.optString("name", "");
            if (!name.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600; width: 30%;'>Name:</td><td style='padding: 12px;'>").append(name).append("</td></tr>");

            String email = info.optString("email", "");
            if (!email.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>Email:</td><td style='padding: 12px;'>").append(email).append("</td></tr>");

            String phone = info.optString("phone", "");
            if (!phone.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>Phone:</td><td style='padding: 12px;'>").append(phone).append("</td></tr>");

            String address = info.optString("address", "");
            if (!address.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>Address:</td><td style='padding: 12px;'>").append(address).append("</td></tr>");

            String github = info.optString("github", "");
            if (!github.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>GitHub:</td><td style='padding: 12px;'>").append(github).append("</td></tr>");

            String linkedin = info.optString("linkedin", "");
            if (!linkedin.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>LinkedIn:</td><td style='padding: 12px;'>").append(linkedin).append("</td></tr>");

            String gender = info.optString("gender", "");
            if (!gender.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>Gender:</td><td style='padding: 12px;'>").append(gender).append("</td></tr>");

            String type = info.optString("type", "");
            if (!type.isEmpty()) html.append("<tr><td style='padding: 12px; font-weight: 600;'>Type:</td><td style='padding: 12px;'>").append(type).append("</td></tr>");

            html.append("</table>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private VBox createMaterialEditModePanel() {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(16));

        // Personal Info Fields with Material Design
        VBox personalInfoBox = new VBox(8);
        personalInfoBox.getStyleClass().addAll("md-card", "md-padding-16");

        Label personalLabel = new Label("Personal Information");
        personalLabel.getStyleClass().add("md-headline-small");

        GridPane personalGrid = new GridPane();
        personalGrid.setHgap(12);
        personalGrid.setVgap(8);
        personalGrid.setPadding(new Insets(8, 0, 8, 0));

        nameField = createMaterialTextField("Name");
        emailField = createMaterialTextField("Email");
        phoneField = createMaterialTextField("Phone");
        addressField = createMaterialTextField("Address");
        githubField = createMaterialTextField("GitHub");
        linkedinField = createMaterialTextField("LinkedIn");

        addMaterialFieldToGrid(personalGrid, "Name:", nameField, 0);
        addMaterialFieldToGrid(personalGrid, "Email:", emailField, 1);
        addMaterialFieldToGrid(personalGrid, "Phone:", phoneField, 2);
        addMaterialFieldToGrid(personalGrid, "Address:", addressField, 3);
        addMaterialFieldToGrid(personalGrid, "GitHub:", githubField, 4);
        addMaterialFieldToGrid(personalGrid, "LinkedIn:", linkedinField, 5);

        // Gender and Type with custom type input
        HBox detailsBox = new HBox(12);
        detailsBox.setAlignment(Pos.CENTER_LEFT);

        genderCombo = createMaterialComboBox();
        genderCombo.getItems().addAll("NONE", "Male", "Female", "Other");
        genderCombo.setValue("NONE");

        typeCombo = createMaterialComboBox();
        typeCombo.getItems().addAll("NONE", "HR Manager", "Web Developer", "Software Engineer", "Designer", "Analyst", "Manager", "Other");
        typeCombo.setValue("NONE");

        customTypeField = createMaterialTextField("Custom Type");
        customTypeField.setVisible(false);
        customTypeField.setManaged(false);

        typeCombo.setOnAction(e -> {
            String selectedType = typeCombo.getValue();
            if ("Other".equals(selectedType)) {
                customTypeField.setVisible(true);
                customTypeField.setManaged(true);
            } else {
                customTypeField.setVisible(false);
                customTypeField.setManaged(false);
                customTypeField.clear();
            }
        });

        detailsBox.getChildren().addAll(
                createMaterialLabel("Gender:"), genderCombo,
                createMaterialLabel("Type:"), typeCombo,
                customTypeField
        );

        personalInfoBox.getChildren().addAll(personalLabel, personalGrid, detailsBox);

        // Edit areas for JSON data with Material Design - Larger text areas
        VBox skillsEditBox = createMaterialEditSectionExpanded("Skills (JSON)", skillsEditArea = new TextArea(), 200);
        VBox educationEditBox = createMaterialEditSectionExpanded("Education (JSON)", educationEditArea = new TextArea(), 200);
        VBox experienceEditBox = createMaterialEditSectionExpanded("Experience (JSON)", experienceEditArea = new TextArea(), 250);

        panel.getChildren().addAll(personalInfoBox, skillsEditBox, educationEditBox, experienceEditBox);

        ScrollPane scrollPane = new ScrollPane(panel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.getStyleClass().add("md-scroll-bar");

        VBox container = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return container;
    }

    private VBox createMaterialEditSectionExpanded(String title, TextArea textArea, double prefHeight) {
        VBox container = new VBox(8);
        container.getStyleClass().addAll("md-card", "md-padding-16");

        // Header with expand button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(title);
        label.getStyleClass().add("md-title-medium");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button expandEditButton = createMaterialButton("📝 Edit Full", "md-button-text");
        expandEditButton.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");
        expandEditButton.setOnAction(e -> showExpandedEditView(title, textArea));

        headerBox.getChildren().addAll(label, spacer, expandEditButton);

        textArea.setPrefHeight(prefHeight);
        textArea.setWrapText(true);
        textArea.getStyleClass().addAll("md-text-field", "expandable-text-area");
        textArea.setStyle("-fx-font-family: 'Roboto Mono', 'Courier New', monospace; -fx-font-size: 14px;");

        container.getChildren().addAll(headerBox, textArea);
        return container;
    }

    private void showExpandedEditView(String title, TextArea originalTextArea) {
        Stage expandedStage = new Stage();
        expandedStage.setTitle("Edit Full - " + title);
        expandedStage.initModality(Modality.APPLICATION_MODAL);

        VBox expandedContent = new VBox(16);
        expandedContent.setPadding(new Insets(24));
        expandedContent.getStyleClass().add("md-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("md-headline-medium");
        titleLabel.setStyle("-fx-text-fill: #6750A4; -fx-font-weight: bold;");

        TextArea expandedTextArea = new TextArea(originalTextArea.getText());
        expandedTextArea.setWrapText(true);
        expandedTextArea.setPrefHeight(450);
        expandedTextArea.setPrefWidth(700);
        expandedTextArea.getStyleClass().add("md-text-field");
        expandedTextArea.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-family: 'Roboto Mono', 'Courier New', monospace; " +
                        "-fx-control-inner-background: #fafafa; " +
                        "-fx-padding: 16px;"
        );

        // Buttons with proper functionality
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 0, 0, 0));

        Button cancelButton = createMaterialButton("Cancel", "md-button-outlined");
        cancelButton.setOnAction(e -> expandedStage.close());

        Button saveToTextAreaButton = createMaterialButton("Save to Form", "md-button-tonal");
        saveToTextAreaButton.setOnAction(e -> {
            // Update the original text area in the form
            originalTextArea.setText(expandedTextArea.getText());
            showMaterialSuccess("Content Updated", "The content has been updated in the form. Click 'Save' in the main form to save to database.");
            expandedStage.close();
        });

        Button saveToMainButton = createMaterialButton("Save to Database", "md-button-filled");
        saveToMainButton.setOnAction(e -> {
            // Update the original text area and trigger main save
            originalTextArea.setText(expandedTextArea.getText());
            expandedStage.close();

            // Trigger the main save function
            saveChanges();
        });

        buttonBox.getChildren().addAll(cancelButton, saveToTextAreaButton, saveToMainButton);

        // Info label to explain the buttons
        Label infoLabel = new Label("• Save to Form: Updates the form only\n• Save to Database: Updates form and saves to server");
        infoLabel.getStyleClass().add("md-body-small");
        infoLabel.setStyle("-fx-text-fill: #666666; -fx-padding: 8px 0;");

        expandedContent.getChildren().addAll(titleLabel, expandedTextArea, infoLabel, buttonBox);

        ScrollPane expandedScrollPane = new ScrollPane(expandedContent);
        expandedScrollPane.setFitToWidth(true);
        expandedScrollPane.setStyle("-fx-background-color: transparent;");

        Scene expandedScene = new Scene(expandedScrollPane, 800, 650);

        try {
            String cssResource = getClass().getResource("/styles/material-design.css").toExternalForm();
            expandedScene.getStylesheets().add(cssResource);
        } catch (Exception ex) {
            // Continue without styling
        }

        expandedStage.setScene(expandedScene);
        expandedStage.show();
    }

    private TextField createMaterialTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("md-text-field");
        field.setPrefWidth(300);
        return field;
    }

    private ComboBox<String> createMaterialComboBox() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getStyleClass().add("md-combo-box");
        combo.setPrefWidth(200);
        return combo;
    }

    private Label createMaterialLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("md-label-medium");
        return label;
    }

    private void addMaterialFieldToGrid(GridPane grid, String label, TextField field, int row) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("md-label-medium");
        grid.add(labelNode, 0, row);
        grid.add(field, 1, row);
    }

    private Button createMaterialButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("md-button", styleClass);

        button.setOnMouseEntered(e -> {
            if (styleClass.contains("filled")) {
                button.setStyle("-fx-effect: -fx-md-elevation-2;");
            }
        });

        button.setOnMouseExited(e -> {
            if (styleClass.contains("filled")) {
                button.setStyle("-fx-effect: -fx-md-elevation-1;");
            }
        });

        return button;
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            switchToViewMode();
            parentApp.showView(CV_APP.SEARCH_VIEW);
        });

        editButton.setOnAction(e -> switchToEditMode());
        saveButton.setOnAction(e -> saveChanges());
        deleteButton.setOnAction(e -> deleteCV());
        fullViewButton.setOnAction(e -> showFullViewDialog());
        viewRawTextButton.setOnAction(e -> {
            if (currentCvId != null) {
                parentApp.showRawTextView(currentCvId, this);
            }
        });
        setupPdfButtonHandlers();
    }

    public void loadCV(String cvId) {
        this.currentCvId = cvId;
        cvIdLabel.setText("CV ID: " + cvId);

        Task<HttpClientUtil.CVDetailsResult> task = new Task<>() {
            @Override
            protected HttpClientUtil.CVDetailsResult call() {
                return HttpClientUtil.getCVDetails(serverUrl, cvId);
            }
        };

        task.setOnSucceeded(e -> {
            HttpClientUtil.CVDetailsResult result = task.getValue();
            if (result.errorMessage == null) {
                try {
                    currentCvData = new JSONObject(result.jsonResponse);
                    displayCVDetails(currentCvData);
                } catch (Exception ex) {
                    showMaterialError("Error parsing CV data: " + ex.getMessage());
                }
            } else {
                showMaterialError("Failed to load CV: " + result.errorMessage);
            }
        });

        new Thread(task).start();
    }

    private void displayCVDetails(JSONObject cvData) {
        // Display personal info with enhanced HTML
        StringBuilder html = new StringBuilder("<html><body style='font-family: Roboto, Arial, sans-serif; padding: 20px; color: #1C1B1F; line-height: 1.6;'>");

        if (cvData.has("personal_info")) {
            JSONObject info = cvData.getJSONObject("personal_info");
            html.append("<h3 style='color: #6750A4; margin-bottom: 20px; font-size: 22px;'>Personal Information</h3>");
            html.append("<table style='width: 100%; border-collapse: collapse; font-size: 15px;'>");

            String name = info.optString("name", "");
            if (!name.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600; width: 25%;'>Name:</td><td style='padding: 10px;'>").append(name).append("</td></tr>");

            String email = info.optString("email", "");
            if (!email.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>Email:</td><td style='padding: 10px;'>").append(email).append("</td></tr>");

            String phone = info.optString("phone", "");
            if (!phone.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>Phone:</td><td style='padding: 10px;'>").append(phone).append("</td></tr>");

            String address = info.optString("address", "");
            if (!address.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>Address:</td><td style='padding: 10px;'>").append(address).append("</td></tr>");

            String github = info.optString("github", "");
            if (!github.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>GitHub:</td><td style='padding: 10px;'>").append(github).append("</td></tr>");

            String linkedin = info.optString("linkedin", "");
            if (!linkedin.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>LinkedIn:</td><td style='padding: 10px;'>").append(linkedin).append("</td></tr>");

            String gender = info.optString("gender", "");
            if (!gender.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>Gender:</td><td style='padding: 10px;'>").append(gender).append("</td></tr>");

            String type = info.optString("type", "");
            if (!type.isEmpty()) html.append("<tr><td style='padding: 10px; font-weight: 600;'>Type:</td><td style='padding: 10px;'>").append(type).append("</td></tr>");

            html.append("</table>");
        }

        html.append("</body></html>");
        personalInfoView.getEngine().loadContent(html.toString());

        // Display other sections
        displaySkills(cvData);
        displayEducation(cvData);
        displayExperience(cvData);
        displayOtherInfo(cvData);
    }

    private void displaySkills(JSONObject cvData) {
        StringBuilder skills = new StringBuilder();
        if (cvData.has("skills")) {
            JSONObject skillsObj = cvData.getJSONObject("skills");
            for (String key : skillsObj.keySet()) {
                JSONArray arr = skillsObj.getJSONArray(key);
                skills.append("• ").append(key.toUpperCase()).append(": ");
                for (int i = 0; i < arr.length(); i++) {
                    skills.append(arr.getString(i));
                    if (i < arr.length() - 1) skills.append(", ");
                }
                skills.append("\n\n");
            }
        }
        skillsArea.setText(skills.toString());
    }

    private void displayEducation(JSONObject cvData) {
        StringBuilder education = new StringBuilder();
        if (cvData.has("education")) {
            JSONArray eduArray = cvData.getJSONArray("education");
            for (int i = 0; i < eduArray.length(); i++) {
                JSONObject edu = eduArray.getJSONObject(i);
                education.append((i + 1)).append(". ");
                if (edu.has("degree")) education.append(edu.getString("degree")).append(" - ");
                if (edu.has("institution")) education.append(edu.getString("institution"));
                if (edu.has("year")) education.append(" (").append(edu.getString("year")).append(")");
                education.append("\n\n");
            }
        }
        educationArea.setText(education.toString());
    }

    private void displayExperience(JSONObject cvData) {
        StringBuilder experience = new StringBuilder();
        if (cvData.has("experience")) {
            JSONArray expArray = cvData.getJSONArray("experience");
            for (int i = 0; i < expArray.length(); i++) {
                JSONObject exp = expArray.getJSONObject(i);
                experience.append((i + 1)).append(". ");
                if (exp.has("position")) experience.append(exp.getString("position")).append(" at ");
                if (exp.has("company")) experience.append(exp.getString("company"));
                if (exp.has("duration")) experience.append(" (").append(exp.getString("duration")).append(")");
                experience.append("\n");
                if (exp.has("description")) experience.append("   ").append(exp.getString("description")).append("\n");
                experience.append("\n");
            }
        }
        experienceArea.setText(experience.toString());
    }

    private void displayOtherInfo(JSONObject cvData) {
        StringBuilder other = new StringBuilder();
        if (cvData.has("filename")) other.append("Filename: ").append(cvData.getString("filename")).append("\n");
        if (cvData.has("upload_date")) other.append("Upload Date: ").append(cvData.getString("upload_date")).append("\n");
        otherInfoArea.setText(other.toString());
    }

    private void switchToEditMode() {
        isEditMode = true;
        viewModePanel.setVisible(false);
        editModePanel.setVisible(true);
        editModePanel.toFront();
        editButton.setVisible(false);
        saveButton.setVisible(true);

        // Populate edit fields
        if (currentCvData != null && currentCvData.has("personal_info")) {
            JSONObject info = currentCvData.getJSONObject("personal_info");
            nameField.setText(info.optString("name", ""));
            emailField.setText(info.optString("email", ""));
            phoneField.setText(info.optString("phone", ""));
            addressField.setText(info.optString("address", ""));
            githubField.setText(info.optString("github", ""));
            linkedinField.setText(info.optString("linkedin", ""));

            String gender = info.optString("gender", "NONE");
            genderCombo.setValue(gender);

            String type = info.optString("type", "NONE");
            if (typeCombo.getItems().contains(type)) {
                typeCombo.setValue(type);
            } else if (!type.equals("NONE") && !type.isEmpty()) {
                typeCombo.setValue("Other");
                customTypeField.setText(type);
                customTypeField.setVisible(true);
                customTypeField.setManaged(true);
            } else {
                typeCombo.setValue("NONE");
            }
        }

        if (currentCvData.has("skills")) {
            skillsEditArea.setText(currentCvData.getJSONObject("skills").toString(2));
        }
        if (currentCvData.has("education")) {
            educationEditArea.setText(currentCvData.getJSONArray("education").toString(2));
        }
        if (currentCvData.has("experience")) {
            experienceEditArea.setText(currentCvData.getJSONArray("experience").toString(2));
        }
    }

    private void switchToViewMode() {
        isEditMode = false;
        viewModePanel.setVisible(true);
        editModePanel.setVisible(false);
        viewModePanel.toFront();
        editButton.setVisible(true);
        saveButton.setVisible(false);
    }

    private void saveChanges() {
        try {
            if (currentCvData == null) {
                currentCvData = new JSONObject();
            }

            JSONObject personalInfo = new JSONObject();
            personalInfo.put("name", nameField.getText());
            personalInfo.put("email", emailField.getText());
            personalInfo.put("phone", phoneField.getText());
            personalInfo.put("address", addressField.getText());
            personalInfo.put("github", githubField.getText());
            personalInfo.put("linkedin", linkedinField.getText());
            personalInfo.put("gender", genderCombo.getValue());

            String selectedType = typeCombo.getValue();
            if ("Other".equals(selectedType)) {
                String customType = customTypeField.getText().trim();
                personalInfo.put("type", customType.isEmpty() ? "Other" : customType);
            } else {
                personalInfo.put("type", selectedType);
            }

            currentCvData.put("personal_info", personalInfo);

            try {
                currentCvData.put("skills", new JSONObject(skillsEditArea.getText()));
            } catch (Exception e) {
                showMaterialError("Invalid JSON format in Skills field");
                return;
            }

            try {
                currentCvData.put("education", new JSONArray(educationEditArea.getText()));
            } catch (Exception e) {
                showMaterialError("Invalid JSON format in Education field");
                return;
            }

            try {
                currentCvData.put("experience", new JSONArray(experienceEditArea.getText()));
            } catch (Exception e) {
                showMaterialError("Invalid JSON format in Experience field");
                return;
            }

            Task<HttpClientUtil.UpdateResult> updateTask = new Task<>() {
                @Override
                protected HttpClientUtil.UpdateResult call() {
                    return HttpClientUtil.updateCVData(serverUrl, currentCvId, currentCvData.toString(), token);
                }
            };

            updateTask.setOnSucceeded(e -> {
                HttpClientUtil.UpdateResult result = updateTask.getValue();
                if (result.success) {
                    displayCVDetails(currentCvData);
                    showMaterialSuccess("Changes Saved", "CV has been updated successfully.");
                    switchToViewMode();
                } else {
                    showMaterialError("Failed to save changes: " + result.message);
                }
            });

            updateTask.setOnFailed(e -> {
                showMaterialError("Error saving changes: " + updateTask.getException().getMessage());
            });

            new Thread(updateTask).start();

        } catch (Exception ex) {
            showMaterialError("Error saving changes: " + ex.getMessage());
        }
    }

    private void deleteCV() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete CV");
        confirm.setHeaderText("Confirm Deletion");
        confirm.setContentText("Are you sure you want to delete this CV? This action cannot be undone.");
        confirm.getDialogPane().getStyleClass().add("md-dialog");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<HttpClientUtil.DeleteResult> deleteTask = new Task<>() {
                @Override
                protected HttpClientUtil.DeleteResult call() {
                    return HttpClientUtil.deleteCV(serverUrl, currentCvId, token);
                }
            };

            deleteTask.setOnSucceeded(e -> {
                HttpClientUtil.DeleteResult deleteResult = deleteTask.getValue();
                if (deleteResult.success) {
                    showMaterialSuccess("CV Deleted", "CV has been deleted successfully.");
                    parentApp.showView(CV_APP.SEARCH_VIEW);
                } else {
                    showMaterialError("Failed to delete CV: " + deleteResult.errorMessage);
                }
            });

            deleteTask.setOnFailed(e -> {
                showMaterialError("Error deleting CV: " + deleteTask.getException().getMessage());
            });

            new Thread(deleteTask).start();
        }
    }

    private void showMaterialError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("An error occurred");
        alert.setContentText(message);
        alert.getDialogPane().getStyleClass().add("md-dialog");
        alert.showAndWait();
    }

    private void showMaterialSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStyleClass().add("md-dialog");
        alert.showAndWait();
    }

    private void showMaterialInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStyleClass().add("md-dialog");
        alert.showAndWait();
    }

    private void setupPdfButtonHandlers() {
        viewPdfButton.setOnAction(e -> {
            if (currentCvId == null) return;

            viewPdfButton.setDisable(true);
            downloadPdfButton.setDisable(true);
            editButton.setDisable(true);
            saveButton.setDisable(true);

            Task<DownloadResult> task = new Task<>() {
                @Override
                protected DownloadResult call() throws Exception {
                    return DownloadResult.downloadPdfData(serverUrl, currentCvId);
                }
            };

            task.setOnSucceeded(event -> {
                try {
                    DownloadResult result = task.getValue();
                    if (result.success && result.fileData != null) {
                        try {
                            Path tempFile = Files.createTempFile("cv_view_", ".pdf");
                            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                                fos.write(result.fileData);
                            }

                            if (Desktop.isDesktopSupported()) {
                                Desktop desktop = Desktop.getDesktop();
                                if (desktop.isSupported(Desktop.Action.OPEN)) {
                                    desktop.open(tempFile.toFile());
                                } else {
                                    showMaterialInfo("View PDF", "Opening files not supported. Saved to: " + tempFile.toAbsolutePath());
                                }
                            } else {
                                showMaterialInfo("View PDF", "Desktop actions not supported. Saved to: " + tempFile.toAbsolutePath());
                            }
                        } catch (IOException ioEx) {
                            showMaterialError("Failed to save or open PDF locally: " + ioEx.getMessage());
                        }
                    } else {
                        showMaterialError("Failed to download PDF for viewing: " + (result.message != null ? result.message : "Unknown error"));
                    }
                } catch (Exception ex) {
                    showMaterialError("Unexpected error during PDF view: " + ex.getMessage());
                } finally {
                    viewPdfButton.setDisable(false);
                    downloadPdfButton.setDisable(false);
                    editButton.setDisable(false);
                    saveButton.setDisable(!isEditMode);
                }
            });

            task.setOnFailed(event -> {
                showMaterialError("PDF viewing failed: " + task.getException().getMessage());
                viewPdfButton.setDisable(false);
                downloadPdfButton.setDisable(false);
                editButton.setDisable(false);
                saveButton.setDisable(!isEditMode);
            });

            new Thread(task).start();
        });

        downloadPdfButton.setOnAction(e -> {
            if (currentCvId == null) return;

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF As");
            fileChooser.setInitialFileName((currentCvId != null ? currentCvId : "cv") + ".pdf");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );

            File fileToSave = fileChooser.showSaveDialog(getScene().getWindow());

            if (fileToSave != null) {
                String filePath = fileToSave.getAbsolutePath();
                if (!filePath.toLowerCase().endsWith(".pdf")) {
                    filePath += ".pdf";
                }

                viewPdfButton.setDisable(true);
                downloadPdfButton.setDisable(true);
                editButton.setDisable(true);
                saveButton.setDisable(true);

                String finalFilePath = filePath;
                Task<DownloadResult> task = new Task<>() {
                    @Override
                    protected DownloadResult call() throws Exception {
                        return DownloadResult.downloadPdfToFile(serverUrl, currentCvId, finalFilePath);
                    }
                };

                task.setOnSucceeded(event -> {
                    try {
                        DownloadResult result = task.getValue();
                        if (result.success) {
                            showMaterialSuccess("Download Successful", result.message);
                        } else {
                            showMaterialError("Failed to download PDF: " + (result.message != null ? result.message : "Unknown error"));
                        }
                    } catch (Exception ex) {
                        showMaterialError("Unexpected error during PDF download: " + ex.getMessage());
                    } finally {
                        viewPdfButton.setDisable(false);
                        downloadPdfButton.setDisable(false);
                        editButton.setDisable(false);
                        saveButton.setDisable(!isEditMode);
                    }
                });

                task.setOnFailed(event -> {
                    showMaterialError("PDF download failed: " + task.getException().getMessage());
                    viewPdfButton.setDisable(false);
                    downloadPdfButton.setDisable(false);
                    editButton.setDisable(false);
                    saveButton.setDisable(!isEditMode);
                });

                new Thread(task).start();
            }
        });
    }
}