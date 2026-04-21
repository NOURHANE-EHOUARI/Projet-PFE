package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.ContrainteRepository;
import ma.ensa.pfe.dao.EtudiantRepository;
import ma.ensa.pfe.dao.ProfesseurRepository;
import ma.ensa.pfe.dao.SalleRepository;
import ma.ensa.pfe.model.*;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service d'import Excel (feuilles "professeurs", "salles", "etudiants").
 * Compatible .xlsx et .xls via Apache POI 5.2.
 *
 * @author Membre A
 */
@Service
@Transactional
public class ExcelImportService {

    @Autowired private EtudiantRepository   etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private ContrainteRepository contrainteRepository;
    @Autowired private SalleRepository      salleRepository;

    // ══════════════════════════════════════════════
    //  RÉSULTAT D'IMPORT
    // ══════════════════════════════════════════════

    /**
     * DTO encapsulant les compteurs et erreurs d'un import.
     */
    public static class ImportResult {
        private int profsImportes        = 0;
        private int etudiantsImportes    = 0;
        private int contraintesImportees = 0;
        private int sallesImportees      = 0;
        private final List<String> erreurs = new ArrayList<>();

        public void addErreur(String e)           { erreurs.add(e); }
        public int getProfsImportes()             { return profsImportes; }
        public int getEtudiantsImportes()         { return etudiantsImportes; }
        public int getContraintesImportees()      { return contraintesImportees; }
        public int getSallesImportees()           { return sallesImportees; }
        public List<String> getErreurs()          { return erreurs; }
        public boolean hasErreurs()               { return !erreurs.isEmpty(); }

        void incrProfs(int n)        { profsImportes += n; }
        void incrEtudiants(int n)    { etudiantsImportes += n; }
        void incrContraintes(int n)  { contraintesImportees += n; }
        void incrSalles(int n)       { sallesImportees += n; }
    }

    // ══════════════════════════════════════════════
    //  POINT D'ENTRÉE
    // ══════════════════════════════════════════════

    /**
     * Importe le fichier Excel complet.
     * Ordre : professeurs → salles → étudiants (dépendance FK).
     *
     * @param file fichier .xlsx / .xls uploadé
     * @return ImportResult avec compteurs et erreurs
     */
    public ImportResult importerFichier(MultipartFile file) throws Exception {
        ImportResult result = new ImportResult();

        try (InputStream is = file.getInputStream();
             Workbook wb   = ouvrirWorkbook(file.getOriginalFilename(), is)) {

            // 1. Professeurs en premier (les étudiants en dépendent)
            Sheet sheetProfs = trouverFeuille(wb, "professeurs");
            if (sheetProfs != null) {
                importerProfesseurs(sheetProfs, result);
            } else {
                result.addErreur("Feuille 'professeurs' introuvable dans le fichier Excel.");
            }

            // 2. Salles (l'algorithme en a besoin)
            Sheet sheetSalles = trouverFeuille(wb, "salles");
            if (sheetSalles != null) {
                importerSalles(sheetSalles, result);
            } else {
                result.addErreur("Feuille 'salles' introuvable dans le fichier Excel.");
            }

            // 3. Étudiants en dernier (dépendent des professeurs)
            Sheet sheetEtudiants = trouverFeuille(wb, "etudiants");
            if (sheetEtudiants != null) {
                importerEtudiants(sheetEtudiants, result);
            } else {
                result.addErreur("Feuille 'etudiants' introuvable dans le fichier Excel.");
            }
        }

        return result;
    }

    // ══════════════════════════════════════════════
    //  IMPORT PROFESSEURS
    // ══════════════════════════════════════════════

    /**
     * Format attendu (ligne 1 = en-têtes) :
     * A: nom | B: prenom | C: specialite | D: parle_anglais (oui/yes/true/1)
     * E: date_indispo (YYYY-MM-DD, optionnel)
     * F: heure_debut  (HH:mm, optionnel)
     * G: heure_fin    (HH:mm, optionnel)
     */
    private void importerProfesseurs(Sheet sheet, ImportResult result) {
        int imported    = 0;
        int contraintes = 0;

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;

            try {
                String nom        = lireString(row, 0).toUpperCase().trim();
                String prenom     = capitalize(lireString(row, 1).trim());
                String specialite = lireString(row, 2).trim();
                boolean parleAng  = lireBoolean(row, 3);

                if (nom.isBlank() || prenom.isBlank() || specialite.isBlank()) {
                    result.addErreur("Prof ligne " + (i + 1)
                            + " : nom/prénom/spécialité manquants.");
                    continue;
                }

                // Upsert : mise à jour si déjà présent
                Professeur prof = professeurRepository
                        .findByNomAndPrenom(nom, prenom)
                        .orElse(new Professeur());
                prof.setNom(nom);
                prof.setPrenom(prenom);
                prof.setSpecialite(specialite);
                prof.setParleAnglais(parleAng);
                professeurRepository.save(prof);
                imported++;

                // Contrainte d'indisponibilité (optionnel)
                String dateStr = lireString(row, 4).trim();
                if (!dateStr.isBlank()) {
                    try {
                        LocalDate date  = LocalDate.parse(dateStr);
                        LocalTime debut = parseTime(lireString(row, 5));
                        LocalTime fin   = parseTime(lireString(row, 6));

                        Contrainte c = new Contrainte();
                        c.setProfesseur(prof);
                        c.setDateIndisponible(date);
                        c.setHeureDebut(debut);
                        c.setHeureFin(fin);
                        contrainteRepository.save(c);
                        contraintes++;
                    } catch (Exception e) {
                        result.addErreur("Contrainte prof ligne " + (i + 1)
                                + " : date invalide '" + dateStr + "'.");
                    }
                }

            } catch (Exception e) {
                result.addErreur("Erreur prof ligne " + (i + 1) + " : " + e.getMessage());
            }
        }

