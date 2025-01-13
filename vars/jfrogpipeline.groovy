import com.i27academy.builds.Calculator
import com.i27academy.builds.Docker
import com.i27academy.k8s.K8s

def call(Map pipelineParams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)
    Docker docker = new Docker(this)   
    K8s k8s = new K8s(this) 
    

// this Jenkins pipeline is for Eureka deployment

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'scanOnly',
                choices: 'no\nyes',
                description: 'This will ScanOnly your application'
            )
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'This will build your application'
            )
            choice(name: 'dockerpush',
                choices: 'no\nyes',
                description: 'his will build docker image and push'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will Deploy your app to Dev env'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will Deploy your app to Test env'
            )
            choice(name: 'deployTostage',
                choices: 'no\nyes',
                description: 'This will Deploy your app to stage env'
            )
            choice(name: 'deployToprod',
                choices: 'no\nyes',
                description: 'This will Deploy your app to stage prod'
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
            DOCKER_HUB = "docker.io/vishnu7674"
            DOCKERHUB_CREDS = credentials('dockerhub_creds')
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
            stage ('Build') {
                when {
                    anyOf {
                        expression {
                            params.dockerpush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                steps {
                    script{
                        docker.buildApp("${env.APPLICATION_NAME}") //appname
                    }
                    
                }
            }
            stage ('sonar') {
                when {
                    expression {
                        params.scanOnly == 'yes'
                    }
                    // anyOf {
                    //     expression {
                    //         params.scanOnly == 'yes'
                    //         params.buildOnly == 'yes'
                    //         params.dockerpush == 'yes'
                    //     }
                    // }
                }
                steps {
                    script {
                        echo "Starting sonar scan"
                        withSonarQubeEnv('SonarQube'){  //the name we saved in system under manage jenkins
                            sh """
                            mvn clean verify sonar:sonar \
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
            }
            stage ('Docker build and push') {
                when {
                    anyOf {
                        expression {
                            params.dockerpush == 'yes'
                        }
                    }
                }
                steps {
                    // existing artifact format: i27-eureka-0.0.1-SNAPSHOT.jar
                    // My Destination artificat format: i27-eureka-buildnumber-branchname.jar
                    //echo "My JAR Source: i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING}"
                    //echo "MY JAR Destination: i27-${env.APPLICATION_NAME}-${BUILD_NUMBER}-${BRANCH_NAME}.${env.POM_PACKAGING}"
                    script {
                        dockerbuildAndpush().call()
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
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //envDeploy, hostPort, contPort
                        imageValidation().call()
                        //dockerDeploy('dev', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                        k8s.k8sdeploy("${env.k8s_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                        echo "Deployed to dev successfully"
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
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //envDeploy, hostPort, contPort
                        imageValidation().call()
                        //dockerDeploy('Test', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                        k8s.k8sdeploy("${env.k8s_TST_FILE}", docker_image, "${env.TST_NAMESPACE}")
                    }
                }
            }
            stage ('Deploy to Stage') {
                // when {
                //     expression {
                //         params.deployTostage == 'yes'
                //     }
                // }
                when {
                    allOf {
                        anyOf {
                            expression{
                                params.deployTostage == 'yes'
                            }
                            
                        }
                        anyOf {
                            branch 'release/*'
                            
                        }
                    }
                }
                steps {
                script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //envDeploy, hostPort, contPort
                        imageValidation().call()
                       // dockerDeploy('stage', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                       k8s.k8sdeploy("${env.k8s_STG_FILE}", docker_image, "${env.STG_NAMESPACE}")
                    }
                }
            }
            stage ('Deploy to prod') {
                when {
                    allOf {
                        anyOf {
                            expression {
                                params.deployToprod == 'yes'
                            }
                        }
                        anyOf {
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}",  comparator: "REGEXP" //v1.2.3

                        }
                    }
                    
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS' ) { // SECONDS, MINUTES,HOURS{
                        input message: "Deploying to ${env.APPLICATION_NAME} to production ??", ok: 'yes', submitter: 'vishnudev'
                    }
                script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //envDeploy, hostPort, contPort
                        //dockerDeploy('prod', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                        k8s.k8sdeploy("${env.k8s_PROD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                    }
                }
            }
        }
    }
}
    

//method for maven build

def buildApp() {
    return {
        echo "Building the ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DSkipTests=true'
    }
}

//method for docker build and push
def dockerBuildAndPush(){
    return {
        echo "************************* Building Docker image*************************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
        echo "************************ Login to Docker Registry ************************"
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW} i27devopsb4.jfrog.io"
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




// method for deploy containers in different env
// def dockerDeploy(envDeploy, hostPort, contPort) {
//     return {
//         echo "Deploying to dev $envDeploy environment"
//             withCredentials([usernamePassword(credentialsId: 'maha_ssh_docker_server_creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {

//                     script {
//                         sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip \"docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} \""
//                         try {
//                             // stop the container
//                             sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker stop ${env.APPLICATION_NAME}-$envDeploy"
//                             // remove the continer
//                             sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker rm ${env.APPLICATION_NAME}-$envDeploy"
//                         }
//                         catch(err) {
//                             echo "Error caught: $err"
//                         }
//                         // create the container
//                         sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker run -dit --name ${env.APPLICATION_NAME}-$envDeploy -p $hostPort:$contPort ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}" 
//                     }
//                 }
//     }
// }
// create a container
                // docker container create imagename
                // docker run -dit --name containername imageName
                // docker run -dit --name eureka-dev
               // docker run -dit --name ${env.APPLICATION_NAME}-dev -p 5761:8761 ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} 
               // run -dit --name ${env.APPLICATION_NAME}-dev -p 5761:8761
//sshpass -p password ssh -o StrictHostKeyChecking=no username@dockerserverip

