package com.spacecards.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Preventa {
    private int idPreventa;
    private int idCliente;
    private Cliente cliente;
    private int idUsuario;
    private Usuario usuario;
    private int idProducto;
    private Producto producto;
    private BigDecimal montoAnticipo;
    private BigDecimal montoTotal;
    private LocalDateTime fechaSolicitud;
    private LocalDate fechaEstimadaEntrega;
    private String estado; // 'Pendiente', 'Entregado', 'Cancelado'

    public Preventa() {
        this.fechaSolicitud = LocalDateTime.now();
        this.fechaEstimadaEntrega = LocalDate.now().plusWeeks(2);
        this.estado = "Pendiente";
        this.montoAnticipo = BigDecimal.ZERO;
        this.montoTotal = BigDecimal.ZERO;
    }

    public Preventa(int idPreventa, Cliente cliente, Usuario usuario, Producto producto,
                    BigDecimal montoAnticipo, BigDecimal montoTotal, LocalDate fechaEstimadaEntrega, String estado) {
        this.idPreventa = idPreventa;
        this.cliente = cliente;
        this.idCliente = cliente != null ? cliente.getIdCliente() : 0;
        this.usuario = usuario;
        this.idUsuario = usuario != null ? usuario.getIdUsuario() : 0;
        this.producto = producto;
        this.idProducto = producto != null ? producto.getIdProducto() : 0;
        this.montoAnticipo = montoAnticipo;
        this.montoTotal = montoTotal;
        this.fechaSolicitud = LocalDateTime.now();
        this.fechaEstimadaEntrega = fechaEstimadaEntrega;
        this.estado = estado != null ? estado : "Pendiente";
    }

    public int getIdPreventa() { return idPreventa; }
    public void setIdPreventa(int idPreventa) { this.idPreventa = idPreventa; }

    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
        if (cliente != null) this.idCliente = cliente.getIdCliente();
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        if (usuario != null) this.idUsuario = usuario.getIdUsuario();
    }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) {
        this.producto = producto;
        if (producto != null) this.idProducto = producto.getIdProducto();
    }

    public BigDecimal getMontoAnticipo() { return montoAnticipo; }
    public void setMontoAnticipo(BigDecimal montoAnticipo) { this.montoAnticipo = montoAnticipo; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public BigDecimal getSaldoPendiente() {
        if (montoTotal != null && montoAnticipo != null) {
            return montoTotal.subtract(montoAnticipo);
        }
        return BigDecimal.ZERO;
    }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDate getFechaEstimadaEntrega() { return fechaEstimadaEntrega; }
    public void setFechaEstimadaEntrega(LocalDate fechaEstimadaEntrega) { this.fechaEstimadaEntrega = fechaEstimadaEntrega; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
