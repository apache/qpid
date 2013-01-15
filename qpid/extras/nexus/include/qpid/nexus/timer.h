#ifndef __nexus_timer_h__
#define __nexus_timer_h__ 1
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
 * \defgroup Timer Server Timer Functions
 * @{
 */

typedef struct nx_timer_t nx_timer_t;

/**
 * Timer Callback
 *
 * Callback invoked after a timer's interval expires and the timer fires.
 *
 * @param context The context supplied in nx_timer
 */
typedef void (*nx_timer_cb_t)(void* context);


/**
 * Create a new timer object.
 *
 * @param cb The callback function to be invoked when the timer expires.
 * @param context An opaque, user-supplied context to be passed into the callback.
 * @return A pointer to the new timer object or NULL if memory is exhausted.
 */
nx_timer_t *nx_timer(nx_timer_cb_t cb, void* context);


/**
 * Free the resources for a timer object.  If the timer was scheduled, it will be canceled 
 * prior to freeing.  After this function returns, the callback will not be invoked for this
 * timer.
 *
 * @param timer Pointer to the timer object returned by nx_timer.
 */
void nx_timer_free(nx_timer_t *timer);


/**
 * Schedule a timer to fire in the future.
 *
 * Note that the timer callback will never be invoked synchronously during the execution
 * of nx_timer_schedule.  Even if the interval is immediate (0), the callback invocation will
 * be asynchronous and after the return of this function.
 *
 * @param timer Pointer to the timer object returned by nx_timer.
 * @param msec The minimum number of milliseconds of delay until the timer fires.
 *             If 0 is supplied, the timer will fire immediately.
 */
void nx_timer_schedule(nx_timer_t *timer, long msec);


/**
 * Attempt to cancel a scheduled timer.  Since the timer callback can be invoked on any
 * server thread, it is always possible that a last-second cancel attempt may arrive too late
 * to stop the timer from firing (i.e. the cancel is concurrent with the fire callback).
 *
 * @param timer Pointer to the timer object returned by nx_timer.
 */
void nx_timer_cancel(nx_timer_t *timer);

/**
 * @}
 */

#endif
