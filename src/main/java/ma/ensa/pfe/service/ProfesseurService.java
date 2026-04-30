package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.ContrainteRepository;
import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.model.Contrainte;
import ma.ensa.pfe.model.Professeur;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@Transactional
public class ProfesseurService {

    @Autowired
    private ProfesseurRepository professeurRepository;

    @Autowired
    private ContrainteRepository contrainteRepository;

    @Autowired
    private EtudiantRepository etudiantRepository;

    // ===== LECTURE =====

    @Transactional(readOnly = true)
    public List<Professeur> findAll() {
        return professeurRepository.findAllWithContraintes();
    }

    @Transactional(readOnly = true)
    public Professeur findById(Long id) {
        return professeurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Professeur introuvable : id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Professeur> findBySpecialite(String specialite) {
        return professeurRepository.findBySpecialite(specialite);
    }

    @Transactional(readOnly = true)
    public List<Professeur> findAnglophones() {
        return professeurRepository.findByParleAnglais(true);
    }

    @Transactional(readOnly = true)
    public List<String> findAllSpecialites() {
        return professeurRepository.findAllSpecialites();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return professeurRepository.count();
    }

    @Transactional(readOnly = true)
    public long countAnglophones() {
        return professeurRepository.findByParleAnglais(Boolean.TRUE).size();
    }

    // ===== ÉCRITURE =====

    public Professeur save(Professeur professeur) {
        // Si mode édition, vérifier que le professeur existe réellement en base
        if (professeur.getId() != null) {
            professeurRepository.findById(professeur.getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Professeur introuvable : id=" + professeur.getId()));
        }

        // Validation : nom et prénom obligatoires (en plus de @NotBlank)
        if (professeur.getNom() == null || professeur.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom du professeur est obligatoire.");
        }
        if (professeur.getPrenom() == null || professeur.getPrenom().isBlank()) {
            throw new IllegalArgumentException("Le prénom du professeur est obligatoire.");
        }
        if (professeur.getSpecialite() == null || professeur.getSpecialite().isBlank()) {
            throw new IllegalArgumentException("La spécialité est obligatoire.");
        }

        return professeurRepository.save(professeur);
    }

    public void deleteById(Long id) {
        Professeur prof = professeurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Professeur introuvable : id=" + id));

        // Vérifier qu'aucun étudiant n'est encore encadré par ce professeur
        long nbEtudiants = etudiantRepository.countByEncadrant(prof);
        if (nbEtudiants > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer : ce professeur encadre encore " + nbEtudiants + " étudiant(s).");
        }

        professeurRepository.deleteById(id);
    }

    // ===== CONTRAINTES (indisponibilités) =====

    public Contrainte ajouterContrainte(Long professeurId, Contrainte contrainte) {
        Professeur prof = professeurRepository.findById(professeurId)
                .orElseThrow(() -> new EntityNotFoundException("Professeur introuvable : id=" + professeurId));

        if (contrainte.getDateIndisponible() == null) {
            throw new IllegalArgumentException("La date d'indisponibilité est obligatoire.");
        }

        contrainte.setProfesseur(prof);
        return contrainteRepository.save(contrainte);
    }

    public void supprimerContrainte(Long contrainteId) {
        if (!contrainteRepository.existsById(contrainteId)) {
            throw new EntityNotFoundException("Contrainte introuvable : id=" + contrainteId);
        }
        contrainteRepository.deleteById(contrainteId);
    }

    @Transactional(readOnly = true)
    public List<Contrainte> findContraintesByProfesseur(Long professeurId) {
        Professeur prof = professeurRepository.findById(professeurId)
                .orElseThrow(() -> new EntityNotFoundException("Professeur introuvable : id=" + professeurId));
        return contrainteRepository.findByProfesseur(prof);
    }
}