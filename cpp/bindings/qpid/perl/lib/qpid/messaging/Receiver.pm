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
}

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

sub get_available {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAvailable;
}

sub get_unsettled {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettled;
}

sub close {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->close;
}

sub is_closed {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->isClosed;
}

sub get_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getName;
}

sub get_session {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->{_session};
}

1;
