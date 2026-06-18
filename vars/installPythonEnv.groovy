def call(Map config = [:]) {
    // Version de Python souhaitée (par défaut 3.13 comme demandé)
    def pythonVersion = config.get('pythonVersion', '3.13')

    echo "[CI] Initialisation de l'environnement avec UV (Astral) et Python ${pythonVersion}..."

    sh """
        # 1. Installation de 'uv' de manière isolée si non présent sur le worker Jenkins
        if ! command -v uv &> /dev/null; then
            echo "[CI] 'uv' non trouvé. Installation en cours..."
            apt-get update
            apt-get install -y --no-install-recommends curl
            apt-get clean
            rm -rf /var/lib/apt/lists/*
            curl -LsSf https://astral.sh/uv/install.sh | sh
            # Ajout temporaire au PATH pour la suite du script si installé localement
            export PATH="\$HOME/.local/bin:\$PATH"
        fi

        # Vérification de la présence du fichier pyproject.toml
        if [ ! -f "pyproject.toml" ]; then
            echo "[ERROR] Le fichier pyproject.toml est introuvable à la racine du workspace."
            exit 1
        fi

        # 2. Demander à 'uv' de télécharger et d'utiliser la version spécifique de Python (3.13)
        echo "[CI] Configuration de Python ${pythonVersion} via uv..."
        uv python install ${pythonVersion}

        # 3. Création/Gestion de l'environnement virtuel (.venv par défaut pour uv)
        # S'il existe déjà, uv le réutilisera intelligemment
        echo "[CI] Création/Vérification de l'environnement virtuel..."
        uv venv --python ${pythonVersion}

        # 4. Synchronisation des dépendances
        # Cette commande installe exactement ce qui est dans le pyproject.toml et nettoie le surplus
        echo "[CI] Synchronisation des dépendances depuis pyproject.toml..."
        uv sync

        # 5. Validation des versions installées dans l'environnement virtuel
        echo "[CI] Outils installés avec succès :"
        uv run python --version
        uv run ansible --version
        uv run molecule --version
    """
}