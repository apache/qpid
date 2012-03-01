The BDB JE jar must be downloaded into this directory in order to allow the optional bdbstore module to be built against it.

*NOTE* The BDB JE library is licensed under the Sleepycat Licence [1], which is not compatible with the Apache Lience v2.0. As a result, the BDB JE library is not distributed with the project, and the optional bdbstore module is not compiled by default.

The jar file may be downloaded by either:

   Seperately running the following command from the qpid/java/bdbstore dir: ant download-bdb

   OR

   Adding -Ddownload-bdb=true to your regular build command


[1] http://www.oracle.com/technetwork/database/berkeleydb/downloads/jeoslicense-086837.html
