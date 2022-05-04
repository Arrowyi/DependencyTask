package indi.arrowyi.dependencytask

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
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
        task3 = SuspendTaskC("Task 3")
        task4 = SuspendTaskC("Task 4")
        task5 = SuspendTaskC("Task 5")
        task6 = SuspendTaskC("Task 6")
    }


    @Test
    fun noCycleDependencyTest() = runBlocking {
        task1.addDependency(InitTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
        task4.addDependency(InitTask)
        task4.addDependency(task3)
        task5.addDependency(task1)
        task5.addDependency(task4)
        task6.addDependency(task3)

        val taskProcessor = TaskProcessor(InitTask)
        assertTrue  { taskProcessor.check(null) }
        assertEquals(7, taskProcessor.tasks.size)
    }

    @Test
    internal fun hasCycleDependencyTest() = runBlocking {
        task1.addDependency(InitTask)
        task2.addDependency(task1)
        task3.addDependency(task2)
        task4.addDependency(task3)
        task5.addDependency(task2)
        task5.addDependency(task3)
        task6.addDependency(task5)
        task2.addDependency(task6)


        val taskProcessor = TaskProcessor(InitTask)
        assertFalse{ taskProcessor.check(null) }
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

open class SuspendTaskC(private val name: String) : Task() {
    override suspend fun doAction() = withContext(Dispatchers.Default) {
        delay(100)
        actionResult(true)
    }

    override fun getTaskDescription(): String {
        return name
    }
}


