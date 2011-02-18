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
use Time::Local;

my $url = "127.0.0.1";
my $timeout = 0;
my $count   = 1;
my $id      = "";
my $replyto = "";
my @properties;
my @entries;
my $content = "";
my $connectionOptions = "";
my $address = "amq.direct";

my $result = GetOptions(
    "broker|b=s"           => \ $url,
    "timeout|t=i"          => \ $timeout,
    "count|c=i"            => \ $count,
    "id|i=s"               => \ $id,
    "replyto=s"            => \ $replyto,
    "property|p=s@"        => \ @properties,
    "map|m=s@"             => \ @entries,
    "content=s"            => \ $content,
    "connection-options=s" => \ $connectionOptions,   
);


if (! $result) {
    print "Usage: perl drain.pl [OPTIONS]\n";
}


if ($#ARGV ge 0) {
    $address = $ARGV[0]
}


sub setEntries {
    my ($content) = @_;

    foreach (@entries) {
        my ($name, $value) = split("=", $_);
        $content->{$name} = $value;
    }
}


sub setProperties {
    my ($message) = @_;

    foreach (@properties) {
        my ($name, $value) = split("=", $_);
        $message->getProperties()->{$name} = $value;
    }
}

my $connection = new cqpid_perl::Connection($url, $connectionOptions);

eval {
    $connection->open();
    my $session  = $connection->createSession();
    my $sender = $session->createSender($address);

    my $message = new cqpid_perl::Message();
    setProperties($message) if (@properties);
    if (@entries) {
        my $content = {};
        setEntries($content);
        cqpid_perl::encode($content, $message);
    }
    elsif ($content) {
        $message->setContent($content);
        $message->setContentType("text/plain");
    }

    my $receiver;
    if ($replyto) {
        my $responseQueue = new cqpid_perl::Address($replyto);
        $receiver = $session->createReceiver($responseQueue);
        $message->setReplyTo($responseQueue);
    }

    my $start = localtime;
    my @s = split(/[:\s]/, $start);
    my $s = "$s[3]$s[4]$s[5]";
    my $n = $s;

    for (my $i = 0; 
        ($i < $count || $count == 0) and
        ($timeout == 0 || abs($n - $s) < $timeout); 
        $i++) {

        $sender->send($message);

        if ($receiver) {
            my $response = $receiver->fetch();
            print "$i -> " . $response->getContent() . "\n";
        }

        my $now = localtime;
        my @n = split(/[:\s]/, $now);
        my $n = "$n[3]$n[4]$n[5]";
    }
    $session->sync();
    $connection->close();
};

if ($@) {
  $connection->close();
  die $@;
}


