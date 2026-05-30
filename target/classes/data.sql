

INSERT INTO professeurs (nom, prenom, specialite, parle_anglais) VALUES
('BADI',     'Imad',    'Génie Informatique',       FALSE),
('BOUJRAF',  'Ahmed',   'Génie Informatique',       TRUE),
('ALAMI',    'Sara',    'Informatique Décisionnelle', FALSE),
('IDRISSI',  'Youssef', 'Informatique Décisionnelle', TRUE),
('BENNANI',  'Fatima',  'Génie Informatique',       FALSE),
('CHERKAOUI','Omar',    'Réseaux & Systèmes',       TRUE),
('FILALI',   'Nadia',   'Informatique Décisionnelle', FALSE),
('HAJJI',    'Khalid',  'Génie Informatique',       TRUE),
('MANSOURI', 'Leila',   'Réseaux & Systèmes',       FALSE),
('ZOUHRI',   'Amine',   'Génie Informatique',       TRUE);



INSERT INTO etudiants (nom, prenom, filiere, langue, encadrant_id, titre_projet) VALUES
('CHENTOUF',  'Ismail',      'GI', 'FR', 1, 'Système de gestion des absences'),
('ELAMRI',    'Badr-Eddine', 'GI', 'FR', 1, 'Application mobile de suivi PFE'),
('AFKIR',     'Mohamed',     'GI', 'EN', 2, 'AI-based scheduling system'),
('ARBAHI',    'Jawad',       'ID', 'FR', 2, 'Tableau de bord décisionnel RH'),
('TAHIRI',    'Zineb',       'ID', 'FR', 3, 'Analyse prédictive des ventes'),
('BELHAJ',    'Karim',       'GI', 'EN', 3, 'Deep learning for image classification'),
('OUALI',     'Hajar',       'ID', 'FR', 4, 'Data warehouse pour hôpital'),
('MEZIANE',   'Soufiane',    'GI', 'FR', 4, 'Plateforme e-learning Spring Boot'),
('RACHIDI',   'Aya',         'ID', 'EN', 5, 'Machine learning for fraud detection'),
('AMRANI',    'Yassine',     'GI', 'FR', 5, 'Système de ticketing en ligne'),
('BERRADA',   'Salma',       'GI', 'FR', 6, 'API REST microservices'),
('GHAZI',     'Hamza',       'ID', 'EN', 6, 'Business intelligence dashboard'),
('KADIRI',    'Rim',         'GI', 'FR', 7, 'Application de gestion hospitalière'),
('LAMRANI',   'Othmane',     'ID', 'FR', 7, 'Analyse sentiments réseaux sociaux'),
('NACIRI',    'Hind',        'GI', 'EN', 8, 'Blockchain for supply chain'),
('OUHABI',    'Zakaria',     'GI', 'FR', 8, 'Système de vote électronique'),
('QASMI',     'Mariam',      'ID', 'FR', 9, 'Visualisation données géospatiales'),
('RHAZI',     'Ilyas',       'GI', 'EN', 9, 'IoT smart campus monitoring'),
('SABRI',     'Dounia',      'ID', 'FR', 10, 'Optimisation chaîne logistique'),
('TAZI',      'Anas',        'GI', 'FR', 10, 'Chatbot intelligent Spring AI');


INSERT INTO salles (nom, capacite) VALUES
('S4A', 30),
('S4B', 30),
('S5A', 25),
('S5B', 25),
('Amphi', 100);



INSERT INTO contraintes (professeur_id, date_indisponible, heure_debut, heure_fin) VALUES
(1, '2025-06-10', '08:00', '10:00'),
(3, '2025-06-11', '14:00', '16:00'),
(6, '2025-06-12', '08:00', '12:00');