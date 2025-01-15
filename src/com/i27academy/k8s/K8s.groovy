package com.i27academy.k8s

// all the methods 
class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }


    // Application Build

    //method to authenticate to kubernetes cluster
    def auth_login(){
        jenkins.sh """
        echo  "************************ Entering into kubernetes Authentication/Login Password ************************"
        gcloud compute instances list
        echo "************************ Get the k8s Node ************************"
        gcloud container clusters get-credentials i27-cluster --zone us-central1-c --project newone-445014
        """
    }

    //Method todeploy the application
     def k8sdeploy(fileName, docker_image, namespace) {
        jenkins.sh """
        echo "********************* Entering into Kubernetes Deployment Method *********************"
        echo "Listing the files in the workspace"
        sed -i "s|DIT|${docker_image}|g" ./.cicd/${fileName}
        kubectl apply -f ./.cicd/${fileName} -n ${namespace}
        """
    }

    // Helm Deployments
    def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace) {
        jenkins.sh """
        echo "********************* Entering into Helm Deployment Method *********************"
        helm version
        # lets verify if chart exists
        echo "Verifying if the helm chart exists"
        helm install ${appName}-${env}-chart -f ./.cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} ${helmChartPath} -n ${namespace}
        """
    }

    // git clone 
    def gitClone() {
        jenkins.sh """
        echo "********************* Entering into Git Clone Method *********************"
        git clone -b master https://github.com/vishnu7674/127-shared-library.git
        echo "********************* Listing the files in the workspace *********************"
        ls -la
        """
    }
    
}




