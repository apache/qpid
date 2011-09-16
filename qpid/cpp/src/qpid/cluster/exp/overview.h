// This file is documentation in doxygen format.
/**

<h1>New cluster implementation overview</h>

The code is broken down into 3 areas indicated by a suffix on class names:

- Replica: State that is replicated to the entire cluster.
  Only called by Handlers in the deliver thread. May call on Contexts.

- Context: State private to this member and associated with a local entity
  such as the Broker or a Queue. Called in deliver and connection threads.
  
- Handler: Dispatch CPG messages by calling Replica objects in the deliver thread.


**/
