/*
 *
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
 *
 */

// Ensure definition of OpenThread in mingw
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0501
#endif

#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/SystemInfo.h"

#include <process.h>
#include <windows.h>

/*
 * This implementation distinguishes between two types of thread: Qpid
 * threads (based on qpid::sys::Runnable) and the rest.  It provides a
 * join() that will not deadlock against the Windows loader lock for
 * Qpid threads.
 *
 * System thread identifiers are unique per Windows thread; thread
 * handles are not.  Thread identifiers can be recycled, but keeping a
 * handle open against the thread prevents recycling as long as
 * shared_ptr references to a ThreadPrivate structure remain.
 *
 * There is a 1-1 relationship between Qpid threads and their
 * ThreadPrivate structure.  Non-Qpid threads do not need to find the
 * qpidThreadDone handle, so there may be a 1-many relationship for
 * them.
 *
 * TLS storage is used for a lockless solution for static library
 * builds.  The special case of LoadLibrary/FreeLibrary requires
 * additional synchronization variables and resource cleanup in
 * DllMain.  _DLL marks the dynamic case.
 */

namespace qpid {
namespace sys {

class ThreadPrivate {
public:
    friend class Thread;
    friend unsigned __stdcall runThreadPrivate(void*);
    typedef boost::shared_ptr<ThreadPrivate> shared_ptr;
    ~ThreadPrivate();

private:
    unsigned threadId;
    HANDLE threadHandle;
    HANDLE initCompleted;
    HANDLE qpidThreadDone;
    Runnable* runnable;
    shared_ptr keepAlive;

    ThreadPrivate() : threadId(GetCurrentThreadId()), initCompleted(NULL),
                      qpidThreadDone(NULL), runnable(NULL) {
        threadHandle =  OpenThread (SYNCHRONIZE, FALSE, threadId);
        QPID_WINDOWS_CHECK_CRT_NZ(threadHandle);
    }

    ThreadPrivate(Runnable* r) : threadHandle(NULL), initCompleted(NULL),
                                 qpidThreadDone(NULL), runnable(r) {}

    void start(shared_ptr& p);
    static shared_ptr createThread(Runnable* r);
};

}}  // namespace qpid::sys 


namespace {
using namespace qpid::sys;

#ifdef _DLL
class ScopedCriticalSection
{
  public:
    ScopedCriticalSection(CRITICAL_SECTION& cs) : criticalSection(cs) { EnterCriticalSection(&criticalSection); }
    ~ScopedCriticalSection() { LeaveCriticalSection(&criticalSection); }
  private:
    CRITICAL_SECTION& criticalSection;
};

CRITICAL_SECTION threadLock;
long runningThreads = 0;
HANDLE threadsDone;
bool terminating = false;
#endif


DWORD volatile tlsIndex = TLS_OUT_OF_INDEXES;

DWORD getTlsIndex() {
    if (tlsIndex != TLS_OUT_OF_INDEXES)
        return tlsIndex;        // already set

    DWORD trialIndex = TlsAlloc();
    QPID_WINDOWS_CHECK_NOT(trialIndex, TLS_OUT_OF_INDEXES); // No OS resource
    
    // only one thread gets to set the value
    DWORD actualIndex = (DWORD) InterlockedCompareExchange((LONG volatile *) &tlsIndex, (LONG) trialIndex, (LONG) TLS_OUT_OF_INDEXES);
    if (actualIndex == TLS_OUT_OF_INDEXES)
        return trialIndex;      // we won the race
    else {
        TlsFree(trialIndex);
        return actualIndex;
    }
}

} // namespace

