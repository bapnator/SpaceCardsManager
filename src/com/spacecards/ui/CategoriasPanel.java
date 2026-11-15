package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Categoria;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CategoriasPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{"Todos los estados", "Solo Activas", "Solo Inactivas"});
    private final JLabel lblTotal = new JLabel("0");
    private final JLabel lblActivas = new JLabel("0");
    private final JLabel lblProductos = new JLabel("0");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final List<Categoria> filteredList = new ArrayList<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Runnable onDataChanged;

    public CategoriasPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        setLayout(new BorderLayout(16, 16));
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Categorías de productos");
        title.setFont(UITheme.FONT_TITLE);
        JLabel subtitle = new JLabel("Administra las categorías disponibles para el catálogo y punto de venta");
        subtitle.setFont(UITheme.FONT_REGULAR);
        subtitle.setForeground(UITheme.COLOR_TEXT_MUTED);
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton add = UITheme.createPrimaryButton("Registrar categoría");
        add.addActionListener(e -> showCategoriaDialog(null));
        JButton edit = UITheme.createSecondaryButton("Editar");
        edit.addActionListener(e -> editSelected());
        JButton toggle = UITheme.createSecondaryButton("Activar / Desactivar");
        toggle.addActionListener(e -> toggleSelected());
        JButton delete = UITheme.createSecondaryButton("Eliminar");
        delete.addActionListener(e -> deleteSelected());
        actions.add(add);
        actions.add(edit);
        actions.add(toggle);
        actions.add(delete);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(14, 14));
        center.setOpaque(false);
        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0));
        stats.setOpaque(false);
        stats.add(createKpiCard("Total de categorías", lblTotal, UITheme.COLOR_PRIMARY));
        stats.add(createKpiCard("Categorías activas", lblActivas, UITheme.COLOR_SUCCESS));
        stats.add(createKpiCard("Productos asociados", lblProductos, UITheme.COLOR_ACCENT));
        center.add(stats, BorderLayout.NORTH);

        JPanel card = UITheme.createCardPanel();
        card.setLayout(new BorderLayout(12, 12));
        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.7;
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar por categoría o descripción...");
        txtBuscar.addCaretListener(e -> refreshTable());
        filters.add(txtBuscar, gbc);
        gbc.gridx = 1; gbc.weightx = 0.3;
        cmbEstado.addActionListener(e -> refreshTable());
        filters.add(cmbEstado, gbc);
        card.add(filters, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Categoría", "Productos", "Estado", "Fecha de registro", "Descripción"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        UITheme.formatTable(table);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(card, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        refreshTable();
    }

    private JPanel createKpiCard(String title, JLabel value, Color accent) {
        JPanel card = UITheme.createCardPanel();
        card.setLayout(new BorderLayout(4, 4));
        JLabel label = new JLabel(title);
        label.setFont(UITheme.FONT_REGULAR);
        label.setForeground(UITheme.COLOR_TEXT_MUTED);
        value.setFont(UITheme.FONT_BIG_NUMBER);
        value.setForeground(accent);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    public void refreshTable() {
        String query = txtBuscar.getText().trim().toLowerCase();
        String state = (String) cmbEstado.getSelectedItem();
        List<Categoria> all = db.getCategorias();
        tableModel.setRowCount(0);
        filteredList.clear();
        int activeCount = 0;
        int productCount = 0;
        for (Categoria categoria : all) {
            int products = db.countProductosByCategoria(categoria.getNombre());
            productCount += products;
            if (categoria.isActivo()) activeCount++;
            boolean stateMatches = !"Solo Activas".equals(state) || categoria.isActivo();
            if ("Solo Inactivas".equals(state)) stateMatches = !categoria.isActivo();
            boolean textMatches = query.isEmpty() || categoria.getNombre().toLowerCase().contains(query) ||
                    (categoria.getDescripcion() != null && categoria.getDescripcion().toLowerCase().contains(query));
            if (stateMatches && textMatches) {
                filteredList.add(categoria);
                tableModel.addRow(new Object[]{
                        categoria.getIdCategoria(), categoria.getNombre(), products,
                        categoria.isActivo() ? "Activa" : "Inactiva",
                        categoria.getFechaRegistro() != null ? categoria.getFechaRegistro().format(fmt) : "-",
                        categoria.getDescripcion() != null ? categoria.getDescripcion() : ""
                });
            }
        }
        lblTotal.setText(String.valueOf(all.size()));
        lblActivas.setText(activeCount + " de " + all.size());
        lblProductos.setText(String.valueOf(productCount));
    }

    private void showCategoriaDialog(Categoria categoria) {
        boolean editing = categoria != null;
        String oldName = editing ? categoria.getNombre() : "";
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), editing ? "Editar categoría" : "Registrar categoría", true);
        dialog.setSize(460, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField name = new JTextField(editing ? categoria.getNombre() : "");
        JTextArea description = new JTextArea(editing && categoria.getDescripcion() != null ? categoria.getDescripcion() : "", 3, 20);
        description.setLineWrap(true);
        JCheckBox active = new JCheckBox("Categoría activa para nuevos productos", !editing || categoria.isActivo());
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(name, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Descripción:"), gbc);
        gbc.gridx = 1;
        form.add(new JScrollPane(description), gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        form.add(active, gbc);
        dialog.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = UITheme.createSecondaryButton("Cancelar");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = UITheme.createPrimaryButton("Guardar");
        save.addActionListener(e -> {
            String newName = name.getText().trim();
            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "El nombre de la categoría es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if (editing) {
                    int linkedProducts = db.countProductosByCategoria(oldName);
                    if (!oldName.equals(newName) && linkedProducts > 0) {
                        int confirm = JOptionPane.showConfirmDialog(dialog,
                                "Se actualizarán los " + linkedProducts + " productos asociados. ¿Deseas continuar?",
                                "Confirmar cambio de nombre", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (confirm != JOptionPane.YES_OPTION) return;
                    }
                    categoria.setNombre(newName);
                    categoria.setDescripcion(description.getText().trim());
                    categoria.setActivo(active.isSelected());
                    db.updateCategoria(categoria, oldName);
                } else {
                    Categoria newCategory = new Categoria();
                    newCategory.setNombre(newName);
                    newCategory.setDescripcion(description.getText().trim());
                    newCategory.setActivo(active.isSelected());
                    db.addCategoria(newCategory);
                }
                dialog.dispose();
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error al guardar categoría", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona una categoría de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showCategoriaDialog(filteredList.get(row));
    }

    private void toggleSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona una categoría de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Categoria categoria = filteredList.get(row);
        String oldName = categoria.getNombre();
        categoria.setActivo(!categoria.isActivo());
        try {
            db.updateCategoria(categoria, oldName);
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredList.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona una categoría para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Categoria categoria = filteredList.get(row);
        int products = db.countProductosByCategoria(categoria.getNombre());
        if (products > 0) {
            JOptionPane.showMessageDialog(this,
                    "La categoría está asociada a " + products + " producto(s). Desactívala para ocultarla en nuevos registros.",
                    "Categoría en uso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Eliminar permanentemente la categoría '" + categoria.getNombre() + "'?",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                db.deleteCategoria(categoria.getIdCategoria(), categoria.getNombre());
                refreshTable();
                if (onDataChanged != null) onDataChanged.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}