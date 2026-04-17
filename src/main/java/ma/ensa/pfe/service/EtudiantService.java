package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.model.Professeur;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@Transactional
public class EtudiantService {

    @Autowired
    private EtudiantRepository etudiantRepository;

    @Autowired
    private ProfesseurRepository professeurRepository;

    // ===== LECTURE =====

    @Transactional(readOnly = true)
    public List<Etudiant> findAll() {
        return etudiantRepository.findAllWithEncadrant();
    }

    @Transactional(readOnly = true)
    public Etudiant findById(Long id) {
        return etudiantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable : id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Etudiant> findByFiliere(Filiere filiere) {
        return etudiantRepository.findByFiliere(filiere);
    }

    @Transactional(readOnly = true)
    public List<Etudiant> findByLangue(Langue langue) {
        return etudiantRepository.findByLangue(langue);
    }

    @Transactional(readOnly = true)
    public long countByFiliere(Filiere filiere) {
        return etudiantRepository.countByFiliere(filiere);
    }

    // ===== ÉCRITURE =====

    public Etudiant save(Etudiant etudiant) {
        // FIX : Si mode édition, vérifier que l'étudiant existe réellement en base
        // Évite qu'un POST avec un id inventé crée silencieusement un nouvel enregistrement
        if (etudiant.getId() != null) {
            etudiantRepository.findById(etudiant.getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Étudiant introuvable : id=" + etudiant.getId()));
        }

        // Validation métier : l'encadrant doit exister en base
        if (etudiant.getEncadrant() == null || etudiant.getEncadrant().getId() == null) {
            throw new IllegalArgumentException("Un encadrant est obligatoire.");
        }

        Professeur encadrant = professeurRepository.findById(etudiant.getEncadrant().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Encadrant invalide : id=" + etudiant.getEncadrant().getId()));
        etudiant.setEncadrant(encadrant);

        return etudiantRepository.save(etudiant);
    }

    public void deleteById(Long id) {
        if (!etudiantRepository.existsById(id)) {
            throw new EntityNotFoundException("Étudiant introuvable : id=" + id);
        }
        etudiantRepository.deleteById(id);
    }
}