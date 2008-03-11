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
#include "Assembly.h"
#include "qpid/Exception.h"
#include "Codec.h"
#include "qpid/framing/Blob.h"

namespace qpid {
namespace amqp_0_10 {

using framing::in_place;

Assembly::Assembly(const Command& c) : command(in_place<Command::Holder>(c)) {}
Assembly::Assembly(const Control& c) : control(in_place<Control::Holder>(c)) {}

void Assembly::add(const Frame& f) {
    switch (f.getType()) {
      case COMMAND: {
          Segment& s = segments[ACTION_SEG];
          s.add(f);
          if (s.isComplete()) {
              command = in_place<Command::Holder>();
              Codec::decode(s.begin())(*command);
              if (f.testFlags(Frame::LAST_SEGMENT)) {
                  segments[HEADER_SEG].setMissing();
                  segments[BODY_SEG].setMissing();
              }
          }
          break;
      }
      case CONTROL: {
          Segment& s = segments[ACTION_SEG];
          s.add(f);
          if (s.isComplete()) {
              control = in_place<Control::Holder>();
              Codec::decode(s.begin())(*control);
              if (f.testFlags(Frame::LAST_SEGMENT)) {
                  segments[HEADER_SEG].setMissing();
                  segments[BODY_SEG].setMissing();
              }
          }
          break;
      }
      case HEADER: {
          Segment& s = segments[HEADER_SEG];
          s.add(f);
          if (s.isComplete()) {
              header = in_place<Header>();
              Codec::decode(*header);
              if (f.testFlags(Frame::LAST_SEGMENT)) {
                  segments[BODY_SEG].setMissing();
              }
          }
          break;
      }
      case BODY: {
          Segment& s = segments[BODY_SEG];
          s.add(f);
          break;
      }
    }
}

}} // namespace qpid::amqp_0_10
