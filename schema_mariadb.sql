-- ========================================================
-- PROYECTO: SPACE CARDS MANAGER (TCG STORE)
-- MOTOR DE BASE DE DATOS: MariaDB / MySQL
-- ========================================================

CREATE DATABASE IF NOT EXISTS spacecards CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE spacecards;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS participante_torneo;
DROP TABLE IF EXISTS torneo;
DROP TABLE IF EXISTS preventa;
DROP TABLE IF EXISTS detalle_venta;
DROP TABLE IF EXISTS venta;
DROP TABLE IF EXISTS categoria_producto;
DROP TABLE IF EXISTS producto;
DROP TABLE IF EXISTS usuario;
DROP TABLE IF EXISTS cliente;
SET FOREIGN_KEY_CHECKS = 1;

-- ========================================================
-- TABLA: CLIENTE
-- ========================================================
CREATE TABLE IF NOT EXISTS cliente (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    apellido VARCHAR(50) NOT NULL,
    correo_electronico VARCHAR(100) NOT NULL UNIQUE,
    telefono VARCHAR(20) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: USUARIO (Empleado / Staff)
-- ========================================================
CREATE TABLE IF NOT EXISTS usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    apellido VARCHAR(50) NOT NULL,
    rol VARCHAR(30) NOT NULL CHECK (rol IN ('Administrador', 'Vendedor', 'Organizador_Torneos')),
    horario VARCHAR(50) NOT NULL,
    activo BOOLEAN DEFAULT 1,
    password_hash VARCHAR(255) NOT NULL DEFAULT '',
    must_change_password BOOLEAN NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: FRANQUICIA (Juegos TCG Jugables)
-- ========================================================
CREATE TABLE IF NOT EXISTS franquicia (
    id_franquicia INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    fabricante VARCHAR(50),
    descripcion TEXT,
    activo BOOLEAN DEFAULT 1,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: PRODUCTO
-- ========================================================
CREATE TABLE IF NOT EXISTS producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    codigo_barras VARCHAR(50) UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    categoria VARCHAR(50) NOT NULL,
    juego_tcg VARCHAR(50) DEFAULT 'Pokémon',
    precio_unitario DECIMAL(10,2) NOT NULL CHECK (precio_unitario >= 0),
    porcentaje_apartado DECIMAL(5,2) NOT NULL DEFAULT 30.00 CHECK (porcentaje_apartado > 0 AND porcentaje_apartado <= 100),
    stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    id_torneo INT NULL,
    tipo_venta VARCHAR(20) NOT NULL DEFAULT 'VENTA_NORMAL',
    fecha_salida DATE NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS categoria_producto (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    descripcion TEXT,
    activo BOOLEAN NOT NULL DEFAULT 1,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: VENTA
-- ========================================================
CREATE TABLE IF NOT EXISTS venta (
    id_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NULL,
    id_usuario INT NOT NULL,
    id_preventa INT NULL,
    fecha_venta DATETIME DEFAULT CURRENT_TIMESTAMP,
    monto_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    monto_saldo_favor DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    metodo_pago VARCHAR(30) NOT NULL CHECK (metodo_pago IN ('Efectivo', 'Tarjeta', 'Transferencia')),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente) ON DELETE SET NULL,
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: DETALLE_VENTA
-- ========================================================
CREATE TABLE IF NOT EXISTS detalle_venta (
    id_detalle INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    precio_historico DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio_historico) STORED,
    FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: PREVENTA
-- ========================================================
CREATE TABLE IF NOT EXISTS preventa (
    id_preventa INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    id_usuario INT NOT NULL,
    id_producto INT NOT NULL,
    monto_anticipo DECIMAL(10,2) NOT NULL CHECK (monto_anticipo >= 0),
    monto_total DECIMAL(10,2) NOT NULL CHECK (monto_total >= monto_anticipo),
    fecha_solicitud DATETIME DEFAULT CURRENT_TIMESTAMP,
    fecha_estimada_entrega DATE NOT NULL,
    fecha_vencimiento_apartado DATE NULL,
    estado VARCHAR(20) DEFAULT 'Pendiente' CHECK (estado IN ('Pendiente', 'Entregado', 'Cancelado')),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: TORNEO
-- ========================================================
CREATE TABLE IF NOT EXISTS torneo (
    id_torneo INT AUTO_INCREMENT PRIMARY KEY,
    id_organizador INT NOT NULL,
    nombre_torneo VARCHAR(100) NOT NULL,
    juego_tcg VARCHAR(50) NOT NULL,
    fecha_evento DATETIME NOT NULL,
    costo_inscripcion DECIMAL(10,2) NOT NULL CHECK (costo_inscripcion >= 0),
    cupo_maximo INT NOT NULL CHECK (cupo_maximo > 0),
    porcentaje_premios_monetarios DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_premios_monetarios >= 0 AND porcentaje_premios_monetarios <= 100),
    estado VARCHAR(20) DEFAULT 'Programado' CHECK (estado IN ('Programado', 'En Curso', 'Finalizado', 'Cancelado')),
    FOREIGN KEY (id_organizador) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: PARTICIPANTE_TORNEO
-- ========================================================
CREATE TABLE IF NOT EXISTS participante_torneo (
    id_inscripcion INT AUTO_INCREMENT PRIMARY KEY,
    id_torneo INT NOT NULL,
    id_cliente INT NOT NULL,
    id_jugador_tcg VARCHAR(50),
    fecha_inscripcion DATETIME DEFAULT CURRENT_TIMESTAMP,
    pago_confirmado BOOLEAN DEFAULT 0,
    posicion_final INT NULL,
    premio VARCHAR(255) NULL,
    premio_inscripcion_otorgado VARCHAR(255) NULL,
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE,
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bracket_partida (
    id_torneo INT NOT NULL,
    ronda INT NOT NULL,
    numero_partida INT NOT NULL,
    tipo_partida VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (tipo_partida IN ('NORMAL', 'TERCER_LUGAR')),
    id_participante_a INT NULL,
    id_participante_b INT NULL,
    id_ganador INT NULL,
    completada BOOLEAN NOT NULL DEFAULT 0,
    PRIMARY KEY (id_torneo, ronda, tipo_partida, numero_partida),
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE,
    FOREIGN KEY (id_participante_a) REFERENCES participante_torneo(id_inscripcion),
    FOREIGN KEY (id_participante_b) REFERENCES participante_torneo(id_inscripcion),
    FOREIGN KEY (id_ganador) REFERENCES participante_torneo(id_inscripcion)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS saldo_favor (
    id_saldo INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    monto_original DECIMAL(10,2) NOT NULL CHECK (monto_original > 0),
    saldo_disponible DECIMAL(10,2) NOT NULL CHECK (saldo_disponible >= 0),
    fecha_emision DATETIME NOT NULL,
    fecha_vencimiento DATE NULL,
    tipo_origen VARCHAR(40) NOT NULL,
    id_origen INT NOT NULL,
    CONSTRAINT unique_saldo_origen UNIQUE (tipo_origen, id_origen),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS producto_premio (
    id_producto_premio INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    patrocinador VARCHAR(100) NULL,
    descripcion VARCHAR(255) NULL,
    stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS premio_torneo (
    id_torneo INT NOT NULL,
    posicion INT NOT NULL CHECK (posicion > 0),
    tipo_premio VARCHAR(20) NOT NULL CHECK (tipo_premio IN ('MONETARIO', 'INVENTARIO', 'PATROCINADO')),
    id_producto INT NULL,
    id_producto_premio INT NULL,
    porcentaje_fondo DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_fondo >= 0 AND porcentaje_fondo <= 100),
    PRIMARY KEY (id_torneo, posicion),
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
    FOREIGN KEY (id_producto_premio) REFERENCES producto_premio(id_producto_premio)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS premio_inscripcion_torneo (
    id_torneo INT PRIMARY KEY,
    tipo_premio VARCHAR(20) NOT NULL CHECK (tipo_premio IN ('INVENTARIO', 'PATROCINADO', 'SALDO_FAVOR')),
    id_producto INT NULL,
    id_producto_premio INT NULL,
    monto DECIMAL(10,2) NOT NULL DEFAULT 0.00 CHECK (monto >= 0),
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
    FOREIGN KEY (id_producto_premio) REFERENCES producto_premio(id_producto_premio)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS premio_inscripcion_otorgado (
    id_premio INT AUTO_INCREMENT PRIMARY KEY,
    id_torneo INT NOT NULL,
    id_cliente INT NOT NULL,
    id_inscripcion INT NOT NULL UNIQUE,
    id_jugador_tcg VARCHAR(50) NULL,
    fecha_otorgamiento DATETIME NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS configuracion_sistema (
    clave VARCHAR(100) PRIMARY KEY,
    valor VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
