#ifndef _framing_amqp_types_decl_h
#define _framing_amqp_types_decl_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/** \file
 * Definitions and full declarations of all types used
 * in AMQP messages.
 *
 * It's better to include amqp_types.h in another header instead of this file
 * unless the header actually needs the full declarations. Including
 * full declarations when forward declarations would increase compile
 * times.
 */

#include "amqp_types.h"
#include "Array.h"
#include "FieldTable.h"
#include "SequenceSet.h"
#include "Uuid.h"

#endif  /*!_framing_amqp_types_decl_h*/
