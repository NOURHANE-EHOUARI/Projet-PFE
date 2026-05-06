package ma.ensa.pfe.service.validation.rules;

import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.validation.ValidationResult;
import ma.ensa.pfe.service.validation.ValidationRule;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Règle : vérifie que la répartition des étudiants par encadrant est équitable.
 * Moyenne cible : 3-4 étudiants par prof. Écart significatif = ±2 de la moyenne.
 */
@Component
public class AssignmentEquityRule implements ValidationRule<List<Soutenance>> {
    
    private static final double MOYENNE_CIBLE_MIN = 3.0;
    private static final double MOYENNE_CIBLE_MAX = 4.0;
    private static final double ECART_MAX_TOLERANCE = 2.0; // ±2 étudiants
    
    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (soutenances == null || soutenances.isEmpty()) {
            return results; // Rien à valider
        }
        
        // Compter le nombre d'étudiants encadrés par prof
        Map<Professeur, Long> chargeParProf = soutenances.stream()
            .map(Soutenance::getEncadrant)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(p -> p, Collectors.counting()));
        
        if (chargeParProf.isEmpty()) {
            return results;
        }
        
        // Calculer la moyenne réelle
        double moyenneReelle = chargeParProf.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        // Vérifier chaque prof
        for (Map.Entry<Professeur, Long> entry : chargeParProf.entrySet()) {
            Professeur prof = entry.getKey();
            long charge = entry.getValue();
            double ecart = Math.abs(charge - moyenneReelle);
            
            // ✅ CORRECTION : utiliser getNom() + " " + getPrenom() au lieu de getNomComplet()
            String nomCompletProf = prof.getNom() + " " + prof.getPrenom();
            
            // Si la moyenne globale est dans la cible [3-4] mais qu'un prof est très éloigné
            if (moyenneReelle >= MOYENNE_CIBLE_MIN && moyenneReelle <= MOYENNE_CIBLE_MAX) {
                if (ecart > ECART_MAX_TOLERANCE) {
                    results.add(ValidationResult.warning(
                        getRuleName(),
                        String.format("Prof. %s encadre %d étudiants (moyenne: %.1f, écart: +%.1f)",
                            nomCompletProf, charge, moyenneReelle, ecart),
                        nomCompletProf
                    ));
                }
            }
            // Si la moyenne globale est hors cible, on alerte sur tous les écarts > 1
            else if (ecart > 1.0) {
                String sens = charge > moyenneReelle ? "+" : "";
                results.add(ValidationResult.warning(
                    getRuleName(),
                    String.format("Prof. %s : %d étudiants (moyenne globale: %.1f, écart: %s%.1f)",
                        nomCompletProf, charge, moyenneReelle, sens, ecart),
                    nomCompletProf
                ));
            }
        }
        
        return results;
    }
    
    @Override
    public String getRuleName() {
        return "ASSIGNMENT_EQUITY";
    }
    
    @Override
    public String getDescription() {
        return "Vérifie la répartition équitable des étudiants encadrés (cible: 3-4 étudiants/prof)";
    }
}