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

#include <qpid/nexus/ctools.h>
#include <qpid/nexus/threading.h>
#include <qpid/nexus/log.h>
#include "server_private.h"
#include "timer_private.h"
#include "alloc_private.h"
#include "auth.h"
#include "work_queue.h"
#include <stdio.h>
#include <time.h>
#include <signal.h>

static char *module="SERVER";

typedef struct nx_thread_t {
    int           thread_id;
    volatile int  running;
    volatile int  canceled;
    int           using_thread;
    sys_thread_t *thread;
} nx_thread_t;


typedef struct nx_server_t {
    int                      thread_count;
    pn_driver_t             *driver;
    nx_thread_start_cb_t     start_handler;
    nx_conn_handler_cb_t     conn_handler;
    nx_signal_handler_cb_t   signal_handler;
    nx_user_fd_handler_cb_t  ufd_handler;
    void                    *start_context;
    void                    *conn_context;
    void                    *signal_context;
    sys_cond_t              *cond;
    sys_mutex_t             *lock;
    nx_thread_t            **threads;
    work_queue_t            *work_queue;
    nx_timer_list_t          pending_timers;
    bool                     a_thread_is_waiting;
    int                      threads_active;
    int                      pause_requests;
    int                      threads_paused;
    int                      pause_next_sequence;
    int                      pause_now_serving;
    int                      pending_signal;
} nx_server_t;


ALLOC_DEFINE(nx_listener_t);
ALLOC_DEFINE(nx_connector_t);
ALLOC_DEFINE(nx_connection_t);
ALLOC_DEFINE(nx_user_fd_t);


/**
 * Singleton Concurrent Proton Driver object
 */
static nx_server_t *nx_server = 0;


static void signal_handler(int signum)
{
    nx_server->pending_signal = signum;
    sys_cond_signal_all(nx_server->cond);
}


static nx_thread_t *thread(int id)
{
    nx_thread_t *thread = NEW(nx_thread_t);
    if (!thread)
        return 0;

    thread->thread_id    = id;
    thread->running      = 0;
    thread->canceled     = 0;
    thread->using_thread = 0;

    return thread;
}


static void thread_process_listeners(pn_driver_t *driver)
{
    pn_listener_t   *listener = pn_driver_listener(driver);
    pn_connector_t  *cxtr;
    nx_connection_t *ctx;

    while (listener) {
        nx_log(module, LOG_TRACE, "Accepting Connection");
        cxtr = pn_listener_accept(listener);
        ctx = new_nx_connection_t();
        ctx->state        = CONN_STATE_SASL_SERVER;
        ctx->owner_thread = CONTEXT_NO_OWNER;
        ctx->enqueued     = 0;
        ctx->pn_cxtr      = cxtr;
        ctx->pn_conn      = 0;
        ctx->listener     = (nx_listener_t*) pn_listener_context(listener);
        ctx->connector    = 0;
        ctx->context      = ctx->listener->context;
        ctx->ufd          = 0;

        pn_connector_set_context(cxtr, ctx);
        listener = pn_driver_listener(driver);
    }
}


static void handle_signals_LH(void)
{
    int signum = nx_server->pending_signal;

    if (signum) {
        nx_server->pending_signal = 0;
        if (nx_server->signal_handler) {
            sys_mutex_unlock(nx_server->lock);
            nx_server->signal_handler(nx_server->signal_context, signum);
            sys_mutex_lock(nx_server->lock);
        }
    }
}


static void block_if_paused_LH(void)
{
    if (nx_server->pause_requests > 0) {
        nx_server->threads_paused++;
        sys_cond_signal_all(nx_server->cond);
        while (nx_server->pause_requests > 0)
            sys_cond_wait(nx_server->cond, nx_server->lock);
        nx_server->threads_paused--;
    }
}


