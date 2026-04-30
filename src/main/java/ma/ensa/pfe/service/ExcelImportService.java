package ma.ensa.pfe.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.dao.SalleRepository;
import ma.ensa.pfe.dao.VersionPlanningRepository;
import ma.ensa.pfe.model.Etudiant;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import ma.ensa.pfe.model.Professeur;
import ma.ensa.pfe.model.Salle;
import ma.ensa.pfe.model.VersionPlanning;

@Service
public class ExcelImportService {

    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private SalleRepository salleRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    public static class ImportResult {
        private int profsImportes = 0;
        private int etudiantsImportes = 0;
        private int sallesImportees = 0;
        private final List<String> erreurs = new ArrayList<>();

        public void addErreur(String e) { erreurs.add(e); }
        public int getProfsImportes() { return profsImportes; }
        public int getEtudiantsImportes() { return etudiantsImportes; }
        public int getSallesImportees() { return sallesImportees; }
        public List<String> getErreurs() { return erreurs; }
        public boolean hasErreurs() { return !erreurs.isEmpty(); }
        
        void incrProfs(int n) { profsImportes += n; }
        void incrEtudiants(int n) { etudiantsImportes += n; }
        void incrSalles(int n) { sallesImportees += n; }
    }

    /**
     * Import SANS transaction globale pour éviter l'erreur "rollback-only".
     * Chaque entité est sauvée et flushée immédiatement.
     */
    public ImportResult importerFichier(MultipartFile file) throws Exception {
        ImportResult result = new ImportResult();

        try (InputStream is = file.getInputStream();
             Workbook wb = ouvrirWorkbook(file.getOriginalFilename(), is)) {

            // 1. PROFESSEURS
            Sheet sheetProfs = trouverFeuille(wb, "profs");
            if (sheetProfs != null) {
                for (int i = 1; i <= sheetProfs.getLastRowNum(); i++) {
                    Row row = sheetProfs.getRow(i);
                    if (row == null || estLigneVide(row)) continue;
                    try {
                        String nom = lireString(row, 0).toUpperCase().trim();
                        String prenom = capitalize(lireString(row, 1)).trim();
                        String specialite = lireString(row, 2).trim();
                        boolean parleAng = lireBoolean(row, 3);

                        if (nom.isBlank() || prenom.isBlank()) continue;

                        Optional<Professeur> optProf = professeurRepository.findByNomAndPrenom(nom, prenom);
                        Professeur prof = optProf.orElse(new Professeur());
                        prof.setNom(nom);
                        prof.setPrenom(prenom);
                        prof.setSpecialite(specialite);
                        prof.setParleAnglais(parleAng);
                        
                        professeurRepository.save(prof);
                        professeurRepository.flush(); // ✅ Force l'écriture immédiate
                        
                        result.incrProfs(1);
                    } catch (Exception e) {
                        result.addErreur("Prof ligne " + (i + 1) + " : " + e.getMessage());
                    }
                }
            }

            // 2. SALLES
            Sheet sheetSalles = trouverFeuille(wb, "salles");
            if (sheetSalles != null) {
                for (int i = 1; i <= sheetSalles.getLastRowNum(); i++) {
                    Row row = sheetSalles.getRow(i);
                    if (row == null || estLigneVide(row)) continue;
                    try {
                        String nom = lireString(row, 0).trim();
                        if (nom.isBlank()) continue;
                        
                        int capacite = 30;
                        String capStr = lireString(row, 1).trim();
                        if (!capStr.isBlank()) {
                            try { capacite = Integer.parseInt(capStr); } catch (NumberFormatException ignored) {}
                        }

                        Optional<Salle> optSalle = salleRepository.findByNom(nom);
                        Salle salle = optSalle.orElse(new Salle());
                        salle.setNom(nom);
                        salle.setCapacite(capacite);
                        
                        salleRepository.save(salle);
                        salleRepository.flush(); // ✅ Force l'écriture immédiate

                        result.incrSalles(1);
                    } catch (Exception e) {
                        result.addErreur("Salle ligne " + (i + 1) + " : " + e.getMessage());
                    }
                }
            }

            // 3. ÉTUDIANTS
            Sheet sheetEtudiants = trouverFeuille(wb, "etudiants");
            if (sheetEtudiants != null) {
                // ✅ Récupérer le premier prof pour servir d'encadrant par défaut
                // Cela évite l'erreur "NULL not allowed for column ENCANDRANT_ID"
                Professeur encadrantDefaut = professeurRepository.findAll().stream().findFirst().orElse(null);

                for (int i = 1; i <= sheetEtudiants.getLastRowNum(); i++) {
                    Row row = sheetEtudiants.getRow(i);
                    if (row == null || estLigneVide(row)) continue;
                    try {
                        String cne = lireString(row, 0).trim();
                        String nom = lireString(row, 1).toUpperCase().trim();
                        String prenom = capitalize(lireString(row, 2)).trim();
                        String filiereStr = lireString(row, 3).toUpperCase().trim();
                        String langueStr = lireString(row, 4).trim();
                        String email = lireString(row, 5).trim();

                        if (nom.isBlank() || prenom.isBlank() || cne.isBlank()) {
                            result.addErreur("Étudiant ligne " + (i + 1) + " : Données manquantes.");
                            continue;
                        }

                        Filiere filiere;
                        try { 
                            filiere = Filiere.valueOf(filiereStr); 
                        } catch (IllegalArgumentException e) { 
                            result.addErreur("Étudiant " + nom + " ligne " + (i+1) + " : filière '" + filiereStr + "' invalide."); 
                            continue; 
                        }

                        Langue langue;
                        String langLower = langueStr.toLowerCase();
                        if (langLower.contains("en") || langLower.contains("ang")) {
                            langue = Langue.EN;
                        } else {
                            langue = Langue.FR;
                        }

                        Optional<Etudiant> optEtud = etudiantRepository.findByCne(cne);
                        Etudiant etudiant = optEtud.orElse(new Etudiant());
                        
                        etudiant.setCne(cne);
                        etudiant.setNom(nom);
                        etudiant.setPrenom(prenom);
                        etudiant.setFiliere(filiere);
                        etudiant.setLangue(langue);
                        etudiant.setEmail(email);

                        // ✅ CORRECTION CRUCIALE : Assigner un encadrant si nul
                        if (etudiant.getEncadrant() == null) {
                            if (encadrantDefaut != null) {
                                etudiant.setEncadrant(encadrantDefaut);
                            } else {
                                result.addErreur("Étudiant " + nom + " : Impossible d'importer car aucun professeur n'existe en base pour être encadrant.");
                                continue;
                            }
                        }

                        etudiantRepository.save(etudiant);
                        etudiantRepository.flush(); // ✅ Force l'écriture immédiate

                        result.incrEtudiants(1);
                    } catch (Exception e) {
                        e.printStackTrace(); 
                        result.addErreur("Étudiant ligne " + (i + 1) + " : " + e.getMessage());
                    }
                }
            }

            // 4. VERSION PLANNING
            if (result.getProfsImportes() > 0 || result.getEtudiantsImportes() > 0 || result.getSallesImportees() > 0) {
                VersionPlanning version = new VersionPlanning();
                version.setDateGeneration(LocalDateTime.now());
                version.setDescription("Import initial - " + LocalDateTime.now());
                versionPlanningRepository.save(version);
                versionPlanningRepository.flush();
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur critique lors de l'ouverture du fichier : " + e.getMessage(), e);
        }

        return result;
    }

    // ===== UTILITAIRES POI =====
    private Workbook ouvrirWorkbook(String filename, InputStream is) throws Exception {
        if (filename != null && filename.toLowerCase().endsWith(".xls")) return new HSSFWorkbook(is);
        return new XSSFWorkbook(is);
    }

    private Sheet trouverFeuille(Workbook wb, String nom) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).trim().equalsIgnoreCase(nom)) return wb.getSheetAt(i);
        }
        return null;
    }

    private String lireString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> { 
                double v = cell.getNumericCellValue(); 
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v); 
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private boolean lireBoolean(Row row, int col) {
        String v = lireString(row, col).toLowerCase();
        return v.equals("oui") || v.equals("yes") || v.equals("true") || v.equals("1") || v.equals("o");
    }

    private boolean estLigneVide(Row row) {
        for (Cell c : row) {
            if (c != null && c.getCellType() != CellType.BLANK && !c.toString().trim().isEmpty()) return false;
        }
        return true;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}