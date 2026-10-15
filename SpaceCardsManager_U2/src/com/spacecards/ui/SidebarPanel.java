package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SidebarPanel extends JPanel {
    private final Consumer<String> onNavigate;
    private final List<JButton> navButtons = new ArrayList<>();
    private final JLabel lblDbStatus;

    public SidebarPanel(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        this.setLayout(new BorderLayout());
        this.setPreferredSize(new Dimension(240, 720));
        this.setBackground(UITheme.COLOR_BG_SIDEBAR);
        this.setBorder(new EmptyBorder(16, 12, 16, 12));

        // Top: Logo & Title
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel lblLogo = new JLabel("⚡ SPACE CARDS");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("TCG Business Manager");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(UITheme.COLOR_ACCENT);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerPanel.add(lblLogo);
        headerPanel.add(Box.createVerticalStrut(2));
        headerPanel.add(lblSub);
        headerPanel.add(Box.createVerticalStrut(20));

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Navigation buttons
        JPanel navContainer = new JPanel();
        navContainer.setOpaque(false);
        navContainer.setLayout(new GridLayout(8, 1, 0, 6));

        addNavItem(navContainer, "📊  Dashboard", "DASHBOARD", true);
        addNavItem(navContainer, "🛒  Punto de Venta", "POS", false);
        addNavItem(navContainer, "🎴  Inventario TCG", "INVENTARIO", false);
        addNavItem(navContainer, "👥  Clientes", "CLIENTES", false);
        addNavItem(navContainer, "📦  Preventas", "PREVENTAS", false);
        addNavItem(navContainer, "🏆  Torneos", "TORNEOS", false);
        addNavItem(navContainer, "🛡️  Personal / Staff", "USUARIOS", false);
        addNavItem(navContainer, "⚙️  Base de Datos", "DATABASE", false);

        this.add(navContainer, BorderLayout.CENTER);

        // Bottom: Status and current active user
        JPanel footerPanel = new JPanel();
        footerPanel.setOpaque(false);
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        footerPanel.add(sep);
        footerPanel.add(Box.createVerticalStrut(10));

        JLabel lblUser = new JLabel("👤 Ash Ketchum");
        lblUser.setFont(UITheme.FONT_BOLD);
        lblUser.setForeground(Color.WHITE);
        lblUser.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblRole = new JLabel("Rol: Administrador");
        lblRole.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRole.setForeground(UITheme.COLOR_TEXT_MUTED);
        lblRole.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblDbStatus = new JLabel("🟢 " + DatabaseService.getInstance().getConnectionStatus());
        lblDbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblDbStatus.setForeground(UITheme.COLOR_SUCCESS);
        lblDbStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        footerPanel.add(lblUser);
        footerPanel.add(Box.createVerticalStrut(2));
        footerPanel.add(lblRole);
        footerPanel.add(Box.createVerticalStrut(6));
        footerPanel.add(lblDbStatus);

        this.add(footerPanel, BorderLayout.SOUTH);
    }

    private void addNavItem(JPanel container, String text, String cardName, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(UITheme.FONT_REGULAR);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("FlatLaf.style", "arc: 8; borderWidth: 0");

        setButtonActive(btn, active);

        btn.addActionListener(e -> {
            for (JButton b : navButtons) {
                setButtonActive(b, false);
            }
            setButtonActive(btn, true);
            onNavigate.accept(cardName);
        });

        navButtons.add(btn);
        container.add(btn);
    }

    private void setButtonActive(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(UITheme.COLOR_PRIMARY);
            btn.setForeground(Color.WHITE);
            btn.setFont(UITheme.FONT_BOLD);
        } else {
            btn.setBackground(UITheme.COLOR_BG_SIDEBAR);
            btn.setForeground(new Color(203, 213, 225));
            btn.setFont(UITheme.FONT_REGULAR);
        }
    }

    public void updateDbStatus(String status, boolean isConnected) {
        lblDbStatus.setText((isConnected ? "🟢 " : "🟡 ") + status);
        lblDbStatus.setForeground(isConnected ? UITheme.COLOR_SUCCESS : UITheme.COLOR_ACCENT);
    }
}
