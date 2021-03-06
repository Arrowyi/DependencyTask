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

import kotlinx.coroutines.*
import java.util.concurrent.CancellationException

internal class TaskScope(
    private val actionDispatcher: CoroutineDispatcher,
    private val notifyDispatcher: CoroutineDispatcher
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val taskDispatcher by lazy { Dispatchers.Default.limitedParallelism(1) }
    private var taskScope: CoroutineScope? = null

    fun runOnTaskScope(block: () -> Unit) {
        getScope().launch {
            block()
        }
    }

    fun launchOnActionDispatcher(block: suspend CoroutineScope.() -> Unit) {
        getScope().launch(actionDispatcher) {
            block()
        }
    }

    fun launchOnNotifyDispatcher(block: () -> Unit) {
        getScope().launch(notifyDispatcher) {
            block()
        }
    }


    @Synchronized
    fun close(closeBlock: () -> Unit) {
        if (taskScope !== null) {
            taskScope!!.takeIf { it.isActive }?.cancel(CancellationException("Task Scope closed"))
            closeBlock()
        }
        taskScope = null
    }

    @Synchronized
    private fun getScope(): CoroutineScope {
        if (taskScope === null) {
            taskScope = CoroutineScope(taskDispatcher)
        }

        return taskScope as CoroutineScope
    }


}