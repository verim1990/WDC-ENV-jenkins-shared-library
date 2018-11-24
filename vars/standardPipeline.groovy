def call(body) {

	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	
	env.DOCKER_REGISTRY = "${config.docker_registry}"
	env.DOCKER_IMAGE = "${config.docker_image}"
	env.DOCKER_REVISION = "latest"
	
	node('swarm') {

		deleteDir()

		try {
			stage ('Clone') {
				checkout scm
			}
			stage ('Build') {		
				sh "docker build -t ${env.DOCKER_IMAGE}  ."

				app = docker.image(env.DOCKER_IMAGE)
			}
			stage ('Tests') {
				parallel 'static': {
					sh "echo 'shell scripts to run static tests...'"
					
					app.inside {
						sh "echo 'static inside test'"
					}
				},
				'unit': {
					sh "echo 'shell scripts to run unit tests...'"
				},
				'integration': {
					sh "echo 'shell scripts to run integration tests...'"
				}
			}
			stage ('Push image') {
				docker.withRegistry("${env.DOCKER_REGISTRY}") {
					app.push("${env.BUILD_NUMBER}")
					app.push("${env.DOCKER_REVISION}")
				}
			}
			stage ('Deploy') {	
			}
		} catch (err) {
			currentBuild.result = 'FAILED'
			throw err
		}
	}
}