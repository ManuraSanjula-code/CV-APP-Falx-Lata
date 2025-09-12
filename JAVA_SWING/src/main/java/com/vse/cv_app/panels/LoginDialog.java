package com.vse.cv_app.panels;

import com.vse.cv_app.CV_APP;
import com.vse.cv_app.utils.HttpClientUtil;

import javax.swing.*;
import java.awt.*;


public class LoginDialog extends JDialog {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton cancelButton;
    private JLabel statusLabel;
    private CV_APP parentApp;
    private String serverUrl;

    public LoginDialog(Frame parent, String serverUrl, CV_APP app) {
        super(parent, "Login", true);
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
        loginButton = new JButton("Login");
        cancelButton = new JButton("Cancel");
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
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

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        centerPanel.add(statusLabel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(loginButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addEventListeners() {
        loginButton.addActionListener(e -> performLogin());
        cancelButton.addActionListener(e -> dispose());
        passwordField.addActionListener(e -> performLogin()); // Enter key
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        statusLabel.setText("Logging in...");
        loginButton.setEnabled(false);
        cancelButton.setEnabled(false);

        SwingWorker<HttpClientUtil.LoginResult, Void> worker = new SwingWorker<HttpClientUtil.LoginResult, Void>() {
            @Override
            protected HttpClientUtil.LoginResult doInBackground() throws Exception {
                return HttpClientUtil.login(serverUrl, username, password);
            }

            @Override
            protected void done() {
                try {
                    HttpClientUtil.LoginResult result = get();
                    if (result.success) {
                        statusLabel.setText("Login successful!");
                        statusLabel.setForeground(Color.GREEN.darker());
                        parentApp.setJwtToken(result.token);
                        dispose();
                    } else {
                        statusLabel.setText("Login failed: " + result.errorMessage);
                        statusLabel.setForeground(Color.RED);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Login error: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    e.printStackTrace();
                } finally {
                    loginButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}