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
    activo BOOLEAN DEFAULT 1
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
    stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    tipo_venta VARCHAR(20) NOT NULL DEFAULT 'VENTA_NORMAL'
) ENGINE=InnoDB;

-- ========================================================
-- TABLA: VENTA
-- ========================================================
CREATE TABLE IF NOT EXISTS venta (
    id_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NULL,
    id_usuario INT NOT NULL,
    fecha_venta DATETIME DEFAULT CURRENT_TIMESTAMP,
    monto_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
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
    FOREIGN KEY (id_torneo) REFERENCES torneo(id_torneo) ON DELETE CASCADE,
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT unique_jugador_torneo UNIQUE (id_torneo, id_cliente)
) ENGINE=InnoDB;
