package com.spacecards.model;

import java.time.LocalDateTime;

public class ParticipanteTorneo {
    private int idInscripcion;
    private int idTorneo;
    private Torneo torneo;
    private int idCliente;
    private Cliente cliente;
    private String idJugadorTcg; // Melee ID / POP ID Pokémon
    private LocalDateTime fechaInscripcion;
    private boolean pagoConfirmado;
    private Integer posicionFinal; // Nullable

    public ParticipanteTorneo() {
        this.fechaInscripcion = LocalDateTime.now();
        this.pagoConfirmado = false;
    }

    public ParticipanteTorneo(int idInscripcion, Torneo torneo, Cliente cliente, String idJugadorTcg,
                              boolean pagoConfirmado, Integer posicionFinal) {
        this.idInscripcion = idInscripcion;
        this.torneo = torneo;
        this.idTorneo = torneo != null ? torneo.getIdTorneo() : 0;
        this.cliente = cliente;
        this.idCliente = cliente != null ? cliente.getIdCliente() : 0;
        this.idJugadorTcg = idJugadorTcg;
        this.fechaInscripcion = LocalDateTime.now();
        this.pagoConfirmado = pagoConfirmado;
        this.posicionFinal = posicionFinal;
    }

    public int getIdInscripcion() { return idInscripcion; }
    public void setIdInscripcion(int idInscripcion) { this.idInscripcion = idInscripcion; }

    public int getIdTorneo() { return idTorneo; }
    public void setIdTorneo(int idTorneo) { this.idTorneo = idTorneo; }

    public Torneo getTorneo() { return torneo; }
    public void setTorneo(Torneo torneo) {
        this.torneo = torneo;
        if (torneo != null) this.idTorneo = torneo.getIdTorneo();
    }

    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
        if (cliente != null) this.idCliente = cliente.getIdCliente();
    }

    public String getIdJugadorTcg() { return idJugadorTcg; }
    public void setIdJugadorTcg(String idJugadorTcg) { this.idJugadorTcg = idJugadorTcg; }

    public LocalDateTime getFechaInscripcion() { return fechaInscripcion; }
    public void setFechaInscripcion(LocalDateTime fechaInscripcion) { this.fechaInscripcion = fechaInscripcion; }

    public boolean isPagoConfirmado() { return pagoConfirmado; }
    public void setPagoConfirmado(boolean pagoConfirmado) { this.pagoConfirmado = pagoConfirmado; }

    public Integer getPosicionFinal() { return posicionFinal; }
    public void setPosicionFinal(Integer posicionFinal) { this.posicionFinal = posicionFinal; }
}
