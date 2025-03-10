/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.test;

import static org.nuxeo.ecm.core.model.Session.PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.runners.model.FrameworkMethod;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.event.CoreEventFeature;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;
import org.nuxeo.ecm.core.work.WorkManagerFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.cluster.ClusterFeature;
import org.nuxeo.runtime.model.URLStreamRef;
import org.nuxeo.runtime.stream.RuntimeStreamFeature;
import org.nuxeo.runtime.test.runner.Defaults;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Binder;

/**
 * The core feature provides a default {@link CoreSession} that can be injected.
 * <p>
 * The provided {@link CoreSession} is opened for the principal logged in by the {@link DummyLoginFeature}.
 * <p>
 * In addition, by injecting the feature itself, some helper methods are available to open new sessions.
 */
@Deploy("org.nuxeo.runtime.management")
@Deploy("org.nuxeo.runtime.metrics")
@Deploy("org.nuxeo.runtime.reload")
@Deploy("org.nuxeo.runtime.kv")
@Deploy("org.nuxeo.runtime.pubsub")
@Deploy("org.nuxeo.runtime.mongodb")
@Deploy("org.nuxeo.runtime.migration")
@Deploy("org.nuxeo.runtime.stream")
@Deploy("org.nuxeo.ecm.core.schema")
@Deploy("org.nuxeo.ecm.core.query")
@Deploy("org.nuxeo.ecm.core.api")
@Deploy("org.nuxeo.ecm.core")
@Deploy("org.nuxeo.ecm.core.io")
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.core.test")
@Deploy("org.nuxeo.ecm.core.mimetype")
@Deploy("org.nuxeo.ecm.core.convert")
@Deploy("org.nuxeo.ecm.core.convert.plugins")
@Deploy("org.nuxeo.ecm.core.storage")
@Deploy("org.nuxeo.ecm.core.storage.sql")
@Deploy("org.nuxeo.ecm.core.storage.sql.test")
@Deploy("org.nuxeo.ecm.core.storage.dbs")
@Deploy("org.nuxeo.ecm.core.storage.mem")
@Deploy("org.nuxeo.ecm.core.storage.mongodb")
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.platform.el")
@RepositoryConfig(cleanup = Granularity.METHOD)
@Features({ ClusterFeature.class, //
        TransactionalFeature.class, //
        RuntimeStreamFeature.class, //
        DummyLoginFeature.class, //
        CoreEventFeature.class, //
        WorkManagerFeature.class, //
        CoreBulkFeature.class })
public class CoreFeature implements RunnerFeature {

    protected ACP rootAcp;

    private static final Log log = LogFactory.getLog(CoreFeature.class);

    protected StorageConfiguration storageConfiguration;

    protected RepositoryInit repositoryInit;

    protected Granularity granularity;

    // this value gets injected
    protected CoreSession session;

    protected boolean cleaned;

    // no injection on features because we need it during beforeRun (injection has not been done at this stage)
    protected TransactionalFeature txFeature;

    protected DummyLoginFeature loginFeature;

    public StorageConfiguration getStorageConfiguration() {
        return storageConfiguration;
    }

