package bj.epac.retine.plugin.visualiseur;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Observateur;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.LecteurParametres;
import bj.epac.retine.parametres.Parametres;
import bj.epac.retine.plugin.AdaptateurImageJ;
import bj.epac.retine.pretraitement.ChainePretraitement;
import bj.epac.retine.segmentation.SegmentationHessienne;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageConverter;

/**
 * Visualiseur de la chaîne : ouvre une image du fond d'œil, exécute le prétraitement et
 * la segmentation, et affiche chaque résultat intermédiaire <b>au moment où il est
 * calculé</b>. Zoom synchronisé, comparaison avant / après, valeur du pixel sous la
 * souris, paramètres modifiables et relance, export des étapes en PNG.
 *
 * <p>Lancement : {@code mvn -q install -DskipTests} puis
 * {@code mvn -q -pl retine-plugin exec:java} (depuis la racine du dépôt), ou depuis Fiji :
 * Plugins &gt; RDR &gt; Visualiseur. Les explications des étapes reprennent
 * Docs/algorithmes.md.</p>
 */
public final class VisualiseurChaine extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final String ORIGINE = "Image d'origine";

	private final DefaultListModel<EtapeVisible> etapes = new DefaultListModel<EtapeVisible>();
	private final JList<EtapeVisible> liste = new JList<EtapeVisible>(etapes);
	private final DefaultComboBoxModel<EtapeVisible> modeleAvant = new DefaultComboBoxModel<EtapeVisible>();
	private final JComboBox<EtapeVisible> choixAvant = new JComboBox<EtapeVisible>(modeleAvant);
	private final JTextArea explication = new JTextArea();
	private final PanneauImage.Vue vue = new PanneauImage.Vue();
	private final PanneauImage panneauApres = new PanneauImage(vue,
			"Ouvrez une image (bouton « Ouvrir » ou glisser-déposer)");
	private final PanneauImage panneauAvant = new PanneauImage(vue, "Choisissez l'étape « avant »");
	private final JPanel centre = new JPanel(new GridLayout(1, 1, 4, 0));
	private final JPanel enTeteAvant = new JPanel(new BorderLayout());
	private final JToggleButton comparer = new JToggleButton("Avant / après");
	private final JCheckBox suivre = new JCheckBox("Suivre en direct", true);
	private final JButton relancer = new JButton("▶ Relancer avec ces paramètres");
	private final JButton exporter = new JButton("Exporter les étapes…");
	private final PanneauParametres parametres = new PanneauParametres();
	private final JLabel etatPixel = new JLabel(" ");
	private final JLabel resume = new JLabel(" ");
	private final JProgressBar progression = new JProgressBar();

	private Path fichier;
	private JsonObject parametresDuCalcul;
	private String etapeSuivie;
	private boolean choixAvantManuel;
	private boolean calculEnCours;

	/**
	 * @param quitterALaFermeture true pour une application seule (fermer la fenêtre arrête
	 *                            Java), false depuis Fiji
	 */
	public VisualiseurChaine(boolean quitterALaFermeture) {
		super("Rétinopathie — visualiseur de la chaîne");
		setDefaultCloseOperation(quitterALaFermeture ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE);
		parametres.charger(parametresDuFichier());

		// --- barre d'outils
		JToolBar outils = new JToolBar();
		outils.setFloatable(false);
		JButton ouvrir = new JButton("Ouvrir une image…");
		ouvrir.addActionListener(e -> choisirImage());
		JButton ajuster = new JButton("Voir toute l'image");
		ajuster.addActionListener(e -> panneauApres.ajuster());
		comparer.setToolTipText("Affiche deux étapes côte à côte, avec le même zoom");
		comparer.addActionListener(e -> disposerCentre());
		suivre.setToolTipText("Affiche chaque étape dès qu'elle est calculée");
		exporter.addActionListener(e -> exporterEtapes());
		exporter.setEnabled(false);
		outils.add(ouvrir);
		outils.addSeparator();
		outils.add(ajuster);
		outils.add(comparer);
		outils.add(suivre);
		outils.addSeparator();
		outils.add(exporter);
		outils.addSeparator();
		JLabel aide = new JLabel("  molette : zoom · glisser : déplacer · double-clic : tout voir · ↑ ↓ : changer d'étape");
		aide.setForeground(Color.GRAY);
		outils.add(aide);

		// --- liste des étapes et explication
		liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		liste.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		liste.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				etapeChoisie();
			}
		});
		explication.setLineWrap(true);
		explication.setWrapStyleWord(true);
		explication.setEditable(false);
		explication.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		explication.setText("Choisissez une étape pour lire ce qu'elle fait.");
		JScrollPane defilListe = new JScrollPane(liste);
		defilListe.setBorder(BorderFactory.createTitledBorder("Étapes"));
		JScrollPane defilExpl = new JScrollPane(explication);
		defilExpl.setBorder(BorderFactory.createTitledBorder("Qu'est-ce que je regarde ?"));
		JSplitPane gauche = new JSplitPane(JSplitPane.VERTICAL_SPLIT, defilListe, defilExpl);
		gauche.setResizeWeight(0.55);
		gauche.setPreferredSize(new Dimension(430, 700));

		// --- centre : un ou deux panneaux d'image
		choixAvant.addActionListener(e -> {
			if (choixAvant.hasFocus()) {
				choixAvantManuel = true;
			}
			panneauAvant.afficher((EtapeVisible) choixAvant.getSelectedItem());
		});
		enTeteAvant.add(new JLabel(" Avant : "), BorderLayout.WEST);
		enTeteAvant.add(choixAvant, BorderLayout.CENTER);
		panneauApres.barreEtat(etatPixel::setText);
		panneauAvant.barreEtat(etatPixel::setText);
		disposerCentre();

		// --- paramètres
		relancer.addActionListener(e -> lancer());
		relancer.setEnabled(false);
		JButton recharger = new JButton("Valeurs du fichier");
		recharger.setToolTipText("Recharge config/defaut.json");
		recharger.addActionListener(e -> parametres.charger(parametresDuFichier()));
		JButton enregistrer = new JButton("Enregistrer…");
		enregistrer.setToolTipText("Enregistre ces paramètres dans un fichier JSON");
		enregistrer.addActionListener(e -> enregistrerParametres());
		JButton charger = new JButton("Charger…");
		charger.addActionListener(e -> chargerParametres());
		JPanel boutons = new JPanel(new GridLayout(0, 1, 4, 4));
		boutons.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		boutons.add(relancer);
		JPanel fichierBtn = new JPanel(new GridLayout(1, 3, 4, 0));
		fichierBtn.add(recharger);
		fichierBtn.add(charger);
		fichierBtn.add(enregistrer);
		boutons.add(fichierBtn);
		JPanel droite = new JPanel(new BorderLayout());
		JScrollPane defilParam = new JScrollPane(parametres);
		defilParam.setBorder(BorderFactory.createTitledBorder("Paramètres (survolez pour l'aide)"));
		droite.add(defilParam, BorderLayout.CENTER);
		droite.add(boutons, BorderLayout.SOUTH);
		droite.setPreferredSize(new Dimension(390, 700));

		// --- barre d'état
		JPanel bas = new JPanel(new BorderLayout(8, 0));
		bas.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		etatPixel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		progression.setPreferredSize(new Dimension(160, 16));
		progression.setVisible(false);
		JPanel droiteBas = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		droiteBas.add(resume);
		droiteBas.add(progression);
		bas.add(etatPixel, BorderLayout.CENTER);
		bas.add(droiteBas, BorderLayout.EAST);

		JSplitPane principal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gauche,
				new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centre, droite));
		((JSplitPane) principal.getRightComponent()).setResizeWeight(1);
		principal.setDividerLocation(430); // fixe : sinon recalculé (et rétréci) à chaque changement du centre
		getContentPane().add(outils, BorderLayout.NORTH);
		getContentPane().add(principal, BorderLayout.CENTER);
		getContentPane().add(bas, BorderLayout.SOUTH);

		// glisser-déposer d'une image sur la fenêtre
		getRootPane().setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport(TransferSupport s) {
				return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferSupport s) {
				try {
					@SuppressWarnings("unchecked")
					List<File> f = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (!f.isEmpty()) {
						ouvrir(f.get(0).toPath());
						return true;
					}
				} catch (Exception e) {
					erreur("Glisser-déposer impossible : " + e.getMessage());
				}
				return false;
			}
		});

		setSize(1500, 900);
		setLocationRelativeTo(null);
	}

	/** Ouvre une image et lance la chaîne dessus. */
	public void ouvrir(Path image) {
		this.fichier = image;
		setTitle("Rétinopathie — visualiseur — " + image.getFileName());
		vue.reinitialiser();
		lancer();
	}

	private void choisirImage() {
		JFileChooser fc = new JFileChooser(fichier != null ? fichier.getParent().toFile() : dossierDonnees());
		fc.setFileFilter(new FileNameExtensionFilter("Images (jpg, png, tif)", "jpg", "jpeg", "png", "tif", "tiff"));
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			ouvrir(fc.getSelectedFile().toPath());
		}
	}

	private void lancer() {
		if (fichier == null || calculEnCours) {
			return;
		}
		JsonObject json;
		try {
			json = parametres.versJson();
		} catch (IllegalArgumentException e) {
			erreur(e.getMessage());
			return;
		}
		EtapeVisible courante = liste.getSelectedValue();
		etapeSuivie = courante != null ? courante.nom() : null;
		etapes.clear();
		modeleAvant.removeAllElements();
		choixAvantManuel = false;
		panneauApres.afficher(null);
		panneauAvant.afficher(null);
		parametresDuCalcul = json;
		calculEnCours = true;
		relancer.setEnabled(false);
		exporter.setEnabled(false);
		progression.setIndeterminate(true);
		progression.setVisible(true);
		resume.setText("Calcul en cours…");
		new Calcul(fichier, new Parametres(json)).execute();
	}

	/** Exécute la chaîne hors du fil de l'interface et publie chaque étape dès qu'elle est prête. */
	private final class Calcul extends SwingWorker<String, EtapeVisible> {
		private final Path image;
		private final Parametres params;
		private long top;
		private long debut;
		private int numero;
		private ImageGris dernierFond;

		Calcul(Path image, Parametres params) {
			this.image = image;
			this.params = params;
		}

		@Override
		protected String doInBackground() throws Exception {
			debut = System.nanoTime();
			top = debut;
			ImagePlus imp = IJ.openImage(image.toString());
			if (imp == null) {
				throw new IOException("Impossible d'ouvrir " + image);
			}
			if (imp.getType() != ImagePlus.COLOR_RGB) {
				new ImageConverter(imp).convertToRGB();
			}
			ImageCouleur c = AdaptateurImageJ.versCouleur(imp);
			publier(EtapeVisible.couleur(++numero, ORIGINE, c, ecoule()));
			ContexteExecution ctx = new ContexteExecution(params, 42L, Paths.get("executions"));
			Observateur obs = (nom, img, masque) -> publier(convertir(nom, img, masque));
			ChainePretraitement.Resultat pr = new ChainePretraitement().executer(c, ctx, obs);
			SegmentationHessienne.Resultat s = new SegmentationHessienne().segmenter(pr.pretraitee(), ctx, obs);
			long nv = s.vaisseaux().nombrePixelsActifs();
			long nm = pr.masque().nombrePixelsActifs();
			return String.format(Locale.FRANCE,
					"%d × %d px · champ : centre (%.0f, %.0f), rayon %.0f px · vaisseaux : %.1f %% du champ · total %.1f s",
					c.largeur(), c.hauteur(), pr.masque().cx(), pr.masque().cy(), pr.masque().rayon(), 100.0 * nv / nm,
					(System.nanoTime() - debut) / 1e9);
		}

		private EtapeVisible convertir(String nom, ImageGris img, Masque masque) {
			double s = ecoule();
			int n = ++numero;
			if (ChainePretraitement.MASQUE.equals(nom)) {
				return EtapeVisible.champ(n, nom, img, masque, s);
			}
			if (SegmentationHessienne.GERMES.equals(nom)) {
				return EtapeVisible.superpose(n, nom, dernierFond, masque, new Color(255, 60, 60), s);
			}
			if (SegmentationHessienne.HYSTERESIS.equals(nom)) {
				return EtapeVisible.superpose(n, nom, dernierFond, masque, new Color(255, 200, 0), s);
			}
			if (SegmentationHessienne.VAISSEAUX.equals(nom)) {
				return EtapeVisible.superpose(n, nom, img, masque, new Color(40, 230, 60), s);
			}
			if (ChainePretraitement.MEDIAN.equals(nom)) {
				dernierFond = img; // fond des masques de la segmentation : l'image prétraitée
			}
			return EtapeVisible.gris(n, nom, img, masque, nom.startsWith("Réponse"), s);
		}

		/** Secondes depuis l'étape précédente (le temps de rendu n'est pas compté). */
		private double ecoule() {
			long t = System.nanoTime();
			double s = (t - top) / 1e9;
			top = t;
			return s;
		}

		private void publier(EtapeVisible e) {
			e.rendu(); // calculé ici, pas dans le fil de l'interface
			top = System.nanoTime();
			publish(e);
		}

		@Override
		protected void process(List<EtapeVisible> nouvelles) {
			for (EtapeVisible e : nouvelles) {
				etapes.addElement(e);
				modeleAvant.addElement(e);
				if (suivre.isSelected() || e.nom().equals(etapeSuivie) || etapes.size() == 1) {
					liste.setSelectedIndex(etapes.size() - 1);
					liste.ensureIndexIsVisible(etapes.size() - 1);
				}
			}
		}

		@Override
		protected void done() {
			calculEnCours = false;
			relancer.setEnabled(true);
			progression.setVisible(false);
			try {
				resume.setText(get());
				exporter.setEnabled(true);
			} catch (ExecutionException e) {
				Throwable c = e.getCause() != null ? e.getCause() : e;
				resume.setText("Échec : " + c.getMessage());
				erreur(c.getClass().getSimpleName() + " : " + c.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void etapeChoisie() {
		EtapeVisible e = liste.getSelectedValue();
		panneauApres.afficher(e);
		if (e == null) {
			return;
		}
		explication.setText(Explications.pour(e.nom()));
		explication.setCaretPosition(0);
		if (!choixAvantManuel) {
			int i = liste.getSelectedIndex();
			choixAvant.setSelectedItem(i > 0 ? etapes.get(i - 1) : e);
		}
	}

	private void disposerCentre() {
		centre.removeAll();
		if (comparer.isSelected()) {
			centre.setLayout(new GridLayout(1, 2, 4, 0));
			JPanel g = new JPanel(new BorderLayout());
			g.add(enTeteAvant, BorderLayout.NORTH);
			g.add(panneauAvant, BorderLayout.CENTER);
			JPanel d = new JPanel(new BorderLayout());
			d.add(new JLabel(" Après : l'étape choisie dans la liste"), BorderLayout.NORTH);
			d.add(panneauApres, BorderLayout.CENTER);
			centre.add(g);
			centre.add(d);
		} else {
			centre.setLayout(new GridLayout(1, 1));
			centre.add(panneauApres);
		}
		centre.revalidate();
		centre.repaint();
		SwingUtilities.invokeLater(panneauApres::ajuster); // la largeur des panneaux vient de changer
	}

	private void exporterEtapes() {
		if (etapes.isEmpty()) {
			return;
		}
		String nomImage = fichier.getFileName().toString().replaceFirst("\\.[^.]+$", "");
		Path dossier = Paths.get("executions",
				new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-visualiseur-" + nomImage);
		Map<String, EtapeVisible> copie = new LinkedHashMap<String, EtapeVisible>();
		for (int i = 0; i < etapes.size(); i++) {
			EtapeVisible e = etapes.get(i);
			copie.put(String.format(Locale.ROOT, "%02d_%s.png", e.numero(), nomFichier(e.nom())), e);
		}
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(parametresDuCalcul);
		String texteResume = resume.getText();
		exporter.setEnabled(false);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				Files.createDirectories(dossier);
				for (Map.Entry<String, EtapeVisible> e : copie.entrySet()) {
					ImageIO.write(e.getValue().rendu(), "png", dossier.resolve(e.getKey()).toFile());
				}
				Files.write(dossier.resolve("parametres_utilises.json"), json.getBytes(StandardCharsets.UTF_8));
				Files.write(dossier.resolve("resume.txt"),
						("Image : " + fichier + "\n" + texteResume + "\n").getBytes(StandardCharsets.UTF_8));
				return null;
			}

			@Override
			protected void done() {
				exporter.setEnabled(true);
				try {
					get();
					JOptionPane.showMessageDialog(VisualiseurChaine.this,
							copie.size() + " images et les paramètres enregistrés dans\n" + dossier.toAbsolutePath(),
							"Export terminé", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception e) {
					erreur("Export impossible : " + e.getMessage());
				}
			}
		}.execute();
	}

	private void enregistrerParametres() {
		JFileChooser fc = new JFileChooser(new File("config"));
		fc.setSelectedFile(new File("config", "essai.json"));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			String json = new GsonBuilder().setPrettyPrinting().create().toJson(parametres.versJson());
			Files.write(fc.getSelectedFile().toPath(), (json + "\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException | IllegalArgumentException e) {
			erreur("Enregistrement impossible : " + e.getMessage());
		}
	}

	private void chargerParametres() {
		JFileChooser fc = new JFileChooser(new File("config"));
		fc.setFileFilter(new FileNameExtensionFilter("Paramètres JSON", "json"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			parametres.charger(new LecteurParametres().lire(fc.getSelectedFile().toPath()));
		} catch (IOException e) {
			erreur(e.getMessage());
		}
	}

	private static Parametres parametresDuFichier() {
		Path p = Paths.get(System.getProperty("rdr.config", "config/defaut.json"));
		if (Files.exists(p)) {
			try {
				return new LecteurParametres().lire(p);
			} catch (IOException e) {
				IJ.log("RDR : " + e.getMessage() + " — valeurs par défaut utilisées.");
			}
		}
		return Parametres.defauts();
	}

	private static File dossierDonnees() {
		File d = new File("Donnees");
		return d.isDirectory() ? d : new File(".");
	}

	private static String nomFichier(String nom) {
		String s = java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		s = s.replace("σ", "sigma").replaceAll("[^A-Za-z0-9.]+", "_").replaceAll("^_+|_+$", "");
		return s.length() > 60 ? s.substring(0, 60) : s;
	}

	private void erreur(String message) {
		JOptionPane.showMessageDialog(this, message, "Visualiseur", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Point d'entrée de l'application seule.
	 *
	 * @param args facultatif : chemin d'une image à ouvrir tout de suite
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				// apparence par défaut
			}
			VisualiseurChaine v = new VisualiseurChaine(true);
			v.setVisible(true);
			if (args.length > 0) {
				v.ouvrir(Paths.get(args[0]));
			}
		});
	}
}
