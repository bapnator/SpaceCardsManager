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
        super(parent, "Space Cards Manager", true);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        boolean hasAdmin = db.hasActiveAdministrator();
        if (!hasAdmin) {
            setTitle("Configuración Inicial - Space Cards Manager");
            setSize(560, 480);
            initWizardUI();
        } else {
            setTitle("Iniciar Sesión - Space Cards Manager");
            setSize(460, 320);
            initStandardLoginUI();
        }
        setLocationRelativeTo(parent);
    }

    /**
     * Pantalla de bienvenida e introducción cuando aún NO hay administradores.
     * Guía paso a paso al usuario para poner en marcha la tienda.
     */
    private void initWizardUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(UITheme.COLOR_BG_SIDEBAR);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel lblBadge = new JLabel("✨ ASISTENTE DE PRIMER INICIO");
        lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblBadge.setForeground(UITheme.COLOR_ACCENT);
        lblBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel("Bienvenido a Space Cards Manager");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Sistema de Gestión Integral para Tiendas TCG");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblBadge);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(lblTitle);
        headerPanel.add(Box.createVerticalStrut(2));
        headerPanel.add(lblSub);
        root.add(headerPanel, BorderLayout.NORTH);

        // Center: Pasos iniciales informativos y usuario puente
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JPanel guideCard = UITheme.createCardPanel();
        guideCard.setLayout(new BoxLayout(guideCard, BoxLayout.Y_AXIS));
        guideCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel lblStepsTitle = new JLabel("📋 Primeros pasos para configurar tu tienda:");
        lblStepsTitle.setFont(UITheme.FONT_BOLD);
        lblStepsTitle.setForeground(Color.WHITE);
        guideCard.add(lblStepsTitle);
        guideCard.add(Box.createVerticalStrut(10));

        String[] steps = {
            "<b>1. Acceso de configuración:</b> Ingresa como <b>Ash Ketchum (Usuario Puente)</b>.",
            "<b>2. Crea tu Administrador:</b> En el módulo de <b>Personal</b> registra tu cuenta oficial.",
            "<b>3. Alta de empleados:</b> Registra a tus cajeros (Vendedor) y jueces (Organizador).",
            "<b>4. Comienza a operar:</b> Ash se deshabilitará automáticamente y la tienda quedará protegida."
        };

        for (String step : steps) {
            JLabel lblStep = new JLabel("<html><span style='color:#94a3b8;'>• </span>" + step + "</html>");
            lblStep.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblStep.setForeground(new Color(226, 232, 240));
            guideCard.add(lblStep);
            guideCard.add(Box.createVerticalStrut(6));
        }

        centerPanel.add(guideCard);
        centerPanel.add(Box.createVerticalStrut(16));

        // Selector / Confirmación de acceso
        JLabel lblPrompt = new JLabel("Ingresar como Usuario Puente:");
        lblPrompt.setFont(UITheme.FONT_BOLD);
        lblPrompt.setForeground(Color.WHITE);
        centerPanel.add(lblPrompt);
        centerPanel.add(Box.createVerticalStrut(6));

        cmbUsuarios.removeAllItems();
        cmbUsuarios.addItem(DatabaseService.ASH_BRIDGE_USER);
        cmbUsuarios.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cmbUsuarios.setFont(UITheme.FONT_REGULAR);
        centerPanel.add(cmbUsuarios);

        root.add(centerPanel, BorderLayout.CENTER);

        // Footer buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        JButton btnSalir = UITheme.createSecondaryButton("Salir");
        btnSalir.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Deseas cerrar la aplicación?", "Salir", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        JButton btnComenzar = UITheme.createPrimaryButton("🚀 Comenzar Configuración");
        btnComenzar.addActionListener(e -> {
            selectedUser = DatabaseService.ASH_BRIDGE_USER;
            dispose();
        });

        btnPanel.add(btnSalir);
        btnPanel.add(btnComenzar);
        root.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    /**
     * Pantalla limpia de Login estándar para cuando ya existe al menos un Administrador.
     * No muestra banners informativos ni referencias al usuario puente/proceso anterior.
     */
    private void initStandardLoginUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(UITheme.COLOR_BG_SIDEBAR);
        root.setBorder(new EmptyBorder(24, 28, 20, 28));

        // Header limpio
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel lblLogo = new JLabel("⚡ SPACE CARDS MANAGER");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Selecciona tu usuario para ingresar");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblLogo);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(lblSub);
        root.add(headerPanel, BorderLayout.NORTH);

        // Center: Selector limpio
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(Box.createVerticalStrut(10));

        JLabel lblSelect = new JLabel("Usuario:");
        lblSelect.setFont(UITheme.FONT_BOLD);
        lblSelect.setForeground(Color.WHITE);
        centerPanel.add(lblSelect);
        centerPanel.add(Box.createVerticalStrut(8));

        cmbUsuarios.removeAllItems();
        List<Usuario> activeUsers = db.getUsuarios();
        for (Usuario u : activeUsers) {
            if (u.isActivo()) {
                cmbUsuarios.addItem(u);
            }
        }

        if (cmbUsuarios.getItemCount() == 0) {
            cmbUsuarios.addItem("No hay usuarios activos");
        }

        cmbUsuarios.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cmbUsuarios.setFont(UITheme.FONT_REGULAR);
        centerPanel.add(cmbUsuarios);

        root.add(centerPanel, BorderLayout.CENTER);

        // Footer
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        JButton btnSalir = UITheme.createSecondaryButton("Salir");
        btnSalir.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Deseas cerrar la aplicación?", "Salir", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        JButton btnEntrar = UITheme.createPrimaryButton("Iniciar Sesión");
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
