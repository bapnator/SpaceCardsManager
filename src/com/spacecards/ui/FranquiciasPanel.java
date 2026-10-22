package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.dao.DatabaseService.Franquicia;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel para la administración de Franquicias / Juegos TCG jugables.
 * Permite registrar, editar, activar/desactivar y eliminar franquicias,
 * manteniendo la coherencia con los productos y torneos registrados.
 */
public class FranquiciasPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{
            "Todos los estados", "Solo Activas", "Solo Inactivas"
    });

    private final JLabel lblTotal = new JLabel("0");
    private final JLabel lblActivas = new JLabel("0");
    private final JLabel lblProds = new JLabel("0");
    private final JLabel lblTorneos = new JLabel("0");

    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Franquicia> filteredList = new ArrayList<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Runnable onDataChanged;

    public FranquiciasPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Franquicias TCG Jugables");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Gestión de juegos de cartas coleccionables disponibles en la tienda (Pokémon, Magic, Lorcana, etc.)");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        // Botones superiores de acción
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionButtons.setOpaque(false);

        JButton btnNuevo = UITheme.createPrimaryButton("Registrar Franquicia");
        btnNuevo.addActionListener(e -> showFranquiciaDialog(null));

        JButton btnEditar = UITheme.createSecondaryButton("Editar");
        btnEditar.addActionListener(e -> editSelectedFranquicia());

        JButton btnToggle = UITheme.createSecondaryButton("Activar / Desactivar");
        btnToggle.addActionListener(e -> toggleActivo());

        JButton btnEliminar = UITheme.createSecondaryButton("Eliminar");
        btnEliminar.addActionListener(e -> deleteSelectedFranquicia());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnEditar);
        actionButtons.add(btnToggle);
        actionButtons.add(btnEliminar);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center Container
        JPanel centerPanel = new JPanel(new BorderLayout(14, 14));
        centerPanel.setOpaque(false);

        // Tarjetas KPI de Resumen
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 14, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(createKpiCard("Total Franquicias", lblTotal, UITheme.COLOR_PRIMARY));
        statsGrid.add(createKpiCard("Franquicias Activas", lblActivas, UITheme.COLOR_SUCCESS));
        statsGrid.add(createKpiCard("Productos Asociados", lblProds, UITheme.COLOR_ACCENT));
        statsGrid.add(createKpiCard("Torneos Vinculados", lblTorneos, new Color(168, 85, 247)));
        centerPanel.add(statsGrid, BorderLayout.NORTH);

        // Card Contenedor con Filtros y Tabla
        JPanel cardPanel = UITheme.createCardPanel();
        cardPanel.setLayout(new BorderLayout(12, 12));

        // Barra de Filtros
        JPanel filterBar = new JPanel(new GridBagLayout());
        filterBar.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.7;
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar por nombre de franquicia, fabricante o descripción...");
        txtBuscar.addCaretListener(e -> refreshTable());
        filterBar.add(txtBuscar, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3;
        cmbEstado.addActionListener(e -> refreshTable());
        filterBar.add(cmbEstado, gbc);

        cardPanel.add(filterBar, BorderLayout.NORTH);

        // Tabla de Franquicias
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Nombre de Franquicia", "Fabricante / Empresa", "Productos", "Torneos", "Estado", "Fecha Registro", "Descripción"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        UITheme.formatTable(table);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedFranquicia();
            }
        });
        cardPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        centerPanel.add(cardPanel, BorderLayout.CENTER);
        this.add(centerPanel, BorderLayout.CENTER);

        refreshTable();
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

    public void refreshTable() {
        String query = txtBuscar.getText().trim().toLowerCase();
        String estadoFilter = (String) cmbEstado.getSelectedItem();

        tableModel.setRowCount(0);
        filteredList.clear();

        List<Franquicia> all = db.getFranquicias();
        int totalProds = 0;
        int totalTorneos = 0;
        int countActivas = 0;

        for (Franquicia f : all) {
            int pCount = db.countProductosByFranquicia(f.getNombre());
            int tCount = db.countTorneosByFranquicia(f.getNombre());
            totalProds += pCount;
            totalTorneos += tCount;
            if (f.isActivo()) countActivas++;

            boolean matchEstado = true;
            if ("Solo Activas".equals(estadoFilter)) matchEstado = f.isActivo();
            else if ("Solo Inactivas".equals(estadoFilter)) matchEstado = !f.isActivo();

            boolean matchQuery = query.isEmpty() ||
                    f.getNombre().toLowerCase().contains(query) ||
                    (f.getFabricante() != null && f.getFabricante().toLowerCase().contains(query)) ||
                    (f.getDescripcion() != null && f.getDescripcion().toLowerCase().contains(query));

            if (matchEstado && matchQuery) {
                filteredList.add(f);
                tableModel.addRow(new Object[]{
                        f.getIdFranquicia(),
                        f.getNombre(),
                        (f.getFabricante() != null && !f.getFabricante().isEmpty()) ? f.getFabricante() : "-",
                        pCount + " producto(s)",
                        tCount + " torneo(s)",
                        f.isActivo() ? "Activa" : "Inactiva",
                        f.getFechaRegistro() != null ? f.getFechaRegistro().format(fmt) : "-",
                        f.getDescripcion() != null ? f.getDescripcion() : ""
                });
            }
        }

        lblTotal.setText(String.valueOf(all.size()));
        lblActivas.setText(countActivas + " de " + all.size());
        lblProds.setText(String.valueOf(totalProds));
        lblTorneos.setText(String.valueOf(totalTorneos));
    }

    private void showFranquiciaDialog(Franquicia f) {
        boolean isEdit = (f != null);
        String oldName = isEdit ? f.getNombre() : "";

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Editar Franquicia TCG" : "Registrar Nueva Franquicia TCG", true);
        dialog.setSize(480, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtNombre = new JTextField(isEdit ? f.getNombre() : "");
        JTextField txtFabricante = new JTextField(isEdit ? f.getFabricante() : "");
        JTextArea txtDesc = new JTextArea(isEdit ? f.getDescripcion() : "", 3, 20);
        txtDesc.setLineWrap(true);
        JCheckBox chkActivo = new JCheckBox("Franquicia Activa para nuevos productos y torneos", isEdit ? f.isActivo() : true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre Franquicia:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Fabricante / Editorial:"), gbc);
        gbc.gridx = 1;
        form.add(txtFabricante, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Descripción:"), gbc);
        gbc.gridx = 1;
        form.add(new JScrollPane(txtDesc), gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(chkActivo, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar");
        btnGuardar.addActionListener(e -> {
            String nom = txtNombre.getText().trim();
            String fab = txtFabricante.getText().trim();
            String desc = txtDesc.getText().trim();

            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "El nombre de la franquicia es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (isEdit) {
                // Si el nombre cambió y hay productos o torneos asociados, confirmar actualización
                if (!nom.equalsIgnoreCase(oldName)) {
                    int prods = db.countProductosByFranquicia(oldName);
                    int torneos = db.countTorneosByFranquicia(oldName);
                    if (prods > 0 || torneos > 0) {
                        int conf = JOptionPane.showConfirmDialog(dialog,
                                "Has modificado el nombre de '" + oldName + "' a '" + nom + "'.\n\n" +
                                "Se actualizarán automáticamente los " + prods + " producto(s) y " + torneos + " torneo(s) vinculados.\n" +
                                "¿Deseas continuar?",
                                "Confirmar Renombrado", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (conf != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                }

                f.setNombre(nom);
                f.setFabricante(fab);
                f.setDescripcion(desc);
                f.setActivo(chkActivo.isSelected());
                try {
                    db.updateFranquicia(f, oldName);
                    dialog.dispose();
                    refreshTable();
                    if (onDataChanged != null) onDataChanged.run();
                    JOptionPane.showMessageDialog(this, "Franquicia '" + nom + "' actualizada correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error al Actualizar", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                Franquicia nueva = new Franquicia(0, nom, fab, desc, chkActivo.isSelected());
                try {
                    db.addFranquicia(nueva);
                    dialog.dispose();
                    refreshTable();
                    if (onDataChanged != null) onDataChanged.run();
                    JOptionPane.showMessageDialog(this, "Franquicia '" + nom + "' registrada exitosamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error al Registrar", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton btnCancel = UITheme.createSecondaryButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnGuardar);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void editSelectedFranquicia() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Por favor selecciona una franquicia de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showFranquiciaDialog(filteredList.get(row));
    }

    private void toggleActivo() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Por favor selecciona una franquicia de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Franquicia f = filteredList.get(row);
        f.setActivo(!f.isActivo());
        try {
            db.updateFranquicia(f, f.getNombre());
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
            JOptionPane.showMessageDialog(this,
                    "La franquicia '" + f.getNombre() + "' ahora está " + (f.isActivo() ? "ACTIVA" : "INACTIVA") + ".",
                    "Estado Actualizado", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedFranquicia() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Por favor selecciona una franquicia para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Franquicia f = filteredList.get(row);
        int prods = db.countProductosByFranquicia(f.getNombre());
        int torneos = db.countTorneosByFranquicia(f.getNombre());

        if (prods > 0 || torneos > 0) {
            JOptionPane.showMessageDialog(this,
                    "No es posible eliminar la franquicia '" + f.getNombre() + "':\n\n" +
                    "• Tiene " + prods + " producto(s) en catálogo.\n" +
                    "• Tiene " + torneos + " torneo(s) registrados.\n\n" +
                    "Para mantener la integridad histórica de la base de datos, puedes usar la opción 'Activar / Desactivar' para ocultarla de nuevas selecciones.",
                    "Franquicia en Uso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de que deseas eliminar permanentemente la franquicia '" + f.getNombre() + "'?\n" +
                "Esta acción no se puede deshacer.",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                db.deleteFranquicia(f.getIdFranquicia(), f.getNombre());
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
                JOptionPane.showMessageDialog(this, "Franquicia '" + f.getNombre() + "' eliminada correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar franquicia: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
