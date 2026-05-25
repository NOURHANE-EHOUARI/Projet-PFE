package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.PlanningConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PlanningConfigRepository extends JpaRepository<PlanningConfig, String> {
    Optional<PlanningConfig> findByCle(String cle);
}