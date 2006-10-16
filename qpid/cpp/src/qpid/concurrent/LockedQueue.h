/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _LockedQueue_
#define _LockedQueue_

#include <queue>
#include "qpid/concurrent/Monitor.h"

/**
 * A threadsafe queue abstraction
 */
namespace qpid {
namespace concurrent {
    template<class T, class L> class LockedQueue
    {
        L lock;
        std::queue<T*> queue;

    public:
        void put(T* item);
        T* take();
        bool empty();
    };

    template<class T, class L> void LockedQueue<T, L>::put(T* item){
        lock.acquire();
        queue.push(item);
        lock.release();
    }

    template<class T, class L> T* LockedQueue<T, L>::take(){
        lock.acquire();        
        T* item = 0;
        if(!queue.empty()){
            item = queue.front();
            queue.pop();
        }
        lock.release();
        return item;
    }

    template<class T, class L> bool LockedQueue<T, L>::empty(){
        lock.acquire();
        bool result = queue.empty();
        lock.release();
        return result;
    }

}
}


#endif
