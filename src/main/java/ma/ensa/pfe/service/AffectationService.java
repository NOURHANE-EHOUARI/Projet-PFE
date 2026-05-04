package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 1 : AFFECTATION DES ENCADRANTS
 *
 * Règles STRICTES par filière :
 *
 *   GI   → encadrant discipline INFORMATIQUE ou GI uniquement
 *   TDIA → encadrant discipline INFORMATIQUE, GI ou MATHEMATIQUE
 *   DATA →  encadrant discipline MATHEMATIQUE uniquement
 *
 *   GESTION et ANGLAIS → ne peuvent jamais encadrer personne
 *
 * Répartition équitable (charge min) + aléatoire à charge égale.
 *
 * ── BONUS NLP ─────────────────────────────────────────────────────────────
 * Avant l'affectation, NlpLangueService analyse le titre de chaque étudiant.
 * Si le titre est détecté EN → la langue de l'étudiant est mise à EN
 * automatiquement, ce qui garantira que BOUAZZA sera dans le jury au planning.
 * Cette étape NLP est INDÉPENDANTE de la logique d'affectation existante.
 * ──────────────────────────────────────────────────────────────────────────
 */
@Service
@Transactional
public class AffectationService {

    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private EtudiantRepository   etudiantRepository;

    // ── AJOUT NLP : injecté séparément, n'affecte pas le reste ───────────
    @Autowired private NlpLangueService nlpLangueService;

    // Disciplines autorisées par filière
    private static final Set<String> DISCIPLINES_GI   =
            Set.of("GI", "INFORMATIQUE");

    private static final Set<String> DISCIPLINES_TDIA =
            Set.of("GI", "INFORMATIQUE", "MATHEMATIQUE");

    private static final Set<String> DISCIPLINES_DATA =
            Set.of("MATHEMATIQUE");

    // Disciplines jamais autorisées comme encadrant
    private static final Set<String> DISCIPLINES_EXCLUES =
            Set.of("GESTION", "ANGLAIS");

    // ══════════════════════════════════════════════
    //  RÉSULTAT D'AFFECTATION
    // ══════════════════════════════════════════════
    public static class AffectationResult {
        private int nbAffectes = 0;
        private int nbEchecs   = 0;
        // ── AJOUT NLP ──
        private int nbNlpDetectes = 0;
        private final List<String> details = new ArrayList<>();
        private final List<String> erreurs = new ArrayList<>();

        public int getNbAffectes()        { return nbAffectes; }
        public int getNbEchecs()          { return nbEchecs; }
        // ── AJOUT NLP ──
        public int getNbNlpDetectes()     { return nbNlpDetectes; }
        public List<String> getDetails()  { return details; }
        public List<String> getErreurs()  { return erreurs; }
        public boolean hasErreurs()       { return !erreurs.isEmpty(); }

