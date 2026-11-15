package com.spacecards.model;

import java.math.BigDecimal;

public class PremioTorneo {
    public static final String TIPO_MONETARIO = "MONETARIO";
    public static final String TIPO_INVENTARIO = "INVENTARIO";
    public static final String TIPO_PATROCINADO = "PATROCINADO";

    private int idTorneo;
    private int posicion;
    private String tipoPremio;
    private Integer idProducto;
    private Integer idProductoPremio;
    private BigDecimal porcentajeFondo = BigDecimal.ZERO;
    private String nombrePremio;

    public int getIdTorneo() { return idTorneo; }
    public void setIdTorneo(int idTorneo) { this.idTorneo = idTorneo; }

    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }

    public String getTipoPremio() { return tipoPremio; }
    public void setTipoPremio(String tipoPremio) { this.tipoPremio = tipoPremio; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getIdProductoPremio() { return idProductoPremio; }
    public void setIdProductoPremio(Integer idProductoPremio) { this.idProductoPremio = idProductoPremio; }

    public BigDecimal getPorcentajeFondo() { return porcentajeFondo; }
    public void setPorcentajeFondo(BigDecimal porcentajeFondo) {
        this.porcentajeFondo = porcentajeFondo != null ? porcentajeFondo : BigDecimal.ZERO;
    }

    public String getNombrePremio() { return nombrePremio; }
    public void setNombrePremio(String nombrePremio) { this.nombrePremio = nombrePremio; }
}
