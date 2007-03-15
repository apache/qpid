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
#include "AccumulatedAck.h"

#include <assert.h>

using std::less_equal;
using std::bind2nd;
using namespace qpid::broker;

void AccumulatedAck::update(uint64_t firstTag, uint64_t lastTag){
    assert(firstTag<=lastTag);
    if (firstTag <= range + 1) {
        if (lastTag > range) range = lastTag;
    } else {
    	for (uint64_t tag = firstTag; tag<=lastTag; tag++)
            individual.push_back(tag);
    }
}

void AccumulatedAck::consolidate(){
    individual.sort();
    //remove any individual tags that are covered by range
    individual.remove_if(bind2nd(less_equal<uint64_t>(), range));
    //update range if possible (using <= allows for duplicates from overlapping ranges)
    while (individual.front() <= range + 1) {
        range = individual.front();
        individual.pop_front();
    }
}

void AccumulatedAck::clear(){
    range = 0;
    individual.clear();
}

bool AccumulatedAck::covers(uint64_t tag) const{
    return tag <= range || find(individual.begin(), individual.end(), tag) != individual.end();
}
