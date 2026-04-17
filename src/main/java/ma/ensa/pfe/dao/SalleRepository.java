package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Salle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SalleRepository extends JpaRepository<Salle, Long> {

    // Trouver une salle par son nom (S4A, AMPHI A, etc.)
    Optional<Salle> findByNom(String nom);

    // Vérifier si une salle existe déjà
    boolean existsByNom(String nom);
}