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
use Data::Dumper;

use cqpid_perl;

my $broker            = ( @ARGV > 0 ) ? $ARGV[0] : "localhost:5672";
my $address           = ( @ARGV > 1 ) ? $ARGV[0] : "amq.topic";
my $connectionOptions = ( @ARGV > 2 ) ? $ARGV[1] : "";

my $connection = new cqpid_perl::Connection($broker, $connectionOptions);

eval {
    $connection->open();
    my $session = $connection->createSession();

    my $receiver = $session->createReceiver($address);
    my $sender   = $session->createSender($address);

    $sender->send(new cqpid_perl::Message("Hello world!"));

    #my $duration = new cqpid_perl::Duration(1000);
    #print ">>>" . $duration->getMilliseconds() . "\n";

    my $message = $receiver->fetch($cqpid_perl::Duration::SECOND);

    #$message->setDurable(1);
    #print "Durable: " . $message->getDurable() . "\n";
    #print Dumper($message->getProperties());

    print $message->getContent() . "\n";
    $session->acknowledge();

    $connection->close();
};

die $@ if ($@);
