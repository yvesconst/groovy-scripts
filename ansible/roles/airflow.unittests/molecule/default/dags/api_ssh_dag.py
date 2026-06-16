from datetime import datetime
from airflow.sdk import dag
from airflow.providers.ssh.operators.ssh import SSHOperator
from airflow.providers.http.operators.http import HttpOperator

# 1. Définition du DAG avec le décorateur Airflow 3 / TaskFlow
@dag(
    dag_id="exemple_ssh_http_airflow3",
    start_date=datetime(2026, 1, 1),
    schedule="@daily",
    catchup=False,
    tags=["airflow3", "ssh", "http"],
)
def mon_workflow():

    # 2. Première tâche : SSHOperator
    # Il utilise une connexion configurée dans Airflow (ex: 'conn_ssh_serveur')
    liste_fichiers_ssh = SSHOperator(
        task_id="lister_repertoires_ssh",
        ssh_conn_id="conn_ssh_serveur",  # À configurer dans l'UI Airflow
        command="ls -la",
    )

    # 3. Deuxième tâche : HttpOperator
    # Appelle l'endpoint public /get de httpbin
    appel_httpbin = HttpOperator(
        task_id="appel_endpoint_httpbin",
        http_conn_id="conn_http_httpbin",  # À configurer (Host: https://httpbin.org)
        endpoint="get",
        method="GET",
        headers={"Content-Type": "application/json"},
    )

    # 4. Définition du cycle de vie / Dépendances (Syntaxe Airflow)
    liste_fichiers_ssh >> appel_httpbin

# Instanciation obligatoire du DAG pour qu'Airflow le détecte
exemple_dag = mon_workflow()