static void process_connector(pn_connector_t *cxtr)
{
    nx_connection_t *ctx = pn_connector_context(cxtr);
    int events      = 0;
    int auth_passes = 0;

    if (ctx->state == CONN_STATE_USER) {
        nx_server->ufd_handler(ctx->ufd->context, ctx->ufd);
        return;
    }

    do {
        //
        // Step the engine for pre-handler processing
        //
        pn_connector_process(cxtr);

        //
        // Call the handler that is appropriate for the connector's state.
        //
        switch (ctx->state) {
        case CONN_STATE_CONNECTING:
            if (!pn_connector_closed(cxtr)) {
                ctx->state = CONN_STATE_SASL_CLIENT;
                assert(ctx->connector);
                ctx->connector->state = CXTR_STATE_OPEN;
                events = 1;
            } else {
                ctx->state = CONN_STATE_FAILED;
                events = 0;
            }
            break;

        case CONN_STATE_SASL_CLIENT:
            if (auth_passes == 0) {
                auth_client_handler(cxtr);
                events = 1;
            } else {
                auth_passes++;
                events = 0;
            }
            break;

        case CONN_STATE_SASL_SERVER:
            if (auth_passes == 0) {
                auth_server_handler(cxtr);
                events = 1;
            } else {
                auth_passes++;
                events = 0;
            }
            break;

        case CONN_STATE_OPENING:
            ctx->state = CONN_STATE_OPERATIONAL;

            pn_connection_t *conn = pn_connection();
            pn_connection_set_container(conn, "nexus"); // TODO - make unique
            pn_connector_set_connection(cxtr, conn);
            pn_connection_set_context(conn, ctx);
            ctx->pn_conn = conn;

            nx_conn_event_t ce = NX_CONN_EVENT_PROCESS; // Initialize to keep the compiler happy

            if (ctx->listener) {
                ce = NX_CONN_EVENT_LISTENER_OPEN;
            } else if (ctx->connector) {
                ce = NX_CONN_EVENT_CONNECTOR_OPEN;
                ctx->connector->delay = 0;
            } else
                assert(0);

            nx_server->conn_handler(ctx->context, ce, (nx_connection_t*) pn_connector_context(cxtr));
            events = 1;
            break;

        case CONN_STATE_OPERATIONAL:
            if (pn_connector_closed(cxtr)) {
                nx_server->conn_handler(ctx->context,
                                        NX_CONN_EVENT_CLOSE,
                                        (nx_connection_t*) pn_connector_context(cxtr));
                events = 0;
            }
            else
                events = nx_server->conn_handler(ctx->context,
                                                 NX_CONN_EVENT_PROCESS,
                                                 (nx_connection_t*) pn_connector_context(cxtr));
            break;

        default:
            break;
        }
    } while (events > 0);
}


//
// TEMPORARY FUNCTION PROTOTYPES
//
void pn_driver_wait_1(pn_driver_t *d);
int  pn_driver_wait_2(pn_driver_t *d, int timeout);
void pn_driver_wait_3(pn_driver_t *d);
//
// END TEMPORARY
//

