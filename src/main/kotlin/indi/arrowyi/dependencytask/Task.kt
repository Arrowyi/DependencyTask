package indi.arrowyi.dependencytask

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskStatusListener {
    fun onStatusChanged(task: Task)
}

enum class Status {
    Init,
    Checked,
    ActionSuccess,
    ActionFailed,
    Running

}

abstract class Task {
    private var successors = HashSet<Task>()
    private var dependencies = HashSet<Task>()
    private val listeners = ConcurrentLinkedDeque<TaskStatusListener>()

    @Volatile
    var status = Status.Init
        private set

    @Volatile
    var isAllSuccessorsDone: Boolean = false
        private set

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        internal val taskDispatcher by lazy { Dispatchers.Default.limitedParallelism(1) }
        internal val taskScope = CoroutineScope(taskDispatcher)

        internal fun runOnTaskScope(block: () -> Unit) {
            taskScope.launch {
                block()
            }
        }
    }

    abstract suspend fun doAction()

    suspend fun addDependency(task: Task) = withContext(taskDispatcher) {
        takeIf { status.ordinal < Status.Checked.ordinal }
            ?.run {
                task.addSuccessor(this@Task)
            }
    }

    fun addResultListener(taskStatusListener: TaskStatusListener) {
        listeners.add(taskStatusListener)
    }

    fun removeResultListener(taskStatusListener: TaskStatusListener) {
        listeners.remove(taskStatusListener)
    }

    fun actionResult(res: Boolean) = runOnTaskScope {
        status = if (res) Status.ActionSuccess else Status.ActionFailed
        notifyStatusChanged()
        onActionDone(res)
    }

    open fun onDependencyDone(task: Task) = runOnTaskScope {
        dependencies.takeIf { it.contains(task) }
            ?.takeIf {
                it.all { status == Status.ActionSuccess }
            }?.run {
                start()
            }

    }

    open fun onSuccessorResult(successor: Task, res: Boolean) = runOnTaskScope {
        if (!res) {
            reportResult(false)
        } else {
            successors.takeIf { it.contains(successor) }?.takeIf {
                it.all { isAllSuccessorsDone }
            }?.let {
                isAllSuccessorsDone = true
                notifyStatusChanged()
                reportResult(true)
            }
        }
    }

    open fun getTaskDescription() = ""

    internal fun getDescription() = "${this::class.simpleName} : ${getTaskDescription()}"

    internal fun check(): Boolean {
        return when (status) {
            Status.Init -> {
                status = Status.Checked
                true
            }
            Status.Checked -> {
                false
            }
            else -> {
                throw TaskException("check : task -> ${getDescription()} status is wrong : $status")
            }
        }
    }

    //caz it is the internal method, we can't make sure that it is running on the task scope, so
    //there is no need to put it on the task dispatcher
    internal fun start() {
        when (status) {
            Status.ActionSuccess -> {
                notifyStatusChanged()
                if (isAllSuccessorsDone)
                    reportResult(true)
                else
                    onActionDone(true)
            }
            Status.Checked, Status.ActionFailed -> {
                status = Status.Running
                try {
                    taskScope.launch(Dispatchers.Default) { doAction() }
                } catch (e: Exception) {
                    TaskLog.e("task : ${getDescription()}, ${e.message}")
                    actionResult(false)
                }
            }
            else -> {
                throw TaskException("start :  task -> ${getDescription()} start status wrong : $status")
            }
        }
    }


    internal fun getSuccessors(): List<Task> {
        return successors.toList()
    }

    private fun onActionDone(res: Boolean) {
        if (res && successors.isNotEmpty()) {
            startSuccessors()
        } else {
            reportResult(res)
        }
    }

    private fun notifyStatusChanged() {
        listeners.forEach {
            taskScope.launch(Dispatchers.Default) { it.onStatusChanged(this@Task) }
        }
    }

    private fun reportResult(res: Boolean) {
        dependencies.forEach {
            it.onSuccessorResult(this, res)
        }
    }

    private fun addSuccessor(task: Task) {
        successors.add(task)
    }

    private fun startSuccessors() {
        successors.forEach {
            it.onDependencyDone(this@Task)
        }
    }
}

object InitTask : Task() {
    override suspend fun doAction() {
        actionResult(true)
    }

}