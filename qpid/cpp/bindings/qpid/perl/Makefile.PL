#!/usr/bin/env perl -w

use strict;

use ExtUtils::MakeMaker;
use Config;

# generate the Swig wrappers for the Qpid libraries
system('swig -perl -c++ -I/usr/include -o cqpid_perl.cpp perl.i');

WriteMakefile(
    NAME              => 'cqpid_perl',
    PREREQ_PM         => {},
    LIBS              => ["-lqpidmessaging -lqpidtypes"],
    C                 => ['cqpid_perl.cpp'],
);
