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
#include <qpid/nexus/ctools.h>
#include <qpid/nexus/threading.h>
#include <assert.h>
#include <stdio.h>

static sys_mutex_t     *lock;
static nx_timer_list_t  free_list;
static nx_timer_list_t  idle_timers;
static nx_timer_list_t  scheduled_timers;
static long             time_base;


//=========================================================================
// Private static functions
//=========================================================================

static void nx_timer_cancel_LH(nx_timer_t *timer)
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
        nx_server_timer_cancel_LH(timer);
        break;
    }

    timer->state = TIMER_IDLE;
}


//=========================================================================
// Public Functions from timer.h
//=========================================================================

nx_timer_t *nx_timer(nx_timer_cb_t cb, void* context)
{
    nx_timer_t *timer;

    sys_mutex_lock(lock);

    timer = DEQ_HEAD(free_list);
    if (timer) {
        DEQ_REMOVE_HEAD(free_list);
    } else {
        timer = NEW(nx_timer_t);
        DEQ_ITEM_INIT(timer);
    }

    if (timer) {
        timer->handler    = cb;
        timer->context    = context;
        timer->delta_time = 0;
        timer->state      = TIMER_IDLE;
        DEQ_INSERT_TAIL(idle_timers, timer);
    }

    sys_mutex_unlock(lock);
    return timer;
}


void nx_timer_free(nx_timer_t *timer)
{
    sys_mutex_lock(lock);
    nx_timer_cancel_LH(timer);
    DEQ_REMOVE(idle_timers, timer);
    DEQ_INSERT_TAIL(free_list, timer);
    timer->state = TIMER_FREE;
    sys_mutex_unlock(lock);
}


void nx_timer_schedule(nx_timer_t *timer, long duration)
{
    nx_timer_t *ptr;
    nx_timer_t *last;
    long        total_time;

    sys_mutex_lock(lock);
    nx_timer_cancel_LH(timer);  // Timer is now on the idle list
    assert(timer->state == TIMER_IDLE);
    DEQ_REMOVE(idle_timers, timer);

    //
    // Handle the special case of a zero-time scheduling.  In this case,
    // the timer doesn't go on the scheduled list.  It goes straight to the
    // pending list in the server.
    //
    if (duration == 0) {
        timer->state = TIMER_PENDING;
        nx_server_timer_pending_LH(timer);
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


void nx_timer_cancel(nx_timer_t *timer)
{
    sys_mutex_lock(lock);
    nx_timer_cancel_LH(timer);
    sys_mutex_unlock(lock);
}


//=========================================================================
// Private Functions from timer_private.h
//=========================================================================

void nx_timer_initialize(sys_mutex_t *server_lock)
{
    lock = server_lock;
    DEQ_INIT(free_list);
    DEQ_INIT(idle_timers);
    DEQ_INIT(scheduled_timers);
    time_base = 0;
}


void nx_timer_finalize(void)
{
    lock = 0;
}


long nx_timer_next_duration_LH(void)
{
    nx_timer_t *timer = DEQ_HEAD(scheduled_timers);
    if (timer)
        return timer->delta_time;
    return -1;
}


void nx_timer_visit_LH(long current_time)
{
    long        delta;
    nx_timer_t *timer = DEQ_HEAD(scheduled_timers);

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
            nx_server_timer_pending_LH(timer);

        }
        timer = DEQ_HEAD(scheduled_timers);
    }
}


void nx_timer_idle_LH(nx_timer_t *timer)
{
    timer->state = TIMER_IDLE;
    DEQ_INSERT_TAIL(idle_timers, timer);
}

