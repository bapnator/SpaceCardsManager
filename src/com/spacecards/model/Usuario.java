package com.spacecards.model;

public class Usuario {
    private int idUsuario;
    private String nombre;
    private String apellido;
    private String rol; // 'Administrador', 'Vendedor', 'Organizador_Torneos'
    private String horario;
    private boolean activo;

    public Usuario() {
        this.activo = true;
    }

    public Usuario(int idUsuario, String nombre, String apellido, String rol, String horario, boolean activo) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.apellido = apellido;
        this.rol = rol;
        this.horario = horario;
        this.activo = activo;
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getNombreCompleto() { return nombre + " " + apellido; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return getNombreCompleto() + " [" + rol + "]";
    }
}