static void *thread_run(void *arg)
{
    nx_thread_t     *thread = (nx_thread_t*) arg;
    pn_connector_t  *work;
    pn_connection_t *conn;
    nx_connection_t *ctx;
    int              error;
    int              poll_result;
    int              timer_holdoff = 0;

    if (!thread)
        return 0;

    thread->running = 1;

    if (thread->canceled)
        return 0;

    //
    // Invoke the start handler if the application supplied one.
    // This handler can be used to set NUMA or processor affinnity for the thread.
    //
    if (nx_server->start_handler)
        nx_server->start_handler(nx_server->start_context, thread->thread_id);

    //
    // Main Loop
    //
    while (thread->running) {
        sys_mutex_lock(nx_server->lock);

        //
        // Check for pending signals to process
        //
        handle_signals_LH();
        if (!thread->running) {
            sys_mutex_unlock(nx_server->lock);
            break;
        }

        //
        // Check to see if the server is pausing.  If so, block here.
        //
        block_if_paused_LH();
        if (!thread->running) {
            sys_mutex_unlock(nx_server->lock);
            break;
        }

        //
        // Service pending timers.
        //
        nx_timer_t *timer = DEQ_HEAD(nx_server->pending_timers);
        if (timer) {
            DEQ_REMOVE_HEAD(nx_server->pending_timers);

            //
            // Mark the timer as idle in case it reschedules itself.
            //
            nx_timer_idle_LH(timer);

            //
            // Release the lock and invoke the connection handler.
            //
            sys_mutex_unlock(nx_server->lock);
            timer->handler(timer->context);
            pn_driver_wakeup(nx_server->driver);
            continue;
        }

        //
        // Check the work queue for connectors scheduled for processing.
        //
        work = work_queue_get(nx_server->work_queue);
        if (!work) {
            //
            // There is no pending work to do
            //
            if (nx_server->a_thread_is_waiting) {
                //
                // Another thread is waiting on the proton driver, this thread must
                // wait on the condition variable until signaled.
                //
                sys_cond_wait(nx_server->cond, nx_server->lock);
            } else {
                //
                // This thread elects itself to wait on the proton driver.  Set the
                // thread-is-waiting flag so other idle threads will not interfere.
                //
                nx_server->a_thread_is_waiting = true;

                //
                // Ask the timer module when its next timer is scheduled to fire.  We'll
                // use this value in driver_wait as the timeout.  If there are no scheduled
                // timers, the returned value will be -1.
                //
                long duration = nx_timer_next_duration_LH();

                //
                // Invoke the proton driver's wait sequence.  This is a bit of a hack for now
                // and will be improved in the future.  The wait process is divided into three parts,
                // the first and third of which need to be non-reentrant, and the second of which
                // must be reentrant (and blocks).
                //
                pn_driver_wait_1(nx_server->driver);
                sys_mutex_unlock(nx_server->lock);

                do {
                    error = 0;
                    poll_result = pn_driver_wait_2(nx_server->driver, duration);
                    if (poll_result == -1)
                        error = pn_driver_errno(nx_server->driver);
                } while (error == PN_INTR);
                if (error) {
                    nx_log(module, LOG_ERROR, "Driver Error: %s", pn_error_text(pn_error(nx_server->driver)));
                    exit(-1);
                }

                sys_mutex_lock(nx_server->lock);
                pn_driver_wait_3(nx_server->driver);

                if (!thread->running) {
                    sys_mutex_unlock(nx_server->lock);
                    break;
                }

                //
                // Visit the timer module.
                //
                if (poll_result == 0 || ++timer_holdoff == 100) {
                    struct timespec tv;
                    clock_gettime(CLOCK_REALTIME, &tv);
                    long milliseconds = tv.tv_sec * 1000 + tv.tv_nsec / 1000000;
                    nx_timer_visit_LH(milliseconds);
                    timer_holdoff = 0;
                }

                //
                // Process listeners (incoming connections).
                //
                thread_process_listeners(nx_server->driver);

                //
                // Traverse the list of connectors-needing-service from the proton driver.
                // If the connector is not already in the work queue and it is not currently
                // being processed by another thread, put it in the work queue and signal the
                // condition variable.
                //
                work = pn_driver_connector(nx_server->driver);
                while (work) {
                    ctx = pn_connector_context(work);
                    if (!ctx->enqueued && ctx->owner_thread == CONTEXT_NO_OWNER) {
                        ctx->enqueued = 1;
                        work_queue_put(nx_server->work_queue, work);
                        sys_cond_signal(nx_server->cond);
                    }
                    work = pn_driver_connector(nx_server->driver);
                }

                //
                // Release our exclusive claim on pn_driver_wait.
                //
                nx_server->a_thread_is_waiting = false;
            }
        }

        //
        // If we were given a connector to work on from the work queue, mark it as
        // owned by this thread and as no longer enqueued.
        //
        if (work) {
            ctx = pn_connector_context(work);
            if (ctx->owner_thread == CONTEXT_NO_OWNER) {
                ctx->owner_thread = thread->thread_id;
                ctx->enqueued = 0;
                nx_server->threads_active++;
            } else {
                //
                // This connector is being processed by another thread, re-queue it.
                //
                work_queue_put(nx_server->work_queue, work);
                work = 0;
            }
        }
        sys_mutex_unlock(nx_server->lock);

        //
        // Process the connector that we now have exclusive access to.
        //
        if (work) {
            process_connector(work);

            //
            // Check to see if the connector was closed during processing
            //
            if (pn_connector_closed(work)) {
                //
                // Connector is closed.  Free the context and the connector.
                //
                conn = pn_connector_connection(work);
                if (ctx->connector) {
                    ctx->connector->ctx = 0;
                    ctx->connector->state = CXTR_STATE_CONNECTING;
                    nx_timer_schedule(ctx->connector->timer, ctx->connector->delay);
                }
                sys_mutex_lock(nx_server->lock);
                free_nx_connection_t(ctx);
                pn_connector_free(work);
                if (conn)
                    pn_connection_free(conn);
                nx_server->threads_active--;
                sys_mutex_unlock(nx_server->lock);
            } else {
                //
                // The connector lives on.  Mark it as no longer owned by this thread.
                //
                sys_mutex_lock(nx_server->lock);
                ctx->owner_thread = CONTEXT_NO_OWNER;
                nx_server->threads_active--;
                sys_mutex_unlock(nx_server->lock);
            }

            //
            // Wake up the proton driver to force it to reconsider its set of FDs
            // in light of the processing that just occurred.
            //
            pn_driver_wakeup(nx_server->driver);
        }
    }

    return 0;
}


