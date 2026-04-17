package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Contrainte;
import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContrainteRepository extends JpaRepository<Contrainte, Long> {

    // Contraintes d'un professeur
    List<Contrainte> findByProfesseur(Professeur professeur);

    // Contraintes d'un professeur à une date donnée
    List<Contrainte> findByProfesseurAndDateIndisponible(Professeur professeur, LocalDate date);
}