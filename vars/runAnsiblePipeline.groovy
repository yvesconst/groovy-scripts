def call(Map config = [:]) {
    echo "[CI] Lancement de Molecule via UV..."
    sh "uv run molecule test"
}