package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfesseurRepository extends JpaRepository<Professeur, Long> {

    // ✅ Avec JOIN FETCH pour charger les étudiants
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.specialite = :specialite")
    List<Professeur> findBySpecialite(@Param("specialite") String specialite);

    // ✅ Avec JOIN FETCH
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.parleAnglais = :parleAnglais")
    List<Professeur> findByParleAnglais(@Param("parleAnglais") Boolean parleAnglais);

    // ✅ Avec JOIN FETCH pour les deux critères
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.specialite = :specialite AND p.parleAnglais = :parleAnglais")
    List<Professeur> findBySpecialiteAndParleAnglais(@Param("specialite") String specialite, @Param("parleAnglais") Boolean parleAnglais);

    Optional<Professeur> findByNomAndPrenom(String nom, String prenom);

    @Query("SELECT DISTINCT p.specialite FROM Professeur p ORDER BY p.specialite")
    List<String> findAllSpecialites();

    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.contraintes ORDER BY p.nom")
    List<Professeur> findAllWithContraintes();
    
    // ✅ Méthode pour charger TOUS les profs avec étudiants
    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres ORDER BY p.nom")
    List<Professeur> findAllWithEtudiants();
    
// Dans l'interface, ajoute :
    @Query("SELECT DISTINCT p FROM Professeur p " +
       "LEFT JOIN FETCH p.contraintes " +
       "LEFT JOIN FETCH p.etudiantsEncadres")
    List<Professeur> findAllWithDetails(); 
    // ✅ Comptages
    long countByParleAnglais(boolean parleAnglais);
    long countBySpecialite(String specialite);
}
