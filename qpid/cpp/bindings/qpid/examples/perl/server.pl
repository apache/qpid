#!/usr/bin/perl
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

use cqpid;

my $url = ( @ARGV == 1 ) ? $ARGV[0] : "amqp:tcp:127.0.0.1:5672";
my $connectionOptions =  ( @ARGV > 1 ) ? $ARGV[1] : ""; 


my $connection = new cqpid::Connection($url, $connectionOptions);

eval {
    $connection->open();
    my $session = $connection->createSession();

    my $receiver = $session->createReceiver("service_queue; {create: always}");

    while (1) {
        my $request = $receiver->fetch();
        my $address = $request->getReplyTo();
        if ($address) {
            my $sender = $session->createSender($address);
            my $s = $request->getContent();
            $s = uc($s);
            my $response = new cqpid::Message($s);
            $sender->send($response);
            print "Processed request: " . $request->getContent() . " -> " . $response->getContent() . "\n";
            $session->acknowledge();
        }
        else {
            print "Error: no reply address specified for request: " . $request->getContent() . "\n";
            $session->reject($request);
        }
    }

$connection->close();
};

if ($@) {
    die $@;
}


