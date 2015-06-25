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

# milliseconds
# duration cannot be null
{
    dies_ok (sub {new qpid::messaging::Duration(undef);},
             "Durations cannot have null time periods");
}

# duration cannot be negative
{
    my $period = 0 - (int(rand(65535)) + 1);
    dies_ok(sub {new qpid::messaging::Duration($period);},
            "Duration times cannot be negative");
}

# duration can be an arbitrary value
{
    my $period = int(rand(65535));
    my $duration = new qpid::messaging::Duration($period);
    ok ($duration->get_milliseconds() == $period,
        "Milliseconds are properly stored and fetched");
}

# multiplier
# cannot multiply by null
dies_ok(sub {qpid::messaging::Duration::FOREVER * undef;},
        "Cannot multiply a duration times a null");

# cannot multiply by a negative
dies_ok (sub {qpid::messaging::Duration::MINUTE * -2;},
         "Duration cannot be multiplied by a negative");

# multiply by zero returns a zero time period
{
    my $result = qpid::messaging::Duration::MINUTE * 0;

    ok ($result->get_milliseconds() == 0,
        "Multiplying duration by 0 returns a 0 duration");
}

# multiply by arbitrary values works
{
    my $factor = int(1 + rand(100));
    my $result = qpid::messaging::Duration::MINUTE * $factor;
    ok ($result->get_milliseconds() == 60000 * $factor,
        "Multiplying by a factor returns a new Duration with that period");
}

# equality
# always fails with null
ok (!(qpid::messaging::Duration::MINUTE == undef),
    "Duration is never equal to null");

# never equal to a non-duration class
ok (!(qpid::messaging::Duration::MINUTE == random_string(12)),
    "Duration is never equal to a non-Duration");

# works with self
ok (qpid::messaging::Duration::MINUTE == qpid::messaging::Duration::MINUTE,
    "Duration is always equal to itself");

# fails with non-equal instance
ok (!(qpid::messaging::Duration::MINUTE == qpid::messaging::Duration::SECOND),
    "Duration non-equality works");

# works with equal instance
{
    my $result = qpid::messaging::Duration::MINUTE * 0;
    ok ($result == qpid::messaging::Duration::IMMEDIATE,
        "Equality comparison works correctly");
}

# non-equality
# always not equal to null
ok (qpid::messaging::Duration::MINUTE != undef,
    "Always unequal to null");

# always not equal to a non-duration class
ok (qpid::messaging::Duration::MINUTE != random_string(64),
    "Always unequal to a non-duration class");

# not unequal to itself
ok (!(qpid::messaging::Duration::MINUTE != qpid::messaging::Duration::MINUTE),
    "Never unequal to itself");

# not unequal to an equal instance
{
    my $duration = qpid::messaging::Duration::MINUTE * 1;
    ok (!(qpid::messaging::Duration::MINUTE != $duration),
        "Never unequal to an equal instance");
}

# works with unequal instances
ok (qpid::messaging::Duration::MINUTE != qpid::messaging::Duration::FOREVER,
    "Always unequal to a non-equal instance");

