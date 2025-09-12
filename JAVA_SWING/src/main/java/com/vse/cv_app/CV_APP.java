package com.vse.cv_app;

import com.vse.cv_app.panels.*;
import com.vse.cv_app.panels.view.RAW_VIEW;
import com.vse.cv_app.utils.JWTTokenManager;

import javax.swing.*;
import java.awt.*;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.awt.desktop.QuitEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

 public class CV_APP {

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private UploadPanel uploadPanel;
    private SearchPanel searchPanel;
    private ViewCVPanel viewCVPanel;
    private RAW_VIEW rawTextPanel;
    private AuditLogPanel auditLogPanel;

    public String jwtToken = null;
    private JMenuItem loginMenuItem;
    private JMenuItem registerMenuItem;
    private JMenuItem logoutMenuItem;
    private JMenuItem auditLogMenuItem;
    private JWTTokenManager jwtTokenManager;

    public static final String UPLOAD_VIEW = "UploadView";
    public static final String SEARCH_VIEW = "SearchView";
    public static final String VIEW_CV_VIEW = "ViewCVView";
    public static final String RAW_TEXT_VIEW = "RAW_TEXT_VIEW";
    public static final String AUDIT_LOG_VIEW = "AuditLogView";

    private static final String SERVER_BASE_URL = "http://13.250.35.49:8000";

    public CV_APP() {
        initialize();
    }

    private void initialize() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            configureMacOSIntegration();
        }

        frame = new JFrame();
        frame.setTitle("CV Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);

        JMenuBar menuBar = new JMenuBar();
        JMenu userMenu = new JMenu("User");
        JMenu otherMenu = new JMenu("Other");

        loginMenuItem = new JMenuItem("Login");
        registerMenuItem = new JMenuItem("Register");
        logoutMenuItem = new JMenuItem("Logout");
        logoutMenuItem.setEnabled(false);
        auditLogMenuItem = new JMenuItem("View Audit Logs");
        jwtTokenManager = new JWTTokenManager();

        auditLogMenuItem.addActionListener(e -> showAuditLogView());
        userMenu.addSeparator();
        otherMenu.addSeparator();

        loginMenuItem.addActionListener(e -> showLoginDialog());
        registerMenuItem.addActionListener(e -> showRegisterDialog());
        logoutMenuItem.addActionListener(e -> logout());

        otherMenu.add(auditLogMenuItem);

        userMenu.add(loginMenuItem);
        userMenu.add(registerMenuItem);
        userMenu.addSeparator();
        userMenu.add(logoutMenuItem);

        menuBar.add(userMenu);
        menuBar.add(otherMenu);

        frame.setJMenuBar(menuBar);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        uploadPanel = new UploadPanel(this, SERVER_BASE_URL);
        searchPanel = new SearchPanel(this, SERVER_BASE_URL);
        viewCVPanel = new ViewCVPanel(this, SERVER_BASE_URL);
        rawTextPanel = new RAW_VIEW(this, SERVER_BASE_URL);

        mainPanel.add(uploadPanel, UPLOAD_VIEW);
        mainPanel.add(searchPanel, SEARCH_VIEW);
        mainPanel.add(viewCVPanel, VIEW_CV_VIEW);
        mainPanel.add(rawTextPanel, RAW_TEXT_VIEW);
        auditLogPanel = new AuditLogPanel(this, SERVER_BASE_URL);
        mainPanel.add(auditLogPanel, AUDIT_LOG_VIEW);

        frame.getContentPane().add(mainPanel);
        frame.setVisible(true);

        showView(SEARCH_VIEW);
        String retrievedToken = jwtTokenManager.getToken("access_token");
        if(retrievedToken != null) setJwtToken(retrievedToken);
        else updateAuthUI();
    }

    private void configureMacOSIntegration() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "CV Application");

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            desktop.setAboutHandler(e -> showAboutDialog());

            desktop.setQuitHandler(new QuitHandler() {
                @Override
                public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
                    int result = JOptionPane.showConfirmDialog(frame,
                            "Are you sure you want to quit?",
                            "Quit CV Application",
                            JOptionPane.YES_NO_OPTION);

                    if (result == JOptionPane.YES_OPTION) {
                        response.performQuit();
                    } else {
                        response.cancelQuit();
                    }
                }
            });
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(frame,
                "CV Application\nVersion 1.0\n© 2023 VSE",
                "About CV Application",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void setJwtToken(String token) {
        String expiresAt = LocalDateTime.now().plusDays(30)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.jwtToken = token;
        jwtTokenManager.saveNewToken("access_token", token, expiresAt);
        updateAuthUI();
        if (uploadPanel != null) {
            uploadPanel.setToken(token);
        }
        if (viewCVPanel != null) {
            viewCVPanel.setToken(token);
        }
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public boolean isLoggedIn() {
        return jwtToken != null && !jwtToken.isEmpty();
    }

    private void updateAuthUI() {
        boolean loggedIn = isLoggedIn();
        loginMenuItem.setEnabled(!loggedIn);
        registerMenuItem.setEnabled(!loggedIn);
        logoutMenuItem.setEnabled(loggedIn);
    }

     private void updateAuthUI(boolean loggedIn) {
         loginMenuItem.setEnabled(!loggedIn);
         registerMenuItem.setEnabled(!loggedIn);
         logoutMenuItem.setEnabled(loggedIn);
     }

    private void showLoginDialog() {
        LoginDialog loginDialog = new LoginDialog(frame, SERVER_BASE_URL, this);
        loginDialog.setVisible(true);
    }

     public void showAuditLogView() {
         if (auditLogPanel != null) {
             auditLogPanel.refreshLogs();
         }
         showView(AUDIT_LOG_VIEW);
     }

    private void showRegisterDialog() {
        RegisterDialog registerDialog = new RegisterDialog(frame, SERVER_BASE_URL, this);
        registerDialog.setVisible(true);
    }

    private void logout() {
        int result = JOptionPane.showConfirmDialog(frame,
                "Are you sure you want to log out?",
                "Logout",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            this.jwtToken = null;
            updateAuthUI();
            jwtTokenManager.clearAllTokens();
            JOptionPane.showMessageDialog(frame, "You have been logged out.", "Logout", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void showRawTextView(String cvId, ViewCVPanel viewCVPanel) {
        if (rawTextPanel != null && cvId != null && !cvId.isEmpty()) {
            rawTextPanel.loadRawText(cvId);
            showView(RAW_TEXT_VIEW);
        } else if (cvId == null || cvId.isEmpty()) {
            JOptionPane.showMessageDialog(viewCVPanel, "No CV selected to view raw text.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void showView(String viewName) {
        cardLayout.show(mainPanel, viewName);
        if (SEARCH_VIEW.equals(viewName)) {
            searchPanel.refresh();
        }
    }

    public void showCVDetails(String cvId) {
        viewCVPanel.loadCV(cvId);
        showView(VIEW_CV_VIEW);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new CV_APP();
        });
    }
}