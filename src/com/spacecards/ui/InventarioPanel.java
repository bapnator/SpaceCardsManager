package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Producto;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class InventarioPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbCategoria = new JComboBox<>(new String[]{
            "Todas las categorías", "Single Card", "Booster Box", "Elite Trainer Box", "Sleeves", "Deck"
    });
    private final JComboBox<String> cmbJuego = new JComboBox<>();
    private final JComboBox<String> cmbStock = new JComboBox<>(new String[]{
            "Todos", "Con stock (> 0)", "Stock crítico (<= 5)", "Agotado (0)"
    });
    private boolean updatingJuegosFilter = false;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Producto> filteredList = new ArrayList<>();
    private final Runnable onDataChanged;

    public InventarioPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Inventario TCG - Catálogo de Cartas & Productos");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Consulta, registra y actualiza productos coleccionables y sellados");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        // Botones de acción superior
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);

        JButton btnNuevo = UITheme.createPrimaryButton("Registrar Producto");
        btnNuevo.addActionListener(e -> showProductoDialog(null));

        JButton btnEditar = UITheme.createSecondaryButton("Editar");
        btnEditar.addActionListener(e -> editSelectedProducto());

        JButton btnEliminar = UITheme.createSecondaryButton("Eliminar");
        btnEliminar.addActionListener(e -> deleteSelectedProducto());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnEditar);
        actionButtons.add(btnEliminar);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Card container con Filtros y Tabla
        JPanel cardPanel = UITheme.createCardPanel();
        cardPanel.setLayout(new BorderLayout(12, 12));

        // Filtros bar
        JPanel filterBar = new JPanel(new GridBagLayout());
        filterBar.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4;
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar por código, nombre o descripción...");
        txtBuscar.addCaretListener(e -> refreshTable());
        filterBar.add(txtBuscar, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.2;
        cmbCategoria.addActionListener(e -> refreshTable());
        filterBar.add(cmbCategoria, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.2;
        cmbJuego.addActionListener(e -> {
            if (!updatingJuegosFilter) refreshTable();
        });
        filterBar.add(cmbJuego, gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.2;
        cmbStock.addActionListener(e -> refreshTable());
        filterBar.add(cmbStock, gbc);

        cardPanel.add(filterBar, BorderLayout.NORTH);

        // Tabla
        tableModel = new DefaultTableModel(new String[]{
            "ID", "Código Barras", "Nombre del Producto", "Categoría", "Juego TCG", "Precio Unit.", "Stock", "Modalidad", "Estado"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        UITheme.formatTable(table);
        cardPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        this.add(cardPanel, BorderLayout.CENTER);

        updateJuegosFilter();
        refreshTable();
    }

    public void updateJuegosFilter() {
        updatingJuegosFilter = true;
        try {
            String current = (String) cmbJuego.getSelectedItem();
            List<String> activeNames = db.getNombresFranquiciasActivas();
            boolean needReload = false;
            if (cmbJuego.getItemCount() != activeNames.size() + 1) {
                needReload = true;
            } else {
                for (int i = 0; i < activeNames.size(); i++) {
                    if (!activeNames.get(i).equals(cmbJuego.getItemAt(i + 1))) {
                        needReload = true;
                        break;
                    }
                }
            }
            if (needReload) {
                cmbJuego.removeAllItems();
                cmbJuego.addItem("Todos los juegos");
                for (String f : activeNames) {
                    cmbJuego.addItem(f);
                }
                if (current != null) {
                    cmbJuego.setSelectedItem(current);
                }
            }
        } finally {
            updatingJuegosFilter = false;
        }
    }

    public void refreshTable() {
        updateJuegosFilter();
        String query = txtBuscar.getText().trim().toLowerCase();
        String cat = (String) cmbCategoria.getSelectedItem();
        String juego = (String) cmbJuego.getSelectedItem();
        String stockFilter = (String) cmbStock.getSelectedItem();

        tableModel.setRowCount(0);
        filteredList.clear();

        for (Producto p : db.getProductos()) {
            boolean matchCat = cat == null || "Todas las categorías".equals(cat) || p.getCategoria().equalsIgnoreCase(cat);
            boolean matchJuego = juego == null || "Todos los juegos".equals(juego) || p.getJuegoTcg().equalsIgnoreCase(juego);
            boolean matchQuery = query.isEmpty() ||
                    p.getNombre().toLowerCase().contains(query) ||
                    (p.getCodigoBarras() != null && p.getCodigoBarras().toLowerCase().contains(query)) ||
                    (p.getDescripcion() != null && p.getDescripcion().toLowerCase().contains(query));

            boolean matchStock = true;
            if (p.isPreventa()) matchStock = "Todos".equals(stockFilter);
            else if ("Con stock (> 0)".equals(stockFilter)) matchStock = p.getStock() > 0;
            else if ("Stock crítico (<= 5)".equals(stockFilter)) matchStock = p.getStock() > 0 && p.getStock() <= 5;
            else if ("Agotado (0)".equals(stockFilter)) matchStock = p.getStock() == 0;

            if (matchCat && matchJuego && matchQuery && matchStock) {
                filteredList.add(p);
                String estadoStock = p.isPreventa() ? "No disponible para venta" :
                        (p.getStock() == 0 ? "Agotado" : (p.getStock() <= 5 ? "Crítico" : "Disponible"));
                tableModel.addRow(new Object[]{
                        p.getIdProducto(),
                        p.getCodigoBarras() != null ? p.getCodigoBarras() : "-",
                        p.getNombre(),
                        p.getCategoria(),
                        p.getJuegoTcg(),
                        "$" + p.getPrecioUnitario(),
                        p.isPreventa() ? "-" : p.getStock(),
                        p.isPreventa() ? "Preventa" : "Venta normal",
                        estadoStock
                });
            }
        }
    }

    private void showProductoDialog(Producto p) {
        boolean isEdit = (p != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Editar Producto" : "PantallaCrearRegistroProducto", true);
        dialog.setSize(500, 550);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtCode = new JTextField(isEdit ? p.getCodigoBarras() : "");
        JTextField txtNombre = new JTextField(isEdit ? p.getNombre() : "");
        JComboBox<String> cmbCat = new JComboBox<>(new String[]{"Single Card", "Booster Box", "Elite Trainer Box", "Sleeves", "Deck"});
        if (isEdit) cmbCat.setSelectedItem(p.getCategoria());

        List<String> listaJuegos = new ArrayList<>(db.getNombresFranquiciasActivas());
        if (listaJuegos.isEmpty()) {
            listaJuegos.add("General");
        }
        if (isEdit && p.getJuegoTcg() != null && !p.getJuegoTcg().isEmpty() && !listaJuegos.contains(p.getJuegoTcg())) {
            listaJuegos.add(p.getJuegoTcg());
        }
        JComboBox<String> cmbGame = new JComboBox<>(listaJuegos.toArray(new String[0]));
        if (isEdit && p.getJuegoTcg() != null) cmbGame.setSelectedItem(p.getJuegoTcg());

        JTextField txtPrecio = new JTextField(isEdit ? p.getPrecioUnitario().toString() : "0.00");
        JSpinner spinStock = new JSpinner(new SpinnerNumberModel(isEdit ? p.getStock() : 1, 0, 9999, 1));
        JComboBox<String> cmbTipoVenta = new JComboBox<>(new String[]{"Venta normal", "Preventa"});
        if (isEdit) cmbTipoVenta.setSelectedItem(p.isPreventa() ? "Preventa" : "Venta normal");
        JLabel lblStock = new JLabel("Stock real:");
        spinStock.setEnabled(!"Preventa".equals(cmbTipoVenta.getSelectedItem()));
        if (!spinStock.isEnabled()) lblStock.setText("Stock real (no aplica):");
        cmbTipoVenta.addActionListener(e -> {
            boolean isPreventa = "Preventa".equals(cmbTipoVenta.getSelectedItem());
            if (isPreventa && ((Number) spinStock.getValue()).intValue() > 0) {
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Al cambiar a preventa se eliminará el stock real registrado. ¿Deseas continuar?",
                        "Confirmar cambio", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    cmbTipoVenta.setSelectedItem("Venta normal");
                    return;
                }
                spinStock.setValue(0);
            }
            spinStock.setEnabled(!isPreventa);
            lblStock.setText(isPreventa ? "Stock real (no aplica):" : "Stock real:");
        });
        JTextArea txtDesc = new JTextArea(isEdit ? p.getDescripcion() : "", 3, 20);
        txtDesc.setLineWrap(true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Código de Barras:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtCode, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Nombre:*"), gbc);
        gbc.gridx = 1;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Categoría:"), gbc);
        gbc.gridx = 1;
        form.add(cmbCat, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Juego TCG:"), gbc);
        gbc.gridx = 1;
        form.add(cmbGame, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("Precio Unitario ($):*"), gbc);
        gbc.gridx = 1;
        form.add(txtPrecio, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Modalidad:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbTipoVenta, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        form.add(lblStock, gbc);
        gbc.gridx = 1;
        form.add(spinStock, gbc);

        gbc.gridx = 0; gbc.gridy = 7;
        form.add(new JLabel("Descripción:"), gbc);
        gbc.gridx = 1;
        form.add(new JScrollPane(txtDesc), gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar");
        btnGuardar.addActionListener(e -> {
            String name = txtNombre.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "El nombre del producto es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal precio;
            try {
                precio = new BigDecimal(txtPrecio.getText().trim());
                if (precio.compareTo(BigDecimal.ZERO) < 0) throw new Exception();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "El precio debe ser un número válido mayor o igual a 0.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean isPreventa = "Preventa".equals(cmbTipoVenta.getSelectedItem());
            int stockVal = isPreventa ? 0 : (int) spinStock.getValue();
            String tipoVenta = isPreventa ? Producto.TIPO_PREVENTA : Producto.TIPO_VENTA_NORMAL;

            if (isEdit) {
                p.setCodigoBarras(txtCode.getText().trim());
                p.setNombre(name);
                p.setCategoria((String) cmbCat.getSelectedItem());
                p.setJuegoTcg((String) cmbGame.getSelectedItem());
                p.setPrecioUnitario(precio);
                p.setStock(stockVal);
                p.setTipoVenta(tipoVenta);
                p.setDescripcion(txtDesc.getText().trim());
                db.updateProducto(p);
            } else {
                Producto nuevo = new Producto(0, txtCode.getText().trim(), name, txtDesc.getText().trim(),
                        (String) cmbCat.getSelectedItem(), (String) cmbGame.getSelectedItem(), precio, stockVal);
                nuevo.setTipoVenta(tipoVenta);
                db.addProducto(nuevo);
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

    private void editSelectedProducto() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un producto para editar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showProductoDialog(filteredList.get(row));
    }

    private void deleteSelectedProducto() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un producto para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Producto p = filteredList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de eliminar el producto '" + p.getNombre() + "'?",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.deleteProducto(p.getIdProducto());
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        }
    }
}
