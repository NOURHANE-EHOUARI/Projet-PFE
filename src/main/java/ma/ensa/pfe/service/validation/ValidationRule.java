package ma.ensa.pfe.service.validation;

import java.util.List;

/**
 * Interface commune pour toutes les règles de validation.
 * Principe Open/Closed : on peut ajouter de nouvelles règles sans modifier le code existant.
 * @param <T> Type de données à valider (ex: List<Soutenance>)
 */
public interface ValidationRule<T> {
    
    /**
     * Exécute la validation sur les données fournies.
     * @param data Les données à valider
     * @return Liste des résultats (vides si tout est OK)
     */
    List<ValidationResult> validate(T data);
    
    /**
     * Nom technique de la règle (pour logs/debug).
     */
    String getRuleName();
    
    /**
     * Description lisible par l'utilisateur.
     */
    String getDescription();
    
    /**
     * Niveau de sévérité : ERROR (bloque) ou WARNING (alerte).
     */
    default Severity getSeverity() {
        return Severity.WARNING;
    }
}