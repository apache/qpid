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

use cqpid_perl;
use Getopt::Long;

my $url = "127.0.0.1";
my $timeout = 60;
my $forever = 0;
my $count   = 1;
my $connectionOptions = "";
my $address = "amq.direct";

my $result = GetOptions(
    "broker|b=s"           => \ $url,
    "timeout|t=i"          => \ $timeout,
    "forever|f"            => \ $forever,
    "connection-options=s" => \ $connectionOptions,
    "count|c=i"            => \ $count,
);

if (! $result) {
    print "Usage: perl drain.pl [OPTIONS]\n";
}

if ($#ARGV ge 0) {
    $address = $ARGV[0]
}

sub getTimeout {
   return ($forever) ? $cqpid_perl::Duration::FOREVER : new cqpid_perl::Duration($timeout*1000);
}


my $connection = new cqpid_perl::Connection($url, $connectionOptions);

eval {
    $connection->open();
    my $session  = $connection->createSession();
    my $receiver = $session->createReceiver($address);
    my $timeout  = getTimeout();

    my $message = new cqpid_perl::Message();
    my $i = 0;

    while($receiver->fetch($message, $timeout)) {
        print "Message(properties=" . $message->getProperties() . ",content='";
        if ($message->getContentType() eq "amqp/map") {
            my $content = cqpid_perl::decodeMap($message);
            map{ print "\n$_ => $content->{$_}"; } keys %{$content};
        }
        else {
            print $message->getContent();
        }
        print "')\n";
       
        my $replyto = $message->getReplyTo();
        if ($replyto->getName()) {
            print "Replying to " . $message->getReplyTo()->str() . "...\n";
            my $sender = $session->createSender($replyto);
            my $response = new cqpid_perl::Message("received by the server.");
            $sender->send($response);
        }
        $session->acknowledge();

        if ($count and (++$i ==$count)) {
            last;
        }
    }
    $receiver->close();
    $session->close();
    $connection->close();
};

if ($@) {
  $connection->close();
  die $@;
}

