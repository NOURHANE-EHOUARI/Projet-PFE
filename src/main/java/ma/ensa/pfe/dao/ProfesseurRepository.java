package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfesseurRepository extends JpaRepository<Professeur, Long> {

    List<Professeur> findBySpecialite(String specialite);
    List<Professeur> findByParleAnglais(Boolean parleAnglais);
    List<Professeur> findBySpecialiteAndParleAnglais(String specialite, Boolean parleAnglais);

    // ✅ Requis par ExcelImportService pour l'upsert
    Optional<Professeur> findByNomAndPrenom(String nom, String prenom);

    @Query("SELECT DISTINCT p.specialite FROM Professeur p ORDER BY p.specialite")
    List<String> findAllSpecialites();

    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.contraintes ORDER BY p.nom")
    List<Professeur> findAllWithContraintes();
}