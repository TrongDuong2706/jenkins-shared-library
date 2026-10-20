/**
 * Execute a Docker Compose v2 command.
 *
 * Required:
 *   action       One of: up, down, pull, restart, ps, logs
 *
 * Optional:
 *   file         Compose file (defaults to docker-compose.yml)
 *   projectName  Compose project name
 *   envFile      Environment file passed to Docker Compose
 *   detached     Add -d to `up` (defaults to true)
 *   removeOrphans Add --remove-orphans to `up` (defaults to true)
 *   build        Add --build to `up` (defaults to false)
 *   extraArgs    Extra Compose arguments; use only values controlled by the repository
 */
def call(Map config = [:]) {
    String action = (config.action ?: '').toString()
    if (!(action in ['up', 'down', 'pull', 'restart', 'ps', 'logs'])) {
        error "dockerCompose requires action: up, down, pull, restart, ps, or logs."
    }

    String command = 'docker compose'
    command += " --file ${shellQuote((config.file ?: 'docker-compose.yml').toString())}"
    if (config.projectName) {
        command += " --project-name ${shellQuote(config.projectName.toString())}"
    }
    if (config.envFile) {
        command += " --env-file ${shellQuote(config.envFile.toString())}"
    }
    command += " ${action}"

    if (action == 'up') {
        if (config.detached != false) {
            command += ' --detach'
        }
        if (config.removeOrphans != false) {
            command += ' --remove-orphans'
        }
        if (config.build) {
            command += ' --build'
        }
    }
    if (config.extraArgs) {
        command += " ${config.extraArgs}"
    }

    sh label: "Docker Compose ${action}", script: command
}

private String shellQuote(String value) {
    return "'${value.replace("'", "'\\\"'\\\"'")}'"
}
