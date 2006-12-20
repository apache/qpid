#
# Spec file for Qpid C++ packages: qpidc qpidc-devel, qpidd
#
%define daemon qpidd

Name:           qpidc
Version:        0.1
Release:        1%{?dist}
Summary:        Libraries for Qpid C++ client applications
Group:          System Environment/Libraries
License:        Apache Software License
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

Requires(post):/sbin/chkconfig
Requires(preun):/sbin/chkconfig
Requires(preun):/sbin/service
Requires(postun): /sbin/service

%description 
Run-time libraries for AMQP client applications developed using Qpid
C++. Clients exchange messages with an AMQP message broker using
the AMQP protocol.

%package devel
Summary: Header files and documentation for  developing Qpid C++ clients.
Group: Development/System
Requires: %name = %version-%release
Requires: libtool
Requires: apr-devel
Requires: boost-devel
Requires: cppunit
Requires: cppunit-devel

%description devel
Libraries, header files and documentation for developing AMQP clients
in C++ using Qpid.  Qpid implements the AMQP messaging specification.

%package -n %{daemon}
Summary: An AMQP message broker daemon.
Group: System Environment/Daemons
Requires: %name = %version-%release

%description -n %{daemon}
A message broker daemon that receives stores and routes messages using
the open AMQP messaging protocol.

%prep
%setup -q

%build
%configure
make %{?_smp_mflags} 

%install
rm -rf %{buildroot}
make install DESTDIR=%{buildroot}
install  -Dp -m0755 etc/qpidd %{buildroot}%{_initrddir}/qpidd
rm -f %{buildroot}%_libdir/*.a
rm -f %{buildroot}%_libdir/*.la


%clean
rm -rf %{buildroot}

%check
make check

%files
%defattr(-,root,root,-)
%doc LICENSE.txt NOTICE.txt README.txt
%_libdir/libqpidcommon.so.0
%_libdir/libqpidcommon.so.0.1.0
%_libdir/libqpidclient.so.0
%_libdir/libqpidclient.so.0.1.0

%files devel
%defattr(-,root,root,-)
%_includedir/qpidc
%_libdir/libqpidcommon.so
%_libdir/libqpidclient.so
%doc docs/api/html

%files -n %{daemon}
%_libdir/libqpidbroker.so
%_libdir/libqpidbroker.so.0
%_libdir/libqpidbroker.so.0.1.0
%_sbindir/%{daemon}
%{_initrddir}/qpidd
%doc %_mandir/man1/%{daemon}.*

%post
# This adds the proper /etc/rc*.d links for the script
/sbin/chkconfig --add qpidd

%preun
# Check that this is actual deinstallation, not just removing for upgrade.
if [ $1 = 0 ]; then
        /sbin/service qpidd stop >/dev/null 2>&1 || :
        /sbin/chkconfig --del qpidd
fi

%postun
if [ "$1" -ge "1" ]; then
        /sbin/service qpidd condrestart >/dev/null 2>&1 || :
fi

%changelog
* Mon Dec 19 2006 Alan Conway <aconway@redhat.com> - 0.1-1
- Fixed problems with qpidd init script and doc files.

* Fri Dec  8 2006 David Lutterkort <dlutter@redhat.com> - 0.1-1
- Initial version based on Jim Meyering's sketch and discussions with Alan
  Conway

