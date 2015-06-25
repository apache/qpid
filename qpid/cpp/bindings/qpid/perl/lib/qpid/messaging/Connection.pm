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

qpid::messaging::Connection

=head1 DESCRIPTION

A B<qpid::messaging::Connection> represents a network connection to a remote
endpoint.

=cut

package qpid::messaging::Connection;

=pod

=head1 CONSTRUCTOR

=over

=item $conn = new qpid::messaging::Connection

=item $conn = new qpid::messaging::Connection( url )

=item $conn = new qpid::messaging::Connection( url, options )

Creates a connection object. Raises a C<MessagingError> if an invalid
connection option is used.

=back

=head3 ARGUMENTS

=over

=item * url

The URL for the broker. See B<qpid::messaging::Address> for more on
 address strings

=item * options

The connection options.

=back

=cut

sub new {
    my ($class) = @_;
    my $self = {
        _url => $_[1] || "localhost:5672",
        _options => $_[2] || {},
        _impl => $_[3],
    };

    bless $self, $class;
    return $self;
}

=pod

=head1 ACTIONS

=cut


=pod

=head2 OPENING AND CLOSING CONNECTIONS

=cut


=pod

=over

=item $conn->open

Establishes the connection to the broker.

=back

=cut
sub open {
    my ($self) = @_;
    my $impl = $self->{_impl};

    # if we have an implementation instance then use it, otherwise
    # create a new implementation instance
    unless (defined($impl)) {
        my $url = $self->{_url};
        my ($options) = $self->{_options};

        $impl = new cqpid_perl::Connection($url, $options);
        $self->{_impl} = $impl
    }

    $impl->open() unless $impl->isOpen()
}

=pod

=over

=item $conn->is_open

Reports whether the connection is open.

=back

=cut
sub is_open {
    my ($self) = @_;
    my $impl = $self->{_impl};

    if (defined($impl) && $impl->isOpen()) {
        1;
    } else {
        0;
    }
}

=pod

=over

=item $conn->close

Closes the connection.

=back

=cut
sub close {
    my ($self) = @_;

    if ($self->is_open) {
        my $impl = $self->{_impl};

        $impl->close;
        $self->{_impl} = undef;
    }
}

=pod

=head2 SESSIONS

=cut


=pod

=over

=item $session = $conn->create_session

=item $conn->create_session( name )

Creates a new session.

=back

=head3 ARGUMENTS

=over

=item * name

Specifies a name for the session.

=back

=cut
sub create_session {
    my ($self) = @_;

    die "No connection available." unless ($self->open);

    my $impl = $self->{_impl};
    my $name = $_[1] || "";
    my $session = $impl->createSession($name);

    return new qpid::messaging::Session($session, $self);
}

=pod

=over

=item $session = $conn->create_transactional_session

=item $session = $conn->create_transaction_session( name )

Creates a transactional session.

=back

=head3 ARGUMENTS

=over

=item * name

Specifies a name for the session.

=back

=cut
sub create_transactional_session {
    my ($self) = @_;

    die "No connection available." unless ($self->open);

    my $impl = $self->{_impl};
    my $name = $_[1] || "";
    my $session = $impl->createTransactionalSession($name);

    return new qpid::messaging::Session($session, $self);
}

=pod

=over

=item $session = $conn->get_session( name )

Returns the session with the specified name.

=over

=item $name

The name given to the session when it was created.

=back

=back

=cut
sub get_session {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getSession($_[1]);
}

=pod

=over

=item $uname = $conn->get_authenticated_username

Returns the username user to authenticate with the broker.

If the conneciton did not use authentication credentials, then the
username returned is "anonymous".

=back

=cut
sub get_authenticated_username {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAuthenticatedUsername;
}

1;
