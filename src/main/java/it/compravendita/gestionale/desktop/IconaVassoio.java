package it.compravendita.gestionale.desktop;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

/**
 * Icona nell'area di notifica di Windows (vicino all'orologio): doppio clic apre il gestionale,
 * il menu permette di aprire le cartelle dei dati e dei backup o di chiudere il programma.
 */
final class IconaVassoio {

	private IconaVassoio() {
	}

	static Image immagine() {
		URL risorsa = IconaVassoio.class.getResource("/desktop/icona.png");
		return risorsa == null ? null : Toolkit.getDefaultToolkit().getImage(risorsa);
	}

	static void installa(String indirizzo, CartelleDesktop cartelle, Path cartellaBackup) {
		if (!SystemTray.isSupported() || immagine() == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			PopupMenu menu = new PopupMenu();
			menu.add(voce("Apri " + AvvioDesktop.NOME, () -> AvvioDesktop.Browser.apri(indirizzo)));
			menu.add(voce("Apri la cartella dei backup", () -> apriCartella(cartellaBackup)));
			menu.add(voce("Apri la cartella dei dati", () -> apriCartella(cartelle.base())));
			menu.addSeparator();
			menu.add(voce("Chiudi " + AvvioDesktop.NOME, AvvioDesktop::chiudi));
			TrayIcon icona = new TrayIcon(immagine(), AvvioDesktop.NOME + " · " + indirizzo, menu);
			icona.setImageAutoSize(true);
			icona.addActionListener(e -> AvvioDesktop.Browser.apri(indirizzo));
			try {
				SystemTray.getSystemTray().add(icona);
				icona.displayMessage(AvvioDesktop.NOME, "Il gestionale è aperto. Per chiuderlo usa questa icona "
						+ "o il pulsante \"Chiudi programma\" nella pagina.", TrayIcon.MessageType.INFO);
			}
			catch (AWTException ex) {
				// senza icona il programma si chiude dal pulsante nella pagina
			}
		});
	}

	private static MenuItem voce(String testo, Runnable azione) {
		MenuItem voce = new MenuItem(testo);
		voce.addActionListener(e -> azione.run());
		return voce;
	}

	private static void apriCartella(Path cartella) {
		try {
			Files.createDirectories(cartella);
			java.awt.Desktop.getDesktop().open(cartella.toFile());
		}
		catch (IOException | UnsupportedOperationException ex) {
			Messaggi.errore("Impossibile aprire la cartella " + cartella);
		}
	}

}
