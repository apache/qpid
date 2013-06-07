/*
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

#include "python_embedded.h"
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/log.h>

static uint32_t     ref_count  = 0;
static sys_mutex_t *lock       = 0;
static char        *log_module = "PYTHON";

void dx_python_initialize()
{
    lock = sys_mutex();
}


void dx_python_finalize()
{
    assert(ref_count == 0);
    sys_mutex_free(lock);
}


void dx_python_start()
{
    sys_mutex_lock(lock);
    if (ref_count == 0) {
        Py_Initialize();
        dx_log(log_module, LOG_TRACE, "Embedded Python Interpreter Initialized");
    }
    ref_count++;
    sys_mutex_unlock(lock);
}


void dx_python_stop()
{
    sys_mutex_lock(lock);
    ref_count--;
    if (ref_count == 0) {
        Py_Finalize();
        dx_log(log_module, LOG_TRACE, "Embedded Python Interpreter Shut Down");
    }
    sys_mutex_unlock(lock);
}


