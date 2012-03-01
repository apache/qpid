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

my $broker            = ( @ARGV > 0 ) ? $ARGV[0] : "localhost:5672";
my $connectionOptions = ( @ARGV > 1 ) ? $ARGV[1] : "";

my $query = <<END;
    let \$w := ./weather
    return \$w/station = 'Raleigh-Durham International Airport (KRDU)'
    and \$w/temperature_f > 50
    and \$w/temperature_f - \$w/dewpoint > 5
    and \$w/wind_speed_mph > 7
    and \$w/wind_speed_mph < 20
END

my $address = <<END;
xml-exchange; {
create: always,      
node: { type: topic, x-declare: { type: xml } },
link: {
x-bindings: [{ exchange: xml-exchange, key: weather, arguments: { xquery:" $query" } }]
}}
END


my $connection = new cqpid_perl::Connection($broker, $connectionOptions);

eval {
    $connection->open();
    my $session = $connection->createSession();

    my $receiver = $session->createReceiver($address);
    
    my $message = new cqpid_perl::Message();

    my $content = <<END;
    <weather>
    <station>Raleigh-Durham International Airport (KRDU)</station>
    <wind_speed_mph>16</wind_speed_mph>
    <temperature_f>70</temperature_f>
    <dewpoint>35</dewpoint>
    </weather>
END
    
    $message->setContent($content);
    my $sender = $session->createSender('xml-exchange/weather');
    $sender->send($message);
    
    my $response = $receiver->fetch();
    print $response->getContent() . "\n";

    $connection->close();
};

die $@ if ($@);
