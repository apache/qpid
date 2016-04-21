= Running Qpid C++ tests =

General philosophy is that "make test" run all tests by default, but
developers can run tests selectively as explained below.

== Unit Tests ==

Unit tests use the boost test framework, and are compiled to the programd
unit_test

There are several options to control how test results are displayed. See
http://www.boost.org/doc/libs/1_35_0/libs/test/doc/components/utf/parameters/index.html.

== System Tests ==

System tests are executables or scripts. You can run executable tests
directly as well as via "make test" or "ctest".  Some tests require
environment settings which are set by src/tests/env.sh on Unix or by
src/tests/env.ps1 on Windows.

== Running selected tests ==

The make target "make test" simply runs the command "ctest".  Running ctest
directly gives you additional options, e.g.

  ctest -R <regexp> -VV

This runs tests with names matching the regular expression <regexp> and will
print the full output of the tests rather than just listing which tests pass or
fail.
