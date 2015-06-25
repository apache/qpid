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

#include <qpid/messaging/Address.h>
#include <qpid/messaging/Connection.h>
#include "qpid/messaging/Duration.h"
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Session.h>
#include <qpid/Msg.h>
#include <qpid/Options.h>
#include <qpid/types/Uuid.h>
#include <string>
#include <iostream>

using namespace std;
using namespace qpid::messaging;
using qpid::types::Uuid;

namespace {

struct PingOptions : public qpid::Options {
    string url;
    string address;
    string message;
    string connectionOptions;
    double timeout;             // Timeout in seconds.
    bool quiet;                 // No output

    PingOptions() :
        url("127.0.0.1"),
        address(Uuid(true).str()+";{create:always}"),
        message(Uuid(true).str()),
        timeout(1),
        quiet(false)
    {
        using qpid::optValue;
        addOptions()
            ("broker,b", qpid::optValue(url, "URL"), "url of broker to connect to.")
            ("address,a", qpid::optValue(address, "ADDRESS"), "address to use.")
            ("message,m", optValue(message, "MESSAGE"), "message text to send.")
            ("connection-options", optValue(connectionOptions, "OPTIONS"), "options for the connection.")
            ("timeout,t", optValue(timeout, "SECONDS"), "Max time to wait.")
            ("quiet,q", optValue(quiet), "Don't print anything to stderr/stdout.");
    }
};

} // namespace

int main(int argc, char** argv) {
    Connection connection;
    try {
        PingOptions opts;
        opts.parse(argc, argv);
        connection = Connection(opts.url, opts.connectionOptions);
        connection.open();
        if (!opts.quiet) cout << "Opened connection." << endl;
        Session s = connection.createSession();
        s.createSender(opts.address).send(Message(opts.message));
        if (!opts.quiet) cout << "Sent message." << endl;
        Message m = s.createReceiver(opts.address).
            fetch(Duration(uint64_t(opts.timeout*1000)));
        if (m.getContent() != opts.message)
            throw qpid::Exception(qpid::Msg() << "Expected " << opts.message
                                  << " but received " << m.getContent());
        if (!opts.quiet) cout << "Received message." << endl;
        connection.close();
        return 0;
    } catch (const exception& e) {
        cerr << "Error: " << e.what() << endl;
        connection.close();
        return 1;
    }
}
