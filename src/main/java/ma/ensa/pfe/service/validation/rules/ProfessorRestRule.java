package ma.ensa.pfe.service.validation.rules;

import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.validation.ValidationResult;
import ma.ensa.pfe.service.validation.ValidationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Règle : vérifie qu'un professeur a au moins 1 heure de repos entre deux soutenances successives.
 */
@Component
public class ProfessorRestRule implements ValidationRule<List<Soutenance>> {
    
    private static final int REPOS_MINIMUM_MINUTES = 60; // 1 heure
    private static final int DUREE_SOUTENANCE_MIN = 45;
    
    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (soutenances == null || soutenances.size() < 2) {
            return results;
        }
        
        // Grouper par prof et date
        Map<ProfDateKey, List<Soutenance>> parProfDate = new HashMap<>();
        
        for (Soutenance s : soutenances) {
            if (s.getDate() == null || s.getHeure() == null) continue;
            
            // Ajouter pour l'encadrant
            addSoutenance(parProfDate, s.getEncadrant(), s);
            // Ajouter pour les membres du jury
            addSoutenance(parProfDate, s.getJury1(), s);
            addSoutenance(parProfDate, s.getJury2(), s);
            addSoutenance(parProfDate, s.getJury3(), s);
        }
        
        // Vérifier le repos pour chaque (prof, date)
        for (Map.Entry<ProfDateKey, List<Soutenance>> entry : parProfDate.entrySet()) {
            List<Soutenance> groupe = entry.getValue();
            if (groupe.size() < 2) continue;
            
            // Trier par heure de début
            groupe.sort(Comparator.comparing(Soutenance::getHeure));
            
            // Vérifier le temps entre chaque paire consécutive
            for (int i = 0; i < groupe.size() - 1; i++) {
                Soutenance s1 = groupe.get(i);
                Soutenance s2 = groupe.get(i + 1);
                
                LocalTime fin1 = s1.getHeure().plusMinutes(DUREE_SOUTENANCE_MIN);
                long minutesRepos = ChronoUnit.MINUTES.between(fin1, s2.getHeure());
                
                if (minutesRepos < REPOS_MINIMUM_MINUTES && minutesRepos >= 0) {
                    // ✅ CORRECTION : utiliser getNom() + getPrenom()
                    String nomProf = entry.getKey().prof.getNom() + " " + entry.getKey().prof.getPrenom();
                    String etudiant1 = s1.getEtudiant() != null ? 
                        s1.getEtudiant().getNom() + " " + s1.getEtudiant().getPrenom() : "Inconnu";
                    String etudiant2 = s2.getEtudiant() != null ? 
                        s2.getEtudiant().getNom() + " " + s2.getEtudiant().getPrenom() : "Inconnu";
                    
                    results.add(ValidationResult.error(
                        getRuleName(),
                        String.format("Prof. %s : seulement %d min de repos entre %s (%s-%s) et %s (%s-%s) le %s",
                            nomProf,
                            minutesRepos,
                            etudiant1,
                            s1.getHeure(), fin1,
                            etudiant2,
                            s2.getHeure(), s2.getHeure().plusMinutes(DUREE_SOUTENANCE_MIN),
                            s1.getDate()),
                        String.format("%s - %s", nomProf, s1.getDate())
                    ));
                }
            }
        }
        
        return results;
    }
    
    private void addSoutenance(Map<ProfDateKey, List<Soutenance>> map, Professeur prof, Soutenance s) {
        if (prof == null) return;
        ProfDateKey key = new ProfDateKey(prof, s.getDate());
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    
    @Override
    public String getRuleName() {
        return "PROFESSOR_REST";
    }
    
    @Override
    public String getDescription() {
        return "Vérifie qu'un professeur a au moins 1 heure de repos entre deux soutenances successives";
    }
    
    // Clé composite pour le grouping
    private static class ProfDateKey {
        private final Professeur prof;
        private final LocalDate date;
        
        public ProfDateKey(Professeur prof, LocalDate date) {
            this.prof = prof;
            this.date = date;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProfDateKey)) return false;
            ProfDateKey that = (ProfDateKey) o;
            return Objects.equals(prof.getId(), that.prof.getId()) && Objects.equals(date, that.date);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(prof.getId(), date);
        }
    }
}