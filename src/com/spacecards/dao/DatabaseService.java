package com.spacecards.dao;

import com.spacecards.model.*;

import java.io.File;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio Central de Acceso a Datos (DAO) para Space Cards Manager.
 * Implementa persistencia real con SQLite mediante JDBC, soportando
 * todas las operaciones CRUD y control transaccional para el TCG Store.
 */
public class DatabaseService {
    /** Data model for a supported TCG franchise. */
    public static class Franquicia {
        private int idFranquicia;
        private String nombre;
        private String fabricante;
        private String descripcion;
        private boolean activo;
        private LocalDateTime fechaRegistro;

        public Franquicia() {
        }

        public Franquicia(int idFranquicia, String nombre, String fabricante,
                          String descripcion, boolean activo) {
            this.idFranquicia = idFranquicia;
            this.nombre = nombre;
            this.fabricante = fabricante;
            this.descripcion = descripcion;
            this.activo = activo;
        }

        public int getIdFranquicia() { return idFranquicia; }
        public void setIdFranquicia(int idFranquicia) { this.idFranquicia = idFranquicia; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getFabricante() { return fabricante; }
        public void setFabricante(String fabricante) { this.fabricante = fabricante; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }
        public LocalDateTime getFechaRegistro() { return fechaRegistro; }
        public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    }

    public static class MovimientoCorteCaja {
        private final int referencia;
        private final LocalDateTime fecha;
        private final String tipo;
        private final String cliente;
        private final String producto;
        private final BigDecimal monto;
        private final String metodoPago;

        public MovimientoCorteCaja(int referencia, LocalDateTime fecha, String tipo,
                                   String cliente, String producto, BigDecimal monto, String metodoPago) {
            this.referencia = referencia;
            this.fecha = fecha;
            this.tipo = tipo;
            this.cliente = cliente;
            this.producto = producto;
            this.monto = monto;
            this.metodoPago = metodoPago;
        }

        public int getReferencia() { return referencia; }
        public LocalDateTime getFecha() { return fecha; }
        public String getTipo() { return tipo; }
        public String getCliente() { return cliente; }
        public String getProducto() { return producto; }
        public BigDecimal getMonto() { return monto; }
        public String getMetodoPago() { return metodoPago; }
    }

    private static DatabaseService instance;

    private static final String DEFAULT_DB_FILE = "spacecards.db";
        private static final String[][] SQLITE_BACKUP_TABLES = {
            {"cliente", "id_cliente", "nombre", "apellido", "correo_electronico", "telefono", "fecha_registro"},
            {"usuario", "id_usuario", "nombre", "apellido", "rol", "horario", "activo", "password_hash", "must_change_password"},
            {"producto", "id_producto", "codigo_barras", "nombre", "descripcion", "categoria", "juego_tcg", "precio_unitario", "porcentaje_apartado", "stock", "id_torneo", "tipo_venta", "fecha_salida"},
            {"categoria_producto", "id_categoria", "nombre", "descripcion", "activo", "fecha_registro"},
            {"franquicia", "id_franquicia", "nombre", "fabricante", "descripcion", "activo", "fecha_registro"},
            {"venta", "id_venta", "id_cliente", "id_usuario", "id_preventa", "fecha_venta", "monto_total", "metodo_pago", "monto_saldo_favor"},
            {"detalle_venta", "id_detalle", "id_venta", "id_producto", "cantidad", "precio_historico"},
            {"preventa", "id_preventa", "id_cliente", "id_usuario", "id_producto", "monto_anticipo", "monto_total", "fecha_solicitud", "fecha_estimada_entrega", "fecha_vencimiento_apartado", "estado"},
            {"torneo", "id_torneo", "id_organizador", "nombre_torneo", "juego_tcg", "fecha_evento", "costo_inscripcion", "cupo_maximo", "estado", "porcentaje_premios_monetarios"},
            {"participante_torneo", "id_inscripcion", "id_torneo", "id_cliente", "id_jugador_tcg", "fecha_inscripcion", "pago_confirmado", "posicion_final", "premio", "premio_inscripcion_otorgado"},
            {"premio_inscripcion_otorgado", "id_premio", "id_torneo", "id_cliente", "id_inscripcion", "id_jugador_tcg", "fecha_otorgamiento", "descripcion"},
            {"saldo_favor", "id_saldo", "id_cliente", "monto_original", "saldo_disponible", "fecha_emision", "fecha_vencimiento", "tipo_origen", "id_origen"},
            {"producto_premio", "id_producto_premio", "nombre", "patrocinador", "descripcion", "stock"},
            {"premio_inscripcion_torneo", "id_torneo", "tipo_premio", "id_producto", "id_producto_premio", "monto"},
            {"premio_torneo", "id_torneo", "posicion", "tipo_premio", "id_producto", "id_producto_premio", "porcentaje_fondo"},
            {"configuracion_sistema", "clave", "valor"}
        };
    private String currentDbPath = DEFAULT_DB_FILE;
    private Connection connection;
        private Connection backupConnection;
    private String connectionStatus = "Inicializando Base de Datos...";
        private String backupStatus = "Respaldo SQLite no inicializado";
        private boolean suspendBackupSync;
    private boolean automaticSqliteFallback;

    private String dbType = "mariadb"; // "mariadb" o "sqlite"
    private String host = "100.x.y.z";
    private int port = 3306;
    private String dbName = "spacecards";
    private String user = "spaceuser";
    private String password = "spacepassword123";
    private String sqlitePath = DEFAULT_DB_FILE;
    private final Properties dbProps = new Properties();

    private final DateTimeFormatter dtfDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DatabaseService() {
        loadProperties();
        initConnectionFromConfig();
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    public synchronized void loadProperties() {
        File propFile = new File("database.properties");
        if (propFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(propFile)) {
                dbProps.load(fis);
                dbType = dbProps.getProperty("db.type", "mariadb").trim().toLowerCase();
                host = dbProps.getProperty("db.host", "100.x.y.z").trim();
                try {
                    port = Integer.parseInt(dbProps.getProperty("db.port", "3306").trim());
                } catch (NumberFormatException ignored) {
                    port = 3306;
                }
                dbName = dbProps.getProperty("db.name", "spacecards").trim();
                user = dbProps.getProperty("db.user", "spaceuser").trim();
                password = dbProps.getProperty("db.password", "spacepassword123").trim();
                sqlitePath = dbProps.getProperty("db.sqlite.file", DEFAULT_DB_FILE).trim();
            } catch (Exception ex) {
                System.err.println("Error al cargar database.properties: " + ex.getMessage());
            }
        }
    }

