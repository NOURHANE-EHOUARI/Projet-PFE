package ma.ensa.pfe.service.validation;

import ma.ensa.pfe.model.Soutenance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service principal qui orchestre l'exécution de toutes les règles de validation.
 * Respecte le principe Open/Closed : pour ajouter une règle, il suffit de l'injecter.
 */
@Service
public class ValidationService {
    
    private final List<ValidationRule<List<Soutenance>>> rules;
    
    @Autowired
    public ValidationService(List<ValidationRule<List<Soutenance>>> rules) {
        this.rules = rules;
    }
    
    /**
     * Valide une liste de soutenances contre toutes les règles enregistrées.
     */
    public ValidationSummary validatePlanning(List<Soutenance> soutenances) {
        ValidationSummary summary = new ValidationSummary();
        summary.setValid(true);
        
        for (ValidationRule<List<Soutenance>> rule : rules) {
            List<ValidationResult> results = rule.validate(soutenances);
            for (ValidationResult result : results) {
                summary.addResult(result);
            }
        }
        
        return summary;
    }
    
    /**
     * Valide et lance une exception si des erreurs bloquantes sont trouvées.
     */
    public void validateOrThrow(List<Soutenance> soutenances) {
        ValidationSummary summary = validatePlanning(soutenances);
        if (!summary.isValid() && !summary.getErrors().isEmpty()) {
            throw new PlanningValidationException(summary.getFormattedReport());
        }
    }
    
    /**
     * Exception personnalisée pour les échecs de validation.
     */
    public static class PlanningValidationException extends RuntimeException {
        public PlanningValidationException(String message) {
            super(message);
        }
    }
}