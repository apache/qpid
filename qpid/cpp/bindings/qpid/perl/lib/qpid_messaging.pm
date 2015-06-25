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

package qpid::messaging;

use qpid::messaging;
use qpid::messaging::Address;
use qpid::messaging::Duration;
use qpid::messaging::Message;
use qpid::messaging::Receiver;
use qpid::messaging::Sender;
use qpid::messaging::Session;
use qpid::messaging::Connection;

package qpid_messaging;

1;

__END__

=pod

=head1 NAME

qpid::messaging

=head1 DESCRIPTION

The Qpid Messaging framework is an enterprise messaging framework
based on the open-source AMQP protocol.

=head1 EXAMPLE

Here is a simple example application. It creates a link to a broker located
on a system named C<broker.myqpiddomain.com>. It then creates a new messaging
queue named C<qpid-examples> and publishes a message to it. It then consumes
that same message and closes the connection.

   use strict;
   use warnings;
   
   use qpid;
   
   # create a connection, open it and then create a session named "session1"
   my $conn = new qpid::messaging::Connection("broker.myqpiddomain.com");
   $conn->open();
   my $session = $conn->create_session("session1");
   
   # create a sender and a receiver
   # the sender marks the queue as one that is deleted when the sender disconnects
   my $send = $session->create_sender("qpid-examples;{create:always}");
   my $recv = $session->create_receiver("qpid-examples");
   
   # create an outgoing message and send it
   my $outgoing = new qpid::messaging::Message();
   $outgoing->set_content("The time is " . localtime(time)");
   $send->send($outgoing);
   
   # set the receiver's capacity to 10 and then check out many messages are pending
   $recv->set_capacity(10);
   print "There are " . $recv->get_available . " messages waiting.\n";
   
   # get the nextwaitingmessage, which should be in the local queue now,
   # and output the contents
   my $incoming = $recv->fetch();
   print "Received the following message: " . $incoming->get_content() . "\n";
   # the output should be the text that was sent earlier
   
   # acknowledge the message, letting the sender know the message was received
   printf "The sender currently has " . $send->get_unsettled . " message(s) pending.\n";
   # should report 1 unsettled message
   $session->acknowledge(); # acknowledges all pending messages
   print "Now sender currently has " . $send->get_unsettled . " message(s) pending.\n";
   # should report 0 unsettled messages
   
   # close the connection
   $conn->close
