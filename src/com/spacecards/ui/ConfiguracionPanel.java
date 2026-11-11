package com.spacecards.ui;

import com.spacecards.dao.DatabaseService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ConfiguracionPanel extends JPanel {
    private final DatabaseService db = DatabaseService.getInstance();
    private final FranquiciasPanel franquiciasPanel;
    private final CategoriasPanel categoriasPanel;
    private final JSpinner spinMargenLlegada = new JSpinner(new SpinnerNumberModel(7, 0, 365, 1));
    private final JSpinner spinPorcentajeApartado = new JSpinner(new SpinnerNumberModel(30.0, 0.01, 100.0, 1.0));
    private final JSpinner spinDiasVigencia = new JSpinner(new SpinnerNumberModel(30, 0, 365, 1));
    private final JTextField txtCostoTorneo = new JTextField("15.00", 12);
    private final JSpinner spinCupoTorneo = new JSpinner(new SpinnerNumberModel(16, 2, Integer.MAX_VALUE, 2));

    public ConfiguracionPanel(Runnable onDataChanged) {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(20, 20, 16, 20));

        JLabel title = new JLabel("Configuración");
        title.setFont(UITheme.FONT_TITLE);
        JLabel subtitle = new JLabel("Administra catálogos y parámetros operativos de la tienda");
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
        JPanel parametrosPanel = crearParametrosPanel(onDataChanged);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Franquicias TCG", franquiciasPanel);
        tabs.addTab("Categorías de inventario", categoriasPanel);
        tabs.addTab("Parámetros", parametrosPanel);
        add(tabs, BorderLayout.CENTER);
        cargarParametros();
    }

    public void refreshTables() {
        franquiciasPanel.refreshTable();
        categoriasPanel.refreshTable();
        cargarParametros();
    }

    private JPanel crearParametrosPanel(Runnable onDataChanged) {
        JPanel panel = UITheme.createCardPanel();
        panel.setLayout(new BorderLayout(12, 12));

        JLabel titulo = new JLabel("Parámetros operativos");
        titulo.setFont(UITheme.FONT_SUBTITLE);
        JLabel descripcion = new JLabel("<html>Ajusta los valores iniciales y reglas operativas. Cada producto o torneo nuevo puede conservar sus propios valores.</html>");
        descripcion.setFont(UITheme.FONT_REGULAR);
        descripcion.setForeground(UITheme.COLOR_TEXT_MUTED);

        JPanel encabezado = new JPanel();
        encabezado.setOpaque(false);
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.add(titulo);
        encabezado.add(Box.createVerticalStrut(6));
        encabezado.add(descripcion);
        panel.add(encabezado, BorderLayout.NORTH);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6;
        formulario.add(new JLabel("Margen de llegada de preventas (días):"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.4;
        formulario.add(spinMargenLlegada, gbc);

        spinPorcentajeApartado.setEditor(new JSpinner.NumberEditor(spinPorcentajeApartado, "#0.##"));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        formulario.add(new JLabel("Porcentaje predeterminado para apartar (%):"), gbc);
        gbc.gridx = 1;
        formulario.add(spinPorcentajeApartado, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formulario.add(new JLabel("Vigencia del apartado (días):"), gbc);
        gbc.gridx = 1;
        formulario.add(spinDiasVigencia, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formulario.add(new JLabel("Costo predeterminado de inscripción ($):"), gbc);
        gbc.gridx = 1;
        formulario.add(txtCostoTorneo, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formulario.add(new JLabel("Cupo máximo predeterminado:"), gbc);
        gbc.gridx = 1;
        formulario.add(spinCupoTorneo, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        formulario.add(new JLabel("<html>El margen y la vigencia aceptan de 0 a 365 días. Los apartados vencidos se señalan para cancelación manual.<br>El porcentaje acepta de 0.01 a 100; el cupo de torneo debe ser par y mayor o igual a 2.</html>"), gbc);
        panel.add(formulario, BorderLayout.CENTER);

        JButton btnGuardar = UITheme.createPrimaryButton("Guardar parámetros");
        btnGuardar.addActionListener(e -> {
            try {
                spinCupoTorneo.commitEdit();
                BigDecimal costo = new BigDecimal(txtCostoTorneo.getText().trim())
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal porcentaje = BigDecimal.valueOf(((Number) spinPorcentajeApartado.getValue()).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP);
                db.guardarParametrosOperativos(
                        (Integer) spinMargenLlegada.getValue(),
                        porcentaje,
                        (Integer) spinDiasVigencia.getValue(),
                        costo,
                        (Integer) spinCupoTorneo.getValue());
                if (onDataChanged != null) onDataChanged.run();
                JOptionPane.showMessageDialog(this, "Los parámetros se guardaron correctamente.", "Configuración guardada", JOptionPane.INFORMATION_MESSAGE);
            } catch (java.text.ParseException ex) {
                JOptionPane.showMessageDialog(this, "El cupo debe ser un número entero.", "No se pudo guardar", JOptionPane.WARNING_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "El costo de inscripción debe ser un valor numérico válido.", "No se pudo guardar", JOptionPane.WARNING_MESSAGE);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo guardar", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.setOpaque(false);
        acciones.add(btnGuardar);
        panel.add(acciones, BorderLayout.SOUTH);
        return panel;
    }

    private void cargarParametros() {
        try {
            spinMargenLlegada.setValue(db.getDiasMargenLlegada());
            spinPorcentajeApartado.setValue(db.getPorcentajeApartadoPredeterminado().doubleValue());
            spinDiasVigencia.setValue(db.getDiasVigenciaApartado());
            txtCostoTorneo.setText(db.getCostoTorneoPredeterminado().toPlainString());
            spinCupoTorneo.setValue(db.getCupoTorneoPredeterminado());
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo cargar la configuración", JOptionPane.ERROR_MESSAGE);
        }
    }
}
