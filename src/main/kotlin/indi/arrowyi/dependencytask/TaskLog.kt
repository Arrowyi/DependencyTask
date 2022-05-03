package indi.arrowyi.dependencytask

object TaskLog {
    fun e(msg : String){
        println("error : $msg")
    }
    fun d(msg : String){
        println("debug : $msg")
    }
}