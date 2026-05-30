package ma.ensa.pfe.service.validation;

import ma.ensa.pfe.model.Soutenance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class JuryEquityRule implements ValidationRule<List<Soutenance>> {

    private static final int SEUIL_DESEQUILIBRE = 2;

    @Override
    public List<ValidationResult> validate(List<Soutenance> soutenances) {
        List<ValidationResult> results = new ArrayList<>();

        Map<String, Long> presenceJury = new HashMap<>();

        for (Soutenance s : soutenances) {
            if (s.getJury2() != null) {
                presenceJury.merge(
                    s.getJury2().getNomComplet(), 1L, Long::sum);
            }
            if (s.getJury3() != null) {
                presenceJury.merge(
                    s.getJury3().getNomComplet(), 1L, Long::sum);
            }
        }

        if (presenceJury.isEmpty()) return results;

        // Calculer la moyenne de présence en jury
        double moyenne = presenceJury.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        long max = presenceJury.values().stream()
                .mapToLong(Long::longValue).max().orElse(0);
        long min = presenceJury.values().stream()
                .mapToLong(Long::longValue).min().orElse(0);

        
        if ((max - min) > SEUIL_DESEQUILIBRE) {
           
            presenceJury.forEach((nom, charge) -> {
                if (charge > moyenne + SEUIL_DESEQUILIBRE) {
                    results.add(ValidationResult.warning(
                        getRuleName(),
                        String.format(
                            "%s est surchargé en jury : %d présences (moyenne : %.1f)",
                            nom, charge, moyenne),
                        nom
                    ));
                } else if (charge < moyenne - SEUIL_DESEQUILIBRE) {
                    results.add(ValidationResult.warning(
                        getRuleName(),
                        String.format(
                            "%s est sous-chargé en jury : %d présences (moyenne : %.1f)",
                            nom, charge, moyenne),
                        nom
                    ));
                }
            });
        }

        return results;
    }

    @Override
    public String getRuleName() {
        return "JuryEquityRule";
    }

    @Override
    public String getDescription() {
        return "Vérifie que tous les profs assistent à un nombre équitable de soutenances en tant que jury";
    }

    @Override
    public Severity getSeverity() {
        return Severity.WARNING;
    }
}