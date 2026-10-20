package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ClientesPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Cliente> filteredList = new ArrayList<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Runnable onDataChanged;

    public ClientesPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Directorio de Clientes");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Consulta y registro de clientes para compras, preventas y torneos");
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

        JButton btnNuevo = UITheme.createPrimaryButton("Registrar Cliente");
        btnNuevo.addActionListener(e -> showClienteDialog(null));

        JButton btnEditar = UITheme.createSecondaryButton("Editar");
        btnEditar.addActionListener(e -> editSelectedCliente());

        JButton btnEliminar = UITheme.createSecondaryButton("Eliminar");
        btnEliminar.addActionListener(e -> deleteSelectedCliente());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnEditar);
        actionButtons.add(btnEliminar);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Card
        JPanel cardPanel = UITheme.createCardPanel();
        cardPanel.setLayout(new BorderLayout(12, 12));

        // Buscador
        JPanel searchBar = new JPanel(new BorderLayout(8, 8));
        searchBar.setOpaque(false);
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar cliente por nombre, apellido, correo o teléfono...");
        txtBuscar.addCaretListener(e -> refreshTable());
        searchBar.add(txtBuscar, BorderLayout.CENTER);
        cardPanel.add(searchBar, BorderLayout.NORTH);

        // Tabla
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Nombre", "Apellido", "Correo Electrónico", "Teléfono", "Fecha de Registro"
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
        tableModel.setRowCount(0);
        filteredList.clear();

        for (Cliente c : db.getClientes()) {
            boolean match = query.isEmpty() ||
                    c.getNombre().toLowerCase().contains(query) ||
                    c.getApellido().toLowerCase().contains(query) ||
                    c.getCorreoElectronico().toLowerCase().contains(query) ||
                    c.getTelefono().contains(query);

            if (match) {
                filteredList.add(c);
                tableModel.addRow(new Object[]{
                        c.getIdCliente(),
                        c.getNombre(),
                        c.getApellido(),
                        c.getCorreoElectronico(),
                        c.getTelefono(),
                        c.getFechaRegistro() != null ? c.getFechaRegistro().format(fmt) : "-"
                });
            }
        }
    }

    private void showClienteDialog(Cliente c) {
        boolean isEdit = (c != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Editar Cliente" : "PantallaCrearRegistroCliente", true);
        dialog.setSize(420, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtNombre = new JTextField(isEdit ? c.getNombre() : "");
        JTextField txtApellido = new JTextField(isEdit ? c.getApellido() : "");
        JTextField txtEmail = new JTextField(isEdit ? c.getCorreoElectronico() : "");
        JTextField txtTel = new JTextField(isEdit ? c.getTelefono() : "");

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Apellido:*"), gbc);
        gbc.gridx = 1;
        form.add(txtApellido, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Correo Electrónico:*"), gbc);
        gbc.gridx = 1;
        form.add(txtEmail, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Teléfono:*"), gbc);
        gbc.gridx = 1;
        form.add(txtTel, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar");
        btnGuardar.addActionListener(e -> {
            String nom = txtNombre.getText().trim();
            String ape = txtApellido.getText().trim();
            String email = txtEmail.getText().trim();
            String tel = txtTel.getText().trim();

            if (nom.isEmpty() || ape.isEmpty() || email.isEmpty() || tel.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Todos los campos con asterisco (*) son obligatorios.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!email.contains("@") || !email.contains(".")) {
                JOptionPane.showMessageDialog(dialog, "Por favor introduce un correo electrónico válido.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (isEdit) {
                c.setNombre(nom);
                c.setApellido(ape);
                c.setCorreoElectronico(email);
                c.setTelefono(tel);
                db.updateCliente(c);
            } else {
                Cliente nuevo = new Cliente(0, nom, ape, email, tel);
                db.addCliente(nuevo);
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

    private void editSelectedCliente() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un cliente de la lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showClienteDialog(filteredList.get(row));
    }

    private void deleteSelectedCliente() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un cliente de la lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Cliente c = filteredList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de eliminar al cliente '" + c.getNombreCompleto() + "'?",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.deleteCliente(c.getIdCliente());
            refreshTable();
            if (onDataChanged != null) onDataChanged.run();
        }
    }
}
