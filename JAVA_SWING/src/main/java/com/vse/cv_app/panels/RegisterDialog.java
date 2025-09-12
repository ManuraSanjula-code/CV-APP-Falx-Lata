package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class RegisterDialog extends JDialog {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField nameField;
    private JButton registerButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    private CV_APP parentApp;
    private String serverUrl;

    public RegisterDialog(Frame parent, String serverUrl, CV_APP app) {
        super(parent, "Register", true);
        this.parentApp = app;
        this.serverUrl = serverUrl;

        initializeComponents();
        layoutComponents();
        addEventListeners();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        nameField = new JTextField(15); // Add name field
        registerButton = new JButton("Register");
        cancelButton = new JButton("Cancel");
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.BLUE);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.LINE_END;
        centerPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        centerPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.LINE_END;
        centerPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        centerPanel.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.LINE_END;
        centerPanel.add(new JLabel("Name (Optional):"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START;
        centerPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        centerPanel.add(statusLabel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addEventListeners() {
        registerButton.addActionListener(e -> performRegistration());
        cancelButton.addActionListener(e -> dispose());
    }

    private void performRegistration() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String name = nameField.getText().trim();
        String role = "user";
        int priority = 5;

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password are required.");
            statusLabel.setForeground(Color.RED);
            return;
        }
        // Optional: Validate name length etc.

        statusLabel.setText("Creating account...");
        statusLabel.setForeground(Color.BLUE);
        registerButton.setEnabled(false);
        cancelButton.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    String registerEndpoint = serverUrl + "/register";

                    JSONObject jsonPayload = new JSONObject();
                    jsonPayload.put("username", username);
                    jsonPayload.put("password", password);
                    jsonPayload.put("name", name.isEmpty() ? username : name);
                    jsonPayload.put("role", role);
                    jsonPayload.put("priority", priority);
                    String jsonString = jsonPayload.toString();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(registerEndpoint))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    String responseBody = response.body();

                    if (statusCode == 201) {
                        return "Account created successfully. You can now log in.";
                    } else {
                        String errorMsg = "Registration failed (HTTP " + statusCode + ")";
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("message")) {
                                errorMsg = errorJson.getString("message");
                            }
                        } catch (Exception parseEx) {
                            errorMsg += ": " + responseBody;
                        }
                        throw new Exception(errorMsg);
                    }
                } catch (Exception e) {
                    // Handle network interruptions if needed
                    throw new Exception("Network error during registration: " + e.getMessage(), e);
                }
            }

            @Override
            protected void done() {
                try {
                    String message = get();
                    statusLabel.setText(message);
                    statusLabel.setForeground(Color.GREEN.darker());
                    usernameField.setText("");
                    passwordField.setText("");
                    nameField.setText("");
                } catch (Exception e) {
                    statusLabel.setText("Registration failed: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                } finally {
                    registerButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
