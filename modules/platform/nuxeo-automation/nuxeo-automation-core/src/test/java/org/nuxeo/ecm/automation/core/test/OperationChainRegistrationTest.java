/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.automation.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Properties;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationNotFoundException;
import org.nuxeo.ecm.automation.OperationParameters;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;

/**
 * Test registration of operation chains on service.
 *
 * @sincze 5.7.3
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.automation.core:test-registration-chain.xml")
public class OperationChainRegistrationTest {

    @Inject
    AutomationService service;

    @Inject
    protected HotDeployer hotDeployer;

    /**
     * Check chain properties when using escaped params in operations
     */
    @Test
    public void testChainWithEscapedParams() throws Exception {
        OperationChain chain = service.getOperationChain("chainWithEscapedParams");
        assertEquals(2, chain.getOperations().size());
        OperationParameters params = chain.getOperations().get(1);
        assertEquals(3, params.map().size());
        assertEquals("Note", params.map().get("type"));
        assertEquals("My note > other notes", params.map().get("name"));
        Properties props = new Properties();
        props.put("dc:title", "My note > other notes");
        assertEquals(props, params.map().get("properties"));
    }

    @Test
    @Deploy("org.nuxeo.ecm.automation.core:test-operations.xml")
    public void testDisableChainType() throws Exception {
        service.getOperation("mychain");
        hotDeployer.deploy("org.nuxeo.ecm.automation.core.tests:test-operations-disable.xml");
        try {
            service.getOperation("mychain");
            fail("should not have found a disabled chain");
        } catch (OperationNotFoundException e) {
            assertEquals("No operation was bound on ID: mychain", e.getMessage());
        }
    }

}
