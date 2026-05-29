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

@Service
@Transactional
public class AffectationService {

    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private EtudiantRepository   etudiantRepository;
    @Autowired private NlpLangueService     nlpLangueService;


 
    // ══════════════════════════════════════════════
    //  RÉSULTAT D'AFFECTATION
    // ══════════════════════════════════════════════
    public static class AffectationResult {
        private int nbAffectes = 0;
        private int nbEchecs   = 0;
        private int nbNlpDetectes = 0;
        private final List<String> details = new ArrayList<>();
        private final List<String> erreurs = new ArrayList<>();

        public int getNbAffectes()       { return nbAffectes; }
        public int getNbEchecs()         { return nbEchecs; }
        public int getNbNlpDetectes()    { return nbNlpDetectes; }
        public List<String> getDetails() { return details; }
        public List<String> getErreurs() { return erreurs; }
        public boolean hasErreurs()      { return !erreurs.isEmpty(); }

        void incrAffectes()              { nbAffectes++; }
        void incrEchecs()                { nbEchecs++; }
        void incrNlpDetectes()           { nbNlpDetectes++; }
        void addDetail(String d)         { details.add(d); }
        void addErreur(String e)         { erreurs.add(e); }
    }

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE
    // ══════════════════════════════════════════════
    public AffectationResult affecterEncadrants() {
        AffectationResult result = new AffectationResult();

        List<Professeur> tousProfs = professeurRepository.findAll();
        if (tousProfs.isEmpty()) {
            result.addErreur("Aucun professeur en base. <a href='/pfe-planning/import' "
                    + "style='color:#fbbf24;text-decoration:underline;font-weight:600;'>"
                    + "Importez</a> d'abord le fichier Excel.");
            return result;
        }

        List<Etudiant> tousEtudiants = etudiantRepository.findAll();
        if (tousEtudiants.isEmpty()) {
            result.addErreur("Aucun étudiant en base. Importez d'abord le fichier Excel.");
            return result;
        }

        List<Professeur> profsEligibles = new ArrayList<>(tousProfs);

     
        System.out.println("══════ AFFECTATION - DIAGNOSTIC ══════");
        System.out.println("Profs éligibles : "
            + profsEligibles.stream()
                .map(p -> p.getNom() + " [" + p.getSpecialite() + "]")
                .collect(Collectors.joining(", ")));
        
        // Profs anglophones — pour affichage diagnostic
        List<Professeur> profsAnglais = tousProfs.stream()
                .filter(p -> Boolean.TRUE.equals(p.getParleAnglais()))
                .collect(Collectors.toList());
        System.out.println("Profs anglophones : "
                + profsAnglais.stream()
                    .map(p -> p.getNom() + " [" + p.getSpecialite() + "]")
                    .collect(Collectors.joining(", ")));
        System.out.println("══════════════════════════════════════");

        Map<Long, Integer> charge = new HashMap<>();
        tousProfs.forEach(p -> charge.put(p.getId(), 0));

        // ✅ Ne PAS écraser les encadrants choisis manuellement
        // Seuls les étudiants sans encadrant sont affectés automatiquement
        List<Etudiant> etudiants = tousEtudiants.stream()
                .filter(e -> e.getEncadrant() == null)
                .collect(Collectors.toList());

        System.out.printf("  → %d étudiant(s) sans encadrant à affecter automatiquement%n",
                etudiants.size());
        System.out.printf("  → %d étudiant(s) avec encadrant manuel conservé%n",
                tousEtudiants.size() - etudiants.size());

        if (etudiants.isEmpty()) {
            result.addDetail("✅ Tous les étudiants ont déjà un encadrant assigné.");
            return result;
        }

        Collections.shuffle(etudiants);

        // ── ÉTAPE NLP ────────────────────────────────────────────────────────
        System.out.println("══════ NLP - DÉTECTION LANGUE TITRE ══════");
        for (Etudiant etudiant : etudiants) {
            try {
                String titre = etudiant.getTitreProjet();
                if (titre != null && !titre.isBlank()) {
                    boolean titreEnAnglais = nlpLangueService.estAnglais(titre);
                    if (titreEnAnglais && etudiant.getLangue() != Langue.EN) {
                        etudiant.setLangue(Langue.EN);
                        etudiantRepository.save(etudiant);
                        result.incrNlpDetectes();
                        result.addDetail("🔍 NLP : \"" + titre
                                + "\" → langue EN pour "
                                + etudiant.getNom() + " " + etudiant.getPrenom());
                        System.out.println("[NLP] EN détecté → "
                                + etudiant.getNom() + " : " + titre);
                    }
                }
            } catch (Exception ex) {
                System.err.println("[NLP] Erreur ignorée pour "
                        + etudiant.getNom() + " : " + ex.getMessage());
            }
        }
        System.out.println("══════════════════════════════════════════");

        // ── AFFECTATION ENCADRANTS ────────────────────────────────────────────
        for (Etudiant etudiant : etudiants) {
            Professeur encadrant = choisirEncadrant(
            		etudiant.getFiliere(), profsEligibles, charge);

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
                        + " (" + etudiant.getFiliere() + ")");
            }
        }

        // ── AFFECTATION ÉQUITABLE PROFS ANGLOPHONES ───────────────────────────
        // Les profs anglophones sont affectés équitablement aux étudiants EN
        // qui n'ont pas encore de prof anglophone comme encadrant.
        // (L'encadrant reste celui affecté ci-dessus — ici on prépare
        //  la charge initiale pour que PlanificationService les distribue bien)
        affecterChargeAnglophones(profsAnglais, etudiants, result);

        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ✅ NOUVEAU : Répartition équitable des profs anglophones
    //  Principe : compter les étudiants EN et afficher le diagnostic
    //  La vraie répartition se fait dans PlanificationService via chargeProf
    // ══════════════════════════════════════════════════════════════════════════
    private void affecterChargeAnglophones(List<Professeur> profsAnglais,
                                            List<Etudiant> etudiants,
                                            AffectationResult result) {
        if (profsAnglais.isEmpty()) return;

        long nbEN = etudiants.stream()
                .filter(e -> e.getLangue() == Langue.EN)
                .count();

        if (nbEN == 0) return;

        System.out.println("══════ ANGLOPHONES - DIAGNOSTIC ══════");
        System.out.printf("  %d étudiant(s) EN pour %d prof(s) anglophone(s)%n",
                nbEN, profsAnglais.size());

        // Répartition théorique équitable
        long parProf = nbEN / profsAnglais.size();
        long reste   = nbEN % profsAnglais.size();
        System.out.printf("  Répartition idéale : %d par prof, %d en surplus%n",
                parProf, reste);

        for (int i = 0; i < profsAnglais.size(); i++) {
            long quota = parProf + (i < reste ? 1 : 0);
            System.out.printf("  %-25s → quota jury EN : %d%n",
                    profsAnglais.get(i).getNom(), quota);
        }
        System.out.println("═══════════════════════════════════════");

        result.addDetail(String.format(
                "📊 Profs anglophones : %d prof(s) pour %d soutenance(s) EN",
                profsAnglais.size(), nbEN));
    }

    // ══════════════════════════════════════════════
    //  LOGIQUE DE CHOIX ENCADRANT
    // ══════════════════════════════════════════════
    private Professeur choisirEncadrant(Filiere filiere,
            List<Professeur> profsEligibles,
            Map<Long, Integer> charge) {
      //  Tous les profs éligibles peuvent encadrer n'importe quelle filière
      // L'équité est assurée par la charge minimale
      return choisirAvecCharge(profsEligibles, charge);
    }

    // ══════════════════════════════════════════════
    //  CHOIX AVEC CHARGE ÉQUITABLE
    // ══════════════════════════════════════════════
    private Professeur choisirAvecCharge(List<Professeur> profs,
                                          Map<Long, Integer> charge) {
        if (profs == null || profs.isEmpty()) return null;

        int chargeMin = profs.stream()
                .mapToInt(p -> charge.getOrDefault(p.getId(), 0))
                .min().orElse(0);

        List<Professeur> profsChargeMin = profs.stream()
                .filter(p -> charge.getOrDefault(p.getId(), 0) == chargeMin)
                .collect(Collectors.toList());

        Collections.shuffle(profsChargeMin);
        return profsChargeMin.get(0);
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════


 

    private String normaliser(String s) {
        if (s == null) return "";
        return s.toUpperCase()
                .replace("É","E").replace("È","E").replace("Ê","E")
                .replace("À","A").replace("Â","A")
                .replace("Î","I").replace("Ô","O").replace("Û","U")
                .trim();
    }

    // ══════════════════════════════════════════════
    //  STATS (pour la vue)
    // ══════════════════════════════════════════════
    public Map<String, Long> getChargeEncadrants() {
        return etudiantRepository.findAll().stream()
                .filter(e -> e.getEncadrant() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEncadrant().getNom() + " " + e.getEncadrant().getPrenom(),
                        Collectors.counting()));
    }

    public long getNbEtudiantsSansEncadrant() {
        return etudiantRepository.findAll().stream()
                .filter(e -> e.getEncadrant() == null)
                .count();
    }
}