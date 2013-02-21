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

#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/ctools.h>
#include <stdio.h>
#include <pthread.h>

struct sys_mutex_t {
    pthread_mutex_t mutex;
    int             acquired;
};

sys_mutex_t *sys_mutex(void)
{
    sys_mutex_t *mutex = NEW(sys_mutex_t);
    pthread_mutex_init(&(mutex->mutex), 0);
    mutex->acquired = 0;
    return mutex;
}


void sys_mutex_free(sys_mutex_t *mutex)
{
    assert(!mutex->acquired);
    pthread_mutex_destroy(&(mutex->mutex));
    free(mutex);
}


void sys_mutex_lock(sys_mutex_t *mutex)
{
    pthread_mutex_lock(&(mutex->mutex));
    assert(!mutex->acquired);
    mutex->acquired++;
}


void sys_mutex_unlock(sys_mutex_t *mutex)
{
    mutex->acquired--;
    assert(!mutex->acquired);
    pthread_mutex_unlock(&(mutex->mutex));
}


struct sys_cond_t {
    pthread_cond_t cond;
};


sys_cond_t *sys_cond(void)
{
    sys_cond_t *cond = NEW(sys_cond_t);
    pthread_cond_init(&(cond->cond), 0);
    return cond;
}


void sys_cond_free(sys_cond_t *cond)
{
    pthread_cond_destroy(&(cond->cond));
    free(cond);
}


void sys_cond_wait(sys_cond_t *cond, sys_mutex_t *held_mutex)
{
    assert(held_mutex->acquired);
    held_mutex->acquired--;
    pthread_cond_wait(&(cond->cond), &(held_mutex->mutex));
    held_mutex->acquired++;
}


void sys_cond_signal(sys_cond_t *cond)
{
    pthread_cond_signal(&(cond->cond));
}


void sys_cond_signal_all(sys_cond_t *cond)
{
    pthread_cond_broadcast(&(cond->cond));
}


struct sys_thread_t {
    pthread_t thread;
}; 

sys_thread_t *sys_thread(void *(*run_function) (void *), void *arg)
{
    sys_thread_t *thread = NEW(sys_thread_t);
    pthread_create(&(thread->thread), 0, run_function, arg);
    return thread;
}


void sys_thread_free(sys_thread_t *thread)
{
    free(thread);
}


void sys_thread_join(sys_thread_t *thread)
{
    pthread_join(thread->thread, 0);
}

