import os
from  pathlib import Path
import pytest
from airflow.utils import db
from airflow.models import DagBag

# dags_folder = os.path.join(Path(__file__).resolve().parent.parent, "dags")
dags_folder = "/tmp/airflow_tests/dags"

@pytest.fixture(scope="session", autouse=True)
def init_airflow_db():
    """Initialise la base de données SQLite locale pour les tests."""
    db.initdb()

@pytest.fixture(scope="session")
def dagbag():
    # En contexte Molecule, on remonte à la racine pour trouver /dags
    return DagBag(dag_folder=dags_folder, include_examples=False)

def test_dag_chargement_sans_erreur(dagbag):
    """Vérifie que le DAG Airflow ne contient pas d'erreur de syntaxe."""
    dag_id = "exemple_ssh_http_airflow3"
    dag = dagbag.get_dag(dag_id)
    assert dag is not None
    assert len(dagbag.import_errors) == 0

# def test_airflow_service_and_port(host):
#     """Vérifie l'état de l'infrastructure via testinfra."""
#     # Vérifie que le fichier de configuration a été créé dans le conteneur
#     airflow_cfg = host.file("/root/airflow/airflow.cfg")
#     assert airflow_cfg.exists