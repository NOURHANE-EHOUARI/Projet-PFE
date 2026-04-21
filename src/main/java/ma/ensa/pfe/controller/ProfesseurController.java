package ma.ensa.pfe.controller;

import ma.ensa.pfe.model.Contrainte;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.service.ProfesseurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/professeurs")
public class ProfesseurController {

    @Autowired
    private ProfesseurService professeurService;

    // ===== LISTE =====
    @GetMapping
    public String liste(Model model,
                        @RequestParam(required = false) String specialite,
                        @RequestParam(required = false) Boolean anglophone) {

        List<Professeur> professeurs;

        // ✅ Filtrage combiné : spécialité + anglophone
        if (specialite != null && !specialite.isBlank() && anglophone != null) {
            // On charge par spécialité puis on filtre en mémoire pour l'anglophone
            // (plus simple que d'ajouter une nouvelle requête repo)
            professeurs = professeurService.findBySpecialite(specialite).stream()
                    .filter(p -> p.getParleAnglais().equals(anglophone))
                    .collect(Collectors.toList());
        } 
        // ✅ Filtrage par spécialité uniquement
        else if (specialite != null && !specialite.isBlank()) {
            professeurs = professeurService.findBySpecialite(specialite);
        } 
        // ✅ Filtrage par anglophone uniquement
        else if (anglophone != null) {
            professeurs = professeurService.findByParleAnglais(anglophone);
        } 
        // ✅ Aucun filtre : tous les professeurs
        else {
            professeurs = professeurService.findAll();
        }

        model.addAttribute("professeurs", professeurs);
        model.addAttribute("totalProfs", professeurService.countAll());
        model.addAttribute("totalAnglophones", professeurService.countAnglophones());
        model.addAttribute("specialites", professeurService.findAllSpecialites());
        model.addAttribute("specialiteActive", specialite);
        model.addAttribute("anglophoneActive", anglophone);
        model.addAttribute("activePage", "professeurs");
        
        return "professeurs/liste";
    }

    // ===== FORMULAIRE AJOUT =====
    @GetMapping("/nouveau")
    public String formulaireAjout(Model model) {
        model.addAttribute("professeur", new Professeur());
        model.addAttribute("modeEdition", false);
        model.addAttribute("activePage", "professeurs");
        return "professeurs/formulaire";
    }

    // ===== FORMULAIRE MODIFICATION =====
    @GetMapping("/modifier/{id}")
    public String formulaireModif(@PathVariable Long id, Model model) {
        model.addAttribute("professeur", professeurService.findById(id));
        model.addAttribute("modeEdition", true);
        model.addAttribute("activePage", "professeurs");
        return "professeurs/formulaire";
    }

    // ===== SAUVEGARDER (ajout + modification) =====
    @PostMapping("/sauvegarder")
    public String sauvegarder(@Valid @ModelAttribute Professeur professeur,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttrs) {

        if (result.hasErrors()) {
            model.addAttribute("modeEdition", professeur.getId() != null);
            model.addAttribute("activePage", "professeurs");
            return "professeurs/formulaire";
        }

        try {
            boolean isNew = professeur.getId() == null;
            professeurService.save(professeur);
            String msg = isNew ? "Professeur ajouté avec succès !" : "Professeur modifié avec succès !";
            redirectAttrs.addFlashAttribute("successMsg", msg);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("modeEdition", professeur.getId() != null);
            model.addAttribute("activePage", "professeurs");
            return "professeurs/formulaire";
        }

        return "redirect:/professeurs";
    }

    // ===== SUPPRIMER =====
    @PostMapping("/supprimer/{id}")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            professeurService.deleteById(id);
            redirectAttrs.addFlashAttribute("successMsg", "Professeur supprimé avec succès !");
        } catch (IllegalStateException e) {
            // Le professeur encadre encore des étudiants — on affiche l'erreur dans la liste
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        } catch (EntityNotFoundException e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/professeurs";
    }

    // ===== FICHE DÉTAIL (contraintes) =====
    @GetMapping("/{id}/contraintes")
    public String contraintes(@PathVariable Long id, Model model) {
        Professeur prof = professeurService.findById(id);
        model.addAttribute("professeur", prof);
        model.addAttribute("contraintes", professeurService.findContraintesByProfesseur(id));
        model.addAttribute("nouvelleContrainte", new Contrainte());
        model.addAttribute("activePage", "professeurs");
        return "professeurs/contraintes";
    }

    // ===== AJOUTER UNE CONTRAINTE =====
    @PostMapping("/{id}/contraintes/ajouter")
    public String ajouterContrainte(@PathVariable Long id,
                                     @ModelAttribute Contrainte contrainte,
                                     RedirectAttributes redirectAttrs) {
        try {
            professeurService.ajouterContrainte(id, contrainte);
            redirectAttrs.addFlashAttribute("successMsg", "Indisponibilité ajoutée !");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/professeurs/" + id + "/contraintes";
    }

    // ===== SUPPRIMER UNE CONTRAINTE =====
    @PostMapping("/{profId}/contraintes/supprimer/{contrainteId}")
    public String supprimerContrainte(@PathVariable Long profId,
                                       @PathVariable Long contrainteId,
                                       RedirectAttributes redirectAttrs) {
        professeurService.supprimerContrainte(contrainteId);
        redirectAttrs.addFlashAttribute("successMsg", "Indisponibilité supprimée !");
        return "redirect:/professeurs/" + profId + "/contraintes";
    }
}