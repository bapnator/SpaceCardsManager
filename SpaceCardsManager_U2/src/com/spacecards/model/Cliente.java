package com.spacecards.model;

import java.time.LocalDateTime;

public class Cliente {
    private int idCliente;
    private String nombre;
    private String apellido;
    private String correoElectronico;
    private String telefono;
    private LocalDateTime fechaRegistro;

    public Cliente() {
        this.fechaRegistro = LocalDateTime.now();
    }

    public Cliente(int idCliente, String nombre, String apellido, String correoElectronico, String telefono) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correoElectronico = correoElectronico;
        this.telefono = telefono;
        this.fechaRegistro = LocalDateTime.now();
    }

    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getNombreCompleto() { return nombre + " " + apellido; }

    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    @Override
    public String toString() {
        return getNombreCompleto() + " (" + correoElectronico + ")";
    }
}
