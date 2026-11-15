package com.spacecards.model;

import java.math.BigDecimal;

public class PremioInscripcionTorneo {
    public static final String TIPO_INVENTARIO = "INVENTARIO";
    public static final String TIPO_PATROCINADO = "PATROCINADO";
    public static final String TIPO_SALDO_FAVOR = "SALDO_FAVOR";

    private String tipoPremio;
    private Integer idProducto;
    private Integer idProductoPremio;
    private BigDecimal monto = BigDecimal.ZERO;

    public String getTipoPremio() { return tipoPremio; }
    public void setTipoPremio(String tipoPremio) { this.tipoPremio = tipoPremio; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getIdProductoPremio() { return idProductoPremio; }
    public void setIdProductoPremio(Integer idProductoPremio) { this.idProductoPremio = idProductoPremio; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) {
        this.monto = monto != null ? monto : BigDecimal.ZERO;
    }
}
