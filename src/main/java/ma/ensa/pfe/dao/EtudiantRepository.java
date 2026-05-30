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

    
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant ORDER BY e.nom")
    List<Etudiant> findAllWithEncadrant();

   
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant " +
           "WHERE e.filiere = :filiere ORDER BY e.nom")
    List<Etudiant> findByFiliereWithEncadrant(@Param("filiere") Filiere filiere);

    
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant " +
           "WHERE e.langue = :langue ORDER BY e.nom")
    List<Etudiant> findByLangueWithEncadrant(@Param("langue") Langue langue);

    
    List<Etudiant> findByFiliere(Filiere filiere);
    List<Etudiant> findByLangue(Langue langue);
    List<Etudiant> findByEncadrant(Professeur encadrant);
    List<Etudiant> findByFiliereAndLangue(Filiere filiere, Langue langue);

    
    long countByFiliere(Filiere filiere);
    long countByEncadrant(Professeur encadrant);
    
    
    long countByEncadrantIsNull();

    
    Optional<Etudiant> findByCne(String cne);

   
    Optional<Etudiant> findByNomAndPrenom(String nom, String prenom);
}