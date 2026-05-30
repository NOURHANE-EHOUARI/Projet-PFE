package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    @Autowired private SoutenanceRepository soutenanceRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private EtudiantRepository   etudiantRepository;

  
    public Map<String, Long> getChargeParProfesseur() {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        List<Professeur> professeurs = professeurRepository.findAll();

        Map<String, Long> charges = new LinkedHashMap<>();
        
        professeurs.stream()
                .sorted(Comparator.comparing(Professeur::getNom))
                .forEach(prof -> {
                    long count = soutenances.stream()
                            .filter(s -> {
                                
                                boolean isEncadrant = s.getEncadrant() != null && s.getEncadrant().getId().equals(prof.getId());
                                boolean isJury1 = s.getJury1() != null && s.getJury1().getId().equals(prof.getId());
                                boolean isJury2 = s.getJury2() != null && s.getJury2().getId().equals(prof.getId());
                                boolean isJury3 = s.getJury3() != null && s.getJury3().getId().equals(prof.getId());
                                
                                return isEncadrant || isJury1 || isJury2 || isJury3;
                            })
                            .count();
                    charges.put(prof.getNomComplet(), count);
                });

       
        return charges.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

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


    public Map<String, Long> getRepartitionFiliere() {
        Map<String, Long> result = new LinkedHashMap<>();
        
       
        result.put("GI", etudiantRepository.countByFiliere(Etudiant.Filiere.GI));
        result.put("TDIA", etudiantRepository.countByFiliere(Etudiant.Filiere.TDIA));
        result.put("DATA", etudiantRepository.countByFiliere(Etudiant.Filiere.DATA));
        
        return result;
    }


    public Map<String, Long> getRepartitionLangue() {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        Map<String, Long> result = new LinkedHashMap<>();
        
        result.put("FR", soutenances.stream()
                .filter(s -> s.getEtudiant() != null && s.getEtudiant().getLangue() == Etudiant.Langue.FR)
                .count());
        result.put("EN", soutenances.stream()
                .filter(s -> s.getEtudiant() != null && s.getEtudiant().getLangue() == Etudiant.Langue.EN)
                .count());
                
        return result;
    }

    

    public long getTotalSoutenances() {
        return soutenanceRepository.count();
    }

    public long getTotalProfesseurs() {
        return professeurRepository.count();
    }

    public long getTotalEtudiants() {
        return etudiantRepository.count();
    }

    public long getNbJours() {
        return soutenanceRepository.findAll().stream()
                .map(Soutenance::getDate)
                .distinct()
                .count();
    }

    public long getChargeMax() {
        return getChargeParProfesseur().values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
    }
}