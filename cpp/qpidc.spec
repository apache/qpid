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
URL:            http://people.apache.org/dist/incubator/qpid/M1-incubating/cpp/qpidc-0.1.tar.gz
Source0:        %{name}-%{version}.tar.gz
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

BuildRequires: libtool
BuildRequires: boost-devel
BuildRequires: cppunit
BuildRequires: cppunit-devel
BuildRequires: doxygen
BuildRequires: graphviz
BuildRequires: help2man
BuildRequires: pkgconfig
BuildRequires: e2fsprogs-devel
BuildRequires: apr-devel

Requires: boost

Requires(post):/sbin/chkconfig
Requires(preun):/sbin/chkconfig
Requires(preun):/sbin/service
Requires(postun):/sbin/service

%description
Run-time libraries for AMQP client applications developed using Qpid
C++. Clients exchange messages with an AMQP message broker using
the AMQP protocol.

%package devel
Summary: Header files and documentation for developing Qpid C++ clients
Group: Development/System
Requires: %name = %version-%release
Requires: libtool
Requires: apr-devel
Requires: boost-devel
Requires: cppunit-devel

%description devel
Libraries, header files and documentation for developing AMQP clients
in C++ using Qpid.  Qpid implements the AMQP messaging specification.

%package -n %{daemon}
Summary: An AMQP message broker daemon
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
# Remove this generated perl file, we don't need it and it upsets rpmlint.
rm docs/api/html/installdox

%install
rm -rf %{buildroot}
make install DESTDIR=%{buildroot}
install  -Dp -m0755 etc/qpidd %{buildroot}%{_initrddir}/qpidd
rm -f %{buildroot}%_libdir/*.a
rm -f %{buildroot}%_libdir/*.la
# There's no qpidd-devel package so no .so for the broker needed.
rm -f %{buildroot}%_libdir/libqpidbroker.so


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
# We don't need this perl script and it causes rpmlint to complain.
# There is probably a more polite way of calculating the devel docdir.

%files -n %{daemon}
%_libdir/libqpidbroker.so.0
%_libdir/libqpidbroker.so.0.1.0
%_sbindir/%{daemon}
%{_initrddir}/qpidd
%doc %_mandir/man1/%{daemon}.*

%post -p /sbin/ldconfig

%postun -p /sbin/ldconfig

%post -n %{daemon}
# This adds the proper /etc/rc*.d links for the script
/sbin/chkconfig --add qpidd
/sbin/ldconfig

%preun -n %{daemon}
# Check that this is actual deinstallation, not just removing for upgrade.
if [ $1 = 0 ]; then
        /sbin/service qpidd stop >/dev/null 2>&1 || :
        /sbin/chkconfig --del qpidd
fi

%postun -n %{daemon}
if [ "$1" -ge "1" ]; then
        /sbin/service qpidd condrestart >/dev/null 2>&1 || :
fi
/sbin/ldconfig

%changelog

* Mon Dec 22 2006 Alan Conway <aconway@redhat.com> - 0.1-1
- Fixed all rpmlint complaints (with help from David Lutterkort)
- Added qpidd --daemon behaviour, fix init.rc scripts

* Fri Dec  8 2006 David Lutterkort <dlutter@redhat.com> - 0.1-1
- Initial version based on Jim Meyering's sketch and discussions with Alan
  Conway
