package ma.ensa.pfe.service.validation.rules;

import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.validation.ValidationResult;
import ma.ensa.pfe.service.validation.ValidationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class ProfessorDoubleBookingRule implements ValidationRule<List<Soutenance>> {
    
    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (soutenances == null || soutenances.size() < 2) {
            return results;
        }
        
        
        Map<ProfTimeKey, List<Soutenance>> parProfHeure = new HashMap<>();
        
        for (Soutenance s : soutenances) {
            if (s.getDate() == null || s.getHeure() == null) continue;
            
            
            addSoutenance(parProfHeure, s.getEncadrant(), s);
            
            addSoutenance(parProfHeure, s.getJury1(), s);
            addSoutenance(parProfHeure, s.getJury2(), s);
            addSoutenance(parProfHeure, s.getJury3(), s);
        }
        
        for (Map.Entry<ProfTimeKey, List<Soutenance>> entry : parProfHeure.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<Soutenance> conflits = entry.getValue();
                
                
                String etudiants = conflits.stream()
                    .map(s -> {
                        Etudiant e = s.getEtudiant();
                        return (e != null ? e.getNom() + " " + e.getPrenom() : "Inconnu");
                    })
                    .collect(Collectors.joining(", "));
                
                results.add(ValidationResult.error(
                    getRuleName(),
                    String.format("Prof. %s affecté à %d soutenances le %s à %s : [%s]",
                        entry.getKey().prof.getNom() + " " + entry.getKey().prof.getPrenom(),
                        conflits.size(),
                        entry.getKey().date,
                        entry.getKey().heure,
                        etudiants),
                    String.format("%s - %s %s", 
                        entry.getKey().prof.getNom() + " " + entry.getKey().prof.getPrenom(),
                        entry.getKey().date, 
                        entry.getKey().heure)
                ));
            }
        }
        
        return results;
    }
    
    private void addSoutenance(Map<ProfTimeKey, List<Soutenance>> map, Professeur prof, Soutenance s) {
        if (prof == null) return;
        ProfTimeKey key = new ProfTimeKey(prof, s.getDate(), s.getHeure());
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    
    @Override
    public String getRuleName() {
        return "PROFESSOR_DOUBLE_BOOKING";
    }
    
    @Override
    public String getDescription() {
        return "Détecte si un professeur est affecté à deux soutenances prévues au même horaire exact";
    }
    
    
    private static class ProfTimeKey {
        private final Professeur prof;
        private final LocalDate date;
        private final LocalTime heure;
        
        public ProfTimeKey(Professeur prof, LocalDate date, LocalTime heure) {
            this.prof = prof;
            this.date = date;
            this.heure = heure;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProfTimeKey)) return false;
            ProfTimeKey that = (ProfTimeKey) o;
            return Objects.equals(prof.getId(), that.prof.getId()) 
                && Objects.equals(date, that.date) 
                && Objects.equals(heure, that.heure);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(prof.getId(), date, heure);
        }
    }
}