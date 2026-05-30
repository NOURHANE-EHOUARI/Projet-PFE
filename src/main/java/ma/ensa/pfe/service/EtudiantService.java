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
import java.util.UUID;

@Service
@Transactional
public class EtudiantService {

    @Autowired private EtudiantRepository  etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;

  

    @Transactional(readOnly = true)
    public List<Etudiant> findAll() {
        return etudiantRepository.findAllWithEncadrant();
    }

    @Transactional(readOnly = true)
    public Etudiant findById(Long id) {
        return etudiantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Étudiant introuvable : id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Etudiant> findByFiliere(Filiere filiere) {
        return etudiantRepository.findByFiliereWithEncadrant(filiere);
    }

    @Transactional(readOnly = true)
    public List<Etudiant> findByLangue(Langue langue) {
        return etudiantRepository.findByLangueWithEncadrant(langue);
    }

    @Transactional(readOnly = true)
    public long countByFiliere(Filiere filiere) {
        return etudiantRepository.countByFiliere(filiere);
    }

    
    public Etudiant save(Etudiant etudiant) {

        if (etudiant.getId() != null) {
            
            Etudiant existant = etudiantRepository.findById(etudiant.getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Étudiant introuvable : id=" + etudiant.getId()));
            // Conserver le CNE original (non modifiable)
            etudiant.setCne(existant.getCne());

            
            if (etudiant.getEncadrant() == null
                    || etudiant.getEncadrant().getId() == null
                    || etudiant.getEncadrant().getId() == 0) {
                throw new IllegalArgumentException(
                        "Un encadrant est obligatoire pour la modification.");
            }

        } else {
            
            if (etudiant.getCne() == null || etudiant.getCne().isBlank()) {
                String cneAuto = "AUTO-"
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                etudiant.setCne(cneAuto);
            }

            if (etudiant.getEncadrant() != null
                    && (etudiant.getEncadrant().getId() == null
                        || etudiant.getEncadrant().getId() == 0)) {
                etudiant.setEncadrant(null);
            }
        }

       
        if (etudiant.getEncadrant() != null
                && etudiant.getEncadrant().getId() != null) {
            Professeur encadrant = professeurRepository
                    .findById(etudiant.getEncadrant().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Encadrant invalide : id=" + etudiant.getEncadrant().getId()));
            etudiant.setEncadrant(encadrant);
        }

        return etudiantRepository.save(etudiant);
    }

    public void deleteById(Long id) {
        if (!etudiantRepository.existsById(id)) {
            throw new EntityNotFoundException("Étudiant introuvable : id=" + id);
        }
        etudiantRepository.deleteById(id);
    }
}