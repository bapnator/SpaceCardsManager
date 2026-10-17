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
    private static DatabaseService instance;

    private static final String DEFAULT_DB_FILE = "spacecards.db";
    private String currentDbPath = DEFAULT_DB_FILE;
    private Connection connection;
    private String connectionStatus = "Inicializando Base de Datos...";

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
            return initMariaDbConnection(host, port, dbName, user, password);
        } else {
            return initConnection(sqlitePath);
        }
    }

    /**
     * Inicializa conexión remota con MariaDB (Raspberry Pi).
     */
    public synchronized boolean initMariaDbConnection(String host, int port, String dbName, String user, String password) {
        this.dbType = "mariadb";
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.user = user;
        this.password = password;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            try {
                Class.forName("org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName +
                    "?permitMysqlScheme=true&autoReconnect=true&connectTimeout=6000&socketTimeout=15000";
            this.connection = DriverManager.getConnection(url, user, password);

            createTablesIfNotExist();

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
        this.dbType = "sqlite";
        this.currentDbPath = dbPath;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }

            createTablesIfNotExist();

            File f = new File(dbPath);
            connectionStatus = "SQLite 3 Activo (" + f.getName() + " - " + (f.length() / 1024) + " KB)";
            return true;
        } catch (Exception ex) {
            connectionStatus = "Error de conexión SQLite: " + ex.getMessage();
            ex.printStackTrace();
            return false;
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if ("mariadb".equalsIgnoreCase(dbType)) {
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName +
                        "?permitMysqlScheme=true&autoReconnect=true&connectTimeout=6000&socketTimeout=15000";
                connection = DriverManager.getConnection(url, user, password);
            } else {
                connection = DriverManager.getConnection("jdbc:sqlite:" + currentDbPath);
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON;");
                }
            }
        }
        return connection;
    }

    private void createTablesIfNotExist() throws SQLException {
        try (Statement st = getConnection().createStatement()) {
            if ("mariadb".equalsIgnoreCase(dbType)) {
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
                        "stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0)) ENGINE=InnoDB;");

                // TABLA: VENTA (MariaDB)
                st.execute("CREATE TABLE IF NOT EXISTS venta (" +
                        "id_venta INT AUTO_INCREMENT PRIMARY KEY," +
                        "id_cliente INT NULL," +
                        "id_usuario INT NOT NULL," +
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
                        "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente)) ENGINE=InnoDB;");
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
                        "stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0));");

                // TABLA: VENTA (SQLite)
                st.execute("CREATE TABLE IF NOT EXISTS venta (" +
                        "id_venta INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "id_cliente INT NULL," +
                        "id_usuario INT NOT NULL," +
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
                        "FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE," +
                        "FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)," +
                        "CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente));");
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
                    "Carta individual ultra rara de Scarlet & Violet 151", "Single Card", "Pokémon", new BigDecimal("145.00"), 4));
            addProducto(new Producto(0, "7501002", "Scarlet & Violet: 151 Booster Bundle",
                    "Caja sellada con 6 paquetes de mejora de 151", "Booster Box", "Pokémon", new BigDecimal("38.50"), 18));
            addProducto(new Producto(0, "7501003", "Prismatic Evolutions Elite Trainer Box",
                    "ETB especial con micas de Eevee, dados y 9 sobres", "Elite Trainer Box", "Pokémon", new BigDecimal("59.99"), 12));
            addProducto(new Producto(0, "7501004", "Dragon Shield Sleeves Matte (Emerald)",
                    "Paquete de 100 micas tamaño estándar textura mate", "Sleeves", "Pokémon", new BigDecimal("12.50"), 35));
            addProducto(new Producto(0, "7501005", "Pikachu Ultra Pro Deck Box Alcove",
                    "Caja porta baraja de cuero sintético con cierre magnético", "Deck", "Pokémon", new BigDecimal("24.00"), 15));
            addProducto(new Producto(0, "7501006", "Mewtwo VSTAR Universe Secret Rare",
                    "Carta japonesa en condición Mint graduable", "Single Card", "Pokémon", new BigDecimal("85.00"), 3));
            addProducto(new Producto(0, "7501007", "Surging Sparks Booster Box (36 Sobres)",
                    "Caja completa de sobres sellada de fábrica con Pikachu ex tera", "Booster Box", "Pokémon", new BigDecimal("149.99"), 8));

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
    }

    public synchronized void deleteCliente(int idCliente) {
        String sql = "DELETE FROM cliente WHERE id_cliente = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar cliente: " + ex.getMessage(), ex);
        }
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
    }

    public synchronized void deleteUsuario(int idUsuario) {
        String sql = "DELETE FROM usuario WHERE id_usuario = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar usuario: " + ex.getMessage(), ex);
        }
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
        String sql = "SELECT id_producto, codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, stock FROM producto ORDER BY id_producto ASC";
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
                p.setStock(rs.getInt("stock"));
                list.add(p);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void addProducto(Producto p) {
        String sql = "INSERT INTO producto (codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, stock) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getCodigoBarras());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getDescripcion());
            ps.setString(4, p.getCategoria());
            ps.setString(5, p.getJuegoTcg() != null ? p.getJuegoTcg() : "Pokémon");
            ps.setBigDecimal(6, p.getPrecioUnitario());
            ps.setInt(7, p.getStock());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setIdProducto(keys.getInt(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar producto: " + ex.getMessage(), ex);
        }
    }

    public synchronized void updateProducto(Producto p) {
        String sql = "UPDATE producto SET codigo_barras = ?, nombre = ?, descripcion = ?, categoria = ?, juego_tcg = ?, precio_unitario = ?, stock = ? WHERE id_producto = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, p.getCodigoBarras());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getDescripcion());
            ps.setString(4, p.getCategoria());
            ps.setString(5, p.getJuegoTcg());
            ps.setBigDecimal(6, p.getPrecioUnitario());
            ps.setInt(7, p.getStock());
            ps.setInt(8, p.getIdProducto());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar producto: " + ex.getMessage(), ex);
        }
    }

    public synchronized void deleteProducto(int idProducto) {
        String sql = "DELETE FROM producto WHERE id_producto = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al eliminar producto: " + ex.getMessage(), ex);
        }
    }

    public synchronized Producto findProductoById(int idProducto) {
        String sql = "SELECT id_producto, codigo_barras, nombre, descripcion, categoria, juego_tcg, precio_unitario, stock FROM producto WHERE id_producto = ?";
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
                    p.setStock(rs.getInt("stock"));
                    return p;
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
            String sqlCheckStock = "SELECT stock, nombre FROM producto WHERE id_producto = ?";
            for (DetalleVenta d : v.getDetalles()) {
                try (PreparedStatement psStock = conn.prepareStatement(sqlCheckStock)) {
                    psStock.setInt(1, d.getIdProducto());
                    try (ResultSet rs = psStock.executeQuery()) {
                        if (!rs.next()) {
                            throw new Exception("Producto con ID " + d.getIdProducto() + " no encontrado.");
                        }
                        int stockActual = rs.getInt("stock");
                        String nom = rs.getString("nombre");
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

                    // Descontar inventario
                    psUpdateStock.setInt(1, d.getCantidad());
                    psUpdateStock.setInt(2, d.getIdProducto());
                    psUpdateStock.executeUpdate();
                }
            }

            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
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
        String sql = "INSERT INTO torneo (id_organizador, nombre_torneo, juego_tcg, fecha_evento, costo_inscripcion, cupo_maximo, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getIdOrganizador());
            ps.setString(2, t.getNombreTorneo());
            ps.setString(3, t.getJuegoTcg());
            ps.setString(4, t.getFechaEvento().format(dtfDateTime));
            ps.setBigDecimal(5, t.getCostoInscripcion());
            ps.setInt(6, t.getCupoMaximo());
            ps.setString(7, t.getEstado() != null ? t.getEstado() : "Programado");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setIdTorneo(keys.getInt(1));
                    t.setParticipantesInscritos(0);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al agregar torneo: " + ex.getMessage(), ex);
        }
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
                "pt.pago_confirmado, pt.posicion_final, " +
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

                    list.add(pt);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public synchronized void inscribirParticipante(ParticipanteTorneo pt) throws Exception {
        // 1. Validar cupo
        Torneo t = findTorneoById(pt.getIdTorneo());
        if (t == null) throw new Exception("Torneo no encontrado.");
        if (t.getCuposDisponibles() <= 0) {
            throw new Exception("El torneo ya no cuenta con cupos disponibles (Cupo máximo: " + t.getCupoMaximo() + ").");
        }

        // 2. Validar duplicidad
        String sqlCheck = "SELECT COUNT(*) FROM participante_torneo WHERE id_torneo = ? AND id_cliente = ?";
        try (PreparedStatement psCheck = getConnection().prepareStatement(sqlCheck)) {
            psCheck.setInt(1, pt.getIdTorneo());
            psCheck.setInt(2, pt.getIdCliente());
            try (ResultSet rs = psCheck.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new Exception("El cliente ya está inscrito en este torneo.");
                }
            }
        }

        // 3. Insertar
        String sql = "INSERT INTO participante_torneo (id_torneo, id_cliente, id_jugador_tcg, fecha_inscripcion, pago_confirmado, posicion_final) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pt.getIdTorneo());
            ps.setInt(2, pt.getIdCliente());
            ps.setString(3, pt.getIdJugadorTcg());
            LocalDateTime fIns = pt.getFechaInscripcion() != null ? pt.getFechaInscripcion() : LocalDateTime.now();
            ps.setString(4, fIns.format(dtfDateTime));
            ps.setInt(5, pt.isPagoConfirmado() ? 1 : 0);
            if (pt.getPosicionFinal() != null) {
                ps.setInt(6, pt.getPosicionFinal());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    pt.setIdInscripcion(keys.getInt(1));
                    pt.setFechaInscripcion(fIns);
                }
            }
        } catch (SQLException ex) {
            throw new Exception("Error al inscribir participante: " + ex.getMessage(), ex);
        }
    }

    public synchronized void eliminarParticipante(int idInscripcion) {
        String sql = "DELETE FROM participante_torneo WHERE id_inscripcion = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, idInscripcion);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error al cancelar inscripción: " + ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // UTILIDADES SQLITE, CONSOLA Y MANTENIMIENTO
    // =========================================================================

    public Map<String, Integer> getTableCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] tables = {"cliente", "usuario", "producto", "venta", "detalle_venta", "preventa", "torneo", "participante_torneo"};
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
        try (Statement st = getConnection().createStatement()) {
            if ("mariadb".equalsIgnoreCase(dbType)) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0;");
                st.execute("DROP TABLE IF EXISTS participante_torneo;");
                st.execute("DROP TABLE IF EXISTS torneo;");
                st.execute("DROP TABLE IF EXISTS preventa;");
                st.execute("DROP TABLE IF EXISTS detalle_venta;");
                st.execute("DROP TABLE IF EXISTS venta;");
                st.execute("DROP TABLE IF EXISTS producto;");
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
                st.execute("DROP TABLE IF EXISTS usuario;");
                st.execute("DROP TABLE IF EXISTS cliente;");
                st.execute("PRAGMA foreign_keys = ON;");
            }
        }
        createTablesIfNotExist();
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
                    return "MariaDB Remoto Activo (" + host + ":" + port + " / " + dbName + ")";
                }
            } catch (Exception ignored) {}
            return connectionStatus;
        }
        File f = new File(currentDbPath);
        if (f.exists()) {
            long kb = f.length() / 1024;
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
