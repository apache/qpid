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
#include <iostream>

using std::list;
using std::max;
using std::min;
using namespace qpid::broker;

void AccumulatedAck::update(DeliveryId first, DeliveryId last){
    assert(first <= last);
    if (last < mark) return;


    Range r(first, last);
    bool handled = false;
    list<Range>::iterator merged = ranges.end();
    if (r.mergeable(mark)) {
        mark = r.end;
        merged = ranges.begin();
        handled = true;
    } else {
        for (list<Range>::iterator i = ranges.begin(); i != ranges.end() && !handled; i++) {
            if (i->merge(r)) {
                merged = i;
                handled = true;
            } else if (r.start < i->start) {
                ranges.insert(i, r);
                handled = true;
            }
        }
    }
    if (!handled) {
        ranges.push_back(r);
    } else {
        while (!ranges.empty() && ranges.front().end <= mark) { 
            ranges.pop_front(); 
        }
        //new range is incorporated, but may be possible to consolidate
        if (merged == ranges.begin()) {
            //consolidate mark
            while (merged != ranges.end() && merged->mergeable(mark)) {
                mark = merged->end;
                merged = ranges.erase(merged);
            }
        }
        if (merged != ranges.end()) {
            //consolidate ranges
            list<Range>::iterator i = merged;
            list<Range>::iterator j = i++;
            while (i != ranges.end() && j->merge(*i)) {
                j = i++;
            }
        }
    }
}

void AccumulatedAck::consolidate(){}

void AccumulatedAck::clear(){
    mark = 0;//not sure that this is valid when wraparound is a possibility
    ranges.clear();
}

bool AccumulatedAck::covers(DeliveryId tag) const{
    if (tag <= mark) return true;
    for (list<Range>::const_iterator i = ranges.begin(); i != ranges.end(); i++) {
        if (i->contains(tag)) return true;
    }
    return false;
}

bool Range::contains(DeliveryId i) const 
{ 
    return i >= start && i <= end; 
}

bool Range::intersect(const Range& r) const 
{ 
    return r.contains(start) || r.contains(end) || contains(r.start) || contains(r.end); 
}

bool Range::merge(const Range& r) 
{ 
    if (intersect(r) || mergeable(r.end) || r.mergeable(end)) {
        start = min(start, r.start); 
        end = max(end, r.end); 
        return true;
    } else {
        return false;
    }
}

bool Range::mergeable(const DeliveryId& s) const
{ 
    if (contains(s) || start - s == 1) {
        return true;
    } else {
        return false;
    }
}

Range::Range(DeliveryId s, DeliveryId e) : start(s), end(e) {}


namespace qpid{
namespace broker{
    std::ostream& operator<<(std::ostream& out, const Range& r)
    {
        out << "[" << r.start.getValue() << "-" << r.end.getValue() << "]";
        return out;
    }

    std::ostream& operator<<(std::ostream& out, const AccumulatedAck& a)
    { 
        out << "{mark: " << a.mark.getValue() << ", ranges: (";
        for (list<Range>::const_iterator i = a.ranges.begin(); i != a.ranges.end(); i++) {        
            if (i != a.ranges.begin()) out << ", ";
            out << *i;
        }
        out << ")]";
        return out;
    }
}}
