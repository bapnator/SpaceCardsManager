package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;
import com.spacecards.model.Cliente;
import com.spacecards.model.HistorialPremioTorneo;
import com.spacecards.model.ParticipanteTorneo;
import com.spacecards.model.PartidaBracket;
import com.spacecards.model.PremioInscripcionTorneo;
import com.spacecards.model.PremioTorneo;
import com.spacecards.model.Producto;
import com.spacecards.model.ProductoPremio;
import com.spacecards.model.Torneo;
import com.spacecards.model.Usuario;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

        JLabel lblTitle = new JLabel("Torneos");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Organiza competencias, administra inscripciones y consulta resultados");
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

        JButton btnNuevo = UITheme.createPrimaryButton("Crear Torneo");
        btnNuevo.addActionListener(e -> showCrearTorneoDialog());

        JButton btnInscribir = UITheme.createAccentButton("Inscribir Jugador");
        btnInscribir.addActionListener(e -> showInscribirParticipanteDialog());

        JButton btnCatalogoPremios = UITheme.createSecondaryButton("Catálogo de Premios");
        btnCatalogoPremios.addActionListener(e -> mostrarCatalogoPremios());

        JButton btnHistorialPremios = UITheme.createSecondaryButton("Historial de Premios");
        btnHistorialPremios.addActionListener(e -> mostrarHistorialPremios());

        JButton btnCambiarEstado = UITheme.createSecondaryButton("Cambiar Estado");
        btnCambiarEstado.addActionListener(e -> cambiarEstadoTorneo());

        actionButtons.add(btnNuevo);
        actionButtons.add(btnInscribir);
        actionButtons.add(btnCatalogoPremios);
        actionButtons.add(btnHistorialPremios);
        actionButtons.add(btnCambiarEstado);
        headerPanel.add(actionButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Split layout (Top: Torneos, Bottom: Participantes)
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 0, 16));
        contentPanel.setOpaque(false);

        // Card Torneos
        JPanel cardTorneos = UITheme.createCardPanel();
        cardTorneos.setLayout(new BorderLayout(8, 8));
        JLabel lblTorneosTitle = new JLabel("Torneos registrados");
        lblTorneosTitle.setFont(UITheme.FONT_SUBTITLE);
        cardTorneos.add(lblTorneosTitle, BorderLayout.NORTH);

        torneosModel = new DefaultTableModel(new String[]{
                "ID", "Nombre del Torneo", "Juego TCG", "Organizador", "Fecha y Hora", "Boleto ($)",
                "Inscritos / Cupo", "Ingreso estimado ($)", "Ingreso recibido ($)", "Premios en crédito ($)", "Ingreso tienda ($)", "Estado"
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

        JPanel participantActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        participantActions.setOpaque(false);
        JButton btnBracket = UITheme.createSecondaryButton("Bracket");
        btnBracket.addActionListener(e -> mostrarBracketSeleccionado());
        participantActions.add(btnBracket);
        JButton btnConfigurarPremios = UITheme.createSecondaryButton("Configurar Premios");
        btnConfigurarPremios.addActionListener(e -> configurarPremiosSeleccionado());
        participantActions.add(btnConfigurarPremios);
        JButton btnFinalizar = UITheme.createAccentButton("Finalizar y asignar premios");
        btnFinalizar.addActionListener(e -> finalizarTorneoSeleccionado());
        participantActions.add(btnFinalizar);
        JButton btnEliminarPart = UITheme.createSecondaryButton("Cancelar Inscripción");
        btnEliminarPart.addActionListener(e -> cancelarInscripcionSeleccionada());
        participantActions.add(btnEliminarPart);
        partHeader.add(participantActions, BorderLayout.EAST);

        cardPart.add(partHeader, BorderLayout.NORTH);

        partModel = new DefaultTableModel(new String[]{
                "ID Inscripción", "Jugador / Cliente", "Correo", "Teléfono", "POP ID / Player ID", "Pago Cuota", "Fecha Registro", "Lugar", "Premio"
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
                    "$" + t.getRecaudacionEstimada().setScale(2, RoundingMode.HALF_UP),
                    "$" + t.getRecaudacionReal().setScale(2, RoundingMode.HALF_UP),
                    "$" + t.getPremiosMonetariosReales().setScale(2, RoundingMode.HALF_UP),
                    "$" + t.getIngresoTiendaReal().setScale(2, RoundingMode.HALF_UP),
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
        lblSelectedTorneoTitle.setText("Participantes inscritos en: " + t.getNombreTorneo() +
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
                    pt.getFechaInscripcion().format(fmt),
                    pt.getPosicionFinal() == null ? "-" : pt.getPosicionFinal(),
                    pt.getPremio() == null || pt.getPremio().trim().isEmpty() ? "-" : pt.getPremio()
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
            try {
                db.eliminarParticipante(pt.getIdInscripcion());
                refreshTorneos();
                if (onDataChanged != null) onDataChanged.run();
                String mensaje = pt.isPagoConfirmado()
                        ? "La cuota pagada se devolvió como saldo a favor del cliente."
                        : "La inscripción se canceló y se liberó el lugar.";
                JOptionPane.showMessageDialog(this, mensaje, "Inscripción cancelada", JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo cancelar la inscripción", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showCrearTorneoDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Registrar Torneo TCG", true);
        dialog.setSize(620, 620);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtNombre = new JTextField();
        List<String> activeFranquicias = db.getNombresFranquiciasActivas();
        if (activeFranquicias.isEmpty()) {
            activeFranquicias = new ArrayList<>();
            activeFranquicias.add("General");
        }
        JComboBox<String> cmbJuego = new JComboBox<>(activeFranquicias.toArray(new String[0]));
        Usuario usuarioActual = SessionManager.getInstance().getCurrentUser();
        JLabel lblOrganizador = new JLabel(usuarioActual == null
                ? "Sin sesión" : usuarioActual.getNombreCompleto());

        JTextField txtCosto = new JTextField(db.getCostoTorneoPredeterminado().toPlainString());
        JSpinner spinCupo = new JSpinner(new SpinnerNumberModel(
                db.getCupoTorneoPredeterminado(), 2, Integer.MAX_VALUE, 2));
        List<Producto> productosDisponibles = new ArrayList<>();
        for (Producto producto : db.getProductos()) {
            if (!producto.isPreventa() && !producto.isTicketTorneo() && producto.getStock() > 0) {
                productosDisponibles.add(producto);
            }
        }
        List<ProductoPremio> premiosPatrocinadosDisponibles = new ArrayList<>();
        for (ProductoPremio producto : db.getProductosPremio()) {
            if (producto.getStock() > 0) premiosPatrocinadosDisponibles.add(producto);
        }
        JCheckBox chkPremioInscripcion = new JCheckBox("Dar un premio al confirmar cada inscripción");
        chkPremioInscripcion.setToolTipText(
                "Se entrega al confirmar el pago. Si luego se cancela la inscripción o el torneo, el premio no se devuelve automáticamente.");
        JComboBox<String> cmbTipoPremioInscripcion = new JComboBox<>(new String[]{
                "Producto del inventario", "Producto patrocinado", "Saldo a favor"
        });
        JComboBox<Producto> cmbProductoInscripcion = new JComboBox<>();
        for (Producto producto : productosDisponibles) cmbProductoInscripcion.addItem(producto);
        JComboBox<ProductoPremio> cmbPremioPatrocinadoInscripcion = new JComboBox<>();
        for (ProductoPremio producto : premiosPatrocinadosDisponibles) {
            cmbPremioPatrocinadoInscripcion.addItem(producto);
        }
        JTextField txtSaldoPremioInscripcion = new JTextField("10.00");
        JPanel tipoPremioInscripcionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tipoPremioInscripcionPanel.add(new JLabel("Tipo de premio:"));
        tipoPremioInscripcionPanel.add(cmbTipoPremioInscripcion);
        JPanel detallePremioInscripcionPanel = new JPanel(new CardLayout());
        JPanel detalleInventario = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        detalleInventario.add(new JLabel("Producto:"));
        detalleInventario.add(cmbProductoInscripcion);
        JPanel detallePatrocinado = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        detallePatrocinado.add(new JLabel("Premio:"));
        detallePatrocinado.add(cmbPremioPatrocinadoInscripcion);
        JPanel detalleSaldo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        detalleSaldo.add(new JLabel("Saldo por participante ($):"));
        detalleSaldo.add(txtSaldoPremioInscripcion);
        detallePremioInscripcionPanel.add(detalleInventario, "Producto del inventario");
        detallePremioInscripcionPanel.add(detallePatrocinado, "Producto patrocinado");
        detallePremioInscripcionPanel.add(detalleSaldo, "Saldo a favor");
        CardLayout detallePremioLayout = (CardLayout) detallePremioInscripcionPanel.getLayout();
        Runnable actualizarPremioInscripcion = () -> {
            boolean habilitado = chkPremioInscripcion.isSelected();
            tipoPremioInscripcionPanel.setVisible(habilitado);
            detallePremioInscripcionPanel.setVisible(habilitado);
            cmbTipoPremioInscripcion.setEnabled(habilitado);
            cmbProductoInscripcion.setEnabled(habilitado &&
                    "Producto del inventario".equals(cmbTipoPremioInscripcion.getSelectedItem()));
            cmbPremioPatrocinadoInscripcion.setEnabled(habilitado &&
                    "Producto patrocinado".equals(cmbTipoPremioInscripcion.getSelectedItem()));
            txtSaldoPremioInscripcion.setEnabled(habilitado &&
                    "Saldo a favor".equals(cmbTipoPremioInscripcion.getSelectedItem()));
            detallePremioLayout.show(detallePremioInscripcionPanel,
                    (String) cmbTipoPremioInscripcion.getSelectedItem());
        };
        chkPremioInscripcion.addActionListener(e -> actualizarPremioInscripcion.run());
        cmbTipoPremioInscripcion.addActionListener(e -> actualizarPremioInscripcion.run());
        actualizarPremioInscripcion.run();
        JLabel lblRecaudacionEstimada = new JLabel();
        Runnable actualizarEstimado = () -> {
            try {
                BigDecimal costoEstimado = new BigDecimal(txtCosto.getText().trim());
                BigDecimal totalEstimado = costoEstimado.multiply(BigDecimal.valueOf((Integer) spinCupo.getValue()));
                lblRecaudacionEstimada.setText("$" + totalEstimado.setScale(2, RoundingMode.HALF_UP));
            } catch (NumberFormatException ex) {
                lblRecaudacionEstimada.setText("Ingresa un costo válido.");
            }
        };
        txtCosto.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { actualizarEstimado.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { actualizarEstimado.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarEstimado.run(); }
        });
        spinCupo.addChangeListener(e -> actualizarEstimado.run());
        LocalDate[] fechaEvento = {LocalDate.now().plusDays(7)};
        JLabel lblFechaEvento = new JLabel(fechaEvento[0].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        JButton btnFechaEvento = UITheme.createSecondaryButton("Elegir fecha...");
        btnFechaEvento.addActionListener(e -> {
            LocalDate seleccion = mostrarSelectorFecha(dialog, fechaEvento[0]);
            if (seleccion != null) {
                fechaEvento[0] = seleccion;
                lblFechaEvento.setText(seleccion.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Nombre Torneo:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Juego TCG:"), gbc);
        gbc.gridx = 1;
        form.add(cmbJuego, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Organizador (sesión):*"), gbc);
        gbc.gridx = 1;
        form.add(lblOrganizador, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Costo Inscripción ($):*"), gbc);
        gbc.gridx = 1;
        form.add(txtCosto, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("Cupo Máximo:*"), gbc);
        gbc.gridx = 1;
        form.add(spinCupo, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Ingreso estimado (cupo completo):"), gbc);
        gbc.gridx = 1;
        form.add(lblRecaudacionEstimada, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        form.add(new JLabel("Fecha del evento:*"), gbc);
        gbc.gridx = 1;
        JPanel fechaPanel = new JPanel(new BorderLayout(8, 0));
        fechaPanel.setOpaque(false);
        fechaPanel.add(lblFechaEvento, BorderLayout.CENTER);
        fechaPanel.add(btnFechaEvento, BorderLayout.EAST);
        form.add(fechaPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(chkPremioInscripcion, gbc);
        gbc.gridy = 8;
        form.add(tipoPremioInscripcionPanel, gbc);
        gbc.gridy = 9;
        form.add(detallePremioInscripcionPanel, gbc);
        actualizarEstimado.run();

        dialog.add(new JScrollPane(form), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnGuardar = UITheme.createPrimaryButton("Registrar Torneo");
        btnGuardar.addActionListener(e -> {
            try {
                spinCupo.commitEdit();
            } catch (java.text.ParseException ex) {
                JOptionPane.showMessageDialog(dialog, "El cupo debe ser un número entero.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String nom = txtNombre.getText().trim();
            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "El nombre del torneo es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal costo;
            try {
                costo = new BigDecimal(txtCosto.getText().trim());
                if (costo.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "El costo de inscripción debe ser un valor válido mayor o igual a cero.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (fechaEvento[0].isBefore(LocalDate.now())) {
                JOptionPane.showMessageDialog(dialog, "La fecha del torneo no puede estar en el pasado.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Usuario org = SessionManager.getInstance().getCurrentUser();
            if (org == null || org.getIdUsuario() <= 0 || !org.isActivo()) {
                JOptionPane.showMessageDialog(dialog, "Inicia sesión con un usuario activo para registrar el torneo.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int cupo = (int) spinCupo.getValue();
            if (cupo < 2 || cupo % 2 != 0) {
                JOptionPane.showMessageDialog(dialog, "El cupo máximo debe ser un número par mayor o igual a 2.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Torneo nuevo = new Torneo(0, org, nom, (String) cmbJuego.getSelectedItem(),
                    fechaEvento[0].atTime(16, 0), costo, cupo, "Programado");
            PremioInscripcionTorneo premioInscripcion = null;
            if (chkPremioInscripcion.isSelected()) {
                premioInscripcion = new PremioInscripcionTorneo();
                String tipo = (String) cmbTipoPremioInscripcion.getSelectedItem();
                if ("Producto del inventario".equals(tipo)) {
                    Producto producto = (Producto) cmbProductoInscripcion.getSelectedItem();
                    if (producto == null) {
                        JOptionPane.showMessageDialog(dialog, "No hay productos de inventario disponibles para el premio.", "Validación", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    premioInscripcion.setTipoPremio(PremioInscripcionTorneo.TIPO_INVENTARIO);
                    premioInscripcion.setIdProducto(producto.getIdProducto());
                } else if ("Producto patrocinado".equals(tipo)) {
                    ProductoPremio producto = (ProductoPremio) cmbPremioPatrocinadoInscripcion.getSelectedItem();
                    if (producto == null) {
                        JOptionPane.showMessageDialog(dialog, "No hay premios patrocinados disponibles para el premio por inscripción.", "Validación", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    premioInscripcion.setTipoPremio(PremioInscripcionTorneo.TIPO_PATROCINADO);
                    premioInscripcion.setIdProductoPremio(producto.getIdProductoPremio());
                } else {
                    BigDecimal monto;
                    try {
                        monto = new BigDecimal(txtSaldoPremioInscripcion.getText().trim());
                        if (monto.compareTo(BigDecimal.ZERO) <= 0 ||
                                monto.compareTo(new BigDecimal("99999999.99")) > 0 ||
                                monto.stripTrailingZeros().scale() > 2) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog, "El saldo por participante debe ser mayor que cero y tener un máximo de dos decimales.", "Validación", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    premioInscripcion.setTipoPremio(PremioInscripcionTorneo.TIPO_SALDO_FAVOR);
                    premioInscripcion.setMonto(monto);
                }
            }
            try {
                db.addTorneo(nuevo, premioInscripcion);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, "No se pudo registrar el torneo y su ticket de participación: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

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
        if (!"Programado".equalsIgnoreCase(torneo.getEstado())) {
            JOptionPane.showMessageDialog(this, "No se pueden inscribir jugadores después de que inicia el torneo.",
                    "Inscripciones cerradas", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (torneo.getCuposDisponibles() <= 0) {
            JOptionPane.showMessageDialog(this, "El torneo ya alcanzó su cupo máximo (" + torneo.getCupoMaximo() + " jugadores).", "Cupo Lleno", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Set<Integer> idsClientesInscritos = new HashSet<>();
        for (ParticipanteTorneo participante : db.getParticipantesByTorneo(torneo.getIdTorneo())) {
            idsClientesInscritos.add(participante.getIdCliente());
        }
        List<Cliente> clientesDisponibles = new ArrayList<>();
        for (Cliente cliente : db.getClientes()) {
            if (!idsClientesInscritos.contains(cliente.getIdCliente())) {
                clientesDisponibles.add(cliente);
            }
        }
        if (clientesDisponibles.isEmpty()) {
            int registrar = JOptionPane.showConfirmDialog(this,
                    "Todos los clientes ya están inscritos en este torneo. ¿Deseas registrar un cliente nuevo?",
                    "Sin participantes disponibles", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (registrar == JOptionPane.YES_OPTION) registrarClienteParaInscripcion();
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Inscribir participante", true);
        dialog.setSize(440, 390);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<Cliente> cmbCliente = new JComboBox<>();
        for (Cliente cliente : clientesDisponibles) cmbCliente.addItem(cliente);

        JTextField txtPlayerId = new JTextField("POP-");
        boolean esGratuito = torneo.getCostoInscripcion().compareTo(BigDecimal.ZERO) == 0;
        JCheckBox chkPago = new JCheckBox(esGratuito
                ? "Inscripción gratuita: registrar ticket"
                : "Confirmar pago de inscripción ($" + torneo.getCostoInscripcion() + ")", true);
        chkPago.setEnabled(!esGratuito);
        JComboBox<String> cmbMetodoPago = new JComboBox<>(new String[]{"Efectivo", "Tarjeta", "Transferencia"});
        cmbMetodoPago.setEnabled(!esGratuito);
        JLabel lblSaldoDisponible = new JLabel("$0.00");
        JCheckBox chkUsarSaldoFavor = new JCheckBox();

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

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        form.add(new JLabel("Método de pago:"), gbc);
        gbc.gridx = 1;
        form.add(cmbMetodoPago, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Saldo a favor disponible:"), gbc);
        gbc.gridx = 1;
        form.add(lblSaldoDisponible, gbc);
        gbc.gridx = 0; gbc.gridy = 6;
        form.add(new JLabel("Usar saldo a favor:"), gbc);
        gbc.gridx = 1;
        form.add(chkUsarSaldoFavor, gbc);
        Runnable actualizarSaldo = () -> {
            Cliente seleccionado = (Cliente) cmbCliente.getSelectedItem();
            BigDecimal saldo = seleccionado == null ? BigDecimal.ZERO : db.getSaldoFavorCliente(seleccionado.getIdCliente());
            lblSaldoDisponible.setText("$" + saldo);
            BigDecimal maximoAplicable = saldo.min(torneo.getCostoInscripcion());
            chkUsarSaldoFavor.setText("Aplicar automáticamente hasta $" + maximoAplicable);
            boolean puedeAplicar = !esGratuito && chkPago.isSelected() &&
                    maximoAplicable.compareTo(BigDecimal.ZERO) > 0;
            if (!puedeAplicar) chkUsarSaldoFavor.setSelected(false);
            chkUsarSaldoFavor.setEnabled(puedeAplicar);
        };
        cmbCliente.addActionListener(e -> actualizarSaldo.run());
        chkPago.addActionListener(e -> {
            cmbMetodoPago.setEnabled(chkPago.isSelected() && !esGratuito);
            actualizarSaldo.run();
        });
        actualizarSaldo.run();

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnInscribir = UITheme.createPrimaryButton("Inscribir Participante");
        btnInscribir.addActionListener(e -> {
            Cliente cli = (Cliente) cmbCliente.getSelectedItem();
            if (cli == null) {
                JOptionPane.showMessageDialog(dialog, "Selecciona un cliente.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (chkUsarSaldoFavor.isSelected() && !esGratuito && !chkPago.isSelected()) {
                JOptionPane.showMessageDialog(dialog, "Confirma el pago de inscripción para aplicar saldo a favor.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal montoSaldoFavor = chkUsarSaldoFavor.isSelected()
                    ? db.getSaldoFavorCliente(cli.getIdCliente()).min(torneo.getCostoInscripcion())
                    : BigDecimal.ZERO;
            Usuario usuarioActual = SessionManager.getInstance().getCurrentUser();
            if (usuarioActual == null || usuarioActual.getIdUsuario() <= 0 || !usuarioActual.isActivo()) {
                JOptionPane.showMessageDialog(dialog, "Inicia sesión con un usuario activo para registrar la inscripción.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ParticipanteTorneo pt = new ParticipanteTorneo(0, torneo, cli, txtPlayerId.getText().trim(),
                    esGratuito || chkPago.isSelected(), null);
            try {
                db.inscribirParticipante(pt, (String) cmbMetodoPago.getSelectedItem(), montoSaldoFavor,
                        usuarioActual.getIdUsuario());
                dialog.dispose();
                refreshTorneos();
                if (onDataChanged != null) onDataChanged.run();
                String mensajeInscripcion = "¡Jugador " + cli.getNombreCompleto() + " inscrito correctamente!";
                if (montoSaldoFavor.compareTo(BigDecimal.ZERO) > 0) {
                    mensajeInscripcion += "\nSaldo a favor utilizado: $" + montoSaldoFavor;
                }
                if (pt.getPremioInscripcion() != null) {
                    mensajeInscripcion += "\n" + pt.getPremioInscripcion();
                }
                JOptionPane.showMessageDialog(this,
                        mensajeInscripcion,
                        "Inscripción Exitosa", JOptionPane.INFORMATION_MESSAGE);
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

    private LocalDate mostrarSelectorFecha(Window owner, LocalDate fechaInicial) {
        JDialog calendario = new JDialog(owner, "Fecha del torneo", Dialog.ModalityType.APPLICATION_MODAL);
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
                btnDia.setEnabled(!fecha.isBefore(LocalDate.now()));
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

    private class PremioFilaInput {
        private final int posicion;
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        private final JComboBox<String> cmbTipo = new JComboBox<>(new String[]{
                "Saldo a favor", "Producto del inventario", "Producto patrocinado"
        });
        private final JComboBox<Producto> cmbInventario = new JComboBox<>();
        private final JComboBox<ProductoPremio> cmbPatrocinado = new JComboBox<>();
        private final JSpinner spinPorcentaje = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 0.5));

        PremioFilaInput(int posicion, PremioTorneo existente, List<Producto> inventario,
                        List<ProductoPremio> patrocinados) {
            this.posicion = posicion;
            panel.add(new JLabel("Puesto " + posicion));
            panel.add(cmbTipo);
            for (Producto producto : inventario) cmbInventario.addItem(producto);
            for (ProductoPremio producto : patrocinados) cmbPatrocinado.addItem(producto);
            spinPorcentaje.setEditor(new JSpinner.NumberEditor(spinPorcentaje, "#0.##"));

            if (existente != null) {
                if (PremioTorneo.TIPO_INVENTARIO.equals(existente.getTipoPremio())) {
                    cmbTipo.setSelectedItem("Producto del inventario");
                    seleccionarInventario(existente.getIdProducto());
                } else if (PremioTorneo.TIPO_PATROCINADO.equals(existente.getTipoPremio())) {
                    cmbTipo.setSelectedItem("Producto patrocinado");
                    seleccionarPatrocinado(existente.getIdProductoPremio());
                } else {
                    cmbTipo.setSelectedItem("Saldo a favor");
                    spinPorcentaje.setValue(existente.getPorcentajeFondo().doubleValue());
                }
            }

            cmbTipo.addActionListener(e -> actualizarControles());
            actualizarControles();
        }

        private void seleccionarInventario(Integer idProducto) {
            if (idProducto == null) return;
            for (int i = 0; i < cmbInventario.getItemCount(); i++) {
                if (cmbInventario.getItemAt(i).getIdProducto() == idProducto) {
                    cmbInventario.setSelectedIndex(i);
                    return;
                }
            }
        }

        private void seleccionarPatrocinado(Integer idProducto) {
            if (idProducto == null) return;
            for (int i = 0; i < cmbPatrocinado.getItemCount(); i++) {
                if (cmbPatrocinado.getItemAt(i).getIdProductoPremio() == idProducto) {
                    cmbPatrocinado.setSelectedIndex(i);
                    return;
                }
            }
        }

        private void actualizarControles() {
            panel.removeAll();
            panel.add(new JLabel("Puesto " + posicion));
            String tipo = (String) cmbTipo.getSelectedItem();
            panel.add(cmbTipo);
            if ("Producto del inventario".equals(tipo)) {
                panel.add(cmbInventario);
            } else if ("Producto patrocinado".equals(tipo)) {
                panel.add(cmbPatrocinado);
            } else {
                panel.add(new JLabel("% del monto restante:"));
                panel.add(spinPorcentaje);
            }
            panel.revalidate();
            panel.repaint();
        }

        boolean esMonetario() {
            return "Saldo a favor".equals(cmbTipo.getSelectedItem());
        }

        BigDecimal porcentaje() {
            return BigDecimal.valueOf(((Number) spinPorcentaje.getValue()).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        PremioTorneo toPremio(int idTorneo, int posicion) {
            PremioTorneo premio = new PremioTorneo();
            premio.setIdTorneo(idTorneo);
            premio.setPosicion(posicion);
            String tipo = (String) cmbTipo.getSelectedItem();
            if ("Producto del inventario".equals(tipo)) {
                Producto producto = (Producto) cmbInventario.getSelectedItem();
                premio.setTipoPremio(PremioTorneo.TIPO_INVENTARIO);
                premio.setIdProducto(producto == null ? null : producto.getIdProducto());
            } else if ("Producto patrocinado".equals(tipo)) {
                ProductoPremio producto = (ProductoPremio) cmbPatrocinado.getSelectedItem();
                premio.setTipoPremio(PremioTorneo.TIPO_PATROCINADO);
                premio.setIdProductoPremio(producto == null ? null : producto.getIdProductoPremio());
            } else {
                premio.setTipoPremio(PremioTorneo.TIPO_MONETARIO);
                premio.setPorcentajeFondo(porcentaje());
            }
            return premio;
        }
    }

    private Torneo getTorneoSeleccionado() {
        int row = tableTorneos.getSelectedRow();
        return row >= 0 && row < listTorneos.size() ? listTorneos.get(row) : null;
    }

    private void mostrarCatalogoPremios() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Catálogo de premios patrocinados", true);
        dialog.setSize(680, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Producto", "Patrocinador", "Disponibles", "Descripción"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable table = new JTable(model);
        UITheme.formatTable(table);
        List<ProductoPremio> productos = new ArrayList<>();
        Runnable refresh = () -> {
            productos.clear();
            productos.addAll(db.getProductosPremio());
            model.setRowCount(0);
            for (ProductoPremio producto : productos) {
                model.addRow(new Object[]{producto.getIdProductoPremio(), producto.getNombre(),
                        producto.getPatrocinador(), producto.getStock(), producto.getDescripcion()});
            }
        };
        refresh.run();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 12, 8, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField txtNombre = new JTextField();
        JTextField txtPatrocinador = new JTextField();
        JTextField txtDescripcion = new JTextField();
        JSpinner spinStock = new JSpinner(new SpinnerNumberModel(1, 0, 100000, 1));
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(new JLabel("Producto:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        form.add(txtNombre, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Proveedor / fabricante:"), gbc);
        gbc.gridx = 1;
        form.add(txtPatrocinador, gbc);
        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Descripción:"), gbc);
        gbc.gridx = 1;
        form.add(txtDescripcion, gbc);
        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Cantidad disponible:*"), gbc);
        gbc.gridx = 1;
        form.add(spinStock, gbc);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0 || row >= productos.size()) return;
            ProductoPremio producto = productos.get(row);
            txtNombre.setText(producto.getNombre());
            txtPatrocinador.setText(producto.getPatrocinador() == null ? "" : producto.getPatrocinador());
            txtDescripcion.setText(producto.getDescripcion() == null ? "" : producto.getDescripcion());
            spinStock.setValue(producto.getStock());
        });

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnNuevo = UITheme.createSecondaryButton("Limpiar / Nuevo");
        btnNuevo.addActionListener(e -> {
            table.clearSelection();
            txtNombre.setText("");
            txtPatrocinador.setText("");
            txtDescripcion.setText("");
            spinStock.setValue(1);
        });
        JButton btnGuardar = UITheme.createPrimaryButton("Agregar premio");
        btnGuardar.addActionListener(e -> {
            ProductoPremio producto = new ProductoPremio();
            producto.setNombre(txtNombre.getText().trim());
            producto.setPatrocinador(txtPatrocinador.getText().trim());
            producto.setDescripcion(txtDescripcion.getText().trim());
            producto.setStock((Integer) spinStock.getValue());
            try {
                db.addProductoPremio(producto);
                refresh.run();
                txtNombre.setText("");
                txtPatrocinador.setText("");
                txtDescripcion.setText("");
                spinStock.setValue(1);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "No se pudo agregar", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton btnActualizar = UITheme.createSecondaryButton("Actualizar seleccionado");
        btnActualizar.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= productos.size()) {
                JOptionPane.showMessageDialog(dialog, "Selecciona un premio para actualizar.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ProductoPremio producto = productos.get(row);
            producto.setNombre(txtNombre.getText().trim());
            producto.setPatrocinador(txtPatrocinador.getText().trim());
            producto.setDescripcion(txtDescripcion.getText().trim());
            producto.setStock((Integer) spinStock.getValue());
            try {
                db.updateProductoPremio(producto);
                refresh.run();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "No se pudo actualizar", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton btnCerrar = UITheme.createSecondaryButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());
        acciones.add(btnNuevo);
        acciones.add(btnGuardar);
        acciones.add(btnActualizar);
        acciones.add(btnCerrar);

        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(form, BorderLayout.NORTH);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void mostrarHistorialPremios() {
        List<HistorialPremioTorneo> historial = db.getHistorialPremiosTorneo();
        if (historial.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aún no hay premios registrados en torneos.",
                    "Historial de premios", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Historial de premios de torneos", true);
        dialog.setSize(900, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultTableModel historialModel = new DefaultTableModel(new String[]{
                "Fecha del torneo", "Torneo", "Jugador", "Player ID / POP ID", "Puesto", "Tipo de premio", "Premio registrado"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (HistorialPremioTorneo premio : historial) {
            historialModel.addRow(new Object[]{
                    premio.getFechaEvento() == null ? "-" : premio.getFechaEvento().format(fmt),
                    premio.getNombreTorneo(),
                    premio.getNombreJugador(),
                    premio.getIdJugadorTcg() == null || premio.getIdJugadorTcg().trim().isEmpty()
                            ? "-" : premio.getIdJugadorTcg(),
                    premio.getPosicion() == 0 ? "Inscripción" : premio.getPosicion(),
                    premio.getTipoPremio(),
                    premio.getDescripcionPremio()
            });
        }
        JTable tablaHistorial = new JTable(historialModel);
        UITheme.formatTable(tablaHistorial);
        dialog.add(new JScrollPane(tablaHistorial), BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCerrar = UITheme.createSecondaryButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());
        acciones.add(btnCerrar);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void configurarPremiosSeleccionado() {
        Torneo torneo = getTorneoSeleccionado();
        if (torneo == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un torneo para configurar sus premios.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ("Finalizado".equalsIgnoreCase(torneo.getEstado()) || "Cancelado".equalsIgnoreCase(torneo.getEstado())) {
            JOptionPane.showMessageDialog(this, "No se pueden cambiar los premios de un torneo finalizado o cancelado.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<PremioTorneo> existentes = db.getPremiosTorneo(torneo.getIdTorneo());
        List<Producto> todosLosProductos = db.getProductos();
        List<Producto> inventario = new ArrayList<>();
        for (Producto producto : todosLosProductos) {
            if (!producto.isPreventa() && !producto.isTicketTorneo() &&
                    (producto.getStock() > 0 || premioExistenteUsaProducto(existentes, producto.getIdProducto()))) {
                inventario.add(producto);
            }
        }
        List<ProductoPremio> patrocinados = new ArrayList<>();
        for (ProductoPremio producto : db.getProductosPremio()) {
            if (producto.getStock() > 0 || premioExistenteUsaProductoPatrocinado(existentes, producto.getIdProductoPremio())) {
                patrocinados.add(producto);
            }
        }
        int cantidadInicial = existentes.isEmpty() ? Math.min(3, torneo.getCupoMaximo()) : existentes.size();

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Configurar premios: " + torneo.getNombreTorneo(), true);
        dialog.setSize(760, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JSpinner spinGanadores = new JSpinner(new SpinnerNumberModel(cantidadInicial, 1, torneo.getCupoMaximo(), 1));
        JSpinner spinFondo = new JSpinner(new SpinnerNumberModel(
                existentes.isEmpty() ? 50.0 : torneo.getPorcentajePremiosMonetarios().doubleValue(),
                0.0, 100.0, 1.0));
        spinFondo.setEditor(new JSpinner.NumberEditor(spinFondo, "#0.##"));
        JPanel filas = new JPanel();
        filas.setLayout(new BoxLayout(filas, BoxLayout.Y_AXIS));
        List<PremioFilaInput> entradas = new ArrayList<>();
        JLabel lblResumen = new JLabel();

        Runnable actualizarResumen = () -> {
            BigDecimal porcentajeFondo = BigDecimal.valueOf(((Number) spinFondo.getValue()).doubleValue());
            BigDecimal estimadoFondo = torneo.getRecaudacionEstimada()
                    .multiply(porcentajeFondo).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal realFondo = torneo.getRecaudacionReal()
                    .multiply(porcentajeFondo).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal porcentajeRestante = new BigDecimal("100");
            BigDecimal estimadoRestante = estimadoFondo;
            BigDecimal realRestante = realFondo;
            for (PremioFilaInput entrada : entradas) {
                if (!entrada.esMonetario()) continue;
                BigDecimal porcentajePuesto = entrada.porcentaje();
                BigDecimal porcentajeAplicado = porcentajeRestante.multiply(porcentajePuesto)
                        .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                porcentajeRestante = porcentajeRestante.subtract(porcentajeAplicado);
                BigDecimal premioEstimado = estimadoRestante.multiply(porcentajePuesto)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal premioReal = realRestante.multiply(porcentajePuesto)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                estimadoRestante = estimadoRestante.subtract(premioEstimado);
                realRestante = realRestante.subtract(premioReal);
            }
            BigDecimal estimadoPremios = estimadoFondo.subtract(estimadoRestante);
            BigDecimal realPremios = realFondo.subtract(realRestante);
            BigDecimal porcentajeAsignado = new BigDecimal("100").subtract(porcentajeRestante);
            lblResumen.setText("Para premios: " + porcentajeFondo + "% | porcentaje total asignado: " +
                    porcentajeAsignado.setScale(2, RoundingMode.HALF_UP) + "% | " +
                    "premios estimados: $" + estimadoPremios + " | según pagos recibidos: $" + realPremios +
                    " | ingreso para tienda: $" + torneo.getRecaudacionReal().subtract(realPremios));
        };
        Runnable[] reconstruirFilas = new Runnable[1];
        reconstruirFilas[0] = () -> {
            int cantidad = (Integer) spinGanadores.getValue();
            while (entradas.size() > cantidad) {
                PremioFilaInput eliminada = entradas.remove(entradas.size() - 1);
                filas.remove(eliminada.panel);
            }
            while (entradas.size() < cantidad) {
                int indice = entradas.size();
                PremioTorneo existente = indice < existentes.size() ? existentes.get(indice) : null;
                PremioFilaInput entrada = new PremioFilaInput(indice + 1, existente, inventario, patrocinados);
                entrada.spinPorcentaje.addChangeListener(e -> actualizarResumen.run());
                entrada.cmbTipo.addActionListener(e -> actualizarResumen.run());
                entradas.add(entrada);
                filas.add(entrada.panel);
            }
            filas.revalidate();
            filas.repaint();
            actualizarResumen.run();
        };
        spinGanadores.addChangeListener(e -> reconstruirFilas[0].run());
        spinFondo.addChangeListener(e -> actualizarResumen.run());
        reconstruirFilas[0].run();

        JPanel encabezado = new JPanel(new GridLayout(2, 1, 4, 4));
        encabezado.setBorder(new EmptyBorder(12, 12, 4, 12));
        JPanel opciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opciones.add(new JLabel("Cantidad de puestos premiados:"));
        opciones.add(spinGanadores);
        opciones.add(new JLabel("Porcentaje de ingresos para premios:"));
        opciones.add(spinFondo);
        encabezado.add(opciones);
        encabezado.add(lblResumen);
        dialog.add(encabezado, BorderLayout.NORTH);
        dialog.add(new JScrollPane(filas), BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> dialog.dispose());
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar premios");
        btnGuardar.addActionListener(e -> {
            List<PremioTorneo> premios = new ArrayList<>();
            for (int i = 0; i < entradas.size(); i++) premios.add(entradas.get(i).toPremio(torneo.getIdTorneo(), i + 1));
            try {
                BigDecimal porcentajeFondo = BigDecimal.valueOf(((Number) spinFondo.getValue()).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP);
                db.guardarPremiosTorneo(torneo.getIdTorneo(), porcentajeFondo, premios);
                dialog.dispose();
                refreshTorneos();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "No se pudieron guardar los premios", JOptionPane.ERROR_MESSAGE);
            }
        });
        acciones.add(btnCancelar);
        acciones.add(btnGuardar);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private boolean premioExistenteUsaProducto(List<PremioTorneo> premios, int idProducto) {
        for (PremioTorneo premio : premios) {
            if (idProducto == (premio.getIdProducto() == null ? -1 : premio.getIdProducto())) return true;
        }
        return false;
    }

    private boolean premioExistenteUsaProductoPatrocinado(List<PremioTorneo> premios, int idProducto) {
        for (PremioTorneo premio : premios) {
            if (idProducto == (premio.getIdProductoPremio() == null ? -1 : premio.getIdProductoPremio())) return true;
        }
        return false;
    }

    private void mostrarBracketSeleccionado() {
        Torneo torneo = getTorneoSeleccionado();
        if (torneo == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un torneo para ver su bracket.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            boolean bracketExistente = db.existeBracketTorneo(torneo.getIdTorneo());
            if (!bracketExistente && !"En Curso".equalsIgnoreCase(torneo.getEstado())) {
                JOptionPane.showMessageDialog(this, "Cambia el torneo a En Curso antes de generar el bracket.",
                        "Bracket no disponible", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!bracketExistente) {
                int pagados = 0;
                for (ParticipanteTorneo participante : db.getParticipantesByTorneo(torneo.getIdTorneo())) {
                    if (participante.isPagoConfirmado()) pagados++;
                }
                if (pagados < 2) {
                    JOptionPane.showMessageDialog(this,
                            "Se necesitan al menos dos inscripciones pagadas para generar el bracket.",
                            "Faltan participantes", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int confirmar = JOptionPane.showConfirmDialog(this,
                        "Se sortearán " + pagados + " participantes con inscripción pagada. " +
                                "El bracket no se puede volver a sortear y bloqueará la cancelación de inscripciones. ¿Continuar?",
                        "Generar bracket", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (confirmar != JOptionPane.YES_OPTION) return;
                db.generarBracketTorneo(torneo.getIdTorneo());
            }
            mostrarBracket(torneo, "En Curso".equalsIgnoreCase(torneo.getEstado()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo generar el bracket", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarBracket(Torneo torneo, boolean editable) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Bracket: " + torneo.getNombreTorneo(), true);
        dialog.setSize(980, 620);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel instrucciones = new JLabel(editable
                ? "Selecciona el ganador de cada partida. Si cambias un resultado, se reinician las rondas posteriores."
                : "Bracket en modo consulta.");
        instrucciones.setBorder(new EmptyBorder(10, 12, 4, 12));
        dialog.add(instrucciones, BorderLayout.NORTH);

        JTabbedPane rondas = new JTabbedPane();
        dialog.add(rondas, BorderLayout.CENTER);
        Runnable[] actualizarBracket = new Runnable[1];
        actualizarBracket[0] = () -> {
            int pestañaSeleccionada = rondas.getSelectedIndex();
            rondas.removeAll();
            List<PartidaBracket> partidas = db.getBracketPartidas(torneo.getIdTorneo());
            List<ParticipanteTorneo> participantes = db.getParticipantesByTorneo(torneo.getIdTorneo());
            Map<Integer, ParticipanteTorneo> porInscripcion = new LinkedHashMap<>();
            for (ParticipanteTorneo participante : participantes) {
                porInscripcion.put(participante.getIdInscripcion(), participante);
            }
            int ultimaRonda = 0;
            for (PartidaBracket partida : partidas) {
                if (PartidaBracket.TIPO_NORMAL.equals(partida.getTipoPartida())) {
                    ultimaRonda = Math.max(ultimaRonda, partida.getRonda());
                }
            }
            for (int ronda = 1; ronda <= ultimaRonda; ronda++) {
                JPanel listaPartidas = new JPanel();
                listaPartidas.setBorder(new EmptyBorder(12, 12, 12, 12));
                listaPartidas.setLayout(new BoxLayout(listaPartidas, BoxLayout.Y_AXIS));
                for (PartidaBracket partida : partidas) {
                    if (partida.getRonda() != ronda) continue;
                    listaPartidas.add(crearTarjetaPartidaBracket(
                            dialog, torneo, partida, porInscripcion, editable, actualizarBracket[0]));
                    listaPartidas.add(Box.createVerticalStrut(8));
                }
                JScrollPane scroll = new JScrollPane(listaPartidas);
                rondas.addTab(nombreRondaBracket(ronda, ultimaRonda), scroll);
            }
            if (pestañaSeleccionada >= 0 && pestañaSeleccionada < rondas.getTabCount()) {
                rondas.setSelectedIndex(pestañaSeleccionada);
            }
        };
        actualizarBracket[0].run();

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCerrar = UITheme.createSecondaryButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());
        acciones.add(btnCerrar);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel crearTarjetaPartidaBracket(JDialog dialog, Torneo torneo, PartidaBracket partida,
                                               Map<Integer, ParticipanteTorneo> participantes,
                                               boolean editable,
                                               Runnable actualizarBracket) {
        JPanel tarjeta = UITheme.createCardPanel();
        tarjeta.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        String titulo = PartidaBracket.TIPO_TERCER_LUGAR.equals(partida.getTipoPartida())
                ? "Partido por el tercer lugar"
                : "Partida " + partida.getNumeroPartida();
        gbc.gridx = 0; gbc.gridy = 0;
        tarjeta.add(new JLabel(titulo), gbc);

        String nombreA = partida.getNombreParticipanteA() == null
                ? "Por definir" : partida.getNombreParticipanteA();
        String nombreB = partida.getNombreParticipanteB() == null
                ? "Por definir" : partida.getNombreParticipanteB();
        gbc.gridy = 1;
        tarjeta.add(new JLabel(nombreA + "  vs  " + nombreB), gbc);

        if (partida.isPaseAutomatico()) {
            gbc.gridy = 2;
            tarjeta.add(new JLabel("Pase automático: avanza " +
                    (nombreA.equals("Por definir") ? nombreB : nombreA)), gbc);
            return tarjeta;
        }
        if (partida.getIdParticipanteA() == null || partida.getIdParticipanteB() == null) {
            gbc.gridy = 2;
            tarjeta.add(new JLabel("Esperando resultados de la ronda anterior."), gbc);
            return tarjeta;
        }

        JComboBox<ParticipanteTorneo> cmbGanador = new JComboBox<>();
        ParticipanteTorneo participanteA = participantes.get(partida.getIdParticipanteA());
        ParticipanteTorneo participanteB = participantes.get(partida.getIdParticipanteB());
        if (participanteA == null || participanteB == null) {
            gbc.gridy = 2;
            tarjeta.add(new JLabel("No se encontraron los participantes de esta partida."), gbc);
            return tarjeta;
        }
        cmbGanador.addItem(participanteA);
        cmbGanador.addItem(participanteB);
        cmbGanador.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list,
                        value == null ? "Seleccionar ganador..." : value.toString(),
                        index, isSelected, cellHasFocus);
            }
        });
        if (partida.getIdGanador() != null) {
            cmbGanador.setSelectedItem(partida.getIdGanador().equals(participanteA.getIdInscripcion())
                    ? participanteA : participanteB);
        } else {
            cmbGanador.setSelectedIndex(-1);
        }
        cmbGanador.addActionListener(e -> {
            if (!editable) return;
            ParticipanteTorneo ganador = (ParticipanteTorneo) cmbGanador.getSelectedItem();
            if (ganador == null) return;
            try {
                db.actualizarGanadorBracket(torneo.getIdTorneo(), partida.getRonda(),
                        partida.getNumeroPartida(), partida.getTipoPartida(), ganador.getIdInscripcion());
                actualizarBracket.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                        "No se pudo guardar el resultado", JOptionPane.ERROR_MESSAGE);
                actualizarBracket.run();
            }
        });
        cmbGanador.setEnabled(editable);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.2;
        tarjeta.add(new JLabel("Ganador:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        tarjeta.add(cmbGanador, gbc);
        JButton btnDeshacer = UITheme.createSecondaryButton("Deshacer resultado");
        btnDeshacer.setEnabled(editable && partida.isCompletada());
        btnDeshacer.addActionListener(e -> {
            try {
                db.actualizarGanadorBracket(torneo.getIdTorneo(), partida.getRonda(),
                        partida.getNumeroPartida(), partida.getTipoPartida(), null);
                actualizarBracket.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                        "No se pudo deshacer el resultado", JOptionPane.ERROR_MESSAGE);
            }
        });
        gbc.gridx = 2; gbc.weightx = 0;
        tarjeta.add(btnDeshacer, gbc);
        return tarjeta;
    }

    private String nombreRondaBracket(int ronda, int ultimaRonda) {
        if (ronda == ultimaRonda) return "Final";
        if (ronda == ultimaRonda - 1 && ultimaRonda >= 2) return "Semifinales";
        if (ronda == ultimaRonda - 2 && ultimaRonda >= 4) return "Cuartos";
        return "Ronda " + ronda;
    }

    private void finalizarTorneoSeleccionado() {
        Torneo torneo = getTorneoSeleccionado();
        if (torneo == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un torneo para finalizar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ("Finalizado".equalsIgnoreCase(torneo.getEstado()) || "Cancelado".equalsIgnoreCase(torneo.getEstado())) {
            JOptionPane.showMessageDialog(this, "El torneo ya está finalizado o cancelado.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<PremioTorneo> premios = db.getPremiosTorneo(torneo.getIdTorneo());
        if (premios.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Configura primero los puestos y premios del torneo.", "Faltan premios", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<ParticipanteTorneo> pagados = new ArrayList<>();
        for (ParticipanteTorneo participante : db.getParticipantesByTorneo(torneo.getIdTorneo())) {
            if (participante.isPagoConfirmado()) pagados.add(participante);
        }
        boolean hayBracket = db.existeBracketTorneo(torneo.getIdTorneo());
        Map<Integer, Integer> puestosAutomaticos = new LinkedHashMap<>();
        if (hayBracket) {
            try {
                puestosAutomaticos = db.getPuestosAutomaticosBracket(torneo.getIdTorneo());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Bracket incompleto", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (pagados.size() < premios.size()) {
            JOptionPane.showMessageDialog(this, "No hay suficientes inscripciones pagadas para asignar todos los puestos premiados.", "No se puede finalizar", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (hayBracket) {
            for (PremioTorneo premio : premios) {
                if (premio.getPosicion() <= 3 && !puestosAutomaticos.containsKey(premio.getPosicion())) {
                    JOptionPane.showMessageDialog(this,
                            "Completa el partido por el tercer lugar para asignar automáticamente ese puesto.",
                            "Bracket incompleto", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Asignar ganadores y finalizar torneo", true);
        dialog.setSize(560, Math.min(540, 180 + premios.size() * 42));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        Map<Integer, JComboBox<ParticipanteTorneo>> selecciones = new LinkedHashMap<>();
        boolean[] actualizandoOpciones = {false};
        Runnable[] actualizarOpcionesGanadores = new Runnable[1];
        for (int i = 0; i < premios.size(); i++) {
            PremioTorneo premio = premios.get(i);
            String descripcion = PremioTorneo.TIPO_MONETARIO.equals(premio.getTipoPremio())
                    ? "Saldo a favor (" + premio.getPorcentajeFondo() + "% del monto restante)"
                    : premio.getNombrePremio();
            JComboBox<ParticipanteTorneo> combo = new JComboBox<>();
            for (ParticipanteTorneo participante : pagados) combo.addItem(participante);
            combo.setSelectedIndex(-1);
            combo.addActionListener(e -> {
                if (!actualizandoOpciones[0] && actualizarOpcionesGanadores[0] != null) {
                    actualizarOpcionesGanadores[0].run();
                }
            });
            selecciones.put(premio.getPosicion(), combo);
            Integer idClienteAutomatico = puestosAutomaticos.get(premio.getPosicion());
            if (idClienteAutomatico != null) {
                for (int participanteIndex = 0; participanteIndex < combo.getItemCount(); participanteIndex++) {
                    ParticipanteTorneo participante = combo.getItemAt(participanteIndex);
                    if (participante.getIdCliente() == idClienteAutomatico) {
                        combo.setSelectedItem(participante);
                        break;
                    }
                }
                combo.setEnabled(false);
            } else {
                combo.setSelectedIndex(-1);
            }
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.4;
            String origenGanador = idClienteAutomatico == null ? "" : " (Bracket)";
            form.add(new JLabel("Puesto " + premio.getPosicion() + origenGanador + " — " + descripcion + ":"), gbc);
            gbc.gridx = 1; gbc.weightx = 0.6;
            form.add(combo, gbc);
        }
        actualizarOpcionesGanadores[0] = () -> {
            actualizandoOpciones[0] = true;
            try {
                Set<Integer> clientesYaSeleccionados = new HashSet<>();
                for (JComboBox<ParticipanteTorneo> combo : selecciones.values()) {
                    ParticipanteTorneo seleccionado = (ParticipanteTorneo) combo.getSelectedItem();
                    if (seleccionado != null) clientesYaSeleccionados.add(seleccionado.getIdCliente());
                }
                for (JComboBox<ParticipanteTorneo> combo : selecciones.values()) {
                    ParticipanteTorneo seleccionado = (ParticipanteTorneo) combo.getSelectedItem();
                    int idClienteSeleccionado = seleccionado == null ? -1 : seleccionado.getIdCliente();
                    combo.removeAllItems();
                    for (ParticipanteTorneo participante : pagados) {
                        if (participante.getIdCliente() == idClienteSeleccionado ||
                                !clientesYaSeleccionados.contains(participante.getIdCliente())) {
                            combo.addItem(participante);
                        }
                    }
                    if (seleccionado == null) combo.setSelectedIndex(-1);
                    else combo.setSelectedItem(seleccionado);
                }
            } finally {
                actualizandoOpciones[0] = false;
            }
        };
        dialog.add(new JScrollPane(form), BorderLayout.CENTER);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> dialog.dispose());
        JButton btnFinalizar = UITheme.createPrimaryButton("Confirmar ganadores y finalizar");
        btnFinalizar.addActionListener(e -> {
            Map<Integer, Integer> ganadores = new LinkedHashMap<>();
            for (Map.Entry<Integer, JComboBox<ParticipanteTorneo>> seleccion : selecciones.entrySet()) {
                ParticipanteTorneo participante = (ParticipanteTorneo) seleccion.getValue().getSelectedItem();
                if (participante == null) {
                    JOptionPane.showMessageDialog(dialog, "Selecciona un ganador para cada puesto.", "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (ganadores.containsValue(participante.getIdCliente())) {
                    JOptionPane.showMessageDialog(dialog, "Cada puesto debe asignarse a un ganador diferente.", "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ganadores.put(seleccion.getKey(), participante.getIdCliente());
            }
            try {
                db.finalizarTorneo(torneo.getIdTorneo(), ganadores);
                BigDecimal premiosSaldoFavor = calcularPremiosMonetarios(
                        torneo.getRecaudacionReal(), torneo.getPorcentajePremiosMonetarios(), premios);
                BigDecimal tienda = torneo.getRecaudacionReal().subtract(premiosSaldoFavor);
                dialog.dispose();
                refreshTorneos();
                if (onDataChanged != null) onDataChanged.run();
                JOptionPane.showMessageDialog(this,
                        "Torneo finalizado.\nRecaudado: $" + torneo.getRecaudacionReal() +
                                "\nPremios acreditados como saldo a favor: $" + premiosSaldoFavor +
                                "\nPara la tienda: $" + tienda,
                        "Torneo finalizado", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "No se pudo finalizar", JOptionPane.ERROR_MESSAGE);
            }
        });
        acciones.add(btnCancelar);
        acciones.add(btnFinalizar);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void registrarClienteParaInscripcion() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Registrar cliente para torneo", true);
        dialog.setSize(440, 330);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.3;

        JTextField txtNombre = new JTextField();
        JTextField txtApellido = new JTextField();
        JTextField txtCorreo = new JTextField();
        JTextField txtTelefono = new JTextField();
        JTextField[] campos = {txtNombre, txtApellido, txtCorreo, txtTelefono};
        String[] etiquetas = {"Nombre:*", "Apellido:*", "Correo electrónico:*", "Teléfono:*"};
        for (int i = 0; i < campos.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            form.add(new JLabel(etiquetas[i]), gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.7;
            form.add(campos[i], gbc);
            gbc.weightx = 0.3;
        }
        dialog.add(form, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> dialog.dispose());
        JButton btnGuardar = UITheme.createPrimaryButton("Guardar e inscribir");
        btnGuardar.addActionListener(e -> {
            String nombre = txtNombre.getText().trim();
            String apellido = txtApellido.getText().trim();
            String correo = txtCorreo.getText().trim();
            String telefono = txtTelefono.getText().trim();
            if (nombre.isEmpty() || apellido.isEmpty() || correo.isEmpty() || telefono.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Todos los campos son obligatorios.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!correo.contains("@") || !correo.contains(".")) {
                JOptionPane.showMessageDialog(dialog, "Ingresa un correo electrónico válido.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!Cliente.esTelefonoValido(telefono)) {
                JOptionPane.showMessageDialog(dialog,
                        "Ingresa un teléfono mexicano válido de 10 dígitos; puedes incluir +52 y separadores.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                db.addCliente(new Cliente(0, nombre, apellido, correo, telefono));
                dialog.dispose();
                showInscribirParticipanteDialog();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                        "No se pudo registrar el cliente", JOptionPane.ERROR_MESSAGE);
            }
        });
        acciones.add(btnCancelar);
        acciones.add(btnGuardar);
        dialog.add(acciones, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private BigDecimal calcularPremiosMonetarios(BigDecimal recaudacion, BigDecimal porcentajeFondo,
                                                  List<PremioTorneo> premios) {
        BigDecimal restante = recaudacion.multiply(porcentajeFondo)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalPremios = BigDecimal.ZERO;
        for (PremioTorneo premio : premios) {
            if (!PremioTorneo.TIPO_MONETARIO.equals(premio.getTipoPremio())) continue;
            BigDecimal monto = restante.multiply(premio.getPorcentajeFondo())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalPremios = totalPremios.add(monto);
            restante = restante.subtract(monto);
        }
        return totalPremios;
    }

    private void cambiarEstadoTorneo() {
        Torneo torneo = getTorneoSeleccionado();
        if (torneo == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un torneo de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ("Finalizado".equalsIgnoreCase(torneo.getEstado()) || "Cancelado".equalsIgnoreCase(torneo.getEstado())) {
            JOptionPane.showMessageDialog(this, "El torneo ya está cerrado y no puede cambiar de estado.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String[] opciones = {"Programado", "En Curso", "Cancelado"};
        String seleccionado = (String) JOptionPane.showInputDialog(this,
                "Al cancelar, cada inscripción pagada se devolverá como saldo a favor.\n" +
                        "Selecciona el nuevo estado para '" + torneo.getNombreTorneo() + "':",
                "Actualizar Estado", JOptionPane.QUESTION_MESSAGE, null, opciones, torneo.getEstado());

        if (seleccionado != null && !seleccionado.equals(torneo.getEstado())) {
            if ("Cancelado".equals(seleccionado)) {
                int confirmacion = JOptionPane.showConfirmDialog(this,
                        "¿Cancelar el torneo y acreditar a cada participante lo que pagó?",
                        "Confirmar cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirmacion != JOptionPane.YES_OPTION) return;
            }
            try {
                db.updateTorneoEstado(torneo.getIdTorneo(), seleccionado);
                refreshTorneos();
                if (onDataChanged != null) onDataChanged.run();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo cambiar el estado", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
