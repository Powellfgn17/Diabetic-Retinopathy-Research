package bj.epac.retine.plugin.visualiseur;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.JComponent;

/**
 * Affiche une étape avec zoom (molette, autour du curseur), déplacement (glisser) et
 * ajustement à la fenêtre (double-clic). Plusieurs panneaux partagent la même {@link Vue} :
 * zoomer dans l'un zoome dans l'autre, au même endroit.
 */
final class PanneauImage extends JComponent {

	private static final long serialVersionUID = 1L;

	/** Zoom et centre partagés entre panneaux, en coordonnées image. */
	static final class Vue {
		private double zoom = 0; // pixels écran par pixel image ; 0 = pas encore ajusté
		private double cx;
		private double cy;
		/** Vrai tant que l'utilisateur n'a ni zoomé ni déplacé : l'image suit alors la taille du panneau. */
		private boolean ajusteeAuto = true;
		private final List<Runnable> abonnes = new ArrayList<Runnable>();

		void abonner(Runnable r) {
			abonnes.add(r);
		}

		void changer(double zoom, double cx, double cy) {
			this.zoom = Math.max(0.02, Math.min(40, zoom));
			this.cx = cx;
			this.cy = cy;
			for (Runnable r : abonnes) {
				r.run();
			}
		}

		double zoom() {
			return zoom;
		}

		/** Oublie le zoom : la prochaine image affichée sera ajustée à la fenêtre. */
		void reinitialiser() {
			zoom = 0;
			ajusteeAuto = true;
		}
	}

	/** Couleurs du thème clair. */
	private static final Color FOND = new Color(236, 237, 241);
	private static final Color TEXTE = new Color(35, 35, 40);
	private static final Color TEXTE_DISCRET = new Color(110, 110, 120);
	private static final Color BANDEAU = new Color(255, 255, 255, 225);
	private static final Color BORDURE = new Color(190, 192, 200);

	private final Vue vue;
	private EtapeVisible etape;
	private String titre = "";
	private String siVide;
	private Consumer<String> barreEtat = s -> {
	};
	private Point dernier;

