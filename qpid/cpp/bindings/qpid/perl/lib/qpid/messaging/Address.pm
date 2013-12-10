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

qpid::messaging::Address

=head1 DESCRIPTION

An B<Address> represents an address to which messages can be sent or
from which they can be received.

=head2 THE ADDRESS STRING

An address can be described suing the following pattern:

E<lt>addressE<gt> [ / E<lt>subjectE<gt> ]= ; [ { E<lt>keyE<gt> : E<lt>valueE<gt> , ... } ]

where B<address> is a simple name and B<subject> is a subject or subject
pattern.

=head3 ADDRESS OPTIONS

The options, encluded in curly braces, are key:value pairs delimited by a comma.
The values can be nested maps also enclosed in curly braces. Or they can be
lists of values, where they are contained within square brackets but still comma
delimited, such as:

  [value1,value2,value3]

The following are the list of supported options:

=over

=item B<create>

Indicates if the address should be created; values are B<always>, B<never>,
B<sender> or B<receiver>

=item B<assert>

Indicates whether or not to assert any specified node properties; values are
B<always>, B<never>, B<sender> or B<receiver>

=item B<delete>

Indicates whether or not to delete the addressed node when a sender or receiver
is cancelled; values are B<always>, B<never>, B<sender> or B<receiver>

=item B<node>

A nested map describing properties for the addressed node. Properties are
B<type> (B<topic> or B<queue>), B<durable> (a boolean), B<x-declare> (a nested
map of AMQP 0.10-specific options) and B<x-bindings> (a nested list which
specifies a queue, exchange or a binding key and arguments).

=item B<link>

=item B<mode>

=back

=cut

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

=pod

=head1 CONSTRUCTOR

Creates an B<Address>

=over

=item $address = new qpid::messaging::Address( addr )

=back

=head3 ARGUMENTS

=over

=item * addr

The address string.

=back

=cut
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

=pod

=head1 ATTRIBUTES

=cut

=pod

=head2 NAME

The name portion of the address.

=over

=item $address->set_name( name )

=item $name = $address->get_name

=back

=head3 ARGUMENTS

=over

=item * name

See the address string explanation.

=back

=cut
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

=pod

=head2 SUBJECT

The subject portion of the address.

=over

=item $address->set_subject( subject )

=item $subject = $address->get_subject

=back

=head3 ARGUMENTS

=over

=item * subject

See the address string explanation.

=back

=cut
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

=pod

=head2 OPTIONS

The address options.

=over

=item $address->set_options( options )

=item @opts = $address->get_options

=back

=head3 ARGUMENTS

=over

=item * options

The set of name:value pairs for the address. See the address string explanation.

=back

=cut
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

=pod

=head2 TYPE

The type of the address determines how B<Sender> and B<Receiver> objects are
constructed for it. It also affects how a b<reply-to> address is encoded.

If no type is specified then it willb e determined by querying the broker.
Explicitly setting the type prevents this.

=over

=item $address->set_type( type )

=item $type = $address->get_type

=back

=head3 ARGUMENTS

=over

=item * type

Values can be either B<queue> or B<type>.

=back

=cut
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
