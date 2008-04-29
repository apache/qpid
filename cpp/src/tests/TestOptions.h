#ifndef _TestOptions_
#define _TestOptions_
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

#include "qpid/Options.h"
#include "qpid/log/Options.h"
#include "qpid/Url.h"
#include "qpid/log/Logger.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionSettings.h"

#include <iostream>
#include <exception>

namespace qpid {

struct TestOptions : public qpid::Options
{
    TestOptions(const std::string& helpText_=std::string()) :
        Options("Test Options"), help(false), helpText(helpText_)
    {
        addOptions()
            ("help", optValue(help), "print this usage statement");
        add(con);
    }

    /** As well as parsing, throw help message if requested. */
    void parse(int argc, char** argv) {
        try {
            qpid::Options::parse(argc, argv);
        } catch (const std::exception& e) {
            std::ostringstream msg;
            msg << *this << std::endl << std::endl << e.what() << std::endl;
            throw qpid::Options::Exception(msg.str());
        }
        qpid::log::Logger::instance().configure(con.log, argv[0]);
        if (help) {
            std::ostringstream msg;
            msg << *this << std::endl << std::endl << helpText << std::endl;
            throw qpid::Options::Exception(msg.str());
        }
    }

    /** Open a connection using option values */
    void open(qpid::client::Connection& connection) {
        connection.open(con);
    }

    
    bool help;
    client::ConnectionSettings con;
    std::string helpText;
};

}

#endif
