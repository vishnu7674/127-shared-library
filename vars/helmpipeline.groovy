import com.i27academy.builds.Calculator
import com.i27academy.builds.Docker
import com.i27academy.k8s.K8s

def call(Map pipelineParams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)
    Docker docker = new Docker(this)   
    K8s k8s = new K8s(this)

// This Jenkinsfile is for Eureka Deployment 

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'scanOnly',
                choices: 'no\nyes',
                description: 'This will scan your application'
            )
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'This will Only Build your application'
            )
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This Will build dockerImage and Push'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Dev env'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Test env'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Stage env'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Prod env'
            )
        }
        tools {
            maven 'Maven-3.8.8'
            jdk 'JDK-17'
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            // DEV_HOST_PORT = "${pipelineParams.devHostPort}"
            // TST_HOST_PORT = "${pipelineParams.tstHostPort}"
            // STG_HOST_PORT = "${pipelineParams.stgHostPort}"
            // PRD_HOST_PORT = "${pipelineParams.prdHostPort}"
            HOST_PORT = "${pipelineParams.hostPort}"
            CONT_PORT = "${pipelineParams.contPort}"
            SONAR_TOKEN =  credentials('sonar-creds2')
            SONAR_URL = "http://34.60.91.201:9000"
            // if any errors with readMavenPom, make sure pipeline-utility-steps plugin is install in your jenkins, if not do install
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            //DOCKER_HUB = "docker.io/vishnu7674"
            //DOCKERHUB_CREDS = credentials('dockerhub_creds')
            k8s_DEV_FILE = "k8s_dev.yaml"
            k8s_TST_FILE = "k8s_tst.yaml"
            k8s_STG_FILE = "k8s_stage.yaml"
            k8s_PROD_FILE = "k8s_prod.yaml"
            DEV_NAMESPACE = "cart-dev-ns"
            TST_NAMESPACE = "cart-tst-ns"
            STG_NAMESPACE = "cart-stg-ns"
            PROD_NAMESPACE = "cart-prod-ns"
            JFROG_DOCKER_REGISTRY = "i27devops.jfrog.io"
            JFROG_DOCKER_REPO_NAME = "cont-images-docker-docker"
            JFROG_CREDS = credentials('JFROG_CREDS')
            HELM_PATH = "${workspace}/i27-shared-library/chart"
            DEV_ENV = "dev"
            TST_ENV = "tst"
            STAGE_ENV = "stage"
            PROD_ENV = "prod"

        }
        stages {
            stage ('Authentication'){
                steps {
                    echo "Executing in GCP project"
                    script {
                        k8s.auth_login()
                    }
                }
            }
            stage ('Checkout Shared Lib'){
                steps {
                    script {
                        k8s.gitClone()
                    }
                }
            }
            stage ('Build') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        docker.buildApp("${env.APPLICATION_NAME}") //appName
                    }
                }
            }
            stage ('Sonar') {
                when {
                    expression {
                        params.scanOnly == 'yes'
                    }
                    // anyOf {
                    //     expression {
                    //         params.scanOnly == 'yes'
                    //         params.buildOnly == 'yes'
                    //         params.dockerPush == 'yes'
                    //     }
                    // }
                }
                steps {
                    echo "Starting Sonar Scans"
                    withSonarQubeEnv('SonarQube'){ // The name u saved in system under manage jenkins
                        sh """
                        mvn  sonar:sonar \
                            -Dsonar.projectKey=i27-eureka \
                            -Dsonar.host.url=${env.SONAR_URL} \
                            -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                    timeout (time: 2, unit: 'MINUTES'){
                        waitForQualityGate abortPipeline: true
                    }

                }
            }
            stage ('Docker Build and Push') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps { 
                    script {
                        dockerBuildAndPush().call()
                    }
                } 
            }
            stage ('Deploy to Dev') {
                when {
                    expression {
                        params.deployToDev == 'yes'
                    }
                }
                steps {
                    script {
                        def docker_image = "${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //(envDeploy, hostPort, contPort)
                        imageValidation().call()
                        //dockerDeploy('dev', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                        //k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}") 
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${env.DEV_ENV}", "${env.HELM_PATH}", "${GIT_COMMIT}", "${env.DEV_NAMESPACE}")
                        echo "Deployed to Dev Successfully"
                    }
                }
            }
            stage ('Deploy to Test') {
                when {
                    expression {
                        params.deployToTest == 'yes'
                    }
                }
                steps {
                    script {
                        //envDeploy, hostPort, contPort)
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_TST_FILE}", docker_image, "${env.TST_NAMESPACE}")
                        echo "Deployed to Test Successfully"
                    }
                }
            }
            stage ('Deploy to Stage') {
                when {
                    allOf {
                        anyOf {
                            expression {
                                params.deployToStage == 'yes'
                                // other condition
                            }
                        }
                        anyOf{
                            branch 'release/*'
                        }
                    }
                }
                steps {
                    script {
                        //envDeploy, hostPort, contPort)
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_STG_FILE}", docker_image, "${env.STG_NAMESPACE}")
                        echo "Deployed to Stage Successfully"
                    }

                }
            }
            stage ('Deploy to Prod') {
                when {
                    allOf {
                        anyOf{
                            expression {
                                params.deployToProd == 'yes'
                            }
                        }
                        anyOf{
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}",  comparator: "REGEXP" //v1.2.3
                        }
                    }
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS' ) { // SECONDS, MINUTES,HOURS{
                        input message: "Deploying to ${env.APPLICATION_NAME} to production ??", ok: 'yes', submitter: 'hemasre'
                    }
                    script {
                        //envDeploy, hostPort, contPort)
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        k8s.k8sdeploy("${env.K8S_PRD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                        echo "Deployed to Prod Successfully"
                    }
                }
            }
            stage ('Clean') {
                steps {
                    echo "Cleaning the workspace"
                    cleanWs()
                }
            }
        }
    }
}

// Method for Maven Build
def buildApp() {
    return {
        echo "Building the ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DskipTests=true'
    }
}

// Method for Docker build and Push
def dockerBuildAndPush(){
    return {
        echo "************************* Building Docker image*************************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
        echo "************************ Login to Docker Registry ************************"
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW} i27devops.jfrog.io"
        sh "docker push ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    }
}

def imageValidation() {
    return {
        println("Attemting to Pull the Docker Image")
        try {
            sh "docker pull ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("Image is Pulled Succesfully!!!!")
        }
        catch(Exception e) {
            println("OOPS!, the docker image with this tag is not available,So Creating the Image")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}


// Method for deploying containers in diff env
// def dockerDeploy(envDeploy, hostPort, contPort){
//     return {
//         echo "Deploying to $envDeploy Environmnet"
//             withCredentials([usernamePassword(credentialsId: 'maha_ssh_docker_server_creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
//                 script {
//                     sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip \"docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}\""
//                     try {
//                         // Stop the Container
//                         sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker stop ${env.APPLICATION_NAME}-$envDeploy"
//                         // Remove the Container
//                         sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker rm ${env.APPLICATION_NAME}-$envDeploy"
//                     }
//                     catch(err) {
//                         echo "Error Caught: $err"
//                     }

//                     // Create the container
//                     sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker run -dit --name ${env.APPLICATION_NAME}-$envDeploy -p $hostPort:$contPort ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
//                 }   
//             }
//     }
// }








 