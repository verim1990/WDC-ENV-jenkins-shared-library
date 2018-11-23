def call(body) {

	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	

	node('swarm') {
	
		// Clean workspace before doing anything
		deleteDir()

		try {
			stage ('Clone') {
				checkout scm
			}
			stage ('Build') {
				sh "echo 'building ${config.projectName} ...'"

				commitId = sh(returnStdout: true, script: 'git rev-parse HEAD')
				imageName = "${config.imageName}:${env.BUILD_NUMBER}"
				
				sh "echo '${commitId}'"
				sh "docker build -t ${imageName}  ."

				app = docker.image(imageName)
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
				//docker.withRegistry([credentialsId: 'DockerHub']) {
				docker.withRegistry("${config.registry}") {
					app.push("${env.BUILD_NUMBER}")
					app.push("latest")
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

def get_branch_type(String branch_name) {
    def dev_pattern = ".*development"
    def release_pattern = ".*release/.*"
    def feature_pattern = ".*feature/.*"
    def hotfix_pattern = ".*hotfix/.*"
    def master_pattern = ".*master"
    if (branch_name =~ dev_pattern) {
        return "dev"
    } else if (branch_name =~ release_pattern) {
        return "release"
    } else if (branch_name =~ master_pattern) {
        return "master"
    } else if (branch_name =~ feature_pattern) {
        return "feature"
    } else if (branch_name =~ hotfix_pattern) {
        return "hotfix"
    } else {
        return null;
    }
}

def get_branch_deployment_environment(String branch_type) {
    if (branch_type == "dev") {
        return "dev"
    } else if (branch_type == "release") {
        return "staging"
    } else if (branch_type == "master") {
        return "prod"
    } else {
        return null;
    }
}