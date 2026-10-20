/**
 * Build a Docker image.
 *
 * Required:
 *   image        Repository/name of the image, e.g. registry.example.com/team/api
 *
 * Optional:
 *   tag          Image tag (defaults to BUILD_NUMBER, or "latest" outside Jenkins)
 *   context      Docker build context (defaults to .)
 *   dockerfile   Dockerfile path (defaults to Dockerfile)
 *   buildArgs    Map of Docker build arguments
 *   labels       Map of image labels
 *   noCache      Disable Docker layer cache (defaults to false)
 *
 * Returns the fully-qualified image reference, e.g. registry.example.com/team/api:42.
 */
def call(Map config = [:]) {
    requireValue(config, 'image')

    String imageRef = "${config.image}:${config.tag ?: (env.BUILD_NUMBER ?: 'latest')}"
    String command = "docker build --tag ${shellQuote(imageRef)}"

    if (config.dockerfile) {
        command += " --file ${shellQuote(config.dockerfile.toString())}"
    }
    if (config.noCache) {
        command += ' --no-cache'
    }
    (config.buildArgs ?: [:]).each { key, value ->
        command += " --build-arg ${shellQuote("${key}=${value}")}"
    }
    (config.labels ?: [:]).each { key, value ->
        command += " --label ${shellQuote("${key}=${value}")}"
    }
    command += " ${shellQuote((config.context ?: '.').toString())}"

    sh label: "Build ${imageRef}", script: command
    return imageRef
}

private void requireValue(Map config, String key) {
    if (!config[key]) {
        error "dockerBuild requires '${key}'."
    }
}

private String shellQuote(String value) {
    return "'${value.replace("'", "'\\\"'\\\"'")}'"
}
