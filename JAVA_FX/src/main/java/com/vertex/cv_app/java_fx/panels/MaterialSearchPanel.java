package com.vertex.cv_app.java_fx.panels;

import com.vertex.cv_app.java_fx.CV_APP;
import com.vertex.cv_app.utils.HttpClientUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

public class MaterialSearchPanel extends ScrollPane {

    private CV_APP parentApp;
    private String serverUrl;
    private String jwtToken;

    // UI Components
    private TextField searchField;
    private Button searchButton, uploadButton, refreshButton, clearFiltersButton;
    private TableView<SearchResultItem> resultsTable;
    private ObservableList<SearchResultItem> tableData;

    // Filter components
    private DatePicker dateFromPicker, dateToPicker;
    private ComboBox<String> datePresetCombo, sortByCombo, sortOrderCombo, logicCombo;
    private Spinner<Integer> perPageSpinner;
    private VBox filtersCard;
    private boolean filtersVisible = false;

    // Simplified Pagination components
    private Button prevButton, nextButton;
    private Label pageLabel, statusLabel, totalResultsLabel;
    private TextField pageInputField;
    private int currentPage = 1;
    private int totalPages = 1;
    private int totalResults = 0;

    private HttpClientUtil.SearchParameters currentSearchParams;

    public MaterialSearchPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        this.currentSearchParams = new HttpClientUtil.SearchParameters();