        result.incrProfs(imported);
        result.incrContraintes(contraintes);
    }

    // ══════════════════════════════════════════════
    //  IMPORT SALLES
    // ══════════════════════════════════════════════

    /**
     * Format attendu :
     * A: nom | B: capacite
     */
    private void importerSalles(Sheet sheet, ImportResult result) {
        int imported = 0;

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;

            try {
                String nom = lireString(row, 0).trim();
                if (nom.isBlank()) continue;

                int capacite = 30; // valeur par défaut
                String capStr = lireString(row, 1).trim();
                if (!capStr.isBlank()) {
                    try {
                        capacite = Integer.parseInt(capStr);
                    } catch (NumberFormatException ignored) {
                        // garde la valeur par défaut
                    }
                }

                // Upsert — pas de doublon
                Salle salle = salleRepository.findByNom(nom).orElse(new Salle());
                salle.setNom(nom);
                salle.setCapacite(capacite);
                salleRepository.save(salle);
                imported++;

            } catch (Exception e) {
                result.addErreur("Salle ligne " + (i + 1) + " : " + e.getMessage());
            }
        }

        result.incrSalles(imported);
    }

    // ══════════════════════════════════════════════
    //  IMPORT ÉTUDIANTS
    // ══════════════════════════════════════════════

    /**
     * Format attendu :
     * A: nom | B: prenom | C: filiere (GI/ID) | D: langue (FR/EN)
     * E: encadrant_nom | F: encadrant_prenom | G: titre_projet (optionnel)
     */
    private void importerEtudiants(Sheet sheet, ImportResult result) {
        int imported = 0;

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;

            try {
                String nom        = lireString(row, 0).toUpperCase().trim();
                String prenom     = capitalize(lireString(row, 1).trim());
                String filiereStr = lireString(row, 2).toUpperCase().trim();
                String langueStr  = lireString(row, 3).toUpperCase().trim();
                String encNom     = lireString(row, 4).toUpperCase().trim();
                String encPrenom  = capitalize(lireString(row, 5).trim());
                String titre      = lireString(row, 6).trim();

                if (nom.isBlank() || prenom.isBlank()) {
                    result.addErreur("Étudiant ligne " + (i + 1)
                            + " : nom/prénom manquants.");
                    continue;
                }

                Filiere filiere;
                try {
                    filiere = Filiere.valueOf(filiereStr);
                } catch (IllegalArgumentException e) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : filière invalide '" + filiereStr
                            + "' (attendu : GI ou ID).");
                    continue;
                }

                Langue langue;
                try {
                    langue = Langue.valueOf(langueStr);
                } catch (IllegalArgumentException e) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : langue invalide '" + langueStr
                            + "' (attendu : FR ou EN).");
                    continue;
                }

                if (encNom.isBlank() || encPrenom.isBlank()) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : encadrant manquant.");
                    continue;
                }

                Optional<Professeur> encOpt =
                        professeurRepository.findByNomAndPrenom(encNom, encPrenom);
                if (encOpt.isEmpty()) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : encadrant '" + encNom + " " + encPrenom
                            + "' introuvable.");
                    continue;
                }

                Etudiant etudiant = etudiantRepository
                        .findByNomAndPrenom(nom, prenom)
                        .orElse(new Etudiant());
                etudiant.setNom(nom);
                etudiant.setPrenom(prenom);
                etudiant.setFiliere(filiere);
                etudiant.setLangue(langue);
                etudiant.setEncadrant(encOpt.get());
                if (!titre.isBlank()) etudiant.setTitreProjet(titre);

                etudiantRepository.save(etudiant);
                imported++;

            } catch (Exception e) {
                result.addErreur("Erreur étudiant ligne " + (i + 1)
                        + " : " + e.getMessage());
            }
        }

        result.incrEtudiants(imported);
    }

    // ══════════════════════════════════════════════
    //  UTILITAIRES POI
    // ══════════════════════════════════════════════

    private Workbook ouvrirWorkbook(String filename, InputStream is) throws Exception {
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(is);
        }
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
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private boolean lireBoolean(Row row, int col) {
        String v = lireString(row, col).toLowerCase();
        return v.equals("oui") || v.equals("yes") || v.equals("true") || v.equals("1");
    }

    private boolean estLigneVide(Row row) {
        for (Cell c : row) {
            if (c != null && c.getCellType() != CellType.BLANK
                    && !c.toString().trim().isEmpty())
                return false;
        }
        return true;
    }

    private LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalTime.parse(s.trim()); }
        catch (Exception e) { return null; }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}