package ma.ensa.pfe.service.validation;

import java.util.List;


public interface ValidationRule<T> {
    
    
    List<ValidationResult> validate(T data);
    
    
    String getRuleName();
    
    
    String getDescription();
    default Severity getSeverity() {
        return Severity.WARNING;
    }
}