	PanneauImage(Vue vue, String siVide) {
		this.vue = vue;
		this.siVide = siVide;
		setPreferredSize(new Dimension(700, 700));
		setOpaque(true);
		vue.abonner(this::repaint);
		MouseAdapter souris = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dernier = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (dernier != null && vue.zoom > 0) {
					double dx = (e.getX() - dernier.x) / vue.zoom;
					double dy = (e.getY() - dernier.y) / vue.zoom;
					dernier = e.getPoint();
					vue.ajusteeAuto = false;
					vue.changer(vue.zoom, vue.cx - dx, vue.cy - dy);
				}
				signaler(e.getPoint());
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					ajuster();
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				signaler(e.getPoint());
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (etape == null || vue.zoom <= 0) {
					return;
				}
				double facteur = Math.pow(1.25, -e.getPreciseWheelRotation());
				double ix = versImageX(e.getX());
				double iy = versImageY(e.getY());
				double z = Math.max(0.02, Math.min(40, vue.zoom * facteur));
				vue.ajusteeAuto = false;
				// garder le pixel sous le curseur au même endroit
				vue.changer(z, ix - (e.getX() - getWidth() / 2.0) / z, iy - (e.getY() - getHeight() / 2.0) / z);
			}
		};
		addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				if (vue.ajusteeAuto) {
					ajuster();
				}
			}
		});
		addMouseListener(souris);
		addMouseMotionListener(souris);
		addMouseWheelListener(souris);
	}

	void barreEtat(Consumer<String> c) {
		this.barreEtat = c;
	}

	/**
	 * @param e      étape à afficher, ou null
	 * @param titre  texte du bandeau du haut
	 * @param siVide message affiché quand {@code e} est null
	 */
	void afficher(EtapeVisible e, String titre, String siVide) {
		this.etape = e;
		this.titre = titre;
		this.siVide = siVide;
		if (e != null && vue.zoom <= 0 && getWidth() > 0) {
			ajuster();
		}
		repaint();
	}

	EtapeVisible etape() {
		return etape;
	}

	/** Ajuste le zoom pour voir toute l'image. */
	void ajuster() {
		if (etape == null || getWidth() <= 0) {
			return;
		}
		double z = Math.min(getWidth() / (double) etape.largeur(), getHeight() / (double) etape.hauteur());
		vue.ajusteeAuto = true;
		vue.changer(z * 0.98, etape.largeur() / 2.0, etape.hauteur() / 2.0);
	}

	private double versImageX(int sx) {
		return vue.cx + (sx - getWidth() / 2.0) / vue.zoom;
	}

	private double versImageY(int sy) {
		return vue.cy + (sy - getHeight() / 2.0) / vue.zoom;
	}

	private void signaler(Point p) {
		if (etape == null || vue.zoom <= 0) {
			return;
		}
		int x = (int) Math.floor(versImageX(p.x));
		int y = (int) Math.floor(versImageY(p.y));
		String v = etape.valeurA(x, y);
		barreEtat.accept(v.isEmpty() ? "" : String.format(Locale.FRANCE, "x = %d   y = %d   %s", x, y, v));
	}

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0;
		g.setColor(FOND);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (etape == null) {
			texteCentre(g, siVide);
			return;
		}
		if (vue.zoom <= 0) {
			ajuster();
			if (vue.zoom <= 0) {
				return;
			}
		}
		BufferedImage img = etape.rendu();
		double z = vue.zoom;
		// rectangle source visible, borné à l'image
		double sx1 = Math.max(0, versImageX(0));
		double sy1 = Math.max(0, versImageY(0));
		double sx2 = Math.min(img.getWidth(), versImageX(getWidth()) + 1);
		double sy2 = Math.min(img.getHeight(), versImageY(getHeight()) + 1);
		if (sx2 > sx1 && sy2 > sy1) {
			int ix1 = (int) Math.floor(sx1);
			int iy1 = (int) Math.floor(sy1);
			int ix2 = (int) Math.ceil(sx2);
			int iy2 = (int) Math.ceil(sy2);
			int dx1 = (int) Math.round((ix1 - vue.cx) * z + getWidth() / 2.0);
			int dy1 = (int) Math.round((iy1 - vue.cy) * z + getHeight() / 2.0);
			int dx2 = (int) Math.round((ix2 - vue.cx) * z + getWidth() / 2.0);
			int dy2 = (int) Math.round((iy2 - vue.cy) * z + getHeight() / 2.0);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, z >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
					: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, dx1, dy1, dx2, dy2, ix1, iy1, ix2, iy2, null);
		}
		bandeau(g, titre, 8, true);
		String bas = String.format(Locale.FRANCE, "zoom %.0f %%", z * 100)
				+ (etape.echelle().isEmpty() ? "" : "   ·   " + etape.echelle());
		bandeau(g, bas, getHeight() - 8, false);
	}

	private void bandeau(Graphics2D g, String texte, int y, boolean enHaut) {
		g.setFont(getFont().deriveFont(enHaut ? Font.BOLD : Font.PLAIN, enHaut ? 14f : 12f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(texte) + 16;
		int h = fm.getHeight() + 6;
		int y0 = enHaut ? y : y - h;
		g.setColor(BANDEAU);
		g.fillRoundRect(8, y0, w, h, 8, 8);
		g.setColor(BORDURE);
		g.drawRoundRect(8, y0, w, h, 8, 8);
		g.setColor(TEXTE);
		g.drawString(texte, 16, y0 + 3 + fm.getAscent());
	}

	private void texteCentre(Graphics2D g, String texte) {
		g.setColor(TEXTE_DISCRET);
		g.setFont(getFont().deriveFont(Font.PLAIN, 15f));
		FontMetrics fm = g.getFontMetrics();
		g.drawString(texte, (getWidth() - fm.stringWidth(texte)) / 2, getHeight() / 2);
	}
}
