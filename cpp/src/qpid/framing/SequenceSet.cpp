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

#include "SequenceSet.h"

using namespace qpid::framing;
using std::max;
using std::min;

namespace {
//each range contains 2 numbers, 4 bytes each
uint16_t RANGE_SIZE = 2 * 4; 
}

void SequenceSet::encode(Buffer& buffer) const
{
    buffer.putShort(ranges.size() * RANGE_SIZE);
    for (Ranges::const_iterator i = ranges.begin(); i != ranges.end(); i++) {
        i->encode(buffer);
    }
}

void SequenceSet::decode(Buffer& buffer)
{
    uint16_t size = buffer.getShort();
    uint16_t count = size / RANGE_SIZE;//number of ranges
    if (size % RANGE_SIZE) throw FrameErrorException(QPID_MSG("Invalid size for sequence set: " << size)); 

    for (uint16_t i = 0; i < count; i++) {
        add(SequenceNumber(buffer.getLong()), SequenceNumber(buffer.getLong()));
    }
}

uint32_t SequenceSet::size() const
{
    return 2 /*size field*/ + (ranges.size() * RANGE_SIZE);
}

bool SequenceSet::contains(const SequenceNumber& point) const
{
    for (Ranges::const_iterator i = ranges.begin(); i != ranges.end(); i++) {
        if (i->contains(point)) return true;
    }
    return false;
}

void SequenceSet::add(const SequenceNumber& s)
{
    add(s, s);
}

void SequenceSet::add(const SequenceNumber& start, const SequenceNumber& end)
{
    if (start > end) {
        add(end, start);
    } else {
        Range r(start, end);
        bool merged = false;
        Ranges::iterator i = ranges.begin();
        while (i != ranges.end() && !merged && i->start < start) {
            if (i->merge(r)) merged = true;            
            i++;
        }
        if (!merged) {
            ranges.insert(i, r);
        }
    }
}

void SequenceSet::add(const SequenceSet& set)
{
    for (Ranges::const_iterator i = set.ranges.begin(); i != set.ranges.end(); i++) {
        add(i->start, i->end);
    }
}

void SequenceSet::remove(const SequenceSet& set)
{
    for (Ranges::const_iterator i = set.ranges.begin(); i != set.ranges.end(); i++) {
        remove(i->start, i->end);
    }    
}

void SequenceSet::remove(const SequenceNumber& start, const SequenceNumber& end)
{
    if (start > end) {
        remove(end, start);
    } else {
        Ranges::iterator i = ranges.begin(); 
        while (i != ranges.end() && i->start < start) {
            if (start <= i->end) {
                if (end > i->end) {
                    //i.e. start is within the range pointed to by i, but end is not
                    i->end = (uint32_t)start - 1;
                } else {
                    //whole of range to be deleted is contained within that pointed to be i
                    if (end == i->end) {
                        //just shrink range pointed to by i
                        i->end = (uint32_t)start - 1;
                    } else {
                        //need to split the range pointed to by i
                        Range r(i->start, (uint32_t)start - 1);
                        i->start = end + 1;
                        ranges.insert(i, r);
                    }
                    return;//no need to go any further
                }
            }
            i++;
        }
        Ranges::iterator j = i;
        while (j != ranges.end() && j->end < end) {            
            j++;
        }
        if (j->start <= end){
            j->start = end + 1;
        }
        ranges.erase(i, j);        
    }
}

void SequenceSet::remove(const SequenceNumber& s)
{
    for (Ranges::iterator i = ranges.begin(); i != ranges.end() && s >= i->start; i++) {        
        if (i->start == s) {
            if (i->start == i->end) {
                ranges.erase(i);
            } else {
                ++(i->start);
            }
        } else if (i->end == s) {
            --(i->end);
        } else if (i->contains(s)) {
            //need to split range pointed to by i
            Range r(i->start, (uint32_t)s - 1);
            i->start = s + 1;
            ranges.insert(i, r);
        }
    }    
}

bool SequenceSet::empty() const
{
    return ranges.empty();
}

void SequenceSet::clear()
{
    return ranges.clear();
}

bool SequenceSet::Range::contains(SequenceNumber i) const 
{ 
    return i >= start && i <= end; 
}

bool SequenceSet::Range::intersects(const Range& r) const 
{ 
    return r.contains(start) || r.contains(end) || contains(r.start) || contains(r.end); 
}

bool SequenceSet::Range::merge(const Range& r) 
{ 
    if (intersects(r) || mergeable(r.end) || r.mergeable(end)) {
        start = min(start, r.start); 
        end = max(end, r.end); 
        return true;
    } else {
        return false;
    }
}

bool SequenceSet::Range::mergeable(const SequenceNumber& s) const
{ 
    if (contains(s) || start - s == 1) {
        return true;
    } else {
        return false;
    }
}

void SequenceSet::Range::encode(Buffer& buffer) const
{
    buffer.putLong(start);
    buffer.putLong(end);
}

SequenceSet::Range::Range(SequenceNumber s, SequenceNumber e) : start(s), end(e) {}

namespace qpid{
namespace framing{

std::ostream& operator<<(std::ostream& out, const SequenceSet& set) {
    out << "{";
    for (SequenceSet::Ranges::const_iterator i = set.ranges.begin(); i != set.ranges.end(); i++) {
        if (i != set.ranges.begin()) out << ", "; 
        out << i->start.getValue() << "-" << i->end.getValue();
    }
    out << "}";
    return out;
}

}
}
