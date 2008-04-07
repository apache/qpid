#ifndef QPID_AMQP_0_10_HEADER_H
#define QPID_AMQP_0_10_HEADER_H

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
#include <ostream>

namespace qpid {
namespace amqp_0_10 {

// FIXME aconway 2008-03-27: TODO
class Header
{
  public:
    template <class S> void serialize(S&) {}
  private:
};

// FIXME aconway 2008-03-28: TODO
inline std::ostream& operator<<(std::ostream& o, const Header&) { return o; }

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_HEADER_H*/
