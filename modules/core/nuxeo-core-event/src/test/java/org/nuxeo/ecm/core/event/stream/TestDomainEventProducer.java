/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bdelbosc
 */
package org.nuxeo.ecm.core.event.stream;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.event.CoreEventFeature;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.lib.stream.log.LogLag;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 11.4
 */
@RunWith(FeaturesRunner.class)
@Features(CoreEventFeature.class)
@Deploy("org.nuxeo.ecm.core.event:test-stream-domain-event-producer-contrib.xml")
public class TestDomainEventProducer {

    @Inject
    protected EventService service;

    @Inject
    protected StreamService streamService;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Test
    public void testDomainEventProducer() {
        LogManager logManager = streamService.getLogManager();
        LogLag lag = logManager.getLag(Name.ofUrn("source/test"), Name.ofUrn("test/DomainEvent"));
        assertEquals(0, lag.lag());

        Event event1 = new EventImpl("event1", new EventContextImpl());
        Event event2 = new EventImpl("event2", new EventContextImpl());

        // event #1
        service.fireEvent(event1);
        transactionalFeature.nextTransaction();

        // event will not be appended to domain event streams because of rollback
        service.fireEvent(event1);
        TransactionHelper.setTransactionRollbackOnly();
        // Not using transactionalFeature.nextTransaction() because it propagates the rollback mark
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // event #2
        service.fireEvent(event1);
        // event #3
        service.fireEvent(event2);
        TransactionHelper.commitOrRollbackTransaction();

        // event outside of a transaction
        // event #4
        service.fireEvent(event1);

        TransactionHelper.startTransaction();
        lag = logManager.getLag(Name.ofUrn("source/test"), Name.ofUrn("test/DomainEvent"));
        assertEquals(4, lag.lag());
    }

}
