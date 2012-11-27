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

use strict;
use warnings;
use cqpid_perl;

package qpid::messaging;

sub encode {
    my $content = $_[0];
    my $message = $_[1];

    cqpid_perl::encode($content, $message->get_implementation());
}

sub decode_map {
    my $message = $_[0];

    return cqpid_perl::decodeMap($message->get_implementation());
}



package qpid::messaging::Address;

use overload (
    'bool' =>  \&boolify,
    );

sub boolify {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return length($impl->getName());
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

    $impl->setType($_[1]);
}

sub get_type {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getType;
}



package qpid::messaging::Duration;

sub new {
    my ($class) = @_;
    my ($self) = {
        _impl => new cqpid_perl::Duration($_[1]),
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
    FOREVER => new qpid::messaging::Duration(10000),
    IMMEDIATE => new qpid::messaging::Duration(0),
    SECOND => new qpid::messaging::Duration(1000),
    MINUTE => new qpid::messaging::Duration(60000),
};



package qpid::messaging::Message;

sub new {
    my ($class) = @_;
    my $content = $_[1] if (@_ > 1);
    my $impl = $_[2] if (@_ > 2);
    my ($self) = {
        _content => $content || "",
        _impl => $impl || undef,
    };

    unless (defined($self->{_impl})) {
        my $impl = new cqpid_perl::Message($self->{_content});

        $self->{_impl} = $impl;
    }

    bless $self, $class;
    return $self;
}

sub get_implementation {
    my ($self) = @_;

    return $self->{_impl};
}

sub set_reply_to {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setReplyTo($_[1]->get_implementation());
}

sub get_reply_to {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return new qpid::messaging::Address($impl->getReplyTo());
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

sub set_content_type {
    my ($self) = @_;
    my $type = $_[1];

    my $impl = $self->{_impl};
    $impl->setContentType($type);
}

sub get_content_type {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getContentType;
}

sub set_message_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setMessageId($_[1]);
}

sub get_message_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getMessageId;
}

sub set_user_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setUserId($_[1]);
}

sub get_user_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getUserId;
}

sub set_correlation_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setCorrelationId($_[1]);
}

sub get_correlation_id {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getCorrelationId;
}

sub set_priority {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setPriority($_[1]);
}

sub get_priority {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getPriority;
}

sub set_ttl {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setTtl($_[1]);
}

sub get_ttl {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getTtl;
}

sub set_durable {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setDurable($_[1]);
}

sub get_durable {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getDurable;
}

sub set_redelivered {
    my ($self) = @_;
    my $impl = $self->{_impl};

    $impl->setRedelivered($_[1]);
}

sub get_redelivered {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getRedelivered;
}

sub get_property {
    my ($self) = @_;
    my $key = $_[1];

    my $impl = $self->{_impl};

    return $impl->getPropert($key);
}

sub get_properties {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getProperties;
}

sub set_content {
    my ($self) = @_;
    my $content = $_[1] || "";
    my $impl = $self->{_impl};

    $impl->setContent($content);
}

sub get_content {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getContent();
}

sub get_content_size {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getContentSize;
}



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
