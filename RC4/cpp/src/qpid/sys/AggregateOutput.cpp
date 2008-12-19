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

#include "qpid/sys/AggregateOutput.h"
#include "qpid/log/Statement.h"
#include <algorithm>

namespace qpid {
namespace sys {
    
void AggregateOutput::activateOutput() { control.activateOutput(); }

void AggregateOutput::giveReadCredit(int32_t credit) { control.giveReadCredit(credit); }

bool AggregateOutput::hasOutput() {
    for (TaskList::const_iterator i = tasks.begin(); i != tasks.end(); ++i) 
        if ((*i)->hasOutput()) return true;
    return false;
}

bool AggregateOutput::doOutput()
{
    bool result = false;
    if (!tasks.empty()) {
        if (next >= tasks.size()) next = next % tasks.size();
        
        size_t start = next;
        //loop until a task generated some output
        while (!result) {
            result = tasks[next++]->doOutput();
	    if (tasks.empty()) break;
            if (next >= tasks.size()) next = next % tasks.size();
            if (start == next) break;
        }
    }
    return result;
}

void AggregateOutput::addOutputTask(OutputTask* t)
{
    tasks.push_back(t);
}
     
void AggregateOutput::removeOutputTask(OutputTask* t)
{
    TaskList::iterator i = std::find(tasks.begin(), tasks.end(), t);
    if (i != tasks.end()) tasks.erase(i);
}

void AggregateOutput::removeAll()
{
    tasks.clear();
}

}} // namespace qpid::sys
