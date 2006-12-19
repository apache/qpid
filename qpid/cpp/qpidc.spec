Name:           qpidc
Version:        0.1
Release:        1%{?dist}
Summary: 	Libraries for Qpid C++ client applications.
Group: 		System Environment/Libraries
License:        Apache
URL:            http://incubator.apache.org/qpid/
# FIXME: Source must be a URL pointing to where the tarball can be downloaded
Source0:        %{name}-%{version}.tar.gz
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

# FIXME: The BR's need to be checked against a clean buildroot [lutter]
BuildRequires: libtool
BuildRequires: boost-devel
BuildRequires: cppunit
BuildRequires: cppunit-devel
BuildRequires: doxygen
BuildRequires: graphviz
BuildRequires: help2man
BuildRequires: pkgconfig

# FIXME: Remove when APR dependency is removed. [aconway]
BuildRequires: e2fsprogs-devel
BuildRequires: apr-devel
Requires: apr

Requires: boost

%description 
Run-time libraries for AMQP client applications developed using Qpid
C++. Clients exchange messages with an AMQP message broker using
the AMQP protocol.

%package devel
Summary: Header files and documentation for  developing Qpid C++ clients.
Group: Development/System
Requires: %name-client = %version-%release
Requires: libtool
Requires: apr-devel
Requires: boost-devel
Requires: cppunit
Requires: cppunit-devel

%description devel
Libraries, header files and documentation for developing AMQP clients
in C++ using Qpid.  Qpid implements the AMQP messaging specification.

%define daemon qpidd
%package -n %{daemon}
Summary: An AMQP message broker daemon.
Group: System Environment/Daemons
Requires: %name-client = %version-%release

%description %{daemon}
A message broker daemon that receives stores and routes messages using
the open AMQP messaging protocol.

%prep
%setup -q

%build
%configure
make %{?_smp_mflags} 

%install
rm -rf $RPM_BUILD_ROOT
make install DESTDIR=$RPM_BUILD_ROOT
rm -f $RPM_BUILD_ROOT%_libdir/*.a
rm -f $RPM_BUILD_ROOT%_libdir/*.la

%clean
rm -rf $RPM_BUILD_ROOT

%check
make check

%files %{name}
%defattr(-,root,root,-)
%doc LICENSE.txt NOTICE.txt
%doc %_docdir/html/* 
%_libdir/libqpidcommon.so.0
%_libdir/libqpidcommon.so.0.1.0
%_libdir/libqpidclient.so.0
%_libdir/libqpidclient.so.0.1.0

%files %{name}-devel
%defattr(-,root,root,-)
%_includedir/qpid/*.h
%_libdir/libqpidcommon.so
%_libdir/libqpidclient.so

%files %{daemon}
%_libdir/libqpidbroker.so.0
%_libdir/libqpidbroker.so.0.1.0
%_sbindir/%{daemon}
%doc %_mandir/man1/%{daemon}.*

#FIXME: Fix Makefile.am to install etc/init.d/%{daemon} properly:
%_sysconfdir/init.d/%{daemon}

%changelog
* Mon Dec 11 2006 Alan Conway <aconway@localhost.localdomain> - 0.1-1
- Second cut, still needs work and testing.

* Fri Dec  8 2006 David Lutterkort <dlutter@redhat.com> - 0.1-1
- Initial version based on Jim Meyering's sketch and discussions with Alan
  Conway

