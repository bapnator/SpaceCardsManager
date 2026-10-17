package com.spacecards.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.spacecards.dao.DatabaseService;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsContainer = new JPanel(cardLayout);

    private SidebarPanel sidebarPanel;
    private DashboardPanel dashboardPanel;
    private VentasPanel ventasPanel;
    private InventarioPanel inventarioPanel;
    private ClientesPanel clientesPanel;
    private PreventasPanel preventasPanel;
    private TorneosPanel torneosPanel;
    private UsuariosPanel usuariosPanel;
    private DatabasePanel databasePanel;

    public MainWindow() {
        setTitle("Space Cards Manager - TCG Store [Java 24 + SQLite]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 800);
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        // Crear vistas
        Runnable notifyDataChanged = this::refreshAllPanels;

        dashboardPanel = new DashboardPanel();
        ventasPanel = new VentasPanel(notifyDataChanged);
        inventarioPanel = new InventarioPanel(notifyDataChanged);
        clientesPanel = new ClientesPanel(notifyDataChanged);
        preventasPanel = new PreventasPanel(notifyDataChanged);
        torneosPanel = new TorneosPanel(notifyDataChanged);
        usuariosPanel = new UsuariosPanel(notifyDataChanged);
        databasePanel = new DatabasePanel(() -> {
            sidebarPanel.updateDbStatus(DatabaseService.getInstance().getConnectionStatus(), true);
            refreshAllPanels();
        });

        // Registrar tarjetas
        cardsContainer.add(dashboardPanel, "DASHBOARD");
        cardsContainer.add(ventasPanel, "POS");
        cardsContainer.add(inventarioPanel, "INVENTARIO");
        cardsContainer.add(clientesPanel, "CLIENTES");
        cardsContainer.add(preventasPanel, "PREVENTAS");
        cardsContainer.add(torneosPanel, "TORNEOS");
        cardsContainer.add(usuariosPanel, "USUARIOS");
        cardsContainer.add(databasePanel, "DATABASE");

        // Barra lateral
        sidebarPanel = new SidebarPanel(cardName -> {
            cardLayout.show(cardsContainer, cardName);
            refreshAllPanels();
            if ("DATABASE".equals(cardName) && databasePanel != null) {
                databasePanel.refreshPanel();
            }
        });

        // Contenedor principal
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(sidebarPanel, BorderLayout.WEST);
        rootPanel.add(cardsContainer, BorderLayout.CENTER);

        setContentPane(rootPanel);
    }

    private void refreshAllPanels() {
        if (dashboardPanel != null) dashboardPanel.refreshData();
        if (ventasPanel != null) ventasPanel.refreshAll();
        if (inventarioPanel != null) inventarioPanel.refreshTable();
        if (clientesPanel != null) clientesPanel.refreshTable();
        if (preventasPanel != null) preventasPanel.refreshTable();
        if (torneosPanel != null) torneosPanel.refreshTorneos();
        if (usuariosPanel != null) usuariosPanel.refreshTable();
    }

    public static void main(String[] args) {
        // Inicializar FlatLaf Dark Look & Feel
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        } catch (Exception ex) {
            System.err.println("Fallo al inicializar FlatLaf: " + ex.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
