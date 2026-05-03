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

    @Autowired private EtudiantRepository  etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;

    // ══════════════════════════════════════════════
    //  LECTURE
    // ══════════════════════════════════════════════

    /**
     * Retourne TOUS les étudiants, avec ou sans encadrant.
     * ✅ Utilise LEFT JOIN FETCH pour ne pas exclure les étudiants sans encadrant.
     */
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

    /**
     * Filtre par filière — inclut les étudiants sans encadrant.
     * ✅ Utilise LEFT JOIN FETCH pour cohérence avec findAll().
     */
    @Transactional(readOnly = true)
    public List<Etudiant> findByFiliere(Filiere filiere) {
        return etudiantRepository.findByFiliereWithEncadrant(filiere);
    }

    /**
     * Filtre par langue — inclut les étudiants sans encadrant.
     * ✅ Utilise LEFT JOIN FETCH pour cohérence avec findAll().
     */
    @Transactional(readOnly = true)
    public List<Etudiant> findByLangue(Langue langue) {
        return etudiantRepository.findByLangueWithEncadrant(langue);
    }

    @Transactional(readOnly = true)
    public long countByFiliere(Filiere filiere) {
        return etudiantRepository.countByFiliere(filiere);
    }

    // ══════════════════════════════════════════════
    //  ÉCRITURE
    // ══════════════════════════════════════════════

    public Etudiant save(Etudiant etudiant) {

        if (etudiant.getId() != null) {
            // ── MODE ÉDITION ──
            // Récupérer l'enregistrement existant pour conserver le CNE
            Etudiant existant = etudiantRepository.findById(etudiant.getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Étudiant introuvable : id=" + etudiant.getId()));
            etudiant.setCne(existant.getCne());

            // En mode édition l'encadrant est obligatoire
            if (etudiant.getEncadrant() == null
                    || etudiant.getEncadrant().getId() == null
                    || etudiant.getEncadrant().getId() == 0) {
                throw new IllegalArgumentException(
                        "Un encadrant est obligatoire pour la modification.");
            }

        } else {
            // ── MODE AJOUT ──
            // L'encadrant est optionnel : si non sélectionné on le laisse null
            // Il sera affecté automatiquement lors de la génération du planning
            if (etudiant.getEncadrant() != null
                    && (etudiant.getEncadrant().getId() == null
                        || etudiant.getEncadrant().getId() == 0)) {
                etudiant.setEncadrant(null);
            }
        }

        // Résoudre l'encadrant depuis la BDD si un id est fourni
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