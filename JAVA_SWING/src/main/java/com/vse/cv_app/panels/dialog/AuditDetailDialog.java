package com.vse.cv_app.panels.dialog;

import com.vse.cv_app.utils.HttpClientUtil;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AuditDetailDialog extends JDialog {

    private String serverUrl;
    private String logId;
    private JTextArea detailsArea;
    private JButton closeButton;
    private JButton refreshButton;
    private JScrollPane scrollPane;

    public AuditDetailDialog(Frame parent, String serverUrl, String logId) {
        super(parent, "Audit Log Details - ID: " + logId, true);
        this.serverUrl = serverUrl;
        this.logId = logId;

        initializeComponents();
        layoutComponents();
        addEventListeners();
        loadAuditDetails();

        setSize(800, 600);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setBackground(new Color(248, 248, 248));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(detailsArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        closeButton = new JButton("Close");
        refreshButton = new JButton("Refresh");
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        // Main content area
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new TitledBorder("Audit Log Details"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addEventListeners() {
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadAuditDetails();
            }
        });
    }

    private void loadAuditDetails() {
        detailsArea.setText("Loading audit details...");
        refreshButton.setEnabled(false);

        SwingWorker<HttpClientUtil.AuditDetailResult, Void> worker = new SwingWorker<HttpClientUtil.AuditDetailResult, Void>() {
            @Override
            protected HttpClientUtil.AuditDetailResult doInBackground() throws Exception {
                return HttpClientUtil.fetchAuditLogById(serverUrl, logId);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.AuditDetailResult result = get();
                    if (result.errorMessage != null) {
                        detailsArea.setText("Error loading audit details:\n" + result.errorMessage);
                    } else {
                        displayAuditDetails(result.auditLog, result.metadata);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    detailsArea.setText("Unexpected error loading audit details:\n" + e.getMessage());
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void displayAuditDetails(JSONObject auditLog, JSONObject metadata) {
        if (auditLog == null) {
            detailsArea.setText("No audit log data available.");
            return;
        }

        StringBuilder details = new StringBuilder();

        // Header
        details.append("=".repeat(80)).append("\n");
        details.append("AUDIT LOG DETAILS\n");
        details.append("=".repeat(80)).append("\n\n");

        // Basic Information
        details.append("BASIC INFORMATION:\n");
        details.append("-".repeat(40)).append("\n");
        details.append(String.format("%-20s: %s\n", "Log ID", auditLog.optString("id", "N/A")));

        // Format timestamp
        String timestamp = auditLog.optString("timestamp", "N/A");
        String formattedTime = formatTimestamp(timestamp);
        details.append(String.format("%-20s: %s\n", "Timestamp", formattedTime));

        details.append(String.format("%-20s: %s\n", "User", auditLog.optString("user", "N/A")));
        details.append(String.format("%-20s: %s\n", "Action", auditLog.optString("action", "N/A")));
        details.append(String.format("%-20s: %s\n", "CV ID", auditLog.optString("cv_id", "N/A")));
        details.append(String.format("%-20s: %s\n", "IP Address", auditLog.optString("ip_address", "N/A")));
        details.append("\n");

        // Session Information (if available)
        if (auditLog.has("session_info")) {
            JSONObject sessionInfo = auditLog.optJSONObject("session_info");
            if (sessionInfo != null && sessionInfo.length() > 0) {
                details.append("SESSION INFORMATION:\n");
                details.append("-".repeat(40)).append("\n");
                details.append(String.format("%-20s: %s\n", "User Agent", sessionInfo.optString("user_agent", "N/A")));
                details.append(String.format("%-20s: %s\n", "Session ID", sessionInfo.optString("session_id", "N/A")));
                details.append("\n");
            }
        }

        // Details Section
        if (auditLog.has("details")) {
            JSONObject detailsObj = auditLog.optJSONObject("details");
            if (detailsObj != null && detailsObj.length() > 0) {
                details.append("OPERATION DETAILS:\n");
                details.append("-".repeat(40)).append("\n");

                // Format details in a readable way
                for (String key : detailsObj.keySet()) {
                    Object value = detailsObj.get(key);
                    if (value instanceof JSONObject) {
                        details.append(String.format("%-20s:\n", key));
                        JSONObject subObj = (JSONObject) value;
                        for (String subKey : subObj.keySet()) {
                            details.append(String.format("  %-18s: %s\n", subKey, subObj.get(subKey)));
                        }
                    } else {
                        String valueStr = value.toString();
                        if (valueStr.length() > 60) {
                            details.append(String.format("%-20s:\n%s\n", key, formatLongText(valueStr, 4)));
                        } else {
                            details.append(String.format("%-20s: %s\n", key, valueStr));
                        }
                    }
                }
                details.append("\n");
            }
        }

        // Metadata Section (if available)
        if (metadata != null && metadata.length() > 0) {
            details.append("METADATA:\n");
            details.append("-".repeat(40)).append("\n");
            details.append(String.format("%-20s: %s\n", "Retrieved At", formatTimestamp(metadata.optString("retrieved_at", "N/A"))));
            details.append(String.format("%-20s: %s\n", "Log ID", metadata.optString("log_id", "N/A")));

            if (metadata.has("fields_present")) {
                details.append(String.format("%-20s: %s\n", "Fields Present", metadata.getJSONArray("fields_present").toString()));
            }
            details.append("\n");
        }

        // Raw JSON Section (for debugging)
        details.append("RAW JSON DATA:\n");
        details.append("-".repeat(40)).append("\n");
        details.append(auditLog.toString(2)); // Pretty print JSON with 2-space indentation

        detailsArea.setText(details.toString());
        detailsArea.setCaretPosition(0); // Scroll to top
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.equals("N/A")) {
            return "N/A";
        }

        try {
            Instant instant = Instant.parse(timestamp);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
            return instant.atZone(ZoneId.of("UTC")).format(formatter);
        } catch (DateTimeParseException e) {
            return timestamp; // Return original if parsing fails
        }
    }

    private String formatLongText(String text, int indent) {
        String indentStr = " ".repeat(indent);
        String[] words = text.split("\\s+");
        StringBuilder formatted = new StringBuilder();
        StringBuilder line = new StringBuilder(indentStr);

        for (String word : words) {
            if (line.length() + word.length() + 1 > 76) { // 80 - 4 indent
                formatted.append(line.toString().trim()).append("\n");
                line = new StringBuilder(indentStr);
            }
            line.append(word).append(" ");
        }

        if (line.length() > indentStr.length()) {
            formatted.append(line.toString().trim());
        }

        return formatted.toString();
    }
}
