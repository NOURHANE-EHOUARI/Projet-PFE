package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
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

    @Autowired private EtudiantRepository    etudiantRepository;
    @Autowired private ProfesseurRepository  professeurRepository;
    @Autowired private SalleRepository       salleRepository;
    @Autowired private SoutenanceRepository  soutenanceRepository;
    @Autowired private ContrainteRepository  contrainteRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    // Paramètres configurables (idéalement lus depuis la feuille CONFIG de l'Excel)
    private static final int DUREE_MIN       = 45; // Mis à jour selon ta config
    private static final int DELAI_JURY_MIN  = 60; // Pause d'1h entre deux soutenances
    private static final LocalTime DEBUT_JOURNEE = LocalTime.of(8, 30);
    private static final LocalTime FIN_JOURNEE   = LocalTime.of(17, 30);

    /**
     * Génère le planning complet et l'associe à une nouvelle VersionPlanning.
     */
    public String genererPlanningComplet(List<LocalDate> joursDisponibles) {
        
        // 1. Créer une nouvelle version
        VersionPlanning version = new VersionPlanning();
        version.setDateGeneration(LocalDateTime.now());
        version.setDescription("Planning généré automatiquement - " + LocalDateTime.now());
        version = versionPlanningRepository.save(version);

        // 2. Charger les données
        List<Etudiant> etudiants = etudiantRepository.findAll();
        List<Professeur> professeurs = professeurRepository.findAll();
        List<Salle> salles = salleRepository.findAll();

        if (joursDisponibles == null || joursDisponibles.isEmpty()) {
            // Si aucun jour n'est fourni, on prend les 3 prochains jours ouvrés par défaut
            joursDisponibles = Arrays.asList(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(3)
            );
        }

        try {
            List<Soutenance> nouveauPlanning = genererAlgorithme(etudiants, professeurs, salles, joursDisponibles, version);
            return "Succès : " + nouveauPlanning.size() + " soutenances planifiées dans la Version ID: " + version.getId();
        } catch (PlanificationException e) {
            return "Erreur : " + e.getMessage();
        }
    }

    /**
     * Cœur de l'algorithme (adapté de ton code original)
     */
    private List<Soutenance> genererAlgorithme(List<Etudiant> etudiants,
                                               List<Professeur> professeurs,
                                               List<Salle> salles,
                                               List<LocalDate> jours,
                                               VersionPlanning version) {

        if (professeurs.size() < 3) throw new PlanificationException("Il faut au moins 3 professeurs.");
        if (etudiants.isEmpty()) throw new PlanificationException("Aucun étudiant à planifier.");

        // Tri : Les étudiants en Anglais passent en premier (contrainte plus forte)
        List<Etudiant> etudiantsTries = etudiants.stream()
                .sorted(Comparator.comparingInt(e -> e.getLangue() == Etudiant.Langue.EN ? 0 : 1))
                .collect(Collectors.toList());

        List<LocalTime> creneaux = genererCreneaux();
        List<Soutenance> planningFinal = new ArrayList<>();

        for (Etudiant etudiant : etudiantsTries) {
            boolean planifie = false;

            // Boucle sur les jours, heures, salles
            outer:
            for (LocalDate jour : jours) {
                for (LocalTime heure : creneaux) {
                    for (Salle salle : salles) {

                        if (!estSalleLibre(salle, jour, heure, planningFinal)) continue;
                        
                        Professeur encadrant = etudiant.getEncadrant();
                        if (encadrant == null) continue; // Sécurité
                        if (!estProfDisponible(encadrant, jour, heure, planningFinal)) continue;

                        // Sélection du Jury (Jury1, Jury2, Jury3)
                        // Note: Ton algorithme original choisissait 2 jurys. Ici on en choisit 3 (ou 2 + encadrant)
                        ListeJury jury = choisirJuryComplet(encadrant, professeurs, jour, heure, planningFinal, etudiant.getLangue() == Etudiant.Langue.EN);
                        
                        if (jury == null) continue;

                        // Création de la soutenance
                        Soutenance s = new Soutenance();
                        s.setVersion(version);
                        s.setEtudiant(etudiant);
                        s.setEncadrant(encadrant);
                        s.setJury1(jury.j1);
                        s.setJury2(jury.j2);
                        s.setJury3(jury.j3);
                        s.setSalle(salle);
                        s.setDate(jour);
                        s.setHeure(heure);
                        s.setDureeMn(DUREE_MIN);

                        soutenanceRepository.save(s);
                        planningFinal.add(s);
                        planifie = true;
                        break outer;
                    }
                }
            }

            if (!planifie) {
                throw new PlanificationException("Impossible de planifier l'étudiant : " + etudiant.getNom() + " " + etudiant.getPrenom());
            }
        }

        return planningFinal;
    }

    // ===== LOGIQUE DE SÉLECTION DU JURY (Adaptée pour 3 membres) =====

    private ListeJury choisirJuryComplet(Professeur encadrant,
                                         List<Professeur> tousProfs,
                                         LocalDate jour,
                                         LocalTime heure,
                                         List<Soutenance> planningActuel,
                                         boolean exigeAnglais) {
        
        // On cherche 3 profs disponibles (qui ne sont pas l'encadrant)
        List<Professeur> candidats = tousProfs.stream()
                .filter(p -> !p.getId().equals(encadrant.getId()))
                .filter(p -> estProfDisponible(p, jour, heure, planningActuel))
                .sorted(Comparator.comparingLong(p -> compterSoutenances(p, planningActuel))) // Équité
                .collect(Collectors.toList());

        // On essaie de trouver 3 profs qui respectent la contrainte langue
        for (int i = 0; i < candidats.size(); i++) {
            for (int j = i + 1; j < candidats.size(); j++) {
                for (int k = j + 1; k < candidats.size(); k++) {
                    
                    Professeur p1 = candidats.get(i);
                    Professeur p2 = candidats.get(j);
                    Professeur p3 = candidats.get(k);

                    // Vérification contrainte Anglais : Au moins un des 3 doit parler anglais
                    if (exigeAnglais && !p1.isParleAnglais() && !p2.isParleAnglais() && !p3.isParleAnglais()) {
                        continue;
                    }

                    return new ListeJury(p1, p2, p3);
                }
            }
        }
        return null;
    }

    // ===== VÉRIFICATIONS (Similaires à ton code, adaptées) =====

    public boolean estProfDisponible(Professeur prof, LocalDate jour, LocalTime heure, List<Soutenance> planningActuel) {
        // 1. Vérifier les contraintes BDD
        List<Contrainte> contraintes = contrainteRepository.findByProfesseur(prof);
        LocalTime heureFin = heure.plusMinutes(DUREE_MIN);

        for (Contrainte c : contraintes) {
            if (c.getDateIndisponible().equals(jour)) {
                if (c.getHeureDebut() != null && c.getHeureFin() != null) {
                    if (heure.isBefore(c.getHeureFin()) && heureFin.isAfter(c.getHeureDebut())) return false;
                } else {
                    return false; // Toute la journée bloquée
                }
            }
        }

        // 2. Vérifier les conflits dans le planning actuel (Délai 1h)
        for (Soutenance s : planningActuel) {
            if (!s.getDate().equals(jour)) continue;
            
            // Si le prof est impliqué dans cette soutenance
            if (s.getEncadrant().getId().equals(prof.getId()) ||
                (s.getJury1() != null && s.getJury1().getId().equals(prof.getId())) ||
                (s.getJury2() != null && s.getJury2().getId().equals(prof.getId())) ||
                (s.getJury3() != null && s.getJury3().getId().equals(prof.getId()))) {
                
                long diffMin = Math.abs(heure.toSecondOfDay() / 60L - s.getHeure().toSecondOfDay() / 60L);
                if (diffMin < DELAI_JURY_MIN) return false;
            }
        }
        return true;
    }

    public boolean estSalleLibre(Salle salle, LocalDate jour, LocalTime heure, List<Soutenance> planningActuel) {
        return planningActuel.stream().noneMatch(s ->
                s.getSalle().getId().equals(salle.getId()) &&
                s.getDate().equals(jour) &&
                s.getHeure().equals(heure));
    }

    private List<LocalTime> genererCreneaux() {
        List<LocalTime> creneaux = new ArrayList<>();
        LocalTime t = DEBUT_JOURNEE;
        while (!t.isAfter(FIN_JOURNEE.minusMinutes(DUREE_MIN))) {
            creneaux.add(t);
            t = t.plusMinutes(DUREE_MIN);
        }
        return creneaux;
    }

    public long compterSoutenances(Professeur prof, List<Soutenance> planningActuel) {
        return planningActuel.stream()
                .filter(s -> s.getEncadrant().getId().equals(prof.getId()) ||
                             (s.getJury1() != null && s.getJury1().getId().equals(prof.getId())) ||
                             (s.getJury2() != null && s.getJury2().getId().equals(prof.getId())) ||
                             (s.getJury3() != null && s.getJury3().getId().equals(prof.getId())))
                .count();
    }

    // Types internes
    private record ListeJury(Professeur j1, Professeur j2, Professeur j3) {}
    
    public static class PlanificationException extends RuntimeException {
        public PlanificationException(String message) { super(message); }
    }
}