package com.spacecards.dao;

import com.spacecards.model.*;

import java.io.File;
import java.math.BigDecimal;
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
            {"usuario", "id_usuario", "nombre", "apellido", "rol", "horario", "activo"},
            {"producto", "id_producto", "codigo_barras", "nombre", "descripcion", "categoria", "juego_tcg", "precio_unitario", "porcentaje_apartado", "stock", "id_torneo", "tipo_venta", "fecha_salida"},
            {"categoria_producto", "id_categoria", "nombre", "descripcion", "activo", "fecha_registro"},
            {"franquicia", "id_franquicia", "nombre", "fabricante", "descripcion", "activo", "fecha_registro"},
            {"venta", "id_venta", "id_cliente", "id_usuario", "id_preventa", "fecha_venta", "monto_total", "metodo_pago"},
            {"detalle_venta", "id_detalle", "id_venta", "id_producto", "cantidad", "precio_historico"},
            {"preventa", "id_preventa", "id_cliente", "id_usuario", "id_producto", "monto_anticipo", "monto_total", "fecha_solicitud", "fecha_estimada_entrega", "estado"},
            {"torneo", "id_torneo", "id_organizador", "nombre_torneo", "juego_tcg", "fecha_evento", "costo_inscripcion", "cupo_maximo", "estado"},
            {"participante_torneo", "id_inscripcion", "id_torneo", "id_cliente", "id_jugador_tcg", "fecha_inscripcion", "pago_confirmado", "posicion_final", "premio"}
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
                        "activo BOOLEAN DEFAULT 1) ENGINE=InnoDB;");

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
                        "activo BOOLEAN DEFAULT 1);");

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
            }
        }
        ensureProductSaleTypeColumn(targetConnection);
        ensureProductReleaseDateColumn(targetConnection);
        ensureProductDepositPercentageColumn(targetConnection);
        ensureProductTournamentColumn(targetConnection);
        ensureParticipantPrizeColumn(targetConnection);
        ensurePreventaSaleColumn(targetConnection);
        ensureDefaultProductCategories(targetConnection);
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
        String sql = "SELECT id_usuario, nombre, apellido, rol, horario, activo FROM usuario ORDER BY id_usuario ASC";
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
                list.add(u);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addUsuario(Usuario u) {
        String sql = "INSERT INTO usuario (nombre, apellido, rol, horario, activo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getRol());
            ps.setString(4, u.getHorario());
            ps.setInt(5, u.isActivo() ? 1 : 0);
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
        String sql = "UPDATE usuario SET nombre = ?, apellido = ?, rol = ?, horario = ?, activo = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getRol());
            ps.setString(4, u.getHorario());
            ps.setInt(5, u.isActivo() ? 1 : 0);
            ps.setInt(6, u.getIdUsuario());
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
        String sql = "SELECT id_usuario, nombre, apellido, rol, horario, activo FROM usuario WHERE id_usuario = ?";
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
        String sql = "SELECT v.id_venta, v.id_cliente, v.id_usuario, v.fecha_venta, v.monto_total, v.metodo_pago, " +
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
                v.setMetodoPago(rs.getString("metodo_pago"));

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
                "CASE WHEN v.id_preventa IS NULL THEN v.monto_total - COALESCE((SELECT SUM(dv.subtotal) " +
                "FROM detalle_venta dv JOIN producto pt ON dv.id_producto = pt.id_producto " +
                "WHERE dv.id_venta = v.id_venta AND pt.id_torneo IS NOT NULL), 0) " +
                "ELSE v.monto_total - COALESCE(pr.monto_anticipo, 0) END AS monto, v.metodo_pago AS metodo " +
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
                "t.nombre_torneo AS producto, d.subtotal AS monto, v.metodo_pago AS metodo " +
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
            LocalDateTime fecha = v.getFechaVenta() != null ? v.getFechaVenta() : LocalDateTime.now();

            // 2. Insertar en tabla venta
            String sqlVenta = "INSERT INTO venta (id_cliente, id_usuario, fecha_venta, monto_total, metodo_pago) VALUES (?, ?, ?, ?, ?)";
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

    private void validarTicketTorneo(Connection conn, int idTorneo, int idCliente, int stockDisponible) throws Exception {
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

    private void confirmarTicketTorneo(Connection conn, int idTorneo, int idCliente) throws SQLException {
        if (existeInscripcionPendiente(conn, idTorneo, idCliente)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE participante_torneo SET pago_confirmado = 1 WHERE id_torneo = ? AND id_cliente = ?")) {
                ps.setInt(1, idTorneo);
                ps.setInt(2, idCliente);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO participante_torneo (id_torneo, id_cliente, fecha_inscripcion, pago_confirmado) VALUES (?, ?, ?, 1)")) {
            ps.setInt(1, idTorneo);
            ps.setInt(2, idCliente);
            ps.setString(3, LocalDateTime.now().format(dtfDateTime));
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // CRUD: PREVENTA
    // =========================================================================

    public synchronized List<Preventa> getPreventas() {
        List<Preventa> list = new ArrayList<>();
        String sql = "SELECT pr.id_preventa, pr.id_cliente, pr.id_usuario, pr.id_producto, pr.monto_anticipo, pr.monto_total, " +
                "pr.fecha_solicitud, pr.fecha_estimada_entrega, pr.estado, " +
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

        String sql = "INSERT INTO preventa (id_cliente, id_usuario, id_producto, monto_anticipo, monto_total, fecha_solicitud, fecha_estimada_entrega, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getIdCliente());
            ps.setInt(2, p.getIdUsuario());
            ps.setInt(3, p.getIdProducto());
            ps.setBigDecimal(4, p.getMontoAnticipo());
            ps.setBigDecimal(5, p.getMontoTotal());
            LocalDateTime fSol = p.getFechaSolicitud() != null ? p.getFechaSolicitud() : LocalDateTime.now();
            ps.setString(6, fSol.format(dtfDateTime));
            ps.setString(7, p.getFechaEstimadaEntrega().format(dtfDate));
            ps.setString(8, p.getEstado() != null ? p.getEstado() : "Pendiente");
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
        String sql = "UPDATE preventa SET estado = ? WHERE id_preventa = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idPreventa);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar preventa: " + ex.getMessage(), ex);
        }
        syncSqliteBackup();
    }

    public synchronized void liquidarPreventa(int idPreventa, String metodoPago) throws Exception {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            int idCliente;
            int idUsuario;
            int idProducto;
            BigDecimal montoTotal;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_cliente, id_usuario, id_producto, monto_total, estado FROM preventa WHERE id_preventa = ?")) {
                ps.setInt(1, idPreventa);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("No se encontró la preventa seleccionada.");
                    if (!"Pendiente".equalsIgnoreCase(rs.getString("estado"))) {
                        throw new Exception("La preventa ya no está pendiente y no puede liquidarse.");
                    }
                    idCliente = rs.getInt("id_cliente");
                    idUsuario = rs.getInt("id_usuario");
                    idProducto = rs.getInt("id_producto");
                    montoTotal = rs.getBigDecimal("monto_total");
                }
            }

            String sqlVenta = "INSERT INTO venta (id_cliente, id_usuario, id_preventa, fecha_venta, monto_total, metodo_pago) VALUES (?, ?, ?, ?, ?, ?)";
            int idVenta;
            try (PreparedStatement ps = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idCliente);
                ps.setInt(2, idUsuario);
                ps.setInt(3, idPreventa);
                ps.setString(4, LocalDateTime.now().format(dtfDateTime));
                ps.setBigDecimal(5, montoTotal);
                ps.setString(6, metodoPago);
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
                "t.cupo_maximo, t.estado, " +
                "u.nombre AS org_nom, u.apellido AS org_ape, u.rol AS org_rol, " +
                "(SELECT COUNT(*) FROM participante_torneo pt WHERE pt.id_torneo = t.id_torneo) AS participantes_inscritos " +
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

                list.add(t);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addTorneo(Torneo t) {
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
            conn.commit();
        } catch (SQLException ex) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                ex.addSuppressed(rollbackError);
            }
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

    public synchronized void updateTorneoEstado(int idTorneo, String nuevoEstado) {
        String sql = "UPDATE torneo SET estado = ? WHERE id_torneo = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idTorneo);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar torneo: " + ex.getMessage(), ex);
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
                "pt.pago_confirmado, pt.posicion_final, pt.premio, " +
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

                    list.add(pt);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt) throws Exception {
        inscribirParticipante(pt, "Efectivo");
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt, String metodoPago) throws Exception {
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
            int cupoMaximo;
            int idOrganizador;
            BigDecimal costoInscripcion;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cupo_maximo, id_organizador, costo_inscripcion FROM torneo WHERE id_torneo = ?")) {
                ps.setInt(1, pt.getIdTorneo());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new Exception("Torneo no encontrado.");
                    cupoMaximo = rs.getInt("cupo_maximo");
                    idOrganizador = rs.getInt("id_organizador");
                    costoInscripcion = rs.getBigDecimal("costo_inscripcion");
                }
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
            if (registrarPago) {
                int idVenta;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO venta (id_cliente, id_usuario, fecha_venta, monto_total, metodo_pago) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, pt.getIdCliente());
                    ps.setInt(2, idOrganizador);
                    ps.setString(3, fecha.format(dtfDateTime));
                    ps.setBigDecimal(4, costoInscripcion);
                    ps.setString(5, costoInscripcion.compareTo(BigDecimal.ZERO) == 0 ? "Efectivo" : metodoPago);
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
                    if (keys.next()) pt.setIdInscripcion(keys.getInt(1));
                }
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
        String sql = "DELETE FROM participante_torneo WHERE id_inscripcion = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idInscripcion);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al cancelar inscripción: " + ex.getMessage(), ex);
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
