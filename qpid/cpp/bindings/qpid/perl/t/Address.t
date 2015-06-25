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

# construction
# address cannot be null
dies_ok (sub {new qpid::messaging::Address(undef);},
         "Address cannot be null");

# can use an address
my $address = new qpid::messaging::Address("0.0.0.0");
ok ($address, "Can be created with an arbitrary address");

# name
# name cannot be null
dies_ok (sub {$address->set_name(undef);},
         "Name cannot be null");

# name can be an empty string
$address->set_name("");
ok ($address->get_name() eq "",
    "Name can be empty");

# name can be an arbitrary string
my $name = random_string(25);
$address->set_name($name);
ok ($address->get_name() eq $name,
    "Name can be an arbitrary string");

# subject
# cannot be null
dies_ok (sub {$address->set_subject(undef);},
         "Subject cannot be null");

# can be an empty string
$address->set_subject("");
ok ($address->get_subject() eq "",
    "Subject can be empty");

# can be an arbitrary string
my $subject = random_string(64);
$address->set_subject($subject);
ok ($address->get_subject() eq $subject,
    "Subject can be an arbitrary string");

# options
# options cannot be null
dies_ok (sub {$address->set_options(undef);},
         "Options cannot be null");

# options can be an empty hash
$address->set_options({});
ok (eq_hash($address->get_options(), {}),
    "Options can be an empty hash");

# options cannot be arbitrary values
my %options = ("create", "always", "delete", "always");
$address->set_options(\%options);
ok (eq_hash($address->get_options(), \%options),
    "Options can be arbitrary keys");

# type
# cannot be null
dies_ok (sub {$address->set_type(undef);},
         "Type cannot be null");

# can be an empty string
$address->set_type("");
ok ($address->get_type() eq "",
    "Type can be an empty string");

# can be an arbitrary string
my $type = random_string(16);
$address->set_type($type);
ok ($address->get_type() eq $type,
    "Type can be an arbitrary type");

