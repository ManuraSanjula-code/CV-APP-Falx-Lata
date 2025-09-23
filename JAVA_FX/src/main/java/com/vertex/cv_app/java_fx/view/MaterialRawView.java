package com.vertex.cv_app.java_fx.view;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import org.json.JSONObject;

import java.awt.*;

public class MaterialRawView extends StackPane {

    private CV_APP parentApp;
    private String serverUrl;
    private String currentCvId;

    private Label cvIdLabel;
    private Button backButton, refreshButton, copyButton, exportButton;
    private TextArea rawTextArea;
    private Label statusLabel, textStatsLabel;
    private ProgressIndicator loadingIndicator;
    private boolean isTextExpanded = false;

    private VBox headerContainer;
    private HBox controlsContainer;
    private VBox textContainer;
    private ScrollPane mainScrollPane;

    public MaterialRawView(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initializeMaterialUI();
        setupEventHandlers();
    }

    private void initializeMaterialUI() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;
        // Configure the main StackPane
        getStyleClass().add("material-app");
        setPrefSize(width, height); // Reduced height
        setMinSize(600, 500); // Even smaller minimum
        setMaxHeight(height);

        // Create main content container
        VBox mainContent = new VBox();
        mainContent.setSpacing(16);
        mainContent.setPadding(new Insets(16));

        // Create header section
        headerContainer = createMaterialHeader();

        // Create text content section
        textContainer = createTextSection();

        // Create controls section
        controlsContainer = createMaterialControls();

        // Add all sections to main content
        mainContent.getChildren().addAll(headerContainer, textContainer, controlsContainer);

        // Create ScrollPane for the entire content
        mainScrollPane = new ScrollPane(mainContent);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mainScrollPane.getStyleClass().add("main-scroll-pane");

        // Create loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.getStyleClass().add("md-progress-circular");
        loadingIndicator.setPrefSize(50, 50);
        loadingIndicator.setVisible(false);

