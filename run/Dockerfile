FROM jenkins/jenkins:lts-jdk17

# IMPÉRATIF : Basculer en root pour avoir les permissions d'installation
USER root

# Maintenant apt-get fonctionnera sans erreur de permission
RUN apt-get update -y && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# RECOMMANDÉ : Repasser sur l'utilisateur jenkins pour la sécurité du conteneur
USER jenkins