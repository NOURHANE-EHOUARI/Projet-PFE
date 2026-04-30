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

    // Charge TOUT en une seule requête — évite LazyInitializationException
    @Query("SELECT s FROM Soutenance s " +
           "LEFT JOIN FETCH s.etudiant e " +
           "LEFT JOIN FETCH e.encadrant " +
           "LEFT JOIN FETCH s.encadrant " +
           "LEFT JOIN FETCH s.jury1 " +
           "LEFT JOIN FETCH s.jury2 " +
           "LEFT JOIN FETCH s.salle")
    List<Soutenance> findAllWithDetails();

    List<Soutenance> findByDate(LocalDate date);
    List<Soutenance> findBySalle(Salle salle);
    List<Soutenance> findBySalleAndDate(Salle salle, LocalDate date);

    @Query("SELECT s FROM Soutenance s WHERE s.date = :date AND " +
           "(s.encadrant = :prof OR s.jury1 = :prof OR s.jury2 = :prof)")
    List<Soutenance> findByDateAndProf(
        @Param("date") LocalDate date,
        @Param("prof") Professeur prof);

    @Query("SELECT COUNT(s) > 0 FROM Soutenance s WHERE s.salle = :salle " +
           "AND s.date = :date AND s.heure = :heure")
    boolean existsConflitSalle(
        @Param("salle") Salle salle,
        @Param("date") LocalDate date,
        @Param("heure") LocalTime heure);
}