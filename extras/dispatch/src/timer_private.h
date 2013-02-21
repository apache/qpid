#ifndef __timer_private_h__
#define __timer_private_h__ 1
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

#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/timer.h>
#include <qpid/dispatch/threading.h>

typedef enum {
    TIMER_FREE,
    TIMER_IDLE,
    TIMER_SCHEDULED,
    TIMER_PENDING
} dx_timer_state_t;


struct dx_timer_t {
    DEQ_LINKS(dx_timer_t);
    dx_timer_cb_t     handler;
    void             *context;
    long              delta_time;
    dx_timer_state_t  state;
};

DEQ_DECLARE(dx_timer_t, dx_timer_list_t);

void dx_timer_initialize(sys_mutex_t *server_lock);
void dx_timer_finalize(void);
long dx_timer_next_duration_LH(void);
void dx_timer_visit_LH(long current_time);
void dx_timer_idle_LH(dx_timer_t *timer);


#endif
