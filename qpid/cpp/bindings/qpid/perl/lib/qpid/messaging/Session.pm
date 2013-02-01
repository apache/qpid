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

sub close {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->close;
}

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

# TODO how to handle acknowledging a specific message
sub acknowledge {
    my ($self) = @_;
    my $sync = $_[1] || 0;

    my $impl = $self->{_impl};

    $impl->acknowledge($sync);
}

sub acknowledge_up_to {
}

sub reject {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->reject($_[1]);
}

sub release {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->release($_[1]);
}

sub sync {
    my ($self) = @_;
    my $impl = $self->{_impl};

    if(defined($_[1])) {
        $impl->sync($_[1]);
    } else {
        $impl->sync;
    }
}

sub get_receivable {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getReceivable;
}

sub get_unsettled_acks {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettledAcks;
}

sub get_next_receiver {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $timeout = $_[1] || qpid::messaging::Duration::FOREVER;

    return $impl->getNextReceiver($timeout);
}

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
