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
#ifndef _TaskQueue_
#define _TaskQueue_

#include <iostream>
#include <memory>
#include <queue>
#include "qpid/concurrent/LockedQueue.h"
#include "qpid/concurrent/Runnable.h"
#include "qpid/concurrent/ThreadPool.h"

namespace qpid {
namespace concurrent {
    template<class T, class L> class TaskQueue : public virtual Runnable
    {
        const int max_iterations_per_run;
        L lock;
        //LockedQueue<T, L> queue;
        std::queue<T*> queue;
        ThreadPool* const pool;
        T* work;
        bool running;        
        volatile bool stopped;        
        TaskQueue<T, L>* next;

        volatile bool inrun;

        bool hasWork();
        void completed();

        T* take();

    protected:
        /**
         * Callback though which the task is executed 
         */
        virtual void execute(T* t) = 0;
        /**
         * Allows a task to be completed asynchronously to the
         * execute() call if required.
         */
        virtual bool isComplete(T* t);
        /**
         * Should be called to signal completion of a task that was
         * signalled as not complete through the isComplete() methods
         * return value. This will allow normal processing to resume.
         */
        virtual void complete();

    public:
        TaskQueue(ThreadPool* const pool, int max_iterations_per_run = 100);
        virtual void run();
        void trigger();
        bool append(T* t);
        void stop(bool drain);
        inline void setNext(TaskQueue<T, L>* next){ this->next = next; }
    };

    template<class T, class L> TaskQueue<T, L>::TaskQueue(ThreadPool* const _pool, int _max_iterations_per_run) : 
        pool(_pool), 
        max_iterations_per_run(_max_iterations_per_run),
        work(0), 
        running(false),
        stopped(false),
        next(0), inrun(false){
    }

    template<class T, class L> void TaskQueue<T, L>::run(){
        if(inrun) std::cout << "Already running" << std::endl;
        inrun = true;

        bool blocked = false;
        int count = max_iterations_per_run;
        while(!blocked && hasWork() && count){
            execute(work);
            if(isComplete(work)){
                completed();
            }else{
                blocked = true;
            }
            count--;
        }
        inrun = false;

        if(!blocked && count == 0){//performed max_iterations_per_run, requeue task to ensure fairness 
            //running will still be true at this point
            lock.acquire();
            running = false;
            if(stopped) lock.notify();
            lock.release();

            trigger();
        }else if(hasWork()){//task was added to queue after we exited the loop above; should not need this?
            trigger();
        }
    }

    template<class T, class L> void TaskQueue<T, L>::trigger(){
        lock.acquire();
        if(!running){
            running = true;
            pool->addTask(this);
        }
        lock.release();
    }

    template<class T, class L> bool TaskQueue<T, L>::hasWork(){
        lock.acquire();
        if(!work) work = take();//queue.take();
        if(!work){
            running = false;
            if(stopped) lock.notify();
        }
        lock.release();
        return work;
    }

    template<class T, class L> bool TaskQueue<T, L>::append(T* item){
        if(!stopped){
            lock.acquire();

            //queue.put(item);
            queue.push(item);

            if(!running){
                running = true;
                pool->addTask(this);
            }
            lock.release();
                //}
            return true;
        }else{
            return false;
        }
    }

    template<class T, class L> bool TaskQueue<T, L>::isComplete(T* item){
        return true;//by default assume all tasks are synchronous w.r.t. execute()
    }


    template<class T, class L> void TaskQueue<T, L>::completed(){
        if(next){
            if(!next->append(work)){
                std::cout << "Warning: dropping task as next queue appears to have stopped." << std::endl;
            }
        }else{
            delete work;
        }
        work = 0;        
    }
        
    template<class T, class L> void TaskQueue<T, L>::complete(){
        completed();
        lock.acquire();
        running = false;
        if(stopped) lock.notify();
        lock.release();
    }

    template<class T, class L> void TaskQueue<T, L>::stop(bool drain){
        //prevent new tasks from being added
        stopped = true;
        //wait until no longer running
        lock.acquire();
        while(running && (drain && hasWork())){
            lock.wait();
        }
        lock.release();
    }

    template<class T, class L> T* TaskQueue<T, L>::take(){
        T* item = 0;
        if(!queue.empty()){
            item = queue.front();
            queue.pop();
        }
        return item;
    }
}
}


#endif
