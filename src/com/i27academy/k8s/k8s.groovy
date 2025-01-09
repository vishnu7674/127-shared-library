package com.i27academy.k8s


class k8s {
    def jenkins
    k8s(jenkins) {
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




