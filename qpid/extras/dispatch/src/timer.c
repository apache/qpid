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

#include "timer_private.h"
#include "server_private.h"
#include "dispatch_private.h"
#include <qpid/dispatch/ctools.h>
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/alloc.h>
#include <assert.h>
#include <stdio.h>

static sys_mutex_t     *lock;
static dx_timer_list_t  idle_timers;
static dx_timer_list_t  scheduled_timers;
static long             time_base;

ALLOC_DECLARE(dx_timer_t);
ALLOC_DEFINE(dx_timer_t);

//=========================================================================
// Private static functions
//=========================================================================

static void dx_timer_cancel_LH(dx_timer_t *timer)
{
    switch (timer->state) {
    case TIMER_FREE:
        assert(0);
        break;

    case TIMER_IDLE:
        break;

    case TIMER_SCHEDULED:
        if (timer->next)
            timer->next->delta_time += timer->delta_time;
        DEQ_REMOVE(scheduled_timers, timer);
        DEQ_INSERT_TAIL(idle_timers, timer);
        break;

    case TIMER_PENDING:
        dx_server_timer_cancel_LH(timer);
        break;
    }

    timer->state = TIMER_IDLE;
}


//=========================================================================
// Public Functions from timer.h
//=========================================================================

dx_timer_t *dx_timer(dx_dispatch_t *dx, dx_timer_cb_t cb, void* context)
{
    dx_timer_t *timer = new_dx_timer_t();
    if (!timer)
        return 0;

    DEQ_ITEM_INIT(timer);

    timer->server     = dx ? dx->server : 0;
    timer->handler    = cb;
    timer->context    = context;
    timer->delta_time = 0;
    timer->state      = TIMER_IDLE;

    sys_mutex_lock(lock);
    DEQ_INSERT_TAIL(idle_timers, timer);
    sys_mutex_unlock(lock);

    return timer;
}


void dx_timer_free(dx_timer_t *timer)
{
    sys_mutex_lock(lock);
    dx_timer_cancel_LH(timer);
    DEQ_REMOVE(idle_timers, timer);
    sys_mutex_unlock(lock);

    timer->state = TIMER_FREE;
    free_dx_timer_t(timer);
}


void dx_timer_schedule(dx_timer_t *timer, long duration)
{
    dx_timer_t *ptr;
    dx_timer_t *last;
    long        total_time;

    sys_mutex_lock(lock);
    dx_timer_cancel_LH(timer);  // Timer is now on the idle list
    assert(timer->state == TIMER_IDLE);
    DEQ_REMOVE(idle_timers, timer);

    //
    // Handle the special case of a zero-time scheduling.  In this case,
    // the timer doesn't go on the scheduled list.  It goes straight to the
    // pending list in the server.
    //
    if (duration == 0) {
        timer->state = TIMER_PENDING;
        dx_server_timer_pending_LH(timer);
        sys_mutex_unlock(lock);
        return;
    }

    //
    // Find the insert point in the schedule.
    //
    total_time = 0;
    ptr        = DEQ_HEAD(scheduled_timers);
    assert(!ptr || ptr->prev == 0);
    while (ptr) {
        total_time += ptr->delta_time;
        if (total_time > duration)
            break;
        ptr = ptr->next;
    }

    //
    // Insert the timer into the schedule and adjust the delta time
    // of the following timer if present.
    //
    if (total_time <= duration) {
        assert(ptr == 0);
        timer->delta_time = duration - total_time;
        DEQ_INSERT_TAIL(scheduled_timers, timer);
    } else {
        total_time -= ptr->delta_time;
        timer->delta_time = duration - total_time;
        assert(ptr->delta_time > timer->delta_time);
        ptr->delta_time -= timer->delta_time;
        last = ptr->prev;
        if (last)
            DEQ_INSERT_AFTER(scheduled_timers, timer, last);
        else
            DEQ_INSERT_HEAD(scheduled_timers, timer);
    }

    timer->state = TIMER_SCHEDULED;

    sys_mutex_unlock(lock);
}


void dx_timer_cancel(dx_timer_t *timer)
{
    sys_mutex_lock(lock);
    dx_timer_cancel_LH(timer);
    sys_mutex_unlock(lock);
}


//=========================================================================
// Private Functions from timer_private.h
//=========================================================================

void dx_timer_initialize(sys_mutex_t *server_lock)
{
    lock = server_lock;
    DEQ_INIT(idle_timers);
    DEQ_INIT(scheduled_timers);
    time_base = 0;
}


void dx_timer_finalize(void)
{
    lock = 0;
}


long dx_timer_next_duration_LH(void)
{
    dx_timer_t *timer = DEQ_HEAD(scheduled_timers);
    if (timer)
        return timer->delta_time;
    return -1;
}


void dx_timer_visit_LH(long current_time)
{
    long        delta;
    dx_timer_t *timer = DEQ_HEAD(scheduled_timers);

    if (time_base == 0) {
        time_base = current_time;
        return;
    }

    delta     = current_time - time_base;
    time_base = current_time;

    while (timer) {
        assert(delta >= 0);
        if (timer->delta_time > delta) {
            timer->delta_time -= delta;
            break;
        } else {
            DEQ_REMOVE_HEAD(scheduled_timers);
            delta -= timer->delta_time;
            timer->state = TIMER_PENDING;
            dx_server_timer_pending_LH(timer);

        }
        timer = DEQ_HEAD(scheduled_timers);
    }
}


void dx_timer_idle_LH(dx_timer_t *timer)
{
    timer->state = TIMER_IDLE;
    DEQ_INSERT_TAIL(idle_timers, timer);
}

