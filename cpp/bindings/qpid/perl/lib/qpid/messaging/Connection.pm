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

package qpid::messaging::Connection;

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

sub is_open {
    my ($self) = @_;
    my $impl = $self->{_impl};

    if (defined($impl) && $impl->isOpen()) {
        1;
    } else {
        0;
    }
}

sub close {
    my ($self) = @_;

    if ($self->is_open) {
        my $impl = $self->{_impl};

        $impl->close;
        $self->{_impl} = undef;
    }
}

sub create_session {
    my ($self) = @_;

    die "No connection available." unless ($self->open);

    my $impl = $self->{_impl};
    my $name = $_[1] || "";
    my $session = $impl->createSession($name);

    return new qpid::messaging::Session($session, $self);
}

sub create_transactional_session {
    my ($self) = @_;

    die "No connection available." unless ($self->open);

    my $impl = $self->{_impl};
    my $name = $_[1] || "";
    my $session = $impl->createTransactionalSession($name);

    return new qpid::messaging::Session($session, $self);
}

sub get_session {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getSession($_[1]);
}

sub get_authenticated_username {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getAuthenticatedUsername;
}

1;
