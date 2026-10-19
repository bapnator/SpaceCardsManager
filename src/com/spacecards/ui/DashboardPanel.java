package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Producto;
import com.spacecards.model.Torneo;
import com.spacecards.model.Venta;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JLabel lblTotalVentas = new JLabel("$0.00");
    private final JLabel lblStockTotal = new JLabel("0 items");
    private final JLabel lblPreventasCount = new JLabel("0 activas");
    private final JLabel lblTorneosCount = new JLabel("0 eventos");

    private final DefaultTableModel ventasModel;
    private final JTable tableVentas;
    private final DefaultTableModel torneosModel;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final java.util.List<Venta> currentVentasList = new java.util.ArrayList<>();

    public DashboardPanel() {
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Panel de Control General");
        lblTitle.setFont(UITheme.FONT_TITLE);

        JLabel lblSub = new JLabel("Resumen de operaciones del TCG Pokémon y tienda especializada");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        JButton btnRefresh = UITheme.createSecondaryButton("🔄 Actualizar");
        btnRefresh.addActionListener(e -> refreshData());
        headerPanel.add(btnRefresh, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Cards + Tables
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // 4 KPI Cards
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 16, 0));
        statsGrid.setOpaque(false);

        statsGrid.add(createKpiCard("💰 Ingresos por Ventas", lblTotalVentas, UITheme.COLOR_SUCCESS));
        statsGrid.add(createKpiCard("🎴 Inventario Disponible", lblStockTotal, UITheme.COLOR_PRIMARY));
        statsGrid.add(createKpiCard("📦 Preventas Activas", lblPreventasCount, UITheme.COLOR_ACCENT));
        statsGrid.add(createKpiCard("🏆 Torneos Programados", lblTorneosCount, new Color(168, 85, 247)));

        centerPanel.add(statsGrid, BorderLayout.NORTH);

        // Split view with 2 preview tables
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        tablesPanel.setOpaque(false);

        // Ventas Recientes Card
        JPanel cardVentas = UITheme.createCardPanel();
        cardVentas.setLayout(new BorderLayout(8, 8));

        JPanel ventasHeader = new JPanel(new BorderLayout());
        ventasHeader.setOpaque(false);
        JLabel lblVentasTitle = new JLabel("💳 Ventas Recientes");
        lblVentasTitle.setFont(UITheme.FONT_SUBTITLE);
        ventasHeader.add(lblVentasTitle, BorderLayout.WEST);

        JButton btnVerDetalle = UITheme.createSecondaryButton("🔍 Ver Ticket / Detalle");
        btnVerDetalle.addActionListener(e -> showDetalleVentaDialog());
        ventasHeader.add(btnVerDetalle, BorderLayout.EAST);
        cardVentas.add(ventasHeader, BorderLayout.NORTH);

        ventasModel = new DefaultTableModel(new String[]{"ID", "Cliente", "Total", "Método", "Fecha"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tableVentas = new JTable(ventasModel);
        UITheme.formatTable(tableVentas);
        tableVentas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) showDetalleVentaDialog();
            }
        });
        cardVentas.add(new JScrollPane(tableVentas), BorderLayout.CENTER);
        tablesPanel.add(cardVentas);

        // Torneos Activos Card
        JPanel cardTorneos = UITheme.createCardPanel();
        cardTorneos.setLayout(new BorderLayout(8, 8));
        JLabel lblTorneosTitle = new JLabel("🏆 Próximos Torneos & Eventos");
        lblTorneosTitle.setFont(UITheme.FONT_SUBTITLE);
        cardTorneos.add(lblTorneosTitle, BorderLayout.NORTH);

        torneosModel = new DefaultTableModel(new String[]{"Torneo", "Fecha", "Cupos", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable tableTorneos = new JTable(torneosModel);
        UITheme.formatTable(tableTorneos);
        cardTorneos.add(new JScrollPane(tableTorneos), BorderLayout.CENTER);
        tablesPanel.add(cardTorneos);

        centerPanel.add(tablesPanel, BorderLayout.CENTER);

        this.add(centerPanel, BorderLayout.CENTER);

        refreshData();
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = UITheme.createCardPanel();
        card.setLayout(new BorderLayout(4, 4));

        JLabel lblCardTitle = new JLabel(title);
        lblCardTitle.setFont(UITheme.FONT_REGULAR);
        lblCardTitle.setForeground(UITheme.COLOR_TEXT_MUTED);

        valueLabel.setFont(UITheme.FONT_BIG_NUMBER);
        valueLabel.setForeground(accentColor);

        card.add(lblCardTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public void refreshData() {
        // Calcular total ventas
        BigDecimal sum = BigDecimal.ZERO;
        List<Venta> listaVentas = db.getVentas();
        for (Venta v : listaVentas) {
            sum = sum.add(v.getMontoTotal());
        }
        lblTotalVentas.setText("$" + sum);

        // Calcular stock
        int totalUnits = 0;
        List<Producto> listaProds = db.getProductos();
        for (Producto p : listaProds) {
            totalUnits += p.getStock();
        }
        lblStockTotal.setText(listaProds.size() + " ítems (" + totalUnits + " u.)");

        // Preventas
        long preventasPendientes = db.getPreventas().stream()
                .filter(p -> "Pendiente".equalsIgnoreCase(p.getEstado()))
                .count();
        lblPreventasCount.setText(preventasPendientes + " pendientes");

        // Torneos
        long torneosActivos = db.getTorneos().stream()
                .filter(t -> !"Finalizado".equalsIgnoreCase(t.getEstado()) && !"Cancelado".equalsIgnoreCase(t.getEstado()))
                .count();
        lblTorneosCount.setText(torneosActivos + " programados");

        // Actualizar tabla ventas
        ventasModel.setRowCount(0);
        currentVentasList.clear();
        for (int i = 0; i < Math.min(8, listaVentas.size()); i++) {
            Venta v = listaVentas.get(i);
            currentVentasList.add(v);
            String cli = v.getCliente() != null ? v.getCliente().getNombreCompleto() : "Público General";
            ventasModel.addRow(new Object[]{
                    "#" + v.getIdVenta(),
                    cli,
                    "$" + v.getMontoTotal(),
                    v.getMetodoPago(),
                    v.getFechaVenta().format(fmt)
            });
        }

        // Actualizar tabla torneos
        torneosModel.setRowCount(0);
        List<Torneo> listaTorneos = db.getTorneos();
        for (Torneo t : listaTorneos) {
            torneosModel.addRow(new Object[]{
                    t.getNombreTorneo(),
                    t.getFechaEvento().format(fmt),
                    t.getParticipantesInscritos() + " / " + t.getCupoMaximo(),
                    t.getEstado()
            });
        }
    }

    private void showDetalleVentaDialog() {
        int row = tableVentas.getSelectedRow();
        if (row < 0 || row >= currentVentasList.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona una venta de la lista para ver su detalle.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Venta v = currentVentasList.get(row);
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Detalle del Ticket #" + v.getIdVenta(), true);
        dlg.setSize(560, 420);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(12, 12));

        JPanel header = new JPanel(new GridLayout(3, 2, 8, 4));
        header.setBorder(new EmptyBorder(12, 16, 8, 16));
        String cli = v.getCliente() != null ? v.getCliente().getNombreCompleto() : "Público General";
        String usr = v.getUsuario() != null ? v.getUsuario().getNombreCompleto() : "Staff";
        header.add(new JLabel("Cliente: " + cli));
        header.add(new JLabel("Atendido por: " + usr));
        header.add(new JLabel("Fecha: " + v.getFechaVenta().format(fmt)));
        header.add(new JLabel("Método de Pago: " + v.getMetodoPago()));
        JLabel lblTotalTicket = new JLabel("Total: $" + v.getMontoTotal());
        lblTotalTicket.setFont(UITheme.FONT_BOLD);
        lblTotalTicket.setForeground(UITheme.COLOR_ACCENT);
        header.add(lblTotalTicket);
        dlg.add(header, BorderLayout.NORTH);

        DefaultTableModel detModel = new DefaultTableModel(new String[]{"Producto", "Categoría", "Cant.", "Precio Unit.", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tableDet = new JTable(detModel);
        UITheme.formatTable(tableDet);

        java.util.List<com.spacecards.model.DetalleVenta> detalles = db.getDetallesByVenta(v.getIdVenta());
        for (com.spacecards.model.DetalleVenta d : detalles) {
            String pName = d.getProducto() != null ? d.getProducto().getNombre() : "ID #" + d.getIdProducto();
            String pCat = d.getProducto() != null ? d.getProducto().getCategoria() : "-";
            detModel.addRow(new Object[]{
                    pName,
                    pCat,
                    d.getCantidad(),
                    "$" + d.getPrecioHistorico(),
                    "$" + d.getSubtotal()
            });
        }
        dlg.add(new JScrollPane(tableDet), BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCerrar = UITheme.createSecondaryButton("Cerrar");
        btnCerrar.addActionListener(e -> dlg.dispose());
        btnBar.add(btnCerrar);
        dlg.add(btnBar, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }
}
