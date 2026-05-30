package ma.ensa.pfe.controller;

import ma.ensa.pfe.service.AffectationService;
import ma.ensa.pfe.service.AffectationService.AffectationResult;
import ma.ensa.pfe.service.ExcelImportService;
import ma.ensa.pfe.service.ExcelImportService.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/import")
public class ImportController {

    @Autowired private ExcelImportService excelImportService;
    @Autowired private AffectationService affectationService; // ✅ AJOUTÉ

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @PostMapping("/traiter")
    public String traiterFichier(@RequestParam("fichier") MultipartFile fichier,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {

        if (fichier == null || fichier.isEmpty()) {
            model.addAttribute("erreurUpload", "Veuillez sélectionner un fichier Excel.");
            return "import/upload";
        }

        String filename = fichier.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx")
                && !filename.toLowerCase().endsWith(".xls"))) {
            model.addAttribute("erreurUpload", "Format invalide. Seuls .xlsx et .xls sont acceptés.");
            return "import/upload";
        }

        if (fichier.getSize() > MAX_FILE_SIZE) {
            model.addAttribute("erreurUpload", "Fichier trop volumineux (max 10 Mo).");
            return "import/upload";
        }

        try {
            // 1. Import Excel
            ImportResult result = excelImportService.importerFichier(fichier);

            // 2. ✅ Affectation automatique immédiatement après l'import
            if (result.getEtudiantsImportes() > 0 && result.getProfsImportes() > 0) {
                AffectationResult affResult = affectationService.affecterEncadrants();
                redirectAttrs.addFlashAttribute("nbAffectes", affResult.getNbAffectes());
                redirectAttrs.addFlashAttribute("success",
                    result.getEtudiantsImportes() + " étudiant(s) importé(s) — " +
                    affResult.getNbAffectes() + " encadrant(s) affecté(s) automatiquement.");
            }

            redirectAttrs.addFlashAttribute("importSuccess", true);
            return "redirect:/affectation";

        } catch (Exception e) {
            model.addAttribute("erreurUpload",
                    "Erreur lors du traitement : " + e.getMessage());
            return "import/upload";
        }
    }
}