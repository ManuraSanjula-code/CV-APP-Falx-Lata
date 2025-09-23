package com.vertex.cv_app.java_fx.panels;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import com.vertex.cv_app.utils.HttpClientUtil.AuditLogResult;
import com.vertex.cv_app.utils.HttpClientUtil.FilterOptionsResult;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.json.JSONObject;
import java.time.LocalDate;

public class MaterialAuditLogPanel extends ScrollPane {

    private CV_APP parentApp;
    private String serverUrl;
    private String jwtToken;

    private TableView<AuditLogItem> logTable;
    private ObservableList<AuditLogItem> tableData;

    private Button backButton, refreshButton, prevButton, nextButton;
    private Button firstPageButton, lastPageButton, goToPageButton;
    private DatePicker startDatePicker, endDatePicker;
    private ComboBox<String> userFilterCombo, actionFilterCombo;
    private ComboBox<Integer> pageSizeCombo;
    private TextField pageNumberField;
    private Button applyFiltersButton, clearFiltersButton;
    private Label pageLabel, statusLabel;

    // Stats labels
    private Label totalLogsValue, todayLogsValue, usersValue;

    // Collapsible sections
    private VBox headerSection;
    private VBox filtersSection;
    private VBox mainContent;
    private Button toggleFiltersButton;
    private Label quickStatsLabel;
    private boolean filtersVisible = false;

    private int currentPage = 1;
    private int totalPages = 1;
    private int[] pageSizeOptions = {25, 50, 100, 200, 500};
    private int currentPageSize = 100; // Default page size

    // Reference to controls section for updating pagination info
    private HBox controlsSection;

    public MaterialAuditLogPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initializeMaterialUI();
        setupEventHandlers();
        setupKeyboardShortcuts();
        loadFilterOptions();
        loadStats(); // Load initial stats