    @Override
    public void initialize(FeaturesRunner runner) {
        runner.getFeature(RuntimeFeature.class).registerHandler(new CoreDeployer());

        storageConfiguration = new StorageConfiguration(this);
        txFeature = runner.getFeature(TransactionalFeature.class);
        loginFeature = runner.getFeature(DummyLoginFeature.class);
        // init from RepositoryConfig annotations
        RepositoryConfig repositoryConfig = runner.getConfig(RepositoryConfig.class);
        if (repositoryConfig == null) {
            repositoryConfig = Defaults.of(RepositoryConfig.class);
        }
        try {
            repositoryInit = repositoryConfig.init().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException(e);
        }
        Granularity cleanup = repositoryConfig.cleanup();
        granularity = cleanup == Granularity.UNDEFINED ? Granularity.CLASS : cleanup;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    @Override
    public void start(FeaturesRunner runner) {
        try {
            RuntimeHarness harness = runner.getFeature(RuntimeFeature.class).getHarness();
            storageConfiguration.init();
            for (String bundle : storageConfiguration.getExternalBundles()) {
                try {
                    harness.deployBundle(bundle);
                } catch (Exception e) {
                    throw new NuxeoException(e);
                }
            }
            URL blobContribUrl = storageConfiguration.getBlobManagerContrib(runner);
            harness.getContext().deploy(new URLStreamRef(blobContribUrl));
            URL repoContribUrl = storageConfiguration.getRepositoryContrib(runner);
            harness.getContext().deploy(new URLStreamRef(repoContribUrl));
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void beforeRun(FeaturesRunner runner) {
        // wait for async tasks that may have been triggered by
        // RuntimeFeature (typically repo initialization)
        txFeature.nextTransaction(Duration.ofSeconds(10));
        if (granularity != Granularity.METHOD) {
            // we need a transaction to properly initialize the session
            // but it hasn't been started yet by TransactionalFeature
            TransactionHelper.startTransaction();
            initializeSession(); // create the session for us
            TransactionHelper.commitOrRollbackTransaction();
        }
    }

    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        binder.bind(CoreSession.class).toProvider(() -> session);
    }

    @Override
    public void afterRun(FeaturesRunner runner) {
        waitForAsyncCompletion(); // fulltext and various workers
        if (granularity != Granularity.METHOD) {
            cleanupSession(); // close the session for us
        }
    }

    @Override
    public void beforeSetup(FeaturesRunner runner, FrameworkMethod method, Object test) {
        if (granularity == Granularity.METHOD) {
            initializeSession(); // create the session for us
        }
    }

    @Override
    public void afterTeardown(FeaturesRunner runner, FrameworkMethod method, Object test) {
        waitForAsyncCompletion();
        if (granularity == Granularity.METHOD) {
            cleanupSession(); // close the session for us
        }
    }

    public void waitForAsyncCompletion() {
        txFeature.nextTransaction();
    }

    protected void cleanupSession() {
        releaseCoreSession(); // release session for tests
        // open an administrator session to cleanup repository
        TransactionHelper.runInNewTransaction(() -> {
            try {
                CoreSession adminSession = getCoreSession(createAdministrator());
                log.trace("remove everything except root");
                // remove proxies first, as we cannot remove a target if there's a proxy pointing to it
                try (IterableQueryResult results = adminSession.queryAndFetch(
                        "SELECT ecm:uuid FROM Document WHERE ecm:isProxy = 1", NXQL.NXQL)) {
                    batchRemoveDocuments(adminSession, results);
                } catch (QueryParseException e) {
                    // ignore, proxies disabled
                }
                // remove non-proxies
                Framework.getProperties().put(PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS, "true");
                try {
                    adminSession.removeChildren(new PathRef("/"));
                    waitForAsyncCompletion();
                } finally {
                    Framework.getProperties().remove(PROP_ALLOW_DELETE_UNDELETABLE_DOCUMENTS);
                }
                log.trace(
                        "remove orphan versions as OrphanVersionRemoverListener is not triggered by CoreSession#removeChildren");
                // remove remaining placeless documents
                try (IterableQueryResult results = adminSession.queryAndFetch("SELECT ecm:uuid FROM Document, Relation",
                        NXQL.NXQL)) {
                    batchRemoveDocuments(adminSession, results);
                }
                waitForAsyncCompletion();

                // set original ACP on root
                DocumentModel root = adminSession.getRootDocument();
                root.setACP(rootAcp, true);

                adminSession.save();
                waitForAsyncCompletion();
                if (!adminSession.query("SELECT * FROM Document, Relation").isEmpty()) {
                    log.error("Fail to cleanupSession, repository will not be empty for the next test.");
                }
            } catch (NuxeoException e) {
                log.error("Unable to reset repository", e);
            } finally {
                CoreScope.INSTANCE.exit();
            }
        });
        cleaned = true;
    }

    protected void batchRemoveDocuments(CoreSession adminSession, IterableQueryResult results) {
        String rootDocumentId = adminSession.getRootDocument().getId();
        List<DocumentRef> ids = new ArrayList<>();
        for (Map<String, Serializable> result : results) {
            String id = (String) result.get("ecm:uuid");
            if (id.equals(rootDocumentId)) {
                continue;
            }
            ids.add(new IdRef(id));
            if (ids.size() >= 100) {
                batchRemoveDocuments(adminSession, ids);
                ids.clear();
            }
        }
        if (!ids.isEmpty()) {
            batchRemoveDocuments(adminSession, ids);
        }
    }

    protected void batchRemoveDocuments(CoreSession adminSession, List<DocumentRef> ids) {
        List<DocumentRef> deferredIds = new ArrayList<>();
        for (DocumentRef id : ids) {
            if (!adminSession.exists(id)) {
                continue;
            }
            if (adminSession.canRemoveDocument(id)) {
                adminSession.removeDocument(id);
            } else {
                deferredIds.add(id);
            }
        }
        adminSession.removeDocuments(deferredIds.toArray(new DocumentRef[0]));
    }

    protected void initializeSession() {
        if (cleaned) {
            // reinitialize repositories content
            RepositoryService repositoryService = Framework.getService(RepositoryService.class);
            repositoryService.initRepositories();
            cleaned = false;
        }
        CoreScope.INSTANCE.enter();
        // populate repository and get root acp
        Framework.doPrivileged(() -> {
            CoreSession adminSession = getCoreSession(createAdministrator());
            if (repositoryInit != null) {
                repositoryInit.populate(adminSession);
                adminSession.save();
                waitForAsyncCompletion();
            }
            // save current root acp
            DocumentModel root = adminSession.getRootDocument();
            rootAcp = root.getACP();
        });
        // open session for test
        createCoreSession();
    }

    public String getRepositoryName() {
        return getStorageConfiguration().getRepositoryName();
    }

    public CoreSession getCoreSession(String username) {
        return CoreInstance.getCoreSession(getRepositoryName(), username);
    }

    public CoreSession getCoreSession(NuxeoPrincipal principal) {
        return CoreInstance.getCoreSession(getRepositoryName(), principal);
    }

    public CoreSession getCoreSessionCurrentUser() {
        return CoreInstance.getCoreSession(getRepositoryName());
    }

    public CoreSession getCoreSessionSystem() {
        return CoreInstance.getCoreSessionSystem(getRepositoryName());
    }

    /** @deprecated since 11.1, use {@link #getCoreSession(String)} instead */
    @Deprecated
    public CloseableCoreSession openCoreSession(String username) {
        return (CloseableCoreSession) getCoreSession(username);
    }

    /** @deprecated since 11.1, use {@link #getCoreSession(NuxeoPrincipal)} instead */
    @Deprecated
    public CloseableCoreSession openCoreSession(NuxeoPrincipal principal) {
        return (CloseableCoreSession) getCoreSession(principal);
    }

    /** @deprecated since 11.1, use {@link #getCoreSessionCurrentUser()} instead */
    @Deprecated
    public CloseableCoreSession openCoreSession() {
        return (CloseableCoreSession) getCoreSessionCurrentUser();
    }

    /** @deprecated since 11.1, use {@link #getCoreSessionSystem()} instead */
    @Deprecated
    public CloseableCoreSession openCoreSessionSystem() {
        return (CloseableCoreSession) getCoreSessionSystem();
    }

    public CoreSession createCoreSession() {
        NuxeoPrincipal principal = loginFeature.getPrincipal();
        session = CoreInstance.getCoreSession(getRepositoryName(), principal);
        return session;
    }

    public CoreSession getCoreSession() {
        return session;
    }

    public void releaseCoreSession() {
        session = null;
    }

    public CoreSession reopenCoreSession() {
        releaseCoreSession();
        waitForAsyncCompletion();
        Framework.getService(RepositoryService.class).resetPool();
        createCoreSession();
        return session;
    }

    protected NuxeoPrincipal createAdministrator() {
        return new UserPrincipal("Administrator", List.of(), false, true);
    }

    public class CoreDeployer extends HotDeployer.ActionHandler {

        @Override
        public void exec(String action, String... agrs) throws Exception {
            waitForAsyncCompletion();
            releaseCoreSession();
            next.exec(action, agrs);
            createCoreSession();
        }

    }

}
