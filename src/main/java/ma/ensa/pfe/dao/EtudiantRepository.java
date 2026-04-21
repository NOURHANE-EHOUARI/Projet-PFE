package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {

    @Query("SELECT e FROM Etudiant e JOIN FETCH e.encadrant WHERE e.filiere = :filiere")
    List<Etudiant> findByFiliere(@Param("filiere") Filiere filiere);

    @Query("SELECT e FROM Etudiant e JOIN FETCH e.encadrant WHERE e.langue = :langue")
    List<Etudiant> findByLangue(@Param("langue") Langue langue);

    List<Etudiant> findByEncadrant(Professeur encadrant);
    
    @Query("SELECT e FROM Etudiant e JOIN FETCH e.encadrant WHERE e.filiere = :filiere AND e.langue = :langue")
    List<Etudiant> findByFiliereAndLangue(@Param("filiere") Filiere filiere, @Param("langue") Langue langue);

    long countByFiliere(Filiere filiere);
    long countByEncadrant(Professeur encadrant);

    Optional<Etudiant> findByNomAndPrenom(String nom, String prenom);

    @Query("SELECT e FROM Etudiant e JOIN FETCH e.encadrant ORDER BY e.nom")
    List<Etudiant> findAllWithEncadrant();
}