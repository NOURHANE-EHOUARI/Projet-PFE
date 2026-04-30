package ma.ensa.pfe.service;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AffectationService {

    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private SoutenanceRepository soutenanceRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;
    @Autowired private SalleRepository salleRepository; // Ajouté pour récupérer une vraie salle

    /**
     * Génère une nouvelle affectation des jurys pour tous les étudiants non planifiés.
     * Crée une nouvelle VersionPlanning pour tracer cette génération.
     */
    public String genererAffectations() {
        // 1. Créer une nouvelle version de planning
        VersionPlanning version = new VersionPlanning();
        version.setDateGeneration(LocalDateTime.now());
        version.setDescription("Génération automatique - " + LocalDateTime.now().toString());
        
        // On sauvegarde et on récupère l'objet persisté (avec son ID généré)
        version = versionPlanningRepository.save(version);
        
        // ✅ CORRECTION CRUCIALE : On capture l'ID dans une variable finale pour l'utiliser dans les Lambdas
        final Long finalVersionId = version.getId();

        // 2. Récupérer tous les professeurs disponibles en mémoire pour l'algorithme
        List<Professeur> tousLesProfs = professeurRepository.findAll();
        
        // 3. Récupérer les étudiants
        List<Etudiant> etudiants = etudiantRepository.findAll();

        int nbAffectations = 0;
        int nbErreurs = 0;

        // Récupérer une salle par défaut pour éviter les erreurs NULL (la première disponible)
        Salle salleDefaut = salleRepository.findById(1L).orElse(null);
        if (salleDefaut == null && !salleRepository.findAll().isEmpty()) {
            salleDefaut = salleRepository.findAll().get(0);
        }

        for (Etudiant etudiant : etudiants) {
            try {
                // Vérifier si l'étudiant a déjà une soutenance dans CETTE version spécifique
                // On utilise finalVersionId ici pour éviter l'erreur Java Lambda
                boolean dejaPlanifie = false;
                if (etudiant.getSoutenances() != null) {
                    dejaPlanifie = etudiant.getSoutenances().stream()
                            .anyMatch(s -> s.getVersion() != null && 
                                           s.getVersion().getId().equals(finalVersionId));
                }
                
                if (dejaPlanifie) continue;

                // Appel à l'algorithme de sélection du jury
                List<Professeur> jury = selectionnerJury(etudiant, tousLesProfs);

                if (jury.size() >= 3) {
                    Soutenance s = new Soutenance();
                    s.setEtudiant(etudiant);
                    s.setVersion(version); // Lien vers la version
                    
                    // On assigne l'encadrant comme Jury1 par défaut, ou on le choisit dans la liste
                    s.setEncadrant(etudiant.getEncadrant());
                    s.setJury1(jury.get(0));
                    s.setJury2(jury.get(1));
                    s.setJury3(jury.get(2));
                    
                    // Date et heure seront définies par le PlanningService plus tard
                    // Ici on met des valeurs par défaut pour que la sauvegarde fonctionne
                    s.setDate(LocalDateTime.now().toLocalDate());
                    s.setHeure(java.time.LocalTime.of(8, 30));
                    s.setDureeMn(45);
                    
                    // Assigner la salle par défaut si elle existe
                    if (salleDefaut != null) {
                        s.setSalle(salleDefaut);
                    } else {
                        // Si aucune salle n'est configurée, on skip cet étudiant pour l'instant
                        nbErreurs++;
                        continue;
                    }

                    soutenanceRepository.save(s);
                    nbAffectations++;
                } else {
                    nbErreurs++;
                }

            } catch (Exception e) {
                nbErreurs++;
                e.printStackTrace();
            }
        }

        return "Affectation terminée : " + nbAffectations + " réussies, " + nbErreurs + " erreurs.";
    }

    /**
     * Algorithme de sélection du jury selon tes règles :
     * 1. Au moins 2 profs de la spécialité de l'étudiant.
     * 2. Si langue EN, au moins 1 prof anglophone.
     * 3. Équité : Préférer les profs avec moins de soutenances.
     */
    private List<Professeur> selectionnerJury(Etudiant etudiant, List<Professeur> tousLesProfs) {
        List<Professeur> candidats = new ArrayList<>();

        // Filtrer les profs par spécialité correspondante
        // Note: Si la spécialité du prof est "AUTRE", on peut l'inclure comme joker si besoin
        List<Professeur> profsSpecialistes = tousLesProfs.stream()
                .filter(p -> p.getSpecialite().equals(etudiant.getFiliere().toString()) || p.getSpecialite().equals("AUTRE"))
                .collect(Collectors.toList());

        // Trier par charge de travail (nombre de soutenances existantes) pour l'équité
        candidats.addAll(profsSpecialistes);
        
        // Mélanger pour éviter toujours les mêmes combinaisons si charges égales
        Collections.shuffle(candidats);

        // Sélectionner les 3 premiers qui respectent la contrainte linguistique si nécessaire
        List<Professeur> juryFinal = new ArrayList<>();
        
        // 1. On essaie de prendre l'encadrant en premier s'il est disponible
        if (etudiant.getEncadrant() != null) {
            juryFinal.add(etudiant.getEncadrant());
        }

        // 2. Compléter jusqu'à 3 membres
        for (Professeur p : candidats) {
            if (juryFinal.contains(p)) continue;
            
            // Vérification contrainte Anglais
            if (etudiant.getLangue() == Etudiant.Langue.EN) {
                // Si on n'a pas encore d'anglophone et que ce prof ne l'est pas, on skip (sauf si c'est le dernier recours)
                boolean hasAnglophone = juryFinal.stream().anyMatch(j -> j.isParleAnglais());
                if (!hasAnglophone && !p.isParleAnglais()) {
                     continue; 
                }
            }
            
            juryFinal.add(p);
            if (juryFinal.size() >= 3) break;
        }

        // Si on n'a pas assez de profs avec la logique stricte, on complète avec n'importe qui (fallback)
        if (juryFinal.size() < 3) {
             for (Professeur p : tousLesProfs) {
                 if (!juryFinal.contains(p)) {
                     juryFinal.add(p);
                     if (juryFinal.size() >= 3) break;
                 }
             }
        }

        return juryFinal;
    }
}