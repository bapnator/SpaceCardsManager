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
import java.math.RoundingMode;
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

        JLabel lblTitle = new JLabel("Pedidos anticipados");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Administra apartados, anticipos y fechas de entrega");
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
                "ID", "Cliente", "Producto Reservado", "Anticipo", "Total", "Saldo Pendiente", "Fecha Solicitud", "Llegada Estimada", "Vence Apartado", "Estado"
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
                        p.getFechaVencimientoApartado() != null ? p.getFechaVencimientoApartado().format(fmtDate) : "-",
                        "Pendiente".equalsIgnoreCase(p.getEstado()) && p.getFechaVencimientoApartado() != null &&
                                p.getFechaVencimientoApartado().isBefore(LocalDate.now())
                                ? "Pendiente (vencida)" : p.getEstado()
                });
            }
        }
    }

    private void showPreventaDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Registrar pedido anticipado", true);
        dialog.setSize(520, 440);
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
        for (Producto p : db.getProductos()) {
            if (p.isPreventa()) cmbProd.addItem(p);
        }

        JTextField txtTotal = new JTextField("0.00");
        JTextField txtAnticipo = new JTextField("0.00");
        txtAnticipo.setEditable(false);
        JLabel lblFechaEntrega = new JLabel("Selecciona un producto");
        int diasMargenLlegada = db.getDiasMargenLlegada();
        JLabel lblMargenLlegada = new JLabel("Llegada estimada (+" + diasMargenLlegada + " días):");
        int diasVigenciaApartado = db.getDiasVigenciaApartado();
        LocalDate fechaVencimientoApartado = LocalDate.now().plusDays(diasVigenciaApartado);
        JLabel lblVencimientoApartado = new JLabel(fechaVencimientoApartado.format(fmtDate));

        Runnable actualizarAnticipo = () -> {
            try {
                Producto selected = (Producto) cmbProd.getSelectedItem();
                if (selected == null) {
                    txtAnticipo.setText("");
                    return;
                }
                BigDecimal total = new BigDecimal(txtTotal.getText().trim());
                BigDecimal anticipo = total.multiply(selected.getPorcentajeApartado())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                txtAnticipo.setText(anticipo.toPlainString());
            } catch (NumberFormatException ex) {
                txtAnticipo.setText("");
            }
        };
        txtTotal.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { actualizarAnticipo.run(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { actualizarAnticipo.run(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarAnticipo.run(); }
        });

        cmbProd.addActionListener(e -> {
            Producto selected = (Producto) cmbProd.getSelectedItem();
            if (selected != null) {
                txtTotal.setText(selected.getPrecioUnitario().toString());
                LocalDate fechaLlegada = calcularLlegadaEstimada(selected, diasMargenLlegada);
                lblFechaEntrega.setText(fechaLlegada == null
                        ? "Registra una fecha del proveedor en el producto"
                        : formatearFechaEntrega(fechaLlegada));
            } else {
                lblFechaEntrega.setText("Selecciona un producto");
            }
            actualizarAnticipo.run();
        });
        if (cmbProd.getItemCount() > 0) {
            cmbProd.setSelectedIndex(-1);
            cmbProd.setSelectedIndex(0);
        }

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
        form.add(new JLabel("Monto del apartado ($):"), gbc);
        gbc.gridx = 1;
        form.add(txtAnticipo, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(lblMargenLlegada, gbc);
        gbc.gridx = 1;
        form.add(lblFechaEntrega, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        form.add(new JLabel("Vencimiento del apartado (" + diasVigenciaApartado + " días):"), gbc);
        gbc.gridx = 1;
        form.add(lblVencimientoApartado, gbc);

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
            try {
                total = new BigDecimal(txtTotal.getText().trim());
                anticipo = new BigDecimal(txtAnticipo.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "El total debe ser un valor numérico.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            LocalDate fechaLlegada = calcularLlegadaEstimada(prod, diasMargenLlegada);
            if (fechaLlegada == null) {
                JOptionPane.showMessageDialog(dialog,
                        "El producto no tiene registrada su fecha estimada del proveedor. Actualiza el producto antes de registrar la preventa.",
                        "Fecha pendiente", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (total.compareTo(BigDecimal.ZERO) <= 0 ||
                    anticipo.compareTo(BigDecimal.ZERO) <= 0 ||
                    total.compareTo(anticipo) < 0) {
                JOptionPane.showMessageDialog(dialog,
                        "El total debe ser mayor a cero y el monto del apartado no puede superar el total.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Preventa nueva = new Preventa(0, cli, usr, prod, anticipo, total, fechaLlegada, "Pendiente");
            nueva.setFechaVencimientoApartado(fechaVencimientoApartado);
            try {
                db.addPreventa(nueva);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, "No se pudo registrar la preventa: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

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

    private LocalDate calcularLlegadaEstimada(Producto producto, int diasMargenLlegada) {
        return producto.getFechaSalida() == null ? null : producto.getFechaSalida().plusDays(diasMargenLlegada);
    }

    private String formatearFechaEntrega(LocalDate fecha) {
        return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
            String[] metodosPago = {"Efectivo", "Tarjeta", "Transferencia"};
            String metodoPago = (String) JOptionPane.showInputDialog(this,
                    "Selecciona el método de pago del saldo pendiente:",
                    "Liquidar Preventa", JOptionPane.QUESTION_MESSAGE, null, metodosPago, metodosPago[0]);
            if (metodoPago == null) return;

            try {
                db.liquidarPreventa(p.getIdPreventa(), metodoPago);
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
                JOptionPane.showMessageDialog(this, "Preventa #" + p.getIdPreventa() + " registrada como venta y marcada como Entregada.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo liquidar la preventa: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
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
