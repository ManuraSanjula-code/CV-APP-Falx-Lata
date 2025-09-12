package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.utils.HttpClientUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchPanel extends JPanel {
    private CV_APP parentApp;
    private String serverUrl;
    private JTextField searchField;
    private JButton searchButton;
    private JButton uploadNavButton;
    private JButton fetchIndexesButton;
    private JButton refreshButton;
    private JButton clearFiltersButton;
    private JButton toggleFiltersButton;
    private JTable resultsTable;
    private SearchResultsTableModel tableModel;
    private JTabbedPane indexDisplayTabs;
    private JButton prevPageButton;
    private JButton nextPageButton;
    private JLabel pageLabel;
    private JTextArea statusArea;
    private JTextArea indexStatusArea;

    private JTextField dateFromField;
    private JTextField dateToField;
    private JComboBox<String> datePresetCombo;
    private JComboBox<String> sortByCombo;
    private JComboBox<String> sortOrderCombo;
    private JComboBox<String> logicCombo;
    private JSpinner perPageSpinner;

    private JPanel filtersPanel;
    private boolean filtersVisible = false;
    private JPanel mainPanel;

    private String currentQuery = "";
    private int currentPage = 1;
    private int totalPages = 1;
    private HttpClientUtil.SearchParameters currentSearchParams;

    public static class SearchResultsTableModel extends AbstractTableModel {
        private final String[] columnNames = {"ID", "Name", "Email", "Phone", "Filename", "Upload Date"};
        private List<SearchResultItem> data = new ArrayList<>();

        public void setData(List<SearchResultItem> data) {
            this.data = data != null ? data : new ArrayList<>();
            fireTableDataChanged();
        }

        public SearchResultItem getItemAt(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < data.size()) {
                return data.get(rowIndex);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SearchResultItem item = data.get(rowIndex);
            switch (columnIndex) {
                case 0: return item.id;
                case 1: return item.name;
                case 2: return item.email;
                case 3: return item.phone;
                case 4: return item.filename;
                case 5: return item.uploadDate;
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    public static class SearchResultItem {
        public String id;
        public String name;
        public String email;
        public String phone;
        public String filename;
        public String uploadDate;

        public SearchResultItem(String id, String name, String email, String phone, String filename, String uploadDate) {
            this.id = id;
            this.name = name != null ? name : "N/A";
            this.email = email != null ? email : "N/A";
            this.phone = phone != null ? phone : "N/A";
            this.filename = filename != null ? filename : "N/A";
            this.uploadDate = uploadDate != null ? uploadDate : "N/A";
        }
    }

    public SearchPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        this.currentSearchParams = new HttpClientUtil.SearchParameters();
        initialize();
        refresh();
    }

    private void initialize() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainPanel = createSearchAndFilterPanel();
        add(mainPanel, BorderLayout.NORTH);

        indexDisplayTabs = new JTabbedPane();

        tableModel = new SearchResultsTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Search Results"));

        indexDisplayTabs.addTab("Search Results", tableScrollPane);
        add(indexDisplayTabs, BorderLayout.CENTER);

        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        setupEventListeners();
    }

    private JPanel createSearchAndFilterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createTitledBorder("Search & Filter"));

        JPanel basicSearchPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        searchField = new JTextField(25);
        searchButton = new JButton("Search");
        uploadNavButton = new JButton("Go to Upload");
        fetchIndexesButton = new JButton("Show Indexes");
        refreshButton = new JButton("Refresh");
        clearFiltersButton = new JButton("Clear Filters");
        toggleFiltersButton = new JButton("Show Advanced Filters");

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        basicSearchPanel.add(new JLabel("Query:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        basicSearchPanel.add(searchField, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        basicSearchPanel.add(searchButton, gbc);
        gbc.gridx = 3;
        basicSearchPanel.add(Box.createHorizontalStrut(10), gbc);
        gbc.gridx = 4;
        basicSearchPanel.add(uploadNavButton, gbc);
        gbc.gridx = 5;
        basicSearchPanel.add(fetchIndexesButton, gbc);
        gbc.gridx = 6;
        basicSearchPanel.add(refreshButton, gbc);
        gbc.gridx = 7;
        basicSearchPanel.add(toggleFiltersButton, gbc);

        mainPanel.add(basicSearchPanel, BorderLayout.NORTH);

        filtersPanel = createFiltersPanel();
        filtersPanel.setVisible(false); // Start with filters hidden
        mainPanel.add(filtersPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createFiltersPanel() {
        JPanel filtersPanel = new JPanel(new GridBagLayout());
        filtersPanel.setBorder(BorderFactory.createTitledBorder("Advanced Filters"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        dateFromField = new JTextField(10);
        dateToField = new JTextField(10);
        dateFromField.setToolTipText("Format: YYYY-MM-DD");
        dateToField.setToolTipText("Format: YYYY-MM-DD");

        String[] presets = {
                "Custom Range",
                "Today",
                "Yesterday",
                "Last 7 days",
                "Last 30 days",
                "Last 3 months",
                "Last 6 months",
                "Last year"
        };
        datePresetCombo = new JComboBox<>(presets);

        String[] sortFields = {"upload_date", "name", "filename"};
        sortByCombo = new JComboBox<>(sortFields);

        String[] sortOrders = {"desc", "asc"};
        sortOrderCombo = new JComboBox<>(sortOrders);

        String[] logicOptions = {"and", "or"};
        logicCombo = new JComboBox<>(logicOptions);

        perPageSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 100, 5));

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        filtersPanel.add(new JLabel("Date Preset:"), gbc);
        gbc.gridx = 1;
        filtersPanel.add(datePresetCombo, gbc);

        gbc.gridx = 2;
        filtersPanel.add(new JLabel("From:"), gbc);
        gbc.gridx = 3;
        filtersPanel.add(dateFromField, gbc);

        gbc.gridx = 4;
        filtersPanel.add(new JLabel("To:"), gbc);
        gbc.gridx = 5;
        filtersPanel.add(dateToField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        filtersPanel.add(new JLabel("Sort by:"), gbc);
        gbc.gridx = 1;
        filtersPanel.add(sortByCombo, gbc);

        gbc.gridx = 2;
        filtersPanel.add(new JLabel("Order:"), gbc);
        gbc.gridx = 3;
        filtersPanel.add(sortOrderCombo, gbc);

        gbc.gridx = 4;
        filtersPanel.add(new JLabel("Logic:"), gbc);
        gbc.gridx = 5;
        filtersPanel.add(logicCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        filtersPanel.add(new JLabel("Per Page:"), gbc);
        gbc.gridx = 1;
        filtersPanel.add(perPageSpinner, gbc);

        gbc.gridx = 4; gbc.gridwidth = 2;
        filtersPanel.add(clearFiltersButton, gbc);

        return filtersPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JPanel paginationPanel = new JPanel(new FlowLayout());
        prevPageButton = new JButton("Previous");
        nextPageButton = new JButton("Next");
        pageLabel = new JLabel("Page: 1 of 1");
        paginationPanel.add(prevPageButton);
        paginationPanel.add(pageLabel);
        paginationPanel.add(nextPageButton);
        bottomPanel.add(paginationPanel, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new GridLayout(2, 1));

        statusArea = new JTextArea(1, 50);
        statusArea.setEditable(false);
        statusArea.setFont(statusArea.getFont().deriveFont(Font.PLAIN, 11));
        JScrollPane statusScrollPane = new JScrollPane(statusArea);
        statusScrollPane.setBorder(BorderFactory.createTitledBorder("Search Status"));
        statusScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 40));

        indexStatusArea = new JTextArea(1, 50);
        indexStatusArea.setEditable(false);
        indexStatusArea.setFont(indexStatusArea.getFont().deriveFont(Font.PLAIN, 11));
        JScrollPane indexStatusScrollPane = new JScrollPane(indexStatusArea);
        indexStatusScrollPane.setBorder(BorderFactory.createTitledBorder("Index Status"));
        indexStatusScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 40));

        statusPanel.add(statusScrollPane);
        statusPanel.add(indexStatusScrollPane);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);

        return bottomPanel;
    }

    private void setupEventListeners() {
        searchButton.addActionListener(new SearchActionListener());
        uploadNavButton.addActionListener(e -> parentApp.showView(CV_APP.UPLOAD_VIEW));
        refreshButton.addActionListener(e -> refresh());
        clearFiltersButton.addActionListener(e -> clearAllFilters());
        toggleFiltersButton.addActionListener(e -> toggleFiltersVisibility());

        prevPageButton.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                performSearchWithCurrentParams();
            }
        });

        nextPageButton.addActionListener(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                performSearchWithCurrentParams();
            }
        });

        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = resultsTable.getSelectedRow();
                if (selectedRow >= 0) {
                    SearchResultItem selectedItem = tableModel.getItemAt(selectedRow);
                    if (selectedItem != null) {
                        parentApp.showCVDetails(selectedItem.id);
                    }
                }
            }
        });

        fetchIndexesButton.addActionListener(e -> fetchAndDisplayIndexes());

        // Date preset combo listener
        datePresetCombo.addActionListener(e -> {
            String selectedPreset = (String) datePresetCombo.getSelectedItem();
            if (!"Custom Range".equals(selectedPreset)) {
                applyDatePreset(selectedPreset);
            }
        });
    }

    private void toggleFiltersVisibility() {
        filtersVisible = !filtersVisible;
        filtersPanel.setVisible(filtersVisible);
        toggleFiltersButton.setText(filtersVisible ? "Hide Advanced Filters" : "Show Advanced Filters");
        mainPanel.revalidate();
    }

    private void applyDatePreset(String preset) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        switch (preset) {
            case "Today":
                dateFromField.setText(today.format(formatter));
                dateToField.setText(today.format(formatter));
                break;
            case "Yesterday":
                LocalDate yesterday = today.minusDays(1);
                dateFromField.setText(yesterday.format(formatter));
                dateToField.setText(yesterday.format(formatter));
                break;
            case "Last 7 days":
                dateFromField.setText(today.minusDays(7).format(formatter));
                dateToField.setText(today.format(formatter));
                break;
            case "Last 30 days":
                dateFromField.setText(today.minusDays(30).format(formatter));
                dateToField.setText(today.format(formatter));
                break;
            case "Last 3 months":
                dateFromField.setText(today.minusDays(90).format(formatter));
                dateToField.setText(today.format(formatter));
                break;
            case "Last 6 months":
                dateFromField.setText(today.minusDays(180).format(formatter));
                dateToField.setText(today.format(formatter));
                break;
            case "Last year":
                dateFromField.setText(today.minusDays(365).format(formatter));
                dateToField.setText(today.format(formatter));
                break;
        }
    }

    private void clearAllFilters() {
        searchField.setText("");
        dateFromField.setText("");
        dateToField.setText("");
        datePresetCombo.setSelectedIndex(0);
        sortByCombo.setSelectedIndex(0);
        sortOrderCombo.setSelectedIndex(0);
        logicCombo.setSelectedIndex(0);
        perPageSpinner.setValue(10);

        currentSearchParams = new HttpClientUtil.SearchParameters();
        currentPage = 1;

        tableModel.setData(null);
        statusArea.setText("Filters cleared. Click Search to see all results.");
        pageLabel.setText("Page: - of -");
        updatePaginationButtons();
    }

    public void refresh() {
        performSearchWithCurrentParams();
    }

    private class SearchActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            performSearch();
        }
    }

    private void performSearch() {
        currentSearchParams = new HttpClientUtil.SearchParameters();
        currentSearchParams.query = searchField.getText().trim();
        currentSearchParams.page = 1;
        currentSearchParams.perPage = (Integer) perPageSpinner.getValue();

        String dateFrom = dateFromField.getText().trim();
        String dateTo = dateToField.getText().trim();

        if (!dateFrom.isEmpty()) {
            if (isValidDate(dateFrom)) {
                currentSearchParams.dateFrom = dateFrom;
            } else {
                statusArea.setText("Invalid 'From' date format. Use YYYY-MM-DD");
                return;
            }
        }

        if (!dateTo.isEmpty()) {
            if (isValidDate(dateTo)) {
                currentSearchParams.dateTo = dateTo;
            } else {
                statusArea.setText("Invalid 'To' date format. Use YYYY-MM-DD");
                return;
            }
        }

        currentSearchParams.sortBy = (String) sortByCombo.getSelectedItem();
        currentSearchParams.sortOrder = (String) sortOrderCombo.getSelectedItem();
        currentSearchParams.logic = (String) logicCombo.getSelectedItem();

        currentQuery = currentSearchParams.query;
        currentPage = 1;

        performSearchWithParams(currentSearchParams);
    }

    private void performSearchWithCurrentParams() {
        if (currentSearchParams != null) {
            currentSearchParams.page = currentPage;
            performSearchWithParams(currentSearchParams);
        } else {
            performSearch();
        }
    }

    private void performSearchWithParams(HttpClientUtil.SearchParameters params) {
        statusArea.setText("Searching...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        prevPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        searchButton.setEnabled(false);

        SwingWorker<HttpClientUtil.SearchResult, Void> worker = new SwingWorker<HttpClientUtil.SearchResult, Void>() {
            @Override
            protected HttpClientUtil.SearchResult doInBackground() throws Exception {
                return HttpClientUtil.searchCVs(serverUrl, params);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.SearchResult result = get();
                    if (result.errorMessage != null) {
                        statusArea.setText("Search failed: " + result.errorMessage);
                        tableModel.setData(null);
                        pageLabel.setText("Page: - of -");
                        totalPages = 1;
                    } else {
                        displayResults(result.jsonResponse);
                    }
                } catch (Exception ex) {
                    statusArea.setText("Search failed: " + ex.getMessage());
                    ex.printStackTrace();
                    tableModel.setData(null);
                    pageLabel.setText("Page: - of -");
                    totalPages = 1;
                } finally {
                    updatePaginationButtons();
                    searchButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        worker.execute();
    }

    private boolean isValidDate(String dateStr) {
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void displayResults(String jsonResponse) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            JSONArray resultsArray = responseObj.getJSONArray("results");
            totalPages = responseObj.optInt("total_pages", 1);
            int totalResults = responseObj.optInt("total", 0);

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);
                String id = item.getString("id");
                String name = item.optString("name", "N/A");
                String email = item.optString("email", "N/A");
                String phone = item.optString("phone", "N/A");
                String filename = item.optString("filename", "N/A");
                String uploadDate = item.optString("upload_date", "N/A");
                items.add(new SearchResultItem(id, name, email, phone, filename, uploadDate));
            }

            StringBuilder statusMsg = new StringBuilder();
            statusMsg.append("Found ").append(totalResults).append(" result(s). ");
            statusMsg.append("Showing page ").append(currentPage).append(" of ").append(totalPages).append(".");

            if (currentSearchParams != null) {
                if (currentSearchParams.dateFrom != null || currentSearchParams.dateTo != null) {
                    statusMsg.append(" Date filter: ");
                    if (currentSearchParams.dateFrom != null) {
                        statusMsg.append("from ").append(currentSearchParams.dateFrom);
                    }
                    if (currentSearchParams.dateTo != null) {
                        if (currentSearchParams.dateFrom != null) statusMsg.append(" ");
                        statusMsg.append("to ").append(currentSearchParams.dateTo);
                    }
                    statusMsg.append(".");
                }

                if (!currentSearchParams.sortBy.equals("upload_date") || !currentSearchParams.sortOrder.equals("desc")) {
                    statusMsg.append(" Sorted by ").append(currentSearchParams.sortBy)
                            .append(" (").append(currentSearchParams.sortOrder).append(").");
                }
            }

            statusArea.setText(statusMsg.toString());
            pageLabel.setText("Page: " + currentPage + " of " + totalPages);

        } catch (Exception e) {
            statusArea.setText("Error parsing search results: " + e.getMessage());
            e.printStackTrace();
            pageLabel.setText("Page: - of -");
            totalPages = 1;
        }
        tableModel.setData(items);
        indexDisplayTabs.setSelectedIndex(0);
    }

    private void updatePaginationButtons() {
        prevPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }

    private void fetchAndDisplayIndexes() {
        indexStatusArea.setText("Fetching indexes...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        fetchIndexesButton.setEnabled(false);

        SwingWorker<HttpClientUtil.IndexesResult, Void> worker = new SwingWorker<HttpClientUtil.IndexesResult, Void>() {
            @Override
            protected HttpClientUtil.IndexesResult doInBackground() throws Exception {
                return HttpClientUtil.fetchIndexes(serverUrl);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.IndexesResult result = get();
                    if (result.errorMessage != null) {
                        indexStatusArea.setText("Failed to fetch indexes: " + result.errorMessage);
                        clearIndexTabs();
                    } else {
                        displayIndexes(result.indexesData);
                        indexStatusArea.setText("Indexes fetched successfully.");
                    }
                } catch (Exception ex) {
                    indexStatusArea.setText("Error fetching indexes: " + ex.getMessage());
                    ex.printStackTrace();
                    clearIndexTabs();
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                    fetchIndexesButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void clearIndexTabs() {
        while (indexDisplayTabs.getTabCount() > 1) {
            indexDisplayTabs.removeTabAt(1);
        }
    }

    private void displayIndexes(Map<String, List<String>> indexes) {
        clearIndexTabs();

        for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
            String categoryName = entry.getKey();
            List<String> items = entry.getValue();

            if (items != null && !items.isEmpty()) {
                JPanel categoryPanel = new JPanel(new BorderLayout());

                DefaultListModel<String> listModel = new DefaultListModel<>();
                for (String item : items) {
                    listModel.addElement(item);
                }
                JList<String> itemList = new JList<>(listModel);
                itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

                itemList.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) {
                            String selectedItem = itemList.getSelectedValue();
                            if (selectedItem != null) {
                                searchField.setText(selectedItem);
                                performSearch();
                                indexDisplayTabs.setSelectedIndex(0);
                            }
                        }
                    }
                });

                JScrollPane listScrollPane = new JScrollPane(itemList);
                listScrollPane.setBorder(BorderFactory.createTitledBorder(capitalizeFirstLetter(categoryName)));

                categoryPanel.add(listScrollPane, BorderLayout.CENTER);
                indexDisplayTabs.addTab(capitalizeFirstLetter(categoryName), categoryPanel);
            }
        }
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase().replace("_", " ");
    }

    public void setSearchParameters(String query, String dateFrom, String dateTo) {
        searchField.setText(query != null ? query : "");
        dateFromField.setText(dateFrom != null ? dateFrom : "");
        dateToField.setText(dateTo != null ? dateTo : "");

        if (dateFrom != null || dateTo != null) {
            datePresetCombo.setSelectedItem("Custom Range");
        }
    }


    public void showRecentUploads(int days) {
        clearAllFilters();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        dateFromField.setText(startDate.format(formatter));
        dateToField.setText(today.format(formatter));

        // Set preset combo to appropriate value
        switch (days) {
            case 1:
                datePresetCombo.setSelectedItem("Today");
                break;
            case 7:
                datePresetCombo.setSelectedItem("Last 7 days");
                break;
            case 30:
                datePresetCombo.setSelectedItem("Last 30 days");
                break;
            default:
                datePresetCombo.setSelectedItem("Custom Range");
                break;
        }

        sortByCombo.setSelectedItem("upload_date");
        sortOrderCombo.setSelectedItem("desc");

        performSearch();
    }


    private void exportSearchResults() {
        if (currentSearchParams == null) {
            JOptionPane.showMessageDialog(this,
                    "No search results to export. Please perform a search first.",
                    "Export Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] options = {"JSON", "CSV"};
        int choice = JOptionPane.showOptionDialog(this,
                "Choose export format:",
                "Export Search Results",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == -1) return;

        String format = choice == 0 ? "json" : "csv";

        JOptionPane.showMessageDialog(this,
                "Export functionality would be implemented here for " + format + " format.",
                "Export",
                JOptionPane.INFORMATION_MESSAGE);
    }
}