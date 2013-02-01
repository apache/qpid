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

package qpid::messaging::Address;

use overload (
    'bool' => \& boolify,
    '""'   => \& stringify,
    );

sub boolify {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return length($impl->getName());
}

sub stringify {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $self->str();
}

sub str {
    my ($self) = @_;

    return $self->get_implementation()->str();
}

sub new {
    my ($class) = @_;
    my ($self) = {};

    # 2 args:  either a string address or a cqpid_perl::Address
    # 3+ args: name + subject + options + type
    if (@_ eq 2) {
        my $address = $_[1];

        if (ref($address) eq 'cqpid_perl::Address') {
            $self->{_impl} = $address;
        } else {
            $self->{_impl} = new cqpid_perl::Address($_[1]);
        }
    } elsif (@_ >= 4) {
        my $impl = new cqpid_perl::Address($_[1], $_[2], $_[3]);

        $impl->setType($_[4]) if @_ >= 5;

        $self->{_impl} = $impl;
    } else {
        die "You must specify an address."
    }

    bless $self, $class;
    return $self;
}

sub get_implementation {
    my ($self) = @_;
    return $self->{_impl};
}

sub set_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setName($_[1]);
}

sub get_name {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getName();
}

sub set_subject {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setSubject($_[1]);
}

sub get_subject {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getSubject;
}

sub set_options {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $options = $_[1];

    die "Options cannot be null" if !defined($options);

    $impl->setOptions($_[1]);
}

sub get_options {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getOptions;
}

sub set_type {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $type = $_[1];

    die "Type must be defined" if !defined($type);

    $impl->setType($type);
}

sub get_type {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getType;
}

1;
