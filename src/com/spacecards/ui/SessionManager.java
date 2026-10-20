package com.spacecards.ui;

import com.spacecards.model.Usuario;

public class SessionManager {
    private static SessionManager instance;
    private Usuario currentUser;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public Usuario getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(Usuario user) {
        this.currentUser = user;
    }

    public boolean isAshBridgeUser() {
        return currentUser != null && currentUser.getIdUsuario() == -1;
    }

    public boolean canAccessCard(String cardName) {
        if (currentUser == null) return false;
        if (isAshBridgeUser()) {
            // Ash Ketchum (Usuario Puente) SOLO tiene permiso para USUARIOS (Personal / Staff)
            return "USUARIOS".equals(cardName);
        }
        String rol = currentUser.getRol();
        if ("Administrador".equalsIgnoreCase(rol)) {
            return true; // Administrador tiene acceso total
        } else if ("Vendedor".equalsIgnoreCase(rol)) {
            // Vendedor tiene acceso a POS, Inventario, Clientes, Preventas y Dashboard
            return !"USUARIOS".equals(cardName) && !"DATABASE".equals(cardName);
        } else if ("Organizador_Torneos".equalsIgnoreCase(rol)) {
            // Organizador tiene acceso a Torneos, Franquicias, Clientes y Dashboard
            return "TORNEOS".equals(cardName) || "FRANQUICIAS".equals(cardName) || "CLIENTES".equals(cardName) || "DASHBOARD".equals(cardName);
        }
        return true;
    }
}
