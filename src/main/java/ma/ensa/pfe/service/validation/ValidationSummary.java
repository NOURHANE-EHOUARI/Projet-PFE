package ma.ensa.pfe.service.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Résumé global de toutes les validations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSummary {
    
    private boolean valid;
    private List<ValidationResult> errors = new ArrayList<>();
    private List<ValidationResult> warnings = new ArrayList<>();
    
    public void addResult(ValidationResult result) {
        if (result.isError()) {
            errors.add(result);
            valid = false;
        } else if (result.getMessage() != null) {
            warnings.add(result);
        }
    }
    
    public void merge(ValidationSummary other) {
        errors.addAll(other.getErrors());
        warnings.addAll(other.getWarnings());
        if (!other.isValid()) {
            valid = false;
        }
    }
    
    public String getFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RAPPORT DE VALIDATION ===\n");
        sb.append("Statut : ").append(valid ? "✅ VALIDE" : "❌ INVALIDE").append("\n\n");
        
        if (!errors.isEmpty()) {
            sb.append("🔴 ERREURS (").append(errors.size()).append(") :\n");
            errors.forEach(e -> sb.append("  • ").append(e.getMessage())
                .append(e.getContext() != null ? " [" + e.getContext() + "]" : "")
                .append("\n"));
            sb.append("\n");
        }
        
        if (!warnings.isEmpty()) {
            sb.append("🟡 AVERTISSEMENTS (").append(warnings.size()).append(") :\n");
            warnings.forEach(w -> sb.append("  • ").append(w.getMessage())
                .append(w.getContext() != null ? " [" + w.getContext() + "]" : "")
                .append("\n"));
        }
        
        return sb.toString();
    }
}