package ma.ensa.pfe.service.validation.rules;

import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Salle;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.validation.ValidationResult;
import ma.ensa.pfe.service.validation.ValidationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class RoomConflictRule implements ValidationRule<List<Soutenance>> {
    
    private static final int DUREE_SOUTENANCE_MIN = 45; // minutes
    
    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (soutenances == null || soutenances.size() < 2) {
            return results;
        }
        
        
        Map<DateSalleKey, List<Soutenance>> parDateSalle = soutenances.stream()
            .filter(s -> s.getSalle() != null && s.getDate() != null && s.getHeure() != null)
            .collect(Collectors.groupingBy(
                s -> new DateSalleKey(s.getDate(), s.getSalle().getId())
            ));
        
        
        for (Map.Entry<DateSalleKey, List<Soutenance>> entry : parDateSalle.entrySet()) {
            List<Soutenance> groupe = entry.getValue();
            if (groupe.size() < 2) continue;
           
            groupe.sort(Comparator.comparing(Soutenance::getHeure));
         
            for (int i = 0; i < groupe.size() - 1; i++) {
                Soutenance s1 = groupe.get(i);
                Soutenance s2 = groupe.get(i + 1);
                
                LocalTime fin1 = s1.getHeure().plusMinutes(DUREE_SOUTENANCE_MIN);
                
                if (!fin1.isBefore(s2.getHeure())) {
                    
                    String etudiant1 = s1.getEtudiant() != null ? 
                        s1.getEtudiant().getNom() + " " + s1.getEtudiant().getPrenom() : "Inconnu";
                    String etudiant2 = s2.getEtudiant() != null ? 
                        s2.getEtudiant().getNom() + " " + s2.getEtudiant().getPrenom() : "Inconnu";
                    
                    results.add(ValidationResult.error(
                        getRuleName(),
                        String.format("Salle '%s' réservée deux fois le %s : %s (%s-%s) et %s (%s-%s)",
                            s1.getSalle().getNom(),
                            s1.getDate(),
                            etudiant1,
                            s1.getHeure(), fin1,
                            etudiant2,
                            s2.getHeure(), s2.getHeure().plusMinutes(DUREE_SOUTENANCE_MIN)),
                        String.format("%s - %s", s1.getDate(), s1.getSalle().getNom())
                    ));
                }
            }
        }
        
        return results;
    }
    
    @Override
    public String getRuleName() {
        return "ROOM_CONFLICT";
    }
    
    @Override
    public String getDescription() {
        return "Détecte les chevauchements de salles (deux soutenances dans la même salle au même créneau)";
    }
    
   
    private static class DateSalleKey {
        private final LocalDate date;
        private final Long salleId;
        
        public DateSalleKey(LocalDate date, Long salleId) {
            this.date = date;
            this.salleId = salleId;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DateSalleKey)) return false;
            DateSalleKey that = (DateSalleKey) o;
            return Objects.equals(date, that.date) && Objects.equals(salleId, that.salleId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(date, salleId);
        }
    }
}