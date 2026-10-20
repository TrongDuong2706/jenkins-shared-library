/**
 * Push a Docker image. Logs in to a registry when credentialsId is supplied.
 *
 * Required:
 *   image        Full image reference, including tag
 *
 * Optional:
 *   registry     Registry hostname used by `docker login`
 *   credentialsId Jenkins Username/Password credential for the registry
 */
def call(Map config = [:]) {
    requireValue(config, 'image')

    def pushImage = {
        sh label: "Push ${config.image}", script: "docker push ${shellQuote(config.image.toString())}"
    }

    loginAndRun(config, pushImage)
}

private void loginAndRun(Map config, Closure body) {
    if (!config.credentialsId) {
        body()
        return
    }
    if (!config.registry) {
        error "dockerPush requires 'registry' when 'credentialsId' is set."
    }

    withCredentials([usernamePassword(
        credentialsId: config.credentialsId.toString(),
        usernameVariable: 'REGISTRY_USERNAME',
        passwordVariable: 'REGISTRY_PASSWORD'
    )]) {
        withEnv(["REGISTRY_HOST=${config.registry}"]) {
            sh label: "Login to ${config.registry}", script: '''
                set +x
                printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY_HOST" --username "$REGISTRY_USERNAME" --password-stdin
            '''.stripIndent()
        }
        body()
    }
}

private void requireValue(Map config, String key) {
    if (!config[key]) {
        error "dockerPush requires '${key}'."
    }
}

private String shellQuote(String value) {
    return "'${value.replace("'", "'\\\"'\\\"'")}'"
}
