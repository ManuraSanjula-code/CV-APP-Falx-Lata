package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.utils.HttpClientUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class DownloadResult {
    public boolean success;
    public String message;
    public byte[] fileData;
    public String contentType;

    public DownloadResult(boolean success, String message, byte[] data, String type) {
        this.success = success;
        this.message = message;
        this.fileData = data;
        this.contentType = type;
    }

    public static DownloadResult downloadPdfData(String serverUrl, String cvId) {
        String downloadUrl = serverUrl + "/api/download_pdf/" + cvId;
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            org.apache.hc.client5.http.classic.methods.HttpGet downloadRequest = new org.apache.hc.client5.http.classic.methods.HttpGet(downloadUrl);
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(downloadRequest);
            int statusCode = response.getCode();
            if (statusCode == 200) {
                byte[] fileData = org.apache.hc.core5.http.io.entity.EntityUtils.toByteArray(response.getEntity());
                org.apache.hc.core5.http.Header contentTypeHeader = response.getFirstHeader("Content-Type");
                String contentType = contentTypeHeader != null ? contentTypeHeader.getValue() : "application/octet-stream";
                return new DownloadResult(true, "PDF downloaded successfully", fileData, contentType);
            } else {
                String errorMessage = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
                return new DownloadResult(false, "Server Error (" + statusCode + "): " + errorMessage, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new DownloadResult(false, "Network Error: " + e.getMessage(), null, null);
        }
    }

    public static DownloadResult downloadPdfToFile(String serverUrl, String cvId, String localFilePath) {
        String downloadUrl = serverUrl + "/api/download_pdf/" + cvId;
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            org.apache.hc.client5.http.classic.methods.HttpGet downloadRequest = new org.apache.hc.client5.http.classic.methods.HttpGet(downloadUrl);
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(downloadRequest);
            int statusCode = response.getCode();
            if (statusCode == 200) {
                try (java.io.InputStream inputStream = response.getEntity().getContent()) {
                    Path targetPath = Paths.get(localFilePath);
                    Files.copy(inputStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return new DownloadResult(true, "PDF saved to " + localFilePath, null, null);
            } else {
                String errorMessage = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
                return new DownloadResult(false, "Server Error (" + statusCode + "): " + errorMessage, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new DownloadResult(false, "Download/Error: " + e.getMessage(), null, null);
        }
    }
}

public class ViewCVPanel extends JPanel {
    private CV_APP parentApp;
    private String serverUrl;
    private JButton backButton;
    private JButton viewPdfButton;
    private JButton downloadPdfButton;
    private JButton editButton;
    private JButton saveButton;
    private JButton deleteButton;
    private JButton viewRawTextButton;
    private JLabel cvIdLabel;
    private JEditorPane personalInfoArea;
    private JTextArea skillsArea;
    private JTextArea educationArea;
    private JTextArea experienceArea;
    private JTextArea otherInfoArea;
    private JTextField nameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField addressField;
    private JTextField githubField;
    private JTextField linkedinField;
    private JLabel genderLabel;
    private JLabel typeLabel;
    private JComboBox<String> genderComboBox; 
    private JComboBox<String> typeComboBox;   

    private JTextArea skillsTextArea;
    private JTextArea educationTextArea;
    private JTextArea experienceTextArea;
    private JPanel viewModePanel;
    private JPanel editModePanel;
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private String currentCvId = null;
    private JSONObject currentCvData = null;
    private Dimension preferredViewSize = new Dimension(1200, 1000);
    private String token;
    private String fullSkillsContent = "";
    private String fullEducationContent = "";
    private String fullExperienceContent = "";
    private JButton skillsViewDetailsButton;
    private JButton educationViewDetailsButton;
    private JButton experienceViewDetailsButton;
    private JButton skillsEditDetailsButton;
    private JButton educationEditDetailsButton;
    private JButton experienceEditDetailsButton;

    private static final List<String> GENDER_OPTIONS = Arrays.asList("NONE", "Male", "Female", "Other");
    private static final List<String> TYPE_OPTIONS = Arrays.asList("NONE", "HR Manager", "Web Developer", "Software Engineer", "Designer", "Analyst", "Manager", "Other");

    public ViewCVPanel(CV_APP app, String serverUrl) {
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

        backButton = new JButton("Back to Search");
        cvIdLabel = new JLabel("CV ID: ");
        viewPdfButton = new JButton("View PDF");
        downloadPdfButton = new JButton("Download PDF");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        saveButton = new JButton("Save");
        saveButton.setVisible(false);
        viewRawTextButton = new JButton("View Raw Text");
        viewRawTextButton.setEnabled(true);

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(backButton, gbc);
        gbc.gridx = 1;
        topPanel.add(Box.createHorizontalStrut(20), gbc);
        gbc.gridx = 2;
        topPanel.add(cvIdLabel, gbc);
        gbc.gridx = 3;
        topPanel.add(Box.createHorizontalGlue(), gbc);
        gbc.gridx = 4; gbc.anchor = GridBagConstraints.EAST;
        topPanel.add(viewPdfButton, gbc);
        gbc.gridx = 5;
        topPanel.add(downloadPdfButton, gbc);
        gbc.gridx = 6;
        topPanel.add(editButton, gbc);
        gbc.gridx = 7;
        topPanel.add(viewRawTextButton, gbc);
        gbc.gridx = 8;
        topPanel.add(saveButton, gbc);
        gbc.gridx = 9;
        topPanel.add(deleteButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        viewRawTextButton.addActionListener(e -> {
            if (currentCvId != null && !currentCvId.isEmpty()) {
                parentApp.showRawTextView(currentCvId, this);
            } else {
                JOptionPane.showMessageDialog(this, "No CV loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        viewPdfButton.addActionListener(new ViewPdfActionListener());
        downloadPdfButton.addActionListener(new DownloadPdfActionListener());
        viewPdfButton.setEnabled(false);
        downloadPdfButton.setEnabled(false);

        editButton.addActionListener(e -> switchToEditMode(viewRawTextButton));
        saveButton.addActionListener(e -> saveChanges());
        deleteButton.addActionListener(e -> deleteCv());

        detailCardLayout = new CardLayout();
        detailCardPanel = new JPanel(detailCardLayout);

        viewModePanel = new JPanel();
        viewModePanel.setLayout(new BoxLayout(viewModePanel, BoxLayout.Y_AXIS));
        viewModePanel.setPreferredSize(preferredViewSize);

        personalInfoArea = createHtmlSection("Personal Information", viewModePanel);
        JPanel genderTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        genderTypePanel.setBorder(BorderFactory.createTitledBorder("Details"));
        genderLabel = new JLabel("Gender: NONE");
        typeLabel = new JLabel("Type: NONE");
        genderTypePanel.add(genderLabel);
        genderTypePanel.add(Box.createHorizontalStrut(20));
        genderTypePanel.add(typeLabel);
        genderTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewModePanel.add(genderTypePanel);
        viewModePanel.add(Box.createVerticalStrut(10));

        skillsArea = createSection("Skills", viewModePanel, true);
        educationArea = createSection("Education", viewModePanel, true);
        experienceArea = createSection("Experience", viewModePanel, true);
        otherInfoArea = createSection("Other Information", viewModePanel, false);

        JScrollPane viewScrollPane = new JScrollPane(viewModePanel);
        viewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        viewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        viewScrollPane.setBorder(null);

        editModePanel = new JPanel();
        editModePanel.setLayout(new BoxLayout(editModePanel, BoxLayout.Y_AXIS));
        editModePanel.setPreferredSize(preferredViewSize);
        setupEditFields(editModePanel);

        JScrollPane editScrollPane = new JScrollPane(editModePanel);
        editScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        editScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        editScrollPane.setBorder(null);

        detailCardPanel.add(viewScrollPane, "VIEW_MODE");
        detailCardPanel.add(editScrollPane, "EDIT_MODE");

        add(detailCardPanel, BorderLayout.CENTER);

        backButton.addActionListener(e -> {
            switchToViewMode();
            parentApp.showView(CV_APP.SEARCH_VIEW);
        });
    }

    public void setToken(String token) {
        this.token = token;
    }

    private JEditorPane createHtmlSection(String title, JPanel parentPanel) {
        JPanel sectionPanel = new JPanel(new BorderLayout());
        sectionPanel.setPreferredSize(preferredViewSize);
        sectionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        editorPane.setBackground(UIManager.getColor("Panel.background"));

        editorPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(new URI(e.getURL().toString()));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ViewCVPanel.this,
                                "Could not open link: " + e.getURL().toString() + "Error: " + ex.getMessage(),
                                "Link Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        sectionPanel.add(scrollPane, BorderLayout.CENTER);
        sectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, sectionPanel.getPreferredSize().height));
        parentPanel.add(sectionPanel);
        parentPanel.add(Box.createVerticalStrut(10));
        return editorPane;
    }

    private JTextArea createSection(String title, JPanel parentPanel, boolean addDetailsButton) {
        JPanel sectionPanel = new JPanel(new BorderLayout());
        sectionPanel.setPreferredSize(preferredViewSize);
        sectionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea textArea = new JTextArea(5, 60);
        textArea.setEditable(false);
        textArea.setFont(textArea.getFont().deriveFont(Font.PLAIN, 12));
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        textArea.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        sectionPanel.add(scrollPane, BorderLayout.CENTER);

        if (addDetailsButton) {
            JButton viewDetailsButton = new JButton("View Full Content");
            switch (title) {
                case "Skills":
                    skillsViewDetailsButton = viewDetailsButton;
                    skillsViewDetailsButton.addActionListener(e -> showDetailsDialog("Skills", fullSkillsContent, false));
                    break;
                case "Education":
                    educationViewDetailsButton = viewDetailsButton;
                    educationViewDetailsButton.addActionListener(e -> showDetailsDialog("Education", fullEducationContent, false));
                    break;
                case "Experience":
                    experienceViewDetailsButton = viewDetailsButton;
                    experienceViewDetailsButton.addActionListener(e -> showDetailsDialog("Experience", fullExperienceContent, false));
                    break;
            }
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(viewDetailsButton);
            sectionPanel.add(buttonPanel, BorderLayout.SOUTH);
        }

        sectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, sectionPanel.getPreferredSize().height));
        parentPanel.add(sectionPanel);
        parentPanel.add(Box.createVerticalStrut(10));
        return textArea;
    }


    private void setupEditFields(JPanel parentPanel) {
        JPanel personalInfoEditPanel = new JPanel();
        personalInfoEditPanel.setLayout(new BoxLayout(personalInfoEditPanel, BoxLayout.Y_AXIS));
        personalInfoEditPanel.setBorder(BorderFactory.createTitledBorder("Personal Information"));
        personalInfoEditPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameField = new JTextField(30);
        emailField = new JTextField(30);
        phoneField = new JTextField(30);
        addressField = new JTextField(30);
        githubField = new JTextField(30);
        linkedinField = new JTextField(30);

        personalInfoEditPanel.add(new JLabel("Name:"));
        personalInfoEditPanel.add(nameField);
        personalInfoEditPanel.add(Box.createVerticalStrut(5));
        personalInfoEditPanel.add(new JLabel("Email:"));
        personalInfoEditPanel.add(emailField);
        personalInfoEditPanel.add(Box.createVerticalStrut(5));
        personalInfoEditPanel.add(new JLabel("Phone:"));
        personalInfoEditPanel.add(phoneField);
        personalInfoEditPanel.add(Box.createVerticalStrut(5));
        personalInfoEditPanel.add(new JLabel("Address:"));
        personalInfoEditPanel.add(addressField);
        personalInfoEditPanel.add(Box.createVerticalStrut(5));
        personalInfoEditPanel.add(new JLabel("GitHub:"));
        personalInfoEditPanel.add(githubField);
        personalInfoEditPanel.add(Box.createVerticalStrut(5));
        personalInfoEditPanel.add(new JLabel("LinkedIn:"));
        personalInfoEditPanel.add(linkedinField);

        parentPanel.add(personalInfoEditPanel);
        parentPanel.add(Box.createVerticalStrut(10));

        JPanel genderTypeEditPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        genderTypeEditPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        genderTypeEditPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        genderComboBox = new JComboBox<>(GENDER_OPTIONS.toArray(new String[0]));
        typeComboBox = new JComboBox<>(TYPE_OPTIONS.toArray(new String[0]));
        typeComboBox = new JComboBox<>(TYPE_OPTIONS.toArray(new String[0]));
        typeComboBox.setEditable(true);

        typeComboBox.addActionListener(e -> {
            Object selected = typeComboBox.getSelectedItem();
            if (selected == null) return;

            String selectedStr = selected.toString();

            if ("Other".equals(selectedStr)) {
                SwingUtilities.invokeLater(() -> {
                    String customType = JOptionPane.showInputDialog(
                        ViewCVPanel.this,
                        "Enter custom type (required):",
                        "Custom Type"
                    );

                    if (customType != null && !customType.trim().isEmpty()) {
                        typeComboBox.setSelectedItem(customType.trim());
                    } else {
                        typeComboBox.setSelectedItem("Other");
                    }
                });
            }
        });
        genderTypeEditPanel.add(new JLabel("Gender:"));
        genderTypeEditPanel.add(genderComboBox);
        genderTypeEditPanel.add(Box.createHorizontalStrut(20));
        genderTypeEditPanel.add(new JLabel("Type:"));
        genderTypeEditPanel.add(typeComboBox);

        parentPanel.add(genderTypeEditPanel);
        parentPanel.add(Box.createVerticalStrut(10));
        
        JPanel skillsEditPanel = new JPanel(new BorderLayout());
        skillsEditPanel.setBorder(BorderFactory.createTitledBorder("Skills (JSON)"));
        skillsEditPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        skillsTextArea = new JTextArea(12, 60);
        skillsTextArea.setFont(skillsTextArea.getFont().deriveFont(Font.PLAIN, 12));

        JScrollPane skillsEditScrollPane = new JScrollPane(skillsTextArea);
        skillsEditDetailsButton = new JButton("Edit in Dialog");
        skillsEditDetailsButton.addActionListener(e -> openEditDialog("Skills", skillsTextArea));

        JPanel skillsButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        skillsButtonPanel.add(skillsEditDetailsButton);

        skillsEditPanel.add(skillsEditScrollPane, BorderLayout.CENTER);
        skillsEditPanel.add(skillsButtonPanel, BorderLayout.SOUTH);

        parentPanel.add(skillsEditPanel);
        parentPanel.add(Box.createVerticalStrut(10));

        JPanel educationEditPanel = new JPanel(new BorderLayout());
        educationEditPanel.setBorder(BorderFactory.createTitledBorder("Education (JSON)"));
        educationEditPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        educationTextArea = new JTextArea(12, 60);
        educationTextArea.setFont(educationTextArea.getFont().deriveFont(Font.PLAIN, 12));

        JScrollPane educationEditScrollPane = new JScrollPane(educationTextArea);
        educationEditDetailsButton = new JButton("Edit in Dialog");
        educationEditDetailsButton.addActionListener(e -> openEditDialog("Education", educationTextArea));

        JPanel educationButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        educationButtonPanel.add(educationEditDetailsButton);

        educationEditPanel.add(educationEditScrollPane, BorderLayout.CENTER);
        educationEditPanel.add(educationButtonPanel, BorderLayout.SOUTH);

        parentPanel.add(educationEditPanel);
        parentPanel.add(Box.createVerticalStrut(10));

        JPanel experienceEditPanel = new JPanel(new BorderLayout());
        experienceEditPanel.setBorder(BorderFactory.createTitledBorder("Experience (JSON)"));
        experienceEditPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        experienceTextArea = new JTextArea(12, 60);
        experienceTextArea.setFont(experienceTextArea.getFont().deriveFont(Font.PLAIN, 12));

        JScrollPane experienceEditScrollPane = new JScrollPane(experienceTextArea);
        experienceEditDetailsButton = new JButton("Edit in Dialog");
        experienceEditDetailsButton.addActionListener(e -> openEditDialog("Experience", experienceTextArea));

        JPanel experienceButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        experienceButtonPanel.add(experienceEditDetailsButton);

        experienceEditPanel.add(experienceEditScrollPane, BorderLayout.CENTER);
        experienceEditPanel.add(experienceButtonPanel, BorderLayout.SOUTH);

        parentPanel.add(experienceEditPanel);
        parentPanel.add(Box.createVerticalStrut(10));

        JTextArea otherPlaceholder = new JTextArea("Edit other sections (Languages, Certifications, etc.) here.Implementation depends on desired complexity (JSON edit vs. structured forms).");
        otherPlaceholder.setEditable(false);
        otherPlaceholder.setOpaque(false);
        parentPanel.add(otherPlaceholder);
        parentPanel.add(Box.createVerticalStrut(10));
    }

    private void openEditDialog(String title, JTextArea textAreaToEdit) {
        if (textAreaToEdit != null) {
            TextDetailDialog dialog = new TextDetailDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this),
                    "Edit " + title + " Details",
                    textAreaToEdit.getText(),
                    true
            );
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                String modifiedText = dialog.getModifiedText();
                if (modifiedText != null) {
                    textAreaToEdit.setText(modifiedText);
                }
            }
        }
    }

    public void loadCV(String cvId) {
        if (cvId == null || cvId.equals(currentCvId)) {
            return;
        }
        this.currentCvId = cvId;
        this.currentCvData = null;
        cvIdLabel.setText("CV ID: " + cvId);
        clearAllFields();
        setStatus("Loading CV details...", false, true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        backButton.setEnabled(false);
        viewPdfButton.setEnabled(false);
        downloadPdfButton.setEnabled(false);
        editButton.setEnabled(false);
        saveButton.setEnabled(true);
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
                        setStatus("Failed to load CV: " + result.errorMessage, true, true);
                        switchToViewMode();
                    } else {
                        try {
                            currentCvData = new JSONObject(result.jsonResponse);
                            displayCVDetailsInViewMode(currentCvData);
                            setStatus("CV loaded successfully.", false, true);
                            switchToViewMode();
                        } catch (Exception parseEx) {
                            setStatus("Error parsing CV details: " + parseEx.getMessage(), true, true);
                            switchToViewMode();
                        }
                    }
                } catch (Exception ex) {
                    setStatus("Failed to load CV: " + ex.getMessage(), true, true);
                    ex.printStackTrace();
                    switchToViewMode();
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                    backButton.setEnabled(true);
                    viewPdfButton.setEnabled(true);
                    downloadPdfButton.setEnabled(true);
                    editButton.setEnabled(true);
                    saveButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void clearAllFields() {
        personalInfoArea.setText("");
        skillsArea.setText("");
        educationArea.setText("");
        experienceArea.setText("");
        otherInfoArea.setText("");
        fullSkillsContent = "";
        fullEducationContent = "";
        fullExperienceContent = "";
        if (nameField != null) nameField.setText("");
        if (emailField != null) emailField.setText("");
        if (phoneField != null) phoneField.setText("");
        if (addressField != null) addressField.setText("");
        if (githubField != null) githubField.setText("");
        if (linkedinField != null) linkedinField.setText("");
        if (skillsTextArea != null) skillsTextArea.setText("");
        if (educationTextArea != null) educationTextArea.setText("");
        if (experienceTextArea != null) experienceTextArea.setText("");
        if (genderLabel != null) genderLabel.setText("Gender: NONE");
        if (typeLabel != null) typeLabel.setText("Type: NONE");
        if (genderComboBox != null) genderComboBox.setSelectedItem("NONE");
        if (typeComboBox != null) typeComboBox.setSelectedItem("NONE");
    }

    private void switchToEditMode(JButton viewRawTextButton) {
        if (currentCvData == null) {
            JOptionPane.showMessageDialog(this, "No CV data loaded to edit.", "Edit Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        populateEditFields(currentCvData);
        detailCardLayout.show(detailCardPanel, "EDIT_MODE");
        editButton.setVisible(false);
        saveButton.setVisible(true);
        setStatus("Editing mode. Make changes and click 'Save'.", false, false);
    }

    private void populateEditFields(JSONObject cvData) {
        try {
            if (cvData.has("personal_info")) {
                JSONObject pInfo = cvData.getJSONObject("personal_info");
                if (nameField != null) nameField.setText(pInfo.optString("name", ""));
                if (emailField != null) emailField.setText(pInfo.optString("email", ""));
                if (phoneField != null) phoneField.setText(pInfo.optString("phone", ""));
                if (addressField != null) addressField.setText(pInfo.optString("address", ""));
                if (githubField != null) githubField.setText(pInfo.optString("github", ""));
                if (linkedinField != null) linkedinField.setText(pInfo.optString("linkedin", ""));
            }
            if (skillsTextArea != null) {
                if (cvData.has("skills")) {
                    skillsTextArea.setText(cvData.getJSONObject("skills").toString(2));
                } else {
                    skillsTextArea.setText("{}");
                }
            }
            if (educationTextArea != null) {
                if (cvData.has("education")) {
                    educationTextArea.setText(cvData.getJSONArray("education").toString(2));
                } else {
                    educationTextArea.setText("[]");
                }
            }
            if (experienceTextArea != null) {
                if (cvData.has("experience")) {
                    experienceTextArea.setText(cvData.getJSONArray("experience").toString(2));
                } else {
                    experienceTextArea.setText("[]");
                }
            }
            if (genderComboBox != null) {
                String gender = cvData.optString("gender", "NONE");
                genderComboBox.setSelectedItem(gender);
            }
            if (typeComboBox != null) {
                String type = cvData.optString("type", "NONE");
                typeComboBox.setSelectedItem(type);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error populating edit fields: " + e.getMessage(), "Edit Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void switchToViewMode() {
        detailCardLayout.show(detailCardPanel, "VIEW_MODE");
        editButton.setVisible(true);
        saveButton.setVisible(false);
    }

    private void saveChanges() {
        if (currentCvId == null || currentCvData == null) {
            JOptionPane.showMessageDialog(this, "No CV loaded to save.", "Save Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JSONObject updatedData = new JSONObject();
        try {
            JSONObject updatedPersonalInfo = new JSONObject();
            if (nameField != null) updatedPersonalInfo.put("name", nameField.getText().trim());
            if (emailField != null) updatedPersonalInfo.put("email", emailField.getText().trim());
            if (phoneField != null) updatedPersonalInfo.put("phone", phoneField.getText().trim());
            if (addressField != null) updatedPersonalInfo.put("address", addressField.getText().trim());
            if (githubField != null) updatedPersonalInfo.put("github", githubField.getText().trim());
            if (linkedinField != null) updatedPersonalInfo.put("linkedin", linkedinField.getText().trim());
            updatedData.put("personal_info", updatedPersonalInfo);

            if (skillsTextArea != null && !skillsTextArea.getText().trim().isEmpty()) {
                updatedData.put("skills", new JSONObject(skillsTextArea.getText().trim()));
            } else {
                updatedData.put("skills", new JSONObject());
            }
            if (educationTextArea != null && !educationTextArea.getText().trim().isEmpty()) {
                updatedData.put("education", new JSONArray(educationTextArea.getText().trim()));
            } else {
                updatedData.put("education", new JSONArray());
            }
            if (experienceTextArea != null && !experienceTextArea.getText().trim().isEmpty()) {
                updatedData.put("experience", new JSONArray(experienceTextArea.getText().trim()));
            } else {
                updatedData.put("experience", new JSONArray());
            }

            if (genderComboBox != null) {
                updatedData.put("gender", Objects.toString(genderComboBox.getSelectedItem(), "NONE"));
            } else {
                updatedData.put("gender", "NONE");
            }
            if (typeComboBox != null) {
                updatedData.put("type", Objects.toString(typeComboBox.getSelectedItem(), "NONE"));
            } else {
                updatedData.put("type", "NONE");
            }

            for (String key : currentCvData.keySet()) {
                if (!updatedData.has(key)) {
                    updatedData.put(key, currentCvData.get(key));
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error collecting data for save: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        backButton.setEnabled(false);
        viewPdfButton.setEnabled(false);
        downloadPdfButton.setEnabled(false);
        editButton.setEnabled(false);
        saveButton.setEnabled(false);

        SwingWorker<HttpClientUtil.UpdateResult, Void> worker = new SwingWorker<HttpClientUtil.UpdateResult, Void>() {
            @Override
            protected HttpClientUtil.UpdateResult doInBackground() throws Exception {
                String jsonData = updatedData.toString();
                return HttpClientUtil.updateCVData(serverUrl, currentCvId, jsonData, token);
            }
            @Override
            protected void done() {
                try {
                    HttpClientUtil.UpdateResult result = get();
                    if (result.success) {
                        currentCvData = updatedData;
                        displayCVDetailsInViewMode(currentCvData);
                        switchToViewMode();
                        setStatus("CV updated successfully.", false, true);
                        JOptionPane.showMessageDialog(ViewCVPanel.this, result.message, "Save Successful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        setStatus("Failed to save CV: " + result.message, true, false);
                        JOptionPane.showMessageDialog(ViewCVPanel.this, "Failed to save CV: " + result.message, "Save Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    setStatus("Unexpected error during save: " + ex.getMessage(), true, false);
                    JOptionPane.showMessageDialog(ViewCVPanel.this, "Unexpected error during save: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                    backButton.setEnabled(true);
                    viewPdfButton.setEnabled(true);
                    downloadPdfButton.setEnabled(true);
                    editButton.setEnabled(true);
                    saveButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void deleteCv() {
        if (currentCvId == null || currentCvId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No CV loaded to delete.", "Delete Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirmation = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete the CV '" + currentCvId + "'? This action cannot be undone.",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }
        if (serverUrl == null || serverUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server URL is not configured.", "Delete Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingWorker<HttpClientUtil.DeleteResult, Void> worker = new SwingWorker<HttpClientUtil.DeleteResult, Void>() {
            @Override
            protected HttpClientUtil.DeleteResult doInBackground() throws Exception {
                System.out.println("Attempting to delete CV ID: " + currentCvId + " from " + serverUrl);
                return HttpClientUtil.deleteCV(serverUrl, currentCvId, token);
            }
            @Override
            protected void done() {
                try {
                    HttpClientUtil.DeleteResult result = get();
                    if (result != null && result.success) {
                        JOptionPane.showMessageDialog(
                                ViewCVPanel.this,
                                result.message,
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        currentCvId = null;
                        currentCvData = null;
                        parentApp.showView(CV_APP.SEARCH_VIEW);
                    } else {
                        String errorMessage = (result != null) ? result.errorMessage : "Unknown error during deletion.";
                        JOptionPane.showMessageDialog(
                                ViewCVPanel.this,
                                "Failed to delete CV: " + errorMessage,
                                "Delete Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                        System.err.println("Delete CV Error: " + errorMessage);
                    }
                } catch (Exception e) {
                    String errorMsg = "An unexpected error occurred while processing the delete response: " + e.getMessage();
                    JOptionPane.showMessageDialog(
                            ViewCVPanel.this,
                            errorMsg,
                            "Delete Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void displayCVDetailsInViewMode(JSONObject cvData) {
        try {
            StringBuilder personalInfoHtml = new StringBuilder();
            personalInfoHtml.append("<html><body style='font-family: Arial, sans-serif; font-size: 12px;'>");
            if (cvData.has("personal_info")) {
                JSONObject info = cvData.getJSONObject("personal_info");
                appendHtmlIfExists(personalInfoHtml, "Name", info, "name", false);
                appendHtmlIfExists(personalInfoHtml, "Email", info, "email", true);
                appendHtmlIfExists(personalInfoHtml, "Phone", info, "phone", false);
                appendHtmlIfExists(personalInfoHtml, "Address", info, "address", false);
                appendHtmlIfExists(personalInfoHtml, "Age", info, "age", false);
                appendHtmlIfExists(personalInfoHtml, "Nationality", info, "nationality", false);
                if (info.has("github") && !info.optString("github", "").isEmpty()) {
                    String github = info.getString("github");
                    personalInfoHtml.append("<b>GitHub:</b> <a href=\"").append(github).append("\">").append(github).append("</a><br>");
                }
                if (info.has("linkedin") && !info.optString("linkedin", "").isEmpty()) {
                    String linkedin = info.getString("linkedin");
                    personalInfoHtml.append("<b>LinkedIn:</b> <a href=\"").append(linkedin).append("\">").append(linkedin).append("</a><br>");
                }
            } else {
                personalInfoHtml.append("No personal information found.");
            }
            personalInfoHtml.append("</body></html>");
            personalInfoArea.setText(personalInfoHtml.toString());

            String gender = cvData.optString("gender", "NONE");
            String type = cvData.optString("type", "NONE");
            if (genderLabel != null) {
                genderLabel.setText("Gender: " + gender);
            }
            if (typeLabel != null) {
                typeLabel.setText("Type: " + type);
            }

            StringBuilder skills = new StringBuilder();
            fullSkillsContent = "";
            if (cvData.has("skills")) {
                JSONObject skillsObj = cvData.getJSONObject("skills");
                for (String skillType : skillsObj.keySet()) {
                    JSONArray skillList = skillsObj.getJSONArray(skillType);
                    skills.append(skillType.toUpperCase()).append(": ");
                    fullSkillsContent += skillType.toUpperCase() + ": ";
                    for (int i = 0; i < skillList.length(); i++) {
                        String skill = skillList.getString(i);
                        skills.append(skill);
                        fullSkillsContent += skill;
                        if (i < skillList.length() - 1) {
                            skills.append(", ");
                            fullSkillsContent += ", ";
                        }
                    }
                    skills.append("");
                    fullSkillsContent += "";
                }
            } else {
                skills.append("No skills found.");
                fullSkillsContent = "No skills found.";
            }
            skillsArea.setText(fullSkillsContent);

            StringBuilder education = new StringBuilder();
            fullEducationContent = "";
            if (cvData.has("education")) {
                JSONArray educationArray = cvData.getJSONArray("education");
                if (educationArray.length() == 0) {
                    education.append("No education entries found.");
                    fullEducationContent = "No education entries found.";
                } else {
                    for (int i = 0; i < educationArray.length(); i++) {
                        JSONObject edu = educationArray.getJSONObject(i);
                        String eduEntry = (i + 1) + ". ";
                        education.append(eduEntry);
                        fullEducationContent += eduEntry;
                        eduEntry = appendIfExistsReturn(education, "Degree", edu, "degree");
                        fullEducationContent += eduEntry;
                        eduEntry = appendIfExistsReturn(education, "Institution", edu, "institution");
                        fullEducationContent += eduEntry;
                        eduEntry = appendIfExistsReturn(education, "Dates", edu, "dates");
                        fullEducationContent += eduEntry;
                        eduEntry = appendIfExistsReturn(education, "Description", edu, "description");
                        fullEducationContent += eduEntry;
                        education.append("");
                        fullEducationContent += "";
                    }
                }
            } else {
                education.append("No education information found.");
                fullEducationContent = "No education information found.";
            }
            educationArea.setText(fullEducationContent);

            StringBuilder experience = new StringBuilder();
            fullExperienceContent = "";
            if (cvData.has("experience")) {
                JSONArray experienceArray = cvData.getJSONArray("experience");
                if (experienceArray.length() == 0) {
                    experience.append("No experience entries found.");
                    fullExperienceContent = "No experience entries found.";
                } else {
                    for (int i = 0; i < experienceArray.length(); i++) {
                        JSONObject exp = experienceArray.getJSONObject(i);
                        String expEntry = (i + 1) + ". ";
                        experience.append(expEntry);
                        fullExperienceContent += expEntry;
                        expEntry = appendIfExistsReturn(experience, "Position", exp, "position");
                        fullExperienceContent += expEntry;
                        expEntry = appendIfExistsReturn(experience, "Company", exp, "company");
                        fullExperienceContent += expEntry;
                        expEntry = appendIfExistsReturn(experience, "Dates", exp, "dates");
                        fullExperienceContent += expEntry;
                        expEntry = appendIfExistsReturn(experience, "Description", exp, "description");
                        fullExperienceContent += expEntry;
                        experience.append("");
                        fullExperienceContent += "";
                    }
                }
            } else {
                experience.append("No experience information found.");
                fullExperienceContent = "No experience information found.";
            }
            experienceArea.setText(fullExperienceContent);

            StringBuilder otherInfo = new StringBuilder();
            appendIfExists(otherInfo, "Filename", cvData, "filename");
            appendIfExists(otherInfo, "Upload Date", cvData, "upload_date");
            if (cvData.has("languages")) {
                JSONArray languagesArray = cvData.getJSONArray("languages");
                if (languagesArray.length() > 0) {
                    otherInfo.append(String.format("%-15s: ", "Languages"));
                    for (int i = 0; i < languagesArray.length(); i++) {
                        otherInfo.append(languagesArray.getString(i));
                        if (i < languagesArray.length() - 1) otherInfo.append(", ");
                    }
                    otherInfo.append("");
                }
            }
            if (cvData.has("certifications")) {
                JSONArray certificationsArray = cvData.getJSONArray("certifications");
                if (certificationsArray.length() > 0) {
                    otherInfo.append("CERTIFICATIONS:");
                    for (int i = 0; i < certificationsArray.length(); i++) {
                        JSONObject cert = certificationsArray.getJSONObject(i);
                        otherInfo.append((i + 1)).append(". ");
                        appendIfExists(otherInfo, "Name", cert, "name");
                        appendIfExists(otherInfo, "Issuer", cert, "issuer");
                        appendIfExists(otherInfo, "Date", cert, "date");
                        otherInfo.append("");
                    }
                }
            }
            if (cvData.has("projects")) {
                JSONArray projectsArray = cvData.getJSONArray("projects");
                if (projectsArray.length() > 0) {
                    otherInfo.append("PROJECTS:");
                    for (int i = 0; i < projectsArray.length(); i++) {
                        JSONObject project = projectsArray.getJSONObject(i);
                        otherInfo.append((i + 1)).append(". ");
                        appendIfExists(otherInfo, "Name", project, "name");
                        if (project.has("technologies")) {
                            JSONArray techArray = project.getJSONArray("technologies");
                            if (techArray.length() > 0) {
                                otherInfo.append(String.format("%-15s: ", "Technologies"));
                                for (int j = 0; j < techArray.length(); j++) {
                                    otherInfo.append(techArray.getString(j));
                                    if (j < techArray.length() - 1) otherInfo.append(", ");
                                }
                                otherInfo.append("");
                            }
                        }
                        otherInfo.append("");
                    }
                }
            }
            if (otherInfo.length() == 0) {
                otherInfo.append("No other information available.");
            }
            otherInfoArea.setText(otherInfo.toString());

            if (skillsViewDetailsButton != null) {
                skillsViewDetailsButton.setEnabled(!fullSkillsContent.isEmpty());
            }
            if (educationViewDetailsButton != null) {
                educationViewDetailsButton.setEnabled(!fullEducationContent.isEmpty());
            }
            if (experienceViewDetailsButton != null) {
                experienceViewDetailsButton.setEnabled(!fullExperienceContent.isEmpty());
            }
        } catch (Exception e) {
            setStatus("Error displaying CV details: " + e.getMessage(), true, true);
            e.printStackTrace();
        }
    }

    private void appendHtmlIfExists(StringBuilder sb, String label, JSONObject obj, String key, boolean makeEmailLink) {
        if (obj.has(key)) {
            String value = obj.get(key).toString();
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                sb.append("<b>").append(label).append(":</b> ");
                if (makeEmailLink && value.contains("@")) {
                    sb.append("<a href=\"mailto:").append(value).append("\">").append(value).append("</a>");
                } else {
                    sb.append(value);
                }
                sb.append("<br>");
            }
        }
    }

    private void setStatus(String message, boolean isError, boolean isViewModeStatus) {
        if (isViewModeStatus) {
            if (isError || (personalInfoArea.getText().isEmpty() && skillsArea.getText().isEmpty() &&
                    educationArea.getText().isEmpty() && experienceArea.getText().isEmpty() &&
                    otherInfoArea.getText().isEmpty())) {
                otherInfoArea.setText(message);
            }
        } else {
            if (isError) {
                System.err.println("Edit Mode Status (Error): " + message);
            } else {
                System.out.println("Edit Mode Status: " + message);
            }
        }
    }

    private String appendIfExistsReturn(StringBuilder sb, String label, JSONObject obj, String key) {
        if (obj.has(key)) {
            String value = obj.get(key).toString();
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                String entry = String.format("%-15s: %s%n", label, value);
                sb.append(entry);
                return entry;
            }
        }
        return "";
    }

    private void appendIfExists(StringBuilder sb, String label, JSONObject obj, String key) {
        if (obj.has(key)) {
            String value = obj.get(key).toString();
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                sb.append(String.format("%-15s: %s%n", label, value));
            }
        }
    }

    private void showDetailsDialog(String title, String content, boolean isEditable) {
         TextDetailDialog dialog = new TextDetailDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                title + " Details",
                content,
                isEditable
        );
        dialog.setVisible(true);
    }

    private class ViewPdfActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentCvId == null) return;
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            viewPdfButton.setEnabled(false);
            downloadPdfButton.setEnabled(false);
            editButton.setEnabled(false);
            saveButton.setEnabled(false);
            SwingWorker<DownloadResult, Void> worker = new SwingWorker<DownloadResult, Void>() {
                @Override
                protected DownloadResult doInBackground() throws Exception {
                    return DownloadResult.downloadPdfData(serverUrl, currentCvId);
                }
                @Override
                protected void done() {
                    try {
                        DownloadResult result = get();
                        if (result.success && result.fileData != null) {
                            try {
                                Path tempFile = Files.createTempFile("cv_view_", ".pdf");
                                try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                                    fos.write(result.fileData);
                                }
                                if (Desktop.isDesktopSupported()) {
                                    Desktop desktop = Desktop.getDesktop();
                                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                                        desktop.open(tempFile.toFile());
                                    } else {
                                        JOptionPane.showMessageDialog(ViewCVPanel.this,
                                                "Opening files not supported. Saved to: " + tempFile.toAbsolutePath(),
                                                "View PDF", JOptionPane.INFORMATION_MESSAGE);
                                    }
                                } else {
                                    JOptionPane.showMessageDialog(ViewCVPanel.this,
                                            "Desktop actions not supported. Saved to: " + tempFile.toAbsolutePath(),
                                            "View PDF", JOptionPane.INFORMATION_MESSAGE);
                                }
                            } catch (IOException ioEx) {
                                JOptionPane.showMessageDialog(ViewCVPanel.this,
                                        "Failed to save or open PDF locally: " + ioEx.getMessage(),
                                        "View PDF Error", JOptionPane.ERROR_MESSAGE);
                                ioEx.printStackTrace();
                            }
                        } else {
                            JOptionPane.showMessageDialog(ViewCVPanel.this,
                                    "Failed to download PDF for viewing: " + (result.message != null ? result.message : "Unknown error"),
                                    "View PDF Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ViewCVPanel.this,
                                "Unexpected error during PDF view: " + ex.getMessage(),
                                "View PDF Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                        viewPdfButton.setEnabled(true);
                        downloadPdfButton.setEnabled(true);
                        editButton.setEnabled(true);
                        saveButton.setEnabled(isViewMode() ? false : true);
                    }
                }
                private boolean isViewMode() {
                    return editButton.isVisible();
                }
            };
            worker.execute();
        }
    }

    private class DownloadPdfActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentCvId == null) return;
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save PDF As");
            fileChooser.setSelectedFile(new File((currentCvId != null ? currentCvId : "cv") + ".pdf"));
            int userSelection = fileChooser.showSaveDialog(ViewCVPanel.this);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String filePath = fileToSave.getAbsolutePath();
                if (!filePath.toLowerCase().endsWith(".pdf")) {
                    filePath += ".pdf";
                    fileToSave = new File(filePath);
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                viewPdfButton.setEnabled(false);
                downloadPdfButton.setEnabled(false);
                editButton.setEnabled(false);
                saveButton.setEnabled(false);
                String finalFilePath = filePath;
                SwingWorker<DownloadResult, Void> worker = new SwingWorker<DownloadResult, Void>() {
                    @Override
                    protected DownloadResult doInBackground() throws Exception {
                        return DownloadResult.downloadPdfToFile(serverUrl, currentCvId, finalFilePath);
                    }
                    @Override
                    protected void done() {
                        try {
                            DownloadResult result = get();
                            if (result.success) {
                                JOptionPane.showMessageDialog(ViewCVPanel.this,
                                        result.message,
                                        "Download Successful", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(ViewCVPanel.this,
                                        "Failed to download PDF: " + (result.message != null ? result.message : "Unknown error"),
                                        "Download Error", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(ViewCVPanel.this,
                                    "Unexpected error during PDF download: " + ex.getMessage(),
                                    "Download Error", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                        } finally {
                            setCursor(Cursor.getDefaultCursor());
                            viewPdfButton.setEnabled(true);
                            downloadPdfButton.setEnabled(true);
                            editButton.setEnabled(true);
                            saveButton.setEnabled(isViewMode() ? false : true);
                        }
                    }
                    private boolean isViewMode() {
                        return editButton.isVisible();
                    }
                };
                worker.execute();
            }
        }
    }

    public static class TextDetailDialog extends JDialog {
        private JTextArea textArea;
        private boolean isEditable;
        private boolean confirmed = false;
        public TextDetailDialog(Frame parent, String title, String content, boolean editable) {
            super(parent, title, true);
            this.isEditable = editable;
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());
            textArea = new JTextArea(25, 80);
            textArea.setText(content);
            textArea.setEditable(editable);
            textArea.setFont(textArea.getFont().deriveFont(Font.PLAIN, 12));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            add(scrollPane, BorderLayout.CENTER);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    confirmed = true;
                    dispose();
                }
            });
            if (editable) {
                 buttonPanel.add(okButton);
            }
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(parent);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int maxWidth = (int) (screenSize.width * 0.9);
            int maxHeight = (int) (screenSize.height * 0.9);
            if (getWidth() > maxWidth || getHeight() > maxHeight) {
                setSize(Math.min(getWidth(), maxWidth), Math.min(getHeight(), maxHeight));
                 setLocationRelativeTo(parent);
            }
        }
        public String getModifiedText() {
             if (isEditable && confirmed) {
                 return textArea.getText();
             }
             return null;
        }
        public boolean isConfirmed() {
            return confirmed;
        }
    }
}