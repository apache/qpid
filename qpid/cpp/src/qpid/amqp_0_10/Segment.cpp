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
#include "Segment.h"
#include "qpid/Exception.h"
#include <boost/ref.hpp>
#include <numeric>

namespace qpid {
namespace amqp_0_10 {

Segment::Segment() : missing() {}

bool Segment::isComplete() const {
    return missing || (!frames.empty() && (frames.back().testFlags(Frame::LAST_FRAME)));
}

Segment::const_iterator Segment::begin() const {
    return Segment::const_iterator(frames, frames.begin(), frames.begin()->begin());
}

Segment::const_iterator Segment::end() const {
    return const_iterator(frames, frames.end(), frames.end()->end());
}

namespace {
Frame::Flags segFlags(const Frame& f) {
    return f.testFlags(Frame::FIRST_SEGMENT | Frame::LAST_SEGMENT);
}
} // namespace

void Segment::add(const Frame& frame) {
    // FIXME aconway 2008-03-07:  ex types & messages.
    if (isComplete()) throw Exception("cannot add frame to complete segment");
    if (!frames.empty()) {
        if (frame.testFlags(Frame::FIRST_FRAME) ||
            frame.getType() != frames.front().getType() ||
            segFlags(frames.front()) != segFlags(frame))
            throw Exception("invalid frame");
    }
    frames.push_back(frame);
}

bool Segment::isFirst() const {
    return !frames.empty() && frames.front().testFlags(Frame::FIRST_SEGMENT);
}

bool Segment::isLast() const {
    return !frames.empty() && frames.front().testFlags(Frame::LAST_SEGMENT);
}

namespace {
size_t accumulate_size(size_t total, const Frame& f) {
    return total+f.size();
}
}

size_t Segment::size() const {
    return std::accumulate(frames.begin(), frames.end(), 0, &accumulate_size);
}

}} // namespace qpid::amqp_0_10


