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

qpid::messaging::Duration

=head1 DESCRIPTION

A B<qpid::messaging::Duration> represents a period of time in milliseconds.

=head1 NAMED DURATIONS

The following named durations are available as constants

=over

=item B<FOREVER>

The maximum wait time, equal to the maximum integer value for the platform.
Effective this will wait forever.

=item B<IMMEDIATE>

An alias for 0 milliseconds.

=item B<SECOND>

An alias for 1,000 milliseconds.

=item B<MINUTE>

An alias for 60,000 milliseconds.

=back

=cut

package qpid::messaging::Duration;

=pod

=head1 OPERATORS

=cut

use overload (
    "*" =>  \&multiply,
    "==" => \&equalify,
    "!=" => \&unequalify,
    );

=pod

=over

=item $doubled = $duration * $factor

=item $doubled = $duration * 2

Multiplies the duration and returns a new instance.

=over

=item $factor

A factor for multiplying the duration.

=back

=back

=cut
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

=pod

=head1 CONSTRUCTOR

Creates a new instance.

=over

=item duration = new qpid::messaging::Duration( time  )

=back

=head3 ARGUMENTS

=over

=item * time

The duration in B<milliseconds>.

=back

=cut
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

=pod

=head1 ATTRIBUTES

=cut


=pod

=head2 MILLISECONDS

The length of time is measured in milliseconds.

=over

=item time = $duration->get_milliseconds

=back

=cut
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
