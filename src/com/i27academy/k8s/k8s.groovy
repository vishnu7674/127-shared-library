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
        """
    }
}




