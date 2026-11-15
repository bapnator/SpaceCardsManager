package com.spacecards.model;

public class ProductoPremio {
    private int idProductoPremio;
    private String nombre;
    private String patrocinador;
    private String descripcion;
    private int stock;

    public int getIdProductoPremio() { return idProductoPremio; }
    public void setIdProductoPremio(int idProductoPremio) { this.idProductoPremio = idProductoPremio; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getPatrocinador() { return patrocinador; }
    public void setPatrocinador(String patrocinador) { this.patrocinador = patrocinador; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    @Override
    public String toString() {
        return nombre + " (disponibles: " + stock + ")";
    }
}
