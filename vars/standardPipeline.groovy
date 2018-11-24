def call(body) {

	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	
	env.DOCKER_REVISION = "latest"
	env.DOCKER_REGISTRY = "${config.docker_registry}"
	env.DOCKER_IMAGE = "${config.docker_image}"
	
	node('swarm') {

		deleteDir()

		try {
			stage ('Clone') {
				checkout scm
			}
			
			stage ('Build') {		
				tag = "${env.DOCKER_IMAGE}:${env.DOCKER_REVISION}"
				
				sh "docker build -t ${tag} ."

				app = docker.image(tag)
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
				switch (env.BRANCH_NAME) {
					case "staging":					  
					  break

					case "master":
					  break

					default:
					  echo "Skipping deployment, branch ${env.BRANCH_NAME} not configured."
				  }
				}
			}
		} catch (err) {
			currentBuild.result = 'FAILED'
			throw err
		}
	}
}