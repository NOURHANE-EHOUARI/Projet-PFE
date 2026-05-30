package ma.ensa.pfe.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class NlpLangueService {

    
    private static final Set<String> MOTS_ANGLAIS = Set.of(
       
        "the", "a", "an", "of", "for", "in", "on", "with", "and", "or",
        "to", "by", "from", "at", "using", "via",
        "smart", "deep", "machine", "learning", "detection", "recognition",
        "classification", "prediction", "analysis", "management", "system",
        "design", "development", "implementation", "optimization",
        "monitoring", "tracking", "processing", "generation", "automated",
        "real", "time", "web", "mobile", "cloud", "data",
        "network", "security", "intelligence", "artificial", "neural",
        "image", "object", "natural", "language", "speech", "text",
        "autonomous", "platform", "framework", "application", "approach",
        "enhanced", "efficient", "multi", "driven", "enabled", "based",
        "iot", "api", "nlp", "cnn", "rnn", "lstm", "bert", "gpt",
        "blockchain", "microservices", "chatbot", "recommendation",
        "dashboard", "workflow", "pipeline"
    );

  
    private static final Set<String> MOTS_FRANCAIS = Set.of(
        
        "le", "la", "les", "un", "une", "des", "du", "de", "et", "ou",
        "pour", "avec", "sur", "par", "dans", "vers", "entre",
        "gestion", "systeme", "developpement", "conception", "analyse",
        "detection", "reconnaissance", "classification", "prediction",
        "optimisation", "automatique", "automatisation", "plateforme",
        "application", "suivi", "surveillance", "traitement", "generation",
        "intelligent", "intelligente", "temps", "reel", "base", "donnees",
        "reseau", "securite", "numerique",
        "apprentissage", "profond", "artificielle", "naturel", "langage",
        "vision", "parole", "texte", "image", "objet", "recommandation",
        "tableau", "bord", "flux", "travail", "approche"
    );

    public String detecterLangue(String titre) {
        if (titre == null || titre.isBlank()) return "FR";

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

    
    public boolean estAnglais(String titre) {
        return "EN".equals(detecterLangue(titre));
    }


    private String supprimerAccents(String s) {
        return s.replace("é", "e").replace("è", "e").replace("ê", "e")
                .replace("à", "a").replace("â", "a")
                .replace("î", "i").replace("ï", "i")
                .replace("ô", "o").replace("ù", "u").replace("û", "u")
                .replace("ç", "c");
    }
}