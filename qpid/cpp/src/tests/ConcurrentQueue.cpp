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

/**@file
 * Compare alternative implementations for BlockingQueue.
 */

#include "qpid/sys/BlockingQueue.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Time.h"

#include <boost/test/test_tools.hpp>
#include <boost/bind.hpp>

#include <deque>
#include <vector>
#include <iostream>

#include "time.h"

using namespace qpid::sys;
using namespace std;

template <class T> class DualVectorDualLockQueue {
  public:
    /** Optionally specify initial capacity of the queue to minimize
     * re-allocation.
     */
    DualVectorDualLockQueue(size_t capacity=16) {
        pushVec.reserve(capacity);
        popVec.reserve(capacity);
        popIter = popVec.end();
    }
    
    /** Push a data item onto the back of the queue */
    void push(const T& data) {
        Mutex::ScopedLock l(pushLock);
        pushVec.push_back(data);
    }

    /** If the queue is non-empty, pop the front item into data and
     * return true. If the queue is empty, return false
     */
    bool tryPop(T& data) {
        Mutex::ScopedLock l(popLock);
        if (popIter == popVec.end()) {
            popVec.clear();
            Mutex::ScopedLock l(pushLock);
            pushVec.swap(popVec);
            popIter = popVec.begin();
        }
        if (popIter == popVec.end())
            return false;
        else {
            data = *popIter++;
            return true;
        }
    }

  private:
    Mutex pushLock, popLock;
    std::vector<T> pushVec, popVec;
    typename std::vector<T>::iterator popIter;
};

template <class T> struct LockedDequeQueue : public BlockingQueue<T> {
    /** size_t ignored, can't pre-allocate space in a dequeue */
    LockedDequeQueue(size_t=0) {};
};

// ================ Test code.

/** Pause by sleeping */
void nsleep(const Duration& delay) {
    static Monitor m;
    AbsTime stop(now(), delay);
    while (now() < stop)
        m.wait(stop);
}

/** Pause by spinning */
void nspin(const Duration& delay) {
    AbsTime stop(now(), delay);
    while (now() < stop)
        ;
}

/** Unlocked fake queue for comparison */
struct NullQueue {
    NullQueue(int items=0) : npush(items), npop(items) {}
    void push(int) { --npush; }
    bool tryPop(int& n) {
        if (npop == 0)
            return false;
        else {
            n=npop--;
            return true;
        }
    }
    volatile int npush, npop;
};


// Global test parameters.
int items;
Duration delay(0);
boost::function<void()> npause;

template <class Q>
struct Pusher : public Runnable {
    Pusher(Q& q) : queue(q) {}
    void run() {
        for (int i=items; i > 0; i--) {
            queue.push(i);
            npause();
        }
    }
    Q& queue;
};

template <class Q>
struct Popper : public Runnable {
    Popper(Q& q) : queue(q) {}
    void run() {
        for (int i=items; i > 0; i--) {
            int n;
            if (queue.tryPop(n))
                BOOST_REQUIRE_EQUAL(i,n);
            npause();
        }
    }
    Q& queue;
};

ostream& operator<<(ostream& out, const Duration& d) {
    return out << double(d)/TIME_MSEC << " msecs";
}

void report(const char* s, const Duration &d) {
    cout << s << ": " << d
         << " (" << (double(items)*TIME_SEC)/d << " push-pops/sec" << ")"
         << endl;
}

template <class Q, class PusherT=Pusher<Q>, class PopperT=Popper<Q> >
struct Timer {
    static Duration time() {
        cout << endl << "==" << typeid(Q).name() << endl;
    
        Q queue(items);
        PusherT pusher(queue);
        PopperT popper(queue);

        // Serial
        AbsTime start=now();
        pusher.run();
        popper.run();
        Duration serial(start,now());
        report ("Serial", serial);

        // Concurrent
        start=now();
        Thread pushThread(pusher);
        Thread popThread(popper);
        pushThread.join();
        popThread.join();
        Duration concurrent(start,now());
        report ("Concurrent", concurrent);

        cout << "Serial/concurrent: " << double(serial)/concurrent << endl;
        return concurrent;
    }
};

int test_main(int argc, char** argv) {
    items = (argc > 1) ? atoi(argv[1]) : 250*1000;
    delay = (argc > 2) ? atoi(argv[2]) : 4*1000; 
    npause=boost::bind(nspin, delay);

    cout << "Push/pop " << items << " items, delay=" <<  delay << endl;
    Timer<NullQueue>::time();
    Duration dv = Timer<DualVectorDualLockQueue<int> >::time();
    Duration d = Timer<LockedDequeQueue<int> >::time();
    cout << endl;
    cout << "Ratio deque/dual vector=" << double(d)/dv << endl;
    return 0;
}
// namespace 
