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

#define _GNU_SOURCE
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <assert.h>
#include "test_case.h"
#include <qpid/dispatch.h>

#define THREAD_COUNT 4
#define OCTET_COUNT  100

static dx_dispatch_t *dx;
static sys_mutex_t   *test_lock;

static void *expected_context;
static int   call_count;
static int   threads_seen[THREAD_COUNT];
static char  stored_error[512];

static int   write_count;
static int   read_count;
static int   fd[2];
static dx_user_fd_t *ufd_write;
static dx_user_fd_t *ufd_read;


static void thread_start_handler(void *context, int thread_id)
{
    sys_mutex_lock(test_lock);
    if (context != expected_context && !stored_error[0])
        sprintf(stored_error, "Unexpected Context Value: %lx", (long) context);
    if (thread_id >= THREAD_COUNT && !stored_error[0])
        sprintf(stored_error, "Thread_ID too large: %d", thread_id);
    if (thread_id < 0 && !stored_error[0])
        sprintf(stored_error, "Thread_ID negative: %d", thread_id);

    call_count++;
    if (thread_id >= 0 && thread_id < THREAD_COUNT)
        threads_seen[thread_id]++;

    if (call_count == THREAD_COUNT)
        dx_server_stop(dx);
    sys_mutex_unlock(test_lock);
}


static void ufd_handler(void *context, dx_user_fd_t *ufd)
{
    long    dir = (long) context;
    char    buffer;
    ssize_t len;
    static  int in_read  = 0;
    static  int in_write = 0;

    if (dir == 0) { // READ
        in_read++;
        assert(in_read == 1);
        if (!dx_user_fd_is_readable(ufd_read)) {
            sprintf(stored_error, "Expected Readable");
            dx_server_stop(dx);
        } else {
            len = read(fd[0], &buffer, 1);
            if (len == 1) {
                read_count++;
                if (read_count == OCTET_COUNT)
                    dx_server_stop(dx);
            }
            dx_user_fd_activate_read(ufd_read);
        }
        in_read--;
    } else {        // WRITE
        in_write++;
        assert(in_write == 1);
        if (!dx_user_fd_is_writeable(ufd_write)) {
            sprintf(stored_error, "Expected Writable");
            dx_server_stop(dx);
        } else {
            write(fd[1], "X", 1);

            write_count++;
            if (write_count < OCTET_COUNT)
                dx_user_fd_activate_write(ufd_write);
        }
        in_write--;
    }
}


static void fd_test_start(void *context)
{
    dx_user_fd_activate_read(ufd_read);
}


static char* test_start_handler(void *context)
{
    int i;

    dx = dx_dispatch(THREAD_COUNT);

    expected_context = (void*) 0x00112233;
    stored_error[0] = 0x0;
    call_count      = 0;
    for (i = 0; i < THREAD_COUNT; i++)
        threads_seen[i] = 0;

    dx_server_set_start_handler(dx, thread_start_handler, expected_context);
    dx_server_run(dx);
    dx_dispatch_free(dx);

    if (stored_error[0])            return stored_error;
    if (call_count != THREAD_COUNT) return "Incorrect number of thread-start callbacks";
    for (i = 0; i < THREAD_COUNT; i++)
        if (threads_seen[i] != 1)   return "Incorrect count on one thread ID";

    return 0;
}


static char *test_server_start(void *context)
{
    dx = dx_dispatch(THREAD_COUNT);
    dx_server_start(dx);
    dx_server_stop(dx);
    dx_dispatch_free(dx);

    return 0;
}


static char* test_user_fd(void *context)
{
    int res;
    dx_timer_t *timer;

    dx = dx_dispatch(THREAD_COUNT);
    dx_server_set_user_fd_handler(dx, ufd_handler);
    timer = dx_timer(dx, fd_test_start, 0);
    dx_timer_schedule(timer, 0);

    stored_error[0] = 0x0;
    res = pipe2(fd, O_NONBLOCK);
    if (res != 0) return "Error creating pipe2";

    ufd_write = dx_user_fd(dx, fd[1], (void*) 1);
    ufd_read  = dx_user_fd(dx, fd[0], (void*) 0);

    dx_server_run(dx);
    dx_timer_free(timer);
    dx_dispatch_free(dx);
    close(fd[0]);
    close(fd[1]);

    if (stored_error[0])            return stored_error;
    if (write_count - OCTET_COUNT > 2) sprintf(stored_error, "Excessively high Write Count: %d", write_count);
    if (read_count != OCTET_COUNT)  sprintf(stored_error, "Incorrect Read Count: %d", read_count);;

    if (stored_error[0]) return stored_error;
    return 0;
}


int server_tests(void)
{
    int result = 0;
    test_lock = sys_mutex();
    dx_log_set_mask(LOG_NONE);

    TEST_CASE(test_server_start, 0);
    TEST_CASE(test_start_handler, 0);
    TEST_CASE(test_user_fd, 0);

    sys_mutex_free(test_lock);
    return result;
}

