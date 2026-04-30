package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.service.EtudiantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/etudiants")
public class EtudiantController {

    @Autowired
    private EtudiantService etudiantService;

    @Autowired
    private ProfesseurRepository professeurRepository;

    // ===== LISTE =====
    @GetMapping
    public String liste(Model model,
                        @RequestParam(required = false) String filiere,
                        @RequestParam(required = false) String langue) {

        List<Etudiant> etudiants;

        if (filiere != null && !filiere.isEmpty()) {
            try {
                etudiants = etudiantService.findByFiliere(Filiere.valueOf(filiere));
            } catch (IllegalArgumentException e) {
                // Si la filière n'existe pas (ex: ancien ID), on affiche tous les étudiants
                etudiants = etudiantService.findAll();
            }
        } else if (langue != null && !langue.isEmpty()) {
            try {
                etudiants = etudiantService.findByLangue(Langue.valueOf(langue));
            } catch (IllegalArgumentException e) {
                etudiants = etudiantService.findAll();
            }
        } else {
            etudiants = etudiantService.findAll();
        }

        model.addAttribute("etudiants", etudiants);
        
        // ✅ CORRECTION : Remplacement de ID par TDIA
        model.addAttribute("totalGI", etudiantService.countByFiliere(Filiere.GI));
        model.addAttribute("totalTDIA", etudiantService.countByFiliere(Filiere.TDIA)); 
        model.addAttribute("totalDATA", etudiantService.countByFiliere(Filiere.DATA));
        
        model.addAttribute("filiereActive", filiere);
        model.addAttribute("langueActive", langue);
        model.addAttribute("activePage", "etudiants");
        return "etudiants/liste";
    }

    // ===== FORMULAIRE AJOUT =====
    @GetMapping("/nouveau")
    public String formulaireAjout(Model model) {
        // FIX : initialiser l'encadrant pour éviter NPE dans th:field="*{encadrant.id}"
        Etudiant etudiant = new Etudiant();
        etudiant.setEncadrant(new Professeur());

        model.addAttribute("etudiant", etudiant);
        model.addAttribute("professeurs", professeurRepository.findAll());
        model.addAttribute("filieres", Filiere.values());
        model.addAttribute("langues", Langue.values());
        model.addAttribute("modeEdition", false);
        model.addAttribute("activePage", "etudiants");
        return "etudiants/formulaire";
    }

    // ===== FORMULAIRE MODIFICATION =====
    @GetMapping("/modifier/{id}")
    public String formulaireModif(@PathVariable Long id, Model model) {
        model.addAttribute("etudiant", etudiantService.findById(id));
        model.addAttribute("professeurs", professeurRepository.findAll());
        model.addAttribute("filieres", Filiere.values());
        model.addAttribute("langues", Langue.values());
        model.addAttribute("modeEdition", true);
        model.addAttribute("activePage", "etudiants");
        return "etudiants/formulaire";
    }

    // ===== SAUVEGARDER (ajout + modification) =====
    @PostMapping("/sauvegarder")
    public String sauvegarder(@Valid @ModelAttribute Etudiant etudiant,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttrs) {

        if (result.hasErrors()) {
            model.addAttribute("professeurs", professeurRepository.findAll());
            model.addAttribute("filieres", Filiere.values());
            model.addAttribute("langues", Langue.values());
            model.addAttribute("modeEdition", etudiant.getId() != null);
            model.addAttribute("activePage", "etudiants");
            return "etudiants/formulaire";
        }

        try {
            boolean isNew = etudiant.getId() == null;
            etudiantService.save(etudiant);
            String msg = isNew ? "Étudiant ajouté avec succès !" : "Étudiant modifié avec succès !";
            redirectAttrs.addFlashAttribute("successMsg", msg);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("professeurs", professeurRepository.findAll());
            model.addAttribute("filieres", Filiere.values());
            model.addAttribute("langues", Langue.values());
            model.addAttribute("modeEdition", etudiant.getId() != null);
            model.addAttribute("activePage", "etudiants");
            return "etudiants/formulaire";
        }

        return "redirect:/etudiants";
    }

    // ===== SUPPRIMER =====
    @PostMapping("/supprimer/{id}")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        etudiantService.deleteById(id);
        redirectAttrs.addFlashAttribute("successMsg", "Étudiant supprimé avec succès !");
        return "redirect:/etudiants";
    }
}