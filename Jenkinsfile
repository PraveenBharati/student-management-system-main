pipeline {
    agent any

    environment {
      
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }
stages {

        stage('Checkout') {
            steps {
                echo '===== Pulling source code from GitHub ====='
                checkout scm
            }
        }

}
}
