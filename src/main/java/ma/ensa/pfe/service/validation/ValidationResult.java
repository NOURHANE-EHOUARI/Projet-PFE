package ma.ensa.pfe.service.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private String ruleName;
    private Severity severity;
    private String message;
    private Object context; 
    
    public static ValidationResult ok(String ruleName) {
        return new ValidationResult(ruleName, Severity.WARNING, null, null);
    }
    
    public static ValidationResult error(String ruleName, String message, Object context) {
        return new ValidationResult(ruleName, Severity.ERROR, message, context);
    }
    
    public static ValidationResult warning(String ruleName, String message, Object context) {
        return new ValidationResult(ruleName, Severity.WARNING, message, context);
    }
    
    public boolean isError() {
        return severity == Severity.ERROR;
    }
}