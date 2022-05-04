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

class Check(val result: Boolean, val tasks: List<Task>?) : ProgressStatus()
class Progress(val task: Task) : ProgressStatus()
class Complete : ProgressStatus()
class Failed(val failedTask: Task) : ProgressStatus()

class TaskProcessor(private val firstTask: Task, iTaskLog: ITaskLog = DefaultTaskLog) {

    private var isChecked = false
    internal val tasks = HashSet<Task>()

    init {
        TaskLog(iTaskLog).also { taskLog = it }
    }

    fun start(): Flow<ProgressStatus> = callbackFlow {
        Task.runOnTaskScope {
            if (!isChecked) {
                isChecked = check(object : TaskStatusListener {
                    override fun onActionDone(task: Task) {
                        when (task.status) {
                            Status.ActionSuccess -> trySendBlocking(Progress(task))
                            else -> trySendBlocking(Failed(task))
                        }
                    }

                    override fun onSuccessorsDone(task: Task) {
                        if (task === firstTask) trySendBlocking(Complete())
                    }
                })

                tasks.forEach {
                    it.checked()
                }
            }

            if (!isChecked) {
                trySendBlocking(Check(false, null))
                return@runOnTaskScope
            }

            trySendBlocking(Check(true, tasks.toList()))

            firstTask.start()
        }

        awaitClose()
    }

    internal fun check(taskStatusListener: TaskStatusListener?): Boolean {
        val stack = mutableListOf<TravelNode>()
        val visitedTasks = HashSet<Task>()

        var endPoint = 0

        var curNode = TravelNode(firstTask, firstTask.getSuccessors(), 0)

        while (true) {
            if (visitedTasks.contains(curNode.task)) {
                taskLog.e("${curNode.task.getDescription()} has already been visited, maybe there is the circular dependency")
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

