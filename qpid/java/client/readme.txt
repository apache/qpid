AMQP JMS API

To build this you will need ant. The build.xml file requires that the amq.home
property be set to point to the location of the root directory under which the
base2 and amqp moduels reside (these contain protocol definitions used in 
the code generation).

You can avoid setting it if you have the following structure:

    root/ 
        base/
        base2/
        foreign/
        gsl/
        amqp/
        blaze/
                java/
                    client
                       build.xml
                       Readme.txt [this file]
                    common   


Otherwise you can either pass it in on the command line or add it to a file 
named build.properties in the same directory as this Readme.txt file. 

E.g.:

ant -Damq.home=c:\AMQP\

 