static void thread_start(nx_thread_t *thread)
{
    if (!thread)
        return;

    thread->using_thread = 1;
    thread->thread = sys_thread(thread_run, (void*) thread);
}


static void thread_cancel(nx_thread_t *thread)
{
    if (!thread)
        return;

    thread->running  = 0;
    thread->canceled = 1;
}


static void thread_join(nx_thread_t *thread)
{
    if (!thread)
        return;

    if (thread->using_thread)
        sys_thread_join(thread->thread);
}


static void thread_free(nx_thread_t *thread)
{
    if (!thread)
        return;

    free(thread);
}


static void cxtr_try_open(void *context)
{
    nx_connector_t *ct = (nx_connector_t*) context;
    if (ct->state != CXTR_STATE_CONNECTING)
        return;

    nx_connection_t *ctx = new_nx_connection_t();
    ctx->state        = CONN_STATE_CONNECTING;
    ctx->owner_thread = CONTEXT_NO_OWNER;
    ctx->enqueued     = 0;
    ctx->pn_conn      = 0;
    ctx->listener     = 0;
    ctx->connector    = ct;
    ctx->context      = ct->context;
    ctx->user_context = 0;
    ctx->ufd          = 0;

    //
    // pn_connector is not thread safe
    //
    sys_mutex_lock(nx_server->lock);
    ctx->pn_cxtr = pn_connector(nx_server->driver, ct->config->host, ct->config->port, (void*) ctx);
    sys_mutex_unlock(nx_server->lock);

    ct->ctx   = ctx;
    ct->delay = 5000;
    nx_log(module, LOG_TRACE, "Connecting to %s:%s", ct->config->host, ct->config->port);
}


void nx_server_initialize(int thread_count)
{
    int i;

    if (nx_server)
        return;     // TODO - Fail in a more dramatic way

    nx_alloc_initialize();
    nx_server = NEW(nx_server_t);

    if (!nx_server)
        return;   // TODO - Fail in a more dramatic way

    nx_server->thread_count    = thread_count;
    nx_server->driver          = pn_driver();
    nx_server->start_handler   = 0;
    nx_server->conn_handler    = 0;
    nx_server->signal_handler  = 0;
    nx_server->ufd_handler     = 0;
    nx_server->start_context   = 0;
    nx_server->signal_context  = 0;
    nx_server->lock            = sys_mutex();
    nx_server->cond            = sys_cond();

    nx_timer_initialize(nx_server->lock);

    nx_server->threads = NEW_PTR_ARRAY(nx_thread_t, thread_count);
    for (i = 0; i < thread_count; i++)
        nx_server->threads[i] = thread(i);

    nx_server->work_queue          = work_queue();
    DEQ_INIT(nx_server->pending_timers);
    nx_server->a_thread_is_waiting = false;
    nx_server->threads_active      = 0;
    nx_server->pause_requests      = 0;
    nx_server->threads_paused      = 0;
    nx_server->pause_next_sequence = 0;
    nx_server->pause_now_serving   = 0;
    nx_server->pending_signal      = 0;
}


