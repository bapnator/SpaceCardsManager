package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Cliente;
import com.spacecards.model.ParticipanteTorneo;
import com.spacecards.model.Torneo;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TorneosPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final DefaultTableModel torneosModel;
    private final JTable tableTorneos;
    private final DefaultTableModel partModel;
    private final JTable tablePart;
    private final JLabel lblSelectedTorneoTitle = new JLabel("Selecciona un torneo para ver sus participantes");

    private List<Torneo> listTorneos = new ArrayList<>();
    private List<ParticipanteTorneo> currentPartList = new ArrayList<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Runnable onDataChanged;

    public TorneosPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Torneos & Eventos Competitivos TCG");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Organización de ligas, control de cupos y registro de jugadores");
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

        JButton btnNuevo = UITheme.createPrimaryButton("➕ Crear Torneo");
        btnNuevo.addActionListener(e -> showCrearTorneoDialog());

        JButton btnInscribir = UITheme.createAccentButton("👥 Inscribir Jugador");
        btnInscribir.addActionListener(e -> showInscribirParticipanteDialog());

        JButton btnCambiarEstado = UITheme.createSecondaryButton("⚡ Cambiar Estado");
        btnCambiarEstado.addActionListener(e -> cambiarEstadoTorneo());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnInscribir);
        actionButtons.add(btnCambiarEstado);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Split layout (Top: Torneos, Bottom: Participantes)
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 0, 16));
        contentPanel.setOpaque(false);

        // Card Torneos
        JPanel cardTorneos = UITheme.createCardPanel();
        cardTorneos.setLayout(new BorderLayout(8, 8));
        JLabel lblTorneosTitle = new JLabel("Torneos Registrados (PantallaConsultarTorneo)");
        lblTorneosTitle.setFont(UITheme.FONT_SUBTITLE);
        cardTorneos.add(lblTorneosTitle, BorderLayout.NORTH);

        torneosModel = new DefaultTableModel(new String[]{
                "ID", "Nombre del Torneo", "Juego TCG", "Organizador", "Fecha y Hora", "Costo ($)", "Cupos (Inscritos / Máx)", "Estado"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tableTorneos = new JTable(torneosModel);
        UITheme.formatTable(tableTorneos);
        tableTorneos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshParticipantes();
        });
        cardTorneos.add(new JScrollPane(tableTorneos), BorderLayout.CENTER);
        contentPanel.add(cardTorneos);

        // Card Participantes
        JPanel cardPart = UITheme.createCardPanel();
        cardPart.setLayout(new BorderLayout(8, 8));
        lblSelectedTorneoTitle.setFont(UITheme.FONT_SUBTITLE);

        JPanel partHeader = new JPanel(new BorderLayout());
        partHeader.setOpaque(false);
        partHeader.add(lblSelectedTorneoTitle, BorderLayout.WEST);

        JButton btnEliminarPart = UITheme.createSecondaryButton("Cancelar Inscripción");
        btnEliminarPart.addActionListener(e -> cancelarInscripcionSeleccionada());
        partHeader.add(btnEliminarPart, BorderLayout.EAST);

        cardPart.add(partHeader, BorderLayout.NORTH);

        partModel = new DefaultTableModel(new String[]{
                "ID Inscripción", "Jugador / Cliente", "Correo", "Teléfono", "POP ID / Player ID", "Pago Cuota", "Fecha Registro"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tablePart = new JTable(partModel);
        UITheme.formatTable(tablePart);
        cardPart.add(new JScrollPane(tablePart), BorderLayout.CENTER);
        contentPanel.add(cardPart);

        this.add(contentPanel, BorderLayout.CENTER);

        refreshTorneos();
    }

    public void refreshTorneos() {
        torneosModel.setRowCount(0);
        listTorneos = db.getTorneos();

        for (Torneo t : listTorneos) {
            String orgName = t.getOrganizador() != null ? t.getOrganizador().getNombreCompleto() : "Sin asignar";
            torneosModel.addRow(new Object[]{
                    "#" + t.getIdTorneo(),
                    t.getNombreTorneo(),
                    t.getJuegoTcg(),
                    orgName,
                    t.getFechaEvento().format(fmt),
                    "$" + t.getCostoInscripcion(),
                    t.getParticipantesInscritos() + " / " + t.getCupoMaximo(),
                    t.getEstado()
            });
        }

        if (tableTorneos.getRowCount() > 0 && tableTorneos.getSelectedRow() < 0) {
            tableTorneos.setRowSelectionInterval(0, 0);
        }
        refreshParticipantes();
    }

    private void refreshParticipantes() {
        int row = tableTorneos.getSelectedRow();
        partModel.setRowCount(0);
        currentPartList.clear();

        if (row < 0 || row >= listTorneos.size()) {
            lblSelectedTorneoTitle.setText("Selecciona un torneo para ver sus participantes");
            return;
        }

        Torneo t = listTorneos.get(row);
        lblSelectedTorneoTitle.setText("🎮 Participantes inscritos en: " + t.getNombreTorneo() +
                " (" + t.getParticipantesInscritos() + "/" + t.getCupoMaximo() + " jugadores)");

        currentPartList = db.getParticipantesByTorneo(t.getIdTorneo());
        for (ParticipanteTorneo pt : currentPartList) {
            Cliente c = pt.getCliente();
            partModel.addRow(new Object[]{
                    "#" + pt.getIdInscripcion(),
                    c != null ? c.getNombreCompleto() : "Cliente #" + pt.getIdCliente(),
                    c != null ? c.getCorreoElectronico() : "-",
                    c != null ? c.getTelefono() : "-",
                    pt.getIdJugadorTcg() != null ? pt.getIdJugadorTcg() : "Sin ID TCG",
                    pt.isPagoConfirmado() ? "Confirmado" : "Pendiente",
                    pt.getFechaInscripcion().format(fmt)
            });
        }
    }

    private void cancelarInscripcionSeleccionada() {
        int row = tablePart.getSelectedRow();
        if (row < 0 || row >= currentPartList.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona un participante de la tabla para cancelar su inscripción.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ParticipanteTorneo pt = currentPartList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Deseas cancelar la inscripción de " + (pt.getCliente() != null ? pt.getCliente().getNombreCompleto() : "este jugador") + "?",
                "Confirmar Cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            db.eliminarParticipante(pt.getIdInscripcion());
            refreshTorneos();
            if (onDataChanged != null) onDataChanged.run();
        }
    }

    private void showCrearTorneoDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Registrar Torneo TCG", true);
        dialog.setSize(460, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtNombre = new JTextField();
        JComboBox<String> cmbJuego = new JComboBox<>(new String[]{"Pokémon", "Magic", "Yu-Gi-Oh!", "Lorcana"});
        JComboBox<Usuario> cmbOrg = new JComboBox<>();
        for (Usuario u : db.getUsuarios()) {
            if (u.isActivo() && ("Organizador_Torneos".equals(u.getRol()) || "Administrador".equals(u.getRol()))) {
                cmbOrg.addItem(u);
            }
        }
        if (cmbOrg.getItemCount() == 0) {
            for (Usuario u : db.getUsuarios()) cmbOrg.addItem(u);
        }

        JTextField txtCosto = new JTextField("15.00");
        JSpinner spinCupo = new JSpinner(new SpinnerNumberModel(16, 2, 128, 2));
        JTextField txtDias = new JTextField("7"); // En cuántos días

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre Torneo:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Juego TCG:"), gbc);
        gbc.gridx = 1;
        form.add(cmbJuego, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Organizador:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbOrg, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Costo Inscripción ($):*"), gbc);
        gbc.gridx = 1;
        form.add(txtCosto, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("Cupo Máximo:*"), gbc);
        gbc.gridx = 1;
        form.add(spinCupo, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Fecha Evento (en días):"), gbc);
        gbc.gridx = 1;
        form.add(txtDias, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Registrar Torneo");
        btnGuardar.addActionListener(e -> {
            String nom = txtNombre.getText().trim();
            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "El nombre del torneo es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal costo;
            int dias;
            try {
                costo = new BigDecimal(txtCosto.getText().trim());
                dias = Integer.parseInt(txtDias.getText().trim());
                if (costo.compareTo(BigDecimal.ZERO) < 0 || dias <= 0) throw new Exception();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Por favor introduce valores válidos para costo y días.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Usuario org = (Usuario) cmbOrg.getSelectedItem();
            if (org == null) {
                JOptionPane.showMessageDialog(dialog, "Debes registrar y seleccionar al menos un usuario organizador.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int cupo = (int) spinCupo.getValue();

            Torneo nuevo = new Torneo(0, org, nom, (String) cmbJuego.getSelectedItem(),
                    LocalDateTime.now().plusDays(dias).withHour(16).withMinute(0), costo, cupo, "Programado");
            db.addTorneo(nuevo);

            dialog.dispose();
            refreshTorneos();
            if (onDataChanged != null) onDataChanged.run();
        });

        JButton btnCancel = UITheme.createSecondaryButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnGuardar);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showInscribirParticipanteDialog() {
        int row = tableTorneos.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona primero el torneo donde deseas inscribir al participante.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Torneo torneo = listTorneos.get(row);
        if (torneo.getCuposDisponibles() <= 0) {
            JOptionPane.showMessageDialog(this, "El torneo ya alcanzó su cupo máximo (" + torneo.getCupoMaximo() + " jugadores).", "Cupo Lleno", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "PantallaCrearRegistroParticipante", true);
        dialog.setSize(440, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<Cliente> cmbCliente = new JComboBox<>();
        for (Cliente c : db.getClientes()) cmbCliente.addItem(c);

        JTextField txtPlayerId = new JTextField("POP-");
        JCheckBox chkPago = new JCheckBox("Confirmar pago de inscripción ($" + torneo.getCostoInscripcion() + ")", true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Torneo:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(new JLabel("<html><b>" + torneo.getNombreTorneo() + "</b></html>"), gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Cliente / Jugador:*"), gbc);
        gbc.gridx = 1;
        form.add(cmbCliente, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("ID Oficial TCG (POP):"), gbc);
        gbc.gridx = 1;
        form.add(txtPlayerId, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(chkPago, gbc);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnInscribir = UITheme.createPrimaryButton("Inscribir Participante");
        btnInscribir.addActionListener(e -> {
            Cliente cli = (Cliente) cmbCliente.getSelectedItem();
            if (cli == null) {
                JOptionPane.showMessageDialog(dialog, "Selecciona un cliente.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ParticipanteTorneo pt = new ParticipanteTorneo(0, torneo, cli, txtPlayerId.getText().trim(), chkPago.isSelected(), null);
            try {
                db.inscribirParticipante(pt);
                dialog.dispose();
                refreshTorneos();
                if (onDataChanged != null) onDataChanged.run();
                JOptionPane.showMessageDialog(this, "¡Jugador " + cli.getNombreCompleto() + " inscrito correctamente!", "Inscripción Exitosa", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error de Inscripción", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnCancel = UITheme.createSecondaryButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnInscribir);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void cambiarEstadoTorneo() {
        int row = tableTorneos.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un torneo de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Torneo t = listTorneos.get(row);
        String[] opciones = {"Programado", "En Curso", "Finalizado", "Cancelado"};
        String seleccionado = (String) JOptionPane.showInputDialog(this,
                "Selecciona el nuevo estado para el torneo '" + t.getNombreTorneo() + "':",
                "Actualizar Estado", JOptionPane.QUESTION_MESSAGE, null, opciones, t.getEstado());

        if (seleccionado != null && !seleccionado.equals(t.getEstado())) {
            db.updateTorneoEstado(t.getIdTorneo(), seleccionado);
            refreshTorneos();
            if (onDataChanged != null) onDataChanged.run();
        }
    }
}
