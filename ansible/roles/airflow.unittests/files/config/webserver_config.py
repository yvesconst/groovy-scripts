import os
from airflow.www.fab_security.manager import AUTH_DB


basedir = os.path.abspath(os.path.dirname(__file__))

AUTH_TYPE = AUTH_DB

AUTH_USER_REGISTRATION = False

APP_NAME = "Airflow 3.1 - Production"
