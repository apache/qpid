#ifndef __work_queue_h__
#define __work_queue_h__ 1
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

#include <proton/driver.h>

typedef struct work_queue_t work_queue_t;

work_queue_t *work_queue(void);
void work_queue_free(work_queue_t *w);
void work_queue_put(work_queue_t *w, pn_connector_t *conn);
pn_connector_t *work_queue_get(work_queue_t *w);
int work_queue_empty(work_queue_t *w);
int work_queue_depth(work_queue_t *w);

#endif
