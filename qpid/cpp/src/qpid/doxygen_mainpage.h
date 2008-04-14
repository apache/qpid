// This header file is just for doxygen documentation purposes.

/*!\mainpage Qpid C++ Developer Kit.
 *
 *\section intro_sec Introduction
 *
 * The <a href=http://incubator.apache.org/qpid/index.html>Qpid project</a> provides implementations of the <a href="http://amqp.org/">AMQP messaging specification</a> in several programming language.
 * 
 * Qpidc provides APIs and libraries to implement AMQP
 * clients in C++. Qpidc clients can interact with any compliant AMQP
 * message broker. The Qpid project also provides an AMQP broker
 * daemon called qpidd that you can use with your qpidc clients.
 *
 *\section install_sec Installation
 *
 * If you are installing from the source distribution
 <pre>
   > ./configure && make
   > make install  </pre>
 * This will build and install the client development kit and the broker
 * in standard places. Use
 * <code>./configure --help</code> for more options.
 * 
 * You can also install from RPMs with the <code>rpm -i</code> command.
 * You will need
 *  - <code>qpidc</code> for core libraries.
 *  - <code>qpidc-devel</code> for header files and developer documentation.
 *  - <code>qpidd</code> for the broker daemon.
 *
 *\section getstart_sec Getting Started
 * 
 * If you have installed in the standard places you should use
 * these compile flags:
 * 
 *<code>  -I/usr/include/qpidc -I/usr/include/qpidc/framing -I/usr/include/qpidc/sys</code>
 *
 * and these link flags:
 * 
 *<code>  -lqpidcommon -lqpidclient</code>
 *
 * If you have installed somewhere else you should modify the flags
 * appropriately.
 *
 * See the \ref clientapi "client API module" for more on the client API. 
 */
