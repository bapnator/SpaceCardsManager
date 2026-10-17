package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class LoginDialog extends JDialog {
    private final DatabaseService db = DatabaseService.getInstance();
    private Usuario selectedUser = null;
    private final JComboBox<Object> cmbUsuarios = new JComboBox<>();

    public LoginDialog(Frame parent) {
        super(parent, "Acceso al Sistema - Space Cards Manager", true);
        setSize(520, 380);
        setResizable(false);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(UITheme.COLOR_BG_SIDEBAR);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel lblLogo = new JLabel("⚡ SPACE CARDS MANAGER");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Control de Acceso y Sesión de Usuario");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(UITheme.COLOR_ACCENT);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblLogo);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(lblSub);
        headerPanel.add(Box.createVerticalStrut(16));
        root.add(headerPanel, BorderLayout.NORTH);

        // Center: Form and Status Banner
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        boolean hasAdmin = db.hasActiveAdministrator();
        List<Usuario> activeUsers = db.getUsuarios();

        // Banner informativo
        JPanel bannerCard = UITheme.createCardPanel();
        bannerCard.setLayout(new BorderLayout(8, 8));
        bannerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));

        JLabel lblBanner = new JLabel();
        lblBanner.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        if (!hasAdmin) {
            lblBanner.setText("<html><b style='color:#f59e0b;'>⚠️ Modo Inicial / Sin Administrador:</b><br>" +
                    "El usuario puente <b>Ash Ketchum</b> está <b>habilitado</b> temporalmente para que puedas registrar los administradores del sistema.</html>");
            bannerCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(245, 158, 11), 1, true),
                    new EmptyBorder(8, 12, 8, 12)
            ));
        } else {
            lblBanner.setText("<html><b style='color:#10b981;'>🛡️ Sistema en Producción:</b><br>" +
                    "Ya existen Administradores registrados. El usuario puente <b>Ash está bloqueado</b>.</html>");
            bannerCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(16, 185, 129), 1, true),
                    new EmptyBorder(8, 12, 8, 12)
            ));
        }
        bannerCard.add(lblBanner, BorderLayout.CENTER);
        centerPanel.add(bannerCard);
        centerPanel.add(Box.createVerticalStrut(18));

        // Combo de selección de usuario
        JLabel lblPrompt = new JLabel("Selecciona el usuario para iniciar sesión:");
        lblPrompt.setFont(UITheme.FONT_BOLD);
        lblPrompt.setForeground(Color.WHITE);
        lblPrompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(lblPrompt);
        centerPanel.add(Box.createVerticalStrut(8));

        // Poblar combo
        cmbUsuarios.removeAllItems();

        if (!hasAdmin) {
            // Ash puente habilitado porque NO hay administradores
            cmbUsuarios.addItem(DatabaseService.ASH_BRIDGE_USER);
        }

        for (Usuario u : activeUsers) {
            if (u.isActivo()) {
                cmbUsuarios.addItem(u);
            }
        }

        if (hasAdmin && cmbUsuarios.getItemCount() == 0) {
            // Caso raro: hay admin pero inactivo
            cmbUsuarios.addItem("No hay usuarios activos disponibles");
        }

        cmbUsuarios.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cmbUsuarios.setFont(UITheme.FONT_REGULAR);
        centerPanel.add(cmbUsuarios);

        root.add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        JButton btnSalir = UITheme.createSecondaryButton("Salir");
        btnSalir.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Deseas cerrar la aplicación?", "Salir", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        JButton btnEntrar = UITheme.createPrimaryButton("🚀 Iniciar Sesión");
        btnEntrar.addActionListener(e -> {
            Object item = cmbUsuarios.getSelectedItem();
            if (item instanceof Usuario) {
                selectedUser = (Usuario) item;
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Por favor selecciona un usuario válido.", "Aviso", JOptionPane.WARNING_MESSAGE);
            }
        });

        btnPanel.add(btnSalir);
        btnPanel.add(btnEntrar);
        root.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    public Usuario getSelectedUser() {
        return selectedUser;
    }
}
