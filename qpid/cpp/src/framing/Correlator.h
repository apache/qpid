#ifndef _framing_Correlator_h
#define _framing_Correlator_h

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

#include "../shared_ptr.h"
#include "../framing/AMQResponseBody.h"
#include <boost/function.hpp>
#include <map>

namespace qpid {
namespace framing {

/**
 * Correlate responses with actions established when sending the request.
 *
 * THREAD  UNSAFE.
 */
class Correlator
{
  public:
    typedef shared_ptr<AMQResponseBody> ResponsePtr;
    typedef boost::function<void (ResponsePtr)> Action;
    
    /**
     * Note that request with id was sent, record an action to call
     * when a response arrives.
     */
    void request(RequestId id, Action doOnResponse);

    /**
     * Note response received, call action for associated request if any.
     * Return true of some action(s) were executed.
     */
    bool response(shared_ptr<AMQResponseBody>);

    /**
     * Note the given execution mark was received, call actions
     * for any requests that are impicitly responded to.
     */
    void mark(RequestId mark);

  private:
    typedef std::map<RequestId, Action> Actions;
    Actions actions;
};

}} // namespace qpid::framing



#endif  /*!_framing_Correlator_h*/
