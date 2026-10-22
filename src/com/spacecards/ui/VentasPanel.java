package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class VentasPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();

    // Catálogo
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbCategoria = new JComboBox<>(new String[]{
            "Todas las categorías", "Single Card", "Booster Box", "Elite Trainer Box", "Sleeves", "Deck"
    });
    private final DefaultTableModel catalogModel;
    private final JTable tableCatalog;
    private final JSpinner spinCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private List<Producto> currentCatalogList = new ArrayList<>();

    // Carrito
    private final List<DetalleVenta> cartItems = new ArrayList<>();
    private final DefaultTableModel cartModel;
    private final JTable tableCart;
    private final JLabel lblTotal = new JLabel("$0.00");

    // Checkout
    private final JComboBox<ClienteItem> cmbClientes = new JComboBox<>();
    private final JComboBox<Usuario> cmbUsuarios = new JComboBox<>();
    private final JComboBox<String> cmbMetodoPago = new JComboBox<>(new String[]{"Efectivo", "Tarjeta", "Transferencia"});

    private final Runnable onVentaCompletada;

    // Helper para cliente anónimo
    private static class ClienteItem {
        final Cliente cliente;
        ClienteItem(Cliente cliente) { this.cliente = cliente; }
        @Override
        public String toString() {
            return cliente == null ? "Público General (Anónimo)" : cliente.getNombreCompleto() + " (" + cliente.getCorreoElectronico() + ")";
        }
    }

    public VentasPanel(Runnable onVentaCompletada) {
        this.onVentaCompletada = onVentaCompletada;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Punto de Venta (POS) - Registrar Venta");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Selecciona cartas, sobres o accesorios y genera el ticket de venta");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        headerPanel.add(titleBox, BorderLayout.WEST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Split layout (Left: Catálogo, Right: Carrito & Cobro)
        JPanel contentGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        contentGrid.setOpaque(false);

        // ================= PANEL IZQUIERDO: CATÁLOGO =================
        JPanel panelCatalogo = UITheme.createCardPanel();
        panelCatalogo.setLayout(new BorderLayout(10, 10));

        // Filtros catálogo
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6;
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar carta o producto...");
        txtBuscar.addCaretListener(e -> filterCatalog());
        filterPanel.add(txtBuscar, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.4;
        cmbCategoria.addActionListener(e -> filterCatalog());
        filterPanel.add(cmbCategoria, gbc);

        panelCatalogo.add(filterPanel, BorderLayout.NORTH);

        // Tabla catálogo
        catalogModel = new DefaultTableModel(new String[]{"ID", "Código", "Producto", "Precio", "Stock"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tableCatalog = new JTable(catalogModel);
        UITheme.formatTable(tableCatalog);
        panelCatalogo.add(new JScrollPane(tableCatalog), BorderLayout.CENTER);

        // Botón añadir
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        addPanel.setOpaque(false);
        addPanel.add(new JLabel("Cantidad:"));
        spinCantidad.setPreferredSize(new Dimension(60, 30));
        addPanel.add(spinCantidad);

        JButton btnAddToCart = UITheme.createPrimaryButton("Añadir al Carrito");
        btnAddToCart.addActionListener(e -> addItemToCart());
        addPanel.add(btnAddToCart);
        panelCatalogo.add(addPanel, BorderLayout.SOUTH);

        contentGrid.add(panelCatalogo);

        // ================= PANEL DERECHO: CARRITO Y COBRO =================
        JPanel panelCheckout = UITheme.createCardPanel();
        panelCheckout.setLayout(new BorderLayout(10, 10));

        JLabel lblCartTitle = new JLabel("Carrito de Compras");
        lblCartTitle.setFont(UITheme.FONT_SUBTITLE);
        panelCheckout.add(lblCartTitle, BorderLayout.NORTH);

        // Tabla Carrito
        cartModel = new DefaultTableModel(new String[]{"Producto", "Cant.", "Precio Unit.", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tableCart = new JTable(cartModel);
        UITheme.formatTable(tableCart);
        panelCheckout.add(new JScrollPane(tableCart), BorderLayout.CENTER);

        // Acciones carrito (Quitar item seleccionado)
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cartActions.setOpaque(false);
        JButton btnRemove = UITheme.createSecondaryButton("Quitar del Carrito");
        btnRemove.addActionListener(e -> removeCartItem());
        JButton btnClear = UITheme.createSecondaryButton("Vaciar");
        btnClear.addActionListener(e -> clearCart());
        cartActions.add(btnRemove);
        cartActions.add(btnClear);

        // Checkout form
        JPanel checkoutForm = new JPanel(new GridBagLayout());
        checkoutForm.setOpaque(false);
        GridBagConstraints cf = new GridBagConstraints();
        cf.insets = new Insets(4, 4, 4, 4);
        cf.fill = GridBagConstraints.HORIZONTAL;

        cf.gridx = 0; cf.gridy = 0; cf.weightx = 0.3;
        checkoutForm.add(new JLabel("Cliente:"), cf);
        cf.gridx = 1; cf.gridy = 0; cf.weightx = 0.7;
        checkoutForm.add(cmbClientes, cf);

        cf.gridx = 0; cf.gridy = 1;
        checkoutForm.add(new JLabel("Atendido por:"), cf);
        cf.gridx = 1; cf.gridy = 1;
        checkoutForm.add(cmbUsuarios, cf);

        cf.gridx = 0; cf.gridy = 2;
        checkoutForm.add(new JLabel("Método de Pago:"), cf);
        cf.gridx = 1; cf.gridy = 2;
        checkoutForm.add(cmbMetodoPago, cf);

        cf.gridx = 0; cf.gridy = 3;
        checkoutForm.add(new JLabel("TOTAL A COBRAR:"), cf);
        cf.gridx = 1; cf.gridy = 3;
        lblTotal.setFont(UITheme.FONT_BIG_NUMBER);
        lblTotal.setForeground(UITheme.COLOR_ACCENT);
        checkoutForm.add(lblTotal, cf);

        JButton btnCobrar = UITheme.createAccentButton("Confirmar y Cobrar Venta");
        btnCobrar.setPreferredSize(new Dimension(220, 42));
        btnCobrar.addActionListener(e -> processCheckout());

        cf.gridx = 0; cf.gridy = 4; cf.gridwidth = 2;
        checkoutForm.add(btnCobrar, cf);

        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setOpaque(false);
        bottomContainer.add(cartActions, BorderLayout.NORTH);
        bottomContainer.add(checkoutForm, BorderLayout.CENTER);

        panelCheckout.add(bottomContainer, BorderLayout.SOUTH);

        contentGrid.add(panelCheckout);

        this.add(contentGrid, BorderLayout.CENTER);

        refreshAll();
    }

    private void filterCatalog() {
        String query = txtBuscar.getText().trim().toLowerCase();
        String cat = (String) cmbCategoria.getSelectedItem();

        catalogModel.setRowCount(0);
        currentCatalogList.clear();

        for (Producto p : db.getProductos()) {
            if (p.isPreventa()) continue;
            boolean matchCat = cat == null || "Todas las categorías".equals(cat) || p.getCategoria().equalsIgnoreCase(cat);
            boolean matchQuery = query.isEmpty() ||
                    p.getNombre().toLowerCase().contains(query) ||
                    (p.getCodigoBarras() != null && p.getCodigoBarras().toLowerCase().contains(query));

            if (matchCat && matchQuery) {
                currentCatalogList.add(p);
                catalogModel.addRow(new Object[]{
                        p.getIdProducto(),
                        p.getCodigoBarras(),
                        p.getNombre(),
                        "$" + p.getPrecioUnitario(),
                        p.getStock()
                });
            }
        }
    }

    private void addItemToCart() {
        int row = tableCatalog.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Por favor selecciona un producto de la lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Producto p = currentCatalogList.get(row);
        int qty = (int) spinCantidad.getValue();

        if (p.getStock() <= 0) {
            JOptionPane.showMessageDialog(this, "El producto '" + p.getNombre() + "' está agotado.", "Sin stock", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Buscar si ya está en el carrito
        DetalleVenta existing = null;
        for (DetalleVenta d : cartItems) {
            if (d.getIdProducto() == p.getIdProducto()) {
                existing = d;
                break;
            }
        }

        int totalRequested = (existing != null ? existing.getCantidad() : 0) + qty;
        if (totalRequested > p.getStock()) {
            JOptionPane.showMessageDialog(this, "No hay suficiente stock. Disponible: " + p.getStock() +
                    " (Ya en carrito: " + (existing != null ? existing.getCantidad() : 0) + ")", "Stock superado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (existing != null) {
            existing.setCantidad(totalRequested);
        } else {
            DetalleVenta nuevo = new DetalleVenta(0, 0, p, qty, p.getPrecioUnitario());
            cartItems.add(nuevo);
        }

        updateCartTable();
    }

    private void removeCartItem() {
        int row = tableCart.getSelectedRow();
        if (row >= 0 && row < cartItems.size()) {
            cartItems.remove(row);
            updateCartTable();
        }
    }

    private void clearCart() {
        cartItems.clear();
        updateCartTable();
    }

    private void updateCartTable() {
        cartModel.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;

        for (DetalleVenta d : cartItems) {
            cartModel.addRow(new Object[]{
                    d.getProducto().getNombre(),
                    d.getCantidad(),
                    "$" + d.getPrecioHistorico(),
                    "$" + d.getSubtotal()
            });
            total = total.add(d.getSubtotal());
        }

        lblTotal.setText("$" + total);
    }

    private void processCheckout() {
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El carrito de compras está vacío.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClienteItem cliItem = (ClienteItem) cmbClientes.getSelectedItem();
        Usuario usr = (Usuario) cmbUsuarios.getSelectedItem();
        if (usr == null) {
            JOptionPane.showMessageDialog(this, "Debe registrar y seleccionar al menos un usuario/empleado activo para realizar la venta.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String metodo = (String) cmbMetodoPago.getSelectedItem();

        Venta venta = new Venta();
        if (cliItem != null && cliItem.cliente != null) {
            venta.setCliente(cliItem.cliente);
            venta.setIdCliente(cliItem.cliente.getIdCliente());
        } else {
            venta.setCliente(null);
            venta.setIdCliente(null); // Venta a público general
        }

        if (usr != null) {
            venta.setUsuario(usr);
            venta.setIdUsuario(usr.getIdUsuario());
        }

        venta.setMetodoPago(metodo);

        for (DetalleVenta d : cartItems) {
            venta.addDetalle(d);
        }

        try {
            db.registrarVenta(venta);
            JOptionPane.showMessageDialog(this,
                    "¡Venta registrada con éxito!\n\n" +
                            "Ticket: #" + venta.getIdVenta() + "\n" +
                            "Cliente: " + (venta.getCliente() != null ? venta.getCliente().getNombreCompleto() : "Público General") + "\n" +
                            "Total pagado: $" + venta.getMontoTotal() + "\n" +
                            "Método: " + venta.getMetodoPago(),
                    "Venta Completada", JOptionPane.INFORMATION_MESSAGE);

            clearCart();
            filterCatalog();
            if (onVentaCompletada != null) onVentaCompletada.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al procesar la venta: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshAll() {
        filterCatalog();

        // Recargar combos
        cmbClientes.removeAllItems();
        cmbClientes.addItem(new ClienteItem(null)); // Público general
        for (Cliente c : db.getClientes()) {
            cmbClientes.addItem(new ClienteItem(c));
        }

        cmbUsuarios.removeAllItems();
        for (Usuario u : db.getUsuarios()) {
            if (u.isActivo()) {
                cmbUsuarios.addItem(u);
            }
        }

        Usuario current = SessionManager.getInstance().getCurrentUser();
        if (current != null && current.getIdUsuario() > 0) {
            for (int i = 0; i < cmbUsuarios.getItemCount(); i++) {
                Usuario u = cmbUsuarios.getItemAt(i);
                if (u.getIdUsuario() == current.getIdUsuario()) {
                    cmbUsuarios.setSelectedIndex(i);
                    break;
                }
            }
        }
    }
}
