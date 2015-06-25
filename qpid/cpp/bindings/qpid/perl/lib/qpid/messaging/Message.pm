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

qpid::messaging::Message

=head1 DESCRIPTION

A B<qpid::messaging::Message> a routable piece of information.

=cut

package qpid::messaging::Message;


=pod

=head1 CONSTRUCTOR

Creates a B<Message>.

=over

=item $msg = new qpid::messaging::Message

=item $msg = new qpid::messaging::Message( $content )

=back

=head3 ARGUMENTS

=over

=item * $content

The message's content.

=back

=cut
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


=pod

=head1 ATTRIBUTES

=cut

=pod

=head2 REPLY TO ADDRESS

The reply-to address tells a receiver where to send any responses.

=over

=item $msg->set_reply_to( "#reqly-queue;{create:always}" )

=item $msg->set_reply_to( address )

=item $address = $msg->get_reply_to

=back

=head3 ARGUMENTS

=over

=item * address

The address. Can be either an instance of B<qpid::messaging::Address> or else an
address string.

=back

=cut
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

=pod

=head2 SUBJECT

=over

=item $msg->set_subject( "responses" )

=item $msg->set_subject( subject )

=item $subject = $msg->get_subject

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

=head2 CONTENT TYPE

This should be set by the sending application and indicates to the
recipients of the message how to interpret or decide the content.

By default, only dictionaries and maps are automatically given a content
type. If this content type is replaced then retrieving the content will
not behave correctly.

=over

=item $msg->set_content_type( content_type )

=back

=head3 ARGUMENTS

=over

=item * content_type

The content type. For a list this would be C<amqp/list> and for a hash it is
C<amqp/map>.

=back

=cut
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

=pod

=head2 MESSAGE ID

A message id must be a UUID type. A non-UUID value will be converted
to a zero UUID, thouygh a blank ID will be left untouched.

=over

=item $msg->set_message_id( id )

=item $id = $msg->get_message_id

=back

=cut
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

=pod

=head2 USER ID

The user id should, in general, be the user-id which was used when
authenticating the connection itself, as the messaging infrastructure
will verify this.

See B<qpid::messaging::Address#authenticated_username>.

=over

=item $msg->set_user_id( id )

=item $id = $msg->get_user_id

=back

=cut
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

=pod

=head2 CORRELATION ID

The correlation id can be used as part of a protocol for message exchange
patterns; e.g., a request-response pattern might require the correlation id
of the request and hte response to match, or it might use the message id of
the request as the correlation id on the response.

B<NOTE:> If the id is not a string then the id is setup using the object's
string representation.

=over

=item $msg->set_correlation_id( id )

=item $id = $msg->get_correlation_id

=back

=cut
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

=pod

=head2 PRIORITY

The priority may be used by the messaging infrastructure to prioritize
delivery of messages with higher priority.

B<NOTE:> If the priority is not an integer type then it is set using the
object's integer represtation. If the integer value is greater than an
8-bit value then only 8-bits are used.

=over

=item $msg->set_priority( priority )

=item $priority = $msg->get_priority

=back

=cut
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

=pod

=head2 TIME TO LIVE

This can be used by the messaging infrastructure to discard messages
that are no longer of relevance.

=over

=item $msg->set_ttl( ttl )

=item $ttl = $msg->get_ttl

=back

=head3 ARGUMENTS

=over

=item * ttl

A B<qpid::messaging::Duration> instance. If it is not, then a new instance
is created using the integer value for the argument.

A B<negative> value is treated as the equipment of
B<qpid::messaging::Duration::FOREVER>.

=back

=cut
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

=pod

=head2 DURABILITY

The durability of a B<Message> is a hint to the messaging infrastructure that
the message should be persisted or otherwise stored. This helps to ensure that
the message is not lost due to failures or a shutdown.

=over

=item $msg->set_durable( 1 )

=item $durable = $msg->get_durable

=back

=cut
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

=pod

=head2 REDELIVERED

This is a hint to the messaging infrastructure that if de-duplication is
required, that this message should be examined to determine if it is a
duplicate.

=over

=item $msg->set_redelivered( 1 )

=item $redelivered = $msg->get_redelivered

=back

=cut
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

=pod

=head2 PROPERTIES

Named properties for the message are name/value pairs.

=over

=item $msg->set_property(  name,  value )

=item $value = $msg->get_property( name )

=item @props = $msg->get_properties

=back

=head3 ARGUMENTS

=over

=item * name

The property name.

=item * value

The property value.

=back

=cut
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

=pod

=head2 CONTENT OBJECT

The message content, represented as anh object.

=over

=item $msg->set_content_object( \%content )

=item $content = $msg->get_Content_object

=back

=cut

sub set_content_object {
    my ($self) = @_;
    my $impl = $self->{_impl};

    my $content = $_[1];

    $impl->setContentObject($content);
}

sub get_content_object {
    my ($self) = @_;
    my $impl = $self->{_impl};
    my $content = $impl->getContentObject;

    return $content;
}

=pod

=head2 CONTENT

The message content.

=begin _private

TODO: Need to make the content automatically encode and decode for
hashes and lists.

=end _private

=over

=item $msg->set_content( content )

=item $content = $msg->get_content

=item $length = $msg->get_content_size

=back

=cut
sub set_content {
    my ($self) = @_;
    my $content = $_[1];
    my $impl = $self->{_impl};

    die "Content must be provided" if !defined($content);

    $self->{_content} = $content;

    qpid::messaging::encode($content, $self);
}

sub get_content {
    my ($self) = @_;
    my $impl = $self->{_impl};
    $content = $self->{_content} || undef;

    if(!defined($content)) {
        $content = qpid::messaging::decode($self);
        $self->{_content} = $content;
    }

    return $content;
}

sub get_content_size {
    my ($self) = @_;
    my $impl = $self->{_impl};

    return $impl->getContentSize;
}

1;
