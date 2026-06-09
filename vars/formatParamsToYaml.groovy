import groovy.yaml.YamlBuilder

def call(Map paramsMap, String targetFilePath = 'vars_feeder.yml') {
    echo "[CI] Formatage des paramètres en YAML..."

    def builder = new YamlBuilder()
    builder(paramsMap)

    // Écriture du fichier YAML dans le workspace de l'agent
    writeFile file: targetFilePath, text: builder.toString()
    echo "[CI] Fichier YAML généré avec succès : ${targetFilePath}"

    return targetFilePath
}