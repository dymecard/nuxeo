/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.SerializationUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.trash.TrashService;
import org.nuxeo.ecm.core.api.versioning.VersioningService;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestDocumentModel {

    @Inject
    protected CoreSession session;

    @Inject
    protected SchemaManager schemaManager;

    @Inject
    protected HotDeployer hotDeployer;

    /**
     * Tests on a DocumentModel that hasn't been created in the session yet.
     */
    @Test
    public void testDocumentModelNotYetCreated() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        assertTrue(doc.isCheckedOut());
        assertEquals("0.0", doc.getVersionLabel());
        doc.refresh();
    }

    @Test
    public void testContextDataOfCreatedDocument() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc.putContextData("key", "value");
        doc = session.createDocument(doc);
        assertEquals(doc.getContextData("key"), "value");
    }

    /**
     * NXP-21866
     */
    @Test
    public void testUIDAndPathOfCreatedDocument() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        assertNotNull(doc.getId());
        assertEquals("/doc", doc.getPathAsString());
    }

    /**
     * NXP-21866
     */
    @Test
    public void testUIDAndPathOfCreatedDocumentWithSkipVersioning() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc.putContextData(VersioningService.SKIP_VERSIONING, Boolean.TRUE);
        doc = session.createDocument(doc);
        assertNotNull(doc.getId());
        assertEquals("/doc", doc.getPathAsString());
    }

    @Test
    public void testDetachAttach() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        assertEquals("project", doc.getCurrentLifeCycleState());
        assertEquals("0.0", doc.getVersionLabel());

        doc.detach(false);
        doc.prefetchCurrentLifecycleState(null);
        assertNull(doc.getCurrentLifeCycleState());
        assertEquals("0.0", doc.getVersionLabel()); // version label always available

        doc.attach(session);
        session.saveDocument(doc);
        assertEquals("project", doc.getCurrentLifeCycleState());
        assertEquals("0.0", doc.getVersionLabel());
    }

    /**
     * Verifies that checked out state, lifecycle state and lock info are stored on a detached document.
     */
    @Test
    public void testDetachedSystemInfo() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.setLock();

        // refetch to clear lock info
        doc = session.getDocument(new IdRef(doc.getId()));
        // check in
        doc.checkIn(VersioningOption.MAJOR, null);
        // clear lifecycle info
        doc.prefetchCurrentLifecycleState(null);

        doc.detach(true);
        assertFalse(doc.isCheckedOut());
        assertEquals("project", doc.getCurrentLifeCycleState());
        assertNotNull(doc.getLockInfo());

        // refetch to clear lock info
        doc = session.getDocument(new IdRef(doc.getId()));
        // checkout
        doc.checkOut();
        // clear lifecycle info
        doc.prefetchCurrentLifecycleState(null);

        doc.detach(true);
        assertTrue(doc.isCheckedOut());
        assertEquals("project", doc.getCurrentLifeCycleState());
        assertNotNull(doc.getLockInfo());
    }

    @Test
    public void testDocumentLiveSerialization() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.getProperty("common:icon").setValue("prefetched");
        doc.getProperty("dublincore:language").setValue("not-prefetch");
        doc = session.saveDocument(doc);

        Assertions.assertThat(doc.getCoreSession()).isNotNull();

        doc = SerializationUtils.clone(doc);

        assertThat(doc.getCoreSession()).isNull();
        assertThat(doc.getName()).isEqualTo("doc");
        assertThat(doc.getProperty("common:icon").getValue(String.class)).isEqualTo("prefetched");
        assertThat(doc.getProperty("dublincore:language").getValue(String.class)).isEqualTo("not-prefetch");
    }

    @Test
    public void testDocumentDirtySerialization() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.getProperty("dublincore:source").setValue("Source");

        assertThat(doc.isDirty()).isTrue();

        doc = SerializationUtils.clone(doc);

        assertThat(doc.getCoreSession()).isNull();
        assertThat(doc.getProperty("dublincore:source").getValue(String.class)).isEqualTo("Source");
    }

    @Test
    public void testDocumentDeletedSerialization() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.getProperty("dublincore:title").setValue("doc"); // prefetch
        doc.getProperty("dublincore:source").setValue("Source"); // not prefetch

        session.removeDocument(doc.getRef());

        assertThat(session.exists(doc.getRef())).isFalse();

        doc = SerializationUtils.clone(doc);

        assertThat(doc.getCoreSession()).isNull();
        assertThat(doc.getProperty("dublincore:title").getValue(String.class)).isEqualTo("doc");
        assertThat(doc.getProperty("dublincore:source").getValue(String.class)).isEqualTo("Source");
    }

    @Test
    public void testDetachedDocumentSerialization() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.getProperty("dublincore:source").setValue("Source");
        doc.detach(false);

        assertThat(doc.getCoreSession()).isNull();

        doc = SerializationUtils.clone(doc);

        assertThat(doc.getCoreSession()).isNull();
        assertThat(doc.getName()).isEqualTo("doc");
        assertThat(doc.getProperty("dublincore:source").getValue(String.class)).isEqualTo("Source");
    }

    @Test
    public void testTrashedDetachedDocumentSerialization() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        assertFalse(doc.isTrashed());

        Framework.getService(TrashService.class).trashDocument(doc);
        doc.detach(true);

        doc = SerializationUtils.clone(doc);

        assertTrue(doc.isTrashed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void forbidSlashOnCreate() {
        session.createDocumentModel("/", "doc/doc", "File");
    }

    @Test(expected = IllegalArgumentException.class)
    public void forbidSlashOnMove() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        session.move(doc.getRef(), new PathRef("/"), "toto/tata");
    }

    @Test(expected = IllegalArgumentException.class)
    public void forbidSlashOnCopy() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc = session.createDocument(doc);
        session.copy(doc.getRef(), new PathRef("/"), "toto/tata");
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-doctype-disableable-contrib.xml")
    public void testDocumentTypeDisabled() throws Exception {
        assertNotNull(schemaManager.getDocumentType("disabledDoctype"));
        hotDeployer.deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-doctype-disableable-disable-contrib.xml");
        assertNull(schemaManager.getDocumentType("disabledDoctype"));
    }

    /**
     * @since 2021.17
     */
    @Test
    public void testDocumentModelComparator() {
        // Let's sort 2 docs on a date field
        DocumentModel doc1 = new DocumentModelImpl("/", "doc1", "File");
        Calendar cal1 = new GregorianCalendar(2000, 1, 1);
        doc1.setPropertyValue("dc:expired", cal1);
        DocumentModel doc2 = new DocumentModelImpl("/", "doc2", "File");
        Calendar cal2 = new GregorianCalendar(2022, 1, 1);
        doc2.setPropertyValue("dc:expired", cal2);
        // cal1 is earlier
        assertTrue(cal1.compareTo(cal2) < 0);
        // but the toString for year 2000 starts with 9 so sorts higher
        cal1.getTime(); // trigger time field computation
        cal2.getTime();
        assertTrue(cal1.toString().compareTo(cal2.toString()) > 0);
        // nevertheless doc comparison works based on date, not string
        var docs = new ArrayList<>(List.of(doc1, doc2));
        DocumentModelComparator comp = new DocumentModelComparator("dublincore", Map.of("dc:expired", "desc"));
        docs.sort(comp);
        assertEquals(doc2, docs.get(0));
        assertEquals(doc1, docs.get(1));
    }

}
