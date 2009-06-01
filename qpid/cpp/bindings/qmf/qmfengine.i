
%{

#include "Agent.h"
#include <ResilientConnection.h>

%}


%include <Query.h>
%include <Message.h>
%include <Agent.h>
%include <ResilientConnection.h>
%include <Typecode.h>
%include <Schema.h>
%include <Value.h>
%include <ObjectId.h>
%include <Object.h>

%include <qpid/client/ClientImportExport.h>
%include <qpid/client/ConnectionSettings.h>


%inline {

using namespace std;
using namespace qmf;

namespace qmf {


}
}


%{

%};

