package ma.ensa.pfe.service.validation.rules;

import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.validation.ValidationResult;
import ma.ensa.pfe.service.validation.ValidationRule;
import org.springframework.stereotype.Component;
//AJOUTER cet import
import ma.ensa.pfe.service.validation.Severity;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Règle : vérifie que la répartition des étudiants par encadrant est équitable.
 * Moyenne cible : 3-4 étudiants par prof. Écart significatif = ±2 de la moyenne.
 */
@Component
public class AssignmentEquityRule implements ValidationRule<List<Soutenance>> {
    
    private static final Set<String> EXCLUS = Set.of("ANGLAIS", "GESTION");
    
    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();

        //  Compter uniquement les encadrements
        Map<String, Long> chargeEncadrement = new HashMap<>();
        for (Soutenance s : soutenances) {
            if (s.getEncadrant() == null) continue;
            String sp = s.getEncadrant().getSpecialite();
            if (sp != null && EXCLUS.contains(sp.toUpperCase().trim())) continue;
            chargeEncadrement.merge(
                s.getEncadrant().getNomComplet(), 1L, Long::sum);
        }

        if (chargeEncadrement.isEmpty()) return results;

        double moyenne = chargeEncadrement.values().stream()
                .mapToLong(Long::longValue).average().orElse(0);

        chargeEncadrement.forEach((nom, charge) -> {
            if (Math.abs(charge - moyenne) > 2) {
                results.add(ValidationResult.warning(
                    getRuleName(),
                    String.format(
                        "Charge encadrement déséquilibrée pour %s : %d (moyenne : %.1f)",
                        nom, charge, moyenne),
                    nom
                ));
            }
        });

        return results;
    }

    @Override public String getRuleName()    { return "AssignmentEquityRule"; }
    @Override public String getDescription() { return "Équité du nombre d'étudiants encadrés"; }
    @Override public Severity getSeverity()  { return Severity.WARNING; }
}