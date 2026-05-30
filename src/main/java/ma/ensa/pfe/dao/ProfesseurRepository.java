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

    
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.specialite = :specialite")
    List<Professeur> findBySpecialite(@Param("specialite") String specialite);

    
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.parleAnglais = :parleAnglais")
    List<Professeur> findByParleAnglais(@Param("parleAnglais") Boolean parleAnglais);

   
    @Query("SELECT DISTINCT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres LEFT JOIN FETCH p.contraintes WHERE p.specialite = :specialite AND p.parleAnglais = :parleAnglais")
    List<Professeur> findBySpecialiteAndParleAnglais(@Param("specialite") String specialite, @Param("parleAnglais") Boolean parleAnglais);

    Optional<Professeur> findByNomAndPrenom(String nom, String prenom);

    @Query("SELECT DISTINCT p.specialite FROM Professeur p ORDER BY p.specialite")
    List<String> findAllSpecialites();

    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.contraintes ORDER BY p.nom")
    List<Professeur> findAllWithContraintes();
    
    
    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.etudiantsEncadres ORDER BY p.nom")
    List<Professeur> findAllWithEtudiants();
    

    @Query("SELECT DISTINCT p FROM Professeur p " +
       "LEFT JOIN FETCH p.contraintes " +
       "LEFT JOIN FETCH p.etudiantsEncadres")
    List<Professeur> findAllWithDetails(); 
    
    long countByParleAnglais(boolean parleAnglais);
    long countBySpecialite(String specialite);
}