        // Add both scroll pane and loading indicator to StackPane
        getChildren().addAll(mainScrollPane, loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);
    }

    private VBox createMaterialHeader() {
        VBox headerSection = new VBox();
        headerSection.getStyleClass().addAll("md-card-elevated", "md-spacing-16");
        headerSection.setSpacing(12);

        // Top row with back button and title
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getStyleClass().add("md-spacing-16");

        backButton = new Button("← Back to CV Details");
        backButton.getStyleClass().addAll("md-button", "md-button-text");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Label titleLabel = new Label("Raw Text Content");
        titleLabel.getStyleClass().add("md-headline-small");

        topRow.getChildren().addAll(backButton, titleSpacer, titleLabel);

        Label subtitleLabel = new Label("View the complete extracted text content from the CV document");
        subtitleLabel.getStyleClass().add("md-body-medium");

        // CV info and stats row
        HBox infoRow = new HBox();
        infoRow.getStyleClass().add("md-spacing-24");
        infoRow.setAlignment(Pos.CENTER_LEFT);

        cvIdLabel = new Label("CV ID: ");
        cvIdLabel.getStyleClass().add("md-body-large");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        textStatsLabel = new Label("Text statistics will appear here");
        textStatsLabel.getStyleClass().add("md-body-small");

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("md-body-small");

        infoRow.getChildren().addAll(cvIdLabel, spacer, textStatsLabel, statusLabel);

        headerSection.getChildren().addAll(topRow, subtitleLabel, infoRow);
        return headerSection;
    }

    private VBox createTextSection() {
        // Create text section container
        VBox textSection = new VBox();
        textSection.getStyleClass().addAll("md-card", "md-spacing-16");
        textSection.setSpacing(16);

        // Text area header with controls
        HBox textHeader = new HBox();
        textHeader.setAlignment(Pos.CENTER_LEFT);
        textHeader.getStyleClass().add("md-spacing-16");

        Label textTitle = new Label("Complete Text Content");
        textTitle.getStyleClass().add("md-title-medium");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        // Expand/Collapse button
        Button expandButton = new Button("Expand Text Area");
        expandButton.getStyleClass().addAll("md-button", "md-button-text");
        expandButton.setOnAction(e -> toggleTextAreaSize(expandButton));

        // Action buttons for text area
        copyButton = new Button("Copy Text");
        copyButton.getStyleClass().addAll("md-button", "md-button-text");

        exportButton = new Button("Export");
        exportButton.getStyleClass().addAll("md-button", "md-button-text");

        textHeader.getChildren().addAll(textTitle, headerSpacer, expandButton, copyButton, exportButton);

        // Create the main text area
        rawTextArea = new TextArea();
        rawTextArea.getStyleClass().addAll("md-text-field-outlined", "expandable-text-area");
        rawTextArea.setEditable(false);
        rawTextArea.setWrapText(true);
        rawTextArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
        rawTextArea.setPromptText("Raw text content will appear here after loading...");

        // Set appropriate size - let it grow naturally
        rawTextArea.setPrefRowCount(25);
        rawTextArea.setPrefColumnCount(100);
        rawTextArea.setMinHeight(400);

        textSection.getChildren().addAll(textHeader, rawTextArea);

        return textSection;
    }

    private HBox createMaterialControls() {
        HBox controlsSection = new HBox();
        controlsSection.getStyleClass().addAll("md-card-filled", "md-spacing-16");
        controlsSection.setAlignment(Pos.CENTER_LEFT);
        controlsSection.setMinHeight(80);
        controlsSection.setMaxHeight(80);
        controlsSection.setSpacing(16);

        refreshButton = new Button("Refresh Content");
        refreshButton.getStyleClass().addAll("md-button", "md-button-tonal");

        Button clearButton = new Button("Clear Text");
        clearButton.getStyleClass().addAll("md-button", "md-button-text");
        clearButton.setOnAction(e -> rawTextArea.clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button searchInTextButton = new Button("Search in Text");
        searchInTextButton.getStyleClass().addAll("md-button", "md-button-text");
        searchInTextButton.setOnAction(e -> showSearchDialog());

        Button wrapTextButton = new Button("Toggle Wrap Text");
        wrapTextButton.getStyleClass().addAll("md-button", "md-button-text");
        wrapTextButton.setOnAction(e -> {
            rawTextArea.setWrapText(!rawTextArea.isWrapText());
            wrapTextButton.setText(rawTextArea.isWrapText() ? "Disable Wrap" : "Enable Wrap");
        });

        // Scroll to top button for the main scroll pane
        Button scrollTopButton = new Button("Scroll to Top");
        scrollTopButton.getStyleClass().addAll("md-button", "md-button-text");
        scrollTopButton.setOnAction(e -> scrollToTop());

        controlsSection.getChildren().addAll(
                refreshButton, clearButton, spacer,
                wrapTextButton, searchInTextButton, scrollTopButton
        );
        return controlsSection;
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> parentApp.showView(CV_APP.VIEW_CV_VIEW));
        refreshButton.setOnAction(e -> {
            if (currentCvId != null) {
                loadRawText(currentCvId);
            }
        });
        copyButton.setOnAction(e -> copyTextToClipboard());
        exportButton.setOnAction(e -> exportText());
    }

    private void toggleTextAreaSize(Button expandButton) {
        if (isTextExpanded) {
            // Collapse to normal size
            rawTextArea.setPrefHeight(Region.USE_COMPUTED_SIZE);
            rawTextArea.setMinHeight(400);
            expandButton.setText("Expand Text Area");
        } else {
            // Expand to larger size
            rawTextArea.setPrefHeight(1000);
            rawTextArea.setMinHeight(1000);
            expandButton.setText("Collapse Text Area");
        }
        isTextExpanded = !isTextExpanded;
    }

    private void scrollToTop() {
        // Scroll the main ScrollPane to top
        mainScrollPane.setVvalue(0);
        // Also scroll the text area to the beginning
        rawTextArea.positionCaret(0);
    }

    private void showSearchDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search in Text");
        dialog.setHeaderText("Search for text in the CV content");
        dialog.setContentText("Enter search term:");
        dialog.getDialogPane().getStyleClass().add("md-dialog");

        dialog.showAndWait().ifPresent(searchTerm -> {
            if (!searchTerm.trim().isEmpty()) {
                performTextSearch(searchTerm);
            }
        });
    }

    private void performTextSearch(String searchTerm) {
        String text = rawTextArea.getText();
        if (text == null || text.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Search", "No Content",
                    "There is no text content to search.");
            return;
        }

        int index = text.toLowerCase().indexOf(searchTerm.toLowerCase());
        if (index >= 0) {
            rawTextArea.requestFocus();
            rawTextArea.selectRange(index, index + searchTerm.length());
            rawTextArea.positionCaret(index);
            showAlert(Alert.AlertType.INFORMATION, "Search", "Found",
                    "Found \"" + searchTerm + "\" at position " + index);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Search", "Not Found",
                    "\"" + searchTerm + "\" was not found in the text.");
        }
    }

    public void loadRawText(String cvId) {
        this.currentCvId = cvId;
        cvIdLabel.setText("CV ID: " + cvId);
        statusLabel.setText("Loading...");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error");
        statusLabel.getStyleClass().add("md-status-info");

        rawTextArea.clear();
        textStatsLabel.setText("Loading text statistics...");
        loadingIndicator.setVisible(true);

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
                    JSONObject cvData = new JSONObject(result.jsonResponse);
                    String rawText = cvData.optString("raw_text", "No raw text available");

                    rawTextArea.setText(rawText);
                    updateTextStatistics(rawText);

                    statusLabel.setText("Content loaded successfully");
                    statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
                    statusLabel.getStyleClass().add("md-status-success");

                    // Scroll to top after loading content
                    scrollToTop();
                } catch (Exception ex) {
                    statusLabel.setText("Error parsing data: " + ex.getMessage());
                    statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                    statusLabel.getStyleClass().add("md-status-error");
                    rawTextArea.setText("Error: " + ex.getMessage());
                }
            } else {
                statusLabel.setText("Error: " + result.errorMessage);
                statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                statusLabel.getStyleClass().add("md-status-error");
                rawTextArea.setText("Failed to load raw text: " + result.errorMessage);
            }
            loadingIndicator.setVisible(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Failed to load content");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");
            loadingIndicator.setVisible(false);
        });

        new Thread(task).start();
    }

    private void updateTextStatistics(String text) {
        if (text == null || text.trim().isEmpty()) {
            textStatsLabel.setText("No text content");
            return;
        }

        int charCount = text.length();
        int wordCount = text.trim().split("\\s+").length;
        int lineCount = text.split("\n").length;
        int paragraphCount = text.split("\n\n").length;

        textStatsLabel.setText(String.format(
                "%,d characters • %,d words • %d lines • %d paragraphs",
                charCount, wordCount, lineCount, paragraphCount
        ));
    }

    private void copyTextToClipboard() {
        String text = rawTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Copy Failed", "No Content to Copy",
                    "There is no text content to copy to clipboard.");
            return;
        }

        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);

            showAlert(Alert.AlertType.INFORMATION, "Copy Successful", "Text Copied",
                    "The complete text content has been copied to your clipboard.");

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Copy Failed", "Clipboard Error",
                    "Failed to copy text to clipboard: " + ex.getMessage());
        }
    }

    private void exportText() {
        String text = rawTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Export Failed", "No Content to Export",
                    "There is no text content to export.");
            return;
        }

        try {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Export Raw Text");
            fileChooser.setInitialFileName("cv_raw_text_" + currentCvId + ".txt");
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt")
            );

            java.io.File file = fileChooser.showSaveDialog(getScene().getWindow());
            if (file != null) {
                java.nio.file.Files.write(file.toPath(), text.getBytes("UTF-8"));

                showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Text Exported",
                        "The raw text has been successfully exported to:\n" + file.getAbsolutePath());
            }

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", "File Export Error",
                    "Failed to export text file: " + ex.getMessage());
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("md-dialog");
        if (alertType == Alert.AlertType.INFORMATION) {
            alert.getDialogPane().lookupButton(ButtonType.OK)
                    .getStyleClass().addAll("md-button", "md-button-filled");
        }
        alert.showAndWait();
    }

    public void clearView() {
        currentCvId = null;
        cvIdLabel.setText("CV ID: ");
        rawTextArea.clear();
        textStatsLabel.setText("Text statistics will appear here");
        statusLabel.setText("Ready");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error", "md-status-info");
        loadingIndicator.setVisible(false);
        isTextExpanded = false;

        // Scroll to top when clearing
        scrollToTop();
    }
}