void nx_server_finalize(void)
{
    int i;
    if (!nx_server)
        return;

    for (i = 0; i < nx_server->thread_count; i++)
        thread_free(nx_server->threads[i]);

    work_queue_free(nx_server->work_queue);

    pn_driver_free(nx_server->driver);
    sys_mutex_free(nx_server->lock);
    sys_cond_free(nx_server->cond);
    free(nx_server);
    nx_server = 0;
}


void nx_server_set_conn_handler(nx_conn_handler_cb_t handler)
{
    nx_server->conn_handler = handler;
}


void nx_server_set_signal_handler(nx_signal_handler_cb_t handler, void *context)
{
    nx_server->signal_handler = handler;
    nx_server->signal_context = context;
}


void nx_server_set_start_handler(nx_thread_start_cb_t handler, void *context)
{
    nx_server->start_handler = handler;
    nx_server->start_context = context;
}


void nx_server_set_user_fd_handler(nx_user_fd_handler_cb_t ufd_handler)
{
    nx_server->ufd_handler = ufd_handler;
}


void nx_server_run(void)
{
    int i;
    if (!nx_server)
        return;

    assert(nx_server->conn_handler); // Server can't run without a connection handler.

    for (i = 1; i < nx_server->thread_count; i++)
        thread_start(nx_server->threads[i]);

    nx_log(module, LOG_INFO, "Operational, %d Threads Running", nx_server->thread_count);

    thread_run((void*) nx_server->threads[0]);

    for (i = 1; i < nx_server->thread_count; i++)
        thread_join(nx_server->threads[i]);

    nx_log(module, LOG_INFO, "Shut Down");
}


void nx_server_stop(void)
{
    int idx;

    sys_mutex_lock(nx_server->lock);
    for (idx = 0; idx < nx_server->thread_count; idx++)
        thread_cancel(nx_server->threads[idx]);
    sys_cond_signal_all(nx_server->cond);
    pn_driver_wakeup(nx_server->driver);
    sys_mutex_unlock(nx_server->lock);
}


void nx_server_signal(int signum)
{
    signal(signum, signal_handler);
}


void nx_server_pause(void)
{
    sys_mutex_lock(nx_server->lock);

    //
    // Bump the request count to stop all the threads.
    //
    nx_server->pause_requests++;
    int my_sequence = nx_server->pause_next_sequence++;

    //
    // Awaken all threads that are currently blocking.
    //
    sys_cond_signal_all(nx_server->cond);
    pn_driver_wakeup(nx_server->driver);

    //
    // Wait for the paused thread count plus the number of threads requesting a pause to equal
    // the total thread count.  Also, don't exit the blocking loop until now_serving equals our
    // sequence number.  This ensures that concurrent pausers don't run at the same time.
    //
    while ((nx_server->threads_paused + nx_server->pause_requests < nx_server->thread_count) ||
           (my_sequence != nx_server->pause_now_serving))
        sys_cond_wait(nx_server->cond, nx_server->lock);

    sys_mutex_unlock(nx_server->lock);
}


void nx_server_resume(void)
{
    sys_mutex_lock(nx_server->lock);
    nx_server->pause_requests--;
    nx_server->pause_now_serving++;
    sys_cond_signal_all(nx_server->cond);
    sys_mutex_unlock(nx_server->lock);
}


void nx_server_activate(nx_connection_t *ctx)
{
    if (!ctx)
        return;

    pn_connector_t *ctor = ctx->pn_cxtr;
    if (!ctor)
        return;

    if (!pn_connector_closed(ctor))
        pn_connector_activate(ctor, PN_CONNECTOR_WRITABLE);
}


void nx_connection_set_context(nx_connection_t *conn, void *context)
{
    conn->user_context = context;
}


void *nx_connection_get_context(nx_connection_t *conn)
{
    return conn->user_context;
}


