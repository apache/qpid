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

#include <qpid/client/Connection.h>
#include <qpid/client/Session.h>
#include <qpid/client/AsyncSession.h>
#include <qpid/Url.h>
#include <qpid/framing/reply_exceptions.h>
#include <sstream>

using namespace qpid::client;
using namespace std;

int main(int argc, char** argv) {
    if (argc != 2) {
        cerr << "Expecing URL of broker as argument" << endl;
        exit(1);
    }
    try {
        // We need to create a large number of queues quickly, so we
        // use the old API for it's asynchronous commands.
        // The qpid::messaging API does not allow async queue creation.
        //
        Connection c;
        c.open(qpid::Url(argv[1]));
        AsyncSession s = async(c.newSession());
        // Generate too many queues, make sure we get an exception.
        for (uint64_t i = 0; i < 100000; ++i) {
            ostringstream os;
            os << "q" << i;
            string q = os.str();
            s.queueDeclare(q, arg::sync=false);
            if (i && i % 1000 == 0) {
                s.sync();       // Check for exceptions.
                cout << "Declared " << q << endl;
            }
        }
        cout << "Expected resource-limit-exceeded exception" << endl;
        return 1;
    }
    catch (const qpid::framing::ResourceLimitExceededException& e) {
        cout << "Resource limit exceeded: " << e.what() << endl;
        return 0;
    }
    catch (const std::exception& e) {
        cout << "Error: " << e.what() << endl;
        return 1;
    }
}
