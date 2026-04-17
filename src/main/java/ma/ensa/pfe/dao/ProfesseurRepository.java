package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfesseurRepository extends JpaRepository<Professeur, Long> {

    // Recherche par spécialité
    List<Professeur> findBySpecialite(String specialite);

    // Professeurs anglophones (ou non)
    List<Professeur> findByParleAnglais(Boolean parleAnglais);

    // Professeurs d'une spécialité ET anglophones
    List<Professeur> findBySpecialiteAndParleAnglais(String specialite, Boolean parleAnglais);

    // Comptage des anglophones — natif, évite de charger la liste entière
    long countByParleAnglais(Boolean parleAnglais);

    // Toutes les spécialités distinctes (pour le filtre)
    @Query("SELECT DISTINCT p.specialite FROM Professeur p ORDER BY p.specialite")
    List<String> findAllSpecialites();

    // Professeurs avec leurs contraintes (évite N+1 queries)
    @Query("SELECT p FROM Professeur p LEFT JOIN FETCH p.contraintes ORDER BY p.nom")
    List<Professeur> findAllWithContraintes();
}