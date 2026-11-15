package com.spacecards.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Torneo {
    private int idTorneo;
    private int idOrganizador;
    private Usuario organizador;
    private String nombreTorneo;
    private String juegoTcg;
    private LocalDateTime fechaEvento;
    private BigDecimal costoInscripcion;
    private int cupoMaximo;
    private String estado; // 'Programado', 'En Curso', 'Finalizado', 'Cancelado'
    private int participantesInscritos;
    private int participantesPagados;
    private BigDecimal porcentajePremiosMonetarios = BigDecimal.ZERO;
    private BigDecimal porcentajeFondoAsignado = BigDecimal.ZERO;

    public Torneo() {
        this.juegoTcg = "Pokémon";
        this.fechaEvento = LocalDateTime.now().plusDays(7);
        this.costoInscripcion = BigDecimal.ZERO;
        this.cupoMaximo = 16;
        this.estado = "Programado";
        this.participantesInscritos = 0;
    }

    public Torneo(int idTorneo, Usuario organizador, String nombreTorneo, String juegoTcg,
                  LocalDateTime fechaEvento, BigDecimal costoInscripcion, int cupoMaximo, String estado) {
        this.idTorneo = idTorneo;
        this.organizador = organizador;
        this.idOrganizador = organizador != null ? organizador.getIdUsuario() : 0;
        this.nombreTorneo = nombreTorneo;
        this.juegoTcg = juegoTcg != null ? juegoTcg : "Pokémon";
        this.fechaEvento = fechaEvento;
        this.costoInscripcion = costoInscripcion;
        this.cupoMaximo = cupoMaximo;
        this.estado = estado != null ? estado : "Programado";
        this.participantesInscritos = 0;
    }

    public int getIdTorneo() { return idTorneo; }
    public void setIdTorneo(int idTorneo) { this.idTorneo = idTorneo; }

    public int getIdOrganizador() { return idOrganizador; }
    public void setIdOrganizador(int idOrganizador) { this.idOrganizador = idOrganizador; }

    public Usuario getOrganizador() { return organizador; }
    public void setOrganizador(Usuario organizador) {
        this.organizador = organizador;
        if (organizador != null) this.idOrganizador = organizador.getIdUsuario();
    }

    public String getNombreTorneo() { return nombreTorneo; }
    public void setNombreTorneo(String nombreTorneo) { this.nombreTorneo = nombreTorneo; }

    public String getJuegoTcg() { return juegoTcg; }
    public void setJuegoTcg(String juegoTcg) { this.juegoTcg = juegoTcg; }

    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }

    public BigDecimal getCostoInscripcion() { return costoInscripcion; }
    public void setCostoInscripcion(BigDecimal costoInscripcion) { this.costoInscripcion = costoInscripcion; }

    public int getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(int cupoMaximo) { this.cupoMaximo = cupoMaximo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getParticipantesInscritos() { return participantesInscritos; }
    public void setParticipantesInscritos(int participantesInscritos) { this.participantesInscritos = participantesInscritos; }

    public int getParticipantesPagados() { return participantesPagados; }
    public void setParticipantesPagados(int participantesPagados) { this.participantesPagados = participantesPagados; }

    public BigDecimal getPorcentajePremiosMonetarios() { return porcentajePremiosMonetarios; }
    public void setPorcentajePremiosMonetarios(BigDecimal porcentajePremiosMonetarios) {
        this.porcentajePremiosMonetarios = porcentajePremiosMonetarios != null
                ? porcentajePremiosMonetarios : BigDecimal.ZERO;
    }

    public BigDecimal getPorcentajeFondoAsignado() { return porcentajeFondoAsignado; }
    public void setPorcentajeFondoAsignado(BigDecimal porcentajeFondoAsignado) {
        this.porcentajeFondoAsignado = porcentajeFondoAsignado != null
                ? porcentajeFondoAsignado : BigDecimal.ZERO;
    }

    public BigDecimal getRecaudacionEstimada() {
        return costoInscripcion.multiply(BigDecimal.valueOf(cupoMaximo));
    }

    public BigDecimal getRecaudacionReal() {
        if ("Cancelado".equalsIgnoreCase(estado)) return BigDecimal.ZERO;
        return costoInscripcion.multiply(BigDecimal.valueOf(participantesPagados));
    }

    public BigDecimal getPremiosMonetariosReales() {
        return getRecaudacionReal().multiply(porcentajePremiosMonetarios)
                .multiply(porcentajeFondoAsignado)
                .divide(new BigDecimal("10000"), 2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getIngresoTiendaReal() {
        return getRecaudacionReal().subtract(getPremiosMonetariosReales());
    }

    public int getCuposDisponibles() {
        return Math.max(0, cupoMaximo - participantesInscritos);
    }

    @Override
    public String toString() {
        return nombreTorneo + " (" + juegoTcg + ") - " + estado;
    }
}
