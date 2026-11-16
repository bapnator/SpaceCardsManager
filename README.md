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
| **`cliente`** | `id_cliente`, `nombre`, `apellido`, `correo_electronico`, `telefono`, `fecha_registro` | Correo electrónico único (`UNIQUE`). Teléfono mexicano de 10 dígitos; acepta prefijo `+52` y separadores. |
| **`usuario`** | `id_usuario`, `nombre`, `apellido`, `rol`, `horario`, `activo` | Roles válidos: `'Administrador'`, `'Vendedor'`, `'Organizador_Torneos'`. |
| **`producto`** | `id_producto`, `codigo_barras`, `nombre`, `descripcion`, `categoria`, `juego_tcg`, `precio_unitario`, `stock` | Categorías TCG (`Single Card`, `Booster Box`, `Elite Trainer Box`, `Sleeves`, `Deck`). Precios y stock no negativos. |
| **`venta`** | `id_venta`, `id_cliente` (NULL para anónimo), `id_usuario`, `fecha_venta`, `monto_total`, `metodo_pago`, `monto_saldo_favor` | Métodos: `'Efectivo'`, `'Tarjeta'`, `'Transferencia'`. Registra por separado el saldo a favor aplicado; el usuario responsable se toma de la sesión activa. Llaves foráneas con `cliente` y `usuario`. |
| **`detalle_venta`** | `id_detalle`, `id_venta`, `id_producto`, `cantidad`, `precio_historico`, `subtotal` | Subtotal calculado automáticamente con columna generada `GENERATED ALWAYS AS (cantidad * precio_historico) STORED`. `ON DELETE CASCADE`. |
| **`preventa`** | `id_preventa`, `id_cliente`, `id_usuario`, `id_producto`, `monto_anticipo`, `monto_total`, `fecha_solicitud`, `fecha_estimada_entrega`, `fecha_vencimiento_apartado`, `estado` | Estados: `'Pendiente'`, `'Entregado'`, `'Cancelado'`. Los apartados vencidos se señalan para cancelación manual. Validación `monto_total >= monto_anticipo`. |
| **`torneo`** | `id_torneo`, `id_organizador`, `nombre_torneo`, `juego_tcg`, `fecha_evento`, `costo_inscripcion`, `cupo_maximo`, `porcentaje_premios_monetarios`, `estado` | Estados: `'Programado'`, `'En Curso'`, `'Finalizado'`, `'Cancelado'`. Solo se aceptan inscripciones mientras está Programado; los tickets desaparecen del inventario y punto de venta al iniciar. |
| **`participante_torneo`** | `id_inscripcion`, `id_torneo`, `id_cliente`, `id_jugador_tcg`, `fecha_inscripcion`, `pago_confirmado`, `posicion_final`, `premio_inscripcion_otorgado` | `UNIQUE(id_torneo, id_cliente)` para evitar inscripciones duplicadas. `ON DELETE CASCADE` al torneo. |
| **`bracket_partida`** | Ronda, número, participantes, ganador y estado de cada partida | Persiste el bracket de eliminación directa, incluidos pases automáticos y el partido por el tercer lugar. |
| **`producto_premio`** | Catálogo de productos patrocinados, con patrocinador, descripción y stock | Los productos se pueden asignar como premios a diferentes torneos. |
| **`premio_torneo`** | Puesto, tipo de premio y referencia a inventario o catálogo patrocinado | Permite configurar premios por puesto y porcentaje del fondo para premios monetarios. |
| **`premio_inscripcion_torneo`** | Tipo y detalle del premio opcional por inscripción | Una configuración por torneo; admite producto de tienda, producto patrocinado o saldo a favor. |
| **`premio_inscripcion_otorgado`** | Registro de premios entregados al confirmar una inscripción | Conserva el historial aunque posteriormente se cancele la inscripción. |
| **`saldo_favor`** | Lotes de saldo por cliente, importe disponible, origen y vencimiento | Conserva el origen de cada crédito y permite vencimiento configurable o sin vencimiento. |

---

## 🗺️ Mapeo Arquitectónico BCE (Robustez y SQLite)

| Diagrama BCE (Borde) | Módulo / Vista UI | Entidad BD Asociada | Funcionalidades |
| :--- | :--- | :--- | :--- |
| **`Pantalla Principal`** | [`DashboardPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/DashboardPanel.java) | Vista general | KPIs de ingresos, stock total, preventas activas, torneos programados, y visor de tickets/desglose de ventas. |
| **`Venta / Punto de Venta`** | [`PuntoVentaPanel`](src/com/spacecards/ui/PuntoVentaPanel.java) | `venta`, `detalle_venta`, `preventa`, `producto` | Pestañas de ventas (catálogo, carrito, cobro y descuento automático de stock) y preventas (registro con anticipo, liquidación, entrega y cancelación). |
| **`PantallaCrearRegistroProducto`<br>`PantallaConsultaProducto`** | [`InventarioPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/InventarioPanel.java) | `producto` | Filtros por categoría y juego, alerta de stock crítico, registro, edición y baja de productos. Franquicias y categorías se administran desde Configuración. |
| **Configuración** | [`ConfiguracionPanel`](src/com/spacecards/ui/ConfiguracionPanel.java) | `franquicia`, `categoria`, `configuracion_sistema` | Administra franquicias, categorías, margen y vigencia de preventas, porcentaje de apartado predeterminado y costo/cupo predeterminados para torneos. |
| **`PantallaCrearRegistroCliente`<br>`PantallaConsultarCliente`** | [`ClientesPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/ClientesPanel.java) | `cliente` | Directorio con historial, búsqueda rápida por nombre/teléfono/correo y alta/edición de clientes. |
| **`PantallaCrearRegistroTorneo`<br>`PantallaConsultarTorneo`<br>`PantallaCrearRegistroParticipante`** | [`TorneosPanel`](src/com/spacecards/ui/TorneosPanel.java) | `torneo`, `participante_torneo`, `bracket_partida` | Gestión de torneos, participantes, bracket por rondas, premios por puesto e historial de premios. |
| **`PantallaCrearRegistroUsuario`<br>`PantallaConsultarUsuario`** | [`UsuariosPanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/UsuariosPanel.java) | `usuario` | Gestión del personal (Administrador, Vendedor, Organizador de Torneos), turnos de trabajo y estado activo/inactivo. |
| **`Gestión Base de Datos`** | [`DatabasePanel`](file:///c:/9Sem/is/U2/SpaceCardsManager_U2/src/com/spacecards/ui/DatabasePanel.java) | Persistencia SQLite | Inspección en tiempo real de conteo de filas por tabla, cambio de archivo `.db`, restauración de datos semilla, visor DDL y consola interactiva de consultas SQL. |

## 🔐 Acceso por rol

La barra lateral muestra únicamente las secciones permitidas para el usuario activo:

| Rol | Secciones visibles |
| :--- | :--- |
| **Administrador** | Todas |
| **Vendedor** | Resumen, Punto de Venta, Productos y Clientes |
| **Organizador de Torneos** | Resumen, Configuración, Clientes y Torneos |
| **Usuario Puente** | Personal (solo para registrar al primer administrador y empleados) |

Al registrar un usuario no se solicita una contraseña personal. Se genera una contraseña temporal que debe compartirse con el nuevo usuario; al iniciar sesión, deberá definir su contraseña antes de continuar.

Las ventas, preventas, liquidaciones, inscripciones y torneos se registran con el usuario de la sesión activa; los formularios no permiten asignar la operación a otra cuenta.

## 🏆 Premios de torneos y saldo a favor

En un torneo **En Curso** se puede generar un bracket de eliminación directa con las inscripciones pagadas; el orden inicial se sortea una sola vez y los pases se asignan automáticamente. Al seleccionar al ganador de cada partida, los participantes avanzan a la siguiente ronda. Si se modifica un resultado, se reinician los resultados de las rondas posteriores. El bracket determina automáticamente el primer y segundo lugar, y el tercero mediante una partida de clasificación (o por eliminación directa si solo hay tres participantes). Los puestos premiados posteriores al tercero se asignan manualmente.

Al crear un torneo se puede configurar un premio opcional para cada inscripción confirmada: un producto del inventario, un producto patrocinado o un monto fijo de saldo a favor. El premio se entrega al confirmar el pago, se descuenta del stock correspondiente y queda registrado en el historial. Por separado, se pueden configurar premios para los ganadores por puesto. El fondo para premios se define como porcentaje de la recaudación y cada puesto monetario recibe su porcentaje del remanente del fondo como saldo a favor. Los puestos se calculan en orden y nunca pueden repartir más dinero del que queda; el resto de la recaudación se conserva para la tienda. La pantalla calcula los importes estimados y reales destinados a premios y a la tienda.

El botón **Historial de Premios** permite consultar premios por inscripción y premios registrados para ganadores, incluyendo fecha y nombre del torneo, jugador, puesto, tipo (producto de tienda, producto patrocinado o saldo a favor) y detalle del premio. Es una consulta histórica; no permite modificar ni agregar premios manualmente.

Al cancelar una preventa pendiente o un torneo con inscripciones pagadas, el importe efectivamente pagado se acredita al saldo a favor del cliente. Los premios monetarios de los ganadores también se acreditan a su saldo. La vigencia global se configura en días o sin vencimiento. Para usarlo en caja, al liquidar una preventa o al pagar una inscripción, se activa una casilla y el sistema aplica automáticamente el menor entre el saldo disponible y el importe pendiente. Al finalizar el torneo se registran los ganadores y se descuentan del inventario los premios entregados.

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
│   │   ├── ParticipanteTorneo.java
│   │   ├── PartidaBracket.java
│   │   └── PremioInscripcionTorneo.java
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
│   │   ├── TorneosPanel.java          # Torneos, participantes y bracket
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
