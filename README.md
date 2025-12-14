# Projet "Le Bocal de Noël - Secret Santa"

## Ce que fait l'application

- Côté serveur (Scala + ZIO HTTP) :
  - Gère une liste de prénoms / "whispers" en mémoire (CRUD complet : création, lecture, modification, suppression).
  - Fournit un endpoint pour tirer un prénom au hasard (/random).
  - Diffuse en temps réel les nouveaux prénoms avec WebSocket (/stream).

- Côté client (fichier Stream_site.html) :
  - Permet à chaque personne de déposer son prénom dans le bocal.
  - Affiche en temps réel les nouveaux prénoms reçus du serveur.
  - Permet de tirer au sort un prénom.
  - Contient une petite zone de test pour les opérations CRUD.

## Lancer le serveur Scala

1. Ouvrir un terminal dans le dossier du projet
2. Lancer le serveur :
  - `sbt run`
3. Attendre que le serveur démarre (par défaut : http://localhost:8080).

## Endpoints (API)

- `GET /ping` → "pong" (test de santé)
- `GET /random` → retourne un whisper aléatoire
- `POST /whisper` → ajoute un whisper (JSON : `{ "content": "Prénom" }`)
- `GET /whispers` → liste des whispers
- `GET /whisper/{index}` → whisper à un index (404 si hors limites)
- `PUT /whisper/{index}` → modifie un whisper (JSON : `{ "content": "Nouveau" }`)
- `DELETE /whisper/{index}` → supprime un whisper
- `GET /stream` → WebSocket (stream des nouveaux whispers)

## Lancer le client (page HTML)

1. Ouvrir le fichier Stream_site.html dans un navigateur 
2. Vérifier que le serveur Scala est déjà lancé.
3. Dans la page :
   - Entrer un prénom puis cliquer sur "Envoyer" pour l’ajouter au bocal.
   - Utiliser le bouton "Tirer un prénom" pour récupérer un nom au hasard.
   - Utiliser la section "Test CRUD" pour lister, lire, modifier ou supprimer les entrées.

## Tests

- Lancer les tests : `sbt test`
- Les tests couvrent les codecs JSON et plusieurs routes HTTP en mémoire .