package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.ContrainteRepository;
import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.dao.SalleRepository;
import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.model.Etudiant.Langue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cœur algorithmique du système de planification PFE.
 *
 * <p>Algorithme Greedy en 3 étapes :
 * <ol>
 *   <li>Trier les étudiants par contrainte décroissante
 *       (EN d'abord — jury anglophone plus rare)</li>
 *   <li>Pour chaque étudiant, trouver le premier créneau [date × heure × salle]
 *       satisfaisant toutes les contraintes</li>
 *   <li>Optimisation équité : choisir le binôme jury le moins chargé</li>
 * </ol>
 *
 * <p>Contraintes vérifiées :
 * <ul>
 *   <li>Salle libre sur le créneau</li>
 *   <li>Encadrant disponible (pas d'indisponibilité BDD + délai 1h)</li>
 *   <li>Jury1 et Jury2 disponibles (idem)</li>
 *   <li>Délai minimum 60 min entre deux soutenances du même jury</li>
 *   <li>Langue EN → au moins un jury {@code parleAnglais = true}</li>
 *   <li>L'encadrant ne peut pas être jury de son propre étudiant</li>
 * </ul>
 *
 * @author Membre A
 */
@Service
@Transactional
public class PlanificationService {

    @Autowired private EtudiantRepository    etudiantRepository;
    @Autowired private ProfesseurRepository  professeurRepository;
    @Autowired private SalleRepository       salleRepository;
    @Autowired private SoutenanceRepository  soutenanceRepository;
    @Autowired private ContrainteRepository  contrainteRepository;

    /** Durée standard d'une soutenance (minutes). */
    private static final int DUREE_MIN       = 30;

    /** Délai minimum obligatoire entre deux soutenances du même jury (minutes). */
    private static final int DELAI_JURY_MIN  = 60;

    /** Première heure possible dans la journée. */
    private static final LocalTime DEBUT_JOURNEE = LocalTime.of(8, 0);

    /** Dernière heure de début possible dans la journée. */
    private static final LocalTime FIN_JOURNEE   = LocalTime.of(17, 0);

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE — charge depuis la BDD
    // ══════════════════════════════════════════════

    /**
     * Génère le planning complet en chargeant toutes les données depuis la base.
     *
     * @param joursDisponibles jours de soutenance autorisés
     * @return liste des soutenances créées et persistées
     * @throws PlanificationException si un ou plusieurs étudiants ne peuvent pas être planifiés
     */
    public List<Soutenance> genererPlanning(List<LocalDate> joursDisponibles) {
        return genererPlanning(
                etudiantRepository.findAll(),
                professeurRepository.findAll(),
                salleRepository.findAll(),
                joursDisponibles
        );
    }

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE — listes explicites (tests + surcharge)
    // ══════════════════════════════════════════════

    /**
     * Génère le planning à partir de listes fournies explicitement.
     * Cette surcharge facilite les tests unitaires (pas de BDD nécessaire).
     *
     * @param etudiants  étudiants à planifier
     * @param professeurs professeurs disponibles comme jury
     * @param salles     salles disponibles
     * @param jours      jours de soutenance
     * @return liste des soutenances générées
     */
    public List<Soutenance> genererPlanning(List<Etudiant>   etudiants,
                                             List<Professeur> professeurs,
                                             List<Salle>      salles,
                                             List<LocalDate>  jours) {

        // ── Préconditions ────────────────────────────────
        if (jours == null || jours.isEmpty())
            throw new PlanificationException("Aucun jour disponible fourni.");
        if (salles == null || salles.isEmpty())
            throw new PlanificationException("Aucune salle disponible.");
        if (professeurs == null || professeurs.size() < 3)
            throw new PlanificationException("Il faut au moins 3 professeurs (encadrant + 2 jurys).");
        if (etudiants == null || etudiants.isEmpty())
            throw new PlanificationException("Aucun étudiant à planifier.");

        // ── Réinitialisation ─────────────────────────────
        soutenanceRepository.deleteAll();

        // ── Tri : EN d'abord ─────────────────────────────
        List<Etudiant> etudiantsTries = trierParContrainte(etudiants);

        // ── Créneaux horaires de la journée ───────────────
        List<LocalTime> creneaux = genererCreneaux();

        // ── Planification ────────────────────────────────
        List<Soutenance> planning     = new ArrayList<>();
        List<Etudiant>   nonPlanifies = new ArrayList<>();

        for (Etudiant etudiant : etudiantsTries) {
            boolean planifie = false;

            outer:
            for (LocalDate jour : jours) {
                for (LocalTime heure : creneaux) {
                    for (Salle salle : salles) {

                        if (!estSalleLibre(salle, jour, heure, planning)) continue;

                        Professeur encadrant = etudiant.getEncadrant();
                        if (!estProfDisponible(encadrant, jour, heure, planning)) continue;

                        boolean exigeAnglais = etudiant.getLangue() == Langue.EN;
                        PaireJury jury = choisirJury(encadrant, professeurs,
                                                     jour, heure, planning, exigeAnglais);
                        if (jury == null) continue;

                        Soutenance s = new Soutenance();
                        s.setEtudiant(etudiant);
                        s.setEncadrant(encadrant);
                        s.setJury1(jury.jury1());
                        s.setJury2(jury.jury2());
                        s.setSalle(salle);
                        s.setDate(jour);
                        s.setHeure(heure);
                        s.setDureeMn(DUREE_MIN);

                        planning.add(s);
                        soutenanceRepository.save(s);
                        planifie = true;
                        break outer;
                    }
                }
            }

            if (!planifie) nonPlanifies.add(etudiant);
        }

        if (!nonPlanifies.isEmpty()) {
            String noms = nonPlanifies.stream()
                    .map(e -> e.getNom() + " " + e.getPrenom())
                    .collect(Collectors.joining(", "));
            throw new PlanificationException(
                    nonPlanifies.size() + " étudiant(s) impossible(s) à planifier : " + noms);
        }

        return planning;
    }

    // ══════════════════════════════════════════════
    //  TRI DES ÉTUDIANTS
    // ══════════════════════════════════════════════

    /**
     * Trie les étudiants par contrainte décroissante.
     * Les étudiants EN passent avant FR car le jury anglophone est plus rare.
     */
    private List<Etudiant> trierParContrainte(List<Etudiant> etudiants) {
        return etudiants.stream()
                .sorted(Comparator.comparingInt(e -> e.getLangue() == Langue.EN ? 0 : 1))
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════
    //  SÉLECTION DU JURY
    // ══════════════════════════════════════════════

    /**
     * Choisit le meilleur binôme jury1+jury2 pour un créneau donné.
     *
     * <p>Critères appliqués dans l'ordre :
     * <ol>
     *   <li>Exclure l'encadrant</li>
     *   <li>Garder uniquement les profs disponibles sur le créneau</li>
     *   <li>Si {@code exigeAnglais} : au moins un des deux doit avoir {@code parleAnglais = true}</li>
     *   <li>Trier les candidats par charge croissante pour l'équité</li>
     *   <li>Retourner le premier binôme valide</li>
     * </ol>
     *
     * @return PaireJury ou {@code null} si aucun binôme valide n'existe
     */
    private PaireJury choisirJury(Professeur       encadrant,
                                   List<Professeur> tousProfs,
                                   LocalDate        jour,
                                   LocalTime        heure,
                                   List<Soutenance> planningActuel,
                                   boolean          exigeAnglais) {

        List<Professeur> candidats = tousProfs.stream()
                .filter(p -> !p.getId().equals(encadrant.getId()))
                .filter(p -> estProfDisponible(p, jour, heure, planningActuel))
                .sorted(Comparator.comparingLong(p -> compterSoutenances(p, planningActuel)))
                .collect(Collectors.toList());

        for (int i = 0; i < candidats.size(); i++) {
            for (int j = i + 1; j < candidats.size(); j++) {
                Professeur j1 = candidats.get(i);
                Professeur j2 = candidats.get(j);

                if (exigeAnglais && !j1.isParleAnglais() && !j2.isParleAnglais()) continue;

                return new PaireJury(j1, j2);
            }
        }

        return null; // Aucun binôme valide trouvé pour ce créneau
    }

    // ══════════════════════════════════════════════
    //  VÉRIFICATION DE DISPONIBILITÉ
    // ══════════════════════════════════════════════

    /**
     * Vérifie qu'un professeur est libre sur un créneau donné.
     *
     * <p>Deux sources consultées :
     * <ol>
     *   <li>Contraintes d'indisponibilité en base (table {@code contraintes})</li>
     *   <li>Soutenances déjà planifiées en mémoire (délai 60 min)</li>
     * </ol>
     *
     * @param prof           le professeur à vérifier
     * @param jour           la date du créneau
     * @param heure          l'heure de début du créneau
     * @param planningActuel soutenances déjà affectées dans la session courante
     * @return {@code true} si le professeur est disponible
     */
    public boolean estProfDisponible(Professeur       prof,
                                      LocalDate        jour,
                                      LocalTime        heure,
                                      List<Soutenance> planningActuel) {

        // 1. Contraintes d'indisponibilité (BDD)
        List<Contrainte> contraintes = contrainteRepository.findByProfesseur(prof);
        LocalTime heureFin = heure.plusMinutes(DUREE_MIN);

        for (Contrainte c : contraintes) {
            if (!c.getDateIndisponible().equals(jour)) continue;

            // Pas d'heure précisée → toute la journée bloquée
            if (c.getHeureDebut() == null || c.getHeureFin() == null) return false;

            // Chevauchement : la soutenance débute avant la fin de l'indispo
            // et se termine après son début
            if (heure.isBefore(c.getHeureFin()) && heureFin.isAfter(c.getHeureDebut()))
                return false;
        }

        // 2. Délai minimum 60 min dans le planning en cours
        for (Soutenance s : planningActuel) {
            if (!s.getDate().equals(jour)) continue;

            boolean implique = s.getEncadrant().getId().equals(prof.getId())
                    || s.getJury1().getId().equals(prof.getId())
                    || s.getJury2().getId().equals(prof.getId());

            if (!implique) continue;

            long diffMin = Math.abs(
                    heure.toSecondOfDay() / 60L - s.getHeure().toSecondOfDay() / 60L);

            if (diffMin < DELAI_JURY_MIN) return false;
        }

        return true;
    }

    /**
     * Vérifie qu'une salle est libre à un créneau donné
     * (en consultant le planning en mémoire, pas la BDD, pour la cohérence).
     */
    public boolean estSalleLibre(Salle            salle,
                                  LocalDate        jour,
                                  LocalTime        heure,
                                  List<Soutenance> planningActuel) {
        return planningActuel.stream().noneMatch(s ->
                s.getSalle().getId().equals(salle.getId())
                && s.getDate().equals(jour)
                && s.getHeure().equals(heure));
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════

    /**
     * Génère les créneaux horaires de la journée par tranches de {@code DUREE_MIN} minutes.
     * Ex : 08:00, 08:30, 09:00, …, 17:00
     */
    private List<LocalTime> genererCreneaux() {
        List<LocalTime> creneaux = new ArrayList<>();
        LocalTime t = DEBUT_JOURNEE;
        while (!t.isAfter(FIN_JOURNEE)) {
            creneaux.add(t);
            t = t.plusMinutes(DUREE_MIN);
        }
        return creneaux;
    }

    /**
     * Compte le nombre de soutenances où un professeur est impliqué
     * (comme encadrant, jury1 ou jury2). Utilisé pour l'équité.
     *
     * @param prof           le professeur concerné
     * @param planningActuel planning en mémoire
     * @return nombre de soutenances
     */
    public long compterSoutenances(Professeur prof, List<Soutenance> planningActuel) {
        return planningActuel.stream()
                .filter(s -> s.getEncadrant().getId().equals(prof.getId())
                        || s.getJury1().getId().equals(prof.getId())
                        || s.getJury2().getId().equals(prof.getId()))
                .count();
    }

    // ══════════════════════════════════════════════
    //  TYPES INTERNES
    // ══════════════════════════════════════════════

    /**
     * Binôme jury1 + jury2 sélectionné pour une soutenance.
     * Record Java 16+ : immuable et concis.
     */
    private record PaireJury(Professeur jury1, Professeur jury2) {}

    /**
     * Exception métier levée lorsque la planification ne peut pas aboutir.
     */
    public static class PlanificationException extends RuntimeException {
        public PlanificationException(String message) {
            super(message);
        }
    }
}