# Etude comparative des approches d'exécution des tests avec Pytest 
Pour tester des DAGs Apache Airflow, valider la syntaxe et s'assurer que la logique des opérateurs fonctionne correctement est essentiel. L'utilisation de **[Pytest](https://docs.pytest.org/en/stable/)** est le standard, mais l'écosystème autour (**[Molecule](https://docs.ansible.com/projects/molecule/examples/podman/)**, **[Tox](https://tox.wiki/en/4.55.1/how-to/install.html)**) dépend grandement de votre infrastructure CI/CD et de la façon dont vous gérez vos environnements. Voici un comparatif détaillé de ces trois approches avec des exemples concrets pour chacune.

## 1. L'approche brute : Uniquement Pytest en CLI
C'est l'approche la plus simple et la plus rapide. Vous lancez vos tests directement dans votre environnement local ou dans une phase d'intégration continue (CI).

Exemple de structure de projet :

```Plaintext
projet-dags/     
├── configurations/      # Paramètres du projet (nom, env, owner), test en local
├── metier/              # Définition des DAGs Airflow
├── utilitaires/         # Helpers (SSH, Logging JSON, DAG helpers)
├── tests/               # Tests unitaires (intégrité et utilitaires de dags)
├── .gitignore           # Eléments à exclure des versions de Git
└── readme.md            # Documentation
```

Code du test On vérifie simplement qu'il n'y a pas d'erreur de syntaxe ou de dépendance circulaire (cycles) et que le DAG charge correctement.

```Python
#tests/test_dag.py
import os
from pathlib import Path
import pytest
from airflow.models import DagBag

remote_dags_folder = os.path.join(Path(__file__).resolve().parent.parent, "metier")

def test_dag_loaded_with_no_errors():
    # DagBag cherche par défaut dans le dossier dags/
    dag_bag = DagBag(dag_folder=remote_dags_folder, include_examples=False)
    
    # Vérifie qu'il n'y a pas d'erreurs d'importation
    assert len(dag_bag.import_errors) == 0, f"Erreurs d'import : {dag_bag.import_errors}"
    
    # Vérifie qu'un DAG spécifique est bien présent
    dag = dag_bag.get_dag(dag_id="my_simple_dag")
    assert dag is not None
    assert len(dag.tasks) > 0
```

Liste des commandes de lancement en se positionnant à la racine du projet :

```bash
export AIRFLOW__CORE__UNIT_TEST_MODE=True
pytest tests/
```

**Avantages** : Ultra rapide, idéal pour le feedback local pendant le développement (TDD).Inconvénients : Nécessite que toutes les dépendances Python (Airflow, providers) soient déjà installées dans votre environnement actuel. Pas d'isolation native.

## 2. L'approche Infrastructure-as-Code : Pytest dans Ansible Molecule

Ansible Molecule est conçu pour tester des rôles Ansible. Si vos DAGs Airflow sont déployés via Ansible sur des serveurs ou des clusters, Molecule permet de lever une instance de test (souvent un conteneur Docker), d'y appliquer le rôle Ansible pour installer Airflow, puis de lancer les tests pytest à l'intérieur de cette instance pour valider le déploiement.

**Exemple de structure** :

```Plaintext
airflow.unittests/
├── defaults/
│   └── main.yml  # Variables par défaut du rôle ansible
├── meta/
│   └── main.yml  # Manifest d'installation du rôle
├── tasks/
│   └── main.yml  # Etapes du le rôle de déploiement des DAGs Airflow
└── molecule/
    └── default/
        ├── tests/
        ├── converge.yml
        ├── molecule.yml
        └── verify.yml  # C'est ici qu'on lance pytest
```
**Configuration de l'infrastructure de test**

Le fichier molecule.yml est le manifeste central qui configure l'environnement de test d'un rôle Ansible en définissant le pilote de virtualisation (comme Docker) et les systèmes d'exploitation cibles à instancier. Il orchestre également l'ensemble du cycle de vie des tests, spécifiant la séquence exacte des playbooks à exécuter et les outils de vérification à lancer pour valider la conformité du code déployé.

```YAML
#molecule/default/molecule.yml
---
dependency:
  name: galaxy # Télécharge les rôles/collections requis

driver:
  name: podman # Pilote pour créer les conteneurs de test

platforms:

  - name: postgres-db # Conteneur BDD PostgreSQL
    image: postgres:16 # Image officielle Postgres v16
    pre_build_image: true # Utilise l'image sans la re-builder
    become: true # Exécute avec privilèges élevés
    networks:
      - name: airflow-network # Connexion au réseau partagé Airflow
    groups: 
      - test_airflow # Groupe Ansible pour l'inventaire
    env:
      POSTGRES_USER: airflow # User BDD par défaut
      POSTGRES_PASSWORD: airflow # Mot de passe BDD
      POSTGRES_DB: airflow # Nom de la base créée

  - name: airflow-scheduler # Conteneur Planificateur Airflow
    image: yvesconst/airflow-scheduler:1.0.0-molecule # Image personnalisée Scheduler
    pre_build_image: true # Pas de re-build de l'image
    become: true # Privilèges élevés activés
    command: bash -c "sleep 30 && airflow db migrate && airflow scheduler" # Attente DB + init + run
    networks:
      - name: airflow-network # Connexion au réseau commun
    groups: # Groupes Ansible pour l'inventaire
      - airflow 
      - test_airflow

  - name: airflow-webserver # Conteneur Interface Web Airflow
    image: yvesconst/airflow-webserver:1.0.0-molecule # Image personnalisée Webserver
    pre_build_image: true # Pas de re-build de l'image
    become: true # Privilèges élevés activés
    command: sleep infinity # Maintient le conteneur actif
    volumes:
        - /sys/fs/cgroup:/sys/fs/cgroup:ro # Montage cgroups (requis pour systemd)
    published_ports:
      - "8085:8080" # Redirection port 8085 (hôte) -> 8080 (conteneur)
    networks:
      - name: airflow-network # Connexion au réseau commun
    groups: # Groupes Ansible pour l'inventaire
      - airflow 
      - webserver 
      - test_airflow

provisioner:
  name: ansible # Outil pour appliquer la configuration
  env:
    ANSIBLE_VERBOSITY: "3" # Mode verbeux (-vvv) pour le debug
    options:
      v: true # Active la verbosité de base

verifier:
  name: ansible # Utilise Ansible pour valider les tests

scenario:
  name: default # Nom du scénario de test
  test_sequence:
    - dependency # 1. Récupération des dépendances
    - cleanup # 2. Nettoyage pré-test
    - destroy # 3. Suppression des vieux conteneurs
    - syntax # 4. Vérification syntaxique Ansible
    - create # 5. Création des conteneurs Docker
    - prepare # 6. Configuration initiale (si existante)
    - converge # 7. Exécution des playbooks à tester
    - idempotence # 8. Vérification de la non-répétition des actions
    - side_effect # 9. Vérification des effets secondaires
    - verify # 10. Exécution des tests de conformité
    - cleanup # 11. Nettoyage post-test
    - destroy # 12. Destruction finale des conteneurs
```

**Test du rôle Ansible**
Le fichier converge.yml est le playbook Ansible central du scénario Molecule qui a pour rôle d'appliquer concrètement vos tâches sur les plateformes de test éphémères. Son exécution simule un déploiement en conditions réelles, permettant de vérifier que votre rôle s'exécute sans erreur et qu'il est correctement **idempotent** lors d'un second passage.

````yaml
# converge.yml
---
- name: Converge # Nom de la phase principale de configuration
  hosts: webserver # Cible uniquement le groupe de conteneurs 'webserver'
  gather_facts: false # Désactive la collecte des faits pour optimiser le temps
  vars:
    ansible_python_interpreter: /usr/bin/python3 # Force l'usage du Python de l'environnement virtuel ciblé
  roles:
    - airflow.unittests # Applique le rôle Ansible nommé 'airflow.unittests' à la cible
````

**Le script de vérification Ansible**

Le fichier verify.yml est le playbook Ansible exécuté à la toute fin du cycle Molecule pour contrôler que l'infrastructure a été correctement configurée par le rôle. Il contient des tâches de vérification (via des modules Ansible comme stat ou assert, ou en appelant des outils externes comme Tox/Pytest) qui valident que les services sont actifs, les fichiers présents et les configurations conformes aux attentes.

````yaml
#molecule/default/verify.yml
- name: Verify # Nom du playbook de vérification
  hosts: webserver # Exécution ciblée sur le groupe 'webserver'
  gather_facts: false # Désactive la collecte des faits système pour aller plus vite

  vars: # Déclaration des variables de test
    remote_test_dir: "/tmp/airflow_tests" # Dossier temporaire sur le conteneur
    airflow: # Structure de données pour Airflow
      remote_dags_dir: /opt/airflow/dags # Chemin des DAGs
      remote_dir: /opt/airflow # Dossier principal d'Airflow
      venv_path: /app/.venv # Chemin de l'environnement virtuel Python
  tasks:
    - name: Vérifier que le DAG est chargé # Étape 1 : Lister les DAGs actifs
      command: | # Bloc de commandes à exécuter
        airflow db migrate
        airflow dags list
      register: dags # Sauvegarde le résultat dans la variable 'dags'
      changed_when: false # Indique qu'il s'agit d'une lecture (pas de modification d'état)
      args:
        chdir: "{{ remote_test_dir }}" # Exécute la commande depuis ce dossier
      environment:
        AIRFLOW_HOME: "{{ airflow.remote_dir }}" # Injecte la variable d'environnement requise

    - name: Vérifier la présence du DAG # Étape 2 : Test de validation
      assert: # Module d'assertion Ansible
        that:
          - "'exemple_ssh_http_airflow3' in dags.stdout" # Vérifie que le nom du DAG apparaît dans le résultat précédent
````

Commande de lancement :
```Bash
molecule test
```

Avantages : Test en conditions réelles (intégration). On valide que le DAG fonctionne avec la configuration Airflow générée par Ansible.Inconvénients : Plus lent (il faut build/run le conteneur). Dépend de l'état de l'image Docker de test.

## 3. L'approche Matrix & Isolation Maximale : Tox + Pytest dans Ansible Molecule

Dans l'écosystème **Ansible Molecule**, l'utilisation de **Tox** pour exécuter vos tests **Pytest** sur des DAGs **Airflow** garantit une étanchéité parfaite de vos tests grâce à l'isolation stricte des environnements virtuels Python. Pendant que **Molecule** orchestre le déploiement de l'infrastructure et de vos fichiers, **Tox** prend le relais pour créer un environnement propre et éphémère, capable de tester la conformité de vos DAGs (syntaxe, dépendances, logique métier) sur plusieurs versions d'Airflow et de Python simultanément. Cela permet de séparer clairement la validation de l'infrastructure (Ansible) de la validation du code applicatif (Airflow), tout en évitant les faux positifs liés aux configurations locales de vos machines ou de votre CI.

**Installation de Tox**

```bash
# Installer avec pip
pip install tox
# Installer avec uv
uv tool install tox
```

**Exemple de structure**
La structure est identique à **Molecule**, mais on ajoute un fichier tox.ini qui sera exécuté à l'intérieur de l'instance par **Molecule**.

```Plaintext
airflow.unittests/
├── defaults/
│   └── main.yml  # Variables par défaut du rôle ansible
├── meta/
│   └── main.yml  # Manifest d'installation du rôle
├── tasks/
│   └── main.yml  # Etapes du le rôle de déploiement des DAGs Airflow
├── molecule/
│    └── default/
│        ├── tests/
│        ├── converge.yml
│        ├── molecule.yml
│        └── verify.yml  # C'est ici qu'on lance pytest
└── tox.ini
```

**Configuration Tox (tox.ini)**

```ini
[tox]
minversion = 4.0 # Version minimale de Tox requise
envlist = py311-ansible12 # Environnement de test par défaut (Python 3.11 + Ansible 12)
skipsdist = true # Évite de créer un package installable (projet non-Python standard)

[testenv]
basepython = py311 # Force l'utilisation de Python 3.11 pour cet environnement

passenv = * # Transmet toutes les variables d'environnement de l'hôte à Tox

deps =
    ansible-core>=2.13,<2.17 # Moteur Ansible cible (versions 2.13 à 2.16)
    molecule # Outil principal de test pour les rôles Ansible
    molecule-plugins[docker] # Driver pour permettre à Molecule d'utiliser Docker
    pytest-molecule # Extension pour lier Pytest et Molecule
    pytest # Framework de test Python standard
    pytest-ansible # Permet d'utiliser les modules Ansible dans Pytest
    pytest-testinfra # Permet de tester l'état de l'infrastructure via Pytest
    apache-airflow>=3.1.8 # Core d'Airflow pour la compatibilité locale/tests
    apache-airflow-providers-http # Fournisseur Airflow pour les tâches HTTP
   apache-airflow-providers-ssh # Fournisseur Airflow pour les tâches SSH

commands =
    molecule test --scenario-name default # Commande finale : lance le cycle complet de Molecule
```

Commande de lancement :
  
```Bash
tox -r -e py311-ansible12
```

**Avantages** : Robustesse absolue. Idéal pour les équipes qui maintiennent des plateformes Airflow partagées ou des librairies de DAGs customisés devant tourner sur plusieurs environnements (Prod/horsProd).
**Inconvénients** : Complexité de configuration élevée, temps d'exécution relativement long à cause du build des différents environnements virtuels dans le conteneur de test.

## 4. Tableau Comparatif Récapitulatif

| Critère             | Pytest CLI unique                             | Pytest dans Molecule                               | Tox + Pytest dans Molecule                                                             |
| :------------------ |:----------------------------------------------|:---------------------------------------------------|:---------------------------------------------------------------------------------------|
| Objectif Principal  | Valider la logique du code rapidement.        | Valider le déploiement Ansible + le code.          | Valider la compatibilité multi-versions + le déploiement.                              |
| Vitesse d'exécution | Très rapide (quelques secondes)               | Lent (création de l'infra Docker)                  | Très lent (Infra + création des venvs)                                                 |
| Isolation           | Aucune (dépend de votre machine)              | Moyenne (isolé dans un conteneur)                  | Maximale (isolé dans un venv dans un conteneur)                                        |
| Complexité          | Faible                                        | Moyenne                                            | Élevée                                                                                 |
| Cas d'usage idéal   | Phase de dev local / CI ultra-rapide sur Git. | Équipes DevOps gérant l'infra Airflow via Ansible. | Éditeurs de logiciels ou équipes Plateforme avec de fortes exigences de mise à niveau. |

Dans notre contexte, il est preférable d'utiliser juste Pytest dans Molecule, pour réduire la complexité de l'adoption de la stratégie de test et de la mise en œuvre par les Ops.