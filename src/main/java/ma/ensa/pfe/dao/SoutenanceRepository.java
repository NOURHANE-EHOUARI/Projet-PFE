package ma.ensa.pfe.dao;

import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.model.Salle;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.VersionPlanning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface SoutenanceRepository extends JpaRepository<Soutenance, Long> {

    
    @Query("SELECT s FROM Soutenance s " +
           "JOIN FETCH s.etudiant e " +
           "JOIN FETCH s.encadrant enc " +
           "LEFT JOIN FETCH s.jury1 j1 " +
           "LEFT JOIN FETCH s.jury2 j2 " +
           "LEFT JOIN FETCH s.jury3 j3 " +
           "JOIN FETCH s.salle sal " +
           "WHERE s.version = :version")
    List<Soutenance> findByVersion(@Param("version") VersionPlanning version);

    
    @Query("SELECT s FROM Soutenance s JOIN FETCH s.etudiant JOIN FETCH s.encadrant " +
           "LEFT JOIN FETCH s.jury1 LEFT JOIN FETCH s.jury2 LEFT JOIN FETCH s.jury3 " +
           "JOIN FETCH s.salle WHERE s.date = :date")
    List<Soutenance> findByDateWithDetails(@Param("date") LocalDate date);

    
    List<Soutenance> findByDate(LocalDate date);

    
    List<Soutenance> findBySalle(Salle salle);

    List<Soutenance> findBySalleAndDate(Salle salle, LocalDate date);

    @Query("SELECT s FROM Soutenance s WHERE s.date = :date AND " +
           "(s.encadrant = :prof OR s.jury1 = :prof OR s.jury2 = :prof OR s.jury3 = :prof)")
    List<Soutenance> findByDateAndProf(
        @Param("date") LocalDate date,
        @Param("prof") Professeur prof
    );

    
    @Query("SELECT COUNT(s) > 0 FROM Soutenance s WHERE s.salle = :salle " +
           "AND s.date = :date AND s.heure = :heure")
    boolean existsConflitSalle(
        @Param("salle") Salle salle,
        @Param("date") LocalDate date,
        @Param("heure") LocalTime heure
    );
    
   
    @Query("SELECT COUNT(s) > 0 FROM Soutenance s WHERE s.date = :date AND " +
           "s.heure = :heure AND (s.encadrant = :prof OR s.jury1 = :prof OR s.jury2 = :prof OR s.jury3 = :prof)")
    boolean isProfBusyAt(
        @Param("date") LocalDate date,
        @Param("heure") LocalTime heure,
        @Param("prof") Professeur prof
    );
    @Query("SELECT s FROM Soutenance s " +
           "LEFT JOIN FETCH s.etudiant e " +
           "LEFT JOIN FETCH e.encadrant " +
           "LEFT JOIN FETCH s.encadrant " +
           "LEFT JOIN FETCH s.jury1 " +
           "LEFT JOIN FETCH s.jury2 " +
           "LEFT JOIN FETCH s.jury3 " +
           "LEFT JOIN FETCH s.salle")
    List<Soutenance> findAllWithDetails();
}