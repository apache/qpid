#!/bin/bash

"$JAVA_HOME/bin/java" -Xms40m -Xmx256m -Declipse.consoleLog=false -jar $QPIDMC_HOME/eclipse/startup.jar org.eclipse.core.launcher.Main -launcher $QPIDMC_HOME/eclipse/eclipse -name "Qpid Management Console" -showsplash 600 -configuration "file:$QPIDMC_HOME/configuration"
