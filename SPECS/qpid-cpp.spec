Name:           qpid-cpp
Version:        0.1
Release:        1%{?dist}
Summary:        Qpid is an implementation of the AMQP messaging specification. 
Group:          System Environment/Daemons
License:        Apache
URL:            http://incubator.apache.org/qpid/
# FIXME: Source must be a URL pointing to where the tarball can be downloaded
Source0:        %{name}-%{version}.tar.gz
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

# FIXME: The BR's need to be checked against a clean buildroot [lutter]
BuildRequires: libtool
BuildRequires: apr-devel
BuildRequires: boost-devel
BuildRequires: cppunit
BuildRequires: cppunit-devel
BuildRequires: doxygen
BuildRequires: graphviz
BuildRequires: help2man
BuildRequires: pkgconfig

# FIXME: aconway don't think this is required, I don't have it installed.
# BuildRequires: check-devel
# FIXME: For libuuid. aconway: Why do we depend on libuuid?
BuildRequires: e2fsprogs-devel

Requires: apr
Requires: boost

%description
Qpid-cpp is a C++ implementation of the AMQP messaging specification.

%package client
Summary: Libraries for Qpid client applications.
Group: System Environment/Libraries

%description client
Run-time libraries for Qpid C++ clients. Qpid clients exchange messages
with an AMQP message broker using the AMQP protocol.

%package client-devel
Summary: Headers & libraries for developing Qpid client applications.
Group: Development/System
Requires: %name-client = %version-%release
Requires: libtool
Requires: apr-devel
Requires: boost-devel
Requires: cppunit
Requires: cppunit-devel

%description client-devel
Libraries and header files for developing AMQP clients in C++ using Qpid.
Qpid implements the AMQP messaging specification. 


%package broker
Summary: The Qpid message broker daemon.
Group: System Environment/Daemons
Requires: %name-client = %version-%release

%description broker
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
mkdir -p $RPM_BUILD_ROOT%_localstatedir/run/qpid
mkdir -p $RPM_BUILD_ROOT%_localstatedir/lib/qpid
rm -f $RPM_BUILD_ROOT%_libdir/*.a
rm -f $RPM_BUILD_ROOT%_libdir/*.la

%clean
rm -rf $RPM_BUILD_ROOT

%check
make check

%files client
%defattr(-,root,root,-)
# FIXME: A Changelog or NEWS file might be nice
%doc LICENSE.txt NOTICE.txt 
%_libdir/libqpidcommon.so.0
%_libdir/libqpidcommon.so.0.1.0
%_libdir/libqpidclient.so.0
%_libdir/libqpidclient.so.0.1.0

%files client-devel
%defattr(-,root,root,-)
%_includedir/qpid/*.h
%_libdir/libqpidcommon.so
%_libdir/libqpidclient.so

%files broker
%_libdir/libqpidbroker.so.0
%_libdir/libqpidbroker.so.0.1.0
%_sbindir/qpidd
%_mandir/man1/qpidd.*
%_localstatedir/run/qpid
%_localstatedir/lib/qpid

#FIXME: Fix Makefile.am to install etc/init.d/qpidd properly:
#%_sysconfdir/rc.d/init.d/qpidd

%changelog
* Mon Dec 11 2006 Alan Conway <aconway@localhost.localdomain> - 0.1-1
- Second cut, still needs work and testing.

* Fri Dec  8 2006 David Lutterkort <dlutter@redhat.com> - 0.1-1
- Initial version based on Jim Meyering's sketch and discussions with Alan
  Conway

