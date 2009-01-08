#ifndef _TestCase_
#define _TestCase_
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

#include "ConnectionOptions.h"
#include "qpid/client/Message.h"


namespace qpid {

/**
 * Interface to be implemented by test cases for use with the test
 * runner.
 */
class TestCase
{
public:
    /**
     * Directs the test case to act in a particular role. Some roles
     * may be 'activated' at this stage others may require an explicit
     * start request.
     */
    virtual void assign(const std::string& role, framing::FieldTable& params, client::ConnectionOptions& options) = 0;
    /**
     * Each test will be started on its own thread, which should block
     * until the test completes (this may or may not require an
     * explicit stop() request).
     */
    virtual void start() = 0;
    /**
     * Requests that the test be stopped if still running.
     */
    virtual void stop() = 0;
    /**
     * Allows the test to fill in details on the final report
     * message. Will be called only after start has returned.
     */
    virtual void report(client::Message& report) = 0;

    virtual ~TestCase() {}
};

}

#endif