    private void validarUsuarioActivo(Connection conn, int idUsuario) throws SQLException {
        if (idUsuario <= 0) throw new SQLException("Se requiere una sesión de usuario activa.");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT activo FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("activo") == 0) {
                    throw new SQLException("El usuario de la sesión no existe o está inactivo.");
                }
            }
        }
    }

    public synchronized void saveProperties(String type, String h, int p, String db, String u, String pwd, String sqPath) {
        this.dbType = type.trim().toLowerCase();
        this.host = h.trim();
        this.port = p;
        this.dbName = db.trim();
        this.user = u.trim();
        this.password = pwd.trim();
        this.sqlitePath = sqPath.trim();

        dbProps.setProperty("db.type", this.dbType);
        dbProps.setProperty("db.host", this.host);
        dbProps.setProperty("db.port", String.valueOf(this.port));
        dbProps.setProperty("db.name", this.dbName);
        dbProps.setProperty("db.user", this.user);
        dbProps.setProperty("db.password", this.password);
        dbProps.setProperty("db.sqlite.file", this.sqlitePath);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream("database.properties")) {
            dbProps.store(fos, "Configuracion de Base de Datos - Space Cards Manager");
        } catch (Exception ex) {
            System.err.println("Error al guardar database.properties: " + ex.getMessage());
        }
    }

    public synchronized boolean initConnectionFromConfig() {
        if ("mariadb".equalsIgnoreCase(dbType)) {
            boolean ok = initMariaDbConnection(host, port, dbName, user, password);
            if (!ok) {
                System.out.println("Aviso: No se pudo conectar a MariaDB remoto (" + host + "). Activando fallback a SQLite local...");
                boolean sqliteReady = initConnection(sqlitePath);
                automaticSqliteFallback = sqliteReady;
                return sqliteReady;
            }
            return true;
        } else {
            return initConnection(sqlitePath);
        }
    }

    /**
     * Inicializa conexión remota con MariaDB (Raspberry Pi).
     */
    public synchronized boolean initMariaDbConnection(String host, int port, String dbName, String user, String password) {
        automaticSqliteFallback = false;
        this.dbType = "mariadb";
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.user = user;
        this.password = password;
        try {
            if (backupConnection != null) {
                try { backupConnection.close(); } catch (SQLException ignored) {}
                backupConnection = null;
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            try {
                Class.forName("org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName +
                    "?permitMysqlScheme=true&autoReconnect=true&connectTimeout=4000&socketTimeout=10000";
            this.connection = DriverManager.getConnection(url, user, password);

            boolean wasSuspended = suspendBackupSync;
            suspendBackupSync = true;
            try {
                createTablesIfNotExist();

                if (isTableEmpty("franquicia")) {
                    seedFranquicias();
                }
                if (isTableEmpty("producto")) {
                    seedSampleData();
                }
            } finally {
                suspendBackupSync = wasSuspended;
            }

            try {
                initializeSqliteBackup();
                syncSqliteBackup();
            } catch (Exception backupEx) {
                backupStatus = "Respaldo SQLite no disponible: " + backupEx.getMessage();
            }

            connectionStatus = "MariaDB Remoto Activo (" + host + ":" + port + " / " + dbName + ")";
            return true;
        } catch (Exception ex) {
            connectionStatus = "Error de conexión MariaDB (" + host + "): " + ex.getMessage();
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Inicializa la conexión SQLite local y crea las tablas si no existen.
     */
    public synchronized boolean initConnection(String dbPath) {
        automaticSqliteFallback = false;
        this.dbType = "sqlite";
        this.currentDbPath = dbPath;
        try {
            if (backupConnection != null) {
                try { backupConnection.close(); } catch (SQLException ignored) {}
                backupConnection = null;
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }

            createTablesIfNotExist();

            if (isTableEmpty("franquicia")) {
                seedFranquicias();
            }
            if (isTableEmpty("producto")) {
                seedSampleData();
            }

            File f = new File(dbPath);
            connectionStatus = "SQLite 3 Activo (" + f.getName() + " - " + (f.length() / 1024) + " KB)";
            backupStatus = "SQLite es la base activa";
            return true;
        } catch (Exception ex) {
            connectionStatus = "Error de conexión SQLite: " + ex.getMessage();
            ex.printStackTrace();
            return false;
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if ("mariadb".equalsIgnoreCase(dbType)) {
            boolean remoteAvailable = false;
            try {
                remoteAvailable = connection != null && !connection.isClosed() && connection.isValid(2);
            } catch (SQLException ignored) {}

            if (!remoteAvailable) {
                if (connection != null) {
                    try { connection.close(); } catch (SQLException ignored) {}
                }
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName +
                        "?permitMysqlScheme=true&autoReconnect=true&connectTimeout=6000&socketTimeout=15000";
                try {
                    connection = DriverManager.getConnection(url, user, password);
                } catch (SQLException remoteFailure) {
                    if (initConnection(sqlitePath)) {
                        automaticSqliteFallback = true;
                        return connection;
                    }
                    throw remoteFailure;
                }
            }
            return connection;
        }

        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + currentDbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }
        }
        return connection;
    }

    private void initializeSqliteBackup() throws Exception {
        if (backupConnection != null && !backupConnection.isClosed()) {
            createTablesIfNotExist(backupConnection, "sqlite");
            backupStatus = "Respaldo SQLite preparado";
            return;
        }
        Class.forName("org.sqlite.JDBC");
        backupConnection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
        try (Statement st = backupConnection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        createTablesIfNotExist(backupConnection, "sqlite");
        backupStatus = "Respaldo SQLite preparado";
    }

    private void syncSqliteBackup() {
        if (!"mariadb".equalsIgnoreCase(dbType) || suspendBackupSync) {
            return;
        }

        Connection source = null;
        boolean sourceAutoCommit = true;
        boolean sourceSnapshotStarted = false;
        boolean backupAutoCommit = true;
        try {
            if (backupConnection == null || backupConnection.isClosed()) {
                initializeSqliteBackup();
            }

            source = getConnection();
            sourceAutoCommit = source.getAutoCommit();
            if (sourceAutoCommit) {
                source.setAutoCommit(false);
                sourceSnapshotStarted = true;
            }

            backupAutoCommit = backupConnection.getAutoCommit();
            try (Statement st = backupConnection.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF;");
            }
            if (backupAutoCommit) {
                backupConnection.setAutoCommit(false);
            }

            for (int i = SQLITE_BACKUP_TABLES.length - 1; i >= 0; i--) {
                try (Statement st = backupConnection.createStatement()) {
                    st.executeUpdate("DELETE FROM " + SQLITE_BACKUP_TABLES[i][0]);
                }
            }
            for (String[] table : SQLITE_BACKUP_TABLES) {
                copyTableToSqliteBackup(source, backupConnection, table);
            }

            backupConnection.commit();
            if (sourceSnapshotStarted) {
                source.commit();
            }
            backupStatus = "Respaldo SQLite sincronizado";
        } catch (Exception ex) {
            if (backupConnection != null) {
                try { backupConnection.rollback(); } catch (SQLException ignored) {}
            }
            if (sourceSnapshotStarted && source != null) {
                try { source.rollback(); } catch (SQLException ignored) {}
            }
            backupStatus = "Respaldo SQLite desactualizado: " + ex.getMessage();
            System.err.println(backupStatus);
        } finally {
            if (backupConnection != null) {
                try {
                    if (backupConnection.getAutoCommit() != backupAutoCommit) {
                        backupConnection.setAutoCommit(backupAutoCommit);
                    }
                    try (Statement st = backupConnection.createStatement()) {
                        st.execute("PRAGMA foreign_keys = ON;");
                    }
                } catch (SQLException ignored) {}
            }
            if (sourceSnapshotStarted && source != null) {
                try { source.setAutoCommit(sourceAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }

    private void copyTableToSqliteBackup(Connection source, Connection target, String[] table) throws SQLException {
        String tableName = table[0];
        String columns = String.join(", ", Arrays.copyOfRange(table, 1, table.length));
        String placeholders = String.join(", ", Collections.nCopies(table.length - 1, "?"));
        String insertSql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";

        try (Statement sourceStatement = source.createStatement();
             ResultSet rows = sourceStatement.executeQuery("SELECT " + columns + " FROM " + tableName);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            while (rows.next()) {
                for (int i = 1; i < table.length; i++) {
                    insert.setString(i, rows.getString(i));
                }
                insert.executeUpdate();
            }
        }
    }

    public static String hashPassword(String rawPassword) {
        if (rawPassword == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error al generar hash de contraseña", ex);
        }
    }

    public synchronized boolean authenticateUsuario(Usuario usuario, String password) {
        if (usuario == null || password == null || password.trim().isEmpty()) {
            return false;
        }
        return usuario.isPasswordValid(password);
    }

    public synchronized String resetPasswordForUser(int idUsuario) {
        String nombre;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT nombre FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("No se encontró el usuario que se desea restablecer.");
                }
                nombre = rs.getString("nombre");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el usuario: " + ex.getMessage(), ex);
        }

        String temporaryPassword = Usuario.generateTemporaryPassword(nombre);
        String sql = "UPDATE usuario SET password_hash = ?, must_change_password = 1 WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, hashPassword(temporaryPassword));
            ps.setInt(2, idUsuario);
            if (ps.executeUpdate() == 0) {
                throw new RuntimeException("No se encontró el usuario que se desea restablecer.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al restablecer contraseña: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
        return temporaryPassword;
    }

    public synchronized boolean setPasswordForUser(int idUsuario, String newPassword) {
        if (idUsuario <= 0 || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        String sql = "UPDATE usuario SET password_hash = ?, must_change_password = 0 WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, hashPassword(newPassword.trim()));
            ps.setInt(2, idUsuario);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                syncSqliteBackup();
                return true;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al cambiar contraseña: " + ex.getMessage(), ex);
        }
        return false;
    }

    private void createTablesIfNotExist() throws SQLException {
        createTablesIfNotExist(getConnection(), dbType);
    }

    private void createTablesIfNotExist(Connection targetConnection, String targetDbType) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            if ("mariadb".equalsIgnoreCase(targetDbType)) {
                // TABLA: CLIENTE (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS cliente (" +
                        "id_cliente INT AUTO_INCREMENT PRIMARY KEY," +
                        "nombre VARCHAR(50) NOT NULL," +
                        "apellido VARCHAR(50) NOT NULL," +
                        "correo_electronico VARCHAR(100) NOT NULL UNIQUE," +
                        "telefono VARCHAR(20) NOT NULL," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;");

                // TABLA: USUARIO (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS usuario (" +
                        "id_usuario INT AUTO_INCREMENT PRIMARY KEY," +
                        "nombre VARCHAR(50) NOT NULL," +
                        "apellido VARCHAR(50) NOT NULL," +
                        "rol VARCHAR(30) NOT NULL CHECK (rol IN ('Administrador', 'Vendedor', 'Organizador_Torneos'))," +
                        "horario VARCHAR(50) NOT NULL," +
                        "activo BOOLEAN DEFAULT 1," +
                        "password_hash VARCHAR(255) NOT NULL DEFAULT ''," +
                        "must_change_password BOOLEAN NOT NULL DEFAULT 0) ENGINE=InnoDB;");

                // TABLA: PRODUCTO (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS producto (" +
                        "id_producto INT AUTO_INCREMENT PRIMARY KEY," +
                        "codigo_barras VARCHAR(50) UNIQUE," +
                        "nombre VARCHAR(100) NOT NULL," +
                        "descripcion TEXT," +
                        "categoria VARCHAR(50) NOT NULL," +
                        "juego_tcg VARCHAR(50) DEFAULT 'Pokémon'," +
                        "precio_unitario DECIMAL(10,2) NOT NULL CHECK (precio_unitario >= 0)," +
                        "porcentaje_apartado DECIMAL(5,2) NOT NULL DEFAULT 30.00 CHECK (porcentaje_apartado > 0 AND porcentaje_apartado <= 100)," +
                        "stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0)," +
                        "id_torneo INT NULL," +
                        "tipo_venta VARCHAR(20) NOT NULL DEFAULT 'VENTA_NORMAL'," +
                        "fecha_salida DATE NULL) ENGINE=InnoDB;");

                    st.execute("CREATE TABLE IF NOT EXISTS categoria_producto (" +
                        "id_categoria INT AUTO_INCREMENT PRIMARY KEY," +
                        "nombre VARCHAR(80) NOT NULL UNIQUE," +
                        "descripcion TEXT," +
                        "activo BOOLEAN NOT NULL DEFAULT 1," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;");

                // TABLA: VENTA (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS venta (" +
                        "id_venta INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_cliente INT NULL," +
                        "id_usuario INT NOT NULL," +
                        "id_preventa INT NULL," +
                        "fecha_venta DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "monto_total DECIMAL(10,2) NOT NULL DEFAULT 0.00," +
                        "metodo_pago VARCHAR(30) NOT NULL CHECK (metodo_pago IN ('Efectivo', 'Tarjeta', 'Transferencia'))," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente) ON DELETE SET NULL," +
                        "FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)) ENGINE=InnoDB;");

                // TABLA: DETALLE_VENTA (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS detalle_venta (" +
                        "id_detalle INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_venta INT NOT NULL," +
                        "id_producto INT NOT NULL," +
                        "cantidad INT NOT NULL CHECK (cantidad > 0)," +
                        "precio_historico DECIMAL(10,2) NOT NULL," +
                        "subtotal DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio_historico) STORED," +
                        "FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_producto) REFERENCES producto(id_producto)) ENGINE=InnoDB;");

                // TABLA: PREVENTA (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS preventa (" +
                        "id_preventa INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_cliente INT NOT NULL," +
                        "id_usuario INT NOT NULL," +
                        "id_producto INT NOT NULL," +
                        "monto_anticipo DECIMAL(10,2) NOT NULL CHECK (monto_anticipo >= 0)," +
                        "monto_total DECIMAL(10,2) NOT NULL CHECK (monto_total >= monto_anticipo)," +
                        "fecha_solicitud DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "fecha_estimada_entrega DATE NOT NULL," +
                        "fecha_vencimiento_apartado DATE NULL," +
                        "estado VARCHAR(20) DEFAULT 'Pendiente' CHECK (estado IN ('Pendiente', 'Entregado', 'Cancelado'))," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)," +
                        "FOREIGN KEY (id_producto) REFERENCES producto(id_producto)) ENGINE=InnoDB;");

                // TABLA: TORNEO (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS torneo (" +
                        "id_torneo INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_organizador INT NOT NULL," +
                        "nombre_torneo VARCHAR(100) NOT NULL," +
                        "juego_tcg VARCHAR(50) NOT NULL," +
                        "fecha_evento DATETIME NOT NULL," +
                        "costo_inscripcion DECIMAL(10,2) NOT NULL CHECK (costo_inscripcion >= 0)," +
                        "cupo_maximo INT NOT NULL CHECK (cupo_maximo > 0)," +
                        "estado VARCHAR(20) DEFAULT 'Programado' CHECK (estado IN ('Programado', 'En Curso', 'Finalizado', 'Cancelado'))," +
                        "FOREIGN KEY (id_organizador) REFERENCES usuario(id_usuario)) ENGINE=InnoDB;");

                // TABLA: PARTICIPANTE_TORNEO (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS participante_torneo (" +
                        "id_inscripcion INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_torneo INT NOT NULL," +
                        "id_cliente INT NOT NULL," +
                        "id_jugador_tcg VARCHAR(50)," +
                        "fecha_inscripcion DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "pago_confirmado BOOLEAN DEFAULT 0," +
                        "posicion_final INT NULL," +
                        "premio VARCHAR(255) NULL," +
                        "premio_inscripcion_otorgado VARCHAR(255) NULL," +
                        "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente)) ENGINE=InnoDB;");

                // TABLA: FRANQUICIA (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS franquicia (" +
                        "id_franquicia INT AUTO_INCREMENT PRIMARY KEY," +
                        "nombre VARCHAR(50) NOT NULL UNIQUE," +
                        "fabricante VARCHAR(50)," +
                        "descripcion TEXT," +
                        "activo BOOLEAN DEFAULT 1," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;");

                st.execute("CREATE TABLE IF NOT EXISTS configuracion_sistema (" +
                        "clave VARCHAR(100) PRIMARY KEY," +
                        "valor VARCHAR(255) NOT NULL) ENGINE=InnoDB;");
            } else {
                // TABLA: CLIENTE (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS cliente (" +
                        "id_cliente INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "nombre VARCHAR(50) NOT NULL," +
                        "apellido VARCHAR(50) NOT NULL," +
                        "correo_electronico VARCHAR(100) NOT NULL UNIQUE," +
                        "telefono VARCHAR(20) NOT NULL," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP);");

                // TABLA: USUARIO (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS usuario (" +
                        "id_usuario INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "nombre VARCHAR(50) NOT NULL," +
                        "apellido VARCHAR(50) NOT NULL," +
                        "rol VARCHAR(30) NOT NULL CHECK (rol IN ('Administrador', 'Vendedor', 'Organizador_Torneos'))," +
                        "horario VARCHAR(50) NOT NULL," +
                        "activo BOOLEAN DEFAULT 1," +
                        "password_hash VARCHAR(255) NOT NULL DEFAULT ''," +
                        "must_change_password BOOLEAN NOT NULL DEFAULT 0);");

                // TABLA: PRODUCTO (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS producto (" +
                        "id_producto INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "codigo_barras VARCHAR(50) UNIQUE," +
                        "nombre VARCHAR(100) NOT NULL," +
                        "descripcion TEXT," +
                        "categoria VARCHAR(50) NOT NULL," +
                        "juego_tcg VARCHAR(50) DEFAULT 'Pokémon'," +
                        "precio_unitario DECIMAL(10,2) NOT NULL CHECK (precio_unitario >= 0)," +
                        "porcentaje_apartado DECIMAL(5,2) NOT NULL DEFAULT 30.00 CHECK (porcentaje_apartado > 0 AND porcentaje_apartado <= 100)," +
                        "stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0)," +
                        "id_torneo INT NULL," +
                        "tipo_venta VARCHAR(20) NOT NULL DEFAULT 'VENTA_NORMAL'," +
                        "fecha_salida DATE NULL);");

                    st.execute("CREATE TABLE IF NOT EXISTS categoria_producto (" +
                        "id_categoria INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "nombre VARCHAR(80) NOT NULL UNIQUE," +
                        "descripcion TEXT," +
                        "activo BOOLEAN NOT NULL DEFAULT 1," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP);");

                // TABLA: VENTA (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS venta (" +
                        "id_venta INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_cliente INT NULL," +
                        "id_usuario INT NOT NULL," +
                        "id_preventa INT NULL," +
                        "fecha_venta DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "monto_total DECIMAL(10,2) NOT NULL DEFAULT 0.00," +
                        "metodo_pago VARCHAR(30) NOT NULL CHECK (metodo_pago IN ('Efectivo', 'Tarjeta', 'Transferencia'))," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente) ON DELETE SET NULL," +
                        "FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario));");

                // TABLA: DETALLE_VENTA (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS detalle_venta (" +
                        "id_detalle INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_venta INT NOT NULL," +
                        "id_producto INT NOT NULL," +
                        "cantidad INT NOT NULL CHECK (cantidad > 0)," +
                        "precio_historico DECIMAL(10,2) NOT NULL," +
                        "subtotal DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio_historico) STORED," +
                        "FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_producto) REFERENCES producto(id_producto));");

                // TABLA: PREVENTA (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS preventa (" +
                        "id_preventa INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_cliente INT NOT NULL," +
                        "id_usuario INT NOT NULL," +
                        "id_producto INT NOT NULL," +
                        "monto_anticipo DECIMAL(10,2) NOT NULL CHECK (monto_anticipo >= 0)," +
                        "monto_total DECIMAL(10,2) NOT NULL CHECK (monto_total >= monto_anticipo)," +
                        "fecha_solicitud DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "fecha_estimada_entrega DATE NOT NULL," +
                        "fecha_vencimiento_apartado DATE NULL," +
                        "estado VARCHAR(20) DEFAULT 'Pendiente' CHECK (estado IN ('Pendiente', 'Entregado', 'Cancelado'))," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)," +
                        "FOREIGN KEY (id_producto) REFERENCES producto(id_producto));");

                // TABLA: TORNEO (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS torneo (" +
                        "id_torneo INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_organizador INT NOT NULL," +
                        "nombre_torneo VARCHAR(100) NOT NULL," +
                        "juego_tcg VARCHAR(50) NOT NULL," +
                        "fecha_evento DATETIME NOT NULL," +
                        "costo_inscripcion DECIMAL(10,2) NOT NULL CHECK (costo_inscripcion >= 0)," +
                        "cupo_maximo INT NOT NULL CHECK (cupo_maximo > 0)," +
                        "estado VARCHAR(20) DEFAULT 'Programado' CHECK (estado IN ('Programado', 'En Curso', 'Finalizado', 'Cancelado'))," +
                        "FOREIGN KEY (id_organizador) REFERENCES usuario(id_usuario));");

                // TABLA: PARTICIPANTE_TORNEO (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS participante_torneo (" +
                        "id_inscripcion INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_torneo INT NOT NULL," +
                        "id_cliente INT NOT NULL," +
                        "id_jugador_tcg VARCHAR(50)," +
                        "fecha_inscripcion DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "pago_confirmado BOOLEAN DEFAULT 0," +
                        "posicion_final INT NULL," +
                        "premio VARCHAR(255) NULL," +
                        "premio_inscripcion_otorgado VARCHAR(255) NULL," +
                        "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente));");

                // TABLA: FRANQUICIA (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS franquicia (" +
                        "id_franquicia INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "nombre VARCHAR(50) NOT NULL UNIQUE," +
                        "fabricante VARCHAR(50)," +
                        "descripcion TEXT," +
                        "activo BOOLEAN DEFAULT 1," +
                        "fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP);");

                st.execute("CREATE TABLE IF NOT EXISTS configuracion_sistema (" +
                        "clave VARCHAR(100) PRIMARY KEY," +
                        "valor VARCHAR(255) NOT NULL);");
            }
        }
        ensureProductSaleTypeColumn(targetConnection);
        ensureProductReleaseDateColumn(targetConnection);
        ensureProductDepositPercentageColumn(targetConnection);
        ensureProductTournamentColumn(targetConnection);
        ensureParticipantPrizeColumn(targetConnection);
        ensureParticipationPrizeAwardColumn(targetConnection);
        ensurePreventaSaleColumn(targetConnection);
        ensurePreventaExpirationColumn(targetConnection);
        ensureUsuarioPasswordColumn(targetConnection);
        ensureUsuarioMustChangePasswordColumn(targetConnection);
        ensureDefaultProductCategories(targetConnection);
        ensureSaleCreditColumn(targetConnection);
        ensureTorneoPrizePercentageColumn(targetConnection);
        ensureAdditionalCommerceTables(targetConnection, targetDbType);
    }

    private void ensureSaleCreditColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT monto_saldo_favor FROM venta LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE venta ADD COLUMN monto_saldo_favor DECIMAL(10,2) NOT NULL DEFAULT 0.00");
            }
        }
    }

    private void ensureTorneoPrizePercentageColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT porcentaje_premios_monetarios FROM torneo LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE torneo ADD COLUMN porcentaje_premios_monetarios DECIMAL(5,2) NOT NULL DEFAULT 0.00");
            }
        }
    }

    private void ensureAdditionalCommerceTables(Connection targetConnection, String targetDbType) throws SQLException {
        boolean mariaDb = "mariadb".equalsIgnoreCase(targetDbType);
        String autoId = mariaDb ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String engine = mariaDb ? " ENGINE=InnoDB" : "";
        try (Statement st = targetConnection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS saldo_favor (" +
                    "id_saldo " + autoId + "," +
                    "id_cliente INT NOT NULL," +
                    "monto_original DECIMAL(10,2) NOT NULL CHECK (monto_original > 0)," +
                    "saldo_disponible DECIMAL(10,2) NOT NULL CHECK (saldo_disponible >= 0)," +
                    "fecha_emision DATETIME NOT NULL," +
                    "fecha_vencimiento DATE NULL," +
                    "tipo_origen VARCHAR(40) NOT NULL," +
                    "id_origen INT NOT NULL," +
                    "CONSTRAINT unique_saldo_origen UNIQUE (tipo_origen, id_origen)," +
                    "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente))" + engine);
            st.execute("CREATE TABLE IF NOT EXISTS producto_premio (" +
                    "id_producto_premio " + autoId + "," +
                    "nombre VARCHAR(100) NOT NULL," +
                    "patrocinador VARCHAR(100) NULL," +
                    "descripcion VARCHAR(255) NULL," +
                    "stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0))" + engine);
            st.execute("CREATE TABLE IF NOT EXISTS premio_torneo (" +
                    "id_torneo INT NOT NULL," +
                    "posicion INT NOT NULL CHECK (posicion > 0)," +
                    "tipo_premio VARCHAR(20) NOT NULL CHECK (tipo_premio IN ('MONETARIO', 'INVENTARIO', 'PATROCINADO'))," +
                    "id_producto INT NULL," +
                    "id_producto_premio INT NULL," +
                    "porcentaje_fondo DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_fondo >= 0 AND porcentaje_fondo <= 100)," +
                    "PRIMARY KEY (id_torneo, posicion)," +
                    "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                    "FOREIGN KEY (id_producto) REFERENCES producto(id_producto)," +
                    "FOREIGN KEY (id_producto_premio) REFERENCES producto_premio(id_producto_premio))" + engine);
            st.execute("CREATE TABLE IF NOT EXISTS premio_inscripcion_torneo (" +
                    "id_torneo INT PRIMARY KEY," +
                    "tipo_premio VARCHAR(20) NOT NULL CHECK (tipo_premio IN ('INVENTARIO', 'PATROCINADO', 'SALDO_FAVOR'))," +
                    "id_producto INT NULL," +
                    "id_producto_premio INT NULL," +
                    "monto DECIMAL(10,2) NOT NULL DEFAULT 0.00 CHECK (monto >= 0)," +
                    "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                    "FOREIGN KEY (id_producto) REFERENCES producto(id_producto)," +
                    "FOREIGN KEY (id_producto_premio) REFERENCES producto_premio(id_producto_premio))" + engine);
            st.execute("CREATE TABLE IF NOT EXISTS premio_inscripcion_otorgado (" +
                    "id_premio " + autoId + "," +
                    "id_torneo INT NOT NULL," +
                    "id_cliente INT NOT NULL," +
                    "id_inscripcion INT NOT NULL UNIQUE," +
                    "id_jugador_tcg VARCHAR(50) NULL," +
                    "fecha_otorgamiento DATETIME NOT NULL," +
                    "descripcion VARCHAR(255) NOT NULL," +
                    "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo)," +
                    "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente))" + engine);
        }
    }

    private void ensureUsuarioPasswordColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT password_hash FROM usuario LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE usuario ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT ''");
            }
        }

        try (PreparedStatement ps = targetConnection.prepareStatement(
                "UPDATE usuario SET password_hash = ? WHERE password_hash IS NULL OR TRIM(password_hash) = ''")) {
            ps.setString(1, hashPassword(Usuario.DEFAULT_PASSWORD));
            ps.executeUpdate();
        }
    }

    private void ensureUsuarioMustChangePasswordColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT must_change_password FROM usuario LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE usuario ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT 0");
            }
        }
    }

    private void ensurePreventaSaleColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT id_preventa FROM venta LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE venta ADD COLUMN id_preventa INT NULL");
            }
        }
    }

    private void ensurePreventaExpirationColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT fecha_vencimiento_apartado FROM preventa LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE preventa ADD COLUMN fecha_vencimiento_apartado DATE NULL");
            }
        }

        String sql = "SELECT id_preventa, fecha_solicitud FROM preventa WHERE fecha_vencimiento_apartado IS NULL";
        List<Integer> preventasSinVencimiento = new ArrayList<>();
        List<LocalDate> fechasVencimiento = new ArrayList<>();
        try (Statement st = targetConnection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                LocalDateTime fechaSolicitud = parseDateTime(rs.getString("fecha_solicitud"));
                LocalDate fechaBase = fechaSolicitud != null ? fechaSolicitud.toLocalDate() : LocalDate.now();
                preventasSinVencimiento.add(rs.getInt("id_preventa"));
                fechasVencimiento.add(fechaBase.plusDays(30));
            }
        }
        try (PreparedStatement update = targetConnection.prepareStatement(
                "UPDATE preventa SET fecha_vencimiento_apartado = ? WHERE id_preventa = ?")) {
            for (int i = 0; i < preventasSinVencimiento.size(); i++) {
                update.setString(1, fechasVencimiento.get(i).format(dtfDate));
                update.setInt(2, preventasSinVencimiento.get(i));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void ensureProductSaleTypeColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT tipo_venta FROM producto LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE producto ADD COLUMN tipo_venta VARCHAR(20) NOT NULL DEFAULT 'VENTA_NORMAL'");
            }
        }
    }

    private void ensureProductReleaseDateColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT fecha_salida FROM producto LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE producto ADD COLUMN fecha_salida DATE NULL");
            }
        }
    }

    private void ensureProductDepositPercentageColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT porcentaje_apartado FROM producto LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE producto ADD COLUMN porcentaje_apartado DECIMAL(5,2) NOT NULL DEFAULT 30.00");
            }
        }
    }

    private void ensureProductTournamentColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT id_torneo FROM producto LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE producto ADD COLUMN id_torneo INT NULL");
            }
        }
    }

    private void ensureParticipantPrizeColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT premio FROM participante_torneo LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE participante_torneo ADD COLUMN premio VARCHAR(255) NULL");
            }
        }
    }

    private void ensureParticipationPrizeAwardColumn(Connection targetConnection) throws SQLException {
        try (Statement st = targetConnection.createStatement()) {
            st.executeQuery("SELECT premio_inscripcion_otorgado FROM participante_torneo LIMIT 0").close();
        } catch (SQLException missingColumn) {
            try (Statement st = targetConnection.createStatement()) {
                st.executeUpdate("ALTER TABLE participante_torneo ADD COLUMN premio_inscripcion_otorgado VARCHAR(255) NULL");
            }
        }
    }

    private void ensureDefaultProductCategories(Connection targetConnection) throws SQLException {
        String[][] defaults = {
                {"Carta individual", "Cartas individuales coleccionables."},
                {"Caja de sobres", "Cajas y paquetes de sobres."},
                {"Caja de entrenador elite", "Cajas de entrenador elite y productos equivalentes."},
                {"Fundas protectoras", "Fundas y protectores para cartas."},
                {"Caja para mazo", "Cajas y accesorios para guardar mazos."},
                {"Lata", "Latas y productos coleccionables en lata."},
                {"Ticket de participación", "Inscripciones y tickets para torneos."}
        };
        try (PreparedStatement check = targetConnection.prepareStatement(
                "SELECT id_categoria FROM categoria_producto WHERE nombre = ?");
             PreparedStatement insert = targetConnection.prepareStatement(
                     "INSERT INTO categoria_producto (nombre, descripcion, activo) VALUES (?, ?, 1)");
             PreparedStatement rename = targetConnection.prepareStatement(
                     "UPDATE producto SET categoria = ? WHERE categoria = ?")) {
            for (String[] category : defaults) {
                check.setString(1, category[0]);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        insert.setString(1, category[0]);
                        insert.setString(2, category[1]);
                        insert.executeUpdate();
                    }
                }
            }
            String[][] legacyNames = {
                    {"Single Card", "Carta individual"},
                    {"Booster Box", "Caja de sobres"},
                    {"Elite Trainer Box", "Caja de entrenador elite"},
                    {"Sleeves", "Fundas protectoras"},
                    {"Deck", "Caja para mazo"},
                    {"Tin", "Lata"}
            };
            for (String[] legacy : legacyNames) {
                rename.setString(1, legacy[1]);
                rename.setString(2, legacy[0]);
                rename.executeUpdate();
            }
        }
    }

    private boolean isTableEmpty(String tableName) {
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (Exception ignored) {}
        return true;
    }

    /**
     * Inserta datos demo iniciales de Pokémon TCG para que el sistema esté listo para evaluar y usar.
     */
    public synchronized void seedSampleData() {
        try {
            // 1. Usuarios
            addUsuario(new Usuario(0, "Ash", "Ketchum", "Administrador", "Turno Mañana (08:00 - 16:00)", true));
            addUsuario(new Usuario(0, "Brock", "Harrison", "Vendedor", "Turno Tarde (14:00 - 22:00)", true));
            addUsuario(new Usuario(0, "Misty", "Waterflower", "Organizador_Torneos", "Fines de Semana (10:00 - 18:00)", true));

            // 2. Clientes
            addCliente(new Cliente(0, "Red", "Pallet", "red.kanto@pokemon.com", "311-123-4567"));
            addCliente(new Cliente(0, "Blue", "Oak", "blue.champion@pokemon.com", "311-987-6543"));
            addCliente(new Cliente(0, "Cynthia", "Shirona", "cynthia.sinnoh@pokemon.com", "311-555-8822"));
            addCliente(new Cliente(0, "Steven", "Stone", "steven.hoenn@pokemon.com", "311-444-9911"));

            // 3. Productos TCG
            addProducto(new Producto(0, "7501001", "Charizard ex #199/165 Special Art",
                    "Carta individual ultra rara de Scarlet & Violet 151", "Carta individual", "Pokémon", new BigDecimal("145.00"), 4));
            addProducto(new Producto(0, "7501002", "Scarlet & Violet: 151 Booster Bundle",
                    "Caja sellada con 6 paquetes de mejora de 151", "Caja de sobres", "Pokémon", new BigDecimal("38.50"), 18));
                Producto prismaticEtb = new Producto(0, "7501003", "Prismatic Evolutions Elite Trainer Box",
                    "ETB especial con micas de Eevee, dados y 9 sobres", "Caja de entrenador elite", "Pokémon", new BigDecimal("59.99"), 0);
                prismaticEtb.setTipoVenta(Producto.TIPO_PREVENTA);
                prismaticEtb.setFechaSalida(LocalDate.now().plusDays(5));
                addProducto(prismaticEtb);
            addProducto(new Producto(0, "7501004", "Dragon Shield Sleeves Matte (Emerald)",
                    "Paquete de 100 micas tamaño estándar textura mate", "Fundas protectoras", "Pokémon", new BigDecimal("12.50"), 35));
            addProducto(new Producto(0, "7501005", "Pikachu Ultra Pro Deck Box Alcove",
                    "Caja porta baraja de cuero sintético con cierre magnético", "Caja para mazo", "Pokémon", new BigDecimal("24.00"), 15));
            addProducto(new Producto(0, "7501006", "Mewtwo VSTAR Universe Secret Rare",
                    "Carta japonesa en condición Mint graduable", "Carta individual", "Pokémon", new BigDecimal("85.00"), 3));
                Producto surgingSparksBox = new Producto(0, "7501007", "Surging Sparks Booster Box (36 Sobres)",
                    "Caja completa de sobres sellada de fábrica con Pikachu ex tera", "Caja de sobres", "Pokémon", new BigDecimal("149.99"), 0);
                surgingSparksBox.setTipoVenta(Producto.TIPO_PREVENTA);
                surgingSparksBox.setFechaSalida(LocalDate.now().plusWeeks(2));
                addProducto(surgingSparksBox);

            // 4. Preventas
            List<Cliente> clis = getClientes();
            List<Usuario> usrs = getUsuarios();
            List<Producto> prods = getProductos();

            if (!clis.isEmpty() && !usrs.isEmpty() && prods.size() >= 7) {
                addPreventa(new Preventa(0, clis.get(0), usrs.get(1), prods.get(6),
                        new BigDecimal("50.00"), new BigDecimal("149.99"), LocalDate.now().plusWeeks(2), "Pendiente"));

                addPreventa(new Preventa(0, clis.get(2), usrs.get(0), prods.get(2),
                        new BigDecimal("59.99"), new BigDecimal("59.99"), LocalDate.now().plusDays(5), "Pendiente"));
            }

            // 5. Torneos
            if (usrs.size() >= 3) {
                Usuario uOrg = usrs.get(2);
                Torneo t1 = new Torneo(0, uOrg, "Copa Premier Tepic - Estándar Noviembre", "Pokémon",
                        LocalDateTime.now().plusDays(5).withHour(16).withMinute(0), new BigDecimal("15.00"), 32, "Programado");
                addTorneo(t1);

                Torneo t2 = new Torneo(0, uOrg, "Gym Leader Challenge (GLC) Relámpago", "Pokémon",
                        LocalDateTime.now().plusDays(2).withHour(18).withMinute(30), new BigDecimal("10.00"), 16, "En Curso");
                addTorneo(t2);

                List<Torneo> dbTorneos = getTorneos();
                if (dbTorneos.size() >= 2 && clis.size() >= 3) {
                    Torneo tSaved1 = dbTorneos.get(dbTorneos.size() - 2);
                    Torneo tSaved2 = dbTorneos.get(dbTorneos.size() - 1);

                    inscribirParticipante(new ParticipanteTorneo(0, tSaved1, clis.get(0), "POP-98213", true, null));
                    inscribirParticipante(new ParticipanteTorneo(0, tSaved1, clis.get(1), "POP-44120", true, null));
                    inscribirParticipante(new ParticipanteTorneo(0, tSaved2, clis.get(2), "POP-77192", true, null));
                }
            }

            // 6. Venta Inicial
            if (clis.size() >= 2 && usrs.size() >= 2 && prods.size() >= 4) {
                Venta v1 = new Venta(0, clis.get(1), usrs.get(1), "Tarjeta");
                v1.addDetalle(new DetalleVenta(0, 0, prods.get(3), 2, prods.get(3).getPrecioUnitario()));
                v1.addDetalle(new DetalleVenta(0, 0, prods.get(1), 1, prods.get(1).getPrecioUnitario()));
                registrarVenta(v1);
            }

        } catch (Exception ex) {
            System.err.println("Aviso al sembrar datos: " + ex.getMessage());
        }
    }

    /**
     * Registra las franquicias TCG oficiales iniciales.
     */
    public synchronized void seedFranquicias() {
        try {
            addFranquicia(new Franquicia(0, "Pokémon", "Nintendo / The Pokémon Company", "Juego de cartas coleccionables oficial de monstruos de bolsillo.", true));
            addFranquicia(new Franquicia(0, "Magic: The Gathering", "Wizards of the Coast", "El juego pionero de cartas coleccionables de estrategia y fantasía.", true));
            addFranquicia(new Franquicia(0, "Yu-Gi-Oh!", "Konami", "Juego oficial de monstruos, magias y cartas trampa.", true));
            addFranquicia(new Franquicia(0, "Disney Lorcana", "Ravensburger", "Juego de cartas coleccionables con personajes del universo Disney.", true));
            addFranquicia(new Franquicia(0, "One Piece Card Game", "Bandai", "TCG competitivo basado en el manga y anime de Eiichiro Oda.", true));
            addFranquicia(new Franquicia(0, "Digimon Card Game", "Bandai", "TCG dinámico de monstruos digitales con sistema de medidor de memoria.", true));
            addFranquicia(new Franquicia(0, "Star Wars: Unlimited", "Fantasy Flight Games", "Juego de cartas trepidante y estratégico del universo Star Wars.", true));
        } catch (Exception ex) {
            System.err.println("Aviso al sembrar franquicias: " + ex.getMessage());
        }
    }

    // =========================================================================
    // CRUD: CLIENTE
    // =========================================================================

    public synchronized List<Cliente> getClientes() {
        List<Cliente> list = new ArrayList<>();
        String sql = "SELECT id_cliente, nombre, apellido, correo_electronico, telefono, fecha_registro FROM cliente ORDER BY id_cliente ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Cliente c = new Cliente();
                c.setIdCliente(rs.getInt("id_cliente"));
                c.setNombre(rs.getString("nombre"));
                c.setApellido(rs.getString("apellido"));
                c.setCorreoElectronico(rs.getString("correo_electronico"));
                c.setTelefono(rs.getString("telefono"));
                c.setFechaRegistro(parseDateTime(rs.getString("fecha_registro")));
                list.add(c);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addCliente(Cliente c) {
        if (c == null || !Cliente.esTelefonoValido(c.getTelefono())) {
            throw new IllegalArgumentException("El teléfono debe ser mexicano y tener 10 dígitos; se permite el prefijo +52.");
        }
        String sql = "INSERT INTO cliente (nombre, apellido, correo_electronico, telefono, fecha_registro) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getApellido());
            ps.setString(3, c.getCorreoElectronico());
            ps.setString(4, c.getTelefono());
            LocalDateTime reg = c.getFechaRegistro() != null ? c.getFechaRegistro() : LocalDateTime.now();
            ps.setString(5, reg.format(dtfDateTime));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setIdCliente(keys.getInt(1));
                    c.setFechaRegistro(reg);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar cliente: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateCliente(Cliente c) {
        if (c == null || !Cliente.esTelefonoValido(c.getTelefono())) {
            throw new IllegalArgumentException("El teléfono debe ser mexicano y tener 10 dígitos; se permite el prefijo +52.");
        }
        String sql = "UPDATE cliente SET nombre = ?, apellido = ?, correo_electronico = ?, telefono = ? WHERE id_cliente = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getApellido());
            ps.setString(3, c.getCorreoElectronico());
            ps.setString(4, c.getTelefono());
            ps.setInt(5, c.getIdCliente());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar cliente: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void deleteCliente(int idCliente) {
        String sql = "DELETE FROM cliente WHERE id_cliente = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar cliente: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized Cliente findClienteById(int idCliente) {
        String sql = "SELECT id_cliente, nombre, apellido, correo_electronico, telefono, fecha_registro FROM cliente WHERE id_cliente = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Cliente c = new Cliente();
                    c.setIdCliente(rs.getInt("id_cliente"));
                    c.setNombre(rs.getString("nombre"));
                    c.setApellido(rs.getString("apellido"));
                    c.setCorreoElectronico(rs.getString("correo_electronico"));
                    c.setTelefono(rs.getString("telefono"));
                    c.setFechaRegistro(parseDateTime(rs.getString("fecha_registro")));
                    return c;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // =========================================================================
    // CRUD: USUARIO
    // =========================================================================

    public synchronized List<Usuario> getUsuarios() {
        List<Usuario> list = new ArrayList<>();
        String sql = "SELECT id_usuario, nombre, apellido, rol, horario, activo, password_hash, must_change_password FROM usuario ORDER BY id_usuario ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Usuario u = new Usuario();
                u.setIdUsuario(rs.getInt("id_usuario"));
                u.setNombre(rs.getString("nombre"));
                u.setApellido(rs.getString("apellido"));
                u.setRol(rs.getString("rol"));
                u.setHorario(rs.getString("horario"));
                u.setActivo(rs.getInt("activo") != 0);
                u.setPasswordHash(rs.getString("password_hash"));
                u.setPasswordChangeRequired(rs.getInt("must_change_password") != 0);
                list.add(u);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addUsuario(Usuario u) {
        String sql = "INSERT INTO usuario (nombre, apellido, rol, horario, activo, password_hash, must_change_password) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getRol());
            ps.setString(4, u.getHorario());
            ps.setInt(5, u.isActivo() ? 1 : 0);
            ps.setString(6, u.getPasswordHash() == null || u.getPasswordHash().isBlank() ? hashPassword(Usuario.DEFAULT_PASSWORD) : u.getPasswordHash());
            ps.setInt(7, u.isPasswordChangeRequired() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    u.setIdUsuario(keys.getInt(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar usuario: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateUsuario(Usuario u) {
        String sql = "UPDATE usuario SET nombre = ?, apellido = ?, rol = ?, horario = ?, activo = ?, password_hash = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getRol());
            ps.setString(4, u.getHorario());
            ps.setInt(5, u.isActivo() ? 1 : 0);
            ps.setString(6, u.getPasswordHash() == null || u.getPasswordHash().isBlank() ? hashPassword(Usuario.DEFAULT_PASSWORD) : u.getPasswordHash());
            ps.setInt(7, u.getIdUsuario());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar usuario: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void deleteUsuario(int idUsuario) {
        String sql = "DELETE FROM usuario WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar usuario: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public static final Usuario ASH_BRIDGE_USER = new Usuario(
            -1, "Ash", "Ketchum", "Usuario Puente", "Turno Especial (Configuración Inicial)", true
    );

    static {
        ASH_BRIDGE_USER.setPassword(Usuario.ASH_BRIDGE_PASSWORD);
    }

    public synchronized boolean hasActiveAdministrator() {
        String sql = "SELECT COUNT(*) FROM usuario WHERE rol = 'Administrador' AND activo = 1";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            System.err.println("Error verificando administradores: " + e.getMessage());
        }
        return false;
    }

    public synchronized Usuario findUsuarioById(int idUsuario) {
        String sql = "SELECT id_usuario, nombre, apellido, rol, horario, activo, password_hash, must_change_password FROM usuario WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario();
                    u.setIdUsuario(rs.getInt("id_usuario"));
                    u.setNombre(rs.getString("nombre"));
                    u.setApellido(rs.getString("apellido"));
                    u.setRol(rs.getString("rol"));
                    u.setHorario(rs.getString("horario"));
                    u.setActivo(rs.getInt("activo") != 0);
                    u.setPasswordHash(rs.getString("password_hash"));
                    u.setPasswordChangeRequired(rs.getInt("must_change_password") != 0);
                    return u;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // =========================================================================
    // CRUD: PRODUCTO
    // =========================================================================

    public synchronized List<Producto> getProductos() {
        List<Producto> list = new ArrayList<>();
        String sql = "SELECT id_producto, codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, porcentaje_apartado, stock, id_torneo, tipo_venta, fecha_salida FROM producto ORDER BY id_producto ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Producto p = new Producto();
                p.setIdProducto(rs.getInt("id_producto"));
                p.setCodigoBarras(rs.getString("codigo_barras"));
                p.setNombre(rs.getString("nombre"));
                p.setDescripcion(rs.getString("descripcion"));
                p.setCategoria(rs.getString("categoria"));
                p.setJuegoTcg(rs.getString("juego_tcg"));
                p.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                p.setPorcentajeApartado(rs.getBigDecimal("porcentaje_apartado"));
                p.setStock(rs.getInt("stock"));
                int idTorneo = rs.getInt("id_torneo");
                p.setIdTorneo(rs.wasNull() ? null : idTorneo);
                p.setTipoVenta(rs.getString("tipo_venta"));
                String fechaSalida = rs.getString("fecha_salida");
                p.setFechaSalida(fechaSalida == null || fechaSalida.trim().isEmpty() ? null : parseDate(fechaSalida));
                list.add(p);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized List<Producto> getProductosInventario() {
        Set<Integer> torneosNoProgramados = new HashSet<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id_torneo FROM torneo WHERE estado <> 'Programado'")) {
            while (rs.next()) torneosNoProgramados.add(rs.getInt("id_torneo"));
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar torneos no disponibles para el inventario: " + ex.getMessage(), ex);
        }

        List<Producto> productos = getProductos();
        productos.removeIf(producto -> producto.isTicketTorneo() &&
                torneosNoProgramados.contains(producto.getIdTorneo()));
        return productos;
    }

    public synchronized List<Categoria> getCategorias() {
        List<Categoria> list = new ArrayList<>();
        String sql = "SELECT id_categoria, nombre, descripcion, activo, fecha_registro FROM categoria_producto ORDER BY nombre ASC";
        try (Statement st = getConnection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Categoria categoria = new Categoria();
                categoria.setIdCategoria(rs.getInt("id_categoria"));
                categoria.setNombre(rs.getString("nombre"));
                categoria.setDescripcion(rs.getString("descripcion"));
                categoria.setActivo(rs.getInt("activo") != 0);
                categoria.setFechaRegistro(parseDateTime(rs.getString("fecha_registro")));
                list.add(categoria);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized List<String> getNombresCategoriasActivas() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT nombre FROM categoria_producto WHERE activo = 1 ORDER BY nombre ASC";
        try (Statement st = getConnection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(rs.getString("nombre"));
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addCategoria(Categoria categoria) throws Exception {
        String nombre = categoria.getNombre() != null ? categoria.getNombre().trim() : "";
        if (nombre.isEmpty()) throw new Exception("El nombre de la categoría es obligatorio.");
        String sql = "INSERT INTO categoria_producto (nombre, descripcion, activo, fecha_registro) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, categoria.getDescripcion() != null ? categoria.getDescripcion().trim() : "");
            ps.setInt(3, categoria.isActivo() ? 1 : 0);
            LocalDateTime fecha = categoria.getFechaRegistro() != null ? categoria.getFechaRegistro() : LocalDateTime.now();
            ps.setString(4, fecha.format(dtfDateTime));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) categoria.setIdCategoria(keys.getInt(1));
            }
            categoria.setNombre(nombre);
            categoria.setFechaRegistro(fecha);
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")) {
                throw new Exception("Ya existe una categoría con el nombre '" + nombre + "'.");
            }
            throw new Exception("Error al registrar categoría: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateCategoria(Categoria categoria, String oldName) throws Exception {
        String nombre = categoria.getNombre() != null ? categoria.getNombre().trim() : "";
        if (nombre.isEmpty()) throw new Exception("El nombre de la categoría es obligatorio.");
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE categoria_producto SET nombre = ?, descripcion = ?, activo = ? WHERE id_categoria = ?")) {
                ps.setString(1, nombre);
                ps.setString(2, categoria.getDescripcion() != null ? categoria.getDescripcion().trim() : "");
                ps.setInt(3, categoria.isActivo() ? 1 : 0);
                ps.setInt(4, categoria.getIdCategoria());
                ps.executeUpdate();
            }
            if (oldName != null && !oldName.equals(nombre)) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE producto SET categoria = ? WHERE categoria = ?")) {
                    ps.setString(1, nombre);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            categoria.setNombre(nombre);
        } catch (SQLException ex) {
            conn.rollback();
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")) {
                throw new Exception("Ya existe otra categoría con el nombre '" + nombre + "'.");
            }
            throw new Exception("Error al actualizar categoría: " + ex.getMessage(), ex);
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        syncSqliteBackup();
    }

    public synchronized int countProductosByCategoria(String nombreCategoria) {
        String sql = "SELECT COUNT(*) FROM producto WHERE categoria = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, nombreCategoria);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    public synchronized void deleteCategoria(int idCategoria, String nombreCategoria) throws Exception {
        int productos = countProductosByCategoria(nombreCategoria);
        if (productos > 0) {
            throw new Exception("No se puede eliminar la categoría porque está asociada a " + productos + " producto(s). Desactívala para ocultarla en nuevos registros.");
        }
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM categoria_producto WHERE id_categoria = ?")) {
            ps.setInt(1, idCategoria);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new Exception("Error al eliminar categoría: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void addProducto(Producto p) {
        String sql = "INSERT INTO producto (codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, porcentaje_apartado, stock, id_torneo, tipo_venta, fecha_salida) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            validarCodigoBarrasDisponible(p.getCodigoBarras(), 0);
            try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setCodigoBarras(ps, 1, p.getCodigoBarras());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getDescripcion());
            ps.setString(4, p.getCategoria());
            ps.setString(5, p.getJuegoTcg() != null ? p.getJuegoTcg() : "Pokémon");
            ps.setBigDecimal(6, p.getPrecioUnitario());
            ps.setBigDecimal(7, p.getPorcentajeApartado());
            ps.setInt(8, p.getStock());
            if (p.getIdTorneo() != null) ps.setInt(9, p.getIdTorneo());
            else ps.setNull(9, Types.INTEGER);
            ps.setString(10, p.getTipoVenta());
            if (p.getFechaSalida() != null) ps.setString(11, p.getFechaSalida().toString());
            else ps.setNull(11, Types.DATE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setIdProducto(keys.getInt(1));
                }
            }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar producto: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateProducto(Producto p) {
        String sql = "UPDATE producto SET codigo_barras = ?, nombre = ?, descripcion = ?, categoria = ?, juego_tcg = ?, precio_unitario = ?, porcentaje_apartado = ?, stock = ?, id_torneo = ?, tipo_venta = ?, fecha_salida = ? WHERE id_producto = ?";
        try {
            validarCodigoBarrasDisponible(p.getCodigoBarras(), p.getIdProducto());
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            setCodigoBarras(ps, 1, p.getCodigoBarras());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getDescripcion());
            ps.setString(4, p.getCategoria());
            ps.setString(5, p.getJuegoTcg());
            ps.setBigDecimal(6, p.getPrecioUnitario());
            ps.setBigDecimal(7, p.getPorcentajeApartado());
            ps.setInt(8, p.getStock());
            if (p.getIdTorneo() != null) ps.setInt(9, p.getIdTorneo());
            else ps.setNull(9, Types.INTEGER);
            ps.setString(10, p.getTipoVenta());
            if (p.getFechaSalida() != null) ps.setString(11, p.getFechaSalida().toString());
            else ps.setNull(11, Types.DATE);
            ps.setInt(12, p.getIdProducto());
            ps.executeUpdate();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar producto: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    private void validarCodigoBarrasDisponible(String codigoBarras, int idProductoExcluir) throws SQLException {
        if (codigoBarras == null || codigoBarras.trim().isEmpty()) return;
        String sql = "SELECT nombre FROM producto WHERE codigo_barras = ? AND id_producto <> ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, codigoBarras.trim());
            ps.setInt(2, idProductoExcluir);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("El código de barras ya está registrado para el producto '" + rs.getString("nombre") + "'.");
                }
            }
        }
    }

    private void setCodigoBarras(PreparedStatement ps, int index, String codigoBarras) throws SQLException {
        String codigoNormalizado = codigoBarras != null ? codigoBarras.trim() : "";
        if (codigoNormalizado.isEmpty()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, codigoNormalizado);
    }

    public synchronized void deleteProducto(int idProducto) {
        String sql = "DELETE FROM producto WHERE id_producto = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar producto: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized Producto findProductoById(int idProducto) {
        String sql = "SELECT id_producto, codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, porcentaje_apartado, stock, id_torneo, tipo_venta, fecha_salida FROM producto WHERE id_producto = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Producto p = new Producto();
                    p.setIdProducto(rs.getInt("id_producto"));
                    p.setCodigoBarras(rs.getString("codigo_barras"));
                    p.setNombre(rs.getString("nombre"));
                    p.setDescripcion(rs.getString("descripcion"));
                    p.setCategoria(rs.getString("categoria"));
                    p.setJuegoTcg(rs.getString("juego_tcg"));
                    p.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                    p.setPorcentajeApartado(rs.getBigDecimal("porcentaje_apartado"));
                    p.setStock(rs.getInt("stock"));
                    int idTorneo = rs.getInt("id_torneo");
                    p.setIdTorneo(rs.wasNull() ? null : idTorneo);
                    p.setTipoVenta(rs.getString("tipo_venta"));
                    String fechaSalida = rs.getString("fecha_salida");
                    p.setFechaSalida(fechaSalida == null || fechaSalida.trim().isEmpty() ? null : parseDate(fechaSalida));
                    return p;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // =========================================================================
    // CRUD: FRANQUICIA (Juegos TCG Jugables)
    // =========================================================================

    public synchronized List<Franquicia> getFranquicias() {
        List<Franquicia> list = new ArrayList<>();
        String sql = "SELECT id_franquicia, nombre, fabricante, descripcion, activo, fecha_registro FROM franquicia ORDER BY nombre ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Franquicia f = new Franquicia();
                f.setIdFranquicia(rs.getInt("id_franquicia"));
                f.setNombre(rs.getString("nombre"));
                f.setFabricante(rs.getString("fabricante"));
                f.setDescripcion(rs.getString("descripcion"));
                f.setActivo(rs.getInt("activo") != 0);
                f.setFechaRegistro(parseDateTime(rs.getString("fecha_registro")));
                list.add(f);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized List<String> getNombresFranquiciasActivas() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT nombre FROM franquicia WHERE activo = 1 ORDER BY nombre ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("nombre"));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        if (list.isEmpty()) {
            list.addAll(Arrays.asList("Pokémon", "Magic: The Gathering", "Yu-Gi-Oh!", "Disney Lorcana", "One Piece Card Game"));
        }
        return list;
    }

    public synchronized void addFranquicia(Franquicia f) throws Exception {
        if (f.getNombre() == null || f.getNombre().trim().isEmpty()) {
            throw new Exception("El nombre de la franquicia es obligatorio.");
        }
        String sql = "INSERT INTO franquicia (nombre, fabricante, descripcion, activo, fecha_registro) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.getNombre().trim());
            ps.setString(2, f.getFabricante() != null ? f.getFabricante().trim() : "");
            ps.setString(3, f.getDescripcion() != null ? f.getDescripcion().trim() : "");
            ps.setInt(4, f.isActivo() ? 1 : 0);
            LocalDateTime reg = f.getFechaRegistro() != null ? f.getFechaRegistro() : LocalDateTime.now();
            ps.setString(5, reg.format(dtfDateTime));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    f.setIdFranquicia(keys.getInt(1));
                    f.setFechaRegistro(reg);
                }
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")) {
                throw new Exception("Ya existe una franquicia registrada con el nombre '" + f.getNombre() + "'.");
            }
            throw new Exception("Error al registrar franquicia: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateFranquicia(Franquicia f, String oldName) throws Exception {
        if (f.getNombre() == null || f.getNombre().trim().isEmpty()) {
            throw new Exception("El nombre de la franquicia es obligatorio.");
        }
        String sql = "UPDATE franquicia SET nombre = ?, fabricante = ?, descripcion = ?, activo = ? WHERE id_franquicia = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, f.getNombre().trim());
            ps.setString(2, f.getFabricante() != null ? f.getFabricante().trim() : "");
            ps.setString(3, f.getDescripcion() != null ? f.getDescripcion().trim() : "");
            ps.setInt(4, f.isActivo() ? 1 : 0);
            ps.setInt(5, f.getIdFranquicia());
            ps.executeUpdate();

            // Si el nombre cambió, actualizar en cascada los productos y torneos asociados
            if (oldName != null && !oldName.trim().equalsIgnoreCase(f.getNombre().trim())) {
                try (PreparedStatement psProd = getConnection().prepareStatement("UPDATE producto SET juego_tcg = ? WHERE juego_tcg = ?")) {
                    psProd.setString(1, f.getNombre().trim());
                    psProd.setString(2, oldName.trim());
                    psProd.executeUpdate();
                }
                try (PreparedStatement psTorneo = getConnection().prepareStatement("UPDATE torneo SET juego_tcg = ? WHERE juego_tcg = ?")) {
                    psTorneo.setString(1, f.getNombre().trim());
                    psTorneo.setString(2, oldName.trim());
                    psTorneo.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique")) {
                throw new Exception("Ya existe otra franquicia registrada con el nombre '" + f.getNombre() + "'.");
            }
            throw new Exception("Error al actualizar franquicia: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized int countProductosByFranquicia(String nombreFranquicia) {
        if (nombreFranquicia == null || nombreFranquicia.trim().isEmpty()) return 0;
        String sql = "SELECT COUNT(*) FROM producto WHERE LOWER(juego_tcg) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, nombreFranquicia.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    public synchronized int countTorneosByFranquicia(String nombreFranquicia) {
        if (nombreFranquicia == null || nombreFranquicia.trim().isEmpty()) return 0;
        String sql = "SELECT COUNT(*) FROM torneo WHERE LOWER(juego_tcg) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, nombreFranquicia.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    public synchronized void deleteFranquicia(int idFranquicia, String nombreFranquicia) throws Exception {
        int prods = countProductosByFranquicia(nombreFranquicia);
        int torneos = countTorneosByFranquicia(nombreFranquicia);

        if (prods > 0 || torneos > 0) {
            throw new Exception("No es posible eliminar la franquicia '" + nombreFranquicia + "' porque tiene " +
                    prods + " producto(s) en inventario y " + torneos + " torneo(s) vinculados.\n\n" +
                    "Sugerencia: Puedes 'Desactivar' la franquicia para que no aparezca en nuevas selecciones, o reasignar sus productos.");
        }

        String sql = "DELETE FROM franquicia WHERE id_franquicia = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idFranquicia);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new Exception("Error al eliminar franquicia: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized Franquicia findFranquiciaById(int idFranquicia) {
        String sql = "SELECT id_franquicia, nombre, fabricante, descripcion, activo, fecha_registro FROM franquicia WHERE id_franquicia = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idFranquicia);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Franquicia f = new Franquicia();
                    f.setIdFranquicia(rs.getInt("id_franquicia"));
                    f.setNombre(rs.getString("nombre"));
                    f.setFabricante(rs.getString("fabricante"));
                    f.setDescripcion(rs.getString("descripcion"));
                    f.setActivo(rs.getInt("activo") != 0);
                    f.setFechaRegistro(parseDateTime(rs.getString("fecha_registro")));
                    return f;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // =========================================================================
    // TRANSACCIÓN DE VENTA & DETALLES
    // =========================================================================

    public synchronized List<Venta> getVentas() {
        List<Venta> list = new ArrayList<>();
        String sql = "SELECT v.id_venta, v.id_cliente, v.id_usuario, v.fecha_venta, v.monto_total, v.metodo_pago, v.monto_saldo_favor, " +
                "c.nombre AS cli_nombre, c.apellido AS cli_apellido, c.correo_electronico AS cli_correo, c.telefono AS cli_tel, c.fecha_registro AS cli_freg, " +
                "u.nombre AS usr_nombre, u.apellido AS usr_apellido, u.rol AS usr_rol, u.horario AS usr_horario, u.activo AS usr_activo " +
                "FROM venta v " +
                "LEFT JOIN cliente c ON v.id_cliente = c.id_cliente " +
                "JOIN usuario u ON v.id_usuario = u.id_usuario " +
                "ORDER BY v.id_venta DESC";

        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Venta v = new Venta();
                v.setIdVenta(rs.getInt("id_venta"));

                int idCli = rs.getInt("id_cliente");
                if (!rs.wasNull()) {
                    v.setIdCliente(idCli);
                    Cliente c = new Cliente();
                    c.setIdCliente(idCli);
                    c.setNombre(rs.getString("cli_nombre"));
                    c.setApellido(rs.getString("cli_apellido"));
                    c.setCorreoElectronico(rs.getString("cli_correo"));
                    c.setTelefono(rs.getString("cli_tel"));
                    c.setFechaRegistro(parseDateTime(rs.getString("cli_freg")));
                    v.setCliente(c);
                } else {
                    v.setIdCliente(null);
                    v.setCliente(null);
                }

                int idUsr = rs.getInt("id_usuario");
                v.setIdUsuario(idUsr);
                Usuario u = new Usuario();
                u.setIdUsuario(idUsr);
                u.setNombre(rs.getString("usr_nombre"));
                u.setApellido(rs.getString("usr_apellido"));
                u.setRol(rs.getString("usr_rol"));
                u.setHorario(rs.getString("usr_horario"));
                u.setActivo(rs.getInt("usr_activo") != 0);
                v.setUsuario(u);

                v.setFechaVenta(parseDateTime(rs.getString("fecha_venta")));
                v.setMontoTotal(rs.getBigDecimal("monto_total"));
                v.setMontoSaldoFavor(rs.getBigDecimal("monto_saldo_favor"));
                String metodoPago = rs.getString("metodo_pago");
                if (v.getMontoSaldoFavor().compareTo(BigDecimal.ZERO) > 0) {
                    metodoPago = v.getMontoSaldoFavor().compareTo(v.getMontoTotal()) >= 0
                            ? "Saldo a favor" : "Pago mixto";
                }
                v.setMetodoPago(metodoPago);

                list.add(v);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized List<MovimientoCorteCaja> getMovimientosCorteCaja(LocalDate fecha) {
        List<MovimientoCorteCaja> movimientos = new ArrayList<>();
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.plusDays(1).atStartOfDay();
        String sql = "SELECT v.id_venta AS referencia, v.fecha_venta AS fecha, " +
                "CASE WHEN v.id_preventa IS NULL THEN 'Venta' ELSE 'Liquidación de preventa' END AS tipo, " +
                "c.nombre AS cli_nombre, c.apellido AS cli_apellido, " +
                "CASE WHEN v.id_preventa IS NULL THEN " +
                "(SELECT GROUP_CONCAT(pv.nombre) FROM detalle_venta dv " +
                "JOIN producto pv ON dv.id_producto = pv.id_producto " +
                "WHERE dv.id_venta = v.id_venta AND pv.id_torneo IS NULL) " +
                "ELSE p.nombre END AS producto, " +
                "CASE WHEN v.id_preventa IS NULL THEN " +
                "(v.monto_total - COALESCE((SELECT SUM(dv.subtotal) " +
                "FROM detalle_venta dv JOIN producto pt ON dv.id_producto = pt.id_producto " +
                "WHERE dv.id_venta = v.id_venta AND pt.id_torneo IS NOT NULL), 0)) - " +
                "CASE WHEN v.monto_total > 0 THEN v.monto_saldo_favor * " +
                "((v.monto_total - COALESCE((SELECT SUM(dv.subtotal) FROM detalle_venta dv " +
                "JOIN producto pt ON dv.id_producto = pt.id_producto " +
                "WHERE dv.id_venta = v.id_venta AND pt.id_torneo IS NOT NULL), 0)) / v.monto_total) ELSE 0 END " +
                "ELSE v.monto_total - COALESCE(pr.monto_anticipo, 0) - v.monto_saldo_favor END AS monto, " +
                "CASE WHEN v.monto_saldo_favor >= v.monto_total AND v.monto_saldo_favor > 0 THEN 'Saldo a favor' " +
                "WHEN v.monto_saldo_favor > 0 THEN 'Pago mixto' ELSE v.metodo_pago END AS metodo " +
                "FROM venta v " +
                "LEFT JOIN preventa pr ON v.id_preventa = pr.id_preventa " +
                "LEFT JOIN cliente c ON v.id_cliente = c.id_cliente " +
                "LEFT JOIN producto p ON pr.id_producto = p.id_producto " +
                "WHERE v.fecha_venta >= ? AND v.fecha_venta < ? " +
                "AND (v.id_preventa IS NOT NULL OR NOT EXISTS (SELECT 1 FROM detalle_venta dt " +
                "JOIN producto pt ON dt.id_producto = pt.id_producto " +
                "WHERE dt.id_venta = v.id_venta AND pt.id_torneo IS NOT NULL) " +
                "OR v.monto_total > COALESCE((SELECT SUM(dt.subtotal) FROM detalle_venta dt " +
                "JOIN producto pt ON dt.id_producto = pt.id_producto " +
                "WHERE dt.id_venta = v.id_venta AND pt.id_torneo IS NOT NULL), 0)) " +
                "UNION ALL " +
                "SELECT pr.id_preventa AS referencia, pr.fecha_solicitud AS fecha, " +
                "'Apartado de preventa' AS tipo, c.nombre AS cli_nombre, c.apellido AS cli_apellido, " +
                "p.nombre AS producto, pr.monto_anticipo AS monto, 'Apartado' AS metodo " +
                "FROM preventa pr " +
                "JOIN cliente c ON pr.id_cliente = c.id_cliente " +
                "JOIN producto p ON pr.id_producto = p.id_producto " +
                "WHERE pr.fecha_solicitud >= ? AND pr.fecha_solicitud < ? " +
                "UNION ALL " +
                "SELECT d.id_detalle AS referencia, v.fecha_venta AS fecha, " +
                "'Inscripción a torneo' AS tipo, c.nombre AS cli_nombre, c.apellido AS cli_apellido, " +
                "t.nombre_torneo AS producto, " +
                "d.subtotal - CASE WHEN v.monto_total > 0 THEN v.monto_saldo_favor * d.subtotal / v.monto_total ELSE 0 END AS monto, " +
                "CASE WHEN v.monto_saldo_favor >= v.monto_total AND v.monto_saldo_favor > 0 THEN 'Saldo a favor' " +
                "WHEN v.monto_saldo_favor > 0 THEN 'Pago mixto' ELSE v.metodo_pago END AS metodo " +
                "FROM detalle_venta d " +
                "JOIN venta v ON d.id_venta = v.id_venta " +
                "JOIN producto p ON d.id_producto = p.id_producto " +
                "JOIN torneo t ON p.id_torneo = t.id_torneo " +
                "LEFT JOIN cliente c ON v.id_cliente = c.id_cliente " +
                "WHERE v.fecha_venta >= ? AND v.fecha_venta < ? " +
                "ORDER BY fecha ASC, tipo ASC";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, inicio.format(dtfDateTime));
            ps.setString(2, fin.format(dtfDateTime));
            ps.setString(3, inicio.format(dtfDateTime));
            ps.setString(4, fin.format(dtfDateTime));
            ps.setString(5, inicio.format(dtfDateTime));
            ps.setString(6, fin.format(dtfDateTime));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nombre = rs.getString("cli_nombre");
                    String apellido = rs.getString("cli_apellido");
                    String cliente = nombre == null ? "Público General" : nombre + " " + (apellido == null ? "" : apellido);
                    movimientos.add(new MovimientoCorteCaja(
                            rs.getInt("referencia"),
                            parseDateTime(rs.getString("fecha")),
                            rs.getString("tipo"),
                            cliente.trim(),
                            rs.getString("producto"),
                            rs.getBigDecimal("monto"),
                            rs.getString("metodo")
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el corte de caja: " + ex.getMessage(), ex);
        }
        return movimientos;
    }

    public synchronized List<DetalleVenta> getDetallesByVenta(int idVenta) {
        List<DetalleVenta> list = new ArrayList<>();
        String sql = "SELECT d.id_detalle, d.id_venta, d.id_producto, d.cantidad, d.precio_historico, d.subtotal, " +
                "p.codigo_barras, p.nombre, p.descripcion, p.categoria, p.juego_tcg, p.precio_unitario, p.stock " +
                "FROM detalle_venta d " +
                "JOIN producto p ON d.id_producto = p.id_producto " +
                "WHERE d.id_venta = ? ORDER BY d.id_detalle ASC";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Producto p = new Producto();
                    p.setIdProducto(rs.getInt("id_producto"));
                    p.setCodigoBarras(rs.getString("codigo_barras"));
                    p.setNombre(rs.getString("nombre"));
                    p.setDescripcion(rs.getString("descripcion"));
                    p.setCategoria(rs.getString("categoria"));
                    p.setJuegoTcg(rs.getString("juego_tcg"));
                    p.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                    p.setStock(rs.getInt("stock"));

                    DetalleVenta d = new DetalleVenta();
                    d.setIdDetalle(rs.getInt("id_detalle"));
                    d.setIdVenta(rs.getInt("id_venta"));
                    d.setIdProducto(p.getIdProducto());
                    d.setProducto(p);
                    d.setCantidad(rs.getInt("cantidad"));
                    d.setPrecioHistorico(rs.getBigDecimal("precio_historico"));
                    d.setSubtotal(rs.getBigDecimal("subtotal"));
                    list.add(d);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    /**
     * Registra una venta completa en una sola transacción atómica en SQLite:
     * 1. Verifica stock disponible de cada producto.
     * 2. Inserta encabezado en 'venta'.
     * 3. Inserta cada línea en 'detalle_venta'.
     * 4. Descuenta el stock en 'producto'.
     */
    public synchronized void registrarVenta(Venta v) throws Exception {
        if (v.getDetalles().isEmpty()) {
            throw new Exception("La venta no contiene ningún producto.");
        }

        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. Validar stock en base de datos
            String sqlCheckStock = "SELECT stock, nombre, tipo_venta, id_torneo FROM producto WHERE id_producto = ?";
            for (DetalleVenta d : v.getDetalles()) {
                try (PreparedStatement psStock = conn.prepareStatement(sqlCheckStock)) {
                    psStock.setInt(1, d.getIdProducto());
                    try (ResultSet rs = psStock.executeQuery()) {
                        if (!rs.next()) {
                            throw new Exception("Producto con ID " + d.getIdProducto() + " no encontrado.");
                        }
                        int stockActual = rs.getInt("stock");
                        String nom = rs.getString("nombre");
                        int idTorneo = rs.getInt("id_torneo");
                        boolean esTicketTorneo = !rs.wasNull();
                        if (esTicketTorneo) {
                            if (d.getProducto() == null) d.setProducto(new Producto());
                            d.getProducto().setIdProducto(d.getIdProducto());
                            d.getProducto().setIdTorneo(idTorneo);
                            if (d.getCantidad() != 1) {
                                throw new Exception("Cada ticket de torneo debe cobrarse en una venta individual.");
                            }
                            if (v.getIdCliente() == null || v.getIdCliente() <= 0) {
                                throw new Exception("Selecciona al participante como cliente para vender un ticket de torneo.");
                            }
                            validarTicketTorneo(conn, idTorneo, v.getIdCliente(), stockActual);
                            continue;
                        }
                        if (Producto.TIPO_PREVENTA.equalsIgnoreCase(rs.getString("tipo_venta"))) {
                            throw new Exception("El producto '" + nom + "' está marcado como preventa y no puede registrarse en el punto de venta.");
                        }
                        if (stockActual < d.getCantidad()) {
                            throw new Exception("Stock insuficiente para '" + nom + "' (Disponible: " + stockActual + ", Solicitado: " + d.getCantidad() + ")");
                        }
                    }
                }
            }

            // Recalcular total de venta
            v.recalculateTotal();
            BigDecimal montoSaldoFavor = v.getMontoSaldoFavor();
            if (montoSaldoFavor.compareTo(BigDecimal.ZERO) < 0 ||
                    montoSaldoFavor.compareTo(v.getMontoTotal()) > 0) {
                throw new Exception("El saldo a favor aplicado debe estar entre cero y el total de la venta.");
            }
            if (montoSaldoFavor.compareTo(BigDecimal.ZERO) > 0) {
                if (v.getIdCliente() == null || v.getIdCliente() <= 0) {
                    throw new Exception("Selecciona un cliente para utilizar su saldo a favor.");
                }
                consumirSaldoFavor(conn, v.getIdCliente(), montoSaldoFavor);
            }
            LocalDateTime fecha = v.getFechaVenta() != null ? v.getFechaVenta() : LocalDateTime.now();

            // 2. Insertar en tabla venta
            String sqlVenta = "INSERT INTO venta (id_cliente, id_usuario, fecha_venta, monto_total, metodo_pago, monto_saldo_favor) VALUES (?, ?, ?, ?, ?, ?)";
            int idVentaGenerado;
            try (PreparedStatement psVenta = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                if (v.getIdCliente() != null && v.getIdCliente() > 0) {
                    psVenta.setInt(1, v.getIdCliente());
                } else {
                    psVenta.setNull(1, Types.INTEGER);
                }
                psVenta.setInt(2, v.getIdUsuario());
                psVenta.setString(3, fecha.format(dtfDateTime));
                psVenta.setBigDecimal(4, v.getMontoTotal());
                psVenta.setString(5, v.getMetodoPago());
                psVenta.setBigDecimal(6, montoSaldoFavor);
                psVenta.executeUpdate();

                try (ResultSet keys = psVenta.getGeneratedKeys()) {
                    if (keys.next()) {
                        idVentaGenerado = keys.getInt(1);
                        v.setIdVenta(idVentaGenerado);
                        v.setFechaVenta(fecha);
                    } else {
                        throw new SQLException("No se obtuvo el ID generado para la venta.");
                    }
                }
            }

            // 3. Insertar detalle_venta y 4. Descontar stock
            String sqlDetalle = "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_historico) VALUES (?, ?, ?, ?)";
            String sqlUpdateStock = "UPDATE producto SET stock = stock - ? WHERE id_producto = ?";

            try (PreparedStatement psDetalle = conn.prepareStatement(sqlDetalle, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psUpdateStock = conn.prepareStatement(sqlUpdateStock)) {

                for (DetalleVenta d : v.getDetalles()) {
                    d.setIdVenta(idVentaGenerado);

                    psDetalle.setInt(1, idVentaGenerado);
                    psDetalle.setInt(2, d.getIdProducto());
                    psDetalle.setInt(3, d.getCantidad());
                    psDetalle.setBigDecimal(4, d.getPrecioHistorico());
                    psDetalle.executeUpdate();

                    try (ResultSet dKeys = psDetalle.getGeneratedKeys()) {
                        if (dKeys.next()) {
                            d.setIdDetalle(dKeys.getInt(1));
                        }
                    }

                    if (d.getProducto().isTicketTorneo()) {
                        boolean reservado = existeInscripcionPendiente(conn,
                                d.getProducto().getIdTorneo(), v.getIdCliente());
                        if (!reservado) {
                            psUpdateStock.setInt(1, d.getCantidad());
                            psUpdateStock.setInt(2, d.getIdProducto());
                            psUpdateStock.executeUpdate();
                        }
                        confirmarTicketTorneo(conn, d.getProducto().getIdTorneo(), v.getIdCliente());
                    } else {
                        // Descontar inventario
                        psUpdateStock.setInt(1, d.getCantidad());
                        psUpdateStock.setInt(2, d.getIdProducto());
                        psUpdateStock.executeUpdate();
                    }
                }
            }

            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        syncSqliteBackup();
    }

    private void consumirSaldoFavor(Connection conn, int idCliente, BigDecimal monto) throws Exception {
        BigDecimal restante = monto;
        String fechaActual = LocalDate.now().format(dtfDate);
        Map<Integer, BigDecimal> saldos = new LinkedHashMap<>();
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT id_saldo, saldo_disponible FROM saldo_favor " +
                        "WHERE id_cliente = ? AND saldo_disponible > 0 " +
                        "AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= ?) " +
                        "ORDER BY fecha_emision ASC, id_saldo ASC")) {
            query.setInt(1, idCliente);
            query.setString(2, fechaActual);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) saldos.put(rs.getInt("id_saldo"), rs.getBigDecimal("saldo_disponible"));
            }
        }
        for (Map.Entry<Integer, BigDecimal> saldo : saldos.entrySet()) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal aplicado = saldo.getValue().min(restante);
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE saldo_favor SET saldo_disponible = saldo_disponible - ? " +
                            "WHERE id_saldo = ? AND saldo_disponible >= ?")) {
                update.setBigDecimal(1, aplicado);
                update.setInt(2, saldo.getKey());
                update.setBigDecimal(3, aplicado);
                if (update.executeUpdate() != 1) {
                    throw new SQLException("El saldo a favor cambió mientras se procesaba la venta.");
                }
            }
            restante = restante.subtract(aplicado);
        }
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            throw new Exception("El cliente no cuenta con saldo a favor suficiente o disponible.");
        }
    }

    private void validarTicketTorneo(Connection conn, int idTorneo, int idCliente, int stockDisponible) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT estado FROM torneo WHERE id_torneo = ?")) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Exception("El torneo del ticket no existe.");
                String estado = rs.getString("estado");
                if (!"Programado".equalsIgnoreCase(estado)) {
                    throw new Exception("Las inscripciones solo están disponibles antes de que inicie el torneo.");
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pago_confirmado FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ?")) {
            ps.setInt(1, idTorneo);
            ps.setInt(2, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt("pago_confirmado") != 0) {
                        throw new Exception("El cliente ya cuenta con un ticket pagado para este torneo.");
                    }
                    return;
                }
            }
        }
        if (stockDisponible <= 0) {
            throw new Exception("El torneo ya agotó sus tickets de participación.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT cupo_maximo FROM torneo WHERE id_torneo = ?")) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Exception("El torneo del ticket no existe.");
                if (contarParticipantes(conn, idTorneo) >= rs.getInt("cupo_maximo")) {
                    throw new Exception("El torneo ya alcanzó su cupo máximo.");
                }
            }
        }
    }

    private int contarParticipantes(Connection conn, int idTorneo) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM participante_torneo WHERE id_torneo = ?")) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private boolean existeInscripcionPendiente(Connection conn, int idTorneo, int idCliente) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ? AND pago_confirmado = 0")) {
            ps.setInt(1, idTorneo);
            ps.setInt(2, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String confirmarTicketTorneo(Connection conn, int idTorneo, int idCliente) throws Exception {
        int idInscripcion = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id_inscripcion FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ? AND pago_confirmado = 0")) {
            ps.setInt(1, idTorneo);
            ps.setInt(2, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) idInscripcion = rs.getInt("id_inscripcion");
            }
        }
        if (idInscripcion > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE participante_torneo SET pago_confirmado = 1 WHERE id_inscripcion = ? AND pago_confirmado = 0")) {
                ps.setInt(1, idInscripcion);
                if (ps.executeUpdate() != 1) throw new SQLException("No se pudo confirmar la inscripción del torneo.");
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO participante_torneo (id_torneo, id_cliente, fecha_inscripcion, pago_confirmado) VALUES (?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idTorneo);
                ps.setInt(2, idCliente);
                ps.setString(3, LocalDateTime.now().format(dtfDateTime));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para la inscripción.");
                    idInscripcion = keys.getInt(1);
                }
            }
        }
        return otorgarPremioInscripcion(conn, idTorneo, idCliente, idInscripcion);
    }

    // =========================================================================
    // CRUD: PREVENTA
    // =========================================================================

    public synchronized List<Preventa> getPreventas() {
        List<Preventa> list = new ArrayList<>();
        String sql = "SELECT pr.id_preventa, pr.id_cliente, pr.id_usuario, pr.id_producto, pr.monto_anticipo, pr.monto_total, " +
                "pr.fecha_solicitud, pr.fecha_estimada_entrega, pr.fecha_vencimiento_apartado, pr.estado, " +
                "c.nombre AS cli_nom, c.apellido AS cli_ape, c.correo_electronico AS cli_cor, c.telefono AS cli_tel, " +
                "u.nombre AS usr_nom, u.apellido AS usr_ape, u.rol AS usr_rol, " +
                "p.nombre AS prod_nom, p.categoria AS prod_cat, p.juego_tcg AS prod_juego, p.precio_unitario AS prod_precio, p.stock AS prod_stock " +
                "FROM preventa pr " +
                "JOIN cliente c ON pr.id_cliente = c.id_cliente " +
                "JOIN usuario u ON pr.id_usuario = u.id_usuario " +
                "JOIN producto p ON pr.id_producto = p.id_producto " +
                "ORDER BY pr.id_preventa DESC";

        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Cliente c = new Cliente();
                c.setIdCliente(rs.getInt("id_cliente"));
                c.setNombre(rs.getString("cli_nom"));
                c.setApellido(rs.getString("cli_ape"));
                c.setCorreoElectronico(rs.getString("cli_cor"));
                c.setTelefono(rs.getString("cli_tel"));

                Usuario u = new Usuario();
                u.setIdUsuario(rs.getInt("id_usuario"));
                u.setNombre(rs.getString("usr_nom"));
                u.setApellido(rs.getString("usr_ape"));
                u.setRol(rs.getString("usr_rol"));

                Producto p = new Producto();
                p.setIdProducto(rs.getInt("id_producto"));
                p.setNombre(rs.getString("prod_nom"));
                p.setCategoria(rs.getString("prod_cat"));
                p.setJuegoTcg(rs.getString("prod_juego"));
                p.setPrecioUnitario(rs.getBigDecimal("prod_precio"));
                p.setStock(rs.getInt("prod_stock"));

                Preventa pr = new Preventa();
                pr.setIdPreventa(rs.getInt("id_preventa"));
                pr.setCliente(c);
                pr.setUsuario(u);
                pr.setProducto(p);
                pr.setMontoAnticipo(rs.getBigDecimal("monto_anticipo"));
                pr.setMontoTotal(rs.getBigDecimal("monto_total"));
                pr.setFechaSolicitud(parseDateTime(rs.getString("fecha_solicitud")));
                pr.setFechaEstimadaEntrega(parseDate(rs.getString("fecha_estimada_entrega")));
                pr.setFechaVencimientoApartado(parseDate(rs.getString("fecha_vencimiento_apartado")));
                pr.setEstado(rs.getString("estado"));

                list.add(pr);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addPreventa(Preventa p) {
        String sqlTipoProducto = "SELECT tipo_venta FROM producto WHERE id_producto = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sqlTipoProducto)) {
            ps.setInt(1, p.getIdProducto());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !Producto.TIPO_PREVENTA.equalsIgnoreCase(rs.getString("tipo_venta"))) {
                    throw new IllegalArgumentException("El producto debe estar marcado como preventa para registrar una reserva.");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al validar producto de preventa: " + ex.getMessage(), ex);
        }

        String sql = "INSERT INTO preventa (id_cliente, id_usuario, id_producto, monto_anticipo, monto_total, fecha_solicitud, fecha_estimada_entrega, fecha_vencimiento_apartado, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getIdCliente());
            ps.setInt(2, p.getIdUsuario());
            ps.setInt(3, p.getIdProducto());
            ps.setBigDecimal(4, p.getMontoAnticipo());
            ps.setBigDecimal(5, p.getMontoTotal());
            LocalDateTime fSol = p.getFechaSolicitud() != null ? p.getFechaSolicitud() : LocalDateTime.now();
            ps.setString(6, fSol.format(dtfDateTime));
            ps.setString(7, p.getFechaEstimadaEntrega().format(dtfDate));
            LocalDate fechaVencimiento = p.getFechaVencimientoApartado() != null
                    ? p.getFechaVencimientoApartado()
                    : fSol.toLocalDate().plusDays(getDiasVigenciaApartado());
            p.setFechaVencimientoApartado(fechaVencimiento);
            ps.setString(8, fechaVencimiento.format(dtfDate));
            ps.setString(9, p.getEstado() != null ? p.getEstado() : "Pendiente");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setIdPreventa(keys.getInt(1));
                    p.setFechaSolicitud(fSol);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar preventa: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updatePreventaEstado(int idPreventa, String nuevoEstado) {
        Connection conn;
        try {
            conn = getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo preparar la actualización de la preventa: " + ex.getMessage(), ex);
        }
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            String estadoActual;
            int idCliente;
            BigDecimal montoPagado;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado, id_cliente, monto_anticipo FROM preventa WHERE id_preventa = ?")) {
                ps.setInt(1, idPreventa);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("No se encontró la preventa seleccionada.");
                    estadoActual = rs.getString("estado");
                    idCliente = rs.getInt("id_cliente");
                    montoPagado = rs.getBigDecimal("monto_anticipo");
                }
            }

            if ("Cancelado".equalsIgnoreCase(nuevoEstado)) {
                if ("Entregado".equalsIgnoreCase(estadoActual)) {
                    throw new IllegalStateException("No se puede cancelar una preventa que ya fue entregada.");
                }
                if (!"Cancelado".equalsIgnoreCase(estadoActual)) {
                    emitirSaldoFavor(conn, idCliente, montoPagado, "PREVENTA", idPreventa);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE preventa SET estado = ? WHERE id_preventa = ?")) {
                ps.setString(1, nuevoEstado);
                ps.setInt(2, idPreventa);
                if (ps.executeUpdate() != 1) throw new SQLException("No se pudo actualizar el estado de la preventa.");
            }
            conn.commit();
        } catch (Exception ex) {
            try { conn.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw new RuntimeException("Error al actualizar preventa: " + ex.getMessage(), ex);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ex) {
                throw new RuntimeException("No se pudo restablecer la conexión tras actualizar la preventa.", ex);
            }
        }
        syncSqliteBackup();
    }

    private void emitirSaldoFavor(Connection conn, int idCliente, BigDecimal monto,
                                  String tipoOrigen, int idOrigen) throws SQLException {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) return;
        int diasVigencia = getDiasVigenciaSaldoFavor();
        String fechaVencimiento = diasVigencia == 0 ? null
                : LocalDate.now().plusDays(diasVigencia).format(dtfDate);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO saldo_favor (id_cliente, monto_original, saldo_disponible, fecha_emision, fecha_vencimiento, tipo_origen, id_origen) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, idCliente);
            ps.setBigDecimal(2, monto);
            ps.setBigDecimal(3, monto);
            ps.setString(4, LocalDateTime.now().format(dtfDateTime));
            if (fechaVencimiento == null) ps.setNull(5, Types.DATE);
            else ps.setString(5, fechaVencimiento);
            ps.setString(6, tipoOrigen);
            ps.setInt(7, idOrigen);
            ps.executeUpdate();
        }
    }

    public synchronized BigDecimal getSaldoFavorCliente(int idCliente) {
        String sql = "SELECT COALESCE(SUM(saldo_disponible), 0) FROM saldo_favor " +
                "WHERE id_cliente = ? AND saldo_disponible > 0 AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.setString(2, LocalDate.now().format(dtfDate));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el saldo a favor: " + ex.getMessage(), ex);
        }
    }

    public synchronized void liquidarPreventa(int idPreventa, String metodoPago) throws Exception {
        liquidarPreventa(idPreventa, metodoPago, BigDecimal.ZERO);
    }

    public synchronized void liquidarPreventa(int idPreventa, String metodoPago,
                                               BigDecimal montoSaldoFavor) throws Exception {
        liquidarPreventa(idPreventa, metodoPago, montoSaldoFavor, null);
    }

    public synchronized void liquidarPreventa(int idPreventa, String metodoPago,
                                               BigDecimal montoSaldoFavor,
                                               Integer idUsuarioOperador) throws Exception {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            if (idUsuarioOperador != null) validarUsuarioActivo(conn, idUsuarioOperador);

            int idCliente;
            int idUsuario;
            int idProducto;
            BigDecimal montoTotal;
            BigDecimal montoAnticipo;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_cliente, id_usuario, id_producto, monto_anticipo, monto_total, estado FROM preventa WHERE id_preventa = ?")) {
                ps.setInt(1, idPreventa);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No se encontró la preventa seleccionada.");
                    if (!"Pendiente".equalsIgnoreCase(rs.getString("estado"))) {
                        throw new Exception("La preventa ya no está pendiente y no puede liquidarse.");
                    }
                    idCliente = rs.getInt("id_cliente");
                    int idUsuarioPreventa = rs.getInt("id_usuario");
                    idUsuario = idUsuarioOperador == null ? idUsuarioPreventa : idUsuarioOperador;
                    idProducto = rs.getInt("id_producto");
                    montoAnticipo = rs.getBigDecimal("monto_anticipo");
                    montoTotal = rs.getBigDecimal("monto_total");
                }
            }

            BigDecimal saldoPendiente = montoTotal.subtract(montoAnticipo);
            if (montoSaldoFavor == null || montoSaldoFavor.compareTo(BigDecimal.ZERO) < 0 ||
                    montoSaldoFavor.compareTo(saldoPendiente) > 0) {
                throw new Exception("El saldo a favor aplicado no puede superar el saldo pendiente de la preventa.");
            }
            if (montoSaldoFavor.compareTo(BigDecimal.ZERO) > 0) {
                consumirSaldoFavor(conn, idCliente, montoSaldoFavor);
            }

            String sqlVenta = "INSERT INTO venta (id_cliente, id_usuario, id_preventa, fecha_venta, monto_total, metodo_pago, monto_saldo_favor) VALUES (?, ?, ?, ?, ?, ?, ?)";
            int idVenta;
            try (PreparedStatement ps = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idCliente);
                ps.setInt(2, idUsuario);
                ps.setInt(3, idPreventa);
                ps.setString(4, LocalDateTime.now().format(dtfDateTime));
                ps.setBigDecimal(5, montoTotal);
                ps.setString(6, metodoPago);
                ps.setBigDecimal(7, montoSaldoFavor);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para la venta.");
                    idVenta = keys.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_historico) VALUES (?, ?, 1, ?)")) {
                ps.setInt(1, idVenta);
                ps.setInt(2, idProducto);
                ps.setBigDecimal(3, montoTotal);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE preventa SET estado = 'Entregado' WHERE id_preventa = ? AND estado = 'Pendiente'")) {
                ps.setInt(1, idPreventa);
                if (ps.executeUpdate() != 1) {
                    throw new Exception("La preventa ya no está pendiente y no pudo marcarse como entregada.");
                }
            }

            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        syncSqliteBackup();
    }

    // =========================================================================
    // CRUD: TORNEO & PARTICIPANTE_TORNEO
    // =========================================================================

    public synchronized List<Torneo> getTorneos() {
        List<Torneo> list = new ArrayList<>();
        String sql = "SELECT t.id_torneo, t.id_organizador, t.nombre_torneo, t.juego_tcg, t.fecha_evento, t.costo_inscripcion, " +
                "t.cupo_maximo, t.estado, t.porcentaje_premios_monetarios, " +
                "u.nombre AS org_nom, u.apellido AS org_ape, u.rol AS org_rol, " +
                "(SELECT COUNT(*) FROM participante_torneo pt WHERE pt.id_torneo = t.id_torneo) AS participantes_inscritos, " +
                "(SELECT COUNT(*) FROM participante_torneo pt WHERE pt.id_torneo = t.id_torneo AND pt.pago_confirmado = 1) AS participantes_pagados " +
                "FROM torneo t " +
                "JOIN usuario u ON t.id_organizador = u.id_usuario " +
                "ORDER BY t.id_torneo DESC";

        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Usuario u = new Usuario();
                u.setIdUsuario(rs.getInt("id_organizador"));
                u.setNombre(rs.getString("org_nom"));
                u.setApellido(rs.getString("org_ape"));
                u.setRol(rs.getString("org_rol"));

                Torneo t = new Torneo();
                t.setIdTorneo(rs.getInt("id_torneo"));
                t.setOrganizador(u);
                t.setNombreTorneo(rs.getString("nombre_torneo"));
                t.setJuegoTcg(rs.getString("juego_tcg"));
                t.setFechaEvento(parseDateTime(rs.getString("fecha_evento")));
                t.setCostoInscripcion(rs.getBigDecimal("costo_inscripcion"));
                t.setCupoMaximo(rs.getInt("cupo_maximo"));
                t.setEstado(rs.getString("estado"));
                t.setParticipantesInscritos(rs.getInt("participantes_inscritos"));
                t.setParticipantesPagados(rs.getInt("participantes_pagados"));
                t.setPorcentajePremiosMonetarios(rs.getBigDecimal("porcentaje_premios_monetarios"));

                list.add(t);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        Map<Integer, BigDecimal> porcentajeRestante = new HashMap<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id_torneo, porcentaje_fondo FROM premio_torneo " +
                             "WHERE tipo_premio = 'MONETARIO' ORDER BY id_torneo, posicion")) {
            while (rs.next()) {
                int idTorneo = rs.getInt("id_torneo");
                BigDecimal restante = porcentajeRestante.getOrDefault(idTorneo, new BigDecimal("100"));
                BigDecimal porcentajePuesto = rs.getBigDecimal("porcentaje_fondo");
                BigDecimal asignado = restante.multiply(porcentajePuesto)
                        .divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);
                porcentajeRestante.put(idTorneo, restante.subtract(asignado));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        for (Torneo torneo : list) {
            BigDecimal restante = porcentajeRestante.getOrDefault(torneo.getIdTorneo(), new BigDecimal("100"));
            torneo.setPorcentajeFondoAsignado(new BigDecimal("100").subtract(restante));
        }
        return list;
    }

    public synchronized List<ProductoPremio> getProductosPremio() {
        List<ProductoPremio> productos = new ArrayList<>();
        String sql = "SELECT id_producto_premio, nombre, patrocinador, descripcion, stock " +
                "FROM producto_premio ORDER BY nombre ASC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ProductoPremio producto = new ProductoPremio();
                producto.setIdProductoPremio(rs.getInt("id_producto_premio"));
                producto.setNombre(rs.getString("nombre"));
                producto.setPatrocinador(rs.getString("patrocinador"));
                producto.setDescripcion(rs.getString("descripcion"));
                producto.setStock(rs.getInt("stock"));
                productos.add(producto);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el catálogo de premios: " + ex.getMessage(), ex);
        }
        return productos;
    }

    public synchronized void addProductoPremio(ProductoPremio producto) {
        validarProductoPremio(producto);
        String sql = "INSERT INTO producto_premio (nombre, patrocinador, descripcion, stock) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, producto.getNombre().trim());
            ps.setString(2, normalizarTextoOpcional(producto.getPatrocinador()));
            ps.setString(3, normalizarTextoOpcional(producto.getDescripcion()));
            ps.setInt(4, producto.getStock());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para el premio.");
                producto.setIdProductoPremio(keys.getInt(1));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar el premio: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void updateProductoPremio(ProductoPremio producto) {
        validarProductoPremio(producto);
        String sql = "UPDATE producto_premio SET nombre = ?, patrocinador = ?, descripcion = ?, stock = ? " +
                "WHERE id_producto_premio = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, producto.getNombre().trim());
            ps.setString(2, normalizarTextoOpcional(producto.getPatrocinador()));
            ps.setString(3, normalizarTextoOpcional(producto.getDescripcion()));
            ps.setInt(4, producto.getStock());
            ps.setInt(5, producto.getIdProductoPremio());
            if (ps.executeUpdate() != 1) throw new SQLException("No se encontró el premio que se desea actualizar.");
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar el premio: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    private void validarProductoPremio(ProductoPremio producto) {
        if (producto == null || producto.getNombre() == null || producto.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del premio es obligatorio.");
        }
        if (producto.getStock() < 0) {
            throw new IllegalArgumentException("La cantidad disponible del premio no puede ser negativa.");
        }
    }

    private String normalizarTextoOpcional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public synchronized List<PremioTorneo> getPremiosTorneo(int idTorneo) {
        List<PremioTorneo> premios = new ArrayList<>();
        String sql = "SELECT pt.posicion, pt.tipo_premio, pt.id_producto, pt.id_producto_premio, " +
                "pt.porcentaje_fondo, p.nombre AS nombre_inventario, pp.nombre AS nombre_patrocinado " +
                "FROM premio_torneo pt " +
                "LEFT JOIN producto p ON pt.id_producto = p.id_producto " +
                "LEFT JOIN producto_premio pp ON pt.id_producto_premio = pp.id_producto_premio " +
                "WHERE pt.id_torneo = ? ORDER BY pt.posicion ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PremioTorneo premio = new PremioTorneo();
                    premio.setIdTorneo(idTorneo);
                    premio.setPosicion(rs.getInt("posicion"));
                    premio.setTipoPremio(rs.getString("tipo_premio"));
                    int idProducto = rs.getInt("id_producto");
                    if (!rs.wasNull()) premio.setIdProducto(idProducto);
                    int idProductoPremio = rs.getInt("id_producto_premio");
                    if (!rs.wasNull()) premio.setIdProductoPremio(idProductoPremio);
                    premio.setPorcentajeFondo(rs.getBigDecimal("porcentaje_fondo"));
                    String nombre = rs.getString("nombre_inventario");
                    if (nombre == null) nombre = rs.getString("nombre_patrocinado");
                    premio.setNombrePremio(nombre);
                    premios.add(premio);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar los premios del torneo: " + ex.getMessage(), ex);
        }
        return premios;
    }

    public synchronized void guardarPremiosTorneo(int idTorneo, BigDecimal porcentajeFondo,
                                                   List<PremioTorneo> premios) {
        if (idTorneo <= 0 || premios == null || premios.isEmpty()) {
            throw new IllegalArgumentException("Configura al menos un puesto con premio.");
        }
        if (porcentajeFondo == null || porcentajeFondo.compareTo(BigDecimal.ZERO) < 0 ||
                porcentajeFondo.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("El fondo de premios debe estar entre 0 y 100% de la recaudación.");
        }
        boolean hayPremioMonetario = false;
        for (int i = 0; i < premios.size(); i++) {
            PremioTorneo premio = premios.get(i);
            if (premio.getPosicion() != i + 1) {
                throw new IllegalArgumentException("Los puestos deben ser consecutivos empezando por el primer lugar.");
            }
            String tipo = premio.getTipoPremio();
            if (PremioTorneo.TIPO_MONETARIO.equals(tipo)) {
                BigDecimal porcentaje = premio.getPorcentajeFondo();
                if (porcentaje.compareTo(BigDecimal.ZERO) <= 0 || porcentaje.compareTo(new BigDecimal("100")) > 0) {
                    throw new IllegalArgumentException("Cada puesto monetario debe tener un porcentaje del fondo entre 0 y 100.");
                }
                hayPremioMonetario = true;
                premio.setIdProducto(null);
                premio.setIdProductoPremio(null);
            } else if (PremioTorneo.TIPO_INVENTARIO.equals(tipo)) {
                if (premio.getIdProducto() == null || premio.getIdProducto() <= 0) {
                    throw new IllegalArgumentException("Selecciona el producto de inventario para cada puesto.");
                }
                premio.setPorcentajeFondo(BigDecimal.ZERO);
                premio.setIdProductoPremio(null);
            } else if (PremioTorneo.TIPO_PATROCINADO.equals(tipo)) {
                if (premio.getIdProductoPremio() == null || premio.getIdProductoPremio() <= 0) {
                    throw new IllegalArgumentException("Selecciona el producto patrocinado para cada puesto.");
                }
                premio.setPorcentajeFondo(BigDecimal.ZERO);
                premio.setIdProducto(null);
            } else {
                throw new IllegalArgumentException("El tipo de premio seleccionado no es válido.");
            }
        }
        if (hayPremioMonetario && porcentajeFondo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Define un porcentaje de la recaudación para crear el fondo monetario.");
        }
        if (!hayPremioMonetario) porcentajeFondo = BigDecimal.ZERO;

        Connection conn;
        try {
            conn = getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo preparar la configuración de premios: " + ex.getMessage(), ex);
        }
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("SELECT estado FROM torneo WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("No se encontró el torneo.");
                    if ("Finalizado".equalsIgnoreCase(rs.getString("estado")) ||
                            "Cancelado".equalsIgnoreCase(rs.getString("estado"))) {
                        throw new IllegalStateException("No se pueden cambiar los premios de un torneo finalizado o cancelado.");
                    }
                }
            }
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM premio_torneo WHERE id_torneo = ?")) {
                delete.setInt(1, idTorneo);
                delete.executeUpdate();
            }
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE torneo SET porcentaje_premios_monetarios = ? WHERE id_torneo = ?")) {
                update.setBigDecimal(1, porcentajeFondo);
                update.setInt(2, idTorneo);
                if (update.executeUpdate() != 1) throw new SQLException("No se encontró el torneo para guardar el fondo de premios.");
            }
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO premio_torneo (id_torneo, posicion, tipo_premio, id_producto, id_producto_premio, porcentaje_fondo) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                for (PremioTorneo premio : premios) {
                    insert.setInt(1, idTorneo);
                    insert.setInt(2, premio.getPosicion());
                    insert.setString(3, premio.getTipoPremio());
                    if (premio.getIdProducto() == null) insert.setNull(4, Types.INTEGER);
                    else insert.setInt(4, premio.getIdProducto());
                    if (premio.getIdProductoPremio() == null) insert.setNull(5, Types.INTEGER);
                    else insert.setInt(5, premio.getIdProductoPremio());
                    insert.setBigDecimal(6, premio.getPorcentajeFondo());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            conn.commit();
        } catch (Exception ex) {
            try { conn.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw new RuntimeException("No se pudieron guardar los premios del torneo: " + ex.getMessage(), ex);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ex) {
                throw new RuntimeException("No se pudo restablecer la conexión tras configurar los premios.", ex);
            }
        }
        syncSqliteBackup();
    }

    public synchronized void addTorneo(Torneo t) {
        addTorneo(t, null);
    }

    public synchronized void addTorneo(Torneo t, PremioInscripcionTorneo premioInscripcion) {
        if (t.getCupoMaximo() < 2 || t.getCupoMaximo() % 2 != 0) {
            throw new IllegalArgumentException("El cupo máximo del torneo debe ser un número par mayor o igual a 2.");
        }
        Connection conn;
        boolean originalAutoCommit;
        try {
            conn = getConnection();
            originalAutoCommit = conn.getAutoCommit();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo preparar el registro del torneo: " + ex.getMessage(), ex);
        }
        String sql = "INSERT INTO torneo (id_organizador, nombre_torneo, juego_tcg, fecha_evento, costo_inscripcion, cupo_maximo, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getIdOrganizador());
            ps.setString(2, t.getNombreTorneo());
            ps.setString(3, t.getJuegoTcg());
            ps.setString(4, t.getFechaEvento().format(dtfDateTime));
            ps.setBigDecimal(5, t.getCostoInscripcion());
            ps.setInt(6, t.getCupoMaximo());
            ps.setString(7, t.getEstado() != null ? t.getEstado() : "Programado");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para el torneo.");
                t.setIdTorneo(keys.getInt(1));
                t.setParticipantesInscritos(0);
            }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO producto (codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, porcentaje_apartado, stock, id_torneo, tipo_venta, fecha_salida) " +
                            "VALUES (NULL, ?, ?, 'Ticket de participación', ?, ?, 30.00, ?, ?, ?, NULL)")) {
                ps.setString(1, "Inscripción: " + t.getNombreTorneo());
                ps.setString(2, "Ticket de participación para el torneo " + t.getNombreTorneo());
                ps.setString(3, t.getJuegoTcg());
                ps.setBigDecimal(4, t.getCostoInscripcion());
                ps.setInt(5, t.getCupoMaximo());
                ps.setInt(6, t.getIdTorneo());
                ps.setString(7, Producto.TIPO_VENTA_NORMAL);
                ps.executeUpdate();
            }
            if (premioInscripcion != null) {
                guardarConfiguracionPremioInscripcion(conn, t.getIdTorneo(), premioInscripcion);
            }
            conn.commit();
        } catch (Exception ex) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            if (ex instanceof RuntimeException) throw (RuntimeException) ex;
            throw new RuntimeException("Error al agregar torneo: " + ex.getMessage(), ex);
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ex) {
                throw new RuntimeException("No se pudo restablecer la conexión tras guardar el torneo: " + ex.getMessage(), ex);
            }
        }
        syncSqliteBackup();
    }

    private void guardarConfiguracionPremioInscripcion(Connection conn, int idTorneo,
                                                       PremioInscripcionTorneo premio) throws SQLException {
        if (premio == null || premio.getTipoPremio() == null) {
            throw new IllegalArgumentException("Selecciona un tipo de premio por inscripción.");
        }
        String tipo = premio.getTipoPremio();
        if (PremioInscripcionTorneo.TIPO_INVENTARIO.equals(tipo)) {
            if (premio.getIdProducto() == null || premio.getIdProducto() <= 0) {
                throw new IllegalArgumentException("Selecciona el producto de inventario para las inscripciones.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM producto WHERE id_producto = ? AND id_torneo IS NULL " +
                            "AND tipo_venta = 'VENTA_NORMAL' AND stock > 0")) {
                ps.setInt(1, premio.getIdProducto());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("El producto de inscripción ya no está disponible.");
                }
            }
            premio.setIdProductoPremio(null);
            premio.setMonto(BigDecimal.ZERO);
        } else if (PremioInscripcionTorneo.TIPO_PATROCINADO.equals(tipo)) {
            if (premio.getIdProductoPremio() == null || premio.getIdProductoPremio() <= 0) {
                throw new IllegalArgumentException("Selecciona el premio patrocinado para las inscripciones.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM producto_premio WHERE id_producto_premio = ? AND stock > 0")) {
                ps.setInt(1, premio.getIdProductoPremio());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("El premio patrocinado ya no está disponible.");
                }
            }
            premio.setIdProducto(null);
            premio.setMonto(BigDecimal.ZERO);
        } else if (PremioInscripcionTorneo.TIPO_SALDO_FAVOR.equals(tipo)) {
            BigDecimal monto = premio.getMonto();
            if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 ||
                    monto.compareTo(new BigDecimal("99999999.99")) > 0 ||
                    monto.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("El saldo por inscripción debe ser mayor que cero y tener un máximo de dos decimales.");
            }
            premio.setMonto(monto.setScale(2, java.math.RoundingMode.UNNECESSARY));
            premio.setIdProducto(null);
            premio.setIdProductoPremio(null);
        } else {
            throw new IllegalArgumentException("El tipo de premio por inscripción no es válido.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO premio_inscripcion_torneo (id_torneo, tipo_premio, id_producto, id_producto_premio, monto) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idTorneo);
            ps.setString(2, tipo);
            if (premio.getIdProducto() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, premio.getIdProducto());
            if (premio.getIdProductoPremio() == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, premio.getIdProductoPremio());
            ps.setBigDecimal(5, premio.getMonto());
            ps.executeUpdate();
        }
    }

    private String otorgarPremioInscripcion(Connection conn, int idTorneo, int idCliente,
                                            int idInscripcion) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT descripcion FROM premio_inscripcion_otorgado WHERE id_inscripcion = ?")) {
            ps.setInt(1, idInscripcion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("descripcion");
            }
        }

        String tipo;
        Integer idProducto;
        Integer idProductoPremio;
        BigDecimal monto;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tipo_premio, id_producto, id_producto_premio, monto " +
                        "FROM premio_inscripcion_torneo WHERE id_torneo = ?")) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                tipo = rs.getString("tipo_premio");
                int producto = rs.getInt("id_producto");
                idProducto = rs.wasNull() ? null : producto;
                int productoPremio = rs.getInt("id_producto_premio");
                idProductoPremio = rs.wasNull() ? null : productoPremio;
                monto = rs.getBigDecimal("monto");
            }
        }

        String descripcion;
        if (PremioInscripcionTorneo.TIPO_INVENTARIO.equals(tipo)) {
            String nombre;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre FROM producto WHERE id_producto = ? AND id_torneo IS NULL " +
                            "AND tipo_venta = 'VENTA_NORMAL' AND stock > 0")) {
                ps.setInt(1, idProducto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No hay existencias del premio de inscripción seleccionado.");
                    nombre = rs.getString("nombre");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto SET stock = stock - 1 WHERE id_producto = ? AND stock > 0")) {
                ps.setInt(1, idProducto);
                if (ps.executeUpdate() != 1) throw new Exception("No hay existencias del premio de inscripción seleccionado.");
            }
            descripcion = "Premio por inscripción: producto de tienda: " + nombre;
        } else if (PremioInscripcionTorneo.TIPO_PATROCINADO.equals(tipo)) {
            String nombre;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre FROM producto_premio WHERE id_producto_premio = ? AND stock > 0")) {
                ps.setInt(1, idProductoPremio);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No hay existencias del premio patrocinado de inscripción.");
                    nombre = rs.getString("nombre");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto_premio SET stock = stock - 1 WHERE id_producto_premio = ? AND stock > 0")) {
                ps.setInt(1, idProductoPremio);
                if (ps.executeUpdate() != 1) throw new Exception("No hay existencias del premio patrocinado de inscripción.");
            }
            descripcion = "Premio por inscripción: producto patrocinado: " + nombre;
        } else if (PremioInscripcionTorneo.TIPO_SALDO_FAVOR.equals(tipo)) {
            if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
                throw new SQLException("El saldo a favor configurado para la inscripción no es válido.");
            }
            emitirSaldoFavor(conn, idCliente, monto, "PREMIO_INSCRIPCION", idInscripcion);
            descripcion = "Premio por inscripción: saldo a favor: $" + monto.toPlainString();
        } else {
            throw new SQLException("El tipo de premio por inscripción guardado no es válido.");
        }

        String fechaOtorgamiento = LocalDateTime.now().format(dtfDateTime);
        String idJugadorTcg;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id_jugador_tcg FROM participante_torneo WHERE id_inscripcion = ?")) {
            ps.setInt(1, idInscripcion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se encontró la inscripción para registrar su premio.");
                idJugadorTcg = rs.getString("id_jugador_tcg");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO premio_inscripcion_otorgado " +
                        "(id_torneo, id_cliente, id_inscripcion, id_jugador_tcg, fecha_otorgamiento, descripcion) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, idTorneo);
            ps.setInt(2, idCliente);
            ps.setInt(3, idInscripcion);
            ps.setString(4, idJugadorTcg);
            ps.setString(5, fechaOtorgamiento);
            ps.setString(6, descripcion);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE participante_torneo SET premio_inscripcion_otorgado = ? WHERE id_inscripcion = ?")) {
            ps.setString(1, descripcion);
            ps.setInt(2, idInscripcion);
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo actualizar la inscripción con su premio.");
        }
        return descripcion;
    }

    public synchronized void updateTorneoEstado(int idTorneo, String nuevoEstado) {
        if (!Arrays.asList("Programado", "En Curso", "Cancelado").contains(nuevoEstado)) {
            throw new IllegalArgumentException("Para finalizar un torneo, registra primero sus ganadores y premios.");
        }
        Connection conn;
        try {
            conn = getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo preparar la actualización del torneo: " + ex.getMessage(), ex);
        }
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            String estadoActual;
            BigDecimal costoInscripcion;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado, costo_inscripcion FROM torneo WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("No se encontró el torneo.");
                    estadoActual = rs.getString("estado");
                    costoInscripcion = rs.getBigDecimal("costo_inscripcion");
                }
            }
            if ("Finalizado".equalsIgnoreCase(estadoActual)) {
                throw new IllegalStateException("No se puede cambiar el estado de un torneo finalizado.");
            }
            if ("Cancelado".equalsIgnoreCase(estadoActual) && !"Cancelado".equalsIgnoreCase(nuevoEstado)) {
                throw new IllegalStateException("Un torneo cancelado no se puede reactivar.");
            }
            if ("Cancelado".equalsIgnoreCase(nuevoEstado) && !"Cancelado".equalsIgnoreCase(estadoActual)) {
                Map<Integer, Integer> inscripcionesPagadas = new LinkedHashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id_inscripcion, id_cliente FROM participante_torneo " +
                                "WHERE id_torneo = ? AND pago_confirmado = 1")) {
                    ps.setInt(1, idTorneo);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            inscripcionesPagadas.put(rs.getInt("id_inscripcion"), rs.getInt("id_cliente"));
                        }
                    }
                }
                for (Map.Entry<Integer, Integer> inscripcion : inscripcionesPagadas.entrySet()) {
                    emitirSaldoFavor(conn, inscripcion.getValue(), costoInscripcion,
                            "TORNEO", inscripcion.getKey());
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE producto SET stock = 0 WHERE id_torneo = ?")) {
                    ps.setInt(1, idTorneo);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE torneo SET estado = ? WHERE id_torneo = ?")) {
                ps.setString(1, nuevoEstado);
                ps.setInt(2, idTorneo);
                if (ps.executeUpdate() != 1) throw new SQLException("No se pudo actualizar el estado del torneo.");
            }
            conn.commit();
        } catch (Exception ex) {
            try { conn.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw new RuntimeException("Error al actualizar torneo: " + ex.getMessage(), ex);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ex) {
                throw new RuntimeException("No se pudo restablecer la conexión tras actualizar el torneo.", ex);
            }
        }
        syncSqliteBackup();
    }

    public synchronized void finalizarTorneo(int idTorneo, Map<Integer, Integer> ganadorPorPosicion) throws Exception {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            BigDecimal costoInscripcion;
            BigDecimal porcentajeFondoPremios;
            String estado;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado, costo_inscripcion, porcentaje_premios_monetarios FROM torneo WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No se encontró el torneo.");
                    estado = rs.getString("estado");
                    costoInscripcion = rs.getBigDecimal("costo_inscripcion");
                    porcentajeFondoPremios = rs.getBigDecimal("porcentaje_premios_monetarios");
                }
            }
            if ("Finalizado".equalsIgnoreCase(estado) || "Cancelado".equalsIgnoreCase(estado)) {
                throw new Exception("El torneo ya está finalizado o cancelado.");
            }

            List<PremioTorneo> premios = getPremiosTorneo(idTorneo);
            if (premios.isEmpty()) throw new Exception("Configura los premios antes de finalizar el torneo.");
            if (ganadorPorPosicion == null || ganadorPorPosicion.size() != premios.size()) {
                throw new Exception("Debes asignar un ganador a cada puesto configurado.");
            }

            int participantesPagados;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM participante_torneo WHERE id_torneo = ? AND pago_confirmado = 1")) {
                ps.setInt(1, idTorneo);
                try (ResultSet rs = ps.executeQuery()) {
                    participantesPagados = rs.next() ? rs.getInt(1) : 0;
                }
            }
            BigDecimal recaudacion = costoInscripcion.multiply(BigDecimal.valueOf(participantesPagados));
            BigDecimal fondoPremios = recaudacion.multiply(porcentajeFondoPremios)
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal fondoRestante = fondoPremios;
            Set<Integer> ganadores = new HashSet<>();
            Map<Integer, String> premiosAsignados = new LinkedHashMap<>();

            for (PremioTorneo premio : premios) {
                Integer idCliente = ganadorPorPosicion.get(premio.getPosicion());
                if (idCliente == null || !ganadores.add(idCliente)) {
                    throw new Exception("Cada puesto debe tener un ganador diferente.");
                }
                int idInscripcionGanador;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id_inscripcion FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ? AND pago_confirmado = 1")) {
                    ps.setInt(1, idTorneo);
                    ps.setInt(2, idCliente);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new Exception("Todos los ganadores deben tener una inscripción pagada.");
                        idInscripcionGanador = rs.getInt("id_inscripcion");
                    }
                }

                String descripcion;
                if (PremioTorneo.TIPO_MONETARIO.equals(premio.getTipoPremio())) {
                    BigDecimal monto = fondoRestante.multiply(premio.getPorcentajeFondo())
                            .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    fondoRestante = fondoRestante.subtract(monto);
                    emitirSaldoFavor(conn, idCliente, monto, "PREMIO_TORNEO",
                            idInscripcionGanador);
                    descripcion = "Saldo a favor: $" + monto.toPlainString();
                } else if (PremioTorneo.TIPO_INVENTARIO.equals(premio.getTipoPremio())) {
                    String nombre;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT nombre FROM producto WHERE id_producto = ? AND id_torneo IS NULL " +
                                    "AND tipo_venta = 'VENTA_NORMAL' AND stock > 0")) {
                        ps.setInt(1, premio.getIdProducto());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new Exception("El producto de inventario del puesto " +
                                    premio.getPosicion() + " no tiene existencias o ya no está disponible.");
                            nombre = rs.getString("nombre");
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE producto SET stock = stock - 1 WHERE id_producto = ? AND stock > 0")) {
                        ps.setInt(1, premio.getIdProducto());
                        if (ps.executeUpdate() != 1) throw new Exception("No hay existencias suficientes del premio de inventario.");
                    }
                    descripcion = "Premio de inventario: " + nombre;
                } else {
                    String nombre;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT nombre FROM producto_premio WHERE id_producto_premio = ? AND stock > 0")) {
                        ps.setInt(1, premio.getIdProductoPremio());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new Exception("El premio patrocinado del puesto " +
                                    premio.getPosicion() + " ya no tiene existencias.");
                            nombre = rs.getString("nombre");
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE producto_premio SET stock = stock - 1 WHERE id_producto_premio = ? AND stock > 0")) {
                        ps.setInt(1, premio.getIdProductoPremio());
                        if (ps.executeUpdate() != 1) throw new Exception("No hay existencias suficientes del premio patrocinado.");
                    }
                    descripcion = "Premio patrocinado: " + nombre;
                }
                premiosAsignados.put(idCliente, premio.getPosicion() + "|" + descripcion);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE participante_torneo SET posicion_final = NULL, premio = NULL WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                ps.executeUpdate();
            }
            for (Map.Entry<Integer, String> premioAsignado : premiosAsignados.entrySet()) {
                String[] partes = premioAsignado.getValue().split("\\|", 2);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE participante_torneo SET posicion_final = ?, premio = ? " +
                                "WHERE id_torneo = ? AND id_cliente = ? AND pago_confirmado = 1")) {
                    ps.setInt(1, Integer.parseInt(partes[0]));
                    ps.setString(2, partes[1]);
                    ps.setInt(3, idTorneo);
                    ps.setInt(4, premioAsignado.getKey());
                    if (ps.executeUpdate() != 1) throw new SQLException("No se pudo registrar un premio ganador.");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE torneo SET estado = 'Finalizado' WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                if (ps.executeUpdate() != 1) throw new SQLException("No se pudo finalizar el torneo.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto SET stock = 0 WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception ex) {
            try { conn.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        syncSqliteBackup();
    }

    public synchronized Torneo findTorneoById(int idTorneo) {
        for (Torneo t : getTorneos()) {
            if (t.getIdTorneo() == idTorneo) return t;
        }
        return null;
    }

    public synchronized List<ParticipanteTorneo> getParticipantesByTorneo(int idTorneo) {
        List<ParticipanteTorneo> list = new ArrayList<>();
        String sql = "SELECT pt.id_inscripcion, pt.id_torneo, pt.id_cliente, pt.id_jugador_tcg, pt.fecha_inscripcion, " +
                "pt.pago_confirmado, pt.posicion_final, pt.premio, pt.premio_inscripcion_otorgado, " +
                "c.nombre AS cli_nom, c.apellido AS cli_ape, c.correo_electronico AS cli_cor, c.telefono AS cli_tel " +
                "FROM participante_torneo pt " +
                "JOIN cliente c ON pt.id_cliente = c.id_cliente " +
                "WHERE pt.id_torneo = ? " +
                "ORDER BY pt.id_inscripcion ASC";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idTorneo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente c = new Cliente();
                    c.setIdCliente(rs.getInt("id_cliente"));
                    c.setNombre(rs.getString("cli_nom"));
                    c.setApellido(rs.getString("cli_ape"));
                    c.setCorreoElectronico(rs.getString("cli_cor"));
                    c.setTelefono(rs.getString("cli_tel"));

                    ParticipanteTorneo pt = new ParticipanteTorneo();
                    pt.setIdInscripcion(rs.getInt("id_inscripcion"));
                    pt.setIdTorneo(idTorneo);
                    pt.setCliente(c);
                    pt.setIdJugadorTcg(rs.getString("id_jugador_tcg"));
                    pt.setFechaInscripcion(parseDateTime(rs.getString("fecha_inscripcion")));
                    pt.setPagoConfirmado(rs.getInt("pago_confirmado") != 0);
                    int pos = rs.getInt("posicion_final");
                    pt.setPosicionFinal(rs.wasNull() ? null : pos);
                    pt.setPremio(rs.getString("premio"));
                    pt.setPremioInscripcion(rs.getString("premio_inscripcion_otorgado"));

                    list.add(pt);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized List<HistorialPremioTorneo> getHistorialPremiosTorneo() {
        List<HistorialPremioTorneo> historial = new ArrayList<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT t.nombre_torneo, t.fecha_evento, c.nombre, c.apellido, " +
                             "pt.id_jugador_tcg, pt.posicion_final, pt.premio " +
                             "FROM participante_torneo pt " +
                             "JOIN torneo t ON t.id_torneo = pt.id_torneo " +
                             "JOIN cliente c ON c.id_cliente = pt.id_cliente " +
                             "WHERE pt.premio IS NOT NULL AND TRIM(pt.premio) <> '' " +
                             "ORDER BY t.fecha_evento DESC, t.id_torneo DESC, pt.posicion_final ASC")) {
            while (rs.next()) {
                HistorialPremioTorneo premio = crearRegistroHistorialPremio(
                        rs.getString("nombre_torneo"), rs.getString("fecha_evento"),
                        rs.getString("nombre"), rs.getString("apellido"),
                        rs.getString("id_jugador_tcg"), rs.getInt("posicion_final"), rs.getString("premio"));
                historial.add(premio);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el historial de premios de torneos: " + ex.getMessage(), ex);
        }
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT t.nombre_torneo, t.fecha_evento, c.nombre, c.apellido, " +
                             "pio.id_jugador_tcg, pio.descripcion " +
                             "FROM premio_inscripcion_otorgado pio " +
                             "JOIN torneo t ON t.id_torneo = pio.id_torneo " +
                             "JOIN cliente c ON c.id_cliente = pio.id_cliente " +
                             "ORDER BY t.fecha_evento DESC, t.id_torneo DESC, pio.fecha_otorgamiento DESC")) {
            while (rs.next()) {
                historial.add(crearRegistroHistorialPremio(
                        rs.getString("nombre_torneo"), rs.getString("fecha_evento"),
                        rs.getString("nombre"), rs.getString("apellido"),
                        rs.getString("id_jugador_tcg"), 0, rs.getString("descripcion")));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el historial de premios de torneos: " + ex.getMessage(), ex);
        }
        historial.sort(Comparator.comparing(HistorialPremioTorneo::getFechaEvento).reversed()
                .thenComparingInt(HistorialPremioTorneo::getPosicion));
        return historial;
    }

    private HistorialPremioTorneo crearRegistroHistorialPremio(String nombreTorneo, String fechaEvento,
                                                                String nombre, String apellido,
                                                                String idJugadorTcg, int posicion,
                                                                String descripcion) {
        HistorialPremioTorneo premio = new HistorialPremioTorneo();
        premio.setNombreTorneo(nombreTorneo);
        premio.setFechaEvento(parseDateTime(fechaEvento));
        premio.setNombreJugador(nombre + " " + apellido);
        premio.setIdJugadorTcg(idJugadorTcg);
        premio.setPosicion(posicion);
        premio.setDescripcionPremio(descripcion);
        if (descripcion.startsWith("Premio por inscripción:")) {
            if (descripcion.contains("saldo a favor:")) {
                premio.setTipoPremio("Inscripción: saldo a favor");
            } else if (descripcion.contains("producto patrocinado:")) {
                premio.setTipoPremio("Inscripción: producto patrocinado");
            } else if (descripcion.contains("producto de tienda:")) {
                premio.setTipoPremio("Inscripción: producto de tienda");
            } else {
                premio.setTipoPremio("Premio por inscripción");
            }
        } else if (descripcion.startsWith("Saldo a favor:") || descripcion.startsWith("Premio monetario:")) {
            premio.setTipoPremio("Saldo a favor en tienda");
        } else if (descripcion.startsWith("Premio de inventario:")) {
            premio.setTipoPremio("Producto de tienda");
        } else if (descripcion.startsWith("Premio patrocinado:")) {
            premio.setTipoPremio("Producto patrocinado");
        } else {
            premio.setTipoPremio("Otro premio");
        }
        return premio;
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt) throws Exception {
        inscribirParticipante(pt, "Efectivo");
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt, String metodoPago) throws Exception {
        inscribirParticipante(pt, metodoPago, BigDecimal.ZERO);
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt, String metodoPago,
                                                    BigDecimal montoSaldoFavor) throws Exception {
        inscribirParticipante(pt, metodoPago, montoSaldoFavor, null);
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt, String metodoPago,
                                                    BigDecimal montoSaldoFavor,
                                                    Integer idUsuarioOperador) throws Exception {
        Connection conn;
        boolean originalAutoCommit;
        try {
            conn = getConnection();
            originalAutoCommit = conn.getAutoCommit();
        } catch (SQLException ex) {
            throw new Exception("No se pudo preparar la inscripción: " + ex.getMessage(), ex);
        }
        LocalDateTime fecha = pt.getFechaInscripcion() != null ? pt.getFechaInscripcion() : LocalDateTime.now();
        try {
            conn.setAutoCommit(false);
            if (idUsuarioOperador != null) validarUsuarioActivo(conn, idUsuarioOperador);
            int cupoMaximo;
            int idOrganizador;
            BigDecimal costoInscripcion;
            String estadoTorneo;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cupo_maximo, id_organizador, costo_inscripcion, estado FROM torneo WHERE id_torneo = ?")) {
                ps.setInt(1, pt.getIdTorneo());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("Torneo no encontrado.");
                    cupoMaximo = rs.getInt("cupo_maximo");
                    idOrganizador = rs.getInt("id_organizador");
                    costoInscripcion = rs.getBigDecimal("costo_inscripcion");
                    estadoTorneo = rs.getString("estado");
                }
            }
            if (!"Programado".equalsIgnoreCase(estadoTorneo)) {
                throw new Exception("No se pueden inscribir jugadores después de que inicia el torneo.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ?")) {
                ps.setInt(1, pt.getIdTorneo());
                ps.setInt(2, pt.getIdCliente());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) throw new Exception("El cliente ya está inscrito en este torneo.");
                }
            }
            if (contarParticipantes(conn, pt.getIdTorneo()) >= cupoMaximo) {
                throw new Exception("El torneo ya no cuenta con cupos disponibles (Cupo máximo: " + cupoMaximo + ").");
            }

            int idProductoTicket;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_producto, stock FROM producto WHERE id_torneo = ?")) {
                ps.setInt(1, pt.getIdTorneo());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No existe el ticket de participación de este torneo.");
                    idProductoTicket = rs.getInt("id_producto");
                    if (rs.getInt("stock") <= 0) throw new Exception("El torneo ya agotó sus tickets de participación.");
                }
            }

            boolean registrarPago = pt.isPagoConfirmado() || costoInscripcion.compareTo(BigDecimal.ZERO) == 0;
            if (montoSaldoFavor == null || montoSaldoFavor.compareTo(BigDecimal.ZERO) < 0 ||
                    montoSaldoFavor.compareTo(costoInscripcion) > 0) {
                throw new Exception("El saldo a favor aplicado debe estar entre cero y el costo de inscripción.");
            }
            if (montoSaldoFavor.compareTo(BigDecimal.ZERO) > 0 && !registrarPago) {
                throw new Exception("Confirma el pago de la inscripción para utilizar saldo a favor.");
            }
            if (registrarPago) {
                if (montoSaldoFavor.compareTo(BigDecimal.ZERO) > 0) {
                    consumirSaldoFavor(conn, pt.getIdCliente(), montoSaldoFavor);
                }
                int idVenta;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO venta (id_cliente, id_usuario, fecha_venta, monto_total, metodo_pago, monto_saldo_favor) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, pt.getIdCliente());
                    ps.setInt(2, idUsuarioOperador == null ? idOrganizador : idUsuarioOperador);
                    ps.setString(3, fecha.format(dtfDateTime));
                    ps.setBigDecimal(4, costoInscripcion);
                    ps.setString(5, costoInscripcion.compareTo(BigDecimal.ZERO) == 0 ? "Efectivo" : metodoPago);
                    ps.setBigDecimal(6, montoSaldoFavor);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para el ticket.");
                        idVenta = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_historico) VALUES (?, ?, 1, ?)")) {
                    ps.setInt(1, idVenta);
                    ps.setInt(2, idProductoTicket);
                    ps.setBigDecimal(3, costoInscripcion);
                    ps.executeUpdate();
                }
                pt.setPagoConfirmado(true);
            } else {
                pt.setPagoConfirmado(false);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto SET stock = stock - 1 WHERE id_producto = ? AND stock > 0")) {
                ps.setInt(1, idProductoTicket);
                if (ps.executeUpdate() != 1) throw new Exception("No quedan tickets disponibles para este torneo.");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO participante_torneo (id_torneo, id_cliente, id_jugador_tcg, fecha_inscripcion, pago_confirmado, posicion_final) " +
                                "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, pt.getIdTorneo());
                ps.setInt(2, pt.getIdCliente());
                ps.setString(3, pt.getIdJugadorTcg());
                ps.setString(4, fecha.format(dtfDateTime));
                ps.setInt(5, pt.isPagoConfirmado() ? 1 : 0);
                if (pt.getPosicionFinal() != null) ps.setInt(6, pt.getPosicionFinal());
                else ps.setNull(6, Types.INTEGER);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No se obtuvo el ID generado para la inscripción.");
                    pt.setIdInscripcion(keys.getInt(1));
                }
            }
            if (pt.isPagoConfirmado()) {
                pt.setPremioInscripcion(otorgarPremioInscripcion(
                        conn, pt.getIdTorneo(), pt.getIdCliente(), pt.getIdInscripcion()));
            }
            pt.setFechaInscripcion(fecha);
            conn.commit();
        } catch (Exception ex) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        syncSqliteBackup();
    }

    public synchronized void eliminarParticipante(int idInscripcion) {
        Connection conn;
        try {
            conn = getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo preparar la cancelación de la inscripción: " + ex.getMessage(), ex);
        }
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int idTorneo;
            int idCliente;
            boolean pagoConfirmado;
            String estadoTorneo;
            BigDecimal costoInscripcion;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pt.id_torneo, pt.id_cliente, pt.pago_confirmado, t.estado, t.costo_inscripcion " +
                            "FROM participante_torneo pt JOIN torneo t ON pt.id_torneo = t.id_torneo " +
                            "WHERE pt.id_inscripcion = ?")) {
                ps.setInt(1, idInscripcion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("No se encontró la inscripción que se desea cancelar.");
                    idTorneo = rs.getInt("id_torneo");
                    idCliente = rs.getInt("id_cliente");
                    pagoConfirmado = rs.getInt("pago_confirmado") != 0;
                    estadoTorneo = rs.getString("estado");
                    costoInscripcion = rs.getBigDecimal("costo_inscripcion");
                }
            }
            if ("Finalizado".equalsIgnoreCase(estadoTorneo) || "Cancelado".equalsIgnoreCase(estadoTorneo)) {
                throw new IllegalStateException("No se puede cancelar la inscripción de un torneo finalizado o cancelado.");
            }
            if (pagoConfirmado) emitirSaldoFavor(conn, idCliente, costoInscripcion, "TORNEO", idInscripcion);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto SET stock = stock + 1 WHERE id_torneo = ?")) {
                ps.setInt(1, idTorneo);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM participante_torneo WHERE id_inscripcion = ?")) {
                ps.setInt(1, idInscripcion);
                if (ps.executeUpdate() != 1) throw new SQLException("No se pudo cancelar la inscripción.");
            }
            conn.commit();
        } catch (Exception ex) {
            try { conn.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw new RuntimeException("Error al cancelar inscripción: " + ex.getMessage(), ex);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ex) {
                throw new RuntimeException("No se pudo restablecer la conexión tras cancelar la inscripción.", ex);
            }
        }
        syncSqliteBackup();
    }

    public synchronized void actualizarResultadoParticipante(int idInscripcion, Integer posicion, String premio) {
        String sql = "UPDATE participante_torneo SET posicion_final = ?, premio = ? WHERE id_inscripcion = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            if (posicion == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, posicion);
            if (premio == null || premio.trim().isEmpty()) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, premio.trim());
            ps.setInt(3, idInscripcion);
            if (ps.executeUpdate() != 1) throw new SQLException("No se encontró la inscripción seleccionada.");
        } catch (SQLException ex) {
            throw new RuntimeException("Error al registrar el lugar y premio: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized int getDiasMargenLlegada() {
        return obtenerParametroEntero("margen_llegada_preventa_dias", 7, "margen de llegada");
    }

    public synchronized void setDiasMargenLlegada(int dias) {
        if (dias < 0 || dias > 365) {
            throw new IllegalArgumentException("El margen de llegada debe estar entre 0 y 365 días.");
        }
        guardarParametros(Collections.singletonMap("margen_llegada_preventa_dias", String.valueOf(dias)));
    }

    public synchronized BigDecimal getPorcentajeApartadoPredeterminado() {
        return obtenerParametroDecimal("porcentaje_apartado_predeterminado", new BigDecimal("30.00"),
                "porcentaje predeterminado de apartado");
    }

    public synchronized int getDiasVigenciaApartado() {
        return obtenerParametroEntero("vigencia_apartado_dias", 30, "vigencia del apartado");
    }

    public synchronized int getDiasVigenciaSaldoFavor() {
        int dias = obtenerParametroEntero("vigencia_saldo_favor_dias", 30, "vigencia del saldo a favor");
        if (dias < 0 || dias > 3650) {
            throw new RuntimeException("La vigencia guardada para el saldo a favor debe estar entre 0 y 3650 días.");
        }
        return dias;
    }

    public synchronized int getCupoTorneoPredeterminado() {
        int cupo = obtenerParametroEntero("cupo_torneo_predeterminado", 16, "cupo predeterminado de torneo");
        if (cupo < 2 || cupo % 2 != 0) {
            throw new RuntimeException("El cupo predeterminado guardado debe ser un número par mayor o igual a 2.");
        }
        return cupo;
    }

    public synchronized BigDecimal getCostoTorneoPredeterminado() {
        return obtenerParametroDecimal("costo_torneo_predeterminado", new BigDecimal("15.00"),
                "costo predeterminado de torneo");
    }

    public synchronized void guardarParametrosOperativos(int diasMargenLlegada, BigDecimal porcentajeApartado,
                                                          int diasVigenciaApartado, BigDecimal costoTorneo,
                                                          int cupoTorneo, int diasVigenciaSaldoFavor) {
        if (diasMargenLlegada < 0 || diasMargenLlegada > 365) {
            throw new IllegalArgumentException("El margen de llegada debe estar entre 0 y 365 días.");
        }
        validarPorcentajeApartado(porcentajeApartado);
        if (diasVigenciaApartado < 0 || diasVigenciaApartado > 365) {
            throw new IllegalArgumentException("La vigencia del apartado debe estar entre 0 y 365 días.");
        }
        validarCostoTorneo(costoTorneo);
        if (cupoTorneo < 2 || cupoTorneo % 2 != 0) {
            throw new IllegalArgumentException("El cupo predeterminado debe ser un número par mayor o igual a 2.");
        }
        if (diasVigenciaSaldoFavor < 0 || diasVigenciaSaldoFavor > 3650) {
            throw new IllegalArgumentException("La vigencia del saldo a favor debe estar entre 0 y 3650 días.");
        }

        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("margen_llegada_preventa_dias", String.valueOf(diasMargenLlegada));
        parametros.put("porcentaje_apartado_predeterminado", porcentajeApartado.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        parametros.put("vigencia_apartado_dias", String.valueOf(diasVigenciaApartado));
        parametros.put("costo_torneo_predeterminado", costoTorneo.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        parametros.put("cupo_torneo_predeterminado", String.valueOf(cupoTorneo));
        parametros.put("vigencia_saldo_favor_dias", String.valueOf(diasVigenciaSaldoFavor));
        guardarParametros(parametros);
    }

    private int obtenerParametroEntero(String clave, int valorPredeterminado, String nombre) {
        try {
            return Integer.parseInt(obtenerParametro(clave, String.valueOf(valorPredeterminado)));
        } catch (NumberFormatException ex) {
            throw new RuntimeException("El valor guardado para " + nombre + " no es válido.", ex);
        }
    }

    private BigDecimal obtenerParametroDecimal(String clave, BigDecimal valorPredeterminado, String nombre) {
        try {
            return new BigDecimal(obtenerParametro(clave, valorPredeterminado.toPlainString()));
        } catch (NumberFormatException ex) {
            throw new RuntimeException("El valor guardado para " + nombre + " no es válido.", ex);
        }
    }

    private String obtenerParametro(String clave, String valorPredeterminado) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT valor FROM configuracion_sistema WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("valor") : valorPredeterminado;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al consultar el parámetro " + clave + ": " + ex.getMessage(), ex);
        }
    }

    private void guardarParametros(Map<String, String> parametros) {
        Connection conn = null;
        boolean autoCommitOriginal = true;
        try {
            conn = getConnection();
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement exists = conn.prepareStatement(
                    "SELECT 1 FROM configuracion_sistema WHERE clave = ?");
                 PreparedStatement update = conn.prepareStatement(
                    "UPDATE configuracion_sistema SET valor = ? WHERE clave = ?");
                 PreparedStatement insert = conn.prepareStatement(
                         "INSERT INTO configuracion_sistema (clave, valor) VALUES (?, ?)")) {
                for (Map.Entry<String, String> parametro : parametros.entrySet()) {
                    exists.setString(1, parametro.getKey());
                    boolean encontrado;
                    try (ResultSet rs = exists.executeQuery()) {
                        encontrado = rs.next();
                    }
                    if (!encontrado) {
                        insert.setString(1, parametro.getKey());
                        insert.setString(2, parametro.getValue());
                        insert.executeUpdate();
                        continue;
                    }
                    update.setString(1, parametro.getValue());
                    update.setString(2, parametro.getKey());
                    update.executeUpdate();
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al guardar la configuración: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    private void validarPorcentajeApartado(BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.compareTo(new BigDecimal("0.01")) < 0 ||
                porcentaje.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("El porcentaje predeterminado debe estar entre 0.01 y 100.");
        }
    }

    private void validarCostoTorneo(BigDecimal costo) {
        if (costo == null || costo.compareTo(BigDecimal.ZERO) < 0 ||
                costo.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("El costo predeterminado del torneo debe estar entre 0 y 99,999,999.99.");
        }
    }

    // =========================================================================
    // UTILIDADES SQLITE, CONSOLA Y MANTENIMIENTO
    // =========================================================================

    public Map<String, Integer> getTableCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] tables = {"cliente", "usuario", "producto", "categoria_producto", "franquicia", "venta", "detalle_venta", "preventa", "torneo", "participante_torneo"};
        for (String t : tables) {
            try (Statement st = getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                if (rs.next()) counts.put(t, rs.getInt(1));
            } catch (Exception e) {
                counts.put(t, 0);
            }
        }
        return counts;
    }

    public synchronized void resetDatabase() throws SQLException {
        boolean wasSuspended = suspendBackupSync;
        suspendBackupSync = true;
        try {
            try (Statement st = getConnection().createStatement()) {
                if ("mariadb".equalsIgnoreCase(dbType)) {
                    st.execute("SET FOREIGN_KEY_CHECKS = 0;");
                    st.execute("DROP TABLE IF EXISTS participante_torneo;");
                    st.execute("DROP TABLE IF EXISTS torneo;");
                    st.execute("DROP TABLE IF EXISTS preventa;");
                    st.execute("DROP TABLE IF EXISTS detalle_venta;");
                    st.execute("DROP TABLE IF EXISTS venta;");
                    st.execute("DROP TABLE IF EXISTS producto;");
                    st.execute("DROP TABLE IF EXISTS categoria_producto;");
                    st.execute("DROP TABLE IF EXISTS franquicia;");
                    st.execute("DROP TABLE IF EXISTS usuario;");
                    st.execute("DROP TABLE IF EXISTS cliente;");
                    st.execute("SET FOREIGN_KEY_CHECKS = 1;");
                } else {
                    st.execute("PRAGMA foreign_keys = OFF;");
                    st.execute("DROP TABLE IF EXISTS participante_torneo;");
                    st.execute("DROP TABLE IF EXISTS torneo;");
                    st.execute("DROP TABLE IF EXISTS preventa;");
                    st.execute("DROP TABLE IF EXISTS detalle_venta;");
                    st.execute("DROP TABLE IF EXISTS venta;");
                    st.execute("DROP TABLE IF EXISTS producto;");
                    st.execute("DROP TABLE IF EXISTS categoria_producto;");
                    st.execute("DROP TABLE IF EXISTS franquicia;");
                    st.execute("DROP TABLE IF EXISTS usuario;");
                    st.execute("DROP TABLE IF EXISTS cliente;");
                    st.execute("PRAGMA foreign_keys = ON;");
                }
            }
            createTablesIfNotExist();
            seedFranquicias();
            seedSampleData();
        } finally {
            suspendBackupSync = wasSuspended;
        }
        syncSqliteBackup();
    }

    public synchronized QueryResult executeCustomQuery(String sql) throws Exception {
        sql = sql.trim();
        QueryResult qr = new QueryResult();
        try (Statement st = getConnection().createStatement()) {
            if (sql.toLowerCase().startsWith("select") || sql.toLowerCase().startsWith("pragma") || sql.toLowerCase().startsWith("show")) {
                try (ResultSet rs = st.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    for (int i = 1; i <= cols; i++) {
                        qr.columns.add(md.getColumnLabel(i));
                    }
                    while (rs.next()) {
                        Object[] row = new Object[cols];
                        for (int i = 1; i <= cols; i++) {
                            row[i - 1] = rs.getObject(i);
                        }
                        qr.rows.add(row);
                    }
                    qr.message = "Consulta ejecutada exitosamente: " + qr.rows.size() + " fila(s) obtenida(s).";
                }
            } else {
                int affected = st.executeUpdate(sql);
                qr.message = "Sentencia ejecutada exitosamente. Filas afectadas: " + affected;
                syncSqliteBackup();
            }
        }
        return qr;
    }

    public static class QueryResult {
        public List<String> columns = new ArrayList<>();
        public List<Object[]> rows = new ArrayList<>();
        public String message = "";
    }

    public String getCurrentDbPath() {
        if ("mariadb".equalsIgnoreCase(dbType)) {
            return "jdbc:mariadb://" + host + ":" + port + "/" + dbName + " (Usuario: " + user + ")";
        }
        return new File(currentDbPath).getAbsolutePath();
    }

    public String getConnectionStatus() {
        if ("mariadb".equalsIgnoreCase(dbType)) {
            try {
                if (connection != null && !connection.isClosed()) {
                    return "MariaDB Remoto Activo (" + host + ":" + port + " / " + dbName + ") | " + backupStatus;
                }
            } catch (Exception ignored) {}
            return connectionStatus;
        }
        File f = new File(currentDbPath);
        if (f.exists()) {
            long kb = f.length() / 1024;
            if (automaticSqliteFallback) {
                return "MariaDB no disponible; usando respaldo SQLite (" + f.getName() + ")";
            }
            return "SQLite 3 Activo (" + f.getName() + " - " + (kb > 0 ? kb + " KB" : f.length() + " bytes") + ")";
        }
        return connectionStatus;
    }

    public boolean isMariaDb() {
        return "mariadb".equalsIgnoreCase(dbType);
    }

    public String getDbType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDbName() {
        return dbName;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    // =========================================================================
    // AUXILIARES DE FECHAS
    // =========================================================================

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.trim().isEmpty()) return LocalDateTime.now();
        str = str.trim();
        try {
            if (str.contains("T")) {
                if (str.length() > 19) str = str.substring(0, 19);
                return LocalDateTime.parse(str);
            } else if (str.contains(" ")) {
                if (str.length() > 19) str = str.substring(0, 19);
                return LocalDateTime.parse(str, dtfDateTime);
            } else {
                return LocalDate.parse(str).atStartOfDay();
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private LocalDate parseDate(String str) {
        if (str == null || str.trim().isEmpty()) return LocalDate.now();
        str = str.trim();
        try {
            if (str.contains("T")) {
                return LocalDateTime.parse(str).toLocalDate();
            } else if (str.contains(" ")) {
                return LocalDate.parse(str.substring(0, 10));
            } else {
                return LocalDate.parse(str);
            }
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}
