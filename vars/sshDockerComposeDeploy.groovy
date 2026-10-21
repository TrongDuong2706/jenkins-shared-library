/**
 * Deploy one Docker Compose service to a remote host through SSH.
 *
 * The remote host pulls an already-pushed image, then starts only the selected
 * service. It is intended for a controller/build-agent -> deploy-server flow.
 *
 * Required:
 *   host                 Deploy server DNS name or IP address
 *   sshCredentialsId     Jenkins SSH Username with private key credential
 *   image                Full image reference, including its tag
 *
 * Optional:
 *   composeFile          Local Compose file to upload (defaults to docker-compose.yml)
 *   deployDirectory      Directory on the deploy server (defaults to /opt/app)
 *   composeProjectName   Existing Docker Compose project to update (optional)
 *   service              Service to update (defaults to frontend)
 *   port                 SSH port (defaults to 22)
 *   registry             Registry for a remote `docker login` (defaults to docker.io)
 *   registryCredentialsId Optional Username/Password credential for a private registry
 *
 * The Jenkins user's known_hosts must already contain the deploy server host key.
 */
def call(Map config = [:]) {
    ['host', 'sshCredentialsId', 'image'].each { requireValue(config, it) }

    String composeFile = (config.composeFile ?: 'docker-compose.yml').toString()
    String deployDirectory = (config.deployDirectory ?: '/opt/app').toString()
    String composeProjectName = (config.composeProjectName ?: '').toString().trim()
    String service = (config.service ?: 'frontend').toString()
    String port = (config.port ?: '22').toString()
    String registry = (config.registry ?: 'docker.io').toString()

    if (!fileExists(composeFile)) {
        error "Compose file not found in workspace: ${composeFile}"
    }
    if (!(service ==~ /^[A-Za-z0-9][A-Za-z0-9_.-]*$/)) {
        error "Invalid Docker Compose service name: ${service}"
    }
    if (!(port ==~ /^[0-9]{1,5}$/)) {
        error "Invalid SSH port: ${port}"
    }
    if (composeProjectName && !(composeProjectName ==~ /^[a-z0-9][a-z0-9_-]*$/)) {
        error "Invalid Docker Compose project name: ${composeProjectName}"
    }

    String composeProjectOption = composeProjectName ? "--project-name ${shellQuote(composeProjectName)}" : ''

    String remoteScript = """set -eu
cd ${shellQuote(deployDirectory)}
printf '%s\\n' ${shellQuote("FRONTEND_IMAGE=${config.image}")} > .jenkins.env
cat > docker-compose.jenkins.yml <<'EOF'
services:
  ${service}:
    image: \${FRONTEND_IMAGE}
EOF
docker pull ${shellQuote(config.image.toString())}
docker compose ${composeProjectOption} --env-file .jenkins.env --file docker-compose.yml --file docker-compose.jenkins.yml up --detach --no-deps --no-build ${shellQuote(service)}
"""

    withCredentials([sshUserPrivateKey(
        credentialsId: config.sshCredentialsId.toString(),
        keyFileVariable: 'DEPLOY_SSH_KEY',
        usernameVariable: 'DEPLOY_SSH_USER'
    )]) {
        withEnv([
            "DEPLOY_HOST=${config.host}",
            "DEPLOY_PORT=${port}",
            "DEPLOY_DIRECTORY=${deployDirectory}",
            "COMPOSE_SOURCE=${composeFile}"
        ]) {
            sh label: 'Prepare deploy directory', script: '''
                set -eu
                ssh -i "$DEPLOY_SSH_KEY" -p "$DEPLOY_PORT" \\
                  -o BatchMode=yes -o StrictHostKeyChecking=yes \\
                  "$DEPLOY_SSH_USER@$DEPLOY_HOST" "mkdir -p '$DEPLOY_DIRECTORY'"
            '''.stripIndent()

            sh label: 'Upload Docker Compose file', script: '''
                set -eu
                scp -i "$DEPLOY_SSH_KEY" -P "$DEPLOY_PORT" \\
                  -o BatchMode=yes -o StrictHostKeyChecking=yes \\
                  "$COMPOSE_SOURCE" "$DEPLOY_SSH_USER@$DEPLOY_HOST:$DEPLOY_DIRECTORY/docker-compose.yml"
            '''.stripIndent()

            loginRemoteRegistry(config.registryCredentialsId, registry)

            sh label: "Deploy ${config.image}", script: """
                set -eu
                ssh -i \"\$DEPLOY_SSH_KEY\" -p \"\$DEPLOY_PORT\" \\
                  -o BatchMode=yes -o StrictHostKeyChecking=yes \\
                  \"\$DEPLOY_SSH_USER@\$DEPLOY_HOST\" 'bash -se' <<'REMOTE_SCRIPT'
${remoteScript}
REMOTE_SCRIPT
            """.stripIndent()
        }
    }
}

private void loginRemoteRegistry(Object credentialsId, String registry) {
    if (!credentialsId) {
        return
    }

    withCredentials([usernamePassword(
        credentialsId: credentialsId.toString(),
        usernameVariable: 'REGISTRY_USERNAME',
        passwordVariable: 'REGISTRY_PASSWORD'
    )]) {
        withEnv(["REGISTRY_HOST=${registry}"]) {
            sh label: "Log deploy server in to ${registry}", script: '''
                set +x
                printf '%s' "$REGISTRY_PASSWORD" | ssh -i "$DEPLOY_SSH_KEY" -p "$DEPLOY_PORT" \\
                  -o BatchMode=yes -o StrictHostKeyChecking=yes \\
                  "$DEPLOY_SSH_USER@$DEPLOY_HOST" \\
                  "docker login '$REGISTRY_HOST' --username '$REGISTRY_USERNAME' --password-stdin"
            '''.stripIndent()
        }
    }
}

private void requireValue(Map config, String key) {
    if (!config[key]) {
        error "sshDockerComposeDeploy requires '${key}'."
    }
}

private String shellQuote(String value) {
    return "'${value.replace("'", "'\\\"'\\\"'")}'"
}