        initializeMaterialUI();
        setupEventHandlers();
        refresh();
    }

    public void setToken(String token) {
        this.jwtToken = token;
    }

    private void initializeMaterialUI() {
        // Create main content container
        VBox mainContent = new VBox();
        mainContent.getStyleClass().addAll("md-spacing-24", "md-padding-24");

        // Create search section
        VBox searchSection = createMaterialSearchSection();

        // Create filters (initially hidden)
        filtersCard = createMaterialFiltersCard();
        filtersCard.setVisible(false);
        filtersCard.setManaged(false);

        // Create results section
        VBox resultsSection = createMaterialResultsSection();

        // Create simplified pagination section
        HBox paginationSection = createSimplePaginationSection();

        mainContent.getChildren().addAll(searchSection, filtersCard, resultsSection, paginationSection);
        VBox.setVgrow(resultsSection, Priority.ALWAYS);

        // Configure ScrollPane properties
        setContent(mainContent);
        setFitToWidth(true);
        setFitToHeight(false);
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        getStyleClass().add("md-scroll-pane");

        // Set scroll speed for better user experience
        setVmax(400);
        setPannable(false);
    }

    private VBox createMaterialSearchSection() {
        VBox section = new VBox();
        section.getStyleClass().addAll("md-card-elevated", "md-spacing-16");

        // Section title
        Label titleLabel = new Label("Search CVs");
        titleLabel.getStyleClass().add("md-headline-small");

        Label subtitleLabel = new Label("Find candidates by searching through CV content, skills, and experience");
        subtitleLabel.getStyleClass().add("md-body-medium");

        // Search input area
        HBox searchArea = createMaterialSearchArea();

        // Action buttons
        HBox actionsArea = createMaterialActionButtons();

        section.getChildren().addAll(titleLabel, subtitleLabel, searchArea, actionsArea);
        return section;
    }

    private HBox createMaterialSearchArea() {
        HBox searchArea = new HBox();
        searchArea.getStyleClass().addAll("md-spacing-12");
        searchArea.setAlignment(Pos.CENTER_LEFT);

        // Material Design search field
        searchField = new TextField();
        searchField.getStyleClass().add("md-text-field-outlined");
        searchField.setPromptText("Search by name, skills, experience, education...");
        searchField.setPrefWidth(500);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Primary search button
        searchButton = new Button("Search");
        searchButton.getStyleClass().addAll("md-button", "md-button-filled");

        searchArea.getChildren().addAll(searchField, searchButton);
        return searchArea;
    }

    private HBox createMaterialActionButtons() {
        HBox actionsArea = new HBox();
        actionsArea.getStyleClass().add("md-spacing-12");
        actionsArea.setAlignment(Pos.CENTER_LEFT);

        uploadButton = new Button("Upload CVs");
        uploadButton.getStyleClass().addAll("md-button", "md-button-tonal");

        refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().addAll("md-button", "md-button-outlined");

        Button filtersButton = new Button("Filters");
        filtersButton.getStyleClass().addAll("md-button", "md-button-text");
        filtersButton.setOnAction(e -> toggleMaterialFilters());

        clearFiltersButton = new Button("Clear");
        clearFiltersButton.getStyleClass().addAll("md-button", "md-button-text");

        actionsArea.getChildren().addAll(uploadButton, refreshButton, filtersButton, clearFiltersButton);
        return actionsArea;
    }

    private VBox createMaterialFiltersCard() {
        VBox filtersCard = new VBox();
        filtersCard.getStyleClass().addAll("md-card-outlined", "md-spacing-16");

        Label filtersTitle = new Label("Advanced Filters");
        filtersTitle.getStyleClass().add("md-title-medium");

        // Date filters
        VBox dateSection = createMaterialDateFilters();

        // Sort and pagination filters
        VBox optionsSection = createMaterialOptionsFilters();

        filtersCard.getChildren().addAll(filtersTitle, dateSection, optionsSection);
        return filtersCard;
    }

    private VBox createMaterialDateFilters() {
        VBox dateSection = new VBox();
        dateSection.getStyleClass().add("md-spacing-12");

        Label dateLabel = new Label("Date Range");
        dateLabel.getStyleClass().add("md-label-large");

        HBox dateControls = new HBox();
        dateControls.getStyleClass().add("md-spacing-16");
        dateControls.setAlignment(Pos.CENTER_LEFT);

        datePresetCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Custom Range", "Today", "Yesterday", "Last 7 days",
                "Last 30 days", "Last 3 months", "Last 6 months", "Last year"
        ));
        datePresetCombo.getStyleClass().add("md-combo-box");
        datePresetCombo.setValue("Custom Range");

        dateFromPicker = new DatePicker();
        dateFromPicker.getStyleClass().add("md-text-field-outlined");
        dateFromPicker.setPromptText("From date");

        dateToPicker = new DatePicker();
        dateToPicker.getStyleClass().add("md-text-field-outlined");
        dateToPicker.setPromptText("To date");

        dateControls.getChildren().addAll(
                new Label("Preset:"), datePresetCombo,
                new Label("From:"), dateFromPicker,
                new Label("To:"), dateToPicker
        );

        dateSection.getChildren().addAll(dateLabel, dateControls);
        return dateSection;
    }

    private VBox createMaterialOptionsFilters() {
        VBox optionsSection = new VBox();
        optionsSection.getStyleClass().add("md-spacing-12");

        Label optionsLabel = new Label("Sort & Display Options");
        optionsLabel.getStyleClass().add("md-label-large");

        HBox optionsControls = new HBox();
        optionsControls.getStyleClass().add("md-spacing-16");
        optionsControls.setAlignment(Pos.CENTER_LEFT);

        sortByCombo = new ComboBox<>(FXCollections.observableArrayList(
                "upload_date", "name", "filename"
        ));
        sortByCombo.getStyleClass().add("md-combo-box");
        sortByCombo.setValue("upload_date");

        sortOrderCombo = new ComboBox<>(FXCollections.observableArrayList("desc", "asc"));
        sortOrderCombo.getStyleClass().add("md-combo-box");
        sortOrderCombo.setValue("desc");

        logicCombo = new ComboBox<>(FXCollections.observableArrayList("and", "or"));
        logicCombo.getStyleClass().add("md-combo-box");
        logicCombo.setValue("and");

        perPageSpinner = new Spinner<>(5, 100, 10, 5);
        perPageSpinner.getStyleClass().add("md-combo-box");
        perPageSpinner.setEditable(true);
        perPageSpinner.setPrefWidth(80);

        optionsControls.getChildren().addAll(
                new Label("Sort by:"), sortByCombo,
                new Label("Order:"), sortOrderCombo,
                new Label("Logic:"), logicCombo,
                new Label("Per page:"), perPageSpinner
        );

        optionsSection.getChildren().addAll(optionsLabel, optionsControls);
        return optionsSection;
    }

    private VBox createMaterialResultsSection() {
        VBox resultsSection = new VBox();
        resultsSection.getStyleClass().addAll("md-card", "md-spacing-16");

        // Results header with enhanced status information
        HBox resultsHeader = new HBox();
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        resultsHeader.getStyleClass().add("md-spacing-16");

        Label resultsTitle = new Label("Search Results");
        resultsTitle.getStyleClass().add("md-title-medium");

        VBox statusContainer = new VBox();
        statusContainer.setAlignment(Pos.CENTER_RIGHT);

        statusLabel = new Label("Ready to search");
        statusLabel.getStyleClass().add("md-body-small");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        resultsHeader.getChildren().addAll(resultsTitle, headerSpacer, statusContainer);

        // Material Design table
        tableData = FXCollections.observableArrayList();
        resultsTable = new TableView<>(tableData);
        resultsTable.getStyleClass().add("md-table-view");

        createMaterialTableColumns();

        VBox.setVgrow(resultsTable, Priority.ALWAYS);

        resultsSection.getChildren().addAll(resultsHeader, resultsTable);
        return resultsSection;
    }

    private void createMaterialTableColumns() {
        TableColumn<SearchResultItem, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(80);

        TableColumn<SearchResultItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);

        TableColumn<SearchResultItem, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setPrefWidth(200);

        TableColumn<SearchResultItem, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        phoneCol.setPrefWidth(130);

        TableColumn<SearchResultItem, String> filenameCol = new TableColumn<>("Filename");
        filenameCol.setCellValueFactory(new PropertyValueFactory<>("filename"));
        filenameCol.setPrefWidth(200);

        TableColumn<SearchResultItem, String> uploadDateCol = new TableColumn<>("Upload Date");
        uploadDateCol.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));
        uploadDateCol.setPrefWidth(130);

        resultsTable.getColumns().addAll(idCol, nameCol, emailCol, phoneCol, filenameCol, uploadDateCol);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private HBox createSimplePaginationSection() {
        HBox paginationContainer = new HBox();
        paginationContainer.getStyleClass().addAll("md-card-filled", "md-spacing-12");
        paginationContainer.setAlignment(Pos.CENTER);

        // Previous button
        prevButton = new Button("◀");
        prevButton.getStyleClass().addAll("md-button", "md-button-outlined");
        prevButton.setDisable(true);

        // Page input field - user can type and press Enter
        pageInputField = new TextField();
        pageInputField.getStyleClass().add("md-text-field-outlined");
        pageInputField.setPromptText("1");
        pageInputField.setPrefWidth(60);
        pageInputField.setMaxWidth(60);
        pageInputField.setAlignment(Pos.CENTER);

        // Page info label
        pageLabel = new Label("of 1");
        pageLabel.getStyleClass().add("md-body-medium");

        // Next button
        nextButton = new Button("▶");
        nextButton.getStyleClass().addAll("md-button", "md-button-outlined");
        nextButton.setDisable(true);

        // Results info
        totalResultsLabel = new Label("");
        totalResultsLabel.getStyleClass().add("md-body-small");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        paginationContainer.getChildren().addAll(
                prevButton, pageInputField, pageLabel, nextButton, spacer, totalResultsLabel
        );

        return paginationContainer;
    }

    private void setupEventHandlers() {
        searchButton.setOnAction(e -> performSearch());
        searchField.setOnAction(e -> performSearch());
        uploadButton.setOnAction(e -> parentApp.showView(CV_APP.UPLOAD_VIEW));
        refreshButton.setOnAction(e -> refresh());
        clearFiltersButton.setOnAction(e -> clearAllFilters());

        // Simplified pagination event handlers
        prevButton.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                updatePageInput();
                performSearchWithCurrentParams();
            }
        });

        nextButton.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                updatePageInput();
                performSearchWithCurrentParams();
            }
        });

        // Handle Enter key or focus lost
        pageInputField.setOnAction(e -> goToPage());
        pageInputField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                goToPage();
            }
        });

        // Only allow numbers
        pageInputField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                pageInputField.setText(oldText);
            }
        });

        // Per page change handler
        perPageSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (currentSearchParams != null && newValue != null && !newValue.equals(oldValue)) {
                // Reset to page 1 when changing page size
                currentPage = 1;
                performSearchWithCurrentParams();
            }
        });

        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                SearchResultItem selected = resultsTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    parentApp.showCVDetails(selected.getId());
                }
            }
        });

        datePresetCombo.setOnAction(e -> applyDatePreset(datePresetCombo.getValue()));
    }

    private void goToPage() {
        try {
            String pageText = pageInputField.getText().trim();
            if (!pageText.isEmpty()) {
                int targetPage = Integer.parseInt(pageText);
                if (targetPage >= 1 && targetPage <= totalPages && targetPage != currentPage) {
                    currentPage = targetPage;
                    performSearchWithCurrentParams();
                } else {
                    updatePageInput(); // Reset to current page if invalid
                }
            } else {
                updatePageInput(); // Reset if empty
            }
        } catch (NumberFormatException e) {
            updatePageInput(); // Reset if invalid number
        }
    }

    private void updatePageInput() {
        pageInputField.setText(String.valueOf(currentPage));
    }

    private void toggleMaterialFilters() {
        filtersVisible = !filtersVisible;
        filtersCard.setVisible(filtersVisible);
        filtersCard.setManaged(filtersVisible);
    }

    private void applyDatePreset(String preset) {
        if (preset.equals("Custom Range")) return;

        LocalDate today = LocalDate.now();
        switch (preset) {
            case "Today":
                dateFromPicker.setValue(today);
                dateToPicker.setValue(today);
                break;
            case "Yesterday":
                dateFromPicker.setValue(today.minusDays(1));
                dateToPicker.setValue(today.minusDays(1));
                break;
            case "Last 7 days":
                dateFromPicker.setValue(today.minusDays(7));
                dateToPicker.setValue(today);
                break;
            case "Last 30 days":
                dateFromPicker.setValue(today.minusDays(30));
                dateToPicker.setValue(today);
                break;
            case "Last 3 months":
                dateFromPicker.setValue(today.minusDays(90));
                dateToPicker.setValue(today);
                break;
            case "Last 6 months":
                dateFromPicker.setValue(today.minusDays(180));
                dateToPicker.setValue(today);
                break;
            case "Last year":
                dateFromPicker.setValue(today.minusDays(365));
                dateToPicker.setValue(today);
                break;
        }
    }

    private void performSearch() {
        currentSearchParams = new HttpClientUtil.SearchParameters();
        currentSearchParams.query = searchField.getText().trim();
        currentSearchParams.page = 1;
        currentSearchParams.perPage = perPageSpinner.getValue();

        if (dateFromPicker.getValue() != null) {
            currentSearchParams.dateFrom = dateFromPicker.getValue().toString();
        }
        if (dateToPicker.getValue() != null) {
            currentSearchParams.dateTo = dateToPicker.getValue().toString();
        }

        currentSearchParams.sortBy = sortByCombo.getValue();
        currentSearchParams.sortOrder = sortOrderCombo.getValue();
        currentSearchParams.logic = logicCombo.getValue();

        currentPage = 1;
        performSearchWithParams(currentSearchParams);
    }

    private void performSearchWithCurrentParams() {
        if (currentSearchParams != null) {
            currentSearchParams.page = currentPage;
            currentSearchParams.perPage = perPageSpinner.getValue(); // Ensure current per-page value
            performSearchWithParams(currentSearchParams);
        }
    }

    private void performSearchWithParams(HttpClientUtil.SearchParameters params) {
        statusLabel.setText("Searching...");
        statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error");
        statusLabel.getStyleClass().add("md-status-info");

        // Disable pagination controls during search
        setSearchingState(true);

        Task<HttpClientUtil.SearchResult> task = new Task<>() {
            @Override
            protected HttpClientUtil.SearchResult call() {
                return HttpClientUtil.searchCVs(serverUrl, params);
            }
        };

        task.setOnSucceeded(e -> {
            HttpClientUtil.SearchResult result = task.getValue();
            if (result.errorMessage != null) {
                statusLabel.setText("Search failed: " + result.errorMessage);
                statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                statusLabel.getStyleClass().add("md-status-error");
                totalResultsLabel.setText("");
                tableData.clear();
                resetPagination();
            } else {
                displayResults(result.jsonResponse);
            }
            setSearchingState(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Search failed");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");
            totalResultsLabel.setText("");
            tableData.clear();
            resetPagination();
            setSearchingState(false);
        });

        new Thread(task).start();
    }

    private void setSearchingState(boolean searching) {
        searchButton.setDisable(searching);
        prevButton.setDisable(searching);
        nextButton.setDisable(searching);
        pageInputField.setDisable(searching);
    }

    private void displayResults(String jsonResponse) {
        tableData.clear();

        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            JSONArray resultsArray = responseObj.getJSONArray("results");
            totalPages = responseObj.optInt("total_pages", 1);
            totalResults = responseObj.optInt("total", 0);

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);
                tableData.add(new SearchResultItem(
                        item.getString("id"),
                        item.optString("name", "N/A"),
                        item.optString("email", "N/A"),
                        item.optString("phone", "N/A"),
                        item.optString("filename", "N/A"),
                        item.optString("upload_date", "N/A")
                ));
            }

            // Update status labels
            statusLabel.setText(String.format("Found %d result(s)", totalResults));
            statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
            statusLabel.getStyleClass().add("md-status-success");

            updatePaginationControls();

        } catch (Exception e) {
            statusLabel.setText("Error parsing results: " + e.getMessage());
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
            statusLabel.getStyleClass().add("md-status-error");
            totalResultsLabel.setText("");
            resetPagination();
        }
    }

    private void updatePaginationControls() {
        prevButton.setDisable(currentPage <= 1);
        nextButton.setDisable(currentPage >= totalPages);
        pageLabel.setText(String.format("of %d", totalPages));
        updatePageInput();

        // Update results info
        if (totalResults > 0) {
            int startResult = ((currentPage - 1) * perPageSpinner.getValue()) + 1;
            int endResult = Math.min(currentPage * perPageSpinner.getValue(), totalResults);
            totalResultsLabel.setText(String.format("%d-%d of %d results", startResult, endResult, totalResults));
        } else {
            totalResultsLabel.setText("");
        }
    }

    private void resetPagination() {
        currentPage = 1;
        totalPages = 1;
        totalResults = 0;
        updatePaginationControls();
        pageInputField.clear();
    }

    private void clearAllFilters() {
        searchField.clear();
        dateFromPicker.setValue(null);
        dateToPicker.setValue(null);
        datePresetCombo.setValue("Custom Range");
        sortByCombo.setValue("upload_date");
        sortOrderCombo.setValue("desc");
        logicCombo.setValue("and");
        perPageSpinner.getValueFactory().setValue(10);

        currentPage = 1;
        currentSearchParams = null;
        tableData.clear();
        resetPagination();

        statusLabel.setText("Filters cleared - ready to search");
        statusLabel.getStyleClass().removeAll("md-status-error", "md-status-info");
        statusLabel.getStyleClass().add("md-status-success");
        totalResultsLabel.setText("");
    }

    public void refresh() {
        if (currentSearchParams != null) {
            performSearchWithCurrentParams();
        } else {
            statusLabel.setText("Loading recent uploads...");
            statusLabel.getStyleClass().removeAll("md-status-success", "md-status-error");
            statusLabel.getStyleClass().add("md-status-info");
            totalResultsLabel.setText("");

            Task<HttpClientUtil.SearchResult> task = new Task<>() {
                @Override
                protected HttpClientUtil.SearchResult call() {
                    return HttpClientUtil.getRecentUploads(serverUrl, 30, currentPage, perPageSpinner.getValue());
                }
            };

            task.setOnSucceeded(e -> {
                HttpClientUtil.SearchResult result = task.getValue();
                if (result.errorMessage != null) {
                    statusLabel.setText("Failed to load recent uploads: " + result.errorMessage);
                    statusLabel.getStyleClass().removeAll("md-status-success", "md-status-info");
                    statusLabel.getStyleClass().add("md-status-error");
                    totalResultsLabel.setText("");
                } else {
                    displayResults(result.jsonResponse);
                }
            });

            new Thread(task).start();
        }
    }

    public static class SearchResultItem {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty email;
        private final SimpleStringProperty phone;
        private final SimpleStringProperty filename;
        private final SimpleStringProperty uploadDate;

        public SearchResultItem(String id, String name, String email, String phone, String filename, String uploadDate) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
            this.email = new SimpleStringProperty(email);
            this.phone = new SimpleStringProperty(phone);
            this.filename = new SimpleStringProperty(filename);
            this.uploadDate = new SimpleStringProperty(uploadDate);
        }

        public String getId() { return id.get(); }
        public String getName() { return name.get(); }
        public String getEmail() { return email.get(); }
        public String getPhone() { return phone.get(); }
        public String getFilename() { return filename.get(); }
        public String getUploadDate() { return uploadDate.get(); }
    }
}