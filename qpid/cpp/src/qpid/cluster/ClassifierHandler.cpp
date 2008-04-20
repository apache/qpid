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

#include "ClassifierHandler.h"

#include "qpid/framing/FrameDefaultVisitor.h"
#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace cluster {

using namespace framing;

struct ClassifierHandler::Visitor : public FrameDefaultVisitor {
    Visitor(AMQFrame& f, ClassifierHandler& c)
        : chosen(0), frame(f), classifier(c) { f.getBody()->accept(*this); }

    void visit(const ExchangeDeclareBody&) { chosen=&classifier.wiring; }
    void visit(const ExchangeDeleteBody&) { chosen=&classifier.wiring; }
    void visit(const ExchangeBindBody&) { chosen=&classifier.wiring; }
    void visit(const ExchangeUnbindBody&) { chosen=&classifier.wiring; }
    void visit(const QueueDeclareBody&) { chosen=&classifier.wiring; }
    void visit(const QueueDeleteBody&) { chosen=&classifier.wiring; }
    void defaultVisit(const AMQBody&) { chosen=&classifier.other; }

    using framing::FrameDefaultVisitor::visit;
    using framing::FrameDefaultVisitor::defaultVisit;

    FrameHandler::Chain chosen;
    AMQFrame& frame;
    ClassifierHandler& classifier;
};

void ClassifierHandler::handle(AMQFrame& f) { Visitor(f, *this).chosen(f); }

}} // namespace qpid::cluster
