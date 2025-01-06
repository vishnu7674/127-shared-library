package com.i27academy.builds


class Docker {
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




