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
import javafx.application.Platform;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MaterialUploadPanel extends StackPane {

    private CV_APP parentApp;
    private String serverUrl;
    private String token;

    private ListView<String> fileListView;
    private Button selectButton, uploadButton, backButton;
    private TextArea statusArea;
    private List<File> selectedFiles;
    private ProgressIndicator progressIndicator;
    private Label uploadStatusLabel;
    private ScrollPane scrollPane;
    private VBox contentBox;

    // Batch status checker components
    private TextField batchIdField;
    private Button checkStatusButton;
    private ProgressIndicator statusProgressIndicator;
    private VBox batchResultsContainer;
    private Pagination batchPagination;
    private Label batchSummaryLabel;
    private List<JSONObject> currentBatchResults;
    private static final int ITEMS_PER_PAGE = 5;

    public MaterialUploadPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        this.selectedFiles = new ArrayList<>();
        this.currentBatchResults = new ArrayList<>();
        this.token = app.getJwtToken();

        initializeMaterialUI();
        setupEventHandlers();
    }

    public void setToken(String token) {
        this.token = token;
    }

    private void initializeMaterialUI() {
        // Create main content VBox
        contentBox = new VBox();
        contentBox.getStyleClass().addAll("md-spacing-24", "md-padding-24");

        // Create header section
        VBox headerSection = createMaterialHeader();

        // Create upload area
        VBox uploadArea = createMaterialUploadArea();

        // Create file list section
        VBox fileListSection = createMaterialFileListSection();

        // Create status section
        VBox statusSection = createMaterialStatusSection();

        // Create batch status checker section
        VBox batchStatusSection = createBatchStatusSection();

        contentBox.getChildren().addAll(headerSection, uploadArea, fileListSection, statusSection, batchStatusSection);
        VBox.setVgrow(fileListSection, Priority.ALWAYS);

        // Wrap content in ScrollPane
        scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("md-scroll-pane");

        // Add ScrollPane to StackPane
        getChildren().add(scrollPane);
    }

    private VBox createBatchStatusSection() {
        VBox batchStatusSection = new VBox();
        batchStatusSection.getStyleClass().addAll("md-card-elevated", "md-spacing-20");

        // Section title
        Label titleLabel = new Label("Check Batch Upload Status");
        titleLabel.getStyleClass().add("md-headline-small");

        Label subtitleLabel = new Label("Enter a batch ID to view the upload status and results");
        subtitleLabel.getStyleClass().add("md-body-medium");

        // Input area
        HBox inputArea = new HBox();
        inputArea.getStyleClass().add("md-spacing-12");
        inputArea.setAlignment(Pos.CENTER_LEFT);

        Label batchIdLabel = new Label("Batch ID:");
        batchIdLabel.getStyleClass().add("md-body-medium");
        batchIdLabel.setMinWidth(80);

        batchIdField = new TextField();
        batchIdField.setPromptText("e.g., batch_1759412420_Manura");
        batchIdField.getStyleClass().add("md-text-field");
        HBox.setHgrow(batchIdField, Priority.ALWAYS);

        checkStatusButton = new Button("Check Status");
        checkStatusButton.getStyleClass().addAll("md-button", "md-button-filled");

        statusProgressIndicator = new ProgressIndicator();
        statusProgressIndicator.setPrefSize(24, 24);
        statusProgressIndicator.setVisible(false);

        inputArea.getChildren().addAll(batchIdLabel, batchIdField, checkStatusButton, statusProgressIndicator);

        // Summary label
        batchSummaryLabel = new Label();
        batchSummaryLabel.getStyleClass().addAll("md-body-medium", "md-status-info");
        batchSummaryLabel.setVisible(false);
        batchSummaryLabel.setWrapText(true);

        // Results container
        batchResultsContainer = new VBox();
        batchResultsContainer.getStyleClass().add("md-spacing-12");
        batchResultsContainer.setVisible(false);

        // Pagination
        batchPagination = new Pagination();
        batchPagination.getStyleClass().add("md-pagination");
        batchPagination.setVisible(false);
        batchPagination.setPageFactory(this::createBatchResultPage);

        batchStatusSection.getChildren().addAll(
                titleLabel, subtitleLabel, inputArea, batchSummaryLabel,
                batchResultsContainer, batchPagination
        );

        return batchStatusSection;
    }

    private VBox createBatchResultPage(int pageIndex) {
        VBox pageContent = new VBox();
        pageContent.getStyleClass().add("md-spacing-12");

        int fromIndex = pageIndex * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, currentBatchResults.size());

        for (int i = fromIndex; i < toIndex; i++) {
            JSONObject result = currentBatchResults.get(i);
            VBox resultCard = createResultCard(result, i + 1);
            pageContent.getChildren().add(resultCard);
        }

        return pageContent;
    }

    private VBox createResultCard(JSONObject result, int index) {
        VBox card = new VBox();
        card.getStyleClass().addAll("md-card-outlined", "md-spacing-12");

        // Header with index and status
        HBox header = new HBox();
        header.getStyleClass().add("md-spacing-12");
        header.setAlignment(Pos.CENTER_LEFT);

        Label indexLabel = new Label("#" + index);
        indexLabel.getStyleClass().add("md-title-medium");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String status = result.getString("status");
        Label statusBadge = new Label(status.toUpperCase());
        statusBadge.getStyleClass().add("md-badge");
        if (status.equals("success")) {
            statusBadge.getStyleClass().add("md-badge-success");
        } else {
            statusBadge.getStyleClass().add("md-badge-error");
        }

        header.getChildren().addAll(indexLabel, spacer, statusBadge);

        // Details grid
        GridPane detailsGrid = new GridPane();
        detailsGrid.getStyleClass().add("md-spacing-8");
        detailsGrid.setHgap(16);
        detailsGrid.setVgap(8);

        int row = 0;

        // Filename
        addDetailRow(detailsGrid, row++, "Filename:", result.getString("filename"));

        // Name
        if (result.has("name") && !result.isNull("name")) {
            addDetailRow(detailsGrid, row++, "Extracted Name:", result.getString("name"));
        }

        // ID
        if (result.has("id") && !result.isNull("id")) {
            addDetailRow(detailsGrid, row++, "Document ID:", result.getString("id"));
        }

        // Action
        if (result.has("action") && !result.isNull("action")) {
            String action = result.getString("action");
            addDetailRow(detailsGrid, row++, "Action:", action.toUpperCase());
        }

        // Processing time
        if (result.has("processing_time") && !result.isNull("processing_time")) {
            double processingTime = result.getDouble("processing_time");
            addDetailRow(detailsGrid, row++, "Processing Time:",
                    String.format("%.2f seconds", processingTime));
        }

        // Existing ID (if duplicate)
        if (result.has("existing_id") && !result.isNull("existing_id")) {
            addDetailRow(detailsGrid, row++, "Existing ID:", result.getString("existing_id"));
        }

        card.getChildren().addAll(header, detailsGrid);
        return card;
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().addAll("md-body-small", "md-text-secondary");
        labelNode.setMinWidth(120);

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("md-body-medium");
        valueNode.setWrapText(true);

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private void checkBatchStatus() {
        String batchId = batchIdField.getText().trim();

        if (batchId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required",
                    "Please enter a batch ID to check status.");
            return;
        }

        checkStatusButton.setDisable(true);
        statusProgressIndicator.setVisible(true);
        batchResultsContainer.setVisible(false);
        batchPagination.setVisible(false);
        batchSummaryLabel.setVisible(false);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String url = serverUrl + "/upload/status/" + batchId;

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new Exception("Failed to fetch batch status: " + response.statusCode());
                }
            }
        };

        task.setOnSucceeded(e -> {
            try {
                String responseBody = task.getValue();
                JSONObject batchData = new JSONObject(responseBody);
                displayBatchResults(batchData);
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Parse Error",
                        "Failed to parse batch status response: " + ex.getMessage());
            }

            checkStatusButton.setDisable(false);
            statusProgressIndicator.setVisible(false);
        });

        task.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Request Failed",
                    "Failed to fetch batch status: " + task.getException().getMessage());
            checkStatusButton.setDisable(false);
            statusProgressIndicator.setVisible(false);
        });

        new Thread(task).start();
    }

    private void displayBatchResults(JSONObject batchData) {
        // Clear previous results
        currentBatchResults.clear();

        // Extract summary information
        String batchId = batchData.getString("batch_id");
        int totalFiles = batchData.getInt("total_files");
        int successCount = batchData.getInt("success_count");
        int errorCount = batchData.getInt("error_count");
        int skippedCount = batchData.getInt("skipped_count");
        String completedAt = batchData.optString("completed_at", "N/A");
        String processingMode = batchData.optString("processing_mode", "N/A");

        // Format summary
        String summaryText = String.format(
                "Batch ID: %s | Total: %d | Success: %d | Errors: %d | Skipped: %d | Completed: %s | Mode: %s",
                batchId, totalFiles, successCount, errorCount, skippedCount,
                formatDateTime(completedAt), processingMode
        );

        batchSummaryLabel.setText(summaryText);
        batchSummaryLabel.setVisible(true);

        // Apply appropriate style based on results
        batchSummaryLabel.getStyleClass().removeAll("md-status-error", "md-status-success", "md-status-info");
        if (errorCount > 0) {
            batchSummaryLabel.getStyleClass().add("md-status-error");
        } else if (successCount == totalFiles) {
            batchSummaryLabel.getStyleClass().add("md-status-success");
        } else {
            batchSummaryLabel.getStyleClass().add("md-status-info");
        }

        // Extract processed results
        JSONArray processed = batchData.getJSONArray("processed");
        for (int i = 0; i < processed.length(); i++) {
            currentBatchResults.add(processed.getJSONObject(i));
        }

        // Setup pagination
        if (!currentBatchResults.isEmpty()) {
            int pageCount = (int) Math.ceil((double) currentBatchResults.size() / ITEMS_PER_PAGE);
            batchPagination.setPageCount(pageCount);
            batchPagination.setCurrentPageIndex(0);
            batchPagination.setVisible(true);
            batchResultsContainer.setVisible(true);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "No Results",
                    "This batch has no processed files.");
        }
    }

    private String formatDateTime(String dateTime) {
        if (dateTime == null || dateTime.equals("N/A") || dateTime.isEmpty()) {
            return "N/A";
        }
        try {
            // Simple formatting - just take the first part before microseconds
            if (dateTime.contains(".")) {
                return dateTime.substring(0, dateTime.indexOf(".")).replace("T", " ");
            }
            return dateTime.replace("T", " ");
        } catch (Exception e) {
            return dateTime;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.getDialogPane().getStyleClass().add("md-dialog");
            alert.showAndWait();
        });
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
        checkStatusButton.setOnAction(e -> checkBatchStatus());
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