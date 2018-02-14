node {
    try {
        stage('Clone'){
            checkout scm 
        }

        withMaven(maven: 'M3', options: [artifactsPublisher(disabled: true)] ) {
            stage('Build Bundle'){
                sh "cd bundler && mvn clean install"
            }
            stage('Deploy Update site'){
                sh "cd actual-plugin && mvn clean deploy"
            }
        }
        if (currentBuild.previousBuild.result == "FAILURE") { 
            slackSend (color: '#5cb85c',  message: "BUILD BACK TO NORMAL: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
        }
    } catch (e) {
        slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
        throw e
    }
}