        void incrAffectes()               { nbAffectes++; }
        void incrEchecs()                 { nbEchecs++; }
        // ── AJOUT NLP ──
        void incrNlpDetectes()            { nbNlpDetectes++; }
        void addDetail(String d)          { details.add(d); }
        void addErreur(String e)          { erreurs.add(e); }
    }

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE
    // ══════════════════════════════════════════════
    public AffectationResult affecterEncadrants() {
        AffectationResult result = new AffectationResult();

        List<Professeur> tousProfs = professeurRepository.findAll();
        if (tousProfs.isEmpty()) {
        	result.addErreur("Aucun professeur en base. <a href='/pfe-planning/import' style='color:#fbbf24;text-decoration:underline;font-weight:600;'>Importez</a> d'abord le fichier Excel.");
            return result;
        }

        List<Etudiant> tousEtudiants = etudiantRepository.findAll();
        if (tousEtudiants.isEmpty()) {
            result.addErreur("Aucun étudiant en base. Importez d'abord le fichier Excel.");
            return result;
        }

        // ── Construire les pools par discipline ────────────────────────────
        List<Professeur> profsInfo = tousProfs.stream()
                .filter(p -> estDiscipline(p, DISCIPLINES_GI))
                .collect(Collectors.toList());

        List<Professeur> profsMath = tousProfs.stream()
                .filter(p -> estDiscipline(p, DISCIPLINES_DATA))
                .collect(Collectors.toList());

        // Log de diagnostic
        System.out.println("══════ AFFECTATION — DIAGNOSTIC ══════");
        System.out.println("Profs Informatique/GI (pour GI & TDIA) : "
                + profsInfo.stream().map(Professeur::getNom).collect(Collectors.joining(", ")));
        System.out.println("Profs Mathématique (pour DATA & TDIA)  : "
                + profsMath.stream().map(Professeur::getNom).collect(Collectors.joining(", ")));
        System.out.println("Exclus (Gestion/Anglais) : "
                + tousProfs.stream()
                    .filter(p -> estDisciplineExclue(p))
                    .map(Professeur::getNom)
                    .collect(Collectors.joining(", ")));
        System.out.println("══════════════════════════════════════");

        // ── Compteur de charge par prof ────────────────────────────────────
        Map<Long, Integer> charge = new HashMap<>();
        tousProfs.forEach(p -> charge.put(p.getId(), 0));

        // ── Réinitialiser toutes les affectations ──────────────────────────
        for (Etudiant e : tousEtudiants) {
            e.setEncadrant(null);
        }

        // ── Mélanger les étudiants → ordre différent à chaque import ───────
        List<Etudiant> etudiants = new ArrayList<>(tousEtudiants);
        Collections.shuffle(etudiants);

        // ════════════════════════════════════════════════════════════════════
        //  ÉTAPE NLP (BONUS) — exécutée AVANT l'affectation, sans l'affecter
        //  Si titre EN détecté → langue mise à EN → BOUAZZA inclus dans jury
        // ════════════════════════════════════════════════════════════════════
        System.out.println("══════ NLP — DÉTECTION LANGUE TITRE ══════");
        for (Etudiant etudiant : etudiants) {
            try {
                String titre = etudiant.getTitreProjet();
                if (titre != null && !titre.isBlank()) {
                    boolean titreEnAnglais = nlpLangueService.estAnglais(titre);
                    if (titreEnAnglais && etudiant.getLangue() != Langue.EN) {
                        etudiant.setLangue(Langue.EN);
                        etudiantRepository.save(etudiant);
                        result.incrNlpDetectes();
                        result.addDetail("🔍 NLP : \""
                                + titre + "\" → langue auto-détectée EN pour "
                                + etudiant.getNom() + " " + etudiant.getPrenom());
                        System.out.println("[NLP] EN détecté → "
                                + etudiant.getNom() + " : " + titre);
                    }
                }
            } catch (Exception ex) {
                // NLP ne doit JAMAIS bloquer l'affectation
                System.err.println("[NLP] Erreur ignorée pour "
                        + etudiant.getNom() + " : " + ex.getMessage());
            }
        }
        System.out.println("══════════════════════════════════════════");
        // ════════════════════════════════════════════════════════════════════
        //  FIN ÉTAPE NLP
        // ════════════════════════════════════════════════════════════════════

        // ── Affecter chaque étudiant ────────────────────────────────────────
        // (code original — RIEN n'a changé ici)
        for (Etudiant etudiant : etudiants) {
            Professeur encadrant = choisirEncadrant(
                    etudiant.getFiliere(), profsInfo, profsMath, charge);

            if (encadrant != null) {
                etudiant.setEncadrant(encadrant);
                charge.merge(encadrant.getId(), 1, Integer::sum);
                etudiantRepository.save(etudiant);
                result.incrAffectes();
                result.addDetail(
                        etudiant.getNom() + " " + etudiant.getPrenom()
                        + " (" + etudiant.getFiliere() + ")"
                        + " → " + encadrant.getNom() + " " + encadrant.getPrenom()
                        + " [" + encadrant.getSpecialite() + "]");
            } else {
                result.incrEchecs();
                result.addErreur("Impossible d'affecter un encadrant à : "
                        + etudiant.getNom() + " " + etudiant.getPrenom()
                        + " (" + etudiant.getFiliere() + ")"
                        + " — aucun prof de la discipline requise disponible.");
            }
        }

        return result;
    }

    // ══════════════════════════════════════════════
    //  LOGIQUE DE CHOIX — RÈGLES STRICTES
    //  (code original — RIEN n'a changé)
    // ══════════════════════════════════════════════
    private Professeur choisirEncadrant(Filiere filiere,
                                         List<Professeur> profsInfo,
                                         List<Professeur> profsMath,
                                         Map<Long, Integer> charge) {
        return switch (filiere) {
            case GI -> {
                yield choisirAvecCharge(profsInfo, charge);
            }
            case TDIA -> {
                Professeur choix = choisirAvecCharge(profsInfo, charge);
                if (choix != null) yield choix;
                yield choisirAvecCharge(profsMath, charge);
            }
            case DATA -> {
                yield choisirAvecCharge(profsMath, charge);
            }
        };
    }

    private Professeur choisirAvecCharge(List<Professeur> profs,
                                          Map<Long, Integer> charge) {
        if (profs == null || profs.isEmpty()) return null;

        List<Professeur> melanges = new ArrayList<>(profs);
        Collections.shuffle(melanges);

        return melanges.stream()
                .min(Comparator.comparingInt(p -> charge.getOrDefault(p.getId(), 0)))
                .orElse(null);
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES DISCIPLINE
    //  (code original — RIEN n'a changé)
    // ══════════════════════════════════════════════
    private boolean estDiscipline(Professeur p, Set<String> disciplines) {
        if (p.getSpecialite() == null) return false;
        String sp = normaliser(p.getSpecialite());
        return disciplines.contains(sp);
    }

    private boolean estDisciplineExclue(Professeur p) {
        if (p.getSpecialite() == null) return false;
        String sp = normaliser(p.getSpecialite());
        return DISCIPLINES_EXCLUES.contains(sp);
    }

    private String normaliser(String s) {
        if (s == null) return "";
        return s.toUpperCase()
                .replace("É", "E").replace("È", "E").replace("Ê", "E")
                .replace("À", "A").replace("Â", "A")
                .replace("Î", "I").replace("Ô", "O").replace("Û", "U")
                .trim();
    }

    // ══════════════════════════════════════════════
    //  STATS (pour la vue)
    //  (code original — RIEN n'a changé)
    // ══════════════════════════════════════════════
    public Map<String, Long> getChargeEncadrants() {
        return etudiantRepository.findAll().stream()
                .filter(e -> e.getEncadrant() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEncadrant().getNom() + " " + e.getEncadrant().getPrenom(),
                        Collectors.counting()
                ));
    }

    public long getNbEtudiantsSansEncadrant() {
        return etudiantRepository.findAll().stream()
                .filter(e -> e.getEncadrant() == null)
                .count();
    }
}