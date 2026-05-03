package ma.ensa.pfe.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Service NLP de détection de langue FR / EN
 * basé sur des listes de mots-clés caractéristiques des titres de PFE.
 *
 * Résultat : "EN" si le titre est majoritairement anglais, "FR" sinon.
 */
@Service
public class NlpLangueService {

    // ── Mots-clés anglais fréquents dans les titres de PFE ───────────────
    private static final Set<String> MOTS_ANGLAIS = Set.of(
        // Articles / prépositions
        "the", "a", "an", "of", "for", "in", "on", "with", "and", "or",
        "to", "by", "from", "at", "using", "via",
        // Adjectifs / verbes courants
        "smart", "deep", "machine", "learning", "detection", "recognition",
        "classification", "prediction", "analysis", "management", "system",
        "design", "development", "implementation", "optimization",
        "monitoring", "tracking", "processing", "generation", "automated",
        "real", "time", "web", "mobile", "cloud", "data",
        "network", "security", "intelligence", "artificial", "neural",
        "image", "object", "natural", "language", "speech", "text",
        "autonomous", "platform", "framework", "application", "approach",
        "enhanced", "efficient", "multi", "driven", "enabled", "based",
        // Domaines techniques
        "iot", "api", "nlp", "cnn", "rnn", "lstm", "bert", "gpt",
        "blockchain", "microservices", "chatbot", "recommendation",
        "dashboard", "workflow", "pipeline"
    );

    // ── Mots-clés français fréquents dans les titres de PFE ─────────────
    private static final Set<String> MOTS_FRANCAIS = Set.of(
        // Articles / prépositions
        "le", "la", "les", "un", "une", "des", "du", "de", "et", "ou",
        "pour", "avec", "sur", "par", "dans", "vers", "entre",
        // Adjectifs / verbes courants
        "gestion", "systeme", "developpement", "conception", "analyse",
        "detection", "reconnaissance", "classification", "prediction",
        "optimisation", "automatique", "automatisation", "plateforme",
        "application", "suivi", "surveillance", "traitement", "generation",
        "intelligent", "intelligente", "temps", "reel", "base", "donnees",
        "reseau", "securite", "numerique",
        // Domaines académiques FR
        "apprentissage", "profond", "artificielle", "naturel", "langage",
        "vision", "parole", "texte", "image", "objet", "recommandation",
        "tableau", "bord", "flux", "travail", "approche"
    );

    /**
     * Détecte la langue d'un titre de projet.
     *
     * @param titre le titre du projet (peut être null ou vide)
     * @return "EN" si anglais détecté, "FR" sinon
     */
    public String detecterLangue(String titre) {
        if (titre == null || titre.isBlank()) return "FR";

        // Tokeniser : séparer sur espaces, tirets, underscores, ponctuation
        // Supprimer accents pour la comparaison FR
        String titreNorm = supprimerAccents(titre.toLowerCase(Locale.ROOT));
        String[] tokens  = titreNorm.split("[\\s\\-_/:;,.()'\"]+");

        int scoreEN = 0;
        int scoreFR = 0;

        for (String token : tokens) {
            String t = token.trim();
            if (t.length() < 2) continue;
            if (MOTS_ANGLAIS.contains(t))  scoreEN++;
            if (MOTS_FRANCAIS.contains(t)) scoreFR++;
        }

        System.out.printf("[NLP] Titre: \"%s\" → scoreEN=%d scoreFR=%d → %s%n",
                titre, scoreEN, scoreFR, scoreEN > scoreFR ? "EN" : "FR");

        return scoreEN > scoreFR ? "EN" : "FR";
    }

    /**
     * Détecte la langue et retourne true si anglais.
     */
    public boolean estAnglais(String titre) {
        return "EN".equals(detecterLangue(titre));
    }

    /**
     * Supprime les accents pour permettre la comparaison FR sans accents.
     */
    private String supprimerAccents(String s) {
        return s.replace("é", "e").replace("è", "e").replace("ê", "e")
                .replace("à", "a").replace("â", "a")
                .replace("î", "i").replace("ï", "i")
                .replace("ô", "o").replace("ù", "u").replace("û", "u")
                .replace("ç", "c");
    }
}