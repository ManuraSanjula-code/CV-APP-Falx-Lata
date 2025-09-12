package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.utils.HttpClientUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UploadPanel extends JPanel {
    private CV_APP parentApp;
    private String serverUrl;
    private JList<String> fileList;
    private DefaultListModel<String> listModel;
    private JButton selectButton;
    private JButton uploadButton;
    private JButton backButton;
    private JTextArea statusArea;
    private List<File> selectedFiles;
    private String token = null;
    public UploadPanel(CV_APP app, String serverUrl) {
        this.parentApp = app;
        this.serverUrl = serverUrl;
        this.selectedFiles = new ArrayList<>();
        this.token = app.getJwtToken();
        System.out.println(token);
        initialize();
    }

    public void setToken(String token) {
        this.token = token;
    }

    private void initialize() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Upload CVs"));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectButton = new JButton("Select PDFs...");
        uploadButton = new JButton("Upload Selected Files");
        backButton = new JButton("Back to Search");
        uploadButton.setEnabled(false);

        topPanel.add(selectButton);
        topPanel.add(uploadButton);
        topPanel.add(backButton);
        add(topPanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(fileList);
        listScrollPane.setBorder(new TitledBorder("Selected Files"));
        add(listScrollPane, BorderLayout.CENTER);

        statusArea = new JTextArea(5, 50);
        statusArea.setEditable(false);
        JScrollPane statusScrollPane = new JScrollPane(statusArea);
        statusScrollPane.setBorder(new TitledBorder("Status"));
        add(statusScrollPane, BorderLayout.SOUTH);

        selectButton.addActionListener(new SelectActionListener());
        uploadButton.addActionListener(new UploadActionListener());
        backButton.addActionListener(e -> parentApp.showView(CV_APP.SEARCH_VIEW));
    }

    private class SelectActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);

            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    "PDF and Word Files",
                    "pdf", "docx"
            );
            fileChooser.setFileFilter(filter);

            int result = fileChooser.showOpenDialog(UploadPanel.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] files = fileChooser.getSelectedFiles();
                selectedFiles.clear();
                listModel.clear();
                for (File file : files) {
                    selectedFiles.add(file);
                    listModel.addElement(file.getName());
                }
                uploadButton.setEnabled(!selectedFiles.isEmpty());
                statusArea.setText(selectedFiles.size() + " file(s) selected.");
            }
        }
    }

    private class UploadActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectedFiles.isEmpty()) {
                statusArea.setText("No files selected.");
                return;
            }

            statusArea.setText("Uploading files...\n");
            uploadButton.setEnabled(false);
            selectButton.setEnabled(false);

            // Perform upload in background
            SwingWorker<HttpClientUtil.UploadResult, Void> worker = new SwingWorker<HttpClientUtil.UploadResult, Void>() {
                @Override
                protected HttpClientUtil.UploadResult doInBackground() throws Exception {
                    return HttpClientUtil.uploadFilesWithToken(serverUrl, selectedFiles,token);
                }

                @Override
                protected void done() {
                    try {
                        HttpClientUtil.UploadResult result = get();
                        statusArea.append(result.message + "\n");
                        if (result.successCount > 0) {
                            statusArea.append("Upload completed. You can now search for the new CVs.\n");
                            selectedFiles.clear();
                            listModel.clear();
                        }
                    } catch (Exception ex) {
                        statusArea.append("Upload failed: " + ex.getMessage() + "\n");
                        ex.printStackTrace();
                    } finally {
                        uploadButton.setEnabled(true);
                        selectButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }
    }
}