        // Load initial data
        refreshLogs();
    }

    public void setToken(String token) {
        this.jwtToken = token;
    }

    private void initializeMaterialUI() {
        // Create main content VBox
        mainContent = new VBox();
        mainContent.getStyleClass().addAll("md-spacing-24");
        mainContent.setPadding(new Insets(24, 24, 24, 24)); // Top, Right, Bottom, Left

        // Header section (initially hidden)
        headerSection = createMaterialHeader();
        headerSection.setVisible(false);
        headerSection.setManaged(false);

        // Filters section (initially hidden)
        filtersSection = createMaterialFilters();
        filtersSection.setVisible(false);
        filtersSection.setManaged(false);

        // Create toggle button for filters
        HBox toggleSection = createToggleSection();

        // Table section
        VBox tableSection = createMaterialTable();

        // Controls section
        controlsSection = createMaterialControls();

        mainContent.getChildren().addAll(
                toggleSection,
                headerSection,
                filtersSection,
                tableSection,
                controlsSection
        );
        VBox.setVgrow(tableSection, Priority.ALWAYS);

        // Set up ScrollPane
        setContent(mainContent);
        setFitToWidth(true);
        setFitToHeight(false);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        // Set minimum viewport height
        setPrefViewportHeight(600);

        // Style the scroll pane
        getStyleClass().add("md-scroll-pane");

        // Add padding to the ScrollPane itself
        setPadding(new Insets(16));
    }

    private HBox createToggleSection() {
        HBox toggleSection = new HBox();
        toggleSection.getStyleClass().addAll("md-card-outlined", "md-spacing-12");
        toggleSection.setPadding(new Insets(12, 20, 12, 20));
        toggleSection.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Audit Logs");
        titleLabel.getStyleClass().add("md-headline-medium");
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label subtitleLabel = new Label("System activity and user actions");
        subtitleLabel.getStyleClass().add("md-body-medium");
        subtitleLabel.setStyle("-fx-text-fill: #666666;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Quick stats in collapsed mode
        quickStatsLabel = new Label("Loading...");
        quickStatsLabel.getStyleClass().add("md-body-small");
        quickStatsLabel.setStyle("-fx-text-fill: #1976D2; -fx-font-weight: bold;");

        toggleFiltersButton = new Button("⚙️ Filters & Stats");
        toggleFiltersButton.getStyleClass().addAll("md-button", "md-button-outlined");
        toggleFiltersButton.setPadding(new Insets(8, 16, 8, 16));
        toggleFiltersButton.setTooltip(new Tooltip("Press ':' to toggle filters (Ctrl+F)"));

        VBox titleSection = new VBox();
        titleSection.setSpacing(2);
        titleSection.getChildren().addAll(titleLabel, subtitleLabel);

        toggleSection.getChildren().addAll(titleSection, spacer, quickStatsLabel, toggleFiltersButton);

        // Update quick stats
        updateQuickStats();

        // Add margin below toggle section
        VBox.setMargin(toggleSection, new Insets(0, 0, 16, 0));

        return toggleSection;
    }

    private void updateQuickStats() {
        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                try {
                    AuditLogResult totalResult = HttpClientUtil.fetchAuditLogs(serverUrl, 1, 1);
                    String today = LocalDate.now().toString();
                    AuditLogResult todayResult = HttpClientUtil.fetchAuditLogs(serverUrl, 1, 1,
                            null, null, today, today);

                    if (totalResult.errorMessage == null && todayResult.errorMessage == null) {
                        return String.format("%,d total logs • %,d today",
                                totalResult.total, todayResult.total);
                    } else {
                        return "Stats unavailable";
                    }
                } catch (Exception e) {
                    return "Error loading stats";
                }
            }
        };

        task.setOnSucceeded(e -> quickStatsLabel.setText(task.getValue()));
        task.setOnFailed(e -> quickStatsLabel.setText("Error loading stats"));

        new Thread(task).start();
    }

    private void setupKeyboardShortcuts() {
        // Set up keyboard event handler for the entire scene
        this.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SEMICOLON && event.isShiftDown()) {
                // Colon key pressed (:) - semicolon with shift
                toggleFiltersAndStats();
                event.consume();
            } else if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.F) {
                // Ctrl+F (or Cmd+F on Mac)
                toggleFiltersAndStats();
                event.consume();
            }
        });

        // Make sure the ScrollPane can receive focus
        this.setFocusTraversable(true);
        this.requestFocus();
    }

    private void toggleFiltersAndStats() {
        filtersVisible = !filtersVisible;

        if (filtersVisible) {
            showFiltersAndStats();
        } else {
            hideFiltersAndStats();
        }

        // Update button text
        toggleFiltersButton.setText(filtersVisible ? "⬆️ Hide Filters" : "⚙️ Filters & Stats");
    }

    private void showFiltersAndStats() {
        // Make sections visible and managed
        headerSection.setVisible(true);
        headerSection.setManaged(true);
        filtersSection.setVisible(true);
        filtersSection.setManaged(true);

        // Create fade-in animation
        FadeTransition headerFade = new FadeTransition(Duration.millis(300), headerSection);
        headerFade.setFromValue(0.0);
        headerFade.setToValue(1.0);

        FadeTransition filtersFade = new FadeTransition(Duration.millis(300), filtersSection);
        filtersFade.setFromValue(0.0);
        filtersFade.setToValue(1.0);

        // Create slide-down animation
        TranslateTransition headerSlide = new TranslateTransition(Duration.millis(300), headerSection);
        headerSlide.setFromY(-50);
        headerSlide.setToY(0);

        TranslateTransition filtersSlide = new TranslateTransition(Duration.millis(300), filtersSection);
        filtersSlide.setFromY(-30);
        filtersSlide.setToY(0);

        // Start animations
        headerFade.play();
        filtersFade.play();
        headerSlide.play();
        filtersSlide.play();

        // Focus on first filter after animation
        filtersFade.setOnFinished(e -> {
            if (startDatePicker != null) {
                startDatePicker.requestFocus();
            }
        });
    }

    private void hideFiltersAndStats() {
        // Create fade-out animation
        FadeTransition headerFade = new FadeTransition(Duration.millis(200), headerSection);
        headerFade.setFromValue(1.0);
        headerFade.setToValue(0.0);

        FadeTransition filtersFade = new FadeTransition(Duration.millis(200), filtersSection);
        filtersFade.setFromValue(1.0);
        filtersFade.setToValue(0.0);

        // Create slide-up animation
        TranslateTransition headerSlide = new TranslateTransition(Duration.millis(200), headerSection);
        headerSlide.setFromY(0);
        headerSlide.setToY(-50);

        TranslateTransition filtersSlide = new TranslateTransition(Duration.millis(200), filtersSection);
        filtersSlide.setFromY(0);
        filtersSlide.setToY(-30);

        // Hide sections after animation completes
        headerFade.setOnFinished(e -> {
            headerSection.setVisible(false);
            headerSection.setManaged(false);
        });

        filtersFade.setOnFinished(e -> {
            filtersSection.setVisible(false);
            filtersSection.setManaged(false);
        });

        // Start animations
        headerFade.play();
        filtersFade.play();
        headerSlide.play();
        filtersSlide.play();
    }

    private VBox createMaterialHeader() {
        VBox headerSection = new VBox();
        headerSection.getStyleClass().addAll("md-card-elevated", "md-spacing-16");
        headerSection.setPadding(new Insets(20, 24, 20, 24)); // Add internal padding

        Label titleLabel = new Label("Detailed Statistics");
        titleLabel.getStyleClass().add("md-headline-small");

        Label subtitleLabel = new Label("Track all system activities, user actions, and data changes");
        subtitleLabel.getStyleClass().add("md-body-medium");

        // Add spacing between title and stats
        VBox.setMargin(subtitleLabel, new Insets(0, 0, 16, 0));

        // Stats cards
        HBox statsCards = new HBox();
        statsCards.getStyleClass().add("md-spacing-16");
        statsCards.setAlignment(Pos.CENTER_LEFT);
        statsCards.setSpacing(16); // Add spacing between cards

        VBox totalLogsCard = createStatsCard("Total Logs", "Loading...", "📊");
        VBox todayLogsCard = createStatsCard("Today's Activity", "Loading...", "📅");
        VBox usersCard = createStatsCard("Active Users", "Loading...", "👥");

        // Store references to value labels for updating
        totalLogsValue = (Label) totalLogsCard.getChildren().get(2);
        todayLogsValue = (Label) todayLogsCard.getChildren().get(2);
        usersValue = (Label) usersCard.getChildren().get(2);

        statsCards.getChildren().addAll(totalLogsCard, todayLogsCard, usersCard);
        HBox.setHgrow(totalLogsCard, Priority.ALWAYS);
        HBox.setHgrow(todayLogsCard, Priority.ALWAYS);
        HBox.setHgrow(usersCard, Priority.ALWAYS);

        headerSection.getChildren().addAll(titleLabel, subtitleLabel, statsCards);

        // Add bottom margin to header section
        VBox.setMargin(headerSection, new Insets(0, 0, 16, 0));

        return headerSection;
    }

    private VBox createStatsCard(String title, String value, String icon) {
        VBox statsCard = new VBox();
        statsCard.getStyleClass().addAll("md-card-filled", "md-spacing-8");
        statsCard.setAlignment(Pos.CENTER);
        statsCard.setMinWidth(120);
        statsCard.setPrefWidth(150);
        statsCard.setPadding(new Insets(16, 12, 16, 12)); // Internal padding for cards

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("md-label-small");
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-alignment: center;");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("md-title-medium");
        valueLabel.setStyle("-fx-text-alignment: center; -fx-font-weight: bold;");

        // Add spacing between elements in the card
        VBox.setMargin(titleLabel, new Insets(8, 0, 4, 0));
        VBox.setMargin(valueLabel, new Insets(4, 0, 0, 0));

        statsCard.getChildren().addAll(iconLabel, titleLabel, valueLabel);
        return statsCard;
    }

    private VBox createMaterialFilters() {
        VBox filtersSection = new VBox();
        filtersSection.getStyleClass().addAll("md-card-outlined", "md-spacing-16");
        filtersSection.setPadding(new Insets(20, 24, 20, 24)); // Internal padding

        Label filtersTitle = new Label("Filter Audit Logs");
        filtersTitle.getStyleClass().add("md-title-medium");

        // Add keyboard shortcut hint
        Label shortcutHint = new Label("Tip: Press ':' or Ctrl+F to toggle this panel");
        shortcutHint.getStyleClass().add("md-body-small");
        shortcutHint.setStyle("-fx-text-fill: #666666; -fx-font-style: italic;");

        VBox titleSection = new VBox();
        titleSection.setSpacing(4);
        titleSection.getChildren().addAll(filtersTitle, shortcutHint);

        // Add margin below title
        VBox.setMargin(titleSection, new Insets(0, 0, 16, 0));

        // Date filters row
        HBox dateFiltersRow = new HBox();
        dateFiltersRow.getStyleClass().add("md-spacing-16");
        dateFiltersRow.setAlignment(Pos.CENTER_LEFT);
        dateFiltersRow.setSpacing(20); // Add spacing between date filters

        VBox startDateBox = new VBox();
        startDateBox.getStyleClass().add("md-spacing-8");
        startDateBox.setSpacing(8);
        Label startDateLabel = new Label("Start Date");
        startDateLabel.getStyleClass().add("md-label-large");
        startDatePicker = new DatePicker();
        startDatePicker.getStyleClass().add("md-text-field-outlined");
        startDatePicker.setPromptText("Select start date");
        startDatePicker.setPrefWidth(200);
        startDateBox.getChildren().addAll(startDateLabel, startDatePicker);

        VBox endDateBox = new VBox();
        endDateBox.getStyleClass().add("md-spacing-8");
        endDateBox.setSpacing(8);
        Label endDateLabel = new Label("End Date");
        endDateLabel.getStyleClass().add("md-label-large");
        endDatePicker = new DatePicker();
        endDatePicker.getStyleClass().add("md-text-field-outlined");
        endDatePicker.setPromptText("Select end date");
        endDatePicker.setPrefWidth(200);
        endDateBox.getChildren().addAll(endDateLabel, endDatePicker);

        dateFiltersRow.getChildren().addAll(startDateBox, endDateBox);
        HBox.setHgrow(startDateBox, Priority.ALWAYS);
        HBox.setHgrow(endDateBox, Priority.ALWAYS);

        // Add margin below date filters
        VBox.setMargin(dateFiltersRow, new Insets(0, 0, 16, 0));

        // User and action filters row
        HBox actionFiltersRow = new HBox();
        actionFiltersRow.getStyleClass().add("md-spacing-16");
        actionFiltersRow.setAlignment(Pos.CENTER_LEFT);
        actionFiltersRow.setSpacing(20); // Add spacing between filters

        VBox userFilterBox = new VBox();
        userFilterBox.getStyleClass().add("md-spacing-8");
        userFilterBox.setSpacing(8);
        Label userFilterLabel = new Label("User");
        userFilterLabel.getStyleClass().add("md-label-large");
        userFilterCombo = new ComboBox<>();
        userFilterCombo.getStyleClass().add("md-combo-box");
        userFilterCombo.getItems().add("All Users");
        userFilterCombo.setValue("All Users");
        userFilterCombo.setPrefWidth(200);
        userFilterBox.getChildren().addAll(userFilterLabel, userFilterCombo);

        VBox actionFilterBox = new VBox();
        actionFilterBox.getStyleClass().add("md-spacing-8");
        actionFilterBox.setSpacing(8);
        Label actionFilterLabel = new Label("Action Type");
        actionFilterLabel.getStyleClass().add("md-label-large");
        actionFilterCombo = new ComboBox<>();
        actionFilterCombo.getStyleClass().add("md-combo-box");
        actionFilterCombo.getItems().add("All Actions");
        actionFilterCombo.setValue("All Actions");
        actionFilterCombo.setPrefWidth(200);
        actionFilterBox.getChildren().addAll(actionFilterLabel, actionFilterCombo);

        actionFiltersRow.getChildren().addAll(userFilterBox, actionFilterBox);
        HBox.setHgrow(userFilterBox, Priority.ALWAYS);
        HBox.setHgrow(actionFilterBox, Priority.ALWAYS);

        // Add margin below action filters
        VBox.setMargin(actionFiltersRow, new Insets(0, 0, 16, 0));

        // Filter buttons
        HBox filterButtonsRow = new HBox();
        filterButtonsRow.getStyleClass().add("md-spacing-12");
        filterButtonsRow.setAlignment(Pos.CENTER_LEFT);
        filterButtonsRow.setSpacing(12); // Add spacing between buttons

        applyFiltersButton = new Button("Apply Filters");
        applyFiltersButton.getStyleClass().addAll("md-button", "md-button-filled");
        applyFiltersButton.setPadding(new Insets(12, 24, 12, 24)); // Button padding

        clearFiltersButton = new Button("Clear All");
        clearFiltersButton.getStyleClass().addAll("md-button", "md-button-outlined");
        clearFiltersButton.setPadding(new Insets(12, 24, 12, 24)); // Button padding

        Button hideFiltersButton = new Button("Hide Filters");
        hideFiltersButton.getStyleClass().addAll("md-button", "md-button-text");
        hideFiltersButton.setPadding(new Insets(12, 24, 12, 24));
        hideFiltersButton.setOnAction(e -> toggleFiltersAndStats());

        filterButtonsRow.getChildren().addAll(applyFiltersButton, clearFiltersButton, hideFiltersButton);

        filtersSection.getChildren().addAll(
                titleSection, dateFiltersRow, actionFiltersRow, filterButtonsRow
        );

        // Add bottom margin to filters section
        VBox.setMargin(filtersSection, new Insets(0, 0, 16, 0));

        return filtersSection;
    }

    private VBox createMaterialTable() {
        VBox tableSection = new VBox();
        tableSection.getStyleClass().addAll("md-card", "md-spacing-16");
        tableSection.setPadding(new Insets(20, 24, 20, 24)); // Internal padding

        // Table header
        HBox tableHeader = new HBox();
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        tableHeader.getStyleClass().add("md-spacing-16");

        Label tableTitle = new Label("Audit Log Entries");
        tableTitle.getStyleClass().add("md-title-medium");

        statusLabel = new Label("Ready to load audit logs");
        statusLabel.getStyleClass().add("md-body-small");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        tableHeader.getChildren().addAll(tableTitle, headerSpacer, statusLabel);

        // Add margin below table header
        VBox.setMargin(tableHeader, new Insets(0, 0, 16, 0));

        // Material Design table with proper scrolling
        tableData = FXCollections.observableArrayList();
        logTable = new TableView<>(tableData);
        logTable.getStyleClass().add("md-table-view");

        // Set table height constraints
        logTable.setPrefHeight(400);
        logTable.setMinHeight(300);
        logTable.setMaxHeight(500);

        // Enable table scrolling
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createMaterialTableColumns();

        // Make table grow within constraints
        VBox.setVgrow(logTable, Priority.NEVER); // Don't let table grow indefinitely

        tableSection.getChildren().addAll(tableHeader, logTable);

        // Add bottom margin to table section
        VBox.setMargin(tableSection, new Insets(0, 0, 16, 0));

        return tableSection;
    }

    private void createMaterialTableColumns() {
        TableColumn<AuditLogItem, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampCol.setPrefWidth(180);
        timestampCol.setMinWidth(150);

        TableColumn<AuditLogItem, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        userCol.setPrefWidth(120);
        userCol.setMinWidth(100);

        TableColumn<AuditLogItem, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        actionCol.setPrefWidth(150);
        actionCol.setMinWidth(120);

        TableColumn<AuditLogItem, String> cvIdCol = new TableColumn<>("CV ID");
        cvIdCol.setCellValueFactory(new PropertyValueFactory<>("cvId"));
        cvIdCol.setPrefWidth(200);
        cvIdCol.setMinWidth(150);

        TableColumn<AuditLogItem, String> ipCol = new TableColumn<>("IP Address");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        ipCol.setPrefWidth(150);
        ipCol.setMinWidth(120);

        logTable.getColumns().addAll(timestampCol, userCol, actionCol, cvIdCol, ipCol);
    }

    private HBox createMaterialControls() {
        HBox controlsSection = new HBox();
        controlsSection.getStyleClass().addAll("md-card-filled", "md-spacing-16");
        controlsSection.setAlignment(Pos.CENTER);
        controlsSection.setPadding(new Insets(20, 24, 20, 24));

        // Left controls (Back and Refresh)
        HBox leftControls = new HBox();
        leftControls.getStyleClass().add("md-spacing-12");
        leftControls.setSpacing(16);
        leftControls.setAlignment(Pos.CENTER_LEFT);

        backButton = new Button("← Back to Search");
        backButton.getStyleClass().addAll("md-button", "md-button-outlined");
        backButton.setPadding(new Insets(12, 24, 12, 24));

        refreshButton = new Button("🔄 Refresh Logs");
        refreshButton.getStyleClass().addAll("md-button", "md-button-tonal");
        refreshButton.setPadding(new Insets(12, 24, 12, 24));

        leftControls.getChildren().addAll(backButton, refreshButton);

        // Center - Enhanced pagination controls
        VBox paginationSection = new VBox();
        paginationSection.setAlignment(Pos.CENTER);
        paginationSection.setSpacing(12);
        paginationSection.setPadding(new Insets(8));

        // Page size selector
        HBox pageSizeSection = new HBox();
        pageSizeSection.setAlignment(Pos.CENTER);
        pageSizeSection.setSpacing(8);

        Label pageSizeLabel = new Label("Show:");
        pageSizeLabel.getStyleClass().add("md-label-medium");

        pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getStyleClass().add("md-combo-box");
        for (int size : pageSizeOptions) {
            pageSizeCombo.getItems().add(size);
        }
        pageSizeCombo.setValue(currentPageSize);
        pageSizeCombo.setPrefWidth(80);

        Label entriesLabel = new Label("entries per page");
        entriesLabel.getStyleClass().add("md-label-medium");

        pageSizeSection.getChildren().addAll(pageSizeLabel, pageSizeCombo, entriesLabel);

        // Navigation controls
        HBox navigationControls = new HBox();
        navigationControls.setAlignment(Pos.CENTER);
        navigationControls.setSpacing(8);

        // First page button
        firstPageButton = new Button("⏪");
        firstPageButton.getStyleClass().addAll("md-button", "md-button-filled");
        firstPageButton.setPrefWidth(40);
        firstPageButton.setTooltip(new Tooltip("First Page"));

        // Previous page button
        prevButton = new Button("Previous");
        prevButton.getStyleClass().addAll("md-button", "md-button-filled");
        prevButton.setPrefWidth(80);
        prevButton.setTooltip(new Tooltip("Previous Page"));

        // Current page display and input
        HBox pageInputSection = new HBox();
        pageInputSection.setAlignment(Pos.CENTER);
        pageInputSection.setSpacing(4);

        Label pageOfLabel = new Label("Page");
        pageOfLabel.getStyleClass().add("md-body-medium");

        pageNumberField = new TextField();
        pageNumberField.getStyleClass().add("md-text-field-outlined");
        pageNumberField.setPrefWidth(60);
        pageNumberField.setPromptText("1");
        pageNumberField.setText("1");

        Label ofLabel = new Label("of");
        ofLabel.getStyleClass().add("md-body-medium");

        Label totalPagesLabel = new Label("1");
        totalPagesLabel.getStyleClass().add("md-body-medium");
        totalPagesLabel.setStyle("-fx-font-weight: bold;");

        goToPageButton = new Button("Go");
        goToPageButton.getStyleClass().addAll("md-button", "md-button-tonal");
        goToPageButton.setPrefWidth(40);

        pageInputSection.getChildren().addAll(pageOfLabel, pageNumberField, ofLabel, totalPagesLabel, goToPageButton);

        // Next page button
        nextButton = new Button("Next");
        nextButton.getStyleClass().addAll("md-button", "md-button-filled");
        nextButton.setPrefWidth(80);
        nextButton.setTooltip(new Tooltip("Next Page"));

        // Last page button
        lastPageButton = new Button("⏩");
        lastPageButton.getStyleClass().addAll("md-button", "md-button-filled");
        lastPageButton.setPrefWidth(40);
        lastPageButton.setTooltip(new Tooltip("Last Page"));

        navigationControls.getChildren().addAll(
                firstPageButton, prevButton, pageInputSection, nextButton, lastPageButton
        );

        // Page info label
        pageLabel = new Label("Page 1 of 1 (0 entries)");
        pageLabel.getStyleClass().add("md-body-large");
        pageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2;");

        paginationSection.getChildren().addAll(pageSizeSection, navigationControls, pageLabel);

        // Spacers for layout
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        controlsSection.getChildren().addAll(leftControls, leftSpacer, paginationSection, rightSpacer);
        return controlsSection;
    }

    private void setupEventHandlers() {
        // Toggle button handler
        toggleFiltersButton.setOnAction(e -> toggleFiltersAndStats());

        // Existing basic handlers
        backButton.setOnAction(e -> parentApp.showView(CV_APP.SEARCH_VIEW));
        refreshButton.setOnAction(e -> {
            refreshLogs();
            loadStats(); // Also refresh stats
            updateQuickStats(); // Update quick stats too
        });

        applyFiltersButton.setOnAction(e -> {
            currentPage = 1;
            refreshLogs();
        });

        clearFiltersButton.setOnAction(e -> clearFilters());

        // Enhanced navigation handlers
        firstPageButton.setOnAction(e -> {
            currentPage = 1;
            refreshLogs();
        });

        prevButton.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                refreshLogs();
            }
        });

        nextButton.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                refreshLogs();
            }
        });

        lastPageButton.setOnAction(e -> {
            currentPage = totalPages;
            refreshLogs();
        });

        // Page size change handler
        pageSizeCombo.setOnAction(e -> {
            Integer newPageSize = pageSizeCombo.getValue();
            if (newPageSize != null && newPageSize != currentPageSize) {
                currentPageSize = newPageSize;
                currentPage = 1; // Reset to first page when changing page size
                refreshLogs();
            }
        });

        // Go to page handler
        goToPageButton.setOnAction(e -> goToSpecificPage());

        // Enter key in page number field
        pageNumberField.setOnAction(e -> goToSpecificPage());

        // Double-click table handler
        logTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                AuditLogItem selected = logTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showMaterialAuditDetails(selected);
                }
            }
        });
    }

    private void goToSpecificPage() {
        try {
            String pageText = pageNumberField.getText().trim();
            if (!pageText.isEmpty()) {
                int targetPage = Integer.parseInt(pageText);
                if (targetPage >= 1 && targetPage <= totalPages) {
                    currentPage = targetPage;
                    refreshLogs();
                } else {
                    // Show error - page out of range
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Invalid Page Number");
                    alert.setHeaderText("Page number out of range");
                    alert.setContentText(String.format("Please enter a page number between 1 and %d", totalPages));
                    alert.getDialogPane().getStyleClass().add("md-dialog");
                    alert.showAndWait();
                    pageNumberField.setText(String.valueOf(currentPage));
                }
            }
        } catch (NumberFormatException ex) {
            // Show error - invalid number format
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Invalid page number");
            alert.setContentText("Please enter a valid number.");
            alert.getDialogPane().getStyleClass().add("md-dialog");
            alert.showAndWait();
            pageNumberField.setText(String.valueOf(currentPage));
        }
    }

    private void loadFilterOptions() {
        Task<HttpClientUtil.FilterOptionsResult> task = new Task<>() {
            @Override
            protected HttpClientUtil.FilterOptionsResult call() {
                return HttpClientUtil.fetchFilterOptions(serverUrl);
            }
        };

        task.setOnSucceeded(e -> {
            HttpClientUtil.FilterOptionsResult result = task.getValue();
            if (result.errorMessage == null && result.users != null && result.actions != null) {
                // Update user filter
                userFilterCombo.getItems().clear();
                userFilterCombo.getItems().add("All Users");
                userFilterCombo.getItems().addAll(result.users);
                userFilterCombo.setValue("All Users");

                // Update action filter
                actionFilterCombo.getItems().clear();
                actionFilterCombo.getItems().add("All Actions");
                actionFilterCombo.getItems().addAll(result.actions);
                actionFilterCombo.setValue("All Actions");
            }
        });

        new Thread(task).start();
    }

    private void loadStats() {
        // Load stats using the existing API endpoints
        Task<Void> statsTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Get total logs using the audit logs API with a single item to get total count
                    AuditLogResult totalResult = HttpClientUtil.fetchAuditLogs(serverUrl, 1, 1);

                    // Get today's logs
                    String today = LocalDate.now().toString();
                    AuditLogResult todayResult = HttpClientUtil.fetchAuditLogs(serverUrl, 1, 1,
                            null, null, today, today);

                    // Get filter options to count users
                    FilterOptionsResult filterResult = HttpClientUtil.fetchFilterOptions(serverUrl);

                    // Update UI on JavaFX Application Thread
                    javafx.application.Platform.runLater(() -> {
                        if (totalResult.errorMessage == null) {
                            totalLogsValue.setText(String.format("%,d", totalResult.total));
                        } else {
                            totalLogsValue.setText("Error");
                        }

                        if (todayResult.errorMessage == null) {
                            todayLogsValue.setText(String.format("%,d", todayResult.total));
                        } else {
                            todayLogsValue.setText("Error");
                        }

                        if (filterResult.errorMessage == null && filterResult.users != null) {
                            usersValue.setText(String.valueOf(filterResult.users.size()));
                        } else {
                            usersValue.setText("Error");
                        }
                    });

                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        totalLogsValue.setText("Error");
                        todayLogsValue.setText("Error");
                        usersValue.setText("Error");
                    });
                }

                return null;
            }
        };

        statsTask.setOnFailed(e -> {
            javafx.application.Platform.runLater(() -> {
                totalLogsValue.setText("Error");
                todayLogsValue.setText("Error");
                usersValue.setText("Error");
            });
        });

        new Thread(statsTask).start();
    }

    public void refreshLogs() {
        statusLabel.setText("Loading audit logs...");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error");
        statusLabel.getStyleClass().add("md-status-info");

        String userFilter;
        String actionFilter;
        String startDate;
        String endDate;

        // Only apply filters if they exist (when filters section is initialized)
        if (userFilterCombo != null && userFilterCombo.getValue() != null) {
            userFilter = userFilterCombo.getValue().equals("All Users") ? null : userFilterCombo.getValue();
        } else {
            userFilter = null;
        }
        if (actionFilterCombo != null && actionFilterCombo.getValue() != null) {
            actionFilter = actionFilterCombo.getValue().equals("All Actions") ? null : actionFilterCombo.getValue();
        } else {
            actionFilter = null;
        }
        if (startDatePicker != null && startDatePicker.getValue() != null) {
            startDate = startDatePicker.getValue().toString();
        } else {
            startDate = null;
        }
        if (endDatePicker != null && endDatePicker.getValue() != null) {
            endDate = endDatePicker.getValue().toString();
        } else {
            endDate = null;
        }

        Task<HttpClientUtil.AuditLogResult> task = new Task<>() {
            @Override
            protected HttpClientUtil.AuditLogResult call() {
                // Use currentPageSize instead of the fixed LOGS_PER_PAGE
                return HttpClientUtil.fetchAuditLogs(serverUrl, currentPage, currentPageSize,
                        userFilter, actionFilter, startDate, endDate);
            }
        };

        task.setOnSucceeded(e -> {
            HttpClientUtil.AuditLogResult result = task.getValue();
            if (result.errorMessage == null) {
                tableData.clear();

                for (JSONObject log : result.logs) {
                    tableData.add(new AuditLogItem(
                            log.optString("timestamp", "N/A"),
                            log.optString("user", "N/A"),
                            log.optString("action", "N/A"),
                            log.optString("cv_id", "N/A"),
                            log.optString("ip_address", "N/A")
                    ));
                }

                totalPages = result.totalPages;

                // Update page info with more detailed information
                pageLabel.setText(String.format("Page %d of %d (%,d total entries, showing %d entries)",
                        currentPage, totalPages, result.total, result.logs.size()));

                // Update page number field
                pageNumberField.setText(String.valueOf(currentPage));

                updatePagination();

                statusLabel.setText(String.format("Loaded %d of %,d audit log entries (Page size: %d)",
                        result.logs.size(), result.total, currentPageSize));
                statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
                statusLabel.getStyleClass().add("md-status-success");
            } else {
                statusLabel.setText("Error: " + result.errorMessage);
                statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                statusLabel.getStyleClass().add("md-status-error");
            }
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Failed to load audit logs");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");
        });

        new Thread(task).start();
    }

    private void updatePagination() {
        boolean isFirstPage = currentPage <= 1;
        boolean isLastPage = currentPage >= totalPages;

        firstPageButton.setDisable(isFirstPage);
        prevButton.setDisable(isFirstPage);
        nextButton.setDisable(isLastPage);
        lastPageButton.setDisable(isLastPage);

        // Update the total pages display in the page input section
        VBox paginationSection = (VBox) controlsSection.getChildren().get(2);
        HBox navigationControls = (HBox) paginationSection.getChildren().get(1);
        HBox pageInputSection = (HBox) navigationControls.getChildren().get(2);
        Label totalPagesLabel = (Label) pageInputSection.getChildren().get(3);
        totalPagesLabel.setText(String.valueOf(totalPages));

        // Enable/disable go to page functionality
        goToPageButton.setDisable(totalPages <= 1);
        pageNumberField.setDisable(totalPages <= 1);
    }

    private void clearFilters() {
        if (startDatePicker != null) startDatePicker.setValue(null);
        if (endDatePicker != null) endDatePicker.setValue(null);
        if (userFilterCombo != null) userFilterCombo.setValue("All Users");
        if (actionFilterCombo != null) actionFilterCombo.setValue("All Actions");
        currentPage = 1;
        refreshLogs();
    }

    private void showMaterialAuditDetails(AuditLogItem item) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Audit Log Details");
        alert.setHeaderText("Detailed Audit Information");

        String details = String.format(
                "Timestamp: %s\n" +
                        "User: %s\n" +
                        "Action: %s\n" +
                        "CV ID: %s\n" +
                        "IP Address: %s",
                item.getTimestamp(),
                item.getUser(),
                item.getAction(),
                item.getCvId(),
                item.getIpAddress()
        );

        alert.setContentText(details);
        alert.getDialogPane().getStyleClass().add("md-dialog");
        alert.getDialogPane().lookupButton(ButtonType.OK)
                .getStyleClass().addAll("md-button", "md-button-filled");

        alert.showAndWait();
    }

    public static class AuditLogItem {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty user;
        private final SimpleStringProperty action;
        private final SimpleStringProperty cvId;
        private final SimpleStringProperty ipAddress;

        public AuditLogItem(String timestamp, String user, String action, String cvId, String ipAddress) {
            this.timestamp = new SimpleStringProperty(timestamp);
            this.user = new SimpleStringProperty(user);
            this.action = new SimpleStringProperty(action);
            this.cvId = new SimpleStringProperty(cvId);
            this.ipAddress = new SimpleStringProperty(ipAddress);
        }

        public String getTimestamp() { return timestamp.get(); }
        public String getUser() { return user.get(); }
        public String getAction() { return action.get(); }
        public String getCvId() { return cvId.get(); }
        public String getIpAddress() { return ipAddress.get(); }
    }
}