package com.spacecards.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class UITheme {
    // Paleta de Colores TCG
    public static final Color COLOR_PRIMARY = new Color(59, 130, 246);      // Azul eléctrico
    public static final Color COLOR_PRIMARY_DARK = new Color(30, 58, 138);
    public static final Color COLOR_ACCENT = new Color(245, 158, 11);        // Amarillo / Oro Pikachu
    public static final Color COLOR_SUCCESS = new Color(16, 185, 129);       // Verde hierba
    public static final Color COLOR_DANGER = new Color(239, 68, 68);         // Rojo Pokéball
    public static final Color COLOR_BG_SIDEBAR = new Color(15, 23, 42);      // Slate oscuro profundo
    public static final Color COLOR_BG_CARD = new Color(30, 41, 59);         // Slate contenedor
    public static final Color COLOR_TEXT_MUTED = new Color(148, 163, 184);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BIG_NUMBER = new Font("Segoe UI", Font.BOLD, 26);

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(COLOR_PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("FlatLaf.style", "arc: 8; borderWidth: 0");
        return btn;
    }

    public static JButton createAccentButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(COLOR_ACCENT);
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("FlatLaf.style", "arc: 8; borderWidth: 0");
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_REGULAR);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("FlatLaf.style", "arc: 8");
        return btn;
    }

    public static JPanel createCardPanel() {
        JPanel card = new JPanel();
        card.setBackground(COLOR_BG_CARD);
        card.putClientProperty("FlatLaf.style", "arc: 12");
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        return card;
    }

    public static void formatTable(JTable table) {
        table.setRowHeight(32);
        table.setFont(FONT_REGULAR);
        table.getTableHeader().setFont(FONT_BOLD);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        // Centrar columnas que no sean de texto largo
        if (table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setPreferredWidth(50);
            table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        }
    }
}
