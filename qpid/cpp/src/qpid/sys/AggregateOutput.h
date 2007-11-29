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
#ifndef _AggregateOutput_
#define _AggregateOutput_

#include <vector>
#include "Mutex.h"
#include "OutputControl.h"
#include "OutputTask.h"

namespace qpid {
namespace sys {

    class AggregateOutput : public OutputTask, public OutputControl
    {
        typedef std::vector<OutputTask*> TaskList;

        TaskList tasks;
        size_t next;
        OutputControl& control;

    public:
        AggregateOutput(OutputControl& c) : next(0), control(c) {};
        //this may be called on any thread
        void activateOutput();
        //all the following will be called on the same thread
        bool doOutput();
        void addOutputTask(OutputTask* t);
        void removeOutputTask(OutputTask* t);
    };

}
}


#endif
