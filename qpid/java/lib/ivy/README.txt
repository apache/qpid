Folder used to store Ivy libs for use in publishing Maven artifacts.

Ivy must be downloaded and extracted into this directory if it is not available in the Ant lib dir.

File may be downloaded in 2 ways:
1. By running the following command from the qpid/java dir:
   ant -buildfile upload.xml download-ivy
2. Manually download and extract via http://ant.apache.org/ivy.

Note for method 1 you may also have to set proxy server settings in advance, eg:
export ANT_OPTS="-Dhttp.proxyHost=<hostname> -Dhttp.proxyPort=<port>"