namespace qpid {
namespace sys {

unsigned __stdcall runThreadPrivate(void* p)
{
    ThreadPrivate* threadPrivate = static_cast<ThreadPrivate*>(p);
    TlsSetValue(getTlsIndex(), threadPrivate);

    WaitForSingleObject (threadPrivate->initCompleted, INFINITE);
    CloseHandle (threadPrivate->initCompleted);
    threadPrivate->initCompleted = NULL;

    try {
        threadPrivate->runnable->run();
    } catch (...) {
        // not our concern
    }

    SetEvent (threadPrivate->qpidThreadDone); // allow join()
    threadPrivate->keepAlive.reset();         // may run ThreadPrivate destructor

#ifdef _DLL
    {
        ScopedCriticalSection l(threadLock);
        if (--runningThreads == 0)
            SetEvent(threadsDone);
    }
#endif
    return 0;
}


ThreadPrivate::shared_ptr ThreadPrivate::createThread(Runnable* runnable) {
    ThreadPrivate::shared_ptr tp(new ThreadPrivate(runnable));
    tp->start(tp);
    return tp;
}

void ThreadPrivate::start(ThreadPrivate::shared_ptr& tp) {
    getTlsIndex();              // fail here if OS problem, not in new thread

    initCompleted = CreateEvent (NULL, TRUE, FALSE, NULL);
    QPID_WINDOWS_CHECK_CRT_NZ(initCompleted);
    qpidThreadDone = CreateEvent (NULL, TRUE, FALSE, NULL);
    QPID_WINDOWS_CHECK_CRT_NZ(qpidThreadDone);

#ifdef _DLL
    {
        ScopedCriticalSection l(threadLock);
        if (terminating)
            throw qpid::Exception(QPID_MSG("creating thread after exit/FreeLibrary"));
        runningThreads++;
    }
#endif

    uintptr_t h =  _beginthreadex(0,
                                  0,
                                  runThreadPrivate,
                                  (void *)this,
                                  0,
                                  &threadId);

#ifdef _DLL
    if (h == NULL) {
        ScopedCriticalSection l(threadLock);
        if (--runningThreads == 0)
            SetEvent(threadsDone);
    }
#endif

    QPID_WINDOWS_CHECK_CRT_NZ(h);

    // Success
    keepAlive = tp;
    threadHandle = reinterpret_cast<HANDLE>(h);
    SetEvent (initCompleted);
}

ThreadPrivate::~ThreadPrivate() {
    if (threadHandle)
        CloseHandle (threadHandle);
    if (initCompleted)
        CloseHandle (initCompleted);
    if (qpidThreadDone)
        CloseHandle (qpidThreadDone);
}


Thread::Thread() {}

Thread::Thread(Runnable* runnable) : impl(ThreadPrivate::createThread(runnable)) {}

Thread::Thread(Runnable& runnable) : impl(ThreadPrivate::createThread(&runnable)) {}

Thread::operator bool() {
    return !!impl;
}

bool Thread::operator==(const Thread& t) const {
    if (!impl || !t.impl)
        return false;
    return impl->threadId == t.impl->threadId;
}

bool Thread::operator!=(const Thread& t) const {
    return !(*this==t);
}

void Thread::join() {
    if (impl) {
        DWORD status;
        if (impl->runnable) {
            HANDLE handles[2] = {impl->qpidThreadDone, impl->threadHandle};
            // wait for either.  threadHandle not signalled if loader
            // lock held (FreeLibrary).  qpidThreadDone not signalled
            // if thread terminated by exit().
            status = WaitForMultipleObjects (2, handles, false, INFINITE);
        }
        else
            status = WaitForSingleObject (impl->threadHandle, INFINITE);
        QPID_WINDOWS_CHECK_NOT(status, WAIT_FAILED);
    }
}

unsigned long Thread::logId() {
    return GetCurrentThreadId();
}

/* static */
Thread Thread::current() {
    ThreadPrivate* tlsValue = (ThreadPrivate *) TlsGetValue(getTlsIndex());
    Thread t;
    if (tlsValue != NULL) {
        // called from within Runnable->run(), so keepAlive has positive use count
        t.impl = tlsValue->keepAlive;
    }
    else
        t.impl.reset(new ThreadPrivate());
    return t;
}

}}  // namespace qpid::sys


#ifdef _DLL

namespace qpid {
namespace sys {
namespace windows {

extern bool processExiting;
extern bool libraryUnloading;

}}} // namespace qpid::sys::SystemInfo

// DllMain: called possibly many times in a process lifetime if dll
// loaded and freed repeatedly.  Be mindful of Windows loader lock
// and other DllMain restrictions.

BOOL APIENTRY DllMain(HMODULE hm, DWORD reason, LPVOID reserved) {
    switch (reason) {
    case DLL_PROCESS_ATTACH:
        InitializeCriticalSection(&threadLock);
        threadsDone = CreateEvent(NULL, TRUE, FALSE, NULL);
        break;

    case DLL_PROCESS_DETACH:
        terminating = true;
        if (reserved != NULL) {
            // process exit(): threads are stopped arbitrarily and
            // possibly in an inconsistent state.  Not even threadLock
            // can be trusted.  All static destructors for this unit
            // are pending and face the same unsafe environment.
            // Any resources this unit knows about will be released as
            // part of process tear down by the OS.  Accordingly, skip
            // any clean up tasks.
            qpid::sys::windows::processExiting = true;
            return TRUE;
        }
        else {
            // FreeLibrary(): threads are still running and we are
            // encouraged to clean up to avoid leaks.  Mostly we just
            // want any straggler threads to finish and notify
            // threadsDone as the last thing they do.
            qpid::sys::windows::libraryUnloading = true;
            while (1) {
                {
                    ScopedCriticalSection l(threadLock);
                    if (runningThreads == 0)
                        break;
                    ResetEvent(threadsDone);
                }
                WaitForSingleObject(threadsDone, INFINITE);
            }
            if (tlsIndex != TLS_OUT_OF_INDEXES)
                TlsFree(getTlsIndex());
            CloseHandle(threadsDone);
            DeleteCriticalSection(&threadLock);
        }
        break;

    case DLL_THREAD_ATTACH:
    case DLL_THREAD_DETACH:
        break;
    }
    return TRUE;
}

#endif
