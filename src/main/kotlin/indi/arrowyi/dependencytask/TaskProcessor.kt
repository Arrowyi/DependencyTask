package indi.arrowyi.dependencytask

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private data class TravelNode(
    val task: Task,
    val successors: List<Task>,
    var visitSuccessorIndex: Int
)


sealed class ProgressStatus()

class Check(val result: Boolean) : ProgressStatus()
class Progress(val task : Task) : ProgressStatus()
class Complete : ProgressStatus()
class Failed : ProgressStatus()


class TaskProcessor(private val firstTask: Task) {

    private var isChecked = false
    private val tasks = HashSet<Task>()

    fun start(): Flow<ProgressStatus> = callbackFlow {

        Task.runOnTaskScope {
            if (!isChecked) {
                isChecked = check(object : TaskStatusListener {
                    override fun onStatusChanged(task: Task) {
                        trySendBlocking(Progress(task))
                    }
                })
            }

            if (!isChecked) {
                trySendBlocking(Check(false))
                return@runOnTaskScope
            }

            trySendBlocking(Check(true))

            tasks.forEach {
                it.check()
            }

            firstTask.start()
        }

        awaitClose()
    }

    fun getTaskList(): List<Task> {
        return tasks.toList()
    }

    internal fun check(taskStatusListener: TaskStatusListener?): Boolean {
        val stack = mutableListOf<TravelNode>()
        val visitedTasks = HashSet<Task>()

        var endPoint = 0

        var curNode = TravelNode(firstTask, firstTask.getSuccessors(), 0)

        while (true) {
            if (visitedTasks.contains(curNode.task)) {
                TaskLog.e("${curNode.task.getDescription()} has already been visited, maybe there is the circular dependency")
                return false
            }

            if (!tasks.contains(curNode.task)) {
                tasks.add(curNode.task)
                taskStatusListener?.let {
                    curNode.task.addResultListener(taskStatusListener)
                }
            }

            //the leaf node
            if (curNode.successors.isEmpty()) {
                endPoint++
                if (stack.isEmpty()) {
                    break
                }

                val node = stack.removeLast()
                visitedTasks.remove(node.task)
                node.visitSuccessorIndex++
                curNode = node
                continue

            }

            //all the successors have been visited
            if (curNode.successors.size < curNode.visitSuccessorIndex + 1) {

                if (stack.isEmpty()) {
                    break
                }

                val node = stack.removeLast()
                visitedTasks.remove(node.task)
                node.visitSuccessorIndex++
                curNode = node
                continue
            }

            stack.add(curNode)
            visitedTasks.add(curNode.task)

            val curTask = curNode.successors[curNode.visitSuccessorIndex]
            curNode = TravelNode(curTask, curTask.getSuccessors(), 0)

        }

        return true
    }

}

