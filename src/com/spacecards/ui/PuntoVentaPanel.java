package com.spacecards.ui;

import javax.swing.*;
import java.awt.*;

public class PuntoVentaPanel extends JPanel {
    private final VentasPanel ventasPanel;
    private final PreventasPanel preventasPanel;

    public PuntoVentaPanel(Runnable onDataChanged) {
        setLayout(new BorderLayout());

        ventasPanel = new VentasPanel(onDataChanged);
        preventasPanel = new PreventasPanel(onDataChanged);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ventas", ventasPanel);
        tabs.addTab("Preventas", preventasPanel);
        add(tabs, BorderLayout.CENTER);
    }

    public void refreshPanels() {
        ventasPanel.refreshAll();
        preventasPanel.refreshTable();
    }
}
