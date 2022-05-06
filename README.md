# DependencyTask
This lib is the framework for dependency tasks, specially for the asynchronous tasks.
## Backgroud
Image that there is a progress with some steps, and the order of the dependencies for those steps maybe not fixed. Most ofter ,like in the android app, we need to do some initilaize progress, such as require permissions , check the account or do some asynchronous progress before letting the user to use the app, and there may be some dependency between the steps， for some requirements , the order of the dependencies may be changed，
this lib is just for that scenario.

## Installation
### For gradle 
Add it in your root build.gradle at the end of repositories:
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
Add the dependency:
```gradle
dependencies {
  implementation 'com.github.Arrowyi:DependencyTask:TheVersion'
}
```

### For maven
Add the JitPack repository to your build file
```gradle
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```
Add the dependency
```gradle
<dependency>
  <groupId>com.github.Arrowyi</groupId>
  <artifactId>DependencyTask</artifactId>
  <version>TheVersion</version>
</dependency>
```
## How to use
### Step 1 : inherite from the Task class
- First, you need to divide your progress into serval tasks
- each task need to inherite from the **Task** class
- call **addDependency** function with the task instance to build the releationship of the tasks
- implement the **action** suspend function for your job
- if the task done its job, call the **actionResult** function with true(job done successfully) or false(job done failed) to notify the framework the result of the task

### Step 2 : start with the TaskProcessor
- create the **TaskProcessor** class initance with a --InitTask-- (which is your first order task)
- call the **start** funcation to get a 'flow<ProgressStatus>'
- call **collet** function of the flow to get the **ProgressStatus** class( which is represent for the progress status ) sequentially
  
## Example

  ```kotlin
  import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class SuspendTask : Task() {
    override suspend fun doAction() {
        //do suspend job
        delay(100)
        actionResult(true)
    }
}

class BlockTask : Task() {
    override suspend fun doAction() {
        //some block job
        Thread.sleep(100)
        actionResult(true)
    }
}

class SomeTask(private val name: String) : Task() {
    override suspend fun doAction() {
        actionResult(true)
    }

    override fun getTaskDescription(): String {
        return name
    }
}

fun main() = runBlocking {
    val task1 = SuspendTask()
    val task2 = BlockTask()
    val task3 = SomeTask("task 3")
    val task4 = SomeTask("task 4")

    task2.addDependency(task1)
    task3.addDependency(task1)
    task3.addDependency(task2)
    task4.addDependency(task3)

    TaskProcessor(task1).start().collect() {
        when (it) {
            is Check -> println("check result is : ${it.result}")
            is Progress -> println("task done with ${it.task.getDescription()}")
            is Complete -> println("task is complete")
            is Failed -> println("task is failed with ${it.failedTask.getDescription()}")
        }
    }

}

  ```
  
  ### Attention 
  - there should be **only one** init task node as the root node of the dependence tree
  - you must pass the **root node** to initlaze the **TaskPrograss**
  - if there is a circular dependency, the **Check** status will pass a **result** value false.
  - you must call **actionResult** function of the task after your job is done, whatever it is failed or has an exception.

  ## Progress example
  
  ![text](http://assets.processon.com/chart_image/627502611e08532771695e9f.png)
  
  - As the above dependency task tree, when you beginning to call the function **collect** of the flow, it will pass the `check` status with true to indicate that the circular dependency check is OK.
  - And then ，it will pass the `Progress` status to indicate which task is done. the TaskProgress will call task's **action** function concurrently if their dependency tasks are done. 
  - As the above tree, the task7's **action** function will be called after task2 and task5 are done, and task7 and task8 maybe called concurrently.
  - For example, after task2 is done, the **onDependencyDone** function of task7 will be called, you could override this function to get some info from the dependency task, __you must call the super function if you override this function__
  - And the `Complete` status will passed if all the tasks are done successfully, meanwhile 'Failed` status will passed immedietely if there is a failed task. any of those two status will end the progress.
  - If the `Check` status is with a result false, the progress will also be end.
  
  ## Release Version
  The last release version is v1.0.4
