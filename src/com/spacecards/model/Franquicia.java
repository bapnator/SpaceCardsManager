package com.spacecards.model;

import java.time.LocalDateTime;

/**
 * Modelo de datos para las Franquicias / Juegos TCG jugables.
 */
public class Franquicia {
    private int idFranquicia;
    private String nombre;
    private String fabricante;
    private String descripcion;
    private boolean activo;
    private LocalDateTime fechaRegistro;

    public Franquicia() {
        this.activo = true;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Franquicia(int idFranquicia, String nombre, String fabricante, String descripcion, boolean activo) {
        this.idFranquicia = idFranquicia;
        this.nombre = nombre;
        this.fabricante = fabricante != null ? fabricante : "";
        this.descripcion = descripcion != null ? descripcion : "";
        this.activo = activo;
        this.fechaRegistro = LocalDateTime.now();
    }

    public int getIdFranquicia() {
        return idFranquicia;
    }

    public void setIdFranquicia(int idFranquicia) {
        this.idFranquicia = idFranquicia;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getFabricante() {
        return fabricante;
    }

    public void setFabricante(String fabricante) {
        this.fabricante = fabricante;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
