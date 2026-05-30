package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.VersionPlanning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VersionPlanningRepository extends JpaRepository<VersionPlanning, Long> {

   
    List<VersionPlanning> findAllByOrderByDateGenerationDesc();
    VersionPlanning findFirstByOrderByDateGenerationDesc();
}