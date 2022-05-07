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

interface ITaskLog {
    fun e(msg: String)
    fun d(msg: String)
}

internal lateinit var taskLog: ITaskLog

internal class TaskLog(iTaskLog: ITaskLog) : ITaskLog by iTaskLog

object DefaultTaskLog : ITaskLog {
    override fun e(msg: String) {
        println("error : $msg --> ${Thread.currentThread()}")
    }

    override fun d(msg: String) {
        println("debug : $msg --> ${Thread.currentThread()}")
    }
}

