import com.i27academy.builds.Calculator
import com.i27academy.builds.Docker

def call(Map pipelineParams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)
    Docker docker = new Docker(this)  


