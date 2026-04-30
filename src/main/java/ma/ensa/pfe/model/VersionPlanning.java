package ma.ensa.pfe.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "versions_planning")
@Data // Génère automatiquement les Getters, Setters, toString, equals, hashCode
@NoArgsConstructor // Constructeur sans argument requis par JPA
@AllArgsConstructor // Constructeur avec tous les arguments (optionnel mais utile)
public class VersionPlanning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dateGeneration;

    private String description; // Ex: "Version initiale", "Après ajout prof X"

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Soutenance> soutenances;

    // Si tu n'utilises pas Lombok, décommente ces méthodes manuellement :
    /*
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getDateGeneration() { return dateGeneration; }
    public void setDateGeneration(LocalDateTime dateGeneration) { this.dateGeneration = dateGeneration; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Soutenance> getSoutenances() { return soutenances; }
    public void setSoutenances(List<Soutenance> soutenances) { this.soutenances = soutenances; }
    */
}