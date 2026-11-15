package com.spacecards.model;

import java.time.LocalDateTime;

public class HistorialPremioTorneo {
    private String nombreTorneo;
    private LocalDateTime fechaEvento;
    private String nombreJugador;
    private String idJugadorTcg;
    private int posicion;
    private String tipoPremio;
    private String descripcionPremio;

    public String getNombreTorneo() { return nombreTorneo; }
    public void setNombreTorneo(String nombreTorneo) { this.nombreTorneo = nombreTorneo; }

    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }

    public String getNombreJugador() { return nombreJugador; }
    public void setNombreJugador(String nombreJugador) { this.nombreJugador = nombreJugador; }

    public String getIdJugadorTcg() { return idJugadorTcg; }
    public void setIdJugadorTcg(String idJugadorTcg) { this.idJugadorTcg = idJugadorTcg; }

    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }

    public String getTipoPremio() { return tipoPremio; }
    public void setTipoPremio(String tipoPremio) { this.tipoPremio = tipoPremio; }

    public String getDescripcionPremio() { return descripcionPremio; }
    public void setDescripcionPremio(String descripcionPremio) { this.descripcionPremio = descripcionPremio; }
}
