package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SalleRepository;
import ma.ensa.pfe.model.Salle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Contrôleur Spring MVC pour la gestion CRUD des salles de soutenance.
 * <p>
 * Expose les endpoints :
 * <ul>
 *   <li>GET  {@code /salles}              — liste toutes les salles</li>
 *   <li>GET  {@code /salles/ajouter}      — formulaire d'ajout</li>
 *   <li>POST {@code /salles/enregistrer}  — enregistrer une salle</li>
 *   <li>GET  {@code /salles/modifier/{id}} — formulaire de modification</li>
 *   <li>GET  {@code /salles/supprimer/{id}} — suppression</li>
 * </ul>
 *
 * @author Membre B — ENSA Al Hoceima 2024/2025
 * @version 1.0
 */
@Controller
@RequestMapping("/salles")
public class SalleController {

    @Autowired
    private SalleRepository salleRepository;

    // Liste toutes les salles
    @GetMapping
    public String liste(Model model) {
        model.addAttribute("salles", salleRepository.findAll());
        model.addAttribute("salle", new Salle()); // pour le formulaire vide
        return "salles/liste";
    }

    // Afficher formulaire d'ajout
    @GetMapping("/ajouter")
    public String formulaireAjout(Model model) {
        model.addAttribute("salle", new Salle());
        return "salles/formulaire";
    }

    // Enregistrer une nouvelle salle
    @PostMapping("/enregistrer")
    public String enregistrer(@ModelAttribute Salle salle,
                              RedirectAttributes ra) {
        if (salleRepository.existsByNom(salle.getNom())) {
            ra.addFlashAttribute("erreur", "La salle " + salle.getNom() + " existe déjà !");
            return "redirect:/salles";
        }
        salleRepository.save(salle);
        ra.addFlashAttribute("succes", "Salle " + salle.getNom() + " ajoutée avec succès !");
        return "redirect:/salles";
    }

    // Afficher formulaire de modification
    @GetMapping("/modifier/{id}")
    public String formulaireModifier(@PathVariable Long id, Model model) {
        Salle salle = salleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Salle introuvable : " + id));
        model.addAttribute("salle", salle);
        return "salles/formulaire";
    }

    // Supprimer une salle
    @GetMapping("/supprimer/{id}")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        salleRepository.deleteById(id);
        ra.addFlashAttribute("succes", "Salle supprimée !");
        return "redirect:/salles";
    }
}