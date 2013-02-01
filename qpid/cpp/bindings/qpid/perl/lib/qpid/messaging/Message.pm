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
    my $address = $_[1];

    # if the address was a string, then wrap it
    # in a qpid::messaging::Address instance
    if (!UNIVERSAL::isa($address, 'qpid::messaging::Address')) {
        $address = new qpid::messaging::Address($_[1]);
    }

    $impl->setReplyTo($address->get_implementation());
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
    my $id = $_[1];

    die "message id must be defined" if !defined($id);

    $impl->setMessageId($id);
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
    my $priority = $_[1];

    die "Priority must be provided" if !defined($priority);

    $priority = int($priority);
    die "Priority must be non-negative" if $priority < 0;

    $impl->setPriority($priority);
}

sub get_priority {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getPriority;
}

sub set_ttl {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $duration = $_[1];

    die "Duration must be provided" if !defined($duration);
    if (!UNIVERSAL::isa($duration, 'qpid::messaging::Duration')) {
        $duration = int($duration);

        if ($duration < 0) {
            $duration = qpid::messaging::Duration::FOREVER;
        } elsif ($duration == 0) {
            $duration = qpid::messaging::Duration::IMMEDIATE;
        } else {
            $duration = new qpid::messaging::Duration(int($duration));
        }
    }

    $impl->setTtl($duration->get_implementation());
}

sub get_ttl {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return new qpid::messaging::Duration($impl->getTtl);
}

sub set_durable {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $durable = $_[1];

    die "Durable must be specified" if !defined($durable);

    $impl->setDurable($durable);
}

sub get_durable {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getDurable;
}

sub set_redelivered {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $redelivered = $_[1];

    die "Redelivered must be specified" if !defined($redelivered);

    $impl->setRedelivered($redelivered);
}

sub get_redelivered {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getRedelivered;
}

sub set_property {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $key = $_[1];
    my $value = $_[2];

    $impl->setProperty($key, $value);
}

sub get_properties {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getProperties;
}

sub set_content {
    my ($self) = @_;
    my $content = $_[1];
    my $impl = $self->{_impl};

    die "Content must be provided" if !defined($content);

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

1;
