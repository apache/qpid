#ifndef __dispatch_h__
#define __dispatch_h__ 1
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

#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/buffer.h>
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/hash.h>
#include <qpid/dispatch/iovec.h>
#include <qpid/dispatch/iterator.h>
#include <qpid/dispatch/log.h>
#include <qpid/dispatch/router.h>
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/timer.h>
#include <qpid/dispatch/user_fd.h>
#include <qpid/dispatch/server.h>
#include <qpid/dispatch/message.h>
#include <qpid/dispatch/container.h>
#include <qpid/dispatch/agent.h>

/**
 * \defgroup General Dispatch Definitions
 * @{
 */

typedef struct dx_dispatch_t dx_dispatch_t;

/**
 * \brief Initialize the Dispatch library and prepare it for operation.
 *
 * @param thread_count The number of worker threads (1 or more) that the server shall create
 * @return A handle to be used in API calls for this instance.
 */
dx_dispatch_t *dx_dispatch(int thread_count);


/**
 * \brief Finalize the Dispatch library after it has stopped running.
 *
 * @param dispatch The dispatch handle returned by dx_dispatch
 */
void dx_dispatch_free(dx_dispatch_t *dispatch);


/**
 * @}
 */

#endif
