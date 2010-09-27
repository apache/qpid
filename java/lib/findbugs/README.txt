Download the FindBugs archive from the following location:
http://findbugs.sourceforge.net/downloads.html

Unpack the contents of the 'findbugs-<version>/lib' folder within the archive
to the 'qpid/java/lib/findbugs' (i.e. this) directory. This should leave you
with contents of 'qpid/java/lib/findbugs' similar to:

annotations.jar
ant.jar
asm-3.1.jar
asm-analysis-3.1.jar
asm-commons-3.1.jar
asm-tree-3.1.jar
asm-util-3.1.jar
asm-xml-3.1.jar
bcel.jar
buggy.icns
commons-lang-2.4.jar
dom4j-1.6.1.jar
findbugs-ant.jar
findbugs.jar
jFormatString.jar
jaxen-1.1.1.jar
jdepend-2.9.jar
jsr305.jar
mysql-connector-java-5.1.7-bin.jar


Now simply run "ant findbugs" in qpid/java to generate the report.

