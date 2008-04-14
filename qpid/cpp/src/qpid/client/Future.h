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

#ifndef _Future_
#define _Future_

#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>
#include "qpid/Exception.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/StructHelper.h"
#include "FutureCompletion.h"
#include "FutureResponse.h"
#include "FutureResult.h"
#include "SessionCore.h"

namespace qpid {
namespace client {

class Future : private framing::StructHelper
{
    framing::SequenceNumber command;
    boost::shared_ptr<FutureResponse> response;
    boost::shared_ptr<FutureResult> result;
    bool complete;

public:
    Future() : complete(false) {}    
    Future(const framing::SequenceNumber& id) : command(id), complete(false) {}    

    void sync(SessionCore& session)
    {
        if (!isComplete(session)) {
            session.getExecution().syncTo(command);
            wait(session);
        }
    }

    void wait(SessionCore& session)
    {
        if (!isComplete(session)) {
            FutureCompletion callback;
            session.getExecution().getCompletionTracker().listenForCompletion(
                command,                                                     
                boost::bind(&FutureCompletion::completed, &callback)
            );
            callback.waitForCompletion();
            session.assertOpen();
            complete = true;
        }
    }

    framing::AMQMethodBody* getResponse(SessionCore& session) 
    {
        if (response) {
            session.getExecution().getCompletionTracker().listenForCompletion(
                command,                                                     
                boost::bind(&FutureResponse::completed, response)
            );            
            return response->getResponse(session);
        } else {
            throw Exception("Response not expected");
        }
    }

    template <class T> void decodeResult(T& value, SessionCore& session) 
    {
        if (result) {
            decode(value, result->getResult(session));
        } else {
            throw Exception("Result not expected");
        }
    }

    bool isComplete(SessionCore& session) {
        return complete || session.getExecution().isComplete(command);
    }

    bool isCompleteUpTo(SessionCore& session) {
        return complete || session.getExecution().isCompleteUpTo(command);
    }

    void setCommandId(const framing::SequenceNumber& id) { command = id; }
    void setFutureResponse(boost::shared_ptr<FutureResponse> r) { response = r; }
    void setFutureResult(boost::shared_ptr<FutureResult> r) { result = r; }
};

}}

#endif
