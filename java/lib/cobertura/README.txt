Download the cobertura binary from the following location:

http://cobertura.sourceforge.net/download.html


Unpack it into the cobertura (this) directory and then move the contents up one level i.e. once unpacked it will create a cobertura-<version>. directory with the cobertura.jar and scripts and a lib dir. All this content needs to move up to the cobertura directory proper.

Run "ant cover-test coverage-report" to generate coverage report.

