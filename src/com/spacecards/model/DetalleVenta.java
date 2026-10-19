package com.spacecards.model;

import java.math.BigDecimal;

public class DetalleVenta {
    private int idDetalle;
    private int idVenta;
    private int idProducto;
    private Producto producto; // Referencia en memoria para la UI
    private int cantidad;
    private BigDecimal precioHistorico;
    private BigDecimal subtotal;

    public DetalleVenta() {
        this.cantidad = 1;
        this.precioHistorico = BigDecimal.ZERO;
        this.subtotal = BigDecimal.ZERO;
    }

    public DetalleVenta(int idDetalle, int idVenta, Producto producto, int cantidad, BigDecimal precioHistorico) {
        this.idDetalle = idDetalle;
        this.idVenta = idVenta;
        this.producto = producto;
        this.idProducto = producto != null ? producto.getIdProducto() : 0;
        this.cantidad = cantidad;
        this.precioHistorico = precioHistorico;
        this.subtotal = precioHistorico.multiply(BigDecimal.valueOf(cantidad));
    }

    public int getIdDetalle() { return idDetalle; }
    public void setIdDetalle(int idDetalle) { this.idDetalle = idDetalle; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) {
        this.producto = producto;
        if (producto != null) {
            this.idProducto = producto.getIdProducto();
        }
    }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
        recalculateSubtotal();
    }

    public BigDecimal getPrecioHistorico() { return precioHistorico; }
    public void setPrecioHistorico(BigDecimal precioHistorico) {
        this.precioHistorico = precioHistorico;
        recalculateSubtotal();
    }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public void recalculateSubtotal() {
        if (precioHistorico != null) {
            this.subtotal = precioHistorico.multiply(BigDecimal.valueOf(cantidad));
        }
    }
}
