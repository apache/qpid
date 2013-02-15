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
use Getopt::Long;
use Pod::Usage;

my $url = "127.0.0.1";
my $timeout = 0;
my $forever = 0;
my $count   = 0;
my $connectionOptions = "";
my $address = "amq.direct";
my $help;

my $result = GetOptions(
    "broker|b=s"           => \ $url,
    "timeout|t=i"          => \ $timeout,
    "forever|f"            => \ $forever,
    "connection-options=s" => \ $connectionOptions,
    "count|c=i"            => \ $count,
    "help|h"               => \ $help
    ) || pod2usage(-verbose => 0);

pod2usage(-verbose => 1) if $help;

if ($#ARGV ge 0) {
    $address = $ARGV[0]
}

sub getTimeout {
   return ($forever) ? qpid::messaging::Duration::FOREVER : new qpid::messaging::Duration($timeout*1000);
}

sub printProperties {
  my $h = shift();
  return qq[{${\(join', ',map"'$_': '$h->{$_}'",keys%$h)}}]
}

my $connection = new qpid::messaging::Connection($url, $connectionOptions);

eval {
    $connection->open();
    my $session  = $connection->create_session();
    my $receiver = $session->create_receiver($address);
    my $timeout  = getTimeout();
    my $message = new qpid::messaging::Message();
    my $i = 0;

    for (;;) {
        eval {
            $message = $receiver->fetch($timeout);
        };

        if ($@) {
            last;
        }

        my $redelivered = ($message->get_redelivered) ? "redelivered=True, " : "";
        print "Message(" . $redelivered . "properties=" . printProperties($message->get_properties()) . ", content='";        
        if ($message->get_content_type() eq "amqp/map") {
            my $content = $message->get_content();
            map{ print "\n$_ => $content->{$_}"; } keys %{$content};
        }
        else {
            print $message->get_content();
        }
        print "')\n";

        my $replyto = $message->get_reply_to();
        if ($replyto->get_name()) {
            print "Replying to " . $message->get_reply_to()->str() . "...\n";
            my $sender = $session->create_sender($replyto);
            my $response = new qpid::messaging::Message("received by the server.");
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

__END__

=head1 NAME

drain - Drains messages from the specified address

=head1 SYNOPSIS

  Options:
  -h, --help                    show this message
  -b VALUE, --broker VALUE      url of broker to connect to
  -t VALUE, --timeout VALUE     timeout in seconds to wait before exiting
  -f, --forever                 ignore timeout and wait forever
  --connection-options VALUE    connection options string in the form {name1:value1, name2:value2}
  -c VALUE, --count VALUE       number of messages to read before exiting

=cut

