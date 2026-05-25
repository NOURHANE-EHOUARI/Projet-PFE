package ma.ensa.pfe.model;

import jakarta.persistence.*;

@Entity
@Table(name = "planning_config")
public class PlanningConfig {

    @Id
    @Column(name = "cle", nullable = false, unique = true)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "description")
    private String description;

    public PlanningConfig() {}

    public PlanningConfig(String cle, String valeur, String description) {
        this.cle = cle;
        this.valeur = valeur;
        this.description = description;
    }

    public String getCle()                      { return cle; }
    public void setCle(String cle)              { this.cle = cle; }
    public String getValeur()                   { return valeur; }
    public void setValeur(String valeur)        { this.valeur = valeur; }
    public String getDescription()              { return description; }
    public void setDescription(String desc)     { this.description = desc; }
}