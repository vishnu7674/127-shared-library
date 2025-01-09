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
     def k8sdeploy(fileName, docker_image) {
        jenkins.sh """
        echo "********************* Entering into Kubernetes Deployment Method *********************"
        echo "Listing the files in the workspace"
        sed -i "s|DIT|${docker_image}|g" ./.cicd/${fileName}
        kubectl apply -f ./.cicd/${fileName}
        """
    }
    
}




