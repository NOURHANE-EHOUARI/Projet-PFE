package ma.ensa.pfe.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

/**
 * Service d'import Excel — feuilles PROFS, SALLES, ETUDIANTS, CONFIG.
 * Affectation automatique et équitable des encadrants par filière.
 *
 * @author Membre A
 */
@Service
public class ExcelImportService {

    @Autowired private EtudiantRepository        etudiantRepository;
    @Autowired private ProfesseurRepository       professeurRepository;
    @Autowired private SalleRepository            salleRepository;
    @Autowired private VersionPlanningRepository  versionPlanningRepository;

    // ══════════════════════════════════════════════
    //  RÉSULTAT D'IMPORT
    // ══════════════════════════════════════════════
    public static class ImportResult {
        private int profsImportes     = 0;
        private int etudiantsImportes = 0;
        private int sallesImportees   = 0;
        private final List<String> erreurs = new ArrayList<>();

        public void addErreur(String e)       { erreurs.add(e); }
        public int getProfsImportes()         { return profsImportes; }
        public int getEtudiantsImportes()     { return etudiantsImportes; }
        public int getSallesImportees()       { return sallesImportees; }
        public List<String> getErreurs()      { return erreurs; }
        public boolean hasErreurs()           { return !erreurs.isEmpty(); }

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

