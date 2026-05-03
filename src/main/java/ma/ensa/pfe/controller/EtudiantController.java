package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.service.EtudiantService;
import ma.ensa.pfe.service.PlanificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/etudiants")
public class EtudiantController {

    @Autowired private EtudiantService      etudiantService;
    @Autowired private ProfesseurRepository  professeurRepository;
    @Autowired private PlanificationService  planificationService;

    private void addCommonAttributes(Model model) {
        model.addAttribute("professeurs", professeurRepository.findAll());
        model.addAttribute("filieres",    Filiere.values());
        model.addAttribute("langues",     Langue.values());
        model.addAttribute("activePage",  "etudiants");
    }

    @GetMapping
    public String liste(Model model,
                        @RequestParam(required = false) String filiere,
                        @RequestParam(required = false) String langue) {
        List<Etudiant> etudiants;
        if (filiere != null && !filiere.isEmpty()) {
            try { etudiants = etudiantService.findByFiliere(Filiere.valueOf(filiere)); }
            catch (IllegalArgumentException e) { etudiants = etudiantService.findAll(); }
        } else if (langue != null && !langue.isEmpty()) {
            try { etudiants = etudiantService.findByLangue(Langue.valueOf(langue)); }
            catch (IllegalArgumentException e) { etudiants = etudiantService.findAll(); }
        } else {
            etudiants = etudiantService.findAll();
        }
        model.addAttribute("etudiants",     etudiants);
        model.addAttribute("totalGI",       etudiantService.countByFiliere(Filiere.GI));
        model.addAttribute("totalTDIA",     etudiantService.countByFiliere(Filiere.TDIA));
        model.addAttribute("totalDATA",     etudiantService.countByFiliere(Filiere.DATA));
        model.addAttribute("filiereActive", filiere);
        model.addAttribute("langueActive",  langue);
        model.addAttribute("activePage",    "etudiants");
        return "etudiants/liste";
    }

    @GetMapping("/nouveau")
    public String formulaireAjout(Model model) {
        model.addAttribute("etudiantForm", new EtudiantForm());
        model.addAttribute("modeEdition", false);
        addCommonAttributes(model);
        return "etudiants/formulaire";
    }

    @GetMapping("/modifier/{id}")
    public String formulaireModif(@PathVariable Long id, Model model) {
        Etudiant e = etudiantService.findById(id);
        EtudiantForm form = new EtudiantForm();
        form.setId(e.getId());
        form.setCne(e.getCne());
        form.setNom(e.getNom());
        form.setPrenom(e.getPrenom());
        form.setFiliere(e.getFiliere());
        form.setLangue(e.getLangue());
        form.setAncienneLangue(e.getLangue());
        form.setTitreProjet(e.getTitreProjet());
        form.setEmail(e.getEmail());
        form.setEncadrantId(e.getEncadrant() != null ? e.getEncadrant().getId() : null);
        model.addAttribute("etudiantForm", form);
        model.addAttribute("modeEdition", true);
        addCommonAttributes(model);
        return "etudiants/formulaire";
    }

    @PostMapping("/sauvegarder")
    public String sauvegarder(@ModelAttribute("etudiantForm") EtudiantForm form,
                               Model model,
                               RedirectAttributes redirectAttrs) {
        boolean hasError = false;
        if (form.getNom() == null || form.getNom().isBlank()) {
            model.addAttribute("errNom", "Le nom est obligatoire.");
            hasError = true;
        }
        if (form.getPrenom() == null || form.getPrenom().isBlank()) {
            model.addAttribute("errPrenom", "Le prenom est obligatoire.");
            hasError = true;
        }
        if (form.getFiliere() == null) {
            model.addAttribute("errFiliere", "Veuillez choisir une filiere.");
            hasError = true;
        }
        if (form.getLangue() == null) {
            model.addAttribute("errLangue", "Veuillez choisir une langue.");
            hasError = true;
        }
        // ✅ CORRECTION : la ligne qui rendait l'encadrant obligatoire en mode édition
        // a été supprimée — l'encadrant est optionnel dans les deux modes.
        // L'affectation se fait via le module Affectation automatique.

        if (hasError) {
            model.addAttribute("modeEdition", form.getId() != null);
            addCommonAttributes(model);
            return "etudiants/formulaire";
        }

        Etudiant etudiant = (form.getId() != null)
            ? etudiantService.findById(form.getId())
            : new Etudiant();

        Langue ancienneLangue = form.getAncienneLangue();
        boolean langueChangee = form.getId() != null
                && ancienneLangue != null
                && ancienneLangue != form.getLangue();

        etudiant.setNom(form.getNom().trim().toUpperCase());
        etudiant.setPrenom(form.getPrenom().trim());
        etudiant.setFiliere(form.getFiliere());
        etudiant.setLangue(form.getLangue());
        if (form.getTitreProjet() != null) etudiant.setTitreProjet(form.getTitreProjet().trim());
        if (form.getEmail()       != null) etudiant.setEmail(form.getEmail().trim());

        String cne = (form.getCne() != null && !form.getCne().isBlank())
                   ? form.getCne().trim()
                   : etudiant.getCne();
        if (cne == null || cne.isBlank()) {
            cne = "PFE-" + form.getFiliere().name() + "-" + System.currentTimeMillis();
        }
        etudiant.setCne(cne);

        if (form.getEncadrantId() != null) {
            professeurRepository.findById(form.getEncadrantId())
                .ifPresent(etudiant::setEncadrant);
        }

        try {
            boolean isNew = form.getId() == null;
            etudiantService.save(etudiant);

            if (langueChangee) {
                try {
                    String msg = planificationService
                            .recalculerJuryPourEtudiant(etudiant.getId(), ancienneLangue);
                    redirectAttrs.addFlashAttribute("juryMsg", msg);
                } catch (Exception ex) {
                    redirectAttrs.addFlashAttribute("juryWarning",
                        "Étudiant modifié mais jury non mis à jour : " + ex.getMessage());
                }
            }

            redirectAttrs.addFlashAttribute("successMsg",
                isNew ? "Etudiant ajoute avec succes !" : "Etudiant modifie avec succes !");

        } catch (Exception e) {
            model.addAttribute("errorMsg", "Erreur lors de la sauvegarde : " + e.getMessage());
            model.addAttribute("modeEdition", form.getId() != null);
            addCommonAttributes(model);
            return "etudiants/formulaire";
        }
        return "redirect:/etudiants";
    }

    @PostMapping("/supprimer/{id}")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            etudiantService.deleteById(id);
            redirectAttrs.addFlashAttribute("successMsg", "Etudiant supprime avec succes !");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMsg",
                "Impossible de supprimer : cet etudiant a des soutenances programmees.");
        }
        return "redirect:/etudiants";
    }

    // ══════════════════════════════════════════════
    //  FORM DTO
    // ══════════════════════════════════════════════
    public static class EtudiantForm {
        private Long    id;
        private String  cne;
        private String  nom;
        private String  prenom;
        private Filiere filiere;
        private Langue  langue;
        private Langue  ancienneLangue;
        private Long    encadrantId;
        private String  titreProjet;
        private String  email;

        public Long    getId()                      { return id; }
        public void    setId(Long id)               { this.id = id; }
        public String  getCne()                     { return cne; }
        public void    setCne(String cne)           { this.cne = cne; }
        public String  getNom()                     { return nom; }
        public void    setNom(String nom)           { this.nom = nom; }
        public String  getPrenom()                  { return prenom; }
        public void    setPrenom(String p)          { this.prenom = p; }
        public Filiere getFiliere()                 { return filiere; }
        public void    setFiliere(Filiere f)        { this.filiere = f; }
        public Langue  getLangue()                  { return langue; }
        public void    setLangue(Langue l)          { this.langue = l; }
        public Langue  getAncienneLangue()          { return ancienneLangue; }
        public void    setAncienneLangue(Langue l)  { this.ancienneLangue = l; }
        public Long    getEncadrantId()             { return encadrantId; }
        public void    setEncadrantId(Long eid)     { this.encadrantId = eid; }
        public String  getTitreProjet()             { return titreProjet; }
        public void    setTitreProjet(String t)     { this.titreProjet = t; }
        public String  getEmail()                   { return email; }
        public void    setEmail(String e)           { this.email = e; }
    }
}