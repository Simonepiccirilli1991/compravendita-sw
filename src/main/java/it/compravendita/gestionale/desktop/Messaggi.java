package it.compravendita.gestionale.desktop;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Arrays;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.UIManager;

/** Finestre di dialogo Swing usate all'avvio della versione installata. */
final class Messaggi {

	static {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
			// resta l'aspetto predefinito di Swing
		}
	}

	private Messaggi() {
	}

	/** Carica la classe, e con lei l'aspetto di Windows, prima di mostrare qualsiasi finestra. */
	static void prepara() {
	}

	static void errore(String testo) {
		JOptionPane.showMessageDialog(null, testo, AvvioDesktop.NOME, JOptionPane.ERROR_MESSAGE);
	}

	static void informazione(String testo) {
		JOptionPane.showMessageDialog(null, testo, AvvioDesktop.NOME, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Chiede utente e password alla prima esecuzione.
	 *
	 * @return {utente, password}, oppure null se l'utente annulla (restano admin / admin)
	 */
	static String[] chiediCredenziali(Path fileImpostazioni) {
		JTextField utente = new JTextField(ConfigurazioneDesktop.UTENTE_PREDEFINITO, 18);
		JPasswordField password = new JPasswordField(18);
		JPasswordField conferma = new JPasswordField(18);
		JPanel pannello = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		pannello.add(new JLabel("<html>Benvenuto! Scegli nome utente e password per entrare nel gestionale.<br>"
				+ "Potrai cambiarli in seguito nel file<br><i>" + fileImpostazioni + "</i></html>"), c);
		c.gridwidth = 1;
		aggiungi(pannello, c, 1, "Utente", utente);
		aggiungi(pannello, c, 2, "Password", password);
		aggiungi(pannello, c, 3, "Ripeti la password", conferma);
		while (true) {
			int scelta = JOptionPane.showConfirmDialog(null, pannello, AvvioDesktop.NOME + " · primo avvio",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (scelta != JOptionPane.OK_OPTION) {
				informazione("Credenziali predefinite: utente admin, password admin.\nPuoi cambiarle nel file "
						+ fileImpostazioni);
				return null;
			}
			char[] scelta1 = password.getPassword();
			char[] scelta2 = conferma.getPassword();
			String nome = utente.getText().strip();
			if (nome.isEmpty() || scelta1.length == 0) {
				errore("Inserisci utente e password.");
			}
			else if (!Arrays.equals(scelta1, scelta2)) {
				errore("Le due password non coincidono.");
			}
			else if (new String(scelta1).contains("\n") || new String(scelta1).strip().length() != scelta1.length) {
				errore("La password non può iniziare o finire con uno spazio.");
			}
			else {
				return new String[] { nome, new String(scelta1) };
			}
		}
	}

	private static void aggiungi(JPanel pannello, GridBagConstraints c, int riga, String etichetta,
			JTextField campo) {
		c.gridy = riga;
		c.gridx = 0;
		pannello.add(new JLabel(etichetta), c);
		c.gridx = 1;
		pannello.add(campo, c);
	}

}
