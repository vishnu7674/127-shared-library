package com.i27academy.builds


class Calculator {
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }

    // Application Build

    def buildApp(appName) {
        jenkins.sh """
            echo "Building the $appName Application"
            'mvn clean package -DSkipTests=true'
        """
    }
}


def buildApp() {
    return {
        echo "Building the ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DSkipTests=true'
    }
}