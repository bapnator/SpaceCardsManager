package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class DatabasePanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final JLabel lblDbPath = new JLabel();
    private final JLabel lblDbStatus = new JLabel();
    private final JPanel pnlStats = new JPanel(new GridLayout(2, 4, 10, 10));

    private final JTextArea txtSql = new JTextArea("SELECT id_producto, codigo_barras, nombre, categoria, precio_unitario, stock FROM producto ORDER BY stock ASC;", 4, 50);
    private final JComboBox<String> cmbQuickQueries = new JComboBox<>(new String[]{
            "-- Consultas rápidas recomendadas --",
            "SELECT id_producto, codigo_barras, nombre, categoria, precio_unitario, stock FROM producto ORDER BY stock ASC;",
            "SELECT v.id_venta, CONCAT(c.nombre, ' ', c.apellido) AS cliente, v.metodo_pago, v.monto_total, v.fecha_venta FROM venta v LEFT JOIN cliente c ON v.id_cliente = c.id_cliente ORDER BY v.id_venta DESC;",
            "SELECT id_preventa, monto_anticipo, monto_total, fecha_estimada_entrega, estado FROM preventa WHERE estado = 'Pendiente';",
            "SELECT t.nombre_torneo, t.juego_tcg, t.fecha_evento, t.costo_inscripcion, t.cupo_maximo, t.estado, COUNT(pt.id_inscripcion) AS inscritos FROM torneo t LEFT JOIN participante_torneo pt ON t.id_torneo = pt.id_torneo GROUP BY t.id_torneo;",
            "SELECT p.nombre, SUM(d.cantidad) AS unidades_vendidas, SUM(d.subtotal) AS ingresos FROM detalle_venta d JOIN producto p ON d.id_producto = p.id_producto GROUP BY p.id_producto ORDER BY unidades_vendidas DESC;",
            "SELECT * FROM cliente;",
            "SELECT * FROM usuario;"
    });

    private final DefaultTableModel queryResultModel = new DefaultTableModel();
    private final JTable tableResult = new JTable(queryResultModel);
    private final JLabel lblQueryStatus = new JLabel("Listo para ejecutar consultas.");

    private final Runnable onStatusChange;

    public DatabasePanel(Runnable onStatusChange) {
        this.onStatusChange = onStatusChange;
        this.setLayout(new BorderLayout(16, 16));
        this.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("Gestión de Base de Datos SQLite (spacecards.db)");
        lblTitle.setFont(UITheme.FONT_TITLE);
        JLabel lblSub = new JLabel("Persistencia local embebida con SQLite 3 y soporte de consultas interactivas");
        lblSub.setFont(UITheme.FONT_REGULAR);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        headerPanel.add(titleBox, BorderLayout.WEST);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerButtons.setOpaque(false);

        JButton btnConfig = UITheme.createSecondaryButton("Conexión (MariaDB / SQLite)");
        btnConfig.addActionListener(e -> showConfigDialog());

        JButton btnOpen = UITheme.createSecondaryButton("Cambiar BD (.db)");
        btnOpen.addActionListener(e -> chooseDatabaseFile());

        JButton btnReset = UITheme.createSecondaryButton("Limpiar Base de Datos");
        btnReset.addActionListener(e -> resetDatabase());

        JButton btnScript = UITheme.createSecondaryButton("Ver Esquema DDL");
        btnScript.addActionListener(e -> showSqlScriptDialog());

        headerButtons.add(btnConfig);
        headerButtons.add(btnOpen);
        headerButtons.add(btnReset);
        headerButtons.add(btnScript);
        headerPanel.add(headerButtons, BorderLayout.EAST);

        this.add(headerPanel, BorderLayout.NORTH);

        // Center: Split Top (Info + Stats) and Bottom (SQL Console)
        JPanel centerPanel = new JPanel(new BorderLayout(12, 12));
        centerPanel.setOpaque(false);

        // Top info card
        JPanel topInfoCard = UITheme.createCardPanel();
        topInfoCard.setLayout(new BorderLayout(12, 12));

        JPanel pathAndStatus = new JPanel(new GridLayout(2, 1, 4, 4));
        pathAndStatus.setOpaque(false);

        lblDbStatus.setFont(UITheme.FONT_BOLD);
        lblDbStatus.setForeground(UITheme.COLOR_SUCCESS);

        lblDbPath.setFont(new Font("Consolas", Font.PLAIN, 12));
        lblDbPath.setForeground(UITheme.COLOR_TEXT_MUTED);

        pathAndStatus.add(lblDbStatus);
        pathAndStatus.add(lblDbPath);
        topInfoCard.add(pathAndStatus, BorderLayout.NORTH);

        // Stats Grid
        pnlStats.setOpaque(false);
        topInfoCard.add(pnlStats, BorderLayout.CENTER);

        centerPanel.add(topInfoCard, BorderLayout.NORTH);

        // Bottom SQL Console Card
        JPanel consoleCard = UITheme.createCardPanel();
        consoleCard.setLayout(new BorderLayout(8, 8));

        JPanel consoleHeader = new JPanel(new BorderLayout(8, 8));
        consoleHeader.setOpaque(false);

        JLabel lblConsoleTitle = new JLabel("Consola de Consultas SQL Directa");
        lblConsoleTitle.setFont(UITheme.FONT_SUBTITLE);
        consoleHeader.add(lblConsoleTitle, BorderLayout.WEST);
        consoleHeader.add(cmbQuickQueries, BorderLayout.EAST);

        cmbQuickQueries.addActionListener(e -> {
            int idx = cmbQuickQueries.getSelectedIndex();
            if (idx > 0) {
                txtSql.setText((String) cmbQuickQueries.getSelectedItem());
            }
        });

        consoleCard.add(consoleHeader, BorderLayout.NORTH);

        // SQL Input & Run Button
        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setOpaque(false);

        txtSql.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtSql.setLineWrap(true);
        txtSql.setBackground(new Color(15, 23, 42));
        txtSql.setForeground(new Color(248, 250, 252));
        txtSql.setCaretColor(Color.WHITE);
        txtSql.setBorder(new EmptyBorder(8, 8, 8, 8));

        JButton btnRunSql = UITheme.createPrimaryButton("Ejecutar SQL");
        btnRunSql.setPreferredSize(new Dimension(140, 40));
        btnRunSql.addActionListener(e -> runQuery());

        inputPanel.add(new JScrollPane(txtSql), BorderLayout.CENTER);
        inputPanel.add(btnRunSql, BorderLayout.EAST);

        // Middle container for input and results
        JPanel midPanel = new JPanel(new BorderLayout(8, 8));
        midPanel.setOpaque(false);
        midPanel.add(inputPanel, BorderLayout.NORTH);

        UITheme.formatTable(tableResult);
        midPanel.add(new JScrollPane(tableResult), BorderLayout.CENTER);

        lblQueryStatus.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblQueryStatus.setForeground(UITheme.COLOR_TEXT_MUTED);
        midPanel.add(lblQueryStatus, BorderLayout.SOUTH);

        consoleCard.add(midPanel, BorderLayout.CENTER);
        centerPanel.add(consoleCard, BorderLayout.CENTER);

        this.add(centerPanel, BorderLayout.CENTER);

        refreshPanel();
    }

    public void refreshPanel() {
        lblDbStatus.setText("" + db.getConnectionStatus() + " | Driver: org.sqlite.JDBC (SQLite 3)");
        lblDbPath.setText("Archivo activo: " + db.getCurrentDbPath());

        // Actualizar estadísticas de tablas
        pnlStats.removeAll();
        Map<String, Integer> counts = db.getTableCounts();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            JPanel statBadge = new JPanel(new BorderLayout(4, 2));
            statBadge.setBackground(new Color(15, 23, 42));
            statBadge.setBorder(new EmptyBorder(6, 10, 6, 10));
            statBadge.putClientProperty("FlatLaf.style", "arc: 8");

            JLabel lblTName = new JLabel(entry.getKey());
            lblTName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblTName.setForeground(new Color(203, 213, 225));

            JLabel lblTCount = new JLabel(entry.getValue() + " filas");
            lblTCount.setFont(UITheme.FONT_REGULAR);
            lblTCount.setForeground(UITheme.COLOR_ACCENT);

            statBadge.add(lblTName, BorderLayout.WEST);
            statBadge.add(lblTCount, BorderLayout.EAST);
            pnlStats.add(statBadge);
        }
        pnlStats.revalidate();
        pnlStats.repaint();
    }

    private void runQuery() {
        String sql = txtSql.getText().trim();
        if (sql.isEmpty()) return;

        try {
            long start = System.currentTimeMillis();
            DatabaseService.QueryResult qr = db.executeCustomQuery(sql);
            long elapsed = System.currentTimeMillis() - start;

            queryResultModel.setRowCount(0);
            queryResultModel.setColumnCount(0);

            for (String col : qr.columns) {
                queryResultModel.addColumn(col);
            }

            for (Object[] row : qr.rows) {
                queryResultModel.addRow(row);
            }

            lblQueryStatus.setText("✅ " + qr.message + " (" + elapsed + " ms)");
            lblQueryStatus.setForeground(UITheme.COLOR_SUCCESS);
            refreshPanel();
            if (onStatusChange != null) onStatusChange.run();
        } catch (Exception ex) {
            lblQueryStatus.setText("Error SQL: " + ex.getMessage());
            lblQueryStatus.setForeground(UITheme.COLOR_DANGER);
        }
    }

    private void chooseDatabaseFile() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("Seleccionar archivo de base de datos SQLite (.db)");
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            boolean ok = db.initConnection(selected.getAbsolutePath());
            if (ok) {
                JOptionPane.showMessageDialog(this, "Conectado a la base de datos: " + selected.getName(), "Éxito", JOptionPane.INFORMATION_MESSAGE);
                refreshPanel();
                if (onStatusChange != null) onStatusChange.run();
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo abrir la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void resetDatabase() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Deseas reiniciar la base de datos SQLite y vaciar todas las tablas?\n" +
                "Esto re-creará las tablas completamente vacías sin ningún registro.",
                "Confirmar Limpieza de BD", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                db.resetDatabase();
                JOptionPane.showMessageDialog(this, "Base de datos limpiada correctamente. Todas las tablas están vacías.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                refreshPanel();
                if (onStatusChange != null) onStatusChange.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al reiniciar base de datos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showSqlScriptDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Script DDL SQLite - schema.sql", true);
        dlg.setSize(650, 520);
        dlg.setLocationRelativeTo(this);

        JTextArea ta = new JTextArea();
        ta.setFont(new Font("Consolas", Font.PLAIN, 12));
        ta.setEditable(false);
        try {
            ta.setText(new String(Files.readAllBytes(Paths.get("schema.sql"))));
        } catch (Exception ex) {
            ta.setText("No se pudo cargar schema.sql: " + ex.getMessage());
        }

        dlg.add(new JScrollPane(ta));
        dlg.setVisible(true);
    }

    private void showConfigDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Configuración de Conexión de Base de Datos", true);
        dlg.setSize(520, 420);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> cmbType = new JComboBox<>(new String[]{"MariaDB (Remoto - Raspberry Pi)", "SQLite (Local)"});
        if (!db.isMariaDb()) {
            cmbType.setSelectedIndex(1);
        }

        JTextField txtHost = new JTextField(db.getHost());
        JTextField txtPort = new JTextField(String.valueOf(db.getPort()));
        JTextField txtDbName = new JTextField(db.getDbName());
        JTextField txtUser = new JTextField(db.getUser());
        JPasswordField txtPass = new JPasswordField(db.getPassword());
        JTextField txtSqlitePath = new JTextField(db.getSqlitePath());

        Runnable toggleFields = () -> {
            boolean isM = cmbType.getSelectedIndex() == 0;
            txtHost.setEnabled(isM);
            txtPort.setEnabled(isM);
            txtDbName.setEnabled(isM);
            txtUser.setEnabled(isM);
            txtPass.setEnabled(isM);
            txtSqlitePath.setEnabled(!isM);
        };
        cmbType.addActionListener(e -> toggleFields.run());
        toggleFields.run();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        form.add(new JLabel("Motor / Modo:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        form.add(cmbType, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("IP Host (Tailscale):"), gbc);
        gbc.gridx = 1;
        form.add(txtHost, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 1;
        form.add(txtPort, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        form.add(new JLabel("Nombre Base de Datos:"), gbc);
        gbc.gridx = 1;
        form.add(txtDbName, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        form.add(new JLabel("Usuario MariaDB:"), gbc);
        gbc.gridx = 1;
        form.add(txtUser, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        form.add(new JLabel("Contraseña:"), gbc);
        gbc.gridx = 1;
        form.add(txtPass, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        form.add(new JLabel("Ruta SQLite (.db):"), gbc);
        gbc.gridx = 1;
        form.add(txtSqlitePath, gbc);

        dlg.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnCancelar = UITheme.createSecondaryButton("Cancelar");
        btnCancelar.addActionListener(e -> dlg.dispose());

        JButton btnGuardar = UITheme.createPrimaryButton("Guardar y Conectar");
        btnGuardar.addActionListener(e -> {
            String selectedType = cmbType.getSelectedIndex() == 0 ? "mariadb" : "sqlite";
            String h = txtHost.getText().trim();
            int p = 3306;
            try { p = Integer.parseInt(txtPort.getText().trim()); } catch (Exception ignored) {}
            String dbN = txtDbName.getText().trim();
            String u = txtUser.getText().trim();
            String pwd = new String(txtPass.getPassword()).trim();
            String sqP = txtSqlitePath.getText().trim();

            db.saveProperties(selectedType, h, p, dbN, u, pwd, sqP);
            boolean ok = db.initConnectionFromConfig();
            if (ok) {
                JOptionPane.showMessageDialog(dlg, "¡Conexión establecida correctamente!\n" + db.getConnectionStatus(), "Conexión Exitosa", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
                refreshPanel();
                if (onStatusChange != null) onStatusChange.run();
            } else {
                JOptionPane.showMessageDialog(dlg, "Fallo al conectar:\n" + db.getConnectionStatus() + "\n\nRevisa la IP de Tailscale, el puerto 3306 y las credenciales.", "Error de Conexión", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(btnCancelar);
        btnPanel.add(btnGuardar);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }
}
