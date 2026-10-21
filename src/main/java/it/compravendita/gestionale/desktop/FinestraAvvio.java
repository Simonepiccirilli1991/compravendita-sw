package it.compravendita.gestionale.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.lang.reflect.InvocationTargetException;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Piccola finestra mostrata mentre database e applicazione si avviano (10-20 secondi). */
final class FinestraAvvio {

	private final JWindow finestra;

	private final JLabel stato;

	private FinestraAvvio(JWindow finestra, JLabel stato) {
		this.finestra = finestra;
		this.stato = stato;
	}

	static FinestraAvvio mostra() throws InterruptedException, InvocationTargetException {
		FinestraAvvio[] creata = new FinestraAvvio[1];
		SwingUtilities.invokeAndWait(() -> {
			JWindow finestra = new JWindow();
			JPanel pannello = new JPanel(new BorderLayout(12, 12));
			pannello.setBackground(Color.WHITE);
			pannello.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x0F766E), 2),
					BorderFactory.createEmptyBorder(20, 24, 20, 24)));
			Image icona = IconaVassoio.immagine();
			if (icona != null) {
				pannello.add(new JLabel(new ImageIcon(icona.getScaledInstance(64, 64, Image.SCALE_SMOOTH))),
						BorderLayout.WEST);
			}
			JPanel testi = new JPanel(new BorderLayout(4, 4));
			testi.setOpaque(false);
			JLabel titolo = new JLabel(AvvioDesktop.NOME);
			titolo.setFont(titolo.getFont().deriveFont(Font.BOLD, 20f));
			JLabel stato = new JLabel("Avvio in corso…", SwingConstants.LEFT);
			JProgressBar barra = new JProgressBar();
			barra.setIndeterminate(true);
			testi.add(titolo, BorderLayout.NORTH);
			testi.add(stato, BorderLayout.CENTER);
			testi.add(barra, BorderLayout.SOUTH);
			pannello.add(testi, BorderLayout.CENTER);
			finestra.setContentPane(pannello);
			finestra.setSize(420, 130);
			finestra.setLocationRelativeTo(null);
			finestra.setAlwaysOnTop(true);
			finestra.setVisible(true);
			creata[0] = new FinestraAvvio(finestra, stato);
		});
		return creata[0];
	}

	void stato(String testo) {
		SwingUtilities.invokeLater(() -> stato.setText(testo));
	}

	void chiudi() {
		SwingUtilities.invokeLater(finestra::dispose);
	}

}
