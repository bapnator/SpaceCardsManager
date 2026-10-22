package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UsuariosPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbRol = new JComboBox<>(new String[]{"Todos los roles", "Administrador", "Vendedor", "Organizador_Torneos"});
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Usuario> filteredList = new ArrayList<>();
    private final Runnable onDataChanged;

    public UsuariosPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Personal y Usuarios del Sistema");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Gestión de administradores, vendedores y organizadores de torneos");
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

        JButton btnNuevo = UITheme.createPrimaryButton("Registrar Usuario");
        btnNuevo.addActionListener(e -> showUsuarioDialog(null));

        JButton btnEditar = UITheme.createSecondaryButton("Editar");
        btnEditar.addActionListener(e -> editSelectedUsuario());

        JButton btnToggle = UITheme.createSecondaryButton("Activar / Desactivar");
        btnToggle.addActionListener(e -> toggleActivo());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnEditar);
        actionButtons.add(btnToggle);
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
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar usuario por nombre o turno...");
        txtBuscar.addCaretListener(e -> refreshTable());
        filterBar.add(txtBuscar, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3;
        cmbRol.addActionListener(e -> refreshTable());
        filterBar.add(cmbRol, gbc);

        cardPanel.add(filterBar, BorderLayout.NORTH);

        // Tabla
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Nombre", "Apellido", "Rol Asignado", "Horario / Turno", "Estado"
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
        String rolFilter = (String) cmbRol.getSelectedItem();

        tableModel.setRowCount(0);
        filteredList.clear();

        for (Usuario u : db.getUsuarios()) {
            boolean matchRol = rolFilter == null || "Todos los roles".equals(rolFilter) || u.getRol().equalsIgnoreCase(rolFilter);
            boolean matchQuery = query.isEmpty() ||
                    u.getNombre().toLowerCase().contains(query) ||
                    u.getApellido().toLowerCase().contains(query) ||
                    u.getHorario().toLowerCase().contains(query);

            if (matchRol && matchQuery) {
                filteredList.add(u);
                tableModel.addRow(new Object[]{
                        u.getIdUsuario(),
                        u.getNombre(),
                        u.getApellido(),
                        u.getRol(),
                        u.getHorario(),
                        u.isActivo() ? "Activo" : "Inactivo"
                });
            }
        }
    }

    private void showUsuarioDialog(Usuario u) {
        boolean isEdit = (u != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Editar Usuario" : "PantallaCrearRegistroUsuario", true);
        dialog.setSize(440, 380);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtNombre = new JTextField(isEdit ? u.getNombre() : "");
        JTextField txtApellido = new JTextField(isEdit ? u.getApellido() : "");
        JComboBox<String> cmbRoles = new JComboBox<>(new String[]{"Administrador", "Vendedor", "Organizador_Torneos"});
        if (isEdit) cmbRoles.setSelectedItem(u.getRol());

        JTextField txtHorario = new JTextField(isEdit ? u.getHorario() : "Turno Matutino (08:00 - 16:00)");
        JCheckBox chkActivo = new JCheckBox("Usuario Activo", isEdit ? u.isActivo() : true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Apellido:*"), gbc);
        gbc.gridx = 1;
        form.add(txtApellido, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Rol:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbRoles, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Horario / Turno:*"), gbc);
        gbc.gridx = 1;
        form.add(txtHorario, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        form.add(chkActivo, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar");
        btnGuardar.addActionListener(e -> {
            String nom = txtNombre.getText().trim();
            String ape = txtApellido.getText().trim();
            String hor = txtHorario.getText().trim();

            if (nom.isEmpty() || ape.isEmpty() || hor.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Todos los campos son obligatorios.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (isEdit) {
                u.setNombre(nom);
                u.setApellido(ape);
                u.setRol((String) cmbRoles.getSelectedItem());
                u.setHorario(hor);
                u.setActivo(chkActivo.isSelected());
                db.updateUsuario(u);
            } else {
                String rolSeleccionado = (String) cmbRoles.getSelectedItem();
                Usuario nuevo = new Usuario(0, nom, ape, rolSeleccionado, hor, chkActivo.isSelected());
                db.addUsuario(nuevo);

                if (SessionManager.getInstance().isAshBridgeUser() && "Administrador".equals(rolSeleccionado)) {
                    JOptionPane.showMessageDialog(dialog,
                            "¡Primer Administrador Registrado con Éxito!\n\n" +
                            "Nombre: " + nuevo.getNombreCompleto() + "\n" +
                            "Rol: Administrador\n\n" +
                            "A partir de este momento, el sistema ya cuenta con un Administrador oficial.\n" +
                            "Al cerrar esta sesión o cambiar de usuario, la cuenta puente 'Ash' quedará bloqueada.",
                            "Administrador Configurado", JOptionPane.INFORMATION_MESSAGE);
                }
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

    private void editSelectedUsuario() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showUsuarioDialog(filteredList.get(row));
    }

    private void toggleActivo() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Usuario u = filteredList.get(row);
        u.setActivo(!u.isActivo());
        db.updateUsuario(u);
        refreshTable();
        if (onDataChanged != null) onDataChanged.run();
    }
}
