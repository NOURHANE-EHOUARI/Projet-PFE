package ma.ensa.pfe.service.validation;

/**
 * Niveau de sévérité d'un résultat de validation.
 */
public enum Severity {
    /** Erreur bloquante : la génération doit être rejetée */
    ERROR,
    
    /** Avertissement : l'utilisateur est notifié mais peut continuer */
    WARNING
}