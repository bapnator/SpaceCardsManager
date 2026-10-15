# ⚡ Space Cards Manager - TCG Store (Java + SQLite 3)
### Proyecto de Ingeniería de Software (Unidad 2) - Instituto Tecnológico de Tepic

Aplicación de escritorio moderna desarrollada en **Java (JDK 24/25)** con persistencia local embebida en **SQLite 3** e interfaz gráfica avanzada basada en **FlatLaf Dark Theme**, mapeada estrictamente a los diagramas de robustez **BCE (Boundary - Control - Entity)** y al esquema relacional de base de datos solicitado.

---

## 🚀 Cómo Ejecutar la Aplicación

1. **Ejecución directa:**
   Haz doble clic sobre el archivo [run.bat](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/run.bat).
   *No requiere instalar ni configurar servidores de bases de datos externos; utiliza SQLite embebido y las librerías incluidas en `lib/`.*

2. **Compilación manual:**
   Si realizas cambios en el código fuente, haz doble clic en [compile.bat](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/compile.bat).

---

## 🗄️ Estructura de la Base de Datos SQLite (`spacecards.db`)

La aplicación crea automáticamente el archivo `spacecards.db` en la primera ejecución con la estructura relacional completa y activa la integridad referencial (`PRAGMA foreign_keys = ON;`):

| Tabla SQLite | Descripción y Atributos Principales | Reglas de Negocio / Restricciones |
| :--- | :--- | :--- |
| **`cliente`** | `id_cliente`, `nombre`, `apellido`, `correo_electronico`, `telefono`, `fecha_registro` | Correo electrónico único (`UNIQUE`). Autoincremental. |
| **`usuario`** | `id_usuario`, `nombre`, `apellido`, `rol`, `horario`, `activo` | Roles válidos: `'Administrador'`, `'Vendedor'`, `'Organizador_Torneos'`. |
| **`producto`** | `id_producto`, `codigo_barras`, `nombre`, `descripcion`, `categoria`, `juego_tcg`, `precio_unitario`, `stock` | Categorías TCG (`Single Card`, `Booster Box`, `Elite Trainer Box`, `Sleeves`, `Deck`). Precios y stock no negativos. |
| **`venta`** | `id_venta`, `id_cliente` (NULL para anónimo), `id_usuario`, `fecha_venta`, `monto_total`, `metodo_pago` | Métodos: `'Efectivo'`, `'Tarjeta'`, `'Transferencia'`. Llaves foráneas con `cliente` y `usuario`. |
| **`detalle_venta`** | `id_detalle`, `id_venta`, `id_producto`, `cantidad`, `precio_historico`, `subtotal` | Subtotal calculado automáticamente con columna generada `GENERATED ALWAYS AS (cantidad * precio_historico) STORED`. `ON DELETE CASCADE`. |
| **`preventa`** | `id_preventa`, `id_cliente`, `id_usuario`, `id_producto`, `monto_anticipo`, `monto_total`, `fecha_solicitud`, `fecha_estimada_entrega`, `estado` | Estados: `'Pendiente'`, `'Entregado'`, `'Cancelado'`. Validación `monto_total >= monto_anticipo`. |
| **`torneo`** | `id_torneo`, `id_organizador`, `nombre_torneo`, `juego_tcg`, `fecha_evento`, `costo_inscripcion`, `cupo_maximo`, `estado` | Estados: `'Programado'`, `'En Curso'`, `'Finalizado'`, `'Cancelado'`. Control de cupos máximos en tiempo real. |
| **`participante_torneo`** | `id_inscripcion`, `id_torneo`, `id_cliente`, `id_jugador_tcg`, `fecha_inscripcion`, `pago_confirmado`, `posicion_final` | `UNIQUE(id_torneo, id_cliente)` para evitar inscripciones duplicadas. `ON DELETE CASCADE` al torneo. |

---

## 🗺️ Mapeo Arquitectónico BCE (Robustez y SQLite)

| Diagrama BCE (Borde) | Módulo / Vista UI | Entidad BD Asociada | Funcionalidades |
| :--- | :--- | :--- | :--- |
| **`Pantalla Principal`** | [`DashboardPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/DashboardPanel.java) | Vista general | KPIs de ingresos, stock total, preventas activas, torneos programados, y visor de tickets/desglose de ventas. |
| **`Venta / Punto de Venta`** | [`VentasPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/VentasPanel.java) | `venta`, `detalle_venta`, `producto` | Catálogo interactivo, carrito de compras, cálculo de subtotales, venta a cliente registrado o público general anónimo, transacción SQLite atómica y descuento automático de stock. |
| **`PantallaCrearRegistroProducto`<br>`PantallaConsultaProducto`** | [`InventarioPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/InventarioPanel.java) | `producto` | Filtros por categoría y juego (Pokémon, Magic, Yu-Gi-Oh!, Lorcana), alerta de stock crítico, registro, edición y baja de productos. |
| **`PantallaCrearRegistroCliente`<br>`PantallaConsultarCliente`** | [`ClientesPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/ClientesPanel.java) | `cliente` | Directorio con historial, búsqueda rápida por nombre/teléfono/correo y alta/edición de clientes. |
| **`PantallaCrearRegistroPreventa`<br>`PantallaConsultarPreventa`** | [`PreventasPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/PreventasPanel.java) | `preventa`, `cliente`, `producto` | Registro de preventas con anticipo, cálculo de saldo pendiente y liquidación/entrega al recibir la mercancía. |
| **`PantallaCrearRegistroTorneo`<br>`PantallaConsultarTorneo`<br>`PantallaCrearRegistroParticipante`** | [`TorneosPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/TorneosPanel.java) | `torneo`, `participante_torneo` | Gestión de torneos oficiales, control de cupos, inscripción de jugadores con Player ID / POP ID, confirmación de cuota y cancelación de registros. |
| **`PantallaCrearRegistroUsuario`<br>`PantallaConsultarUsuario`** | [`UsuariosPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/UsuariosPanel.java) | `usuario` | Gestión del personal (Administrador, Vendedor, Organizador de Torneos), turnos de trabajo y estado activo/inactivo. |
| **`Gestión Base de Datos`** | [`DatabasePanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/DatabasePanel.java) | Persistencia SQLite | Inspección en tiempo real de conteo de filas por tabla, cambio de archivo `.db`, restauración de datos semilla, visor DDL y consola interactiva de consultas SQL. |

---

## 📁 Estructura del Proyecto

```
SpaceCardsManager_U2/
├── lib/
│   ├── flatlaf-3.5.4.jar              # Look & Feel moderno estilo Dark Theme
│   └── sqlite-jdbc-3.49.1.0.jar       # Driver JDBC oficial de SQLite 3
├── src/com/spacecards/
│   ├── model/                         # Entidades de datos (POJOs)
│   │   ├── Cliente.java
│   │   ├── Usuario.java
│   │   ├── Producto.java
│   │   ├── Venta.java
│   │   ├── DetalleVenta.java
│   │   ├── Preventa.java
│   │   ├── Torneo.java
│   │   └── ParticipanteTorneo.java
│   ├── dao/                           # Controladores y persistencia
│   │   └── DatabaseService.java       # Manejador SQLite JDBC con transacciones
│   ├── ui/                            # Clases de Borde (Boundary / Interfaz)
│   │   ├── UITheme.java               # Colores, estilos y componentes base
│   │   ├── SidebarPanel.java          # Barra de navegación lateral
│   │   ├── DashboardPanel.java        # Pantalla Principal con KPIs y tickets
│   │   ├── VentasPanel.java           # Punto de Venta (POS) transaccional
│   │   ├── InventarioPanel.java       # Catálogo TCG e Inventario
│   │   ├── ClientesPanel.java         # Directorio de Clientes
│   │   ├── PreventasPanel.java        # Control de Preventas y Anticipos
│   │   ├── TorneosPanel.java          # Torneos y Participantes
│   │   ├── UsuariosPanel.java         # Personal, Roles y Horarios
│   │   ├── DatabasePanel.java         # Panel de gestión y consola SQLite
│   │   └── MainWindow.java            # Ventana Principal (JFrame)
│   └── Main.java                      # Punto de entrada
├── bin/                               # Clases compiladas (.class)
├── spacecards.db                      # Archivo de base de datos SQLite persistente
├── schema.sql                         # Script SQL DDL de la Base de Datos SQLite
├── run.bat                            # Lanzador de la aplicación en Windows
└── compile.bat                        # Script de compilación automática
```
