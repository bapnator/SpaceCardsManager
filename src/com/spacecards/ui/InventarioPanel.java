package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Producto;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class InventarioPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
        private final JComboBox<String> cmbCategoria = new JComboBox<>();
    private final JComboBox<String> cmbJuego = new JComboBox<>();
    private final JComboBox<String> cmbStock = new JComboBox<>(new String[]{
                "Todos", "Con unidades (> 0)", "Unidades críticas (<= 5)", "Sin unidades (0)"
    });
    private boolean updatingJuegosFilter = false;
    private boolean updatingCategoriasFilter = false;

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

        JLabel lblTitle = new JLabel("Inventario de productos");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Consulta y administra cartas, accesorios y productos sellados");
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
        cmbCategoria.addActionListener(e -> {
            if (!updatingCategoriasFilter) refreshTable();
        });
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
            "ID", "Código Barras", "Nombre del Producto", "Categoría", "Juego TCG", "Precio Unit.", "Disponibles / pedidas", "Modalidad", "Fecha de salida", "Estado"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        UITheme.formatTable(table);
        cardPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        this.add(cardPanel, BorderLayout.CENTER);

        updateJuegosFilter();
        updateCategoriasFilter();
        refreshTable();
    }

    private void updateCategoriasFilter() {
        updatingCategoriasFilter = true;
        try {
            String current = (String) cmbCategoria.getSelectedItem();
            List<String> categories = db.getNombresCategoriasActivas();
            cmbCategoria.removeAllItems();
            cmbCategoria.addItem("Todas las categorías");
            for (String category : categories) cmbCategoria.addItem(category);
            if ("Todas las categorías".equals(current) || categories.contains(current)) cmbCategoria.setSelectedItem(current);
            else cmbCategoria.setSelectedIndex(0);
        } finally {
            updatingCategoriasFilter = false;
        }
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
        updateCategoriasFilter();
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
            if ("Con unidades (> 0)".equals(stockFilter)) matchStock = p.getStock() > 0;
            else if ("Unidades críticas (<= 5)".equals(stockFilter)) matchStock = p.getStock() > 0 && p.getStock() <= 5;
            else if ("Sin unidades (0)".equals(stockFilter)) matchStock = p.getStock() == 0;

            if (matchCat && matchJuego && matchQuery && matchStock) {
                filteredList.add(p);
                String estadoStock = p.isPreventa()
                        ? (p.getStock() == 0 ? "Sin unidades solicitadas" : "Unidades pedidas al proveedor") :
                        (p.getStock() == 0 ? "Agotado" : (p.getStock() <= 5 ? "Crítico" : "Disponible"));
                tableModel.addRow(new Object[]{
                        p.getIdProducto(),
                        p.getCodigoBarras() != null ? p.getCodigoBarras() : "-",
                        p.getNombre(),
                        Producto.getCategoriaVisible(p.getCategoria()),
                        p.getJuegoTcg(),
                        "$" + p.getPrecioUnitario(),
                        p.isPreventa() ? p.getStock() + " pedidas" : p.getStock(),
                        p.isPreventa() ? "Preventa" : "Venta normal",
                        p.getFechaSalida() != null ? p.getFechaSalida().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-",
                        estadoStock
                });
            }
        }
    }

    private void showProductoDialog(Producto p) {
        List<String> categories = db.getNombresCategoriasActivas();
        if (p == null && categories.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero registra o activa una categoría de producto.", "Sin categorías disponibles", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean isEdit = (p != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Editar producto" : "Registrar producto", true);
        dialog.setSize(520, 610);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtCode = new JTextField(isEdit ? p.getCodigoBarras() : "");
        JTextField txtNombre = new JTextField(isEdit ? p.getNombre() : "");
        JComboBox<String> cmbCat = new JComboBox<>(categories.toArray(new String[0]));
        if (isEdit) {
            String currentCategory = Producto.getCategoriaVisible(p.getCategoria());
            if (!categories.contains(currentCategory)) {
                cmbCat.addItem(currentCategory);
            }
            cmbCat.setSelectedItem(currentCategory);
        }

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
        JSpinner spinPorcentajeApartado = new JSpinner(new SpinnerNumberModel(
                isEdit ? p.getPorcentajeApartado().doubleValue() : db.getPorcentajeApartadoPredeterminado().doubleValue(),
                0.01, 100.0, 1.0));
        spinPorcentajeApartado.setEditor(new JSpinner.NumberEditor(spinPorcentajeApartado, "#0.##"));
        JTextField txtFechaSalida = new JTextField(isEdit && p.getFechaSalida() != null
            ? p.getFechaSalida().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
        txtFechaSalida.setEditable(false);
        JButton btnElegirFechaSalida = UITheme.createSecondaryButton("Elegir fecha...");
        JPanel fechaSalidaPanel = new JPanel(new BorderLayout(8, 0));
        fechaSalidaPanel.setOpaque(false);
        fechaSalidaPanel.add(txtFechaSalida, BorderLayout.CENTER);
        fechaSalidaPanel.add(btnElegirFechaSalida, BorderLayout.EAST);
        JSpinner spinStock = new JSpinner(new SpinnerNumberModel(isEdit ? p.getStock() : 1, 0, 9999, 1));
        JComboBox<String> cmbTipoVenta = new JComboBox<>(new String[]{"Venta normal", "Preventa"});
        if (isEdit) cmbTipoVenta.setSelectedItem(p.isPreventa() ? "Preventa" : "Venta normal");
        JLabel lblStock = new JLabel("Stock disponible:");
        JLabel lblFechaSalida = new JLabel("Fecha estimada del proveedor:");
        JLabel lblPorcentajeApartado = new JLabel("Porcentaje para apartado:");
        boolean esPreventaInicial = "Preventa".equals(cmbTipoVenta.getSelectedItem());
        final boolean[] previousPreventa = {esPreventaInicial};
        spinStock.setEnabled(true);
        lblStock.setText(esPreventaInicial ? "Unidades pedidas al proveedor:" : "Stock disponible:");
        txtFechaSalida.setEnabled(esPreventaInicial);
        btnElegirFechaSalida.setEnabled(esPreventaInicial);
        lblFechaSalida.setVisible(esPreventaInicial);
        fechaSalidaPanel.setVisible(esPreventaInicial);
        spinPorcentajeApartado.setVisible(esPreventaInicial);
        lblPorcentajeApartado.setVisible(esPreventaInicial);
        cmbTipoVenta.addActionListener(e -> {
            boolean isPreventa = "Preventa".equals(cmbTipoVenta.getSelectedItem());
            int unidades = ((Number) spinStock.getValue()).intValue();
            if (isPreventa != previousPreventa[0] && unidades > 0) {
                String mensaje = isPreventa
                        ? "La cantidad actual se registrará como unidades solicitadas al proveedor, no como stock disponible. Puedes ajustarla antes de guardar. ¿Deseas continuar?"
                        : "La cantidad solicitada al proveedor no es inventario disponible. Se borrará para que registres el stock real. ¿Deseas continuar?";
                int confirm = JOptionPane.showConfirmDialog(dialog, mensaje,
                        "Confirmar cambio", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    cmbTipoVenta.setSelectedItem(previousPreventa[0] ? "Preventa" : "Venta normal");
                    return;
                }
                if (!isPreventa) spinStock.setValue(0);
            }
            previousPreventa[0] = isPreventa;
            spinStock.setEnabled(true);
            txtFechaSalida.setEnabled(isPreventa);
            btnElegirFechaSalida.setEnabled(isPreventa);
            lblFechaSalida.setVisible(isPreventa);
            fechaSalidaPanel.setVisible(isPreventa);
            spinPorcentajeApartado.setVisible(isPreventa);
            lblPorcentajeApartado.setVisible(isPreventa);
            lblStock.setText(isPreventa ? "Unidades pedidas al proveedor:" : "Stock disponible:");
            form.revalidate();
            form.repaint();
        });
        btnElegirFechaSalida.addActionListener(e -> {
            LocalDate fechaActual = txtFechaSalida.getText().trim().isEmpty()
                    ? LocalDate.now() : LocalDate.parse(txtFechaSalida.getText().trim(),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
            LocalDate fechaSeleccionada = mostrarSelectorFecha(dialog, fechaActual);
            if (fechaSeleccionada != null) {
                txtFechaSalida.setText(fechaSeleccionada.format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }
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
        form.add(lblFechaSalida, gbc);
        gbc.gridx = 1;
        form.add(fechaSalidaPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        form.add(lblPorcentajeApartado, gbc);
        gbc.gridx = 1;
        form.add(spinPorcentajeApartado, gbc);

        gbc.gridx = 0; gbc.gridy = 9;
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
            int stockVal = (int) spinStock.getValue();
            String tipoVenta = isPreventa ? Producto.TIPO_PREVENTA : Producto.TIPO_VENTA_NORMAL;
            java.time.LocalDate fechaSalida = null;
            if (isPreventa) {
                try {
                    fechaSalida = java.time.LocalDate.parse(txtFechaSalida.getText().trim(),
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "La fecha de salida es obligatoria y debe usar el formato dd/MM/aaaa.", "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            try {
                if (isEdit) {
                    p.setCodigoBarras(txtCode.getText().trim());
                    p.setNombre(name);
                    p.setCategoria((String) cmbCat.getSelectedItem());
                    p.setJuegoTcg((String) cmbGame.getSelectedItem());
                    p.setPrecioUnitario(precio);
                    p.setPorcentajeApartado(BigDecimal.valueOf(((Number) spinPorcentajeApartado.getValue()).doubleValue()));
                    p.setStock(stockVal);
                    p.setTipoVenta(tipoVenta);
                    p.setFechaSalida(fechaSalida);
                    p.setDescripcion(txtDesc.getText().trim());
                    db.updateProducto(p);
                } else {
                    Producto nuevo = new Producto(0, txtCode.getText().trim(), name, txtDesc.getText().trim(),
                            (String) cmbCat.getSelectedItem(), (String) cmbGame.getSelectedItem(), precio, stockVal);
                    nuevo.setTipoVenta(tipoVenta);
                    nuevo.setPorcentajeApartado(BigDecimal.valueOf(((Number) spinPorcentajeApartado.getValue()).doubleValue()));
                    nuevo.setFechaSalida(fechaSalida);
                    db.addProducto(nuevo);
                }
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "No se pudo guardar el producto", JOptionPane.WARNING_MESSAGE);
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

    private void editSelectedProducto() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un producto para editar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showProductoDialog(filteredList.get(row));
    }

    private LocalDate mostrarSelectorFecha(Window owner, LocalDate fechaInicial) {
        JDialog calendario = new JDialog(owner, "Fecha estimada del proveedor", Dialog.ModalityType.APPLICATION_MODAL);
        calendario.setLayout(new BorderLayout(8, 8));
        calendario.setSize(360, 360);
        calendario.setLocationRelativeTo(owner);

        YearMonth[] mesMostrado = {YearMonth.from(fechaInicial)};
        LocalDate[] fechaSeleccionada = {fechaInicial};
        boolean[] confirmarSeleccion = {false};
        JPanel diasPanel = new JPanel(new GridLayout(0, 7, 4, 4));
        JLabel lblMes = new JLabel("", SwingConstants.CENTER);
        lblMes.setFont(UITheme.FONT_SUBTITLE);

        Runnable[] actualizarCalendario = new Runnable[1];
        actualizarCalendario[0] = () -> {
            lblMes.setText(mesMostrado[0].format(
                    DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("es", "MX"))));
            diasPanel.removeAll();
            for (String dia : new String[]{"L", "M", "X", "J", "V", "S", "D"}) {
                JLabel encabezado = new JLabel(dia, SwingConstants.CENTER);
                encabezado.setFont(UITheme.FONT_BOLD);
                diasPanel.add(encabezado);
            }

            int desplazamiento = mesMostrado[0].atDay(1).getDayOfWeek().getValue() - 1;
            for (int i = 0; i < desplazamiento; i++) diasPanel.add(new JLabel());
            for (int dia = 1; dia <= mesMostrado[0].lengthOfMonth(); dia++) {
                LocalDate fecha = mesMostrado[0].atDay(dia);
                JButton btnDia = new JButton(String.valueOf(dia));
                if (fecha.equals(fechaSeleccionada[0])) {
                    btnDia.setBackground(UITheme.COLOR_PRIMARY);
                    btnDia.setForeground(Color.WHITE);
                }
                btnDia.addActionListener(e -> {
                    fechaSeleccionada[0] = fecha;
                    actualizarCalendario[0].run();
                });
                diasPanel.add(btnDia);
            }
            diasPanel.revalidate();
            diasPanel.repaint();
        };

        JButton btnMesAnterior = UITheme.createSecondaryButton("<");
        btnMesAnterior.addActionListener(e -> {
            mesMostrado[0] = mesMostrado[0].minusMonths(1);
            actualizarCalendario[0].run();
        });
        JButton btnMesSiguiente = UITheme.createSecondaryButton(">");
        btnMesSiguiente.addActionListener(e -> {
            mesMostrado[0] = mesMostrado[0].plusMonths(1);
            actualizarCalendario[0].run();
        });

        JPanel navegacion = new JPanel(new BorderLayout());
        navegacion.add(btnMesAnterior, BorderLayout.WEST);
        navegacion.add(lblMes, BorderLayout.CENTER);
        navegacion.add(btnMesSiguiente, BorderLayout.EAST);
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(new EmptyBorder(12, 12, 4, 12));
        contenido.add(navegacion, BorderLayout.NORTH);
        contenido.add(diasPanel, BorderLayout.CENTER);
        calendario.add(contenido, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> calendario.dispose());
        JButton btnSeleccionar = UITheme.createPrimaryButton("Seleccionar");
        btnSeleccionar.addActionListener(e -> {
            confirmarSeleccion[0] = true;
            calendario.dispose();
        });
        acciones.add(btnCancelar);
        acciones.add(btnSeleccionar);
        calendario.add(acciones, BorderLayout.SOUTH);

        actualizarCalendario[0].run();
        calendario.setVisible(true);
        return confirmarSeleccion[0] ? fechaSeleccionada[0] : null;
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
