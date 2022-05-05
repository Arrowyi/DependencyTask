package indi.arrowyi.dependencytask

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class UnitTest {

    private lateinit var task1: Task
    private lateinit var task2: Task
    private lateinit var task3: Task
    private lateinit var task4: Task
    private lateinit var task5: Task
    private lateinit var task6: Task

    @BeforeEach
    fun setUp() {
        task1 = BlockTask("Task 1")
        task2 = BlockTask("Task 2")
        task3 = SuspendTask("Task 3")
        task4 = SuspendTask("Task 4")
        task5 = SuspendTask("Task 5")
        task6 = SuspendTask("Task 6")
    }


    @Test
    fun noCycleDependencyTest() = runBlocking {
        val initTask = BlockTask("init")
        task1.addDependency(initTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
        task4.addDependency(initTask)
        task4.addDependency(task3)
        task5.addDependency(task1)
        task5.addDependency(task4)
        task6.addDependency(task3)

        val taskProcessor = TaskProcessor(initTask)
        assertTrue { taskProcessor.check() }
        assertEquals(7, taskProcessor.tasks.size)
    }

    @Test
    internal fun hasCycleDependencyTest() = runBlocking {
        val initTask = BlockTask("init")

        task1.addDependency(initTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
        task4.addDependency(task3)
        task5.addDependency(task2)
        task5.addDependency(task3)
        task6.addDependency(task5)
        task2.addDependency(task6)


        val taskProcessor = TaskProcessor(initTask)
        assertFalse { taskProcessor.check() }
    }

    @Test
    internal fun processorRunCompletedTest() = runBlocking {
        val initTask = BlockTask("init")

        task1.addDependency(initTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
        task4.addDependency(initTask)
        task4.addDependency(task3)
        task5.addDependency(task1)
        task5.addDependency(task4)
        task6.addDependency(task3)

        assertTrue { checkProcessorResult(TaskProcessor(initTask).start(), 1000) }
    }

    @Test
    internal fun processorRunFailedTest() = runBlocking {
        val initTask = BlockTask("init")

        task1.addDependency(initTask)
//        task2.addDependency(task1)
//        task3.addDependency(task2)
        task4.addDependency(initTask)
        task4.addDependency(task3)
        task5.addDependency(task1)
        task5.addDependency(task4)
        task6.addDependency(task3)

        val taskFailed2 = SuspendFailedTask("Failed suspend task 2")
        taskFailed2.addDependency(task1)
        task3.addDependency(taskFailed2)

        assertFalse {
            checkProcessorResult(TaskProcessor(initTask).start(), 1000)
        }
    }

    @Test
    internal fun processorRunWithFirstFailedSecondTureTest() = runBlocking {
        val initTask = BlockTask("init")


        task1.addDependency(initTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
//        task4.addDependency(InitTask)
//        task4.addDependency(task3)
        task5.addDependency(task1)
//        task5.addDependency(task4)
        task6.addDependency(task3)

        val taskTwiceRun4 = SuspendTaskWithOneFailedSecondSuccess("task twice run 4")
        taskTwiceRun4.addDependency(initTask)
        taskTwiceRun4.addDependency(task3)
        task5.addDependency(taskTwiceRun4)

        val taskProcessor = TaskProcessor(initTask)

        assertFalse { checkProcessorResult(taskProcessor.start(), 1000) }
        delay(100)
        assertTrue { checkProcessorResult(taskProcessor.start(), 1000) }
    }

    @Test
    internal fun NotCallActionAgainAfterActionTrueTest() = runBlocking {
        task1 = SuspendTaskNotDoActionAgain("not call again task 1")
        task2 = SuspendTaskNotDoActionAgain("not call again task 2")
        task3 = SuspendTaskNotDoActionAgain("not call again task 3")
        val taskTwiceRun4 = SuspendTaskWithOneFailedSecondSuccess("task twice run 4")
        task5 = SuspendTaskNotDoActionAgain("not call again task 5")
        task6 = SuspendTaskNotDoActionAgain("not call again task 6")

        task2.addDependency(task1)
        task3.addDependency(task1)
        taskTwiceRun4.addDependency(task3)
        taskTwiceRun4.addDependency(task2)
        task5.addDependency(taskTwiceRun4)
        task6.addDependency(task5)
        task6.addDependency(taskTwiceRun4)

        val taskProcessor = TaskProcessor(task1)

        assertFalse { checkProcessorResult(taskProcessor.start(), 1000) }
        delay(100)
        assertTrue { checkProcessorResult(taskProcessor.start(), 1000) }


    }

    private suspend fun checkProcessorResult(flow: Flow<ProgressStatus>, maxTime: Long): Boolean {
        var res = false

        withTimeout(maxTime) {
            flow.collect() {
                when (it) {
                    is Check -> {
                        println("check : ${it.result}")
                        assertTrue { it.result }
                    }
                    is Progress -> println("task is done : ${it.task.getDescription()}")
                    is Complete -> {
                        println("processor done")
                        res = true
                        return@collect
                    }
                    is Failed -> {
                        println("task is failed : ${it.failedTask.getDescription()}")
                        return@collect
                    }
                }
            }
        }

        return res
    }
}

open class BlockTask(private val name: String) : Task() {
    override suspend fun doAction() {
        Thread.sleep(100)
        actionResult(true)
    }

    override fun getTaskDescription(): String {
        return name
    }
}

open class SuspendTask(private val name: String) : Task() {
    override suspend fun doAction() = withContext(Dispatchers.Default) {
        delay(100)
        actionResult(true)
    }

    override fun getTaskDescription(): String {
        return name
    }
}

class BlockFailedTask(name: String) : BlockTask(name) {
    override suspend fun doAction() {
        Thread.sleep(100)
        actionResult(false)
    }
}

class SuspendFailedTask(name: String) : SuspendTask(name) {
    override suspend fun doAction() {
        delay(100)
        actionResult(false)
    }
}

class SuspendTaskWithOneFailedSecondSuccess(name: String) : SuspendTask(name) {
    private var firstCalled = false
    override suspend fun doAction() {
        delay(100)
        if (firstCalled) {
            actionResult(true)
        } else {
            firstCalled = true
            actionResult(false)
        }
    }
}

class SuspendTaskNotDoActionAgain(name: String) : SuspendTask(name) {
    private var firstCalled = false
    override suspend fun doAction() {
        delay(100)
        if (firstCalled) {
            throw java.lang.RuntimeException("call the action again after action true")
        } else {
            firstCalled = true
            actionResult(true)
        }
    }
}


