This directory contains C++ example management tools and an example
managed application based on the QPID Management Framework (QMF)
Library.

agent.cpp
---------

This is an example of a managed application.  Applications that can be
managed by QMF are called "agents".  This example shows how an agent
can create managed objects and service method calls.  When run, this
agent will attempt to connect to a broker at address "localhost:5672".

list_agents.cpp
---------------

This is an example of a management tool.  QMF management tools are
called "consoles". This console monitors the broker for agent
additions and removals.  When run, it will attempt to connect to a
broker at address "localhost:5672".

event_driven_list_agents.cpp
----------------------------

This console is similar to the list_agents.cpp example, except it uses
an EventNotifier to wake up when new events arrive.  An EventNotifier
may be used in a POSIX select/poll loop.

print_events.cpp
----------------

A very basic console that monitors for all events published by agents.


Running the examples
--------------------

In order to run any of the examples, you'll first need to run a broker
daemon on your local machine (qpidd).  Once the broker is up and
running, start any of the example consoles.  While the consoles are
running, run the example agent.  The consoles will print out event
information that is published by the example agent.
