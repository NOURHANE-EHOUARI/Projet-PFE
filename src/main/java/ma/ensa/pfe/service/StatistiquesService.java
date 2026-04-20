package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de calcul des statistiques pour le tableau de bord.
 * Fournit : charge par professeur, répartition par jour, par filière, par langue.
 *
 * @author Membre A
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    @Autowired private SoutenanceRepository soutenanceRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private EtudiantRepository   etudiantRepository;

    // ══════════════════════════════════════════════
    //  CHARGE PAR PROFESSEUR
    // ══════════════════════════════════════════════

    /**
     * Retourne pour chaque professeur le nombre de soutenances
     * où il est impliqué (encadrant OU jury1 OU jury2).
     * Trié par charge décroissante.
     *
     * @return Map ordonnée : nomComplet → nbSoutenances
     */
    public Map<String, Long> getChargeParProfesseur() {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        List<Professeur> professeurs = professeurRepository.findAll();

        Map<String, Long> charges = new LinkedHashMap<>();
        professeurs.stream()
                .sorted(Comparator.comparing(Professeur::getNom))
                .forEach(prof -> {
                    long count = soutenances.stream()
                            .filter(s -> s.getEncadrant().getId().equals(prof.getId())
                                    || (s.getJury1() != null && s.getJury1().getId().equals(prof.getId()))
                                    || (s.getJury2() != null && s.getJury2().getId().equals(prof.getId())))
                            .count();
                    charges.put(prof.getNomComplet(), count);
                });

        // Trier par charge décroissante
        return charges.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    // ══════════════════════════════════════════════
    //  SOUTENANCES PAR JOUR
    // ══════════════════════════════════════════════

    /**
     * Retourne le nombre de soutenances par date, trié chronologiquement.
     *
     * @return Map ordonnée : date → nbSoutenances
     */
    public Map<String, Long> getSoutenancesParJour() {
        List<Soutenance> soutenances = soutenanceRepository.findAll();

        return soutenances.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getDate().toString(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    // ══════════════════════════════════════════════
    //  RÉPARTITION PAR FILIÈRE
    // ══════════════════════════════════════════════

    /**
     * Retourne le nombre d'étudiants par filière (GI / ID).
     */
    public Map<String, Long> getRepartitionFiliere() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("GI", etudiantRepository.countByFiliere(ma.ensa.pfe.model.Etudiant.Filiere.GI));
        result.put("ID", etudiantRepository.countByFiliere(ma.ensa.pfe.model.Etudiant.Filiere.ID));
        return result;
    }

    // ══════════════════════════════════════════════
    //  RÉPARTITION PAR LANGUE
    // ══════════════════════════════════════════════

    /**
     * Retourne le nombre d'étudiants par langue de soutenance (FR / EN).
     */
    public Map<String, Long> getRepartitionLangue() {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("FR", soutenances.stream()
                .filter(s -> s.getEtudiant().getLangue() == ma.ensa.pfe.model.Etudiant.Langue.FR)
                .count());
        result.put("EN", soutenances.stream()
                .filter(s -> s.getEtudiant().getLangue() == ma.ensa.pfe.model.Etudiant.Langue.EN)
                .count());
        return result;
    }

    // ══════════════════════════════════════════════
    //  STATS GLOBALES
    // ══════════════════════════════════════════════

    /** Nombre total de soutenances planifiées. */
    public long getTotalSoutenances() {
        return soutenanceRepository.count();
    }

    /** Nombre total de professeurs. */
    public long getTotalProfesseurs() {
        return professeurRepository.count();
    }

    /** Nombre total d'étudiants. */
    public long getTotalEtudiants() {
        return etudiantRepository.count();
    }

    /** Nombre de jours de soutenance distincts. */
    public long getNbJours() {
        return soutenanceRepository.findAll().stream()
                .map(Soutenance::getDate)
                .distinct()
                .count();
    }

    /**
     * Charge maximale d'un professeur (pour colorer le graphique en rouge si > seuil).
     */
    public long getChargeMax() {
        return getChargeParProfesseur().values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
    }
}