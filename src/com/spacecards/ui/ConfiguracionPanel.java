package com.spacecards.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConfiguracionPanel extends JPanel {
    private final FranquiciasPanel franquiciasPanel;
    private final CategoriasPanel categoriasPanel;

    public ConfiguracionPanel(Runnable onDataChanged) {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(20, 20, 16, 20));

        JLabel title = new JLabel("Configuración");
        title.setFont(UITheme.FONT_TITLE);
        JLabel subtitle = new JLabel("Administra las franquicias TCG y las categorías disponibles para el inventario");
        subtitle.setFont(UITheme.FONT_REGULAR);
        subtitle.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        franquiciasPanel = new FranquiciasPanel(onDataChanged);
        categoriasPanel = new CategoriasPanel(onDataChanged);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Franquicias TCG", franquiciasPanel);
        tabs.addTab("Categorías de inventario", categoriasPanel);
        add(tabs, BorderLayout.CENTER);
    }

    public void refreshTables() {
        franquiciasPanel.refreshTable();
        categoriasPanel.refreshTable();
    }
}
