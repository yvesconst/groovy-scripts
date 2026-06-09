def call(Map config = [:]) {
    def repoUrl = config.get('repoUrl')
    def branch = config.get('branch', 'main')
    def credentialsId = config.get('credentialsId')

    if (!repoUrl || !credentialsId) {
        error "gitCheckout: 'repoUrl' et 'credentialsId' sont requis."
    }

    echo "[CI] Checkout de la branche ${branch} depuis ${repoUrl}"

    checkout([$class: 'GitSCM',
        branches: [[name: "*/${branch}"]],
        doGenerateSubmoduleConfigurations: false,
        extensions: [[$class: 'CleanBeforeCheckout']],
        submoduleCfg: [],
        userRemoteConfigs: [[credentialsId: credentialsId, url: repoUrl]]
    ])
}