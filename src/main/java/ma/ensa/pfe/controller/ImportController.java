package ma.ensa.pfe.controller;

import ma.ensa.pfe.model.*;
import ma.ensa.pfe.dao.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.util.*;

@Controller
@RequestMapping("/import")
public class ImportController {

    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private SalleRepository salleRepository;

    @GetMapping
    public String page() {
        return "import/upload";
    }

    @PostMapping("/excel")
    public String importerExcel(@RequestParam("fichier") MultipartFile fichier,
                                 RedirectAttributes ra, Model model) {

        if (fichier.isEmpty()) {
            ra.addFlashAttribute("erreur", "Veuillez sélectionner un fichier Excel.");
            return "redirect:/import";
        }

        if (!fichier.getOriginalFilename().endsWith(".xlsx")) {
            ra.addFlashAttribute("erreur", "Format invalide — uniquement les fichiers .xlsx sont acceptés.");
            return "redirect:/import";
        }

        List<String> rapport = new ArrayList<>();
        int nbEtudiants = 0, nbProfs = 0, nbSalles = 0, nbErreurs = 0;

        try (InputStream is = fichier.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            // ── Feuille PROFESSEURS ──
            Sheet sheetProfs = workbook.getSheet("professeurs");
            if (sheetProfs != null) {
                for (int i = 1; i <= sheetProfs.getLastRowNum(); i++) {
                    Row row = sheetProfs.getRow(i);
                    if (row == null) continue;
                    try {
                        String nom    = getCellValue(row, 0);
                        String prenom = getCellValue(row, 1);
                        String spec   = getCellValue(row, 2);
                        String anglais = getCellValue(row, 3);

                        if (nom.isEmpty() || prenom.isEmpty()) {
                            rapport.add("Profs ligne " + (i+1) + " : nom/prénom manquant");
                            nbErreurs++; continue;
                        }

                        Professeur p = new Professeur();
                        p.setNom(nom);
                        p.setPrenom(prenom);
                        p.setSpecialite(spec);
                        p.setParleAnglais("O".equalsIgnoreCase(anglais) || "oui".equalsIgnoreCase(anglais));
                        professeurRepository.save(p);
                        nbProfs++;
                    } catch (Exception e) {
                        rapport.add("Profs ligne " + (i+1) + " : " + e.getMessage());
                        nbErreurs++;
                    }
                }
            } else {
                rapport.add("⚠️ Feuille 'professeurs' introuvable");
            }

            // ── Feuille SALLES ──
            Sheet sheetSalles = workbook.getSheet("salles");
            if (sheetSalles != null) {
                for (int i = 1; i <= sheetSalles.getLastRowNum(); i++) {
                    Row row = sheetSalles.getRow(i);
                    if (row == null) continue;
                    try {
                        String nom = getCellValue(row, 0);
                        int capacite = (int) row.getCell(1).getNumericCellValue();

                        if (nom.isEmpty()) {
                            rapport.add("Salles ligne " + (i+1) + " : nom manquant");
                            nbErreurs++; continue;
                        }
                        if (!salleRepository.existsByNom(nom)) {
                            salleRepository.save(new Salle(nom, capacite));
                            nbSalles++;
                        }
                    } catch (Exception e) {
                        rapport.add("Salles ligne " + (i+1) + " : " + e.getMessage());
                        nbErreurs++;
                    }
                }
            } else {
                rapport.add("⚠️ Feuille 'salles' introuvable");
            }

            // ── Feuille ETUDIANTS ──
            Sheet sheetEtu = workbook.getSheet("etudiants");
            if (sheetEtu != null) {
                for (int i = 1; i <= sheetEtu.getLastRowNum(); i++) {
                    Row row = sheetEtu.getRow(i);
                    if (row == null) continue;
                    try {
                        String nom      = getCellValue(row, 0);
                        String prenom   = getCellValue(row, 1);
                        String filiere  = getCellValue(row, 2);
                        String langue   = getCellValue(row, 3);
                        String encNom   = getCellValue(row, 4);

                        if (nom.isEmpty() || prenom.isEmpty()) {
                            rapport.add("Etudiants ligne " + (i+1) + " : nom/prénom manquant");
                            nbErreurs++; continue;
                        }

                        Etudiant e = new Etudiant();
                        e.setNom(nom);
                        e.setPrenom(prenom);
                        e.setFiliere(filiere.isEmpty() ? Etudiant.Filiere.GI : Etudiant.Filiere.valueOf(filiere.toUpperCase()));
                        e.setLangue(langue.isEmpty() ? Etudiant.Langue.FR : Etudiant.Langue.valueOf(langue.toUpperCase()));


                        // Chercher l'encadrant par nom
                        if (!encNom.isEmpty()) {
                            List<Professeur> profs = professeurRepository.findAll();
                            profs.stream()
                                 .filter(p -> p.getNom().equalsIgnoreCase(encNom))
                                 .findFirst()
                                 .ifPresent(e::setEncadrant);
                        }

                        etudiantRepository.save(e);
                        nbEtudiants++;
                    } catch (Exception ex) {
                        rapport.add("Etudiants ligne " + (i+1) + " : " + ex.getMessage());
                        nbErreurs++;
                    }
                }
            } else {
                rapport.add("⚠️ Feuille 'etudiants' introuvable");
            }

            ra.addFlashAttribute("succes",
                "Import terminé — " + nbProfs + " profs, " +
                nbSalles + " salles, " + nbEtudiants + " étudiants importés.");
            ra.addFlashAttribute("rapport", rapport);
            ra.addFlashAttribute("nbErreurs", nbErreurs);

        } catch (Exception e) {
            ra.addFlashAttribute("erreur", "Erreur lecture fichier : " + e.getMessage());
        }

        return "redirect:/import";
    }

    private String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }
}