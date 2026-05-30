package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.service.validation.ValidationService;
import ma.ensa.pfe.service.validation.ValidationSummary;
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
    @Autowired private AffectationService        affectationService;
    @Autowired private ValidationService         validationService;
    @Autowired private ConfigService configService;
 
    private int dureeSoutenance;
    private int pauseSalle;
    private int delaiProf;
    private int intervalle;
    private LocalTime debutJournee;
    private LocalTime finJournee;
    
    private Map<Long, List<Soutenance>> cacheSoutenancesParJour;
    private Map<Long, Long>             cacheChargeProf;
    private Map<Long, List<Contrainte>> cacheContraintes;
    private Map<Long, Long> cacheChargeJury;

    // ══════════════════════════════════════════════════════════════════════
    //  POINT D'ENTRÉE — GÉNÉRATION COMPLÈTE
    // ══════════════════════════════════════════════════════════════════════
    private void chargerConfig() {
        this.dureeSoutenance = configService.getDureeSoutenance();
        this.delaiProf       = configService.getPauseEntreSoutenances();
        this.debutJournee    = configService.getHeureDebutJournee();
        this.finJournee      = configService.getHeureFinJournee();
        this.pauseSalle = configService.getPauseSalle();
        this.intervalle      = this.dureeSoutenance + this.pauseSalle;

        System.out.println(" CONFIG CHARGÉE :");
        System.out.printf("   Durée soutenance   : %d min%n", dureeSoutenance);
        System.out.printf("   Délai entre profs  : %d min%n", delaiProf);
        System.out.printf("   Début journée      : %s%n", debutJournee);
        System.out.printf("   Fin journée        : %s%n", finJournee);
    }
    
    public String genererPlanningComplet(List<LocalDate> joursDisponibles) {
    	chargerConfig();
        if (joursDisponibles == null || joursDisponibles.isEmpty()) {
            joursDisponibles = List.of(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(3));
        }
        if (joursDisponibles.size() > 3)
            joursDisponibles = joursDisponibles.subList(0, 3);

        long sansEncadrant = etudiantRepository.findAll().stream()
            .filter(e -> e.getEncadrant() == null).count();
        if (sansEncadrant > 0) {
            System.out.printf(
                "⚠️  %d étudiant(s) sans encadrant - affectation automatique...%n",
                sansEncadrant);
            AffectationService.AffectationResult affResult =
                affectationService.affecterEncadrants();
            System.out.printf("    %d encadrant(s) affecté(s)%n", affResult.getNbAffectes());
            if (affResult.hasErreurs())
                affResult.getErreurs().forEach(e -> System.out.println("   ⚠️  " + e));
        }

        VersionPlanning version = new VersionPlanning();
        version.setDateGeneration(LocalDateTime.now());
        version.setDescription("Génération – " + LocalDateTime.now());
        version = versionPlanningRepository.save(version);

        cacheSoutenancesParJour = new HashMap<>();
        cacheChargeProf         = new HashMap<>();
        cacheContraintes        = contrainteRepository.findAll().stream()
            .collect(Collectors.groupingBy(c -> c.getProfesseur().getId()));
        cacheChargeJury = new HashMap<>();
        etudiantRepository.findAll().stream()
        .filter(e -> e.getEncadrant() != null)
        .forEach(e -> cacheChargeJury.merge(e.getEncadrant().getId(), 1L, Long::sum));


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
    	chargerConfig();
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
    //  ✅ NOUVEAU : RECALCUL DES SOUTENANCES APRÈS AJOUT D'UNE INDISPONIBILITÉ
    //
    //  Appelé depuis ProfesseurService.ajouterContrainte() après sauvegarde.
    //  Pour chaque soutenance du prof dans la dernière version :
    //    - Si le créneau tombe dans la plage d'indisponibilité → chercher un autre créneau
    //    - Sinon → ne rien faire
    // ══════════════════════════════════════════════════════════════════════
    public String recalculerSoutenancesPourContrainte(Long professeurId) {
    	chargerConfig();
        Professeur prof = professeurRepository.findById(professeurId).orElse(null);
        if (prof == null) return "Professeur introuvable.";

        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();
        if (derniereVersion == null) return "Aucun planning généré.";

        // Charger toutes les contraintes à jour
        cacheContraintes = contrainteRepository.findAll().stream()
                .collect(Collectors.groupingBy(c -> c.getProfesseur().getId()));

        // Trouver toutes les soutenances où ce prof est impliqué
        List<Soutenance> soutenancesProf = soutenanceRepository
                .findByVersion(derniereVersion).stream()
                .filter(s -> implique(s, prof))
                .collect(Collectors.toList());

        if (soutenancesProf.isEmpty()) return "Aucune soutenance affectée à ce professeur.";

        List<Salle>      salles      = salleRepository.findAll();
        List<Professeur> tousProfs   = professeurRepository.findAll();
        List<LocalTime>  creneaux    = genererCreneaux();

        int nbRecalcules = 0;

        for (Soutenance soutenance : soutenancesProf) {
            LocalDate jour  = soutenance.getDate();
            LocalTime heure = soutenance.getHeure();

            // Vérifier si le prof est indisponible à ce créneau
            if (!estProfIndisponibleContrainte(prof, jour, heure)) continue;

            // Le créneau est en conflit avec une indisponibilité — chercher un autre créneau
            System.out.printf("  ⚠️  Conflit indisponibilité : %s le %s à %s - recalcul...%n",
                prof.getNom(), jour, heure);

            // Initialiser le cache avec les autres soutenances du même jour
            cacheSoutenancesParJour = new HashMap<>();
            cacheChargeProf         = new HashMap<>();

            List<Soutenance> autresSoutenancesDuJour = soutenanceRepository
                    .findByVersion(derniereVersion).stream()
                    .filter(s -> s.getDate().equals(jour)
                              && !s.getId().equals(soutenance.getId()))
                    .collect(Collectors.toList());
            cacheSoutenancesParJour.put(jour.toEpochDay(),
                    new ArrayList<>(autresSoutenancesDuJour));

            // Chercher un nouveau créneau sur le même jour
            boolean trouve = false;
            for (LocalTime nouveauCreneau : creneaux) {
                if (nouveauCreneau.equals(heure)) continue;

                // Vérifier que l'encadrant est dispo
                if (encadrantOccupeMemeCreneauExact(soutenance.getEncadrant(), jour, nouveauCreneau))
                    continue;

                // Vérifier que le prof impliqué est dispo
                if (!estProfDisponible(prof, jour, nouveauCreneau)) continue;

                // Vérifier une salle libre
                Salle salleLibre = trouverSalleLibre(salles, jour, nouveauCreneau);
                if (salleLibre == null) continue;

                // Vérifier que le jury est toujours valide à ce créneau
                Etudiant etudiant    = soutenance.getEtudiant();
                boolean exigeAnglais = etudiant.getLangue() == Etudiant.Langue.EN;
                ListeJury jury = choisirJury(soutenance.getEncadrant(), tousProfs,
                        jour, nouveauCreneau, etudiant.getFiliere(), exigeAnglais);
                if (jury == null) continue;

                // Mettre à jour la soutenance
                soutenance.setHeure(nouveauCreneau);
                soutenance.setSalle(salleLibre);
                soutenance.setJury1(jury.j1());
                soutenance.setJury2(jury.j2());
                soutenance.setJury3(jury.j3());
                soutenanceRepository.save(soutenance);
                nbRecalcules++;
                trouve = true;

                System.out.printf("  → Nouveau créneau : %s à %s (salle %s)%n",
                    jour, nouveauCreneau, salleLibre.getNom());
                break;
            }

            if (!trouve) {
                System.out.printf("  ❌ Impossible de trouver un créneau alternatif pour %s le %s%n",
                    prof.getNom(), jour);
            }
        }

        return nbRecalcules > 0
            ? nbRecalcules + " soutenance(s) recalculée(s) suite à l'indisponibilité."
            : "Aucune soutenance affectée par cette indisponibilité.";
    }

    /**
     * Vérifie uniquement les contraintes d'indisponibilité (pas les autres soutenances).
     * Utilisé pour détecter les conflits après ajout d'une contrainte.
     */
    private boolean estProfIndisponibleContrainte(Professeur prof,
                                                   LocalDate jour,
                                                   LocalTime heureDebut) {
    	LocalTime heureFin = heureDebut.plusMinutes(dureeSoutenance);
        for (Contrainte c : cacheContraintes.getOrDefault(prof.getId(), List.of())) {
            if (!c.getDateIndisponible().equals(jour)) continue;
            if (c.getHeureDebut() == null || c.getHeureFin() == null) return true;
            if (heureDebut.isBefore(c.getHeureFin()) && heureFin.isAfter(c.getHeureDebut()))
                return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXÉCUTION
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
            " %d soutenances planifiées sur %d étudiants.", succes, etudiants.size());
        if (echecs > 0) {
            String premiereCause = rapportEchecs.keySet().iterator().next();
            int nb = rapportEchecs.get(premiereCause).size();
            msg += String.format(" ⚠️ %d non planifiés. Cause : %s (%d cas).",
                echecs, premiereCause, nb);
        }

        try {
            List<Soutenance> soutenancesGenerees = soutenanceRepository.findByVersion(version);
            ValidationSummary validation = validationService.validatePlanning(soutenancesGenerees);
            if (!validation.isValid()) {
                System.out.println("\n⚠️  " + validation.getFormattedReport());
            } else {
                System.out.println("\n✅ Planning validé sans anomalie.");
            }
        } catch (Exception e) {
            System.err.println("⚠️  Erreur validation : " + e.getMessage());
        }

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
                s.setDureeMn(dureeSoutenance);

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
    //  VÉRIFICATION ENCADRANT
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
 // ══════════════════════════════════════════════════════════════════════
    //  SÉLECTION DU JURY
    //  ✅ FIX : Pour EN, le jury doit TOUJOURS contenir exactement 1 prof anglais.
    //           Si aucun prof anglais n'est disponible → retourner null
    //           pour forcer un autre créneau (pas de fallback sans anglophone).
    // ══════════════════════════════════════════════════════════════════════
    private ListeJury choisirJury(Professeur encadrant, List<Professeur> tousProfs,
            LocalDate jour, LocalTime heure,
            Etudiant.Filiere filiere, boolean exigeAnglais) {

        // Pool : tous les profs disponibles sauf l'encadrant
        // triés par charge jury croissante → équité automatique
        List<Professeur> candidats = tousProfs.stream()
            .filter(p -> !p.getId().equals(encadrant.getId()))
            .filter(p -> estProfDisponible(p, jour, heure))
            .sorted(Comparator.comparingLong(p -> chargeJury(p.getId())))
            .collect(Collectors.toList());

        if (candidats.size() < 2) return null;

        if (exigeAnglais) {
            // ── SOUTENANCE EN ──────────────────────────────────────────────
            // Chercher un prof anglophone parmi les candidats
            Professeur profAnglais = candidats.stream()
                .filter(p -> Boolean.TRUE.equals(p.getParleAnglais()))
                .findFirst()
                .orElse(null);

            if (profAnglais != null) {
                // jury2 = premier prof de la même spécialité (≠ profAnglais)
                Professeur profSpec = candidats.stream()
                    .filter(p -> !p.getId().equals(profAnglais.getId()))
                    .filter(p -> estSpecialiteFiliere(p, filiere))
                    .findFirst().orElse(null);

                if (profSpec != null) {
                    return new ListeJury(encadrant, profSpec, profAnglais);
                }

                // Mode dégradé : pas de prof spécialité dispo → n'importe qui
                Professeur autre = candidats.stream()
                    .filter(p -> !p.getId().equals(profAnglais.getId()))
                    .findFirst().orElse(null);
                if (autre != null) {
                    System.out.printf("  ⚠️  Mode dégradé EN : pas de prof spécialité %s dispo%n", filiere);
                    return new ListeJury(encadrant, autre, profAnglais);
                }
                return null;
            }
            //  Aucun anglophone dispo → prendre les 2 premiers candidats
            System.out.printf("  ⚠️  Aucun prof anglophone dispo à %s — jury sans anglophone%n", heure);
            return new ListeJury(encadrant, candidats.get(0), candidats.get(1));

        } else {
            // ── SOUTENANCE FR ──
            // jury2 = premier prof de la même spécialité
            Professeur profSpec = candidats.stream()
                .filter(p -> estSpecialiteFiliere(p, filiere))
                .findFirst().orElse(null);

            if (profSpec != null) {
                // jury3 = n'importe qui d'autre (charge minimale)
                Professeur autre = candidats.stream()
                    .filter(p -> !p.getId().equals(profSpec.getId()))
                    .findFirst().orElse(null);
                if (autre != null) {
                    return new ListeJury(encadrant, profSpec, autre);
                }
            }

            // Mode dégradé : pas de prof spécialité dispo → 2 premiers par charge
            System.out.printf("  ⚠️  Mode dégradé FR : pas de prof spécialité %s dispo%n", filiere);
            return new ListeJury(encadrant, candidats.get(0), candidats.get(1));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DISPONIBILITÉ PROF
    // ══════════════════════════════════════════════════════════════════════
    public boolean estProfDisponible(Professeur prof, LocalDate jour, LocalTime heureDebut) {
    	LocalTime heureFin = heureDebut.plusMinutes(dureeSoutenance);

        for (Contrainte c : cacheContraintes.getOrDefault(prof.getId(), List.of())) {
            if (!c.getDateIndisponible().equals(jour)) continue;
            if (c.getHeureDebut() == null || c.getHeureFin() == null) return false;
            if (heureDebut.isBefore(c.getHeureFin()) && heureFin.isAfter(c.getHeureDebut()))
                return false;
        }

        for (Soutenance s : getCacheJour(jour)) {
            if (!implique(s, prof)) continue;
            LocalTime exDebut = s.getHeure();
            LocalTime exFin   = exDebut.plusMinutes(dureeSoutenance);
            boolean nouvFinAvantEx = !heureFin.plusMinutes(delaiProf).isAfter(exDebut);
            boolean exFinAvantNouv = !exFin.plusMinutes(delaiProf).isAfter(heureDebut);
            if (!nouvFinAvantEx && !exFinAvantNouv) return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DISPONIBILITÉ SALLE
    // ══════════════════════════════════════════════════════════════════════
    public boolean estSalleLibre(Salle salle, LocalDate jour, LocalTime heureDebut) {
    	LocalTime heureFin = heureDebut.plusMinutes(dureeSoutenance);
        for (Soutenance s : getCacheJour(jour)) {
            if (!s.getSalle().getId().equals(salle.getId())) continue;
            LocalTime exDebut = s.getHeure();
            LocalTime exFin   = exDebut.plusMinutes(dureeSoutenance);
            boolean nouvFinAvantEx = !heureFin.plusMinutes(pauseSalle).isAfter(exDebut);
            boolean exFinAvantNouv = !exFin.plusMinutes(pauseSalle).isAfter(heureDebut);
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

        // Matin : de debutJournee jusqu'à 12h00
        LocalTime debutMatin = debutJournee;
        LocalTime finMatin   = LocalTime.of(12, 0);
        LocalTime t = debutMatin;
        while (!t.plusMinutes(dureeSoutenance).isAfter(finMatin)) {
            cr.add(t);
            t = t.plusMinutes(intervalle);
        }

        // Après-midi : de 14h00 jusqu'à finJournee
        LocalTime debutApres = LocalTime.of(14, 0);
        t = debutApres;
        while (!t.plusMinutes(dureeSoutenance).isAfter(finJournee)) {
            cr.add(t);
            t = t.plusMinutes(intervalle);
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
    private boolean estProfAnglais(Professeur p) {
        if (p.getSpecialite() == null) return false;
        String sp = p.getSpecialite().trim().toUpperCase()
            .replace("É", "E").replace("È", "E");
        return sp.equals("ANGLAIS");
    }
    /**
     * Vérifie si un prof correspond à la spécialité requise par la filière.
     * GI   → GI ou INFORMATIQUE
     * TDIA → GI, INFORMATIQUE ou MATHEMATIQUE
     * DATA → MATHEMATIQUE
     */
    private boolean estSpecialiteFiliere(Professeur p, Etudiant.Filiere filiere) {
        if (p.getSpecialite() == null) return false;
        String sp = p.getSpecialite().trim().toUpperCase()
            .replace("É", "E").replace("È", "E").replace("Ê", "E");

        return switch (filiere) {
            case GI   -> sp.equals("GI") || sp.contains("INFORMATIQUE");
            case TDIA -> sp.equals("GI") || sp.contains("INFORMATIQUE")
                      || sp.contains("MATHEMATIQUE");
            case DATA -> sp.contains("MATHEMATIQUE");
        };
    }
    
    private boolean estProfGI(Professeur p) {
        if (p.getSpecialite() == null) return false;
        String s = p.getSpecialite().trim().toUpperCase();
        return s.equals("GI") || s.contains("INFORMATIQUE")
            || s.contains("GÉNIE INFO") || s.contains("GENIE INFO");
    }

    private long nbProfsGI(Professeur e, Professeur c1, Professeur c2) {
        return (estProfGI(e) ? 1 : 0)
             + (estProfGI(c1) ? 1 : 0)
             + (estProfGI(c2) ? 1 : 0);
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
        if (s.getJury2() != null)
            cacheChargeJury.merge(s.getJury2().getId(), 1L, Long::sum);
        if (s.getJury3() != null)
            cacheChargeJury.merge(s.getJury3().getId(), 1L, Long::sum);
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
    private long chargeJury(Long id) {
        return cacheChargeJury.getOrDefault(id, 0L);
    }

    public static class PlanificationException extends RuntimeException {
        public PlanificationException(String message) { super(message); }
    }
}
