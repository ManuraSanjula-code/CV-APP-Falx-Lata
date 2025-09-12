package com.vse.cv_app.panels.view;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.utils.HttpClientUtil;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;

public class RAW_VIEW extends JPanel {

    private CV_APP parentApp;
    private String serverUrl;
    private String currentCvId = null;
    private JLabel cvIdLabel;
    private JButton backButton;
    private JButton refreshButton;
    private JTextArea rawTextArea;
    private JScrollPane textScrollPane;
    private JLabel statusLabel;

    public RAW_VIEW(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        initialize();
    }

    private void initialize() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        backButton = new JButton("Back to CV Details");
        cvIdLabel = new JLabel("CV ID: ");
        refreshButton = new JButton("Refresh Raw Text");

        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(backButton, gbc);

        gbc.gridx = 1;
        topPanel.add(Box.createHorizontalStrut(20), gbc);

        gbc.gridx = 2;
        topPanel.add(cvIdLabel, gbc);

        gbc.gridx = 3;
        topPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 4;
        gbc.anchor = GridBagConstraints.EAST;
        topPanel.add(refreshButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        rawTextArea = new JTextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rawTextArea.setText("Raw text will be displayed here...");
        rawTextArea.setLineWrap(true);
        rawTextArea.setWrapStyleWord(false);

        textScrollPane = new JScrollPane(rawTextArea);
        textScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        textScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        add(textScrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        add(statusLabel, BorderLayout.SOUTH);

        backButton.addActionListener(e -> parentApp.showView(CV_APP.VIEW_CV_VIEW));
        refreshButton.addActionListener(e -> {
            if (currentCvId != null && !currentCvId.isEmpty()) {
                loadRawText(currentCvId);
            }
        });
    }

    public void loadRawText(String cvId) {
        if (cvId == null || cvId.isEmpty()) {
            statusLabel.setText("Error: No CV ID provided.");
            rawTextArea.setText("Error: No CV ID provided.");
            return;
        }

        this.currentCvId = cvId;
        cvIdLabel.setText("CV ID: " + cvId);
        rawTextArea.setText("");
        setStatus("Loading raw text...", false);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        backButton.setEnabled(false);
        refreshButton.setEnabled(false);

        SwingWorker<HttpClientUtil.CVDetailsResult, Void> worker = new SwingWorker<HttpClientUtil.CVDetailsResult, Void>() {
            @Override
            protected HttpClientUtil.CVDetailsResult doInBackground() throws Exception {
                return HttpClientUtil.getCVDetails(serverUrl, cvId);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.CVDetailsResult result = get();

                    if (result.errorMessage != null) {
                        setStatus("Failed to load raw text: " + result.errorMessage, true);
                        rawTextArea.setText("Error: " + result.errorMessage);
                    } else {
                        try {
                            JSONObject cvDataJson = new JSONObject(result.jsonResponse);

                            String rawText = cvDataJson.optString("raw_text", "Raw text not available in the data.");

                            rawTextArea.setText(rawText);
                            setStatus("Raw text loaded successfully.", false);

                        } catch (Exception parseEx) {
                            setStatus("Error parsing CV details for raw text: " + parseEx.getMessage(), true);
                            rawTextArea.setText("Error parsing data: " + parseEx.getMessage());
                            parseEx.printStackTrace();
                        }
                    }
                } catch (Exception ex) {
                    setStatus("Unexpected error loading raw text: " + ex.getMessage(), true);
                    rawTextArea.setText("Unexpected error: " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                    backButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }


    private void setStatus(String message, boolean isError) {
        if (isError) {
            statusLabel.setForeground(Color.RED);
            System.err.println("RAW_VIEW Status (Error): " + message);
        } else {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            System.out.println("RAW_VIEW Status: " + message);
        }
        statusLabel.setText(message);
    }

    public void clearView() {
        this.currentCvId = null;
        cvIdLabel.setText("CV ID: ");
        rawTextArea.setText("");
        statusLabel.setText(" ");
    }
}