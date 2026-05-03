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

    // ✅ LEFT JOIN FETCH — inclut les étudiants AVEC et SANS encadrant
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant ORDER BY e.nom")
    List<Etudiant> findAllWithEncadrant();

    // ✅ LEFT JOIN FETCH par filière
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant " +
           "WHERE e.filiere = :filiere ORDER BY e.nom")
    List<Etudiant> findByFiliereWithEncadrant(@Param("filiere") Filiere filiere);

    // ✅ LEFT JOIN FETCH par langue
    @Query("SELECT e FROM Etudiant e LEFT JOIN FETCH e.encadrant " +
           "WHERE e.langue = :langue ORDER BY e.nom")
    List<Etudiant> findByLangueWithEncadrant(@Param("langue") Langue langue);

    // Filtres simples
    List<Etudiant> findByFiliere(Filiere filiere);
    List<Etudiant> findByLangue(Langue langue);
    List<Etudiant> findByEncadrant(Professeur encadrant);
    List<Etudiant> findByFiliereAndLangue(Filiere filiere, Langue langue);

    // Comptages
    long countByFiliere(Filiere filiere);
    long countByEncadrant(Professeur encadrant);

    // Recherche par CNE (identifiant unique étudiant)
    Optional<Etudiant> findByCne(String cne);

    // Recherche par nom + prénom (upsert dans l'import)
    Optional<Etudiant> findByNomAndPrenom(String nom, String prenom);
}