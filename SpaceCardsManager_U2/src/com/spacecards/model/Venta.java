package com.spacecards.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Venta {
    private int idVenta;
    private Integer idCliente; // Nullable
    private Cliente cliente;
    private int idUsuario;
    private Usuario usuario;
    private LocalDateTime fechaVenta;
    private BigDecimal montoTotal;
    private String metodoPago; // 'Efectivo', 'Tarjeta', 'Transferencia'
    private List<DetalleVenta> detalles;

    public Venta() {
        this.fechaVenta = LocalDateTime.now();
        this.montoTotal = BigDecimal.ZERO;
        this.metodoPago = "Efectivo";
        this.detalles = new ArrayList<>();
    }

    public Venta(int idVenta, Cliente cliente, Usuario usuario, String metodoPago) {
        this.idVenta = idVenta;
        this.cliente = cliente;
        this.idCliente = cliente != null ? cliente.getIdCliente() : null;
        this.usuario = usuario;
        this.idUsuario = usuario != null ? usuario.getIdUsuario() : 0;
        this.fechaVenta = LocalDateTime.now();
        this.metodoPago = metodoPago;
        this.montoTotal = BigDecimal.ZERO;
        this.detalles = new ArrayList<>();
    }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
        this.idCliente = cliente != null ? cliente.getIdCliente() : null;
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        this.idUsuario = usuario != null ? usuario.getIdUsuario() : 0;
    }

    public LocalDateTime getFechaVenta() { return fechaVenta; }
    public void setFechaVenta(LocalDateTime fechaVenta) { this.fechaVenta = fechaVenta; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public List<DetalleVenta> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleVenta> detalles) {
        this.detalles = detalles;
        recalculateTotal();
    }

    public void addDetalle(DetalleVenta detalle) {
        this.detalles.add(detalle);
        recalculateTotal();
    }

    public void recalculateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (DetalleVenta d : detalles) {
            sum = sum.add(d.getSubtotal());
        }
        this.montoTotal = sum;
    }
}