pn_connection_t *nx_connection_pn(nx_connection_t *conn)
{
    return conn->pn_conn;
}


nx_listener_t *nx_server_listen(const nx_server_config_t *config, void *context)
{
    nx_listener_t *li = new_nx_listener_t();

    if (!li)
        return 0;

    li->config      = config;
    li->context     = context;
    li->pn_listener = pn_listener(nx_server->driver, config->host, config->port, (void*) li);

    if (!li->pn_listener) {
        nx_log(module, LOG_ERROR, "Driver Error %d (%s)",
               pn_driver_errno(nx_server->driver), pn_driver_error(nx_server->driver));
        free_nx_listener_t(li);
        return 0;
    }
    nx_log(module, LOG_TRACE, "Listening on %s:%s", config->host, config->port);

    return li;
}


void nx_server_listener_free(nx_listener_t* li)
{
    pn_listener_free(li->pn_listener);
    free_nx_listener_t(li);
}


void nx_server_listener_close(nx_listener_t* li)
{
    pn_listener_close(li->pn_listener);
}


nx_connector_t *nx_server_connect(const nx_server_config_t *config, void *context)
{
    nx_connector_t *ct = new_nx_connector_t();

    if (!ct)
        return 0;

    ct->state   = CXTR_STATE_CONNECTING;
    ct->config  = config;
    ct->context = context;
    ct->ctx     = 0;
    ct->timer   = nx_timer(cxtr_try_open, (void*) ct);
    ct->delay   = 0;

    nx_timer_schedule(ct->timer, ct->delay);
    return ct;
}


void nx_server_connector_free(nx_connector_t* ct)
{
    // Don't free the proton connector.  This will be done by the connector
    // processing/cleanup.

    if (ct->ctx) {
        pn_connector_close(ct->ctx->pn_cxtr);
        ct->ctx->connector = 0;
    }

    nx_timer_free(ct->timer);
    free_nx_connector_t(ct);
}


nx_user_fd_t *nx_user_fd(int fd, void *context)
{
    nx_user_fd_t *ufd = new_nx_user_fd_t();

    if (!ufd)
        return 0;

    nx_connection_t *ctx = new_nx_connection_t();
    ctx->state        = CONN_STATE_USER;
    ctx->owner_thread = CONTEXT_NO_OWNER;
    ctx->enqueued     = 0;
    ctx->pn_conn      = 0;
    ctx->listener     = 0;
    ctx->connector    = 0;
    ctx->context      = 0;
    ctx->user_context = 0;
    ctx->ufd          = ufd;

    ufd->context = context;
    ufd->fd      = fd;
    ufd->pn_conn = pn_connector_fd(nx_server->driver, fd, (void*) ctx);
    pn_driver_wakeup(nx_server->driver);

    return ufd;
}


void nx_user_fd_free(nx_user_fd_t *ufd)
{
    pn_connector_close(ufd->pn_conn);
    free_nx_user_fd_t(ufd);
}


void nx_user_fd_activate_read(nx_user_fd_t *ufd)
{
    pn_connector_activate(ufd->pn_conn, PN_CONNECTOR_READABLE);
    pn_driver_wakeup(nx_server->driver);
}


void nx_user_fd_activate_write(nx_user_fd_t *ufd)
{
    pn_connector_activate(ufd->pn_conn, PN_CONNECTOR_WRITABLE);
    pn_driver_wakeup(nx_server->driver);
}


bool nx_user_fd_is_readable(nx_user_fd_t *ufd)
{
    return pn_connector_activated(ufd->pn_conn, PN_CONNECTOR_READABLE);
}


bool nx_user_fd_is_writeable(nx_user_fd_t *ufd)
{
    return pn_connector_activated(ufd->pn_conn, PN_CONNECTOR_WRITABLE);
}


void nx_server_timer_pending_LH(nx_timer_t *timer)
{
    DEQ_INSERT_TAIL(nx_server->pending_timers, timer);
}


void nx_server_timer_cancel_LH(nx_timer_t *timer)
{
    DEQ_REMOVE(nx_server->pending_timers, timer);
}

