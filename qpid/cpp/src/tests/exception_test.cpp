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

#include <iostream>

#include "TestOptions.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Message.h"

using namespace qpid::client;
using namespace qpid::sys;
using std::string;

int main(int argc, char** argv)
{
    qpid::TestOptions opts;
    opts.parse(argc, argv);

    try {
	Connection con(opts.trace);
	con.open(opts.host, opts.port, opts.username, opts.password, opts.virtualhost);

        Queue queue("I don't exist!");
        Channel channel;      
        con.openChannel(channel);
        channel.start();
        //test handling of get (which is a bit odd)
        try {
            Message msg;
            if (channel.get(msg, queue)) {
                std::cout << "Received " << msg.getData() << " from " << queue.getName() << std::endl;
            } else {
                std::cout << "Queue " << queue.getName() << " was empty." << std::endl;
            }
            con.close();
            return 1;
        } catch (const qpid::ChannelException& e) {
            std::cout << "get failed as expected: " << e.what() << std::endl;
        }

        con.close();
        return 0;
    } catch(const std::exception& e) {
	std::cout << "got unexpected exception: " << e.what() << std::endl;
        return 1;
    }
}
