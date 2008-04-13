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
#include "FrameList.h"
#include "Exception.h"

namespace qpid {
namespace framing {

FrameList::~FrameList()
{
    for (Frames::iterator i = frames.begin(); i != frames.end(); i++) {
        delete (*i);
    }    
}

void FrameList::encode(Buffer& buffer)
{
    for (Frames::iterator i = frames.begin(); i != frames.end(); i++) {
        (*i)->encode(buffer);
    }    
}

bool FrameList::decode(Buffer&)
{
    throw Exception("FrameList::decode() not valid!");
}

u_int32_t FrameList::size() const
{
    uint32_t s(0);
    for (Frames::const_iterator i = frames.begin(); i != frames.end(); i++) {
        s += (*i)->size();
    }
    return s;
}

void FrameList::print(std::ostream& out) const
{
    out << "Frames: ";
    for (Frames::const_iterator i = frames.begin(); i != frames.end(); i++) {
        (*i)->print(out);
        out << "; ";
    }    
}

void FrameList::add(AMQFrame* f)
{
    frames.push_back(f);
}

}}
