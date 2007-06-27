#ifndef QPID_BROKER_CHANNELINITIALIZER_H
#define QPID_BROKER_CHANNELINITIALIZER_H

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <boost/noncopyable.hpp>

namespace qpid {
namespace broker {

/**
 * A ChannelInitializer is called each time a new Channel is created.
 */
class ChannelInitializer : boost::noncopyable
{
  public:
    virtual ~ChannelInitializer() {}

    /** Called for each new channel */
    virtual initialize(Channe&) = 0;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_CHANNELINITIALIZER_H*/
