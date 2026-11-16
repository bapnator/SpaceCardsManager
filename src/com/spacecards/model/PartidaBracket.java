package com.spacecards.model;

public class PartidaBracket {
    public static final String TIPO_NORMAL = "NORMAL";
    public static final String TIPO_TERCER_LUGAR = "TERCER_LUGAR";

    private int idTorneo;
    private int ronda;
    private int numeroPartida;
    private String tipoPartida;
    private Integer idParticipanteA;
    private Integer idParticipanteB;
    private Integer idGanador;
    private String nombreParticipanteA;
    private String nombreParticipanteB;
    private boolean completada;
    private boolean paseAutomatico;

    public int getIdTorneo() { return idTorneo; }
    public void setIdTorneo(int idTorneo) { this.idTorneo = idTorneo; }

    public int getRonda() { return ronda; }
    public void setRonda(int ronda) { this.ronda = ronda; }

    public int getNumeroPartida() { return numeroPartida; }
    public void setNumeroPartida(int numeroPartida) { this.numeroPartida = numeroPartida; }

    public String getTipoPartida() { return tipoPartida; }
    public void setTipoPartida(String tipoPartida) { this.tipoPartida = tipoPartida; }

    public Integer getIdParticipanteA() { return idParticipanteA; }
    public void setIdParticipanteA(Integer idParticipanteA) { this.idParticipanteA = idParticipanteA; }

    public Integer getIdParticipanteB() { return idParticipanteB; }
    public void setIdParticipanteB(Integer idParticipanteB) { this.idParticipanteB = idParticipanteB; }

    public Integer getIdGanador() { return idGanador; }
    public void setIdGanador(Integer idGanador) { this.idGanador = idGanador; }

    public String getNombreParticipanteA() { return nombreParticipanteA; }
    public void setNombreParticipanteA(String nombreParticipanteA) { this.nombreParticipanteA = nombreParticipanteA; }

    public String getNombreParticipanteB() { return nombreParticipanteB; }
    public void setNombreParticipanteB(String nombreParticipanteB) { this.nombreParticipanteB = nombreParticipanteB; }

    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }

    public boolean isPaseAutomatico() { return paseAutomatico; }
    public void setPaseAutomatico(boolean paseAutomatico) { this.paseAutomatico = paseAutomatico; }
}
