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

qpid::messaging::Session

=head1 DESCRIPTION

A B<qpid::messaging::Session> represents a distinct conversation between end
points. They are created from an active (i.e, not closed) B<Connection>.

A session is used to acknowledge individual or all messages that have
passed through it, as well as for creating senders and receivers for conversing.
=cut
package qpid::messaging::Session;

sub new {
    my ($class) = @_;
    my ($self) = {
        _impl => $_[1],
        _conn => $_[2],
    };

    die "Must provide an implementation." unless defined($self->{_impl});
    die "Must provide a Connection." unless defined($self->{_conn});

    bless $self, $class;
    return $self;
}

=pod

=head1 ACTIONS

=cut


=pod

=head2 CLOSING THE SESSION

=over

=item $session->close

=back

=cut
sub close {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->close;
}

=pod

=head2 TRANSACTIONS

Transactions can be rolled back or committed.

=over

=item $session->commit

=item $session->rollback

=back

=cut
sub commit {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->commit;
}

sub rollback {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->rollback;
}

=pod

=head2 MESSAGE DISPOSITIONS

=cut


=pod

=over

=item $session->acknowledge

Acknowledges that a specific message that has been received.

=back

=begin _private

TODO: How to handle acknowledging a specific message?

=end _private

=cut
sub acknowledge {
    my ($self) = @_;
    my $sync = $_[1] || 0;

    my $impl = $self->{_impl};

    $impl->acknowledge($sync);
}

=pod

=over

=item $session->reject( msg )

Rejects the specified message. A reject message will not be redelivered.

=back

=cut
sub reject {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->reject($_[1]->get_implementation);
}

=pod

=over

=item $session->release( msg )

Releases the specified message, which allows the broker to attempt to
redeliver it.

=back

=cut
sub release {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->release($_[1]->get_implementation);
}

=pod

=over

=item $session->sync

=item $session->sync( block )

Requests synchronization with the broker.

=back

=head3 ARGUMENTS

=over

=item * block

If true, then the call blocks until the process completes.

=back

=cut
sub sync {
    my ($self) = @_;
    my $impl = $self->{_impl};

    if(defined($_[1])) {
        $impl->sync($_[1]);
    } else {
        $impl->sync;
    }
}

=pod

=head2 SENDERS AND RECEIVERS

=cut


=pod

=over

=item $sender = $session->create_sender( address )

Creates a new sender.

=back

=head3 ARGUMENTS

=over

=item * address

The sender address. See B<qpid::messaging::Address> for more details

=back

=cut
sub create_sender {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $address = $_[1];

    if (ref($address) eq "qpid::messaging::Address") {
        my $temp = $address->get_implementation();
        $address = $temp;
    }
    my $send_impl = $impl->createSender($address);

    return new qpid::messaging::Sender($send_impl, $self);
}

=pod

=over

=item $sender = $session->get_session( name )

=back

=head3 ARGUMENTS

=over

=item * name

The name of the sender.

Raises an exception when no sender with that name exists.

=back

=cut
sub get_sender {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $send_impl = $impl->getSender($_[1]);
    my $sender = undef;

    if (defined($send_impl)) {
        $sender = new qpid::messaging::Sender($send_impl, $self);
    }

    return $sender;
}

=pod

=over

=item $receiver = $session->create_receiver( address )

=back

=head3 ARGUMENTS

=over

=item * address

The receiver address. see B<qpid::messaging::Address> for more details.

=back

=cut
sub create_receiver {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $address = $_[1];

    if (ref($address) eq "qpid::messaging::Address") {
        $address = $address->get_implementation();
    }
    my $recv_impl = $impl->createReceiver($address);

    return new qpid::messaging::Receiver($recv_impl, $self);
}

=pod

=over

=item $receiver = $session->get_receiver( name )

=back

=head3 ARGUMENTS

=over

=item * name

The name of the receiver.

=back

=cut
sub get_receiver {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $recv_impl = $impl->getReceiver($_[1]);
    my $receiver = undef;

    if (defined($recv_impl)) {
        $receiver = new qpid::messaging::Receiver($recv_impl, $self);
    }

    return $receiver;
}

=pod

=head1 ATTRIBUTES

=cut


=pod

=head2 RECEIVABLE

The total number of receivable messages, and messages already received,
by receivers associated with this session.

=over

=item $session->get_receivable

=back

=cut
sub get_receivable {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getReceivable;
}

=pod

=head2 UNSETTLED ACKNOWLEDGEMENTS

The number of messages that have been acknowledged by this session whose
acknowledgements have not been confirmed as processed by the broker.

=over

=item $session->get_unsettled_acks

=back

=cut
sub get_unsettled_acks {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettledAcks;
}

=pod

=head2 NEXT RECEIVER

The next receiver is the one, created by this session, that has any pending
local messages.

If no receivers are found within the timeout then a B<MessagingException> is
raised.

=over

=item $session->get_next_receiver

=item $session->get_next_receiver( timeout )

=back

=head3 ARGUMENTS

=over

=item * timeout

The period of time to wait for a receiver to be found. If no period of time is
specified then the default is to wait B<forever>.

=back

=cut
sub get_next_receiver {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $timeout = $_[1] || qpid::messaging::Duration::FOREVER;

    return $impl->getNextReceiver($timeout);
}

=pod

=head2 CONNECTION

=over

=item $conn = $session->get_connection

Returns the owning connection for the session.

=back

=cut
sub get_connection {
    my ($self) = @_;

    return $self->{_conn};
}

sub has_error {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->hasError;
}

sub check_for_error {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->checkForError;
}

1;
