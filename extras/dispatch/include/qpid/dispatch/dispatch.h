#ifndef __dispatch_dispatch_h__
#define __dispatch_dispatch_h__ 1
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

/**
 * \defgroup General Dispatch Definitions
 * @{
 */

typedef struct dx_dispatch_t dx_dispatch_t;

/**
 * \brief Initialize the Dispatch library and prepare it for operation.
 *
 * @param thread_count The number of worker threads (1 or more) that the server shall create
 * @param container_name The name of the container.  If NULL, a UUID will be generated.
 * @param router_area The name of the router's area.  If NULL, a default value will be supplied.
 * @param router_id The identifying name of the router.  If NULL, it will be set the same as the
 *        container_name.
 * @return A handle to be used in API calls for this instance.
 */
dx_dispatch_t *dx_dispatch(int thread_count, const char *container_name,
                           const char *router_area, const char *router_id);


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
