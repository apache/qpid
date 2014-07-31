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

qpid::messaging::Receiver

=head1 DESCRIPTION

A B<qpid::messaging::Receiver> is the entity though which messages are received.

An instance can only be created using an active (i.e., not previously closed)
B<qpid::messaging::Session>.

=head1 EXAMPLE

   # create a connection and a session
   my $conn = new qpid::messaging::Connection("mybroker:5672");
   conn->open;
   my $session = $conn->create_session;
   
   # create a receiver that listens on the "updates" topic of "alerts"
   my $recv = $session->create_receiver("alerts/updates");
   
   # set the local queue size to hold a maximum of 100 messages
   $recv->set_capacity(100);
   
   # wait for an incoming message and process it
   my $incoming = $recv->get;
   process($incoming)

=cut

package qpid::messaging::Receiver;

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

There are two ways to retrieve messages: from the local queue or from the
remote queue.

=head2 GETTING FROM THE LOCAL QUEUE

Messages can be held locally in message queues.

=over

=item $incoming = $receiver->get

=item $incoming = $receiver->get( timeout)

=back

=head3 ARGUMENTS

=over

=item * timeout

The period of time to wait for a message before raising an exception. If no
period of time is specified then the default is to wait B<forever>.

=back

=cut
sub get {
    my ($self) = @_;
    my $duration = $_[1];
    my $impl = $self->{_impl};

    $duration = $duration->get_implementation() if defined($duration);

    my $message = undef;

    if (defined($duration)) {
        $message = $impl->get($duration);
    } else {
        $message = $impl->get;
    }

    return new qpid::messaging::Message(undef, $message);
}

=pod

=head2 FETCHING FROM THE REMOTE QUEUE

Messages held in the remote queue must be fetched from the broker in order
to be processed.

=over

=item $incoming = $receiver->fetch

=item $incoming = $receiver->fetch( time )

=back

=head3 ARGUMENTS

=over

=item * timeout

The period of time to wait for a message before raising an exception. If no
period of time is specified then the default is to wait B<forever>.

=back

=cut
sub fetch {
    my ($self) = @_;
    my $duration = $_[1];
    my $impl = $self->{_impl};
    my $message = undef;

    if (defined($duration)) {
        $message = $impl->fetch($duration->get_implementation());
    } else {
        $message = $impl->fetch;
    }

    return new qpid::messaging::Message("", $message);
}

=pod

=head2 CLOSING THE RECEIVER

=over

=item receiver->close

Closes the receiver.

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

The maximum number of messages that are prefected and held locally is
determined by the capacity of the receiver.

=over

=item $receiver->set_capacity( size )

=item $size = $receiver->get_capacity

=back

=cut
sub set_capacity {
    my ($self) = @_;
    my $capacity = $_[1];
    my $impl = $self->{_impl};

    $impl->setCapacity($capacity);
}

sub get_capacity {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getCapacity;
}

=pod

=head2 AVAILABLE

The number of messages waiting in the local queue.

The value is always in the range 0 <= B<available> <= B<capacity>.

=over

=item $count = $receiver->get_available

=back

=cut

sub get_available {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAvailable;
}

=pod

=over

=item $count = $receiver->get_unsettled

Returns the number of messages that have been received and acknowledged but
whose acknowledgements have not been confirmed by the sender.

=back

=cut
sub get_unsettled {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettled;
}

=pod

=over

=item $name = $receiver->get_name

Returns the name of the receiver.

=back

=cut
sub get_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getName;
}

=pod

=over

=item $session = $receiver->get_session

Returns the B<qpid::messaging::Session> instance from which this
receiver was created.

=back

=cut
sub get_session {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->{_session};
}

=pod

Returns the address for this receiver.

=over

=item $address = $receiver->get_address

=back

=cut

sub get_address {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $address = $impl->getAddress;

    return new qpid::messaging::Address($address);
}

=pod

=over

=item $receiver->is_closed

Returns whether the receiver is closed.

=back

=cut
sub is_closed {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->isClosed;
}

1;
