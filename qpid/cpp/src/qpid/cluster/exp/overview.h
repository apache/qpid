// This file is documentation in doxygen format.
/**

<h1>New cluster implementation overview</h>

There are 3 areas indicated by a suffix on class names:

- Replica: State that is replicated to the entire cluster. Only called by Handlers in the deliver thread.
- Context: State that is private to this member. Called by both Replia and broker objects in deliver and connection threads.
- Handler: Dispatch CPG messages by calling Replica objects in the deliver thread.


**/