            // 3. ÉTUDIANTS — avec affectation automatique des encadrants
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
    // ══════════════════════════════════════════════
    // Colonnes : nom | prenom | specialite_cible (GI/AUTRE) | parle_anglais (Oui/Non)
    private void importerProfesseurs(Sheet sheet, ImportResult result) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || estLigneVide(row)) continue;
            try {
                String nom        = lireString(row, 0).toUpperCase().trim();
                String prenom     = capitalize(lireString(row, 1)).trim();
                String specialite = lireString(row, 2).trim(); // GI ou AUTRE
                boolean parleAng  = lireBoolean(row, 3);

                if (nom.isBlank() || prenom.isBlank()) continue;

                Professeur prof = professeurRepository
                        .findByNomAndPrenom(nom, prenom)
                        .orElse(new Professeur());
                prof.setNom(nom);
                prof.setPrenom(prenom);
                prof.setSpecialite(specialite); // on garde GI / AUTRE tel quel
                prof.setParleAnglais(parleAng);

                professeurRepository.save(prof);
                professeurRepository.flush();
                result.incrProfs(1);

            } catch (Exception e) {
                result.addErreur("Prof ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
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
    //  IMPORT ÉTUDIANTS + AFFECTATION ENCADRANTS
    // ══════════════════════════════════════════════
    // Colonnes : cne | nom | prenom | filiere (DATA/GI/TDIA) | langue | email
    private void importerEtudiants(Sheet sheet, ImportResult result) {

        // ── 1. Lire tous les étudiants du fichier ─────────────────────────
        List<Etudiant> etudiantsAImporter = new ArrayList<>();

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

                // Filière : DATA, GI, TDIA
                Filiere filiere;
                try {
                    filiere = Filiere.valueOf(filiereStr);
                } catch (IllegalArgumentException e) {
                    result.addErreur("Étudiant " + nom + " ligne " + (i + 1)
                            + " : filière '" + filiereStr + "' invalide (DATA/GI/TDIA).");
                    continue;
                }

                // Langue
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
                etudiant.setEncadrant(null); // sera affecté ci-dessous

                etudiantsAImporter.add(etudiant);

            } catch (Exception e) {
                result.addErreur("Étudiant ligne " + (i + 1) + " : " + e.getMessage());
            }
        }

        // ── 2. Affectation équitable des encadrants ───────────────────────
        affecterEncadrantsEquitablement(etudiantsAImporter, result);

        // ── 3. Sauvegarder ────────────────────────────────────────────────
        for (Etudiant e : etudiantsAImporter) {
            if (e.getEncadrant() == null) {
                result.addErreur("Étudiant " + e.getNom() + " : impossible d'affecter un encadrant.");
                continue;
            }
            try {
                etudiantRepository.save(e);
                etudiantRepository.flush();
                result.incrEtudiants(1);
            } catch (Exception ex) {
                result.addErreur("Sauvegarde " + e.getNom() + " : " + ex.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════
    //  AFFECTATION ÉQUITABLE DES ENCADRANTS
    // ══════════════════════════════════════════════
    /**
     * Règles d'affectation :
     * 1. Préférence : profs de spécialité GI pour étudiants GI/TDIA, AUTRE pour DATA
     * 2. Répartition équitable : max ~4 étudiants par encadrant
     * 3. Si pas assez de profs spécialisés → profs AUTRE en complément
     */
    private void affecterEncadrantsEquitablement(List<Etudiant> etudiants, ImportResult result) {
        List<Professeur> tousProfs = professeurRepository.findAll();

        if (tousProfs.isEmpty()) {
            result.addErreur("Aucun professeur en base. Importez d'abord les professeurs.");
            return;
        }

        // Séparer profs GI et AUTRE
        // BOUAZZA (anglais) exclu des encadrants
        List<Professeur> profsGI = tousProfs.stream()
            .filter(p -> "GI".equalsIgnoreCase(p.getSpecialite()))
            .filter(p -> !estProfAnglaisUniquement(p))
            .collect(Collectors.toList());

        List<Professeur> profsAUTRE = tousProfs.stream()
            .filter(p -> "AUTRE".equalsIgnoreCase(p.getSpecialite()))
            .filter(p -> !estProfAnglaisUniquement(p))
            .collect(Collectors.toList());

        // Grouper étudiants par filière
        Map<Filiere, List<Etudiant>> parFiliere = etudiants.stream()
            .collect(Collectors.groupingBy(Etudiant::getFiliere));

        // Compteur de charge par prof
        Map<Long, Integer> charge = new HashMap<>();
        tousProfs.forEach(p -> charge.put(p.getId(), 0));

        System.out.println("══ AFFECTATION ENCADRANTS ══");
        System.out.printf("  Profs GI    : %d%n", profsGI.size());
        System.out.printf("  Profs AUTRE : %d%n", profsAUTRE.size());
        parFiliere.forEach((f, l) ->
            System.out.printf("  Filière %-5s : %d étudiants%n", f, l.size()));

        // Affecter chaque étudiant
        for (Etudiant etudiant : etudiants) {
            Professeur encadrant = choisirEncadrant(
                etudiant.getFiliere(), profsGI, profsAUTRE, charge);

            if (encadrant != null) {
                etudiant.setEncadrant(encadrant);
                charge.merge(encadrant.getId(), 1, Integer::sum);
            } else {
                // Dernier recours : n'importe quel prof avec la charge la plus faible
                Professeur fallback = tousProfs.stream()
                    .filter(p -> !estProfAnglaisUniquement(p))
                    .min(Comparator.comparingInt(p -> charge.getOrDefault(p.getId(), 0)))
                    .orElse(null);

                if (fallback != null) {
                    etudiant.setEncadrant(fallback);
                    charge.merge(fallback.getId(), 1, Integer::sum);
                    System.out.printf("  ⚠️  Fallback encadrant %s pour %s (%s)%n",
                        fallback.getNom(), etudiant.getNom(), etudiant.getFiliere());
                }
            }
        }

        // Afficher la répartition finale
        System.out.println("  Répartition finale :");
        charge.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
            .forEach(e -> {
                tousProfs.stream()
                    .filter(p -> p.getId().equals(e.getKey()))
                    .findFirst()
                    .ifPresent(p -> System.out.printf("    %-22s : %d étudiant(s)%n",
                        p.getNom(), e.getValue()));
            });
        System.out.println("═══════════════════════════");
    }

    /**
     * Choisit le meilleur encadrant pour une filière donnée.
     * Priorité : prof de la bonne spécialité avec la charge la plus faible.
     */
    private Professeur choisirEncadrant(Filiere filiere,
                                         List<Professeur> profsGI,
                                         List<Professeur> profsAUTRE,
                                         Map<Long, Integer> charge) {
        // GI et TDIA → préférence profs GI
        // DATA → préférence profs AUTRE (Maths/Gestion)
        List<Professeur> primaires;
        List<Professeur> secondaires;

        if (filiere == Filiere.GI || filiere == Filiere.TDIA) {
            primaires   = profsGI;
            secondaires = profsAUTRE;
        } else { // DATA
            primaires   = profsAUTRE;
            secondaires = profsGI;
        }

        // Choisir le prof primaire avec la charge la plus faible
        Professeur choix = primaires.stream()
            .min(Comparator.comparingInt(p -> charge.getOrDefault(p.getId(), 0)))
            .orElse(null);

        if (choix != null) return choix;

        // Fallback : profs secondaires
        return secondaires.stream()
            .min(Comparator.comparingInt(p -> charge.getOrDefault(p.getId(), 0)))
            .orElse(null);
    }

    private boolean estProfAnglaisUniquement(Professeur p) {
        return p.isParleAnglais()
            && p.getSpecialite() != null
            && p.getSpecialite().trim().equalsIgnoreCase("AUTRE")
            && p.getNom().equalsIgnoreCase("BOUAZZA");
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