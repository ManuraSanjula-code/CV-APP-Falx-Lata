package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.panels.dialog.AuditDetailDialog;
import com.vse.cv_app.utils.HttpClientUtil;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AuditLogPanel extends JPanel {

    private CV_APP mainApp;
    private String serverUrl;
    private JTable logTable;
    private LogTableModel tableModel;

    // Control buttons
    private JButton refreshButton;
    private JButton prevButton;
    private JButton nextButton;
    private JButton viewDetailsButton;
    private JButton clearFiltersButton;
    private JButton backButton;

    // Filter controls
    private JTextField startDateField;
    private JTextField endDateField;
    private JComboBox<String> userFilterCombo;
    private JComboBox<String> actionFilterCombo;
    private JButton applyFiltersButton;
    private JButton todayButton;
    private JButton last7DaysButton;
    private JButton last30DaysButton;

    // Status and pagination
    private JLabel pageLabel;
    private JLabel statusLabel;
    private JLabel filterStatusLabel;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalLogs = 0;

    private static final int LOGS_PER_PAGE = 20;

    public AuditLogPanel(CV_APP app, String serverUrl) {
        this.mainApp = app;
        this.serverUrl = serverUrl;
        initializeComponents();
        layoutComponents();
        addEventListeners();
        loadFilterOptions();
        refreshLogs();
    }

    private void initializeComponents() {
        tableModel = new LogTableModel();
        logTable = new JTable(tableModel);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setFillsViewportHeight(true);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Control buttons
        refreshButton = new JButton("Refresh");
        prevButton = new JButton("Previous");
        nextButton = new JButton("Next");
        viewDetailsButton = new JButton("View Details");
        clearFiltersButton = new JButton("Clear Filters");
        backButton = new JButton("Back to Search");

        // Filter controls
        startDateField = new JTextField(10);
        endDateField = new JTextField(10);
        userFilterCombo = new JComboBox<>();
        actionFilterCombo = new JComboBox<>();
        applyFiltersButton = new JButton("Apply Filters");
        todayButton = new JButton("Today");
        last7DaysButton = new JButton("Last 7 Days");
        last30DaysButton = new JButton("Last 30 Days");

        // Status labels
        pageLabel = new JLabel("Page 1 of 1");
        statusLabel = new JLabel("Click on a row to view full audit details");
        filterStatusLabel = new JLabel("No filters applied");

        // Set initial states
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        viewDetailsButton.setEnabled(false);

        // Add placeholders
        startDateField.setToolTipText("Start date (YYYY-MM-DD)");
        endDateField.setToolTipText("End date (YYYY-MM-DD)");

        // Style labels
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        statusLabel.setForeground(Color.GRAY);
        filterStatusLabel.setFont(filterStatusLabel.getFont().deriveFont(Font.ITALIC));
        filterStatusLabel.setForeground(Color.BLUE);

        backButton.addActionListener(e -> mainApp.showView(CV_APP.SEARCH_VIEW));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        // Top panel with filters
        JPanel topPanel = createFilterPanel();
        add(topPanel, BorderLayout.NORTH);

        // Center panel with table
        JScrollPane scrollPane = new JScrollPane(logTable);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with navigation and actions
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBorder(new TitledBorder("Filters"));

        // Date filter panel
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        datePanel.add(new JLabel("From:"));
        datePanel.add(startDateField);
        datePanel.add(new JLabel("To:"));
        datePanel.add(endDateField);
        datePanel.add(todayButton);
        datePanel.add(last7DaysButton);
        datePanel.add(last30DaysButton);

        // User and action filter panel
        JPanel filterControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterControlPanel.add(new JLabel("User:"));
        filterControlPanel.add(userFilterCombo);
        filterControlPanel.add(new JLabel("Action:"));
        filterControlPanel.add(actionFilterCombo);
        filterControlPanel.add(applyFiltersButton);
        filterControlPanel.add(clearFiltersButton);

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(filterStatusLabel);

        filterPanel.add(datePanel, BorderLayout.NORTH);
        filterPanel.add(filterControlPanel, BorderLayout.CENTER);
        filterPanel.add(statusPanel, BorderLayout.SOUTH);

        return filterPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Left side - Back button
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(backButton);

        // Center - Navigation
        JPanel centerPanel = new JPanel(new FlowLayout());
        centerPanel.add(prevButton);
        centerPanel.add(pageLabel);
        centerPanel.add(nextButton);

        // Right side - Actions
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(viewDetailsButton);
        rightPanel.add(refreshButton);

        bottomPanel.add(leftPanel, BorderLayout.WEST);
        bottomPanel.add(centerPanel, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        return bottomPanel;
    }

    private void addEventListeners() {
        refreshButton.addActionListener(e -> refreshLogs());

        prevButton.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                refreshLogs();
            }
        });

        nextButton.addActionListener(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                refreshLogs();
            }
        });

        viewDetailsButton.addActionListener(e -> showSelectedAuditDetails());

        applyFiltersButton.addActionListener(e -> {
            currentPage = 1;
            refreshLogs();
        });

        clearFiltersButton.addActionListener(e -> clearFilters());

        todayButton.addActionListener(e -> setDateRange(0, 0));
        last7DaysButton.addActionListener(e -> setDateRange(7, 0));
        last30DaysButton.addActionListener(e -> setDateRange(30, 0));

        // Table selection listener
        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                viewDetailsButton.setEnabled(logTable.getSelectedRow() >= 0);
            }
        });

        // Double-click listener
        logTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = logTable.getSelectedRow();
                    if (selectedRow >= 0) {
                        showSelectedAuditDetails();
                    }
                }
            }
        });
    }

    private void setDateRange(int daysBack, int daysForward) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusDays(daysBack);
        LocalDate endDate = now.plusDays(daysForward);

        startDateField.setText(startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        endDateField.setText(endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    private void clearFilters() {
        startDateField.setText("");
        endDateField.setText("");
        userFilterCombo.setSelectedIndex(0);
        actionFilterCombo.setSelectedIndex(0);
        currentPage = 1;
        refreshLogs();
    }

    private void loadFilterOptions() {
        SwingWorker<HttpClientUtil.FilterOptionsResult, Void> worker = new SwingWorker<HttpClientUtil.FilterOptionsResult, Void>() {
            @Override
            protected HttpClientUtil.FilterOptionsResult doInBackground() throws Exception {
                return HttpClientUtil.fetchFilterOptions(serverUrl);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.FilterOptionsResult result = get();
                    if (result.errorMessage == null) {
                        // Update user combo
                        userFilterCombo.removeAllItems();
                        userFilterCombo.addItem("All Users");
                        if (result.users != null) {
                            for (String user : result.users) {
                                userFilterCombo.addItem(user);
                            }
                        }

                        // Update action combo
                        actionFilterCombo.removeAllItems();
                        actionFilterCombo.addItem("All Actions");
                        if (result.actions != null) {
                            for (String action : result.actions) {
                                actionFilterCombo.addItem(action);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Silently fail - filter options are not critical
                }
            }
        };
        worker.execute();
    }

    private void showSelectedAuditDetails() {
        int selectedRow = logTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an audit log to view details.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JSONObject selectedLog = tableModel.getLogAt(selectedRow);
        if (selectedLog == null) {
            JOptionPane.showMessageDialog(this, "Unable to retrieve selected audit log.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String logId = selectedLog.optString("id", null);
        if (logId == null) {
            // If no ID in the log, try to generate one (for backward compatibility)
            logId = "audit_" + Math.abs(selectedLog.toString().hashCode()) % 1000000;
        }

        // Show the detail dialog
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        AuditDetailDialog detailDialog = new AuditDetailDialog(parentFrame, serverUrl, logId);
        detailDialog.setVisible(true);
    }

    public void refreshLogs() {
        String userFilter = getSelectedFilterValue(userFilterCombo, "All Users");
        String actionFilter = getSelectedFilterValue(actionFilterCombo, "All Actions");
        String startDate = startDateField.getText().trim();
        String endDate = endDateField.getText().trim();

        // Validate date format
        if (!startDate.isEmpty() && !isValidDateFormat(startDate)) {
            JOptionPane.showMessageDialog(this, "Invalid start date format. Use YYYY-MM-DD", "Invalid Date", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!endDate.isEmpty() && !isValidDateFormat(endDate)) {
            JOptionPane.showMessageDialog(this, "Invalid end date format. Use YYYY-MM-DD", "Invalid Date", JOptionPane.ERROR_MESSAGE);
            return;
        }

        updateFilterStatus(userFilter, actionFilter, startDate, endDate);

        SwingWorker<HttpClientUtil.AuditLogResult, Void> worker = new SwingWorker<HttpClientUtil.AuditLogResult, Void>() {
            @Override
            protected HttpClientUtil.AuditLogResult doInBackground() throws Exception {
                return HttpClientUtil.fetchAuditLogs(serverUrl, currentPage, LOGS_PER_PAGE,
                        userFilter, actionFilter, startDate, endDate);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.AuditLogResult result = get();
                    if (result.errorMessage != null) {
                        JOptionPane.showMessageDialog(AuditLogPanel.this, "Error loading logs: " + result.errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
                        tableModel.setLogs(new ArrayList<>());
                        updatePagination(1, 1, 0);
                    } else {
                        tableModel.setLogs(result.logs);
                        updatePagination(result.page, result.totalPages, result.total);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(AuditLogPanel.this, "Unexpected error loading logs: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    tableModel.setLogs(new ArrayList<>());
                    updatePagination(1, 1, 0);
                }
            }
        };
        worker.execute();
    }

    private String getSelectedFilterValue(JComboBox<String> combo, String defaultValue) {
        String selected = (String) combo.getSelectedItem();
        return (selected == null || selected.equals(defaultValue)) ? null : selected;
    }

    private boolean isValidDateFormat(String date) {
        try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateFilterStatus(String userFilter, String actionFilter, String startDate, String endDate) {
        StringBuilder status = new StringBuilder();

        if (userFilter != null || actionFilter != null || !startDate.isEmpty() || !endDate.isEmpty()) {
            status.append("Filters: ");
            boolean hasFilter = false;

            if (!startDate.isEmpty()) {
                status.append("From: ").append(startDate);
                hasFilter = true;
            }

            if (!endDate.isEmpty()) {
                if (hasFilter) status.append(", ");
                status.append("To: ").append(endDate);
                hasFilter = true;
            }

            if (userFilter != null) {
                if (hasFilter) status.append(", ");
                status.append("User: ").append(userFilter);
                hasFilter = true;
            }

            if (actionFilter != null) {
                if (hasFilter) status.append(", ");
                status.append("Action: ").append(actionFilter);
            }
        } else {
            status.append("No filters applied");
        }

        filterStatusLabel.setText(status.toString());
    }

    private void updatePagination(int page, int totalPages, int total) {
        this.currentPage = page;
        this.totalPages = totalPages;
        this.totalLogs = total;

        pageLabel.setText("Page " + page + " of " + totalPages + " (Total: " + total + ")");

        prevButton.setEnabled(page > 1);
        nextButton.setEnabled(page < totalPages);

        // Update status if no results
        if (total == 0) {
            statusLabel.setText("No audit logs found with current filters");
        } else {
            statusLabel.setText("Click on a row to view full audit details");
        }
    }

    private static class LogTableModel extends AbstractTableModel {
        private List<JSONObject> logs = new ArrayList<>();
        private final String[] columnNames = {"Timestamp (UTC)", "User", "Action", "CV ID", "IP Address"};

        public void setLogs(List<JSONObject> logs) {
            this.logs = logs != null ? logs : new ArrayList<>();
            fireTableDataChanged();
        }

        public JSONObject getLogAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= logs.size()) {
                return null;
            }
            return logs.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return logs.size();
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
            if (rowIndex < 0 || rowIndex >= logs.size()) {
                return null;
            }
            JSONObject log = logs.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    String timestampStr = log.optString("timestamp", "N/A");
                    try {
                        Instant instant = Instant.parse(timestampStr);
                        return instant.atZone(ZoneId.of("UTC"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));
                    } catch (DateTimeParseException e) {
                        return timestampStr;
                    }
                case 1:
                    return log.optString("user", "N/A");
                case 2:
                    return log.optString("action", "N/A");
                case 3:
                    return log.optString("cv_id", "N/A");
                case 4:
                    return log.optString("ip_address", "N/A");
                default:
                    return "N/A";
            }
        }
    }
}