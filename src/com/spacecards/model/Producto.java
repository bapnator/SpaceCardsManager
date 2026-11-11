package com.spacecards.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Producto {
    public static final String TIPO_PREVENTA = "PREVENTA";
    public static final String TIPO_VENTA_NORMAL = "VENTA_NORMAL";

    private int idProducto;
    private String codigoBarras;
    private String nombre;
    private String descripcion;
    private String categoria; // 'Booster Box', 'Sleeves', 'Single Card', 'Deck', 'Elite Trainer Box', 'Tin'
    private String juegoTcg;  // 'Pokémon', 'Magic', 'Yu-Gi-Oh!', 'Lorcana'
    private BigDecimal precioUnitario;
    private BigDecimal porcentajeApartado = new BigDecimal("30.00");
    private int stock;
    private Integer idTorneo;
    private String tipoVenta = TIPO_VENTA_NORMAL;
    private LocalDate fechaSalida;

    public Producto() {
        this.juegoTcg = "Pokémon";
        this.precioUnitario = BigDecimal.ZERO;
        this.stock = 0;
    }

    public Producto(int idProducto, String codigoBarras, String nombre, String descripcion,
                    String categoria, String juegoTcg, BigDecimal precioUnitario, int stock) {
        this.idProducto = idProducto;
        this.codigoBarras = codigoBarras;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.juegoTcg = juegoTcg != null ? juegoTcg : "Pokémon";
        this.precioUnitario = precioUnitario;
        this.stock = stock;
    }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getJuegoTcg() { return juegoTcg; }
    public void setJuegoTcg(String juegoTcg) { this.juegoTcg = juegoTcg; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getPorcentajeApartado() { return porcentajeApartado; }
    public void setPorcentajeApartado(BigDecimal porcentajeApartado) {
        this.porcentajeApartado = porcentajeApartado != null ? porcentajeApartado : new BigDecimal("30.00");
    }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public Integer getIdTorneo() { return idTorneo; }
    public void setIdTorneo(Integer idTorneo) { this.idTorneo = idTorneo; }
    public boolean isTicketTorneo() { return idTorneo != null && idTorneo > 0; }

    public LocalDate getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(LocalDate fechaSalida) { this.fechaSalida = fechaSalida; }

    public String getTipoVenta() { return tipoVenta; }
    public void setTipoVenta(String tipoVenta) {
        this.tipoVenta = TIPO_PREVENTA.equalsIgnoreCase(tipoVenta) ? TIPO_PREVENTA : TIPO_VENTA_NORMAL;
    }
    public boolean isPreventa() { return TIPO_PREVENTA.equals(tipoVenta); }

    public static String getCategoriaVisible(String categoria) {
        if (categoria == null) return "";
        switch (categoria) {
            case "Single Card": return "Carta individual";
            case "Booster Box": return "Caja de sobres";
            case "Elite Trainer Box": return "Caja de entrenador elite";
            case "Sleeves": return "Fundas protectoras";
            case "Deck": return "Caja para mazo";
            case "Tin": return "Lata";
            default: return categoria;
        }
    }

    public static String getCategoriaAlmacenada(String categoriaVisible) {
        if (categoriaVisible == null) return null;
        switch (categoriaVisible) {
            case "Carta individual": return "Single Card";
            case "Caja de sobres": return "Booster Box";
            case "Caja de entrenador elite": return "Elite Trainer Box";
            case "Fundas protectoras": return "Sleeves";
            case "Caja para mazo": return "Deck";
            case "Lata": return "Tin";
            default: return categoriaVisible;
        }
    }

    @Override
    public String toString() {
        return nombre + " ($" + precioUnitario + ") - " + (isPreventa() ? "Preventa" : "Stock: " + stock);
    }
}
