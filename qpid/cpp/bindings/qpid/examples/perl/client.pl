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

# creates a new connection instance
my $connection = new qpid::messaging::Connection( $url, $connectionOptions );

eval {
    # open the connection and create a session for interacting with it
    $connection->open();

    my $session = $connection->create_session();
    my $sender  = $session->create_sender("service_queue");

    # create an address and receiver for incoming messages
    # the queue will be created always, and will be deleted
    # when the receive disconnects
    my $receiver = $session->create_receiver("#");
    my $responseQueue = $receiver->get_address();
    # Now send some messages...

    my @s = (
        "Twas brillig, and the slithy toves",
        "Did gire and gymble in the wabe.",
        "All mimsy were the borogroves,",
        "And the mome raths outgrabe."
    );

    # create the message object, and set a reply-to address
    # so that the server knows where to send responses
    # the message object will be reused to send each line
    my $request = new qpid::messaging::Message();
    $request->set_reply_to($responseQueue);
    for ( my $i = 0 ; $i < 4 ; $i++ ) {
        $request->set_content( $s[$i] );
        $sender->send($request);

        # wait for the response to the last line sent
        # the message will be taken directly from the
        # broker's queue rather than waiting for it
        # to be queued locally
        my $response = $receiver->fetch();
        print $request->get_content() . " -> "
          . $response->get_content() . "\n";
    }

    # close the connection
    $connection->close();
};

if ($@) {
    die $@;
}
