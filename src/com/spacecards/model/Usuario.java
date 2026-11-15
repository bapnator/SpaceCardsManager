package com.spacecards.model;

import com.spacecards.dao.DatabaseService;

public class Usuario {
    public static final String DEFAULT_PASSWORD = "admin123";
    public static final String ASH_BRIDGE_PASSWORD = "bridge123";

    public static String generateTemporaryPassword(String nombre) {
        if (nombre == null) {
            throw new IllegalArgumentException("El nombre del usuario es obligatorio para generar la contraseña temporal.");
        }
        String normalizedName = nombre.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("El nombre del usuario no contiene caracteres válidos.");
        }
        return normalizedName + "123";
    }

    public static String getPasswordStrengthWarning(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "La contraseña está vacía.";
        }

        String value = password.trim();
        int length = value.length();
        boolean hasUpper = value.matches(".*[A-Z].*");
        boolean hasLower = value.matches(".*[a-z].*");
        boolean hasDigit = value.matches(".*\\d.*");
        boolean hasSpecial = value.matches(".*[^A-Za-z0-9].*");

        int score = 0;
        if (length >= 8) score++;
        if (length >= 12) score++;
        if (hasUpper) score++;
        if (hasLower) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;

        if (score >= 5) {
            return "";
        }

        if (length < 8) {
            return "Aviso: la contraseña es corta; usa al menos 8 caracteres.";
        }

        if (score <= 3) {
            return "Aviso: la contraseña es débil. Mejora con mayúsculas, números o caracteres especiales.";
        }

        return "Aviso: la contraseña puede mejorar; recomienda usar mezcla de letras, números y símbolos.";
    }

    public static boolean isPasswordWeak(String password) {
        return getPasswordStrengthWarning(password) != null && !getPasswordStrengthWarning(password).isEmpty();
    }

    private int idUsuario;
    private String nombre;
    private String apellido;
    private String rol; // 'Administrador', 'Vendedor', 'Organizador_Torneos'
    private String horario;
    private boolean activo;
    private String passwordHash;
    private boolean passwordChangeRequired;

    public Usuario() {
        this.activo = true;
        this.passwordHash = DatabaseService.hashPassword(DEFAULT_PASSWORD);
    }

    public Usuario(int idUsuario, String nombre, String apellido, String rol, String horario, boolean activo) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.apellido = apellido;
        this.rol = rol;
        this.horario = horario;
        this.activo = activo;
        this.passwordHash = DatabaseService.hashPassword(DEFAULT_PASSWORD);
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

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash == null ? "" : passwordHash;
    }

    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public void setPassword(String password) {
        String normalized = password == null || password.trim().isEmpty() ? DEFAULT_PASSWORD : password.trim();
        this.passwordHash = DatabaseService.hashPassword(normalized);
    }

    public boolean isPasswordValid(String password) {
        if (password == null) {
            return false;
        }
        return DatabaseService.hashPassword(password).equals(this.passwordHash);
    }

    @Override
    public String toString() {
        return getNombreCompleto() + " [" + rol + "]";
    }
}
