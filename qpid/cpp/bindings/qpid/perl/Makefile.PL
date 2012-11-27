#!/usr/bin/perl -w

use strict;

use ExtUtils::MakeMaker;
use Config;

WriteMakefile(
    NAME              => 'cqpid_perl',
    PREREQ_PM         => {},
    LIBS              => ["-lqpidmessaging -lqpidtypes"],
    C                 => ['cqpid_perl.cpp'],
);
