package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SidebarPanel extends JPanel {
    private final Consumer<String> onNavigate;
    private final Runnable onLogout;
    private final List<JButton> navButtons = new ArrayList<>();
    private final Map<String, JButton> navMap = new HashMap<>();
    private final JLabel lblUser = new JLabel();
    private final JLabel lblRole = new JLabel();
    private final JLabel lblDbStatus;

    public SidebarPanel(Consumer<String> onNavigate, Runnable onLogout) {
        this.onNavigate = onNavigate;
        this.onLogout = onLogout;
        this.setLayout(new BorderLayout());
        this.setPreferredSize(new Dimension(240, 720));
        this.setBackground(UITheme.COLOR_BG_SIDEBAR);
        this.setBorder(new EmptyBorder(16, 12, 16, 12));

        // Top: Logo & Title
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel lblLogo = new JLabel("SPACE CARDS");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("Gestión de tienda especializada");
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
        navContainer.setLayout(new GridLayout(0, 1, 0, 6));

        addNavItem(navContainer, "Resumen", "DASHBOARD", true);
        addNavItem(navContainer, "Punto de Venta", "POS", false);
        addNavItem(navContainer, "Productos", "INVENTARIO", false);
        addNavItem(navContainer, "Configuración", "CONFIGURACION", false);
        addNavItem(navContainer, "Clientes", "CLIENTES", false);
        addNavItem(navContainer, "Torneos", "TORNEOS", false);
        addNavItem(navContainer, "Personal", "USUARIOS", false);
        addNavItem(navContainer, "Gestión de información", "DATABASE", false);

        this.add(navContainer, BorderLayout.CENTER);

        // Bottom: Status, Current active user and Logout
        JPanel footerPanel = new JPanel();
        footerPanel.setOpaque(false);
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        footerPanel.add(sep);
        footerPanel.add(Box.createVerticalStrut(10));

        lblUser.setFont(UITheme.FONT_BOLD);
        lblUser.setForeground(Color.WHITE);
        lblUser.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblRole.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRole.setForeground(UITheme.COLOR_TEXT_MUTED);
        lblRole.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblDbStatus = new JLabel("" + DatabaseService.getInstance().getConnectionStatus());
        lblDbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblDbStatus.setForeground(UITheme.COLOR_SUCCESS);
        lblDbStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnLogout = UITheme.createSecondaryButton("Cambiar Usuario");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLogout.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnLogout.addActionListener(e -> {
            if (this.onLogout != null) {
                this.onLogout.run();
            }
        });

        JButton btnChangePassword = UITheme.createSecondaryButton("Cambiar contraseña");
        btnChangePassword.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnChangePassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnChangePassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnChangePassword.addActionListener(e -> showChangePasswordDialog());

        footerPanel.add(lblUser);
        footerPanel.add(Box.createVerticalStrut(2));
        footerPanel.add(lblRole);
        footerPanel.add(Box.createVerticalStrut(6));
        footerPanel.add(lblDbStatus);
        footerPanel.add(Box.createVerticalStrut(10));
        footerPanel.add(btnChangePassword);
        footerPanel.add(Box.createVerticalStrut(6));
        footerPanel.add(btnLogout);

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
            if (!SessionManager.getInstance().canAccessCard(cardName)) {
                if (SessionManager.getInstance().isAshBridgeUser()) {
                    JOptionPane.showMessageDialog(this,
                            "Acceso Restringido\n\n" +
                            "'Ash Ketchum' es un usuario puente temporal.\n" +
                            "Su único permiso es acceder a 'Personal / Staff' para registrar los Administradores y empleados iniciales del sistema.",
                            "Permisos de Usuario Puente", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Acceso Denegado\n\n" +
                            "Tu rol actual (" + SessionManager.getInstance().getCurrentUser().getRol() + ") no tiene permisos para acceder a esta sección.",
                            "Acceso Restringido", JOptionPane.WARNING_MESSAGE);
                }
                return;
            }

            for (JButton b : navButtons) {
                setButtonActive(b, false);
            }
            setButtonActive(btn, true);
            onNavigate.accept(cardName);
        });

        navButtons.add(btn);
        navMap.put(cardName, btn);
        container.add(btn);
    }

    public void setActiveNav(String cardName) {
        for (JButton b : navButtons) {
            setButtonActive(b, false);
        }
        JButton target = navMap.get(cardName);
        if (target != null) {
            setButtonActive(target, true);
        }
    }

    public void setCurrentUser(Usuario user) {
        if (user != null) {
            lblUser.setText(user.getNombreCompleto());
            if (user.getIdUsuario() == -1) {
                lblRole.setText("Rol: Usuario Puente (Config)");
                lblRole.setForeground(new Color(245, 158, 11));
            } else {
                lblRole.setText("Rol: " + user.getRol());
                lblRole.setForeground(UITheme.COLOR_TEXT_MUTED);
            }
        }
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

    private void showChangePasswordDialog() {
        Usuario user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "No hay ningún usuario activo en la sesión.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Cambiar contraseña", true);
        dialog.setSize(420, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPasswordField txtCurrent = new JPasswordField();
        JPasswordField txtNew = new JPasswordField();
        JPasswordField txtConfirm = new JPasswordField();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        form.add(new JLabel("Contraseña actual:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        form.add(txtCurrent, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Nueva contraseña:"), gbc);
        gbc.gridx = 1;
        form.add(txtNew, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Confirmar:"), gbc);
        gbc.gridx = 1;
        form.add(txtConfirm, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar");
        btnGuardar.addActionListener(e -> {
            String current = new String(txtCurrent.getPassword()).trim();
            String nueva = new String(txtNew.getPassword()).trim();
            String confirmacion = new String(txtConfirm.getPassword()).trim();

            if (current.isEmpty() || nueva.isEmpty() || confirmacion.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Debes llenar todos los campos.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!DatabaseService.getInstance().authenticateUsuario(user, current)) {
                JOptionPane.showMessageDialog(dialog, "La contraseña actual es incorrecta.", "Acceso denegado", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!nueva.equals(confirmacion)) {
                JOptionPane.showMessageDialog(dialog, "La nueva contraseña y la confirmación no coinciden.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String warning = Usuario.getPasswordStrengthWarning(nueva);
            if (!warning.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        warning + "\n\nSe permitirá continuar, pero es recomendable reforzarla.",
                        "Aviso de seguridad",
                        JOptionPane.WARNING_MESSAGE);
            }

            if (DatabaseService.getInstance().setPasswordForUser(user.getIdUsuario(), nueva)) {
                user.setPassword(nueva);
                JOptionPane.showMessageDialog(dialog, "Contraseña actualizada correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "No se pudo cambiar la contraseña.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnCancel = UITheme.createSecondaryButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnGuardar);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    public void updateDbStatus(String status, boolean isConnected) {
        lblDbStatus.setText((isConnected ? "V" : "A ") + status);
        lblDbStatus.setForeground(isConnected ? UITheme.COLOR_SUCCESS : UITheme.COLOR_ACCENT);
    }
}
