package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.PlanningConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class ConfigService {

    @Autowired
    private PlanningConfigRepository configRepository;

    // ══════════════════════════════════════════════
    //  VALEURS PAR DÉFAUT (si clé absente en base)
    // ══════════════════════════════════════════════
    private static final int    DEFAULT_DUREE_SOUTENANCE      = 45;
    private static final int    DEFAULT_PAUSE_ENTRE_SOUTENANCES = 60;
    private static final int    DEFAULT_NB_JOURS_PLANNING      = 3;
    private static final String DEFAULT_HEURE_DEBUT_JOURNEE    = "08:30";
    private static final String DEFAULT_HEURE_FIN_JOURNEE      = "18:00";
    private static final int    DEFAULT_NB_JURY_MIN            = 3;
    private static final int    DEFAULT_NB_PROFS_SPECIALITE_MIN = 2;
    private static final int DEFAULT_PAUSE_SALLE = 15;

    // ══════════════════════════════════════════════
    //  GETTERS TYPÉS
    // ══════════════════════════════════════════════

    public int getDureeSoutenance() {
        return getInt("DUREE_SOUTENANCE", DEFAULT_DUREE_SOUTENANCE);
    }

    public int getPauseEntreSoutenances() {
        return getInt("PAUSE_ENTRE_SOUTENANCES", DEFAULT_PAUSE_ENTRE_SOUTENANCES);
    }

    public int getNbJoursPlanning() {
        return getInt("NB_JOURS_PLANNING", DEFAULT_NB_JOURS_PLANNING);
    }

    public LocalTime getHeureDebutJournee() {
        return getTime("HEURE_DEBUT_JOURNEE", DEFAULT_HEURE_DEBUT_JOURNEE);
    }

    public LocalTime getHeureFinJournee() { 
        String valeur = configRepository.findByCle("HEURE_FIN_JOURNEE")
                .map(c -> c.getValeur().trim())
                .orElse("NON_TROUVEE");
        System.out.println("[CONFIG] HEURE_FIN_JOURNEE lue : " + valeur);
        return getTime("HEURE_FIN_JOURNEE", DEFAULT_HEURE_FIN_JOURNEE);
    }

    public int getNbJuryMin() {
        return getInt("NB_JURY_MIN", DEFAULT_NB_JURY_MIN);
    }

    public int getNbProfsSpecialiteMin() {
        return getInt("NB_PROFS_SPECIALITE_MIN", DEFAULT_NB_PROFS_SPECIALITE_MIN);
    }
    public int getPauseSalle() {
        return getInt("PAUSE_SALLE", DEFAULT_PAUSE_SALLE);
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES PRIVÉS
    // ══════════════════════════════════════════════

    private int getInt(String cle, int defaut) {
        return configRepository.findByCle(cle)
                .map(c -> {
                    try { return Integer.parseInt(c.getValeur().trim()); }
                    catch (NumberFormatException e) { return defaut; }
                })
                .orElse(defaut);
    }

    private LocalTime getTime(String cle, String defaut) {
        String valeur = configRepository.findByCle(cle)
                .map(c -> c.getValeur().trim())
                .orElse(defaut);
        try { return LocalTime.parse(valeur); }
        catch (Exception e) { return LocalTime.parse(defaut); }
    }
}