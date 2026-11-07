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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import bj.epac.retine.squelette.AmincissementZhangSuen;
import bj.epac.retine.zernike.DescripteurZernike;
import bj.epac.retine.bifurcation.Bifurcation;
import bj.epac.retine.bifurcation.DetecteurBifurcations;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageConverter;

/**
 * Visualiseur de la chaîne sur une <b>paire de visites</b> : visite 1 (référence) et
 * visite 2 (suivi) d'un même œil. Les deux images passent par le prétraitement et la
 * segmentation en même temps, et chaque résultat intermédiaire s'affiche <b>au moment où
 * il est calculé</b>.
 *
 * <p>Affichages : les deux visites côte à côte à la même étape, ou avant / après sur une
 * visite. Zoom synchronisé, valeur du pixel sous la souris, paramètres modifiables et
 * relance, export des étapes en PNG. Une image seule reste possible (HRF, IDRiD).</p>
 *
 * <p>Lancement : {@code mvn -q install -DskipTests} puis
 * {@code mvn -q -pl retine-plugin exec:java} (depuis la racine du dépôt), ou depuis Fiji :
 * Plugins &gt; RDR &gt; Visualiseur. Les explications reprennent Docs/algorithmes.md.</p>
 */
public final class VisualiseurChaine extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final String ORIGINE = "Image d'origine";
	private static final String[] VISITE = { "Visite 1 (référence)", "Visite 2 (suivi)" };
	private static final String[] COURT = { "Visite 1", "Visite 2" };
	private static final String[] AFFICHAGES = { "Visite 1 | Visite 2", "Avant | après — visite 1",
			"Avant | après — visite 2" };
	private static final Pattern NOM_FIRE = Pattern.compile("(.+)_([12])\\.(\\w+)");

	/** Une étape de la chaîne, avec son résultat pour chaque visite (null tant qu'il n'est pas calculé). */
	static final class EtapePaire {
		final int numero;
		final String nom;
		final EtapeVisible[] visites = new EtapeVisible[2];

		EtapePaire(int numero, String nom) {
			this.numero = numero;
			this.nom = nom;
		}

		@Override
		public String toString() {
			StringBuilder t = new StringBuilder();
			for (EtapeVisible v : visites) {
				if (v != null) {
					t.append(t.length() == 0 ? "" : " | ").append(String.format(Locale.FRANCE, "%.1f s", v.secondes()));
				}
			}
			return String.format(Locale.FRANCE, "%2d. %s   (%s)", numero, nom, t);
		}
	}

	private final DefaultListModel<EtapePaire> etapes = new DefaultListModel<EtapePaire>();
	private final JList<EtapePaire> liste = new JList<EtapePaire>(etapes);
	private final DefaultComboBoxModel<EtapePaire> modeleAvant = new DefaultComboBoxModel<EtapePaire>();
	private final JComboBox<EtapePaire> choixAvant = new JComboBox<EtapePaire>(modeleAvant);
	private final JComboBox<String> affichage = new JComboBox<String>(AFFICHAGES);
	private final JComboBox<String> pairesFire = new JComboBox<String>();
	private final JTextArea explication = new JTextArea();
	private final PanneauImage.Vue vue = new PanneauImage.Vue();
	private final PanneauImage panneauG = new PanneauImage(vue, "");
	private final PanneauImage panneauD = new PanneauImage(vue, "");
	private final JLabel enteteG = new JLabel();
	private final JLabel enteteD = new JLabel();
	private final JPanel hautG = new JPanel(new BorderLayout(4, 0));
	private final JLabel avertissement = new JLabel(" ");
	private final JPanel centre = new JPanel(new GridLayout(1, 2, 6, 0));
	private final JCheckBox suivre = new JCheckBox("Suivre en direct", true);
	private final JButton relancer = new JButton("▶ Relancer avec ces paramètres");
	private final JButton exporter = new JButton("Exporter les étapes…");

	/** Noms des étapes publiées directement par le visualiseur (points, fenêtres). */
	static final String POINTS = "Bifurcations et croisements";
	static final String FENETRES = "Zernike : fenêtre autour de chaque point";

	/** Résultats du dernier calcul, par visite, pour l'export CSV (D6). */
	private final Object[][] resultatsVisites = new Object[2][];
	private final PanneauParametres parametres = new PanneauParametres();
	private final JLabel etatPixel = new JLabel(" ");
	private final JLabel resume = new JLabel(" ");
	private final JProgressBar progression = new JProgressBar();

	private final Path[] fichiers = new Path[2];
	private Path dossierFire;
	private JsonObject parametresDuCalcul;
	private String etapeSuivie;
	private boolean choixAvantManuel;
	private boolean calculEnCours;
	private boolean majPairesFire;

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
		JButton ouvrir = new JButton("Ouvrir deux visites…");
		ouvrir.setToolTipText("Choisir la visite 1 puis la visite 2 (ou les deux d'un coup). "
				+ "Pour FIRE, choisir A01_1 suffit : A01_2 est trouvée seule.");
		ouvrir.addActionListener(e -> choisirImages());
		pairesFire.setToolTipText("Paires du jeu FIRE trouvées dans Donnees/");
		pairesFire.setMaximumSize(new Dimension(170, 30));
		pairesFire.addActionListener(e -> paireFireChoisie());
		remplirPairesFire();
		affichage.setMaximumSize(new Dimension(230, 30));
		affichage.addActionListener(e -> {
			choixAvantManuel = false;
			disposerCentre();
		});
		JButton ajuster = new JButton("Voir toute l'image");
		ajuster.addActionListener(e -> panneauD.ajuster());
		suivre.setToolTipText("Affiche chaque étape dès qu'elle est calculée");
		exporter.addActionListener(e -> exporterEtapes());
		exporter.setEnabled(false);
		outils.add(ouvrir);
		outils.add(new JLabel("  ou paire FIRE : "));
		outils.add(pairesFire);
		outils.addSeparator();
		outils.add(new JLabel(" Affichage : "));
		outils.add(affichage);
		outils.add(ajuster);
		outils.add(suivre);
		outils.addSeparator();
		outils.add(exporter);

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
		explication.setText("Ouvrez deux visites d'un même œil, ou choisissez une paire FIRE dans la barre "
				+ "d'outils.\n\nLes deux images passent par la chaîne en même temps ; chaque étape apparaît "
				+ "dans la liste dès qu'elle est calculée.\n\nImage : molette = zoom, glisser = déplacer, "
				+ "double-clic = tout voir ; flèches ↑ ↓ dans la liste = changer d'étape.");
		JScrollPane defilListe = new JScrollPane(liste);
		defilListe.setBorder(BorderFactory.createTitledBorder("Étapes (durée visite 1 | visite 2)"));
		JScrollPane defilExpl = new JScrollPane(explication);
		defilExpl.setBorder(BorderFactory.createTitledBorder("Qu'est-ce que je regarde ?"));
		JSplitPane gauche = new JSplitPane(JSplitPane.VERTICAL_SPLIT, defilListe, defilExpl);
		gauche.setResizeWeight(0.55);
		gauche.setPreferredSize(new Dimension(440, 700));

		// --- centre : deux panneaux d'image
		choixAvant.addActionListener(e -> {
			if (choixAvant.hasFocus()) {
				choixAvantManuel = true;
			}
			rafraichirPanneaux();
		});
		enteteG.setFont(enteteG.getFont().deriveFont(Font.BOLD));
		enteteD.setFont(enteteD.getFont().deriveFont(Font.BOLD));
		avertissement.setForeground(new Color(150, 90, 0));
		avertissement.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		panneauG.barreEtat(t -> etatPixel.setText(t.isEmpty() ? " " : prefixeG() + t));
		panneauD.barreEtat(t -> etatPixel.setText(t.isEmpty() ? " " : prefixeD() + t));
		JPanel zoneCentre = new JPanel(new BorderLayout());
		zoneCentre.add(avertissement, BorderLayout.NORTH);
		zoneCentre.add(centre, BorderLayout.CENTER);
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
		defilParam.setBorder(BorderFactory.createTitledBorder("Paramètres, communs aux deux visites"));
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

		JSplitPane interieur = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, zoneCentre, droite);
		interieur.setResizeWeight(1);
		JSplitPane principal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gauche, interieur);
		principal.setDividerLocation(440); // fixe : sinon recalculé (et rétréci) à chaque changement du centre
		getContentPane().add(outils, BorderLayout.NORTH);
		getContentPane().add(principal, BorderLayout.CENTER);
		getContentPane().add(bas, BorderLayout.SOUTH);

		// glisser-déposer d'une ou deux images sur la fenêtre
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
					ouvrirFichiers(f);
					return !f.isEmpty();
				} catch (Exception e) {
					erreur("Glisser-déposer impossible : " + e.getMessage());
					return false;
				}
			}
		});

		setSize(1600, 920);
		setLocationRelativeTo(null);
	}

	// ------------------------------------------------------------------ ouverture

	/**
	 * Ouvre une paire de visites et lance la chaîne sur les deux.
	 *
	 * @param visite1 image de référence
	 * @param visite2 image de suivi, ou null pour une image seule
	 */
	public void ouvrir(Path visite1, Path visite2) {
		fichiers[0] = visite1;
		fichiers[1] = visite2;
		setTitle("Rétinopathie — visualiseur — " + visite1.getFileName()
				+ (visite2 != null ? "  |  " + visite2.getFileName() : ""));
		vue.reinitialiser();
		choixAvantManuel = false;
		disposerCentre();
		lancer();
	}

	private void choisirImages() {
		JFileChooser fc = new JFileChooser(fichiers[0] != null ? fichiers[0].getParent().toFile() : dossierDepart());
		fc.setDialogTitle("Visite 1 (référence) — ou sélectionnez les deux visites");
		fc.setMultiSelectionEnabled(true);
		fc.setFileFilter(new FileNameExtensionFilter("Images (jpg, png, tif)", "jpg", "jpeg", "png", "tif", "tiff"));
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			ouvrirFichiers(Arrays.asList(fc.getSelectedFiles()));
		}
	}

	/** Deux fichiers : visites 1 et 2 dans l'ordre des noms. Un fichier : partenaire FIRE, sinon on demande. */
	private void ouvrirFichiers(List<File> f) {
		if (f.isEmpty()) {
			return;
		}
		if (f.size() >= 2) {
			List<File> tri = new ArrayList<File>(f.subList(0, 2));
			tri.sort((a, b) -> a.getName().compareTo(b.getName()));
			ouvrir(tri.get(0).toPath(), tri.get(1).toPath());
			return;
		}
		Path un = f.get(0).toPath();
		Path partenaire = partenaireFire(un);
		if (partenaire != null) {
			boolean estDeux = un.getFileName().toString().matches(".+_2\\.\\w+");
			ouvrir(estDeux ? partenaire : un, estDeux ? un : partenaire);
			return;
		}
		JFileChooser fc = new JFileChooser(un.getParent().toFile());
		fc.setDialogTitle("Visite 2 (suivi) de " + un.getFileName() + " — Annuler pour une image seule");
		fc.setFileFilter(new FileNameExtensionFilter("Images (jpg, png, tif)", "jpg", "jpeg", "png", "tif", "tiff"));
		Path deux = fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().toPath() : null;
		ouvrir(un, deux);
	}

	/** @return pour X_1.ext, le fichier X_2.ext s'il existe (et inversement), sinon null */
	static Path partenaireFire(Path p) {
		Matcher m = NOM_FIRE.matcher(p.getFileName().toString());
		if (!m.matches()) {
			return null;
		}
		Path autre = p.resolveSibling(m.group(1) + "_" + ("1".equals(m.group(2)) ? "2" : "1") + "." + m.group(3));
		return Files.exists(autre) ? autre : null;
	}

	/** Cherche le dossier Images de FIRE sous Donnees/ et liste ses paires. */
	private void remplirPairesFire() {
		majPairesFire = true;
		pairesFire.addItem("—");
		Path donnees = Paths.get("Donnees");
		if (Files.isDirectory(donnees)) {
			try (Stream<Path> s = Files.walk(donnees, 5)) {
				dossierFire = s.filter(p -> p.getFileName().toString().equals("Images") && Files.isDirectory(p)
						&& Files.exists(p.resolve("A01_1.jpg"))).findFirst().orElse(null);
			} catch (IOException e) {
				dossierFire = null;
			}
		}
		if (dossierFire != null) {
			try (Stream<Path> s = Files.list(dossierFire)) {
				s.map(p -> p.getFileName().toString()).filter(n -> n.matches("[APS]\\d+_1\\.jpg"))
						.map(n -> n.substring(0, n.indexOf('_'))).sorted().forEach(pairesFire::addItem);
			} catch (IOException e) {
				// liste vide
			}
		}
		pairesFire.setEnabled(pairesFire.getItemCount() > 1);
		majPairesFire = false;
	}

	private void paireFireChoisie() {
		if (majPairesFire || dossierFire == null || pairesFire.getSelectedIndex() <= 0) {
			return;
		}
		String id = (String) pairesFire.getSelectedItem();
		ouvrir(dossierFire.resolve(id + "_1.jpg"), dossierFire.resolve(id + "_2.jpg"));
	}

	// ------------------------------------------------------------------ calcul

	private void lancer() {
		if (fichiers[0] == null || calculEnCours) {
			return;
		}
		JsonObject json;
		try {
			json = parametres.versJson();
		} catch (IllegalArgumentException e) {
			erreur(e.getMessage());
			return;
		}
		EtapePaire courante = liste.getSelectedValue();
		etapeSuivie = courante != null ? courante.nom : null;
		etapes.clear();
		modeleAvant.removeAllElements();
		parametresDuCalcul = json;
		resultatsVisites[0] = null;
		resultatsVisites[1] = null;
		calculEnCours = true;
		relancer.setEnabled(false);
		exporter.setEnabled(false);
		progression.setIndeterminate(true);
		progression.setVisible(true);
		resume.setText("Calcul en cours…");
		rafraichirPanneaux();
		new Calcul(fichiers.clone(), json).execute();
	}

	/** Résultat publié par le calcul : une étape d'une visite. */
	private static final class Publication {
		final int visite;
		final EtapeVisible etape;

		Publication(int visite, EtapeVisible etape) {
			this.visite = visite;
			this.etape = etape;
		}
	}

	/** Exécute la chaîne sur les deux visites en parallèle, hors du fil de l'interface. */
	private final class Calcul extends SwingWorker<String, Publication> {
		private final Path[] images;
		private final JsonObject json;

		Calcul(Path[] images, JsonObject json) {
			this.images = images;
			this.json = json;
		}

		@Override
		protected String doInBackground() throws Exception {
			long debut = System.nanoTime();
			int n = images[1] != null ? 2 : 1;
			ExecutorService pool = Executors.newFixedThreadPool(n);
			try {
				List<Future<String>> res = new ArrayList<Future<String>>();
				for (int k = 0; k < n; k++) {
					final int visite = k;
					res.add(pool.submit(() -> new Visite(visite, images[visite]).executer()));
				}
				StringBuilder t = new StringBuilder();
				for (Future<String> f : res) {
					t.append(f.get()).append("   ·   ");
				}
				return t + String.format(Locale.FRANCE, "total %.1f s", (System.nanoTime() - debut) / 1e9);
			} finally {
				pool.shutdownNow();
			}
		}

		/** La chaîne pour une visite : publie chaque étape avec son numéro et sa durée. */
		private final class Visite {
			private final int visite;
			private final Path image;
			private final Parametres params;
			private long top;
			private int numero;
			private ImageGris dernierFond;
			private Masque derniersVaisseaux;

			Visite(int visite, Path image) {
				this.visite = visite;
				this.image = image;
				// chaque visite lit ses paramètres dans sa propre copie : Parametres mémorise les clés
				// lues et n'est pas fait pour être partagé entre deux fils de calcul
				this.params = new Parametres(json.deepCopy());
			}

			String executer() throws IOException {
				top = System.nanoTime();
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
				Masque squelette = new AmincissementZhangSuen().executer(s.vaisseaux(), ctx, obs);
				DetecteurBifurcations.Resultat b = new DetecteurBifurcations().detecter(squelette, pr.masque(), ctx, obs);
				publier(EtapeVisible.points(++numero, POINTS, dernierFond, b.elague(), b.points(), 0, ecoule()));
				List<DescripteurZernike.Descripteur> d = new DescripteurZernike().decrire(pr.pretraitee().image(),
						b.points(), ctx);
				double rayon = params.reel("zernike.rayon") * params.reel("segmentation.facteurEchelle");
				publier(EtapeVisible.points(++numero, FENETRES, dernierFond, b.elague(), b.points(), rayon, ecoule()));
				resultatsVisites[visite] = new Object[] { b.points(), d, rayon };
				long croisements = b.points().stream().filter(p -> p.type() == Bifurcation.Type.CROISEMENT).count();
				return String.format(Locale.FRANCE,
						"%s : vaisseaux %.1f %% du champ, %d bifurcations, %d croisements (%d écartés au bord)",
						COURT[visite], 100.0 * s.vaisseaux().nombrePixelsActifs() / pr.masque().nombrePixelsActifs(),
						b.points().size() - croisements, croisements, b.ecartesAuBord());
			}

			private EtapeVisible convertir(String nom, ImageGris img, Masque masque) {
				double s = ecoule();
				int n = ++numero;
				if (ChainePretraitement.MASQUE.equals(nom)) {
					return EtapeVisible.champ(n, nom, img, masque, s);
				}
				if (SegmentationHessienne.GERMES.equals(nom)) {
					return EtapeVisible.superpose(n, nom, dernierFond, masque, new Color(230, 30, 30), s);
				}
				if (SegmentationHessienne.HYSTERESIS.equals(nom)) {
					return EtapeVisible.superpose(n, nom, dernierFond, masque, new Color(255, 190, 0), s);
				}
				if (AmincissementZhangSuen.ZHANG_SUEN.equals(nom) || AmincissementZhangSuen.MARCHES.equals(nom)
						|| DetecteurBifurcations.ELAGAGE.equals(nom)) {
					return EtapeVisible.squelette(n, nom, dernierFond, derniersVaisseaux, masque, s);
				}
				if (SegmentationHessienne.VAISSEAUX.equals(nom)) {
					derniersVaisseaux = masque;
					return EtapeVisible.superpose(n, nom, img, masque, new Color(20, 200, 50), s);
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
				publish(new Publication(visite, e));
			}
		}

		@Override
		protected void process(List<Publication> nouvelles) {
			EtapePaire choisie = liste.getSelectedValue();
			boolean rafraichir = false;
			for (Publication p : nouvelles) {
				int n = p.etape.numero();
				boolean nouvelle = n > etapes.size();
				if (nouvelle) {
					EtapePaire e = new EtapePaire(n, p.etape.nom());
					etapes.addElement(e);
					modeleAvant.addElement(e);
				}
				EtapePaire e = etapes.get(n - 1);
				e.visites[p.visite] = p.etape;
				rafraichir |= e == choisie;
				if (nouvelle && (suivre.isSelected() || e.nom.equals(etapeSuivie) || etapes.size() == 1)) {
					liste.setSelectedIndex(n - 1);
					liste.ensureIndexIsVisible(n - 1);
					rafraichir = false; // la sélection rafraîchit déjà
				}
			}
			liste.repaint(); // durées de la visite 2
			if (rafraichir) {
				rafraichirPanneaux();
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
				if (c instanceof ExecutionException && c.getCause() != null) {
					c = c.getCause();
				}
				resume.setText("Échec : " + c.getMessage());
				erreur(c.getClass().getSimpleName() + " : " + c.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			rafraichirPanneaux();
		}
	}

	// ------------------------------------------------------------------ affichage

	private int modeAffichage() {
		return fichiers[1] == null ? 1 : affichage.getSelectedIndex(); // image seule : avant | après
	}

	private int visiteAvantApres() {
		return modeAffichage() == 2 ? 1 : 0;
	}

	private void etapeChoisie() {
		EtapePaire e = liste.getSelectedValue();
		if (e != null) {
			explication.setText(Explications.pour(e.nom));
			explication.setCaretPosition(0);
			if (!choixAvantManuel) {
				int i = liste.getSelectedIndex();
				choixAvant.setSelectedItem(i > 0 ? etapes.get(i - 1) : e);
			}
		}
		rafraichirPanneaux();
	}

	/** Met les bonnes étapes dans les deux panneaux selon l'affichage choisi. */
	private void rafraichirPanneaux() {
		EtapePaire sel = liste.getSelectedValue();
		String attente = calculEnCours ? "calcul en cours…" : "";
		if (fichiers[0] == null) {
			panneauG.afficher(null, "", "Ouvrez deux visites, ou choisissez une paire FIRE");
			panneauD.afficher(null, "", "");
			return;
		}
		if (modeAffichage() == 0) {
			for (int k = 0; k < 2; k++) {
				PanneauImage p = k == 0 ? panneauG : panneauD;
				EtapeVisible v = sel != null ? sel.visites[k] : null;
				p.afficher(v, sel != null ? COURT[k] + " · " + sel.numero + ". " + sel.nom : "",
						COURT[k] + " : " + attente);
			}
		} else {
			int k = visiteAvantApres();
			EtapePaire avant = (EtapePaire) choixAvant.getSelectedItem();
			panneauG.afficher(avant != null ? avant.visites[k] : null,
					avant != null ? "Avant · " + avant.numero + ". " + avant.nom : "", attente);
			panneauD.afficher(sel != null ? sel.visites[k] : null,
					sel != null ? "Après · " + sel.numero + ". " + sel.nom : "", attente);
		}
	}

	private void disposerCentre() {
		centre.removeAll();
		hautG.removeAll();
		int mode = modeAffichage();
		if (mode == 0) {
			enteteG.setText(" " + VISITE[0] + " — " + nomFichier(0));
			enteteD.setText(" " + VISITE[1] + " — " + nomFichier(1));
			hautG.add(enteteG, BorderLayout.CENTER);
			avertissement.setText("Visites pas encore recalées (Point 3) : même position en pixels, "
					+ "pas forcément même endroit de la rétine.");
		} else {
			int k = visiteAvantApres();
			enteteG.setText(" Avant :");
			enteteD.setText(" Après (étape de la liste) — " + COURT[k] + " — " + nomFichier(k));
			hautG.add(enteteG, BorderLayout.WEST);
			hautG.add(choixAvant, BorderLayout.CENTER);
			avertissement.setText(fichiers[1] == null ? "Image seule : affichage avant | après." : " ");
		}
		JPanel g = new JPanel(new BorderLayout());
		g.add(hautG, BorderLayout.NORTH);
		g.add(panneauG, BorderLayout.CENTER);
		JPanel d = new JPanel(new BorderLayout());
		d.add(enteteD, BorderLayout.NORTH);
		d.add(panneauD, BorderLayout.CENTER);
		centre.add(g);
		centre.add(d);
		centre.revalidate();
		centre.repaint();
		affichage.setEnabled(fichiers[1] != null);
		rafraichirPanneaux();
		SwingUtilities.invokeLater(panneauD::ajuster); // la largeur des panneaux vient de changer
	}

	private String nomFichier(int k) {
		return fichiers[k] != null ? fichiers[k].getFileName().toString() : "—";
	}

	private String prefixeG() {
		return (modeAffichage() == 0 ? COURT[0] : "Avant") + " · ";
	}

	private String prefixeD() {
		return (modeAffichage() == 0 ? COURT[1] : "Après") + " · ";
	}

	// ------------------------------------------------------------------ fichiers

	private void exporterEtapes() {
		if (etapes.isEmpty()) {
			return;
		}
		String nom = fichiers[0].getFileName().toString().replaceFirst("\\.[^.]+$", "").replaceFirst("_1$", "");
		Path dossier = Paths.get("executions",
				new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-visualiseur-" + nom);
		List<Object[]> aEcrire = new ArrayList<Object[]>();
		for (int i = 0; i < etapes.size(); i++) {
			EtapePaire e = etapes.get(i);
			for (int k = 0; k < 2; k++) {
				if (e.visites[k] != null) {
					aEcrire.add(new Object[] {
							String.format(Locale.ROOT, "v%d_%02d_%s.png", k + 1, e.numero, nomPourFichier(e.nom)),
							e.visites[k] });
				}
			}
		}
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(parametresDuCalcul);
		Object[][] resultats = { resultatsVisites[0], resultatsVisites[1] };
		Path[] sources = fichiers.clone();
		String texteResume = "Visite 1 : " + fichiers[0] + "\nVisite 2 : " + (fichiers[1] != null ? fichiers[1] : "—")
				+ "\n" + resume.getText() + "\n";
		exporter.setEnabled(false);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				Files.createDirectories(dossier);
				for (Object[] o : aEcrire) {
					ImageIO.write(((EtapeVisible) o[1]).rendu(), "png", dossier.resolve((String) o[0]).toFile());
				}
				Files.write(dossier.resolve("parametres_utilises.json"), json.getBytes(StandardCharsets.UTF_8));
				for (int k = 0; k < 2; k++) {
					if (resultats[k] == null || sources[k] == null) {
						continue;
					}
					// format d'échange D6 : <paire>_<visite>.csv (ex. A01_1), comme le nom de l'image FIRE
					String base = sources[k].getFileName().toString().replaceFirst("\\.[^.]+$", "");
					@SuppressWarnings("unchecked")
					List<Bifurcation> pts = (List<Bifurcation>) resultats[k][0];
					@SuppressWarnings("unchecked")
					List<DescripteurZernike.Descripteur> desc = (List<DescripteurZernike.Descripteur>) resultats[k][1];
					long r = Math.round((Double) resultats[k][2]);
					Bifurcation.ecrireCsv(dossier.resolve("bifurcations").resolve(base + ".csv"), pts);
					DescripteurZernike.ecrireCsv(dossier.resolve("descripteurs").resolve(base + "_R" + r + ".csv"), desc);
				}
				Files.write(dossier.resolve("resume.txt"), texteResume.getBytes(StandardCharsets.UTF_8));
				return null;
			}

			@Override
			protected void done() {
				exporter.setEnabled(true);
				try {
					get();
					JOptionPane.showMessageDialog(VisualiseurChaine.this,
							aEcrire.size() + " images, les paramètres et les CSV (bifurcations, descripteurs)"
									+ " enregistrés dans\n" + dossier.toAbsolutePath(),
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

	private File dossierDepart() {
		if (dossierFire != null) {
			return dossierFire.toFile();
		}
		File d = new File("Donnees");
		return d.isDirectory() ? d : new File(".");
	}

	private static String nomPourFichier(String nom) {
		String s = java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		s = s.replace("σ", "sigma").replaceAll("[^A-Za-z0-9.]+", "_").replaceAll("^_+|_+$", "");
		return s.length() > 60 ? s.substring(0, 60) : s;
	}

	private void erreur(String message) {
		JOptionPane.showMessageDialog(this, message, "Visualiseur", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Thème clair Nimbus, quel que soit le thème du bureau : l'apparence « système » suit un
	 * bureau sombre (GTK) et rend toute la fenêtre noire. À appeler avant de créer la fenêtre.
	 */
	public static void appliquerThemeClair() {
		try {
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {
			try {
				UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); // Metal, clair aussi
			} catch (Exception ignore) {
				// apparence par défaut
			}
		}
	}

	/**
	 * Point d'entrée de l'application seule.
	 *
	 * @param args facultatif : visite 1, puis visite 2 (pour FIRE, la visite 1 suffit)
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			appliquerThemeClair();
			VisualiseurChaine v = new VisualiseurChaine(true);
			v.setVisible(true);
			if (args.length > 0) {
				List<File> f = new ArrayList<File>();
				for (String a : args) {
					f.add(new File(a));
				}
				v.ouvrirFichiers(f);
			}
		});
	}
}
