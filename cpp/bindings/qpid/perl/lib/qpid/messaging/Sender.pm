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

sub send {
    my ($self) = @_;
    my $message = $_[1];
    my $sync = $_[2] || 0;

    die "No message to send." unless defined($message);

    my $impl = $self->{_impl};

    $impl->send($message->get_implementation, $sync);
}

sub close {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->close;
}

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

sub get_unsettled {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUnsettled;
}

sub get_available {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAvailable();
}

sub get_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getName;
}

sub get_session {
    my ($self) = @_;

    return $self->{_session};
}

1;
