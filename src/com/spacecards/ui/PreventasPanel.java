package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Cliente;
import com.spacecards.model.Preventa;
import com.spacecards.model.Producto;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PreventasPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{"Todos los estados", "Pendiente", "Entregado", "Cancelado"});
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Preventa> filteredList = new ArrayList<>();
    private final DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Runnable onDataChanged;

    public PreventasPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Control de Preventas & Anticipos");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Gestión de pedidos anticipados de cartas y cajas con depósito inicial");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        // Botones superiores
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);

        JButton btnNuevo = UITheme.createPrimaryButton("Registrar Preventa");
        btnNuevo.addActionListener(e -> showPreventaDialog());

        JButton btnEntregar = UITheme.createAccentButton("Liquidar y Entregar");
        btnEntregar.addActionListener(e -> marcarEntregado());

        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> cancelarPreventa());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnEntregar);
        actionButtons.add(btnCancelar);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center Card
        JPanel cardPanel = UITheme.createCardPanel();
        cardPanel.setLayout(new BorderLayout(12, 12));

        // Filtros
        JPanel filterBar = new JPanel(new GridBagLayout());
        filterBar.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.7;
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar por cliente o producto...");
        txtBuscar.addCaretListener(e -> refreshTable());
        filterBar.add(txtBuscar, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3;
        cmbEstado.addActionListener(e -> refreshTable());
        filterBar.add(cmbEstado, gbc);

        cardPanel.add(filterBar, BorderLayout.NORTH);

        // Tabla
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Cliente", "Producto Reservado", "Anticipo", "Total", "Saldo Pendiente", "Fecha Solicitud", "Entrega Estimada", "Estado"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        UITheme.formatTable(table);
        cardPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        this.add(cardPanel, BorderLayout.CENTER);

        refreshTable();
    }

    public void refreshTable() {
        String query = txtBuscar.getText().trim().toLowerCase();
        String estadoFilter = (String) cmbEstado.getSelectedItem();

        tableModel.setRowCount(0);
        filteredList.clear();

        for (Preventa p : db.getPreventas()) {
            boolean matchEstado = estadoFilter == null || "Todos los estados".equals(estadoFilter) || p.getEstado().equalsIgnoreCase(estadoFilter);
            String cliName = p.getCliente() != null ? p.getCliente().getNombreCompleto() : "";
            String prodName = p.getProducto() != null ? p.getProducto().getNombre() : "";
            boolean matchQuery = query.isEmpty() ||
                    cliName.toLowerCase().contains(query) ||
                    prodName.toLowerCase().contains(query);

            if (matchEstado && matchQuery) {
                filteredList.add(p);
                tableModel.addRow(new Object[]{
                        "#" + p.getIdPreventa(),
                        cliName,
                        prodName,
                        "$" + p.getMontoAnticipo(),
                        "$" + p.getMontoTotal(),
                        "$" + p.getSaldoPendiente(),
                        p.getFechaSolicitud().format(fmtDate),
                        p.getFechaEstimadaEntrega().format(fmtDate),
                        p.getEstado()
                });
            }
        }
    }

    private void showPreventaDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "PantallaCrearRegistroPreventa", true);
        dialog.setSize(480, 440);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<Cliente> cmbCli = new JComboBox<>();
        for (Cliente c : db.getClientes()) cmbCli.addItem(c);

        JComboBox<Usuario> cmbUsr = new JComboBox<>();
        for (Usuario u : db.getUsuarios()) if (u.isActivo()) cmbUsr.addItem(u);

        JComboBox<Producto> cmbProd = new JComboBox<>();
        for (Producto p : db.getProductos()) cmbProd.addItem(p);

        JTextField txtTotal = new JTextField("0.00");
        JTextField txtAnticipo = new JTextField("0.00");
        JTextField txtDiasEntrega = new JTextField("15");

        // Al cambiar producto, autocompletar precio
        cmbProd.addActionListener(e -> {
            Producto selected = (Producto) cmbProd.getSelectedItem();
            if (selected != null) {
                txtTotal.setText(selected.getPrecioUnitario().toString());
                txtAnticipo.setText(selected.getPrecioUnitario().multiply(new BigDecimal("0.3")).setScale(2, java.math.RoundingMode.HALF_UP).toString());
            }
        });
        if (cmbProd.getItemCount() > 0) cmbProd.setSelectedIndex(0);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Cliente:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(cmbCli, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Atendido por:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbUsr, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Producto TCG:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbProd, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Monto Total ($):*"), gbc);
        gbc.gridx = 1;
        form.add(txtTotal, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("Monto Anticipo ($):*"), gbc);
        gbc.gridx = 1;
        form.add(txtAnticipo, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Entrega en (días):*"), gbc);
        gbc.gridx = 1;
        form.add(txtDiasEntrega, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Registrar Preventa");
        btnGuardar.addActionListener(e -> {
            Cliente cli = (Cliente) cmbCli.getSelectedItem();
            Usuario usr = (Usuario) cmbUsr.getSelectedItem();
            Producto prod = (Producto) cmbProd.getSelectedItem();

            if (cli == null || usr == null || prod == null) {
                JOptionPane.showMessageDialog(dialog, "Debes seleccionar cliente, usuario y producto.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal total, anticipo;
            int dias;
            try {
                total = new BigDecimal(txtTotal.getText().trim());
                anticipo = new BigDecimal(txtAnticipo.getText().trim());
                dias = Integer.parseInt(txtDiasEntrega.getText().trim());

                if (anticipo.compareTo(BigDecimal.ZERO) < 0 || total.compareTo(anticipo) < 0 || dias <= 0) {
                    throw new Exception();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Valores numéricos no válidos (El anticipo no puede superar al total).", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Preventa nueva = new Preventa(0, cli, usr, prod, anticipo, total, LocalDate.now().plusDays(dias), "Pendiente");
            db.addPreventa(nueva);

            dialog.dispose();
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        });

        JButton btnCancel = UITheme.createSecondaryButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnGuardar);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void marcarEntregado() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona una preventa de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Preventa p = filteredList.get(row);
        if ("Entregado".equalsIgnoreCase(p.getEstado())) {
            JOptionPane.showMessageDialog(this, "Esta preventa ya fue entregada y liquidada anteriormente.", "Información", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Liquidar saldo pendiente de $" + p.getSaldoPendiente() + " y entregar producto al cliente?",
                "Confirmar Entrega", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.updatePreventaEstado(p.getIdPreventa(), "Entregado");
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
            JOptionPane.showMessageDialog(this, "Preventa #" + p.getIdPreventa() + " marcada como Entregada.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void cancelarPreventa() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona una preventa para cancelar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Preventa p = filteredList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de cancelar la preventa #" + p.getIdPreventa() + "?",
                "Confirmar Cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.updatePreventaEstado(p.getIdPreventa(), "Cancelado");
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        }
    }
}
