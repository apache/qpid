The Python Examples
===================

README.txt                 -- This file.

api                        -- Directory containing drain, spout,
                              sever, hello, and hello_xml examples.

api/drain                  -- A simple messaging client that prints
                              messages from the source specified on
                              the command line.

api/spout                  -- A simple messaging client that sends
                              messages to the target specified on the
                              command line.

api/server                 -- An example server that process incoming
                              messages and sends replies.

api/hello                  -- An example client that sends a message
                              and then receives it.

api/hello_xml              -- An example client that sends a message
                              to the xml exchange and then receives
                              it.


reservations               -- Directory containing an example machine
                              reservation system.

reservations/common.py     -- Utility code used by reserve,
                              machine-agent, and inventory scripts.

reservations/reserve       -- Messaging client for listing, reserving,
                              and releasing machines.

reservations/machine-agent -- Messaging server that tracks and reports
                              on the status of its host machine and
                              listens for reservation requests.

reservations/inventory     -- Messaging server that tracks the last
                              known status of machines.
