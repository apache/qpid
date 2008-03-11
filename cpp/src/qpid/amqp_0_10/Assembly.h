#ifndef QPID_AMQP_0_10_ASSEMBLY_H
#define QPID_AMQP_0_10_ASSEMBLY_H

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

#include "qpid/amqp_0_10/CommandHolder.h"
#include "qpid/amqp_0_10/ControlHolder.h"
#include "Segment.h"
#include <boost/optional.hpp>

namespace qpid {
namespace amqp_0_10 {

// FIXME aconway 2008-03-06: TODO
struct Header {
    template <class S> void serialize(S&) {}
};

class Assembly
{
  public:
    enum SegmentIndex { ACTION_SEG, HEADER_SEG, BODY_SEG };
    
    Assembly() {}
    Assembly(const Command& c);
    Assembly(const Control& c);

    Segment& getSegment(int i) { return segments[i]; }
    const Segment& getSegment(int i) const { return segments[i]; }
    
    const Command* getCommand() const { return command ? command->get() : 0; }
    const Control* getControl() const { return control ? control->get() : 0; }
    const Header* getHeader() const { return header.get_ptr(); }

    void setCommand(const Command& c) { *command = c; }
    void setControl(const Control& c) { *control = c; }
    void setHeader(const Header& h) { header = h; }
    
    void add(const Frame& f);

    bool isComplete() const { return segments[BODY_SEG].isComplete(); }
    
  private:
    Segment segments[3];
    boost::optional<Command::Holder> command;
    boost::optional<Control::Holder> control;
    boost::optional<Header> header;
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_ASSEMBLY_H*/
