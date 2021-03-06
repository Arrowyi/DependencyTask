/*
 * Copyright (c) 2022—2022.  Arrowyi. All rights reserved.
 * email : arrowyi@gmail.com
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package indi.arrowyi.dependencytask

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private data class TravelNode(
    val task: Task,
    val successors: List<Task>,
    var visitSuccessorIndex: Int
)


sealed class ProgressStatus

//check if there is circular dependency , true is for check ok (no circular dependency), false will end the processor, and the tasks is all
//the tasks this processor will do.
class Check(val result: Boolean, val tasks: List<Task>?) : ProgressStatus()

//called if the task is done
class Progress(val task: Task) : ProgressStatus()

//called if all the tasks is done successfully, and the processor will be ended
class Complete : ProgressStatus()

//called if any of the task failed, and the processor will be ended immediately
class Failed(val failedTask: Task) : ProgressStatus()

//The Processor is already running, and this flow will be ended
class AlreadyRunning() : ProgressStatus()

class TaskProcessor(
    private val firstTask: Task,
    private val iTaskLog: ITaskLog = DefaultTaskLog,
    actionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    notifyDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private var isChecked = false
    internal val tasks = HashSet<Task>()

    @Volatile
    private var isRunning = false

    private val taskScope = TaskScope(actionDispatcher, notifyDispatcher)

    fun start(): Flow<ProgressStatus> = callbackFlow {
        val listener = object : TaskStatusListener {
            override fun onActionDone(task: Task) {
                when (task.status) {
                    Status.ActionSuccess -> trySendBlocking(Progress(task))
                    else -> {
                        iTaskLog.d("send failed")
                        trySendBlocking(Failed(task))
                        channel.close()
                    }
                }
            }

            override fun onSuccessorsDone(task: Task) {
                iTaskLog.d("onSuccessorsDone : ${task.getDescription()}")
                if (task === firstTask) {
                    iTaskLog.d("send complete")
                    trySendBlocking(Complete())
                    channel.close()
                }
            }
        }

        var duplication = false

        taskScope.runOnTaskScope {
            iTaskLog.d(
                "processor begin with running status " +
                        "is $isRunning with ${this@TaskProcessor} \n and producer scope is $this"
            )
            if (isRunning) {
                trySendBlocking(AlreadyRunning())
                duplication = true
                channel.close()
                return@runOnTaskScope
            } else {
                isRunning = true
            }

            if (!isChecked) {
                isChecked = check()
            }

            if (!isChecked) {
                iTaskLog.d("send check failed")
                trySendBlocking(Check(false, null))
                channel.close()
                return@runOnTaskScope
            }

            registerStatusListenerForTasks(listener)
            setLogAndScopeForTasks()
            trySendBlocking(Check(true, tasks.toList()))

            firstTask.start()
        }

        awaitClose {
            takeUnless { duplication }?.run {
                taskScope.close {
                    iTaskLog.d("call close with ${this@TaskProcessor} \n and producer scope is $this")
                    unregisterStatusListenerForTasks(listener)
                    isRunning = false
                }
            }
        }
    }

    private fun registerStatusListenerForTasks(taskStatusListener: TaskStatusListener) {
        tasks.forEach { it.addResultListener(taskStatusListener) }
    }

    private fun unregisterStatusListenerForTasks(taskStatusListener: TaskStatusListener) {
        tasks.forEach { it.removeResultListener(taskStatusListener) }
    }

    private fun setLogAndScopeForTasks() {
        tasks.forEach { it.setLogAndScope(iTaskLog, taskScope) }
    }

    //check if there is a cycle dependency
    internal fun check(): Boolean {
        val stack = mutableListOf<TravelNode>()
        val visitedTasks = HashSet<Task>()

        var endPoint = 0

        var curNode = TravelNode(firstTask, firstTask.getSuccessors(), 0)

        while (true) {
            if (visitedTasks.contains(curNode.task)) {
                iTaskLog.e("${curNode.task.getDescription()} has already been visited, maybe there is the circular dependency")
                return false
            }

            if (!tasks.contains(curNode.task)) {
                tasks.add(curNode.task)
                curNode.task.check()
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

