package com.spacecards.model;

import java.math.BigDecimal;

public class Producto {
    private int idProducto;
    private String codigoBarras;
    private String nombre;
    private String descripcion;
    private String categoria; // 'Booster Box', 'Sleeves', 'Single Card', 'Deck', 'Elite Trainer Box', 'Tin'
    private String juegoTcg;  // 'Pokémon', 'Magic', 'Yu-Gi-Oh!', 'Lorcana'
    private BigDecimal precioUnitario;
    private int stock;

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

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    @Override
    public String toString() {
        return nombre + " ($" + precioUnitario + ") - Stock: " + stock;
    }
}
