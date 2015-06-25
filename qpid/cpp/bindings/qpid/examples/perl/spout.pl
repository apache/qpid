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
use Time::Local;

my $url     = "127.0.0.1";
my $timeout = 0;
my $count   = 1;
my $durable = 0;
my $id      = "";
my $replyto = "";
my @properties;
my @entries;
my $content           = "";
my $connectionOptions = "";
my $address           = "amq.direct";
my $help;

my $result = GetOptions(
    "broker|b=s"           => \$url,
    "timeout|t=i"          => \$timeout,
    "count|c=i"            => \$count,
    "durable|d"            => \$durable,
    "id|i=s"               => \$id,
    "replyto=s"            => \$replyto,
    "property|p=s@"        => \@properties,
    "map|m=s@"             => \@entries,
    "content=s"            => \$content,
    "connection-options=s" => \$connectionOptions,
    "help|h"               => \$help
) || pod2usage( -verbose => 0 );

pod2usage( -verbose => 1 ) if $help;

if ( $#ARGV ge 0 ) {
    $address = $ARGV[0];
}

sub setEntries {
    my ($content) = @_;

    foreach (@entries) {
        my ( $name, $value ) = split( "=", $_ );
        $content->{$name} = $value;
    }
}

sub setProperties {
    my ($message) = @_;

    foreach (@properties) {
        my ( $name, $value ) = split( "=", $_ );
        $message->set_property( $name, $value );
    }
}

# create a connection object
my $connection = new qpid::messaging::Connection( $url, $connectionOptions );

eval {
    # open the connection, create a session and then a sender
    $connection->open();
    my $session = $connection->create_session();
    my $sender  = $session->create_sender($address);

    # create a message to be sent
    my $message = new qpid::messaging::Message();
    setProperties($message) if (@properties);
    if (@entries) {
        my $content = {};
        setEntries($content);
        $message->set_content_object($content);
    }
    elsif ($content) {
        $message->set_content($content);
        $message->set_content_type("text/plain");
    }

    # set durable flag
    $message->set_durable($durable);

    # if a reply-to address was supplied, then create a receiver from the
    # session and wait for a response to be sent
    my $receiver;
    if ($replyto) {
        my $responseQueue = new qpid::messaging::Address($replyto);
        $receiver = $session->create_receiver($responseQueue);
        $message->set_reply_to($responseQueue);
    }

    my $start = localtime;
    my @s     = split( /[:\s]/, $start );
    my $s     = "$s[3]$s[4]$s[5]";
    my $n     = $s;

    for (
        my $i = 0 ;
        ( $i < $count || $count == 0 )
          and ( $timeout == 0 || abs( $n - $s ) < $timeout ) ;
        $i++
      )
    {

        $sender->send($message);

        if ($receiver) {
            print "Waiting for a response.\n";
            my $response = $receiver->fetch();
            print "$i -> " . $response->get_content() . "\n";
        }

        my $now = localtime;
        my @n   = split( /[:\s]/, $now );
        my $n   = "$n[3]$n[4]$n[5]";
    }
    $session->sync();
    $connection->close();
};

if ($@) {
    $connection->close();
    die $@;
}

__END__

=head1 NAME

spout - Send messages to the specified address

=head1 SYNOPSIS

  Usage: spout [OPTIONS] ADDRESS

  Options:
  -h, --help                   show this message
  -b VALUE, --broker VALUE     url of broker to connect to
  -t VALUE, --timeout VALUE    exit after the specified time
  -c VALUE, --count VALUE      stop after count messageshave been sent, zero disables
  -i VALUE, --id VALUE         use the supplied id instead of generating one
  --replyto VALUE              specify reply-to value
  -P VALUE, --property VALUE   specify message property
  -M VALUE, --map VALUE        specify entry for map content
  --content VALUE              specify textual content
  --connection-options VALUE   connection options string in the form {name1:value1, name2:value2}

=cut
