package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Contrainte;
import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContrainteRepository extends JpaRepository<Contrainte, Long> {

    
    List<Contrainte> findByProfesseur(Professeur professeur);

    
    List<Contrainte> findByProfesseurAndDateIndisponible(Professeur professeur, LocalDate date);
}