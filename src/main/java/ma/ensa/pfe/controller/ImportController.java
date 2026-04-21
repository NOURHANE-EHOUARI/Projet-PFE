package ma.ensa.pfe.controller;

import ma.ensa.pfe.service.ExcelImportService;
import ma.ensa.pfe.service.ExcelImportService.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Contrôleur gérant l'upload du fichier Excel et l'affichage du rapport d'import.
 *
 * @author Membre A
 */
@Controller
@RequestMapping("/import")
public class ImportController {

    @Autowired
    private ExcelImportService excelImportService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 Mo

    // ===== PAGE D'UPLOAD =====
    @GetMapping
    public String pageUpload() {
        return "import/upload";
    }

    // ===== TRAITEMENT DU FICHIER =====
    @PostMapping("/traiter")
    public String traiterFichier(@RequestParam("fichier") MultipartFile fichier,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {

        // Validation basique
        if (fichier == null || fichier.isEmpty()) {
            model.addAttribute("erreurUpload", "Veuillez sélectionner un fichier Excel.");
            return "import/upload";
        }

        String filename = fichier.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx")
                && !filename.toLowerCase().endsWith(".xls"))) {
            model.addAttribute("erreurUpload", "Format invalide. Seuls les fichiers .xlsx et .xls sont acceptés.");
            return "import/upload";
        }

        if (fichier.getSize() > MAX_FILE_SIZE) {
            model.addAttribute("erreurUpload", "Fichier trop volumineux (max 10 Mo).");
            return "import/upload";
        }

        // Import
        try {
            ImportResult result = excelImportService.importerFichier(fichier);
            model.addAttribute("result", result);
            model.addAttribute("nomFichier", filename);
            return "import/resultat";

        } catch (Exception e) {
            model.addAttribute("erreurUpload",
                    "Erreur lors du traitement du fichier : " + e.getMessage());
            return "import/upload";
        }
    }
}