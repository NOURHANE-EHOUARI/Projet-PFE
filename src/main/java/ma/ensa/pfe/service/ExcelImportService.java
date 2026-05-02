package ma.ensa.pfe.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;

@Service
public class ExcelImportService {

    @Autowired private EtudiantRepository       etudiantRepository;
    @Autowired private ProfesseurRepository      professeurRepository;
    @Autowired private SalleRepository           salleRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    // ══════════════════════════════════════════════
    //  RÉSULTAT D'IMPORT
    // ══════════════════════════════════════════════
    public static class ImportResult {
        private int profsImportes     = 0;
        private int etudiantsImportes = 0;
        private int sallesImportees   = 0;
        private final List<String> erreurs = new ArrayList<>();

        public void addErreur(String e)      { erreurs.add(e); }
        public int getProfsImportes()        { return profsImportes; }
        public int getEtudiantsImportes()    { return etudiantsImportes; }
        public int getSallesImportees()      { return sallesImportees; }
        public List<String> getErreurs()     { return erreurs; }
        public boolean hasErreurs()          { return !erreurs.isEmpty(); }

        void incrProfs(int n)     { profsImportes += n; }
        void incrEtudiants(int n) { etudiantsImportes += n; }
        void incrSalles(int n)    { sallesImportees += n; }
    }

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE
    // ══════════════════════════════════════════════
    public ImportResult importerFichier(MultipartFile file) throws Exception {
        ImportResult result = new ImportResult();

        try (InputStream is = file.getInputStream();
             Workbook wb   = ouvrirWorkbook(file.getOriginalFilename(), is)) {

            // 1. PROFESSEURS — doivent être importés en premier
            Sheet sheetProfs = trouverFeuille(wb, "profs");
            if (sheetProfs != null) {
                importerProfesseurs(sheetProfs, result);
            } else {
                result.addErreur("Feuille 'PROFS' introuvable dans le fichier Excel.");
            }

            // 2. SALLES
            Sheet sheetSalles = trouverFeuille(wb, "salles");
            if (sheetSalles != null) {
                importerSalles(sheetSalles, result);
            } else {
                result.addErreur("Feuille 'SALLES' introuvable dans le fichier Excel.");
            }

            // 3. ÉTUDIANTS
            Sheet sheetEtudiants = trouverFeuille(wb, "etudiants");
            if (sheetEtudiants != null) {
                importerEtudiants(sheetEtudiants, result);
            } else {
                result.addErreur("Feuille 'ETUDIANTS' introuvable dans le fichier Excel.");
            }

            // 4. VERSION PLANNING
            if (result.getProfsImportes() > 0 || result.getEtudiantsImportes() > 0) {
                VersionPlanning version = new VersionPlanning();
                version.setDateGeneration(LocalDateTime.now());
                version.setDescription("Import – " + LocalDateTime.now());
                versionPlanningRepository.save(version);
                versionPlanningRepository.flush();
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur critique : " + e.getMessage(), e);
        }

        return result;
    }

    // ══════════════════════════════════════════════
    //  IMPORT PROFESSEURS
    //  Colonne 0 : nom
    //  Colonne 1 : prenom
    //  Colonne 2 : specialite_cible  (GI / Informatique / Mathématique / Gestion / Anglais)
    //  Colonne 3 : parle_anglais     (Oui / Non)
    // ══════════════════════════════════════════════
    private void importerProfesseurs(Sheet sheet, ImportResult result) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;
            try {
                String nom        = lireString(row, 0).toUpperCase().trim();
                String prenom     = capitalize(lireString(row, 1)).trim();
                // ✅ On stocke la vraie spécialité : GI / Informatique / Mathématique / Gestion / Anglais
                String specialite = normaliserSpecialite(lireString(row, 2).trim());
                boolean parleAng  = lireBoolean(row, 3);

                if (nom.isBlank() || prenom.isBlank()) continue;

                Professeur prof = professeurRepository
                        .findByNomAndPrenom(nom, prenom)
                        .orElse(new Professeur());
                prof.setNom(nom);
                prof.setPrenom(prenom);
                prof.setSpecialite(specialite);
                prof.setParleAnglais(parleAng);

                professeurRepository.save(prof);
                professeurRepository.flush();
                result.incrProfs(1);

            } catch (Exception e) {
                result.addErreur("Prof ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
    }

    /**
     * Normalise la spécialité lue depuis Excel vers une valeur standard.
     * Valeurs standard : GI | INFORMATIQUE | MATHEMATIQUE | GESTION | ANGLAIS
     */
    private String normaliserSpecialite(String raw) {
        if (raw == null || raw.isBlank()) return "AUTRE";
        String s = raw.toUpperCase()
                      .replace("É", "E").replace("È", "E").replace("Ê", "E")
                      .replace("À", "A").replace("Â", "A")
                      .trim();

        if (s.equals("GI"))                        return "GI";
        if (s.contains("INFORMAT"))                return "INFORMATIQUE";
        if (s.contains("MATH"))                    return "MATHEMATIQUE";
        if (s.contains("GESTION"))                 return "GESTION";
        if (s.contains("ANGL"))                    return "ANGLAIS";
        return s; // garder tel quel si inconnu
    }

    // ══════════════════════════════════════════════
    //  IMPORT SALLES
    // ══════════════════════════════════════════════
    private void importerSalles(Sheet sheet, ImportResult result) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;
            try {
                String nom = lireString(row, 0).trim();
                if (nom.isBlank()) continue;

                int capacite = 30;
                String capStr = lireString(row, 1).trim();
                if (!capStr.isBlank()) {
                    try { capacite = Integer.parseInt(capStr); }
                    catch (NumberFormatException ignored) {}
                }

                Salle salle = salleRepository.findByNom(nom).orElse(new Salle());
                salle.setNom(nom);
                salle.setCapacite(capacite);

                salleRepository.save(salle);
                salleRepository.flush();
                result.incrSalles(1);

            } catch (Exception e) {
                result.addErreur("Salle ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════
    //  IMPORT ÉTUDIANTS
    // ══════════════════════════════════════════════
    private void importerEtudiants(Sheet sheet, ImportResult result) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;
            try {
                String cne        = lireString(row, 0).trim();
                String nom        = lireString(row, 1).toUpperCase().trim();
                String prenom     = capitalize(lireString(row, 2)).trim();
                String filiereStr = lireString(row, 3).toUpperCase().trim();
                String langueStr  = lireString(row, 4).trim();
                String email      = lireString(row, 5).trim();

                if (nom.isBlank() || prenom.isBlank() || cne.isBlank()) {
                    result.addErreur("Étudiant ligne " + (i + 1) + " : données manquantes.");
                    continue;
                }

                Filiere filiere;
                try {
                    filiere = Filiere.valueOf(filiereStr);
                } catch (IllegalArgumentException e) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : filière '" + filiereStr + "' invalide (DATA/GI/TDIA).");
                    continue;
                }

                String langLower = langueStr.toLowerCase();
                Langue langue = (langLower.contains("en") || langLower.contains("ang")
                              || langLower.contains("english"))
                              ? Langue.EN : Langue.FR;

                Etudiant etudiant = etudiantRepository.findByCne(cne).orElse(new Etudiant());
                etudiant.setCne(cne);
                etudiant.setNom(nom);
                etudiant.setPrenom(prenom);
                etudiant.setFiliere(filiere);
                etudiant.setLangue(langue);
                etudiant.setEmail(email);
                etudiant.setEncadrant(null); // affecté via module Affectation

                etudiantRepository.save(etudiant);
                etudiantRepository.flush();
                result.incrEtudiants(1);

            } catch (Exception e) {
                result.addErreur("Étudiant ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES POI
    // ══════════════════════════════════════════════
    private Workbook ouvrirWorkbook(String filename, InputStream is) throws Exception {
        if (filename != null && filename.toLowerCase().endsWith(".xls"))
            return new HSSFWorkbook(is);
        return new XSSFWorkbook(is);
    }

    private Sheet trouverFeuille(Workbook wb, String nom) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).trim().equalsIgnoreCase(nom))
                return wb.getSheetAt(i);
        }
        return null;
    }

    private String lireString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v))
                        ? String.valueOf((long) v)
                        : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private boolean lireBoolean(Row row, int col) {
        String v = lireString(row, col).toLowerCase();
        return v.equals("oui") || v.equals("yes") || v.equals("true")
            || v.equals("1")   || v.equals("o");
    }

    private boolean estLigneVide(Row row) {
        for (Cell c : row) {
            if (c != null && c.getCellType() != CellType.BLANK
                    && !c.toString().trim().isEmpty())
                return false;
        }
        return true;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}