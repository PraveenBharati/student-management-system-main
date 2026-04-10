pipeline {
    agent any

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
    stage('Build & Unit Tests') {
            steps {
                echo '===== Compiling code and running tests ====='
                sh 'mvn clean test -B'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
     stage('SonarQube Analysis') {
            steps {
                echo '===== Running SonarQube code quality scan ====='
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                          -Dsonar.projectName="Student Management System" \
                          -Dsonar.java.binaries=target/classes \
                          -B
                    '''
                }
            }
        }
     stage('Quality Gate') {
            steps {
                echo '===== Waiting for SonarQube quality gate result ====='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    stage('Package JAR') {
            steps {
                echo '===== Creating final JAR file ====='
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

}
}
