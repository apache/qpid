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
use Data::Dumper;

use qpid;

my $broker            = ( @ARGV > 0 ) ? $ARGV[0] : "localhost:5672";
my $address           = ( @ARGV > 1 ) ? $ARGV[1] : "amq.topic";
my $connectionOptions = ( @ARGV > 2 ) ? $ARGV[2] : "";

# create a connection
my $connection = new qpid::messaging::Connection( $broker, $connectionOptions );

eval {
    # open the connection and create a session, and both a sender a receive
    $connection->open();

    my $session = $connection->create_session();

    my $receiver = $session->create_receiver($address);
    my $sender   = $session->create_sender($address);

    # send a simple message
    $sender->send( new qpid::messaging::Message("Hello world!") );

    # receive the message, fetching it directly from the broker
    my $message = $receiver->fetch(qpid::messaging::Duration::SECOND, 1);

    # output the message content, then acknowledge it
    print $message->get_content() . "\n";
    $session->acknowledge();

    # close the connection
    $connection->close();
};

die $@ if ($@);
