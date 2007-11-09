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

#include "FrameSet.h"
#include "qpid/framing/all_method_bodies.h"
#include "qpid/framing/frame_functors.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/TypeFilter.h"

using namespace qpid::framing;
using namespace boost;

FrameSet::FrameSet(const SequenceNumber& _id) : id(_id) {}

void FrameSet::append(AMQFrame& part)
{
    parts.push_back(part);
}

bool FrameSet::isComplete() const
{
    return !parts.empty() && parts.back().getEof() && parts.back().getEos();
}

bool FrameSet::isContentBearing() const
{
    const AMQMethodBody* method = getMethod();
    return method && method->isContentBearing();
}

const AMQMethodBody* FrameSet::getMethod() const
{
    return parts.empty() ? 0 : parts[0].getMethod();
}

const AMQHeaderBody* FrameSet::getHeaders() const
{
    return parts.size() < 2 ? 0 : parts[1].castBody<AMQHeaderBody>();
}

AMQHeaderBody* FrameSet::getHeaders()
{
    return parts.size() < 2 ? 0 : parts[1].castBody<AMQHeaderBody>();
}

uint64_t FrameSet::getContentSize() const
{
    SumBodySize sum;
    map_if(sum, TypeFilter(CONTENT_BODY));
    return sum.getSize();
}

void FrameSet::getContent(std::string& out) const
{
    AccumulateContent accumulator(out);
    map_if(accumulator, TypeFilter(CONTENT_BODY));
}

std::string FrameSet::getContent() const
{
    std::string out;
    getContent(out);
    return out;
}
