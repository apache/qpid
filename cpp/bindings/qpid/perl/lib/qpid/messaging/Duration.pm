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

package qpid::messaging::Duration;

use overload (
    "*" =>  \&multiply,
    "==" => \&equalify,
    "!=" => \&unequalify,
    );

sub multiply {
    my ($self) = @_;
    my $factor = $_[1];

    die "Factor must be non-negative values" if !defined($factor) || ($factor < 0);

    my $duration = $self->{_impl} * $factor;

    return new qpid::messaging::Duration($duration);
}

sub equalify {
    my ($self) = @_;
    my $that = $_[1];

    return 0 if !defined($that) || !UNIVERSAL::isa($that, 'qpid::messaging::Duration');;

    return ($self->get_milliseconds() == $that->get_milliseconds()) ? 1 : 0;
}

sub unequalify {
    my ($self) = @_;
    my $that = $_[1];

    return 1 if !defined($that) || !UNIVERSAL::isa($that, 'qpid::messaging::Duration');;

    return ($self->get_milliseconds() != $that->get_milliseconds()) ? 1 : 0;
}

sub new {
    my ($class) = @_;
    my $duration = $_[1];

    die "Duration time period must be defined" if !defined($duration);

    if (!UNIVERSAL::isa($duration, 'cqpid_perl::Duration')) {
        die "Duration must be non-negative" if $duration < 0;
        $duration = new cqpid_perl::Duration($duration);
    }

    my ($self) = {
        _impl => $duration,
    };

    bless $self, $class;
    return $self;
}

sub get_milliseconds {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getMilliseconds();
}

sub get_implementation {
    my ($self) = @_;

    return $self->{_impl};
}

# TODO: Need a better way to define FOREVER
use constant {
    FOREVER => new qpid::messaging::Duration(1000000),
    IMMEDIATE => new qpid::messaging::Duration(0),
    SECOND => new qpid::messaging::Duration(1000),
    MINUTE => new qpid::messaging::Duration(60000),
};

1;
