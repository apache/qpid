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

=pod

=head1 NAME

qpid::messaging::Sender

=head1 DESCRIPTION

A B<qpid::messaging::Sender> is the entity through which messages are sent.

An instance can only be created using an active (i.e., not previously closed)
B<qpid::messaging::Session>.

=head1 EXAMPLE

   # create a connection and a session
   my $conn = new qpid::messaging::Connection("mybroker:5672");
   conn->open;
   my $session = $conn->create_session;
   
   # create a sender that posts messages to the "updates" queue
   my $sender = $session->create_sender "updates;{create:always}"
   
   # begin sending updates
   while( 1 ) {
     my $content = wait_for_event;
     $sender->send(new qpid::messaging::Message($content));
   }

=cut

package qpid::messaging::Sender;

sub new {
    my ($class) = @_;
    my ($self) = {
        _impl => $_[1],
        _session => $_[2],
    };

    die "Must provide an implementation." unless defined($self->{_impl});
    die "Must provide a Session." unless defined($self->{_session});

    bless $self, $class;
    return $self;
}

=pod

=head1 ACTIONS

=cut


=pod

=head2 SENDING MESSAGES

=over

=item $sender->send( message )

=item $sender->send( message, block)

Sends a message, optionally blocking until the message is received by
the broker.

=back

=head3 ARGUMENTS

=over

=item * message

The message to be sent.

=item * block

If true then blocks until the message is received.

=back

=cut
sub send {
    my ($self) = @_;
    my $message = $_[1];
    my $sync = $_[2] || 0;

    die "No message to send." unless defined($message);

    my $impl = $self->{_impl};

    $impl->send($message->get_implementation, $sync);
}

=pod

=head2 CLOSING THE SENDER

=item sender->close

Closes the sender.

This does not affect the ownering B<Session> or B<Connection>

=back

=cut
sub close {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->close;
}

=pod

=head1 ATTRIBUTES

=cut

=pod

=head2 CAPACITY

The capacity is the number of outoing messages that can be held pending
confirmation of receipt by the broker.

=over

=item sender->set_capacity( size )

=item $size = sender->get_capacity

=back

=back

=cut
sub set_capacity {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setCapacity($_[1]);
}

sub get_capacity {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getCapacity;
}

=pod

=head2 UNSETTLED

The number of messages sent that are pending receipt confirmation by the broker.

=over

=item $count = sender->get_unsettled

=back

=cut
sub get_unsettled {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettled;
}

=pod

=head2 AVAILABLE

The available slots for sending messages.

This differences form B<capacity> in that it is the available slots in the
senders capacity for holding outgoing messages. The difference between
capacity and available is the number of messages that have no been delivered
yet.

=over

=item $slots = sender->get_available

=back

=cut
sub get_available {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAvailable();
}

=pod

=head2 NAME

The human-readable name for this sender.

=over

=item $name = sender-get_name

=back

=cut
sub get_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getName;
}

=pod

=head2 SESSION

The owning session from which the sender was created.

=over

=item $session = $sender->get_session

=back

=cut
sub get_session {
    my ($self) = @_;

    return $self->{_session};
}

=pod

=head2 ADDRESS

Returns the address for this sender.

=over

=item $address = $sender->get_address

=back

=cut

sub get_address {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $address = $impl->getAddress;

    return new qpid::messaging::Address($address);
}

1;
