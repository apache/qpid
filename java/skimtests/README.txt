This module does not contain any code, but uses the other modules and the extended junit test runner, to run some
benchmark tests. The idea is that it will do short run of the benchmark tests on every build, in order monitor
performance changes as changes are applied to the code, and to check that tests will run against an external broker 
(as opposed to an in-vm one, which many of the unit tests use). These are called skim tests because they are a shorter 
run of what would typically be run against the broker when doing a full performance evaluation; each benchmark is 
only run for 1 minute.

One reason this is in a seperate module, is so that in the top-level pom, a profile can exist that must be switched 
on in order to run these skim tests. It is easiest to include/exclude this module in its entirety rather than to 
mess around with maven lifecycle stages.