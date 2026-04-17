package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.model.Salle;
import ma.ensa.pfe.model.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface SoutenanceRepository extends JpaRepository<Soutenance, Long> {

    // Toutes les soutenances d'une date donnée
    List<Soutenance> findByDate(LocalDate date);

    // Toutes les soutenances d'une salle donnée
    List<Soutenance> findBySalle(Salle salle);

    // Toutes les soutenances d'une salle à une date donnée
    List<Soutenance> findBySalleAndDate(Salle salle, LocalDate date);

    // Soutenances où un prof est encadrant OU jury — utile pour la contrainte 1h
    @Query("SELECT s FROM Soutenance s WHERE s.date = :date AND " +
           "(s.encadrant = :prof OR s.jury1 = :prof OR s.jury2 = :prof)")
    List<Soutenance> findByDateAndProf(
        @Param("date") LocalDate date,
        @Param("prof") Professeur prof
    );

    // Vérifier conflit salle + créneau
    @Query("SELECT COUNT(s) > 0 FROM Soutenance s WHERE s.salle = :salle " +
           "AND s.date = :date AND s.heure = :heure")
    boolean existsConflitSalle(
        @Param("salle") Salle salle,
        @Param("date") LocalDate date,
        @Param("heure") LocalTime heure
    );
}