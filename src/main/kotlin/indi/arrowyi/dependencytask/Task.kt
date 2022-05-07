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

import TaskException
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskStatusListener {
    fun onActionDone(task: Task)
    fun onSuccessorsDone(task: Task)
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

    abstract suspend fun doAction()

    //synchronize for check() to change the status to checked
    @Synchronized
    fun addDependency(task: Task) {
        takeIf { status.ordinal < Status.Checked.ordinal && !dependencies.contains(task) }
            ?.let {
                dependencies.add(task)
                task.addSuccessor(this@Task)
            }
    }

    fun actionResult(res: Boolean) = TaskScope.runOnTaskScope {
        status = if (res) Status.ActionSuccess else Status.ActionFailed
        notifyStatusChanged { listener, task ->
            listener.onActionDone(task)
        }
        onActionDone(res)
    }

    protected open fun onDependencyDone(task: Task) {
        dependencies.takeIf { it.contains(task) }
            ?.takeIf {
                it.all { task -> task.status == Status.ActionSuccess }
            }?.run {
                start()
            }

    }

    protected open fun onSuccessorResult(successor: Task, res: Boolean) {
        if (!res) {
            reportResult(false)
        } else {
            successors.takeIf { it.contains(successor) }
                ?.takeUnless { isAllSuccessorsDone }
                ?.takeIf {
                    it.all { successor -> successor.isAllSuccessorsDone }
                }
                ?.run {
                    isAllSuccessorsDone = true
                    notifyStatusChanged { listener, task ->
                        listener.onSuccessorsDone(task)
                    }
                    reportResult(true)
                }
        }
    }

    protected open fun getTaskDescription() = ""

    fun getDescription() = "${this::class.simpleName} : ${getTaskDescription()}"


    internal fun addResultListener(taskStatusListener: TaskStatusListener) {
        listeners.add(taskStatusListener)
    }

    internal fun removeResultListener(taskStatusListener: TaskStatusListener) {
        listeners.remove(taskStatusListener)
    }

    //synchronized for addDependency to check the status
    @Synchronized
    internal fun check() {
        return when (status) {
            Status.Init -> {
                status = Status.Checked
            }
            else -> {
                throw TaskException("check : task -> ${getDescription()} status is wrong : $status")
            }
        }
    }

    //caz it is the internal method, we can't make sure that it is running on the task scope, so
    //there is no need to put it on the task dispatcher
    internal fun start() {
        taskLog.d("${getDescription()} : start and status is $status")
        when (status) {
            Status.ActionSuccess -> {
                notifyStatusChanged { listener, task ->
                    listener.onActionDone(task)
                    task.takeIf { isAllSuccessorsDone }?.let { listener.onSuccessorsDone(task) }
                }
                if (isAllSuccessorsDone)
                    reportResult(true)
                else
                    onActionDone(true)
            }
            Status.Checked, Status.ActionFailed -> {
                status = Status.Running
                try {
                    TaskScope.launchOnActionDispatcher { doAction() }
                } catch (e: Exception) {
                    taskLog.e("task : ${getDescription()}, ${e.message}")
                    actionResult(false)
                }
            }
            Status.Running -> {
                taskLog.d("${getDescription()} is already running")
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
            return
        }

        if (res && successors.isEmpty()) {
            isAllSuccessorsDone = true
        }

        reportResult(res)
    }

    private fun notifyStatusChanged(block: (TaskStatusListener, Task) -> Unit) {
        listeners.forEach {
            taskLog.d("${this@Task.getTaskDescription()} notify status")
            TaskScope.launchOnNotifyDispatcher { block(it, this@Task) }
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