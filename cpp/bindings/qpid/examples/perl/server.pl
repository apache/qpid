#!/usr/bin/env perl
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
use strict;
use warnings;

use qpid;

my $url               = ( @ARGV == 1 ) ? $ARGV[0] : "amqp:tcp:127.0.0.1:5672";
my $connectionOptions = ( @ARGV > 1 )  ? $ARGV[1] : "";

# create a connection object
my $connection = new qpid::messaging::Connection( $url, $connectionOptions );

eval {

    # connect to the broker and create a session
    $connection->open();
    my $session = $connection->create_session();

    # create a receiver for accepting incoming messages
    my $receiver = $session->create_receiver("service_queue; {create: always}");

    # go into an infinite loop to receive messages and process them
    while (1) {

        # wait for the next message to be processed
        my $request = $receiver->fetch();


        # get the address for sending replies
        # if no address was supplised then we can't really respond, so
        # only process when one is present
        my $address = $request->get_reply_to();
        if ($address) {

            # a temporary sender for sending to the response queue
            my $sender = $session->create_sender($address);
            my $s      = $request->get_content();
            $s = uc($s);

            # create the response message and send it
            my $response = new qpid::messaging::Message($s);
            $sender->send($response);
            print "Processed request: "
              . $request->get_content() . " -> "
              . $response->get_content() . "\n";

            # acknowledge the message since it was processed
            $session->acknowledge();
        }
        else {
            print "Error: no reply address specified for request: "
              . $request->get_content() . "\n";
            $session->reject($request);
        }
    }

    # close connections to clean up
    $session->close();
    $connection->close();
};

if ($@) {
    die $@;
}

