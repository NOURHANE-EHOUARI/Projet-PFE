package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.VersionPlanning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VersionPlanningRepository extends JpaRepository<VersionPlanning, Long> {

    /**
     * Récupère toutes les versions de planning, triées de la plus récente à la plus ancienne.
     * Utile pour afficher l'historique des régénérations dans l'interface.
     */
    List<VersionPlanning> findAllByOrderByDateGenerationDesc();

    /**
     * Optionnel : Trouver la dernière version générée.
     */
    VersionPlanning findFirstByOrderByDateGenerationDesc();
}