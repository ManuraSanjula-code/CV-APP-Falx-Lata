package com.vertex.cv_app.java_fx.panels;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.collections.FXCollections;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MaterialUploadPanel extends VBox {

    private CV_APP parentApp;
    private String serverUrl;
    private String token;

    private ListView<String> fileListView;
    private Button selectButton, uploadButton, backButton;
    private TextArea statusArea;
    private List<File> selectedFiles;
    private ProgressIndicator progressIndicator;
    private Label uploadStatusLabel;

    public MaterialUploadPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        this.selectedFiles = new ArrayList<>();
        this.token = app.getJwtToken();

        initializeMaterialUI();
        setupEventHandlers();
    }

    public void setToken(String token) {
        this.token = token;
    }

    private void initializeMaterialUI() {
        getStyleClass().addAll("md-spacing-24", "md-padding-24");

        // Create header section
        VBox headerSection = createMaterialHeader();

        // Create upload area
        VBox uploadArea = createMaterialUploadArea();

        // Create file list section
        VBox fileListSection = createMaterialFileListSection();

        // Create status section
        VBox statusSection = createMaterialStatusSection();

        getChildren().addAll(headerSection, uploadArea, fileListSection, statusSection);
        VBox.setVgrow(fileListSection, Priority.ALWAYS);
    }

    private VBox createMaterialHeader() {
        VBox headerSection = new VBox();
        headerSection.getStyleClass().addAll("md-card-elevated", "md-spacing-16");

        Label titleLabel = new Label("Upload CV Files");
        titleLabel.getStyleClass().add("md-headline-small");

        Label subtitleLabel = new Label("Select and upload PDF or DOCX files to the CV management system");
        subtitleLabel.getStyleClass().add("md-body-medium");

        // Upload instructions card
        VBox instructionsCard = new VBox();
        instructionsCard.getStyleClass().addAll("md-card-filled", "md-spacing-12");

        Label instructionsTitle = new Label("Upload Instructions");
        instructionsTitle.getStyleClass().add("md-title-small");

        Label instruction1 = new Label("• Supported formats: PDF and DOCX files");
        Label instruction2 = new Label("• Maximum file size: 10MB per file");
        Label instruction3 = new Label("• Multiple files can be selected at once");
        Label instruction4 = new Label("• Files are automatically processed for text extraction");

        instruction1.getStyleClass().add("md-body-small");
        instruction2.getStyleClass().add("md-body-small");
        instruction3.getStyleClass().add("md-body-small");
        instruction4.getStyleClass().add("md-body-small");

        instructionsCard.getChildren().addAll(
                instructionsTitle, instruction1, instruction2, instruction3, instruction4
        );

        headerSection.getChildren().addAll(titleLabel, subtitleLabel, instructionsCard);
        return headerSection;
    }

    private VBox createMaterialUploadArea() {
        VBox uploadArea = new VBox();
        uploadArea.getStyleClass().addAll("md-card-outlined", "md-spacing-20");
        uploadArea.setAlignment(Pos.CENTER);
        uploadArea.setMinHeight(200);

        // Upload icon and text
        Label uploadIcon = new Label("📁");
        uploadIcon.setStyle("-fx-font-size: 48px;");

        Label uploadText = new Label("Select Files to Upload");
        uploadText.getStyleClass().add("md-title-medium");

        Label dropText = new Label("Click the button below to choose CV files from your computer");
        dropText.getStyleClass().add("md-body-medium");

        // Action buttons
        HBox buttonArea = createMaterialUploadButtons();

        uploadArea.getChildren().addAll(uploadIcon, uploadText, dropText, buttonArea);
        return uploadArea;
    }

    private HBox createMaterialUploadButtons() {
        HBox buttonArea = new HBox();
        buttonArea.getStyleClass().add("md-spacing-16");
        buttonArea.setAlignment(Pos.CENTER);

        selectButton = new Button("Select Files");
        selectButton.getStyleClass().addAll("md-button", "md-button-filled");

        uploadButton = new Button("Upload Selected");
        uploadButton.getStyleClass().addAll("md-button", "md-button-tonal");
        uploadButton.setDisable(true);

        backButton = new Button("Back to Search");
        backButton.getStyleClass().addAll("md-button", "md-button-outlined");

        progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("md-progress-circular");
        progressIndicator.setPrefSize(32, 32);
        progressIndicator.setVisible(false);

        buttonArea.getChildren().addAll(selectButton, uploadButton, backButton, progressIndicator);
        return buttonArea;
    }

    private VBox createMaterialFileListSection() {
        VBox fileListSection = new VBox();
        fileListSection.getStyleClass().addAll("md-card", "md-spacing-16");

        // Section header
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("md-spacing-16");

        Label titleLabel = new Label("Selected Files");
        titleLabel.getStyleClass().add("md-title-medium");

        uploadStatusLabel = new Label("No files selected");
        uploadStatusLabel.getStyleClass().add("md-body-small");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerBox.getChildren().addAll(titleLabel, spacer, uploadStatusLabel);

        // File list
        fileListView = new ListView<>();
        fileListView.getStyleClass().add("md-list-view");
        fileListView.setPlaceholder(createEmptyStateView());

        VBox.setVgrow(fileListView, Priority.ALWAYS);

        fileListSection.getChildren().addAll(headerBox, fileListView);
        return fileListSection;
    }

    private VBox createEmptyStateView() {
        VBox emptyState = new VBox();
        emptyState.getStyleClass().add("md-spacing-16");
        emptyState.setAlignment(Pos.CENTER);

        Label emptyIcon = new Label("📄");
        emptyIcon.setStyle("-fx-font-size: 32px; -fx-opacity: 0.6;");

        Label emptyText = new Label("No files selected");
        emptyText.getStyleClass().addAll("md-body-medium");

        Label emptySubtext = new Label("Choose PDF or DOCX files to upload");
        emptySubtext.getStyleClass().add("md-body-small");

        emptyState.getChildren().addAll(emptyIcon, emptyText, emptySubtext);
        return emptyState;
    }

    private VBox createMaterialStatusSection() {
        VBox statusSection = new VBox();
        statusSection.getStyleClass().addAll("md-card-filled", "md-spacing-16");

        Label statusTitle = new Label("Upload Status");
        statusTitle.getStyleClass().add("md-title-medium");

        statusArea = new TextArea();
        statusArea.getStyleClass().addAll("md-text-field", "md-body-small");
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(4);
        statusArea.setWrapText(true);
        statusArea.setText("Ready to upload files. Select PDF or DOCX files to begin the upload process.");

        statusSection.getChildren().addAll(statusTitle, statusArea);
        statusSection.setPrefHeight(150);
        return statusSection;
    }

    private void setupEventHandlers() {
        selectButton.setOnAction(e -> selectMaterialFiles());
        uploadButton.setOnAction(e -> uploadMaterialFiles());
        backButton.setOnAction(e -> parentApp.showView(CV_APP.SEARCH_VIEW));
    }

    private void selectMaterialFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CV Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CV Files", "*.pdf", "*.docx"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Word Documents", "*.docx")
        );

        List<File> files = fileChooser.showOpenMultipleDialog(getScene().getWindow());

        if (files != null && !files.isEmpty()) {
            selectedFiles.clear();
            selectedFiles.addAll(files);

            List<String> fileDisplayNames = new ArrayList<>();
            long totalSize = 0;

            for (File file : selectedFiles) {
                String fileName = file.getName();
                String fileSize = formatFileSize(file.length());
                String fileType = getFileType(fileName);

                fileDisplayNames.add(String.format("%s (%s) - %s", fileName, fileSize, fileType));
                totalSize += file.length();
            }

            fileListView.setItems(FXCollections.observableArrayList(fileDisplayNames));
            uploadButton.setDisable(false);

            uploadStatusLabel.setText(String.format("%d files selected (%s total)",
                    selectedFiles.size(), formatFileSize(totalSize)));
            uploadStatusLabel.getStyleClass().removeAll("md-status-error", "md-status-success");
            uploadStatusLabel.getStyleClass().add("md-status-info");

            statusArea.setText(String.format("%d file(s) selected and ready for upload. " +
                            "Total size: %s. Click 'Upload Selected' to proceed.",
                    selectedFiles.size(), formatFileSize(totalSize)));
        }
    }

    private String getFileType(String fileName) {
        if (fileName.toLowerCase().endsWith(".pdf")) {
            return "PDF Document";
        } else if (fileName.toLowerCase().endsWith(".docx")) {
            return "Word Document";
        }
        return "Unknown";
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private void uploadMaterialFiles() {
        if (selectedFiles.isEmpty()) {
            statusArea.setText("No files selected for upload.");
            uploadStatusLabel.setText("No files selected");
            uploadStatusLabel.getStyleClass().removeAll("md-status-info", "md-status-success");
            uploadStatusLabel.getStyleClass().add("md-status-error");
            return;
        }

        // Show Material Design loading state
        statusArea.setText("Preparing files for upload...\n");
        uploadStatusLabel.setText("Uploading files...");
        uploadStatusLabel.getStyleClass().removeAll("md-status-error", "md-status-success");
        uploadStatusLabel.getStyleClass().add("md-status-info");

        uploadButton.setDisable(true);
        selectButton.setDisable(true);
        progressIndicator.setVisible(true);

        Task<HttpClientUtil.UploadResult> task = new Task<>() {
            @Override
            protected HttpClientUtil.UploadResult call() {
                return HttpClientUtil.uploadFilesWithToken(serverUrl, selectedFiles, token);
            }
        };

        task.setOnSucceeded(e -> {
            HttpClientUtil.UploadResult result = task.getValue();
            statusArea.appendText(result.message + "\n");

            if (result.successCount > 0) {
                statusArea.appendText("\n✅ Upload completed successfully!\n");
                statusArea.appendText("Files have been processed and added to the CV database.\n");
                statusArea.appendText("You can now search for the uploaded CVs in the Search section.\n");

                uploadStatusLabel.setText(String.format("Successfully uploaded %d files", result.successCount));
                uploadStatusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
                uploadStatusLabel.getStyleClass().add("md-status-success");

                selectedFiles.clear();
                fileListView.getItems().clear();
                uploadStatusLabel.setText("Upload completed successfully");

                // Show Material Design success alert
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Upload Successful");
                alert.setHeaderText("Files Uploaded Successfully");
                alert.setContentText(String.format(
                        "Successfully uploaded and processed %d CV file(s).\n\n" +
                                "The files have been added to your CV database and are now searchable.",
                        result.successCount));

                alert.getDialogPane().getStyleClass().add("md-dialog");
                alert.getDialogPane().lookupButton(ButtonType.OK)
                        .getStyleClass().addAll("md-button", "md-button-filled");

                alert.showAndWait();
            } else {
                uploadStatusLabel.setText("Upload failed");
                uploadStatusLabel.getStyleClass().removeAll("md-status-info", "md-status-success");
                uploadStatusLabel.getStyleClass().add("md-status-error");
            }

            uploadButton.setDisable(true);
            selectButton.setDisable(false);
            progressIndicator.setVisible(false);
        });

        task.setOnFailed(e -> {
            statusArea.appendText("Upload failed: " + e.getSource().getException().getMessage() + "\n");

            uploadStatusLabel.setText("Upload failed");
            uploadStatusLabel.getStyleClass().removeAll("md-status-info", "md-status-success");
            uploadStatusLabel.getStyleClass().add("md-status-error");

            uploadButton.setDisable(false);
            selectButton.setDisable(false);
            progressIndicator.setVisible(false);
        });

        new Thread(task).start();
    }
}