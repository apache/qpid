#!/usr/bin/env perl -w
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

use Test::More qw(no_plan);
use Test::Exception;

# append the location of the test to the PERL5LIB path
use File::Basename;
BEGIN {push @INC, dirname (__FILE__)};
use utils;

# verify that qpid is available
BEGIN { use_ok( 'qpid' ); }
require_ok ('qpid' );

# Create a new message
my $message = new qpid::messaging::Message();
isa_ok($message, 'qpid::messaging::Message');

# reply to
# rejects an null address
dies_ok (sub {$message->set_reply_to(undef);},
         "Reply to cannot be null.");

# can handle a string address
$message->set_reply_to("test");
ok ($message->get_reply_to()->str() eq "test",
    "Reply to can be set");

# subject
# cannot have an null subject
dies_ok (sub {$message->set_subject(undef);},
         "Subject cannot be null");

# can have an empty subject
$message->set_subject("");
ok ($message->get_subject() eq "",
    "Subject can be empty");

# can have a subject
my $subject = random_string(16);
$message->set_subject($subject);
ok ($message->get_subject() eq $subject,
    "Subject can be set.");

# content type
# cannot have an null content type
dies_ok (sub {$message->set_content_type(undef);},
         "Content type must be defined.");

# can an empty content type
$message->set_content_type("");
ok ($message->get_content_type() eq "",
    "Content type can be empty");

# can have an arbitrary content type
my $content_type = random_string(10);
$message->set_content_type($content_type);
ok ($message->get_content_type() eq $content_type,
    "Content type can be arbitrary");

# can be for a map
$content_type = "amqp/map";
$message->set_content_type($content_type);
ok ($message->get_content_type() eq $content_type,
    "Content type can be for a map");

# message id
# cannot be null
dies_ok (sub {$message->set_message_id(undef);},
         "Message id cannot be null");

# can be an empty string
$message->set_message_id("");
ok ($message->get_message_id() eq "",
    "Message id can be empty");

# can be an arbitrary string
my $id = random_string(32);
$message->set_message_id($id);
ok ($message->get_message_id() eq $id,
    "Message id can be an arbitrary string");

# can be a UUID
$id = generate_uuid();
$message->set_message_id($id);
ok ($message->get_message_id() eq $id,
    "Message id can be a valid UUID");

# user id
# cannot be null
dies_ok (sub {$message->set_user_id(undef);},
         "User id cannot be null");

# can be an empty string
my $user_id = "";
$message->set_user_id($user_id);
ok ($message->get_user_id() eq $user_id,
    "User id can be empty");

# can be an arbitrary string
$id = random_string(65);
$message->set_user_id($user_id);
ok ($message->get_user_id() eq $user_id,
    "User id can be an arbitrary string");

# correlation id
# cannot be null
dies_ok (sub {$message->set_correlation_id(undef);},
         "Correlation id cannot be null");

# can be empty
my $correlation_id = "";
$message->set_correlation_id($correlation_id);
ok ($message->get_correlation_id() eq $correlation_id,
    "Correlation id can be an empty string");

# can be an arbitrary string
$correlation_id = random_string(32);
$message->set_correlation_id($correlation_id);
ok ($message->get_correlation_id() eq $correlation_id,
    "Correlation id can be an arbitrary string");

# priority
# cannot be nul
dies_ok (sub {$message->set_priority(undef);},
         "Priority cannot be null");

# cannot be negative
my $priority = 0 - (rand(2**8) + 1);
dies_ok (sub {$message->set_priority($priority);},
         "Priority cannot be negative");

# can be 0
$message->set_priority(0);
ok ($message->get_priority() == 0,
    "Priority can be zero");

# can be an arbitrary value
$priority = int(rand(2**8) + 1);
$message->set_priority($priority);
ok ($message->get_priority() == $priority,
    "Priority can be any positive value");

# ttl
# cannot be null
dies_ok (sub {$message->set_ttl(undef);},
         "TTL cannot be null");

# can be a duration
$message->set_ttl(qpid::messaging::Duration::FOREVER);
ok ($message->get_ttl()->get_milliseconds() == qpid::messaging::Duration::FOREVER->get_milliseconds(),
    "TTL can be a Duration");

# if numeric, is converted to a duration
my $duration = rand(65535);
$message->set_ttl($duration);
ok ($message->get_ttl()->get_milliseconds() == int($duration),
    "TTL can be any arbitrary duration");

# if 0 it's converted to IMMEDIATE
$message->set_ttl(0);
ok ($message->get_ttl()->get_milliseconds() == qpid::messaging::Duration::IMMEDIATE->get_milliseconds(),
    "TTL of 0 is converted to IMMEDIATE");

# if negative it's converted to FOREVER
$message->set_ttl(0 - (rand(65535) + 1));
ok ($message->get_ttl()->get_milliseconds() == qpid::messaging::Duration::FOREVER->get_milliseconds(),
    "TTL of <0 is converted to FOREVER");

# durable
# cannot be null
dies_ok (sub {$message->set_durable(undef);},
         "Durable cannot be null");

# can be set to true
$message->set_durable(1);
ok ($message->get_durable(),
    "Durable can be true");

# can be set to false
$message->set_durable(0);
ok (!$message->get_durable(),
    "Durable can be false");

# redelivered
# redelivered cannot be null
dies_ok (sub {$message->set_redelivered(undef);},
         "Redelivered cannot be null");

# can be set to true
$message->set_redelivered(1);
ok ($message->get_redelivered(),
    "Redelivered can be true");

# can be set to false
$message->set_redelivered(0);
ok (!$message->get_redelivered(),
    "Redelivered can be false");

# properties
# can retrieve all properties
my $properties = $message->get_properties();
ok (UNIVERSAL::isa($properties, 'HASH'),
    "Returns the properties as a hash map");

# property
# setting a property using a null key fails
dies_ok (sub {$message->set_property(undef, "bar");},
         "Property cannot have a null key");

# setting a property with a null value succeeds
my $key = random_string(16);
$message->set_property($key, undef);
ok (!$message->get_properties()->{$key},
    "Properties can have null values");

# setting a property succeeds
my $value = random_string(255);
$message->set_property($key, $value);
ok ($message->get_properties()->{$key} eq $value,
    "Messages can have arbitrary property values");

# content
# cannot be null
dies_ok (sub {$message->set_content(undef);},
         "Content cannot be null");

# can be an empty string
$message->set_content_object("");
ok ($message->get_content_object() eq "",
    "Content can be an empty string");

# can be an arbitrary string
my $content = random_string(255);
$message->set_content_object($content);
ok ($message->get_content_object() eq $content,
    "Content can be an arbitrary string");

# Embedded nulls should be handled properly
$content = { id => 1234, name => "With\x00null" };
qpid::messaging::encode($content, $message);
my $map = qpid::messaging::decode($message);
ok ($map->{name} eq "With\x00null",
    "Nulls embedded in map values work.");

# Unicode strings shouldn't be broken
$content = { id => 1234, name => "Euro=\x{20AC}" };
qpid::messaging::encode($content, $message);
$map = qpid::messaging::decode($message);
ok ($map->{name} eq "Euro=\x{20AC}",
    "Unicode strings encoded correctly.");

# Maps inside maps should work
$content = { id => 1234, name => { first => "tom" } };
qpid::messaging::encode($content, $message);
$map = qpid::messaging::decode($message);
ok ($map->{name}{first} eq "tom",
    "Map inside map encoded correctly.");
