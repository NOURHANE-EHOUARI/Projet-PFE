package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.service.validation.ValidationService; // ✅ AJOUTÉ : Import du service de validation
import ma.ensa.pfe.service.validation.ValidationSummary;  // ✅ AJOUTÉ : Import du résumé de validation
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PlanificationService {

    @Autowired private EtudiantRepository       etudiantRepository;
    @Autowired private ProfesseurRepository      professeurRepository;
    @Autowired private SalleRepository           salleRepository;
    @Autowired private SoutenanceRepository      soutenanceRepository;
    @Autowired private ContrainteRepository      contrainteRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    // ✅ Injection de AffectationService pour traiter les étudiants sans encadrant
    @Autowired private AffectationService affectationService;
    
    // ✅ AJOUTÉ : Injection du service de validation
    @Autowired private ValidationService validationService;

    private static final int DUREE_SOUTENANCE = 45;
    private static final int PAUSE_SALLE      = 15;
    private static final int DELAI_PROF       = 60;
    private static final int INTERVALLE       = DUREE_SOUTENANCE + PAUSE_SALLE;

    private static final LocalTime DEBUT_MATIN = LocalTime.of(9,  0);
    private static final LocalTime FIN_MATIN   = LocalTime.of(12, 0);
    private static final LocalTime DEBUT_APRES = LocalTime.of(14,  0);
    private static final LocalTime FIN_APRES   = LocalTime.of(18,  0);

    private Map<Long, List<Soutenance>> cacheSoutenancesParJour;
    private Map<Long, Long>             cacheChargeProf;
    private Map<Long, List<Contrainte>> cacheContraintes;

    // ══════════════════════════════════════════════════════════════════════
    //  POINT D'ENTRÉE — GÉNÉRATION COMPLÈTE
    // ══════════════════════════════════════════════════════════════════════
    public String genererPlanningComplet(List<LocalDate> joursDisponibles) {

        if (joursDisponibles == null || joursDisponibles.isEmpty()) {
            joursDisponibles = List.of(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(3));
        }
        if (joursDisponibles.size() > 3)
            joursDisponibles = joursDisponibles.subList(0, 3);

        // ✅ CORRECTION : affecter automatiquement les étudiants sans encadrant
        // (cas des étudiants ajoutés manuellement via le formulaire)
        long sansEncadrant = etudiantRepository.findAll().stream()
            .filter(e -> e.getEncadrant() == null).count();
        if (sansEncadrant > 0) {
            System.out.printf(
                "⚠️  %d étudiant(s) sans encadrant détecté(s) — affectation automatique...%n",
                sansEncadrant);
            AffectationService.AffectationResult affResult =
                affectationService.affecterEncadrants();
            System.out.printf("   ✅ %d encadrant(s) affecté(s) automatiquement%n",
                affResult.getNbAffectes());
            if (affResult.hasErreurs()) {
                affResult.getErreurs().forEach(e ->
                    System.out.println("   ⚠️  " + e));
            }
        }

        VersionPlanning version = new VersionPlanning();
        version.setDateGeneration(LocalDateTime.now());
        version.setDescription("Génération – " + LocalDateTime.now());
        version = versionPlanningRepository.save(version);

        cacheSoutenancesParJour = new HashMap<>();
        cacheChargeProf         = new HashMap<>();
        cacheContraintes        = contrainteRepository.findAll().stream()
            .collect(Collectors.groupingBy(c -> c.getProfesseur().getId()));

        // Recharger les étudiants APRÈS l'affectation automatique
        List<Etudiant>   etudiants   = etudiantRepository.findAll();
        List<Professeur> professeurs = professeurRepository.findAll();
        List<Salle>      salles      = salleRepository.findAll();

        try {
            return executer(etudiants, professeurs, salles, joursDisponibles, version);
        } catch (IllegalStateException e) {
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Erreur inattendue : " + e.getMessage();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RECALCUL DU JURY QUAND LA LANGUE D'UN ÉTUDIANT CHANGE
    // ══════════════════════════════════════════════════════════════════════
    public String recalculerJuryPourEtudiant(Long etudiantId, Etudiant.Langue ancienneLangue) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId).orElse(null);
        if (etudiant == null) return "Étudiant introuvable.";

        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();
        if (derniereVersion == null) return "Aucun planning généré.";

        Soutenance soutenance = soutenanceRepository
                .findByVersion(derniereVersion).stream()
                .filter(s -> s.getEtudiant().getId().equals(etudiantId))
                .findFirst().orElse(null);

        if (soutenance == null) return "Aucune soutenance trouvée pour cet étudiant.";

        boolean exigeAnglais = etudiant.getLangue() == Etudiant.Langue.EN;
        List<Professeur> tousProfs = professeurRepository.findAll();

        cacheSoutenancesParJour = new HashMap<>();
        cacheChargeProf         = new HashMap<>();
        cacheContraintes        = contrainteRepository.findAll().stream()
                .collect(Collectors.groupingBy(c -> c.getProfesseur().getId()));

        LocalDate jour = soutenance.getDate();
        List<Soutenance> soutenancesDuJour = soutenanceRepository
                .findByVersion(derniereVersion).stream()
                .filter(s -> s.getDate().equals(jour)
                          && !s.getId().equals(soutenance.getId()))
                .collect(Collectors.toList());
        cacheSoutenancesParJour.put(jour.toEpochDay(), new ArrayList<>(soutenancesDuJour));

        Professeur encadrant = soutenance.getEncadrant();
        ListeJury nouveauJury = choisirJury(
                encadrant, tousProfs,
                soutenance.getDate(), soutenance.getHeure(),
                etudiant.getFiliere(), exigeAnglais);

        if (nouveauJury == null)
            return "Impossible de trouver un jury valide pour la nouvelle langue.";

        soutenance.setJury1(nouveauJury.j1());
        soutenance.setJury2(nouveauJury.j2());
        soutenance.setJury3(nouveauJury.j3());
        soutenanceRepository.save(soutenance);

        return "Jury mis à jour pour " + etudiant.getNom() + " "
                + etudiant.getPrenom() + " (langue : " + etudiant.getLangue() + ").";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXÉCUTION DE LA PLANIFICATION
    // ══════════════════════════════════════════════════════════════════════
    private String executer(List<Etudiant> etudiants, List<Professeur> professeurs,
                             List<Salle> salles, List<LocalDate> jours,
                             VersionPlanning version) {

        validerPrerequis(etudiants, professeurs, salles);

        List<LocalTime> creneaux = genererCreneaux();
        afficherDiagnosticComplet(etudiants, creneaux, jours);

        System.out.printf("%n📅 %d jours × %d créneaux × %d salles = %d places pour %d étudiants%n",
            jours.size(), creneaux.size(), salles.size(),
            jours.size() * creneaux.size() * salles.size(), etudiants.size());

        List<Etudiant> melanges = melangerParFiliere(etudiants);

        int succes = 0, echecs = 0;
        Map<String, List<String>> rapportEchecs = new LinkedHashMap<>();

        for (Etudiant etudiant : melanges) {
            ResultatPlacement res = placerEtudiant(
                etudiant, professeurs, salles, jours, creneaux, version);

            if (res.planifie()) {
                succes++;
                ajouterAuCache(res.soutenance());
            } else {
                echecs++;
                rapportEchecs
                    .computeIfAbsent(res.raison(), k -> new ArrayList<>())
                    .add(etudiant.getNom() + " " + etudiant.getPrenom()
                         + " (" + etudiant.getFiliere() + ")");
            }
        }

        if (!rapportEchecs.isEmpty()) {
            System.out.println("\n⚠️  RAPPORT D'ÉCHECS :");
            rapportEchecs.forEach((cause, noms) -> {
                System.out.printf("  ❌ %s [%d cas]%n", cause, noms.size());
                noms.forEach(n -> System.out.println("     • " + n));
            });
        }

        String msg = String.format(
            "✅ %d soutenances planifiées sur %d étudiants.", succes, etudiants.size());
        if (echecs > 0) {
            String premiereCause = rapportEchecs.keySet().iterator().next();
            int nb = rapportEchecs.get(premiereCause).size();
            msg += String.format(" ⚠️ %d non planifiés. Cause : %s (%d cas).",
                echecs, premiereCause, nb);
        }
        
        // ══════════════════════════════════════════════════════════════════════
        //  VALIDATION POST-GÉNÉRATION (NOUVELLE FONCTIONNALITÉ)
        // ══════════════════════════════════════════════════════════════════════
        try {
            List<Soutenance> soutenancesGenerees = soutenanceRepository.findByVersion(version);
            ValidationSummary validation = validationService.validatePlanning(soutenancesGenerees);
            
            if (!validation.isValid()) {
                System.out.println("\n⚠️  " + validation.getFormattedReport());
                // Optionnel : tu peux choisir de rejeter le planning si erreurs bloquantes
                // if (!validation.getErrors().isEmpty()) {
                //     throw new IllegalStateException("Validation échouée : " + validation.getFormattedReport());
                // }
            } else {
                System.out.println("\n✅ Planning validé sans anomalie détectée.");
            }
        } catch (Exception e) {
            System.err.println("⚠️  Erreur lors de la validation : " + e.getMessage());
            // La validation ne doit pas bloquer la génération → on log et on continue
        }
        // ══════════════════════════════════════════════════════════════════════
        
        return msg;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PLACEMENT D'UN ÉTUDIANT
    // ══════════════════════════════════════════════════════════════════════
    private ResultatPlacement placerEtudiant(Etudiant etudiant,
                                              List<Professeur> professeurs,
                                              List<Salle> salles,
                                              List<LocalDate> jours,
                                              List<LocalTime> creneaux,
                                              VersionPlanning version) {

        Professeur encadrant = etudiant.getEncadrant();
        if (encadrant == null)
            return ResultatPlacement.echec("Aucun encadrant défini");

        boolean enAnglais = etudiant.getLangue() == Etudiant.Langue.EN;
        String derniereRaison = "";

        for (LocalDate jour : jours) {
            for (LocalTime heure : creneaux) {

                if (encadrantOccupeMemeCreneauExact(encadrant, jour, heure)) {
                    derniereRaison = "Encadrant " + encadrant.getNom()
                        + " déjà occupé au créneau exact " + heure;
                    continue;
                }

                ListeJury jury = choisirJury(encadrant, professeurs, jour, heure,
                                              etudiant.getFiliere(), enAnglais);
                if (jury == null) {
                    derniereRaison = "Jury impossible (spécialité/langue/dispo) à " + heure;
                    continue;
                }

                Salle salleLibre = trouverSalleLibre(salles, jour, heure);
                if (salleLibre == null) {
                    derniereRaison = "Aucune salle disponible à " + heure + " le " + jour;
                    continue;
                }

                Soutenance s = new Soutenance();
                s.setVersion(version);
                s.setEtudiant(etudiant);
                s.setEncadrant(encadrant);
                s.setJury1(jury.j1());
                s.setJury2(jury.j2());
                s.setJury3(jury.j3());
                s.setSalle(salleLibre);
                s.setDate(jour);
                s.setHeure(heure);
                s.setDureeMn(DUREE_SOUTENANCE);

                soutenanceRepository.save(s);
                soutenanceRepository.flush();
                return ResultatPlacement.succes(s);
            }
        }

        return ResultatPlacement.echec(derniereRaison.isEmpty()
            ? "Aucun créneau disponible sur " + jours.size() + " jours"
            : derniereRaison);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VÉRIFICATION ENCADRANT — créneau exact uniquement
    // ══════════════════════════════════════════════════════════════════════
    private boolean encadrantOccupeMemeCreneauExact(Professeur encadrant,
                                                     LocalDate jour,
                                                     LocalTime heure) {
        for (Soutenance s : getCacheJour(jour)) {
            if (!implique(s, encadrant)) continue;
            if (s.getHeure().equals(heure)) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SÉLECTION DU JURY
    // ══════════════════════════════════════════════════════════════════════
    private ListeJury choisirJury(Professeur encadrant, List<Professeur> tousProfs,
                                   LocalDate jour, LocalTime heure,
                                   Etudiant.Filiere filiere, boolean exigeAnglais) {

        List<Professeur> candidats = tousProfs.stream()
            .filter(p -> !p.getId().equals(encadrant.getId()))
            .filter(p -> !estProfAnglaisUniquement(p) || exigeAnglais)
            .filter(p -> estProfDisponible(p, jour, heure))
            .collect(Collectors.toList());

        Collections.shuffle(candidats);
        candidats.sort(Comparator.comparingLong(p -> chargeProf(p.getId())));

        // Mode normal : 2 profs GI minimum
        for (int i = 0; i < candidats.size(); i++) {
            for (int j = i + 1; j < candidats.size(); j++) {
                Professeur c1 = candidats.get(i);
                Professeur c2 = candidats.get(j);
                if (nbProfsGI(encadrant, c1, c2) < 2) continue;
                if (exigeAnglais && !anyAnglais(encadrant, c1, c2)) continue;
                return new ListeJury(encadrant, c1, c2);
            }
        }

        // Mode dégradé : 1 seul GI
        for (int i = 0; i < candidats.size(); i++) {
            for (int j = i + 1; j < candidats.size(); j++) {
                Professeur c1 = candidats.get(i);
                Professeur c2 = candidats.get(j);
                if (nbProfsGI(encadrant, c1, c2) < 1) continue;
                if (exigeAnglais && !anyAnglais(encadrant, c1, c2)) continue;
                return new ListeJury(encadrant, c1, c2);
            }
        }

        // Mode urgence
        if (candidats.size() >= 2) {
            Professeur c1 = candidats.get(0);
            Professeur c2 = candidats.get(1);
            if (!exigeAnglais || anyAnglais(encadrant, c1, c2))
                return new ListeJury(encadrant, c1, c2);
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DISPONIBILITÉ PROF
    // ══════════════════════════════════════════════════════════════════════
    public boolean estProfDisponible(Professeur prof, LocalDate jour, LocalTime heureDebut) {
        LocalTime heureFin = heureDebut.plusMinutes(DUREE_SOUTENANCE);

        for (Contrainte c : cacheContraintes.getOrDefault(prof.getId(), List.of())) {
            if (!c.getDateIndisponible().equals(jour)) continue;
            if (c.getHeureDebut() == null || c.getHeureFin() == null) return false;
            if (heureDebut.isBefore(c.getHeureFin()) && heureFin.isAfter(c.getHeureDebut()))
                return false;
        }

        for (Soutenance s : getCacheJour(jour)) {
            if (!implique(s, prof)) continue;
            LocalTime exDebut = s.getHeure();
            LocalTime exFin   = exDebut.plusMinutes(DUREE_SOUTENANCE);
            boolean nouvFinAvantEx = !heureFin.plusMinutes(DELAI_PROF).isAfter(exDebut);
            boolean exFinAvantNouv = !exFin.plusMinutes(DELAI_PROF).isAfter(heureDebut);
            if (!nouvFinAvantEx && !exFinAvantNouv) return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DISPONIBILITÉ SALLE
    // ══════════════════════════════════════════════════════════════════════
    public boolean estSalleLibre(Salle salle, LocalDate jour, LocalTime heureDebut) {
        LocalTime heureFin = heureDebut.plusMinutes(DUREE_SOUTENANCE);
        for (Soutenance s : getCacheJour(jour)) {
            if (!s.getSalle().getId().equals(salle.getId())) continue;
            LocalTime exDebut = s.getHeure();
            LocalTime exFin   = exDebut.plusMinutes(DUREE_SOUTENANCE);
            boolean nouvFinAvantEx = !heureFin.plusMinutes(PAUSE_SALLE).isAfter(exDebut);
            boolean exFinAvantNouv = !exFin.plusMinutes(PAUSE_SALLE).isAfter(heureDebut);
            if (!nouvFinAvantEx && !exFinAvantNouv) return false;
        }
        return true;
    }

    private Salle trouverSalleLibre(List<Salle> salles, LocalDate jour, LocalTime heure) {
        for (Salle salle : salles) {
            if (estSalleLibre(salle, jour, heure)) return salle;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GÉNÉRATION DES CRÉNEAUX
    // ══════════════════════════════════════════════════════════════════════
    private List<LocalTime> genererCreneaux() {
        List<LocalTime> cr = new ArrayList<>();
        LocalTime t = DEBUT_MATIN;
        while (!t.plusMinutes(DUREE_SOUTENANCE).isAfter(FIN_MATIN)) {
            cr.add(t); t = t.plusMinutes(INTERVALLE);
        }
        t = DEBUT_APRES;
        while (!t.plusMinutes(DUREE_SOUTENANCE).isAfter(FIN_APRES)) {
            cr.add(t); t = t.plusMinutes(INTERVALLE);
        }
        System.out.println("⏰ Créneaux générés : " + cr.size() + " → " + cr);
        return cr;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MÉLANGE INTER-FILIÈRES
    // ══════════════════════════════════════════════════════════════════════
    private List<Etudiant> melangerParFiliere(List<Etudiant> etudiants) {
        Map<Etudiant.Filiere, List<Etudiant>> map = etudiants.stream()
            .collect(Collectors.groupingBy(Etudiant::getFiliere));
        map.values().forEach(Collections::shuffle);

        List<Etudiant> res = new ArrayList<>();
        List<List<Etudiant>> groupes = new ArrayList<>(map.values());
        int max = groupes.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max; i++)
            for (List<Etudiant> g : groupes)
                if (i < g.size()) res.add(g.get(i));

        System.out.println("📊 Distribution filières :");
        map.forEach((f, l) -> System.out.printf("   %-6s : %d%n", f, l.size()));
        return res;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DIAGNOSTIC PRÉ-GÉNÉRATION
    // ══════════════════════════════════════════════════════════════════════
    private void afficherDiagnosticComplet(List<Etudiant> etudiants,
                                            List<LocalTime> creneaux,
                                            List<LocalDate> jours) {
        int capaciteMax = creneaux.size() * jours.size();
        System.out.println("══════════ DIAGNOSTIC PRÉ-GÉNÉRATION ══════════");
        System.out.printf("  Capacité max par encadrant : %d créneaux × %d jours = %d%n",
            creneaux.size(), jours.size(), capaciteMax);

        etudiants.stream()
            .filter(e -> e.getEncadrant() != null)
            .collect(Collectors.groupingBy(Etudiant::getEncadrant, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Professeur, Long>comparingByValue().reversed())
            .forEach(e -> {
                String etat = e.getValue() > capaciteMax ? "🔴 SURCHARGÉ" : "✅";
                System.out.printf("    %s %-25s : %d étudiant(s)%n",
                    etat, e.getKey().getNom(), e.getValue());
            });

        long sansEncadrant = etudiants.stream()
            .filter(e -> e.getEncadrant() == null).count();
        if (sansEncadrant > 0)
            System.out.printf("  🔴 %d étudiant(s) SANS encadrant%n", sansEncadrant);

        System.out.println("═══════════════════════════════════════════════");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VALIDATION DES PRÉREQUIS
    // ══════════════════════════════════════════════════════════════════════
    private void validerPrerequis(List<Etudiant> etudiants,
                                   List<Professeur> professeurs,
                                   List<Salle> salles) {
        if (etudiants.isEmpty())
            throw new IllegalStateException("Aucun étudiant importé.");
        if (professeurs.size() < 3)
            throw new IllegalStateException("Il faut au moins 3 professeurs.");
        if (salles.isEmpty())
            throw new IllegalStateException("Aucune salle définie.");
        long nbGI = professeurs.stream().filter(this::estProfGI).count();
        if (nbGI < 2)
            throw new IllegalStateException(
                "Il faut au moins 2 profs GI (actuellement " + nbGI + ").");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════════════════════════════
    private boolean estProfGI(Professeur p) {
        if (p.getSpecialite() == null) return false;
        String s = p.getSpecialite().trim().toUpperCase();
        return s.equals("GI") || s.contains("INFORMATIQUE")
            || s.contains("GÉNIE INFO") || s.contains("GENIE INFO");
    }

    private boolean estProfAnglaisUniquement(Professeur p) {
        return p.isParleAnglais()
            && p.getNom() != null
            && p.getNom().equalsIgnoreCase("BOUAZZA");
    }

    private long nbProfsGI(Professeur e, Professeur c1, Professeur c2) {
        return (estProfGI(e) ? 1 : 0)
             + (estProfGI(c1) ? 1 : 0)
             + (estProfGI(c2) ? 1 : 0);
    }

    private boolean anyAnglais(Professeur e, Professeur c1, Professeur c2) {
        return e.isParleAnglais() || c1.isParleAnglais() || c2.isParleAnglais();
    }

    private boolean implique(Soutenance s, Professeur prof) {
        Long pid = prof.getId();
        return s.getEncadrant().getId().equals(pid)
            || (s.getJury1() != null && s.getJury1().getId().equals(pid))
            || (s.getJury2() != null && s.getJury2().getId().equals(pid))
            || (s.getJury3() != null && s.getJury3().getId().equals(pid));
    }

    private List<Soutenance> getCacheJour(LocalDate jour) {
        return cacheSoutenancesParJour.computeIfAbsent(
            jour.toEpochDay(), k -> new ArrayList<>());
    }

    private void ajouterAuCache(Soutenance s) {
        getCacheJour(s.getDate()).add(s);
        incrementerCharge(s.getEncadrant().getId());
        if (s.getJury2() != null) incrementerCharge(s.getJury2().getId());
        if (s.getJury3() != null) incrementerCharge(s.getJury3().getId());
    }

    private long chargeProf(Long id) {
        return cacheChargeProf.getOrDefault(id, 0L);
    }

    private void incrementerCharge(Long id) {
        cacheChargeProf.merge(id, 1L, Long::sum);
    }

    public long compterSoutenances(Professeur prof, VersionPlanning version) {
        return soutenanceRepository.findByVersion(version).stream()
            .filter(s -> implique(s, prof)).count();
    }

    private record ListeJury(Professeur j1, Professeur j2, Professeur j3) {}

    private record ResultatPlacement(boolean planifie, Soutenance soutenance, String raison) {
        static ResultatPlacement succes(Soutenance s) {
            return new ResultatPlacement(true, s, "");
        }
        static ResultatPlacement echec(String r) {
            return new ResultatPlacement(false, null, r);
        }
    }

    public static class PlanificationException extends RuntimeException {
        public PlanificationException(String message) { super(message); }
    }
}