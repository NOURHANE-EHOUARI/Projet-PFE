package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {

    List<Etudiant> findByFiliere(Filiere filiere);
    List<Etudiant> findByLangue(Langue langue);
    List<Etudiant> findByEncadrant(Professeur encadrant);
    List<Etudiant> findByFiliereAndLangue(Filiere filiere, Langue langue);

    // Comptage natif — évite de charger toute la liste juste pour compter
    long countByFiliere(Filiere filiere);

    // Utilisé par ProfesseurService pour bloquer la suppression si étudiants liés
    long countByEncadrant(Professeur encadrant);

    @Query("SELECT e FROM Etudiant e JOIN FETCH e.encadrant ORDER BY e.nom")
    List<Etudiant> findAllWithEncadrant();
}