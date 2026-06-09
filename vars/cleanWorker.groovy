def call(Map config = [:]) {
    echo "[CI] Nettoyage..."
    sh "uv run molecule destroy || true"
    cleanWs deleteDirs: true, notFailBuild: true
}