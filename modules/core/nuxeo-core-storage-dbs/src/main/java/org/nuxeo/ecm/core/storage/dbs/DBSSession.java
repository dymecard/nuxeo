/*
 * (C) Copyright 2014-2021 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.storage.dbs;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.action.DeletionAction.ACTION_NAME;
import static org.nuxeo.ecm.core.api.AbstractSession.DISABLED_ISLATESTVERSION_PROPERTY;
import static org.nuxeo.ecm.core.api.CoreSession.BINARY_FULLTEXT_MAIN_KEY;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.FACETED_TAG;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.FACETED_TAG_LABEL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.INITIAL_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.INITIAL_SYS_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_BEGIN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_CREATOR;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_END;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_GRANT;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_PERMISSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_STATUS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_USER;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACL_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACP;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_BASE_VERSION_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_BLOB_KEYS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_FULLTEXT_JOBID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_FULLTEXT_SCORE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_FULLTEXT_SIMPLE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_HAS_LEGAL_HOLD;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_LATEST_MAJOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_LATEST_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_PROXY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_RECORD;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_RETENTION_ACTIVE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_TRASHED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LIFECYCLE_POLICY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_CREATED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_OWNER;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MAJOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MINOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MIXIN_TYPES;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PARENT_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PATH_INTERNAL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_POS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_TARGET_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_VERSION_SERIES_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_READ_ACL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_SYS_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_CREATED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_DESCRIPTION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_LABEL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_SERIES_ID;

import java.io.IOException;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentExistsException;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.core.api.lock.LockManager;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.model.BaseSession;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.ecm.core.query.QueryFilter;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.OrderByList;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.storage.BaseDocument;
import org.nuxeo.ecm.core.storage.ExpressionEvaluator;
import org.nuxeo.ecm.core.storage.QueryOptimizer;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.StateHelper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.transaction.TransactionHelper;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;

/**
 * Implementation of a {@link Session} for Document-Based Storage.
 *
 * @since 5.9.4
 */
public class DBSSession extends BaseSession {

    private static final Logger log = LogManager.getLogger(DBSSession.class);

    protected static final Set<String> KEYS_RETENTION_HOLD_AND_PROXIES = new HashSet<>(Arrays.asList(KEY_RETAIN_UNTIL,
            KEY_HAS_LEGAL_HOLD, KEY_IS_RETENTION_ACTIVE, KEY_IS_PROXY, KEY_PROXY_TARGET_ID, KEY_PROXY_IDS));

    protected final DBSTransactionState transaction;

    protected final boolean fulltextStoredInBlob;

    protected final boolean fulltextSearchDisabled;

    protected final boolean changeTokenEnabled;

    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    private final Timer saveTimer;

    private final Timer queryTimer;

    private static final String LOG_MIN_DURATION_KEY = "org.nuxeo.dbs.query.log_min_duration_ms";

    private long LOG_MIN_DURATION_NS = -1 * 1000000;

    protected boolean isLatestVersionDisabled = false;

    public DBSSession(DBSRepository repository) {
        super(repository);
        transaction = new DBSTransactionState(repository, this);
        FulltextConfiguration fulltextConfiguration = repository.getFulltextConfiguration();
        fulltextStoredInBlob = fulltextConfiguration != null && fulltextConfiguration.fulltextStoredInBlob;
        fulltextSearchDisabled = fulltextConfiguration == null || fulltextConfiguration.fulltextSearchDisabled;
        changeTokenEnabled = repository.isChangeTokenEnabled();

        saveTimer = registry.timer(MetricName.build("nuxeo", "repositories", "repository", "save")
                                             .tagged("repository", repository.getName()));
        queryTimer = registry.timer(MetricName.build("nuxeo", "repositories", "repository", "query")
                                              .tagged("repository", repository.getName()));
        LOG_MIN_DURATION_NS = Long.parseLong(Framework.getProperty(LOG_MIN_DURATION_KEY, "-1")) * 1000000;
        isLatestVersionDisabled = Framework.isBooleanPropertyTrue(DISABLED_ISLATESTVERSION_PROPERTY);
    }

    @Override
    public String getRepositoryName() {
        return repository.getName();
    }

    @Override
    public void destroy() {
        transaction.close();
    }

    @SuppressWarnings("resource") // timerContext closed by stop() in finally
    @Override
    public void save() {
        final Timer.Context timerContext = saveTimer.time();
        try {
            transaction.save();
            if (!TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                transaction.commit();
            }
        } finally {
            timerContext.stop();
        }
    }

    protected String getRootId() {
        return transaction.getRootId();
    }

    /*
     * Normalize using NFC to avoid decomposed characters (like 'e' + COMBINING ACUTE ACCENT instead of LATIN SMALL
     * LETTER E WITH ACUTE). NFKC (normalization using compatibility decomposition) is not used, because compatibility
     * decomposition turns some characters (LATIN SMALL LIGATURE FFI, TRADE MARK SIGN, FULLWIDTH SOLIDUS) into a series
     * of characters ('f'+'f'+'i', 'T'+'M', '/') that cannot be re-composed into the original, and therefore loses
     * information.
     */
    protected String normalize(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC);
    }

    @Override
    public Document resolvePath(String path) {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new DocumentNotFoundException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new DocumentNotFoundException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new DocumentNotFoundException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = normalize(path);

        if (len == 1) {
            return getRootDocument();
        }
        DBSDocumentState docState = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new DocumentNotFoundException("Path with empty component: " + path);
            }
            docState = transaction.getChildState(parentId, name);
            if (docState == null) {
                throw new DocumentNotFoundException(path);
            }
            parentId = docState.getId();
        }
        return getDocument(docState);
    }

    protected String getDocumentIdByPath(String path) {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new DocumentNotFoundException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new DocumentNotFoundException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new DocumentNotFoundException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = normalize(path);

        if (len == 1) {
            return getRootId();
        }
        DBSDocumentState docState = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new DocumentNotFoundException("Path with empty component: " + path);
            }
            // TODO XXX add getChildId method
            docState = transaction.getChildState(parentId, name);
            if (docState == null) {
                return null;
            }
            parentId = docState.getId();
        }
        return docState.getId(); // NOSONAR
    }

    protected Document getChild(String parentId, String name) {
        name = normalize(name);
        DBSDocumentState docState = transaction.getChildState(parentId, name);
        DBSDocument doc = getDocument(docState);
        if (doc == null) {
            throw new DocumentNotFoundException(name);
        }
        return doc;
    }

    protected List<Document> getChildren(String parentId) {
        List<DBSDocumentState> docStates = transaction.getChildrenStates(parentId);
        if (isOrderable(parentId)) {
            // sort children in order
            docStates.sort(POS_COMPARATOR);
        }
        List<Document> children = new ArrayList<>(docStates.size());
        for (DBSDocumentState docState : docStates) {
            try {
                children.add(getDocument(docState));
            } catch (DocumentNotFoundException e) {
                // ignore error retrieving one of the children
                // (Unknown document type)
                continue;
            }
        }
        return children;
    }

    protected List<String> getChildrenIds(String parentId) {
        // We want all children, the filter flags are null
        boolean excludeSpecialChildren = false;
        boolean excludeRegularChildren = false;
        return getChildrenIds(parentId, excludeSpecialChildren, excludeRegularChildren);
    }

    protected List<String> getChildrenIds(String parentId, boolean excludeSpecialChildren,
            boolean excludeRegularChildren) {
        if (isOrderable(parentId)) {
            // TODO get only id and pos, not full state
            // TODO state not for update
            List<DBSDocumentState> docStates = //
                    transaction.getChildrenStates(parentId, excludeSpecialChildren, excludeRegularChildren);
            docStates.sort(POS_COMPARATOR);
            List<String> children = new ArrayList<>(docStates.size());
            for (DBSDocumentState docState : docStates) {
                children.add(docState.getId());
            }
            return children;
        } else {
            return transaction.getChildrenIds(parentId, excludeSpecialChildren, excludeRegularChildren);
        }
    }

    protected boolean hasChildren(String parentId) {
        return transaction.hasChildren(parentId);

    }

    @Override
    public Document getDocumentByUUID(String id) {
        Document doc = getDocument(id);
        if (doc != null) {
            return doc;
        }
        // exception required by API
        throw new DocumentNotFoundException(id);
    }

    @Override
    public Document getRootDocument() {
        return getDocument(getRootId());
    }

    @Override
    public Document getNullDocument() {
        return new DBSDocument(null, null, this, true);
    }

    protected DBSDocument getDocument(String id) {
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        return getDocument(docState);
    }

    protected List<Document> getDocuments(List<String> ids) {
        List<DBSDocumentState> docStates = transaction.getStatesForUpdate(ids);
        List<Document> docs = new ArrayList<>(ids.size());
        for (DBSDocumentState docState : docStates) {
            try {
                docs.add(getDocument(docState));
            } catch (DocumentNotFoundException e) {
                // unknown type in db or null proxy target, ignore
                continue;
            }
        }
        return docs;
    }

    protected DBSDocument getDocument(DBSDocumentState docState) {
        return getDocument(docState, true);
    }

    protected DBSDocument getDocument(DBSDocumentState docState, boolean readonly) {
        if (docState == null) {
            return null;
        }
        boolean isVersion = TRUE.equals(docState.get(KEY_IS_VERSION));

        String typeName = docState.getPrimaryType();
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        DocumentType type = schemaManager.getDocumentType(typeName);
        if (type == null) {
            throw new DocumentNotFoundException("Unknown document type: " + typeName);
        }

        boolean isProxy = TRUE.equals(docState.get(KEY_IS_PROXY));
        if (isProxy) {
            String targetId = (String) docState.get(KEY_PROXY_TARGET_ID);
            DBSDocumentState targetState = transaction.getStateForUpdate(targetId);
            if (targetState == null) {
                throw new DocumentNotFoundException("Proxy has null target");
            }
        }

        if (isVersion) {
            return new DBSDocument(docState, type, this, readonly);
        } else {
            return new DBSDocument(docState, type, this, false);
        }
    }

    protected boolean hasChild(String parentId, String name) {
        name = normalize(name);
        return transaction.hasChild(parentId, name);
    }

    public Document createChild(String id, String parentId, String name, Long pos, String typeName) {
        name = normalize(name);
        DBSDocumentState docState = createChildState(id, parentId, name, pos, typeName);
        return getDocument(docState);
    }

    protected DBSDocumentState createChildState(String id, String parentId, String name, Long pos, String typeName) {
        if (pos == null && parentId != null) {
            pos = getNextPos(parentId);
        }
        return transaction.createChild(id, parentId, name, pos, typeName);
    }

    protected boolean isOrderable(String id) {
        State state = transaction.getStateForRead(id);
        String typeName = (String) state.get(KEY_PRIMARY_TYPE);
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        return schemaManager.getDocumentType(typeName).getFacets().contains(FacetNames.ORDERABLE);
    }

    protected Long getNextPos(String parentId) {
        if (!isOrderable(parentId)) {
            return null;
        }
        long max = -1;
        for (DBSDocumentState docState : transaction.getChildrenStates(parentId)) {
            Long pos = (Long) docState.get(KEY_POS);
            if (pos != null && pos > max) {
                max = pos;
            }
        }
        return max + 1;
    }

    protected void orderBefore(String parentId, String sourceId, String destId) {
        if (!isOrderable(parentId)) {
            // TODO throw exception?
            return;
        }
        if (sourceId.equals(destId)) {
            return;
        }
        // This is optimized by assuming the number of children is small enough
        // to be manageable in-memory.
        // fetch children
        List<DBSDocumentState> docStates = transaction.getChildrenStates(parentId);
        // sort children in order
        docStates.sort(POS_COMPARATOR);
        // renumber
        int i = 0;
        DBSDocumentState source = null; // source if seen
        Long destPos = null;
        for (DBSDocumentState docState : docStates) {
            Serializable id = docState.getId();
            if (id.equals(destId)) {
                destPos = Long.valueOf(i);
                i++;
                if (source != null) {
                    source.put(KEY_POS, destPos);
                }
            }
            Long setPos;
            if (id.equals(sourceId)) {
                i--;
                source = docState;
                setPos = destPos;
            } else {
                setPos = Long.valueOf(i);
            }
            if (setPos != null) {
                if (!setPos.equals(docState.get(KEY_POS))) {
                    docState.put(KEY_POS, setPos);
                }
            }
            i++;
        }
        if (destId == null) {
            Long setPos = Long.valueOf(i);
            if (!setPos.equals(source.get(KEY_POS))) { // NOSONAR
                source.put(KEY_POS, setPos);
            }
        }
    }

    protected void checkOut(String id) {
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        if (!TRUE.equals(docState.get(KEY_IS_CHECKED_IN))) {
            throw new NuxeoException("Already checked out");
        }
        docState.put(KEY_IS_CHECKED_IN, null);
    }

    protected Document checkIn(String id, String label, String checkinComment) {
        transaction.save();
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        if (TRUE.equals(docState.get(KEY_IS_CHECKED_IN))) {
            throw new NuxeoException("Already checked in");
        }
        if (label == null) {
            // use version major + minor as label
            Long major = (Long) docState.get(KEY_MAJOR_VERSION);
            Long minor = (Long) docState.get(KEY_MINOR_VERSION);
            if (major == null || minor == null) {
                label = "";
            } else {
                label = major + "." + minor;
            }
        }

        // copy into a version and create a snapshot of special children but not the regular children
        boolean excludeSpecialChildren = false;
        boolean excludeRegularChildren = true;
        String versionId = //
                copyRecurse(id, null, new LinkedList<>(), null, excludeSpecialChildren, excludeRegularChildren);
        DBSDocumentState verState = transaction.getStateForUpdate(versionId);
        String verId = verState.getId();
        verState.put(KEY_PARENT_ID, null);
        verState.put(KEY_ANCESTOR_IDS, null);
        verState.put(KEY_IS_VERSION, TRUE);
        verState.put(KEY_VERSION_SERIES_ID, id);
        verState.put(KEY_VERSION_CREATED, new GregorianCalendar()); // now
        verState.put(KEY_VERSION_LABEL, label);
        verState.put(KEY_VERSION_DESCRIPTION, checkinComment);
        verState.put(KEY_IS_LATEST_VERSION, TRUE);
        verState.put(KEY_IS_CHECKED_IN, null);
        verState.put(KEY_BASE_VERSION_ID, null);
        boolean isMajor = Long.valueOf(0).equals(verState.get(KEY_MINOR_VERSION));
        verState.put(KEY_IS_LATEST_MAJOR_VERSION, isMajor ? TRUE : null);
        // except in legacy mode, we don't copy the live ACL when creating a version
        if (versionAclMode != VersionAclMode.LEGACY) {
            verState.put(KEY_ACP, null);
        }

        // update the doc to mark it checked in
        docState.put(KEY_IS_CHECKED_IN, TRUE);
        docState.put(KEY_BASE_VERSION_ID, verId);

        if (!isLatestVersionDisabled) {
            recomputeVersionSeries(id);
        }

        // update read acls
        transaction.updateTreeReadAcls(verId);
        transaction.save();

        return getDocument(verId);
    }

    /**
     * Recomputes isLatest / isLatestMajor on all versions.
     */
    protected void recomputeVersionSeries(String versionSeriesId) {
        List<DBSDocumentState> docStates = transaction.getKeyValuedStates(KEY_VERSION_SERIES_ID, versionSeriesId,
                KEY_IS_VERSION, TRUE);
        docStates.sort(VERSION_CREATED_COMPARATOR);
        Collections.reverse(docStates);
        boolean isLatest = true;
        boolean isLatestMajor = true;
        for (DBSDocumentState docState : docStates) {
            // isLatestVersion
            docState.put(KEY_IS_LATEST_VERSION, isLatest ? TRUE : null);
            isLatest = false;
            // isLatestMajorVersion
            boolean isMajor = Long.valueOf(0).equals(docState.get(KEY_MINOR_VERSION));
            docState.put(KEY_IS_LATEST_MAJOR_VERSION, isMajor && isLatestMajor ? TRUE : null);
            if (isMajor) {
                isLatestMajor = false;
            }
        }
    }

    protected void restoreVersion(Document doc, Document version) {
        String docId = doc.getUUID();
        String versionId = version.getUUID();

        DBSDocumentState docState = transaction.getStateForUpdate(docId);

        if (TRUE.equals(docState.get(KEY_IS_RECORD))) {
            getDocumentBlobManager().notifyBeforeRemove(doc);
        }

        State versionState = transaction.getStateForRead(versionId);

        // clear all data
        for (String key : docState.state.keyArray()) {
            if (!keepWhenRestore(key)) {
                docState.put(key, null);
            }
        }
        // update from version
        for (Entry<String, Serializable> en : versionState.entrySet()) {
            String key = en.getKey();
            if (!keepWhenRestore(key)) {
                docState.put(key, StateHelper.deepCopy(en.getValue()));
            }
        }
        docState.put(KEY_IS_VERSION, null);
        docState.put(KEY_IS_CHECKED_IN, TRUE);
        docState.put(KEY_BASE_VERSION_ID, versionId);
        docState.put(KEY_IS_RECORD, null);
        docState.put(KEY_RETAIN_UNTIL, null);
        docState.put(KEY_HAS_LEGAL_HOLD, null);

        if (TRUE.equals(versionState.get(KEY_IS_RECORD))) {
            notifyAfterCopy(doc);
        }
    }

    // keys we don't copy from version when restoring
    protected boolean keepWhenRestore(String key) {
        switch (key) {
        // these are placeful stuff
        case KEY_ID:
        case KEY_PARENT_ID:
        case KEY_ANCESTOR_IDS:
        case KEY_NAME:
        case KEY_POS:
        case KEY_PRIMARY_TYPE:
        case KEY_ACP:
        case KEY_READ_ACL:
            // these are proxy-specific
        case KEY_PROXY_IDS:
            // these are version-specific
        case KEY_VERSION_CREATED:
        case KEY_VERSION_DESCRIPTION:
        case KEY_VERSION_LABEL:
        case KEY_VERSION_SERIES_ID:
        case KEY_IS_LATEST_VERSION:
        case KEY_IS_LATEST_MAJOR_VERSION:
            // these will be updated after restore
        case KEY_IS_VERSION:
        case KEY_IS_CHECKED_IN:
        case KEY_BASE_VERSION_ID:
            // record
        case KEY_IS_RECORD:
        case KEY_RETAIN_UNTIL:
        case KEY_HAS_LEGAL_HOLD:
            return true;
        }
        return false;
    }

    @Override
    public Document copy(Document source, Document parent, String name) {
        transaction.save();
        if (name == null) {
            name = source.getName();
        }
        name = findFreeName(parent, name);
        String sourceId = source.getUUID();
        String parentId = parent.getUUID();
        State sourceState = transaction.getStateForRead(sourceId);
        State parentState = transaction.getStateForRead(parentId);
        String oldParentId = (String) sourceState.get(KEY_PARENT_ID);
        Object[] parentAncestorIds = (Object[]) parentState.get(KEY_ANCESTOR_IDS);
        LinkedList<String> ancestorIds = new LinkedList<>();
        if (parentAncestorIds != null) {
            for (Object id : parentAncestorIds) {
                ancestorIds.add((String) id);
            }
        }
        ancestorIds.add(parentId);
        if (oldParentId != null && !oldParentId.equals(parentId)) {
            if (ancestorIds.contains(sourceId)) {
                throw new DocumentExistsException(
                        "Cannot copy a node under itself: " + parentId + " is under " + sourceId);

            }
            // checkNotUnder(parentId, sourceId, "copy");
        }
        // do the copy
        Long pos = getNextPos(parentId);
        boolean excludeSpecialChildren = true;
        boolean excludeRegularChildren = false;
        String copyId = //
                copyRecurse(sourceId, parentId, ancestorIds, name, excludeSpecialChildren, excludeRegularChildren);
        DBSDocumentState copyState = transaction.getStateForUpdate(copyId);
        // version copy fixup
        if (source.isVersion()) {
            copyState.put(KEY_IS_VERSION, null);
        }
        // pos fixup
        copyState.put(KEY_POS, pos);

        // update read acls
        transaction.updateTreeReadAcls(copyId);

        return getDocument(copyState);
    }

    protected String copyRecurse(String sourceId, String parentId, LinkedList<String> ancestorIds, String name,
            boolean excludeSpecialChildren, boolean excludeRegularChildren) {
        String copyId = copy(sourceId, parentId, ancestorIds, name);
        ancestorIds.addLast(copyId);
        for (String childId : getChildrenIds(sourceId, excludeSpecialChildren, excludeRegularChildren)) {
            // don't exclude regular children when recursing
            copyRecurse(childId, copyId, ancestorIds, null, excludeSpecialChildren, false);
        }
        ancestorIds.removeLast();
        return copyId;
    }

    /**
     * Copy source under parent, and set its ancestors.
     */
    protected String copy(String sourceId, String parentId, List<String> ancestorIds, String name) {
        DBSDocumentState copy = transaction.copy(sourceId);
        copy.put(KEY_PARENT_ID, parentId);
        copy.put(KEY_ANCESTOR_IDS, ancestorIds.toArray(Object[]::new));
        if (name != null) {
            copy.put(KEY_NAME, name);
        }
        copy.put(KEY_BASE_VERSION_ID, null);
        copy.put(KEY_IS_CHECKED_IN, null);
        if (parentId != null) {
            // reset version to 0
            copy.put(KEY_MAJOR_VERSION, 0L);
            copy.put(KEY_MINOR_VERSION, 0L);
        }
        if (TRUE.equals(copy.get(KEY_IS_RECORD))) {
            // unset record on the copy
            copy.put(KEY_IS_RECORD, null);
            copy.put(KEY_RETAIN_UNTIL, null);
            copy.put(KEY_HAS_LEGAL_HOLD, null);
            DBSDocument doc = getDocument(copy);
            notifyAfterCopy(doc);
        }
        return copy.getId();
    }

    protected static final Pattern dotDigitsPattern = Pattern.compile("(.*)\\.[0-9]+$");

    protected String findFreeName(Document parent, String name) {
        if (hasChild(parent.getUUID(), name)) {
            Matcher m = dotDigitsPattern.matcher(name);
            if (m.matches()) {
                // remove trailing dot and digits
                name = m.group(1);
            }
            // add dot + unique digits
            name += "." + System.currentTimeMillis();
        }
        return name;
    }

    /** Checks that we don't move/copy under ourselves. */
    protected void checkNotUnder(String parentId, String id, String op) {
        // TODO use ancestors
        String pid = parentId;
        do {
            if (pid.equals(id)) {
                throw new DocumentExistsException(
                        "Cannot " + op + " a node under itself: " + parentId + " is under " + id);
            }
            State state = transaction.getStateForRead(pid);
            if (state == null) {
                // cannot happen
                throw new NuxeoException("No parent: " + pid);
            }
            pid = (String) state.get(KEY_PARENT_ID);
        } while (pid != null);
    }

    @Override
    public Document move(Document source, Document parent, String name) {
        String oldName = source.getName();
        if (name == null) {
            name = oldName;
        }
        String sourceId = source.getUUID();
        String parentId = parent.getUUID();
        DBSDocumentState sourceState = transaction.getStateForUpdate(sourceId);
        String oldParentId = (String) sourceState.get(KEY_PARENT_ID);

        // simple case of a rename
        if (Objects.equals(oldParentId, parentId)) {
            if (!oldName.equals(name)) {
                if (hasChild(parentId, name)) {
                    throw new DocumentExistsException("Destination name already exists: " + name);
                }
                // do the move
                sourceState.put(KEY_NAME, name);
                // no ancestors to change
            }
            return source;
        } else {
            // if not just a simple rename, flush
            transaction.save();
            if (hasChild(parentId, name)) {
                throw new DocumentExistsException("Destination name already exists: " + name);
            }
        }

        // prepare new ancestor ids
        State parentState = transaction.getStateForRead(parentId);
        Object[] parentAncestorIds = (Object[]) parentState.get(KEY_ANCESTOR_IDS);
        List<String> ancestorIdsList = new ArrayList<>();
        if (parentAncestorIds != null) {
            for (Object id : parentAncestorIds) {
                ancestorIdsList.add((String) id);
            }
        }
        ancestorIdsList.add(parentId);
        Object[] ancestorIds = ancestorIdsList.toArray(Object[]::new);

        if (ancestorIdsList.contains(sourceId)) {
            throw new DocumentExistsException("Cannot move a node under itself: " + parentId + " is under " + sourceId);
        }

        // do the move
        sourceState.put(KEY_NAME, name);
        sourceState.put(KEY_PARENT_ID, parentId);

        // update ancestors on all sub-children
        Object[] oldAncestorIds = (Object[]) sourceState.get(KEY_ANCESTOR_IDS);
        int ndel = oldAncestorIds == null ? 0 : oldAncestorIds.length;
        transaction.updateAncestors(sourceId, ndel, ancestorIds);

        // update read acls
        transaction.updateTreeReadAcls(sourceId);

        return source;
    }

    /**
     * Removes a document.
     * <p>
     * We also have to update everything impacted by "relations":
     * <ul>
     * <li>parent-child relations: delete all subchildren recursively,
     * <li>proxy-target relations: if a proxy is removed, update the target's PROXY_IDS; and if a target is removed,
     * raise an error if a proxy still exists for that target.
     * </ul>
     */
    protected void remove(String rootId, NuxeoPrincipal principal) {
        transaction.save();

        State rootState = transaction.getStateForRead(rootId);
        String versionSeriesId;
        if (TRUE.equals(rootState.get(KEY_IS_VERSION))) {
            versionSeriesId = (String) rootState.get(KEY_VERSION_SERIES_ID);
        } else {
            versionSeriesId = null;
        }

        // find all sub-docs
        Set<String> removedIds = new HashSet<>();
        Set<String> undeletableIds = new HashSet<>();
        Set<String> targetIds = new HashSet<>();
        Map<String, Object[]> targetProxies = new HashMap<>();
        Calendar now = Calendar.getInstance();

        Consumer<State> collector = state -> {
            String id = (String) state.get(KEY_ID);
            removedIds.add(id);
            if (TRUE.equals(state.get(KEY_HAS_LEGAL_HOLD))) {
                undeletableIds.add(id);
            } else {
                Calendar retainUntil = (Calendar) state.get(KEY_RETAIN_UNTIL);
                if (retainUntil != null && now.before(retainUntil)) {
                    undeletableIds.add(id);
                }
            }
            if (TRUE.equals(state.get(KEY_IS_RETENTION_ACTIVE))) {
                undeletableIds.add(id);
            }
            if (TRUE.equals(state.get(KEY_IS_PROXY))) {
                String targetId = (String) state.get(KEY_PROXY_TARGET_ID);
                targetIds.add(targetId);
            }
            Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
            if (proxyIds != null) {
                targetProxies.put(id, proxyIds);
            }
        };
        collector.accept(rootState); // add the root node too
        try (Stream<State> states = transaction.getDescendants(rootId, KEYS_RETENTION_HOLD_AND_PROXIES, 0)) {
            states.forEach(collector);
        }

        // if a subdocument is under retention / hold, removal fails
        if (!undeletableIds.isEmpty()) {
            if (!BaseSession.canDeleteUndeletable(principal)) {
                if (undeletableIds.contains(rootId)) {
                    throw new DocumentExistsException("Cannot remove " + rootId + ", it is under retention / hold");
                } else {
                    throw new DocumentExistsException("Cannot remove " + rootId + ", subdocument "
                            + undeletableIds.iterator().next() + " is under retention / hold");
                }
            }
        }

        // if a proxy target is removed, check that all proxies to it are removed
        for (Entry<String, Object[]> en : targetProxies.entrySet()) {
            String targetId = en.getKey();
            for (Object proxyId : en.getValue()) {
                var pId = (String) proxyId;
                // check also existence of proxy if it has been removed but removed document wasn't updated
                if (!removedIds.contains(pId) && transaction.exists(pId)) {
                    throw new DocumentExistsException("Cannot remove " + rootId + ", subdocument " + targetId
                            + " is the target of proxy " + proxyId);
                }
            }
        }

        // remove root doc
        transaction.removeStates(Collections.singleton(rootId));
        // Check that the ids to remove is not only the root id
        if (removedIds.size() > 1) {
            String nxql = String.format("SELECT * FROM Document, Relation WHERE ecm:ancestorId = '%s'", rootId);
            BulkCommand command = new BulkCommand.Builder(ACTION_NAME, nxql, SYSTEM_USERNAME).repository(
                    getRepositoryName()).build();
            Framework.getService(BulkService.class).submitTransactional(command);
        }

        // fix proxies back-pointers on proxy targets
        for (String targetId : targetIds) {
            if (removedIds.contains(targetId)) {
                // the target was also removed, skip
                continue;
            }
            DBSDocumentState target = transaction.getStateForUpdate(targetId);
            if (target != null) {
                removeBackProxyIds(target, removedIds);
            }
        }

        // recompute version series if needed
        // only done for root of deletion as versions are not fileable
        if (versionSeriesId != null) {
            recomputeVersionSeries(versionSeriesId);
        }
    }

    @Override
    public Document createProxy(Document doc, Document folder) {
        if (doc == null) {
            throw new NullPointerException();
        }
        String id = doc.getUUID();
        String targetId;
        String versionSeriesId;
        if (doc.isVersion()) {
            targetId = id;
            versionSeriesId = doc.getVersionSeriesId();
        } else if (doc.isProxy()) {
            // copy the proxy
            State state = transaction.getStateForRead(id);
            targetId = (String) state.get(KEY_PROXY_TARGET_ID);
            versionSeriesId = (String) state.get(KEY_PROXY_VERSION_SERIES_ID);
        } else {
            // working copy (live document)
            targetId = id;
            versionSeriesId = targetId;
        }

        String parentId = folder.getUUID();
        String name = findFreeName(folder, doc.getName());
        Long pos = parentId == null ? null : getNextPos(parentId);

        DBSDocumentState docState = addProxyState(null, parentId, name, pos, targetId, versionSeriesId);
        return getDocument(docState);
    }

    protected DBSDocumentState addProxyState(String id, String parentId, String name, Long pos, String targetId,
            String versionSeriesId) {
        DBSDocumentState target = transaction.getStateForUpdate(targetId);
        String typeName = (String) target.get(KEY_PRIMARY_TYPE);

        DBSDocumentState proxy = transaction.createChild(id, parentId, name, pos, typeName);
        String proxyId = proxy.getId();
        proxy.put(KEY_IS_PROXY, TRUE);
        proxy.put(KEY_PROXY_TARGET_ID, targetId);
        proxy.put(KEY_PROXY_VERSION_SERIES_ID, versionSeriesId);
        if (changeTokenEnabled) {
            proxy.put(KEY_SYS_CHANGE_TOKEN, INITIAL_SYS_CHANGE_TOKEN);
            proxy.put(KEY_CHANGE_TOKEN, INITIAL_CHANGE_TOKEN);
        }

        // copy target state to proxy
        transaction.updateProxy(target, proxyId);

        // add back-reference to proxy on target
        addBackProxyId(target, proxyId);

        return transaction.getStateForUpdate(proxyId);
    }

    protected void addBackProxyId(DBSDocumentState docState, String id) {
        Object[] proxyIds = (Object[]) docState.get(KEY_PROXY_IDS);
        Object[] newProxyIds;
        if (proxyIds == null) {
            newProxyIds = new Object[] { id };
        } else {
            newProxyIds = new Object[proxyIds.length + 1];
            System.arraycopy(proxyIds, 0, newProxyIds, 0, proxyIds.length);
            newProxyIds[proxyIds.length] = id;
        }
        docState.put(KEY_PROXY_IDS, newProxyIds);
    }

    protected void removeBackProxyId(DBSDocumentState docState, String id) {
        removeBackProxyIds(docState, Collections.singleton(id));
    }

    protected void removeBackProxyIds(DBSDocumentState docState, Set<String> ids) {
        Object[] proxyIds = (Object[]) docState.get(KEY_PROXY_IDS);
        if (proxyIds == null) {
            return;
        }
        List<Object> keepIds = new ArrayList<>(proxyIds.length);
        for (Object pid : proxyIds) {
            if (!ids.contains(pid)) {
                keepIds.add(pid);
            }
        }
        Object[] newProxyIds = keepIds.isEmpty() ? null : keepIds.toArray(Object[]::new);
        docState.put(KEY_PROXY_IDS, newProxyIds);
    }

    @Override
    public List<Document> getProxies(Document doc, Document folder) {
        List<DBSDocumentState> docStates;
        String docId = doc.getUUID();
        if (doc.isVersion()) {
            docStates = transaction.getKeyValuedStates(KEY_PROXY_TARGET_ID, docId);
        } else {
            String versionSeriesId;
            if (doc.isProxy()) {
                State state = transaction.getStateForRead(docId);
                versionSeriesId = (String) state.get(KEY_PROXY_VERSION_SERIES_ID);
            } else {
                versionSeriesId = docId;
            }
            docStates = transaction.getKeyValuedStates(KEY_PROXY_VERSION_SERIES_ID, versionSeriesId);
        }

        String parentId = folder == null ? null : folder.getUUID();
        List<Document> documents = new ArrayList<>(docStates.size());
        for (DBSDocumentState docState : docStates) {
            // filter by parent
            if (parentId != null && !parentId.equals(docState.getParentId())) {
                continue;
            }
            documents.add(getDocument(docState));
        }
        return documents;
    }

    @Override
    public List<Document> getProxies(Document doc) {
        State state = transaction.getStateForRead(doc.getUUID());
        Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
        if (proxyIds != null) {
            List<String> ids = Arrays.stream(proxyIds).map(String::valueOf).collect(Collectors.toList());
            return getDocuments(ids);
        }
        return Collections.emptyList();
    }

    @Override
    public void setProxyTarget(Document proxy, Document target) {
        String proxyId = proxy.getUUID();
        String targetId = target.getUUID();
        DBSDocumentState proxyState = transaction.getStateForUpdate(proxyId);
        String oldTargetId = (String) proxyState.get(KEY_PROXY_TARGET_ID);

        // update old target's back-pointers: remove proxy id
        DBSDocumentState oldTargetState = transaction.getStateForUpdate(oldTargetId);
        removeBackProxyId(oldTargetState, proxyId);
        // update new target's back-pointers: add proxy id
        DBSDocumentState targetState = transaction.getStateForUpdate(targetId);
        addBackProxyId(targetState, proxyId);
        // set new target
        proxyState.put(KEY_PROXY_TARGET_ID, targetId);
    }

    @Override
    public Document importDocument(String id, Document parent, String name, String typeName,
            Map<String, Serializable> properties) {
        String parentId = parent == null ? null : parent.getUUID();
        boolean isProxy = typeName.equals(CoreSession.IMPORT_PROXY_TYPE);
        Map<String, Serializable> props = new HashMap<>();
        Long pos = null; // TODO pos
        DBSDocumentState docState;
        if (isProxy) {
            // check that target exists and find its typeName
            String targetId = (String) properties.get(CoreSession.IMPORT_PROXY_TARGET_ID);
            if (targetId == null) {
                throw new NuxeoException("Cannot import proxy " + id + " with null target");
            }
            State targetState = transaction.getStateForRead(targetId);
            if (targetState == null) {
                throw new DocumentNotFoundException("Cannot import proxy " + id + " with missing target " + targetId);
            }
            String versionSeriesId = (String) properties.get(CoreSession.IMPORT_PROXY_VERSIONABLE_ID);
            docState = addProxyState(id, parentId, name, pos, targetId, versionSeriesId);
        } else {
            // version & live document
            props.put(KEY_LIFECYCLE_POLICY, properties.get(CoreSession.IMPORT_LIFECYCLE_POLICY));
            props.put(KEY_LIFECYCLE_STATE, properties.get(CoreSession.IMPORT_LIFECYCLE_STATE));

            Serializable importLockOwnerProp = properties.get(CoreSession.IMPORT_LOCK_OWNER);
            if (importLockOwnerProp != null) {
                props.put(KEY_LOCK_OWNER, importLockOwnerProp);
            }
            Serializable importLockCreatedProp = properties.get(CoreSession.IMPORT_LOCK_CREATED);
            if (importLockCreatedProp != null) {
                props.put(KEY_LOCK_CREATED, importLockCreatedProp);
            }

            Boolean isRecord = trueOrNull(properties.get(CoreSession.IMPORT_IS_RECORD));
            props.put(KEY_IS_RECORD, isRecord);
            if (TRUE.equals(isRecord)) {
                Calendar retainUntil = (Calendar) properties.get(CoreSession.IMPORT_RETAIN_UNTIL);
                if (retainUntil != null) {
                    props.put(KEY_RETAIN_UNTIL, retainUntil);
                }
                Boolean hasLegalHold = trueOrNull(properties.get(CoreSession.IMPORT_HAS_LEGAL_HOLD));
                props.put(KEY_HAS_LEGAL_HOLD, hasLegalHold);
            }

            Serializable isRetentionActiveProp = properties.get(CoreSession.IMPORT_IS_RETENTION_ACTIVE);
            if (TRUE.equals(isRetentionActiveProp)) {
                props.put(KEY_IS_RETENTION_ACTIVE, TRUE);
            }

            props.put(KEY_MAJOR_VERSION, properties.get(CoreSession.IMPORT_VERSION_MAJOR));
            props.put(KEY_MINOR_VERSION, properties.get(CoreSession.IMPORT_VERSION_MINOR));
            Boolean isVersion = trueOrNull(properties.get(CoreSession.IMPORT_IS_VERSION));
            props.put(KEY_IS_VERSION, isVersion);
            if (TRUE.equals(isVersion)) {
                // version
                props.put(KEY_VERSION_SERIES_ID, properties.get(CoreSession.IMPORT_VERSION_VERSIONABLE_ID));
                props.put(KEY_VERSION_CREATED, properties.get(CoreSession.IMPORT_VERSION_CREATED));
                props.put(KEY_VERSION_LABEL, properties.get(CoreSession.IMPORT_VERSION_LABEL));
                props.put(KEY_VERSION_DESCRIPTION, properties.get(CoreSession.IMPORT_VERSION_DESCRIPTION));
                // TODO maybe these should be recomputed at end of import:
                props.put(KEY_IS_LATEST_VERSION, trueOrNull(properties.get(CoreSession.IMPORT_VERSION_IS_LATEST)));
                props.put(KEY_IS_LATEST_MAJOR_VERSION,
                        trueOrNull(properties.get(CoreSession.IMPORT_VERSION_IS_LATEST_MAJOR)));
            } else {
                // live document
                props.put(KEY_BASE_VERSION_ID, properties.get(CoreSession.IMPORT_BASE_VERSION_ID));
                props.put(KEY_IS_CHECKED_IN, trueOrNull(properties.get(CoreSession.IMPORT_CHECKED_IN)));
            }
            docState = createChildState(id, parentId, name, pos, typeName);
        }
        for (Entry<String, Serializable> entry : props.entrySet()) {
            docState.put(entry.getKey(), entry.getValue());
        }
        return getDocument(docState, false); // not readonly
    }

    protected static Boolean trueOrNull(Object value) {
        return TRUE.equals(value) ? TRUE : null;
    }

    @Override
    public Document getVersion(String versionSeriesId, VersionModel versionModel) {
        DBSDocumentState docState = getVersionByLabel(versionSeriesId, versionModel.getLabel());
        if (docState == null) {
            return null;
        }
        versionModel.setDescription((String) docState.get(KEY_VERSION_DESCRIPTION));
        versionModel.setCreated((Calendar) docState.get(KEY_VERSION_CREATED));
        return getDocument(docState);
    }

    protected DBSDocumentState getVersionByLabel(String versionSeriesId, String label) {
        List<DBSDocumentState> docStates = transaction.getKeyValuedStates(KEY_VERSION_SERIES_ID, versionSeriesId,
                KEY_IS_VERSION, TRUE);
        for (DBSDocumentState docState : docStates) {
            if (label.equals(docState.get(KEY_VERSION_LABEL))) {
                return docState;
            }
        }
        return null;
    }

    protected List<String> getVersionsIds(String versionSeriesId) {
        // order by creation date
        List<DBSDocumentState> docStates = transaction.getKeyValuedStates(KEY_VERSION_SERIES_ID, versionSeriesId,
                KEY_IS_VERSION, TRUE);
        docStates.sort(VERSION_CREATED_COMPARATOR);
        List<String> ids = new ArrayList<>(docStates.size());
        for (DBSDocumentState docState : docStates) {
            ids.add(docState.getId());
        }
        return ids;
    }

    protected Document getLastVersion(String versionSeriesId) {
        List<DBSDocumentState> docStates = transaction.getKeyValuedStates(KEY_VERSION_SERIES_ID, versionSeriesId,
                KEY_IS_VERSION, TRUE);
        // find latest one
        Calendar latest = null;
        DBSDocumentState latestState = null;
        for (DBSDocumentState docState : docStates) {
            Calendar created = (Calendar) docState.get(KEY_VERSION_CREATED);
            if (latest == null || created.compareTo(latest) > 0) {
                latest = created;
                latestState = docState;
            }
        }
        return latestState == null ? null : getDocument(latestState);
    }

    private static final Comparator<DBSDocumentState> VERSION_CREATED_COMPARATOR = (s1, s2) -> {
        Calendar c1 = (Calendar) s1.get(KEY_VERSION_CREATED);
        Calendar c2 = (Calendar) s2.get(KEY_VERSION_CREATED);
        if (c1 == null && c2 == null) {
            // coherent sort
            return s1.hashCode() - s2.hashCode();
        }
        if (c1 == null) {
            return 1;
        }
        if (c2 == null) {
            return -1;
        }
        return c1.compareTo(c2);
    };

    private static final Comparator<DBSDocumentState> POS_COMPARATOR = (s1, s2) -> {
        Long p1 = (Long) s1.get(KEY_POS);
        Long p2 = (Long) s2.get(KEY_POS);
        if (p1 == null && p2 == null) {
            // coherent sort
            return s1.hashCode() - s2.hashCode();
        }
        if (p1 == null) {
            return 1;
        }
        if (p2 == null) {
            return -1;
        }
        return p1.compareTo(p2);
    };

    @Override
    public void updateReadACLs(Collection<String> docIds) {
        transaction.updateReadACLs(docIds);
    }

    @Override
    public boolean isNegativeAclAllowed() {
        return false;
    }

    @Override
    public ACP getACP(Document doc) {
        State state = transaction.getStateForRead(doc.getUUID());
        return memToAcp(doc.getUUID(), state.get(KEY_ACP));
    }

    @Override
    public void setACP(Document doc, ACP acp, boolean overwrite) {
        if (!overwrite && acp == null) {
            return;
        }
        checkNegativeAcl(acp);
        if (!overwrite) {
            acp = updateACP(getACP(doc), acp);
        }
        String id = doc.getUUID();
        DBSDocumentState docState = transaction.getStateForUpdate(id);
        docState.put(KEY_ACP, acpToMem(acp));

        // update read acls
        transaction.updateTreeReadAcls(id);
    }

    public static Serializable acpToMem(ACP acp) {
        if (acp == null) {
            return null;
        }
        ACL[] acls = acp.getACLs();
        if (acls.length == 0) {
            return null;
        }
        List<Serializable> aclList = new ArrayList<>(acls.length);
        for (ACL acl : acls) {
            String name = acl.getName();
            if (name.equals(ACL.INHERITED_ACL)) {
                continue;
            }
            ACE[] aces = acl.getACEs();
            List<Serializable> aceList = new ArrayList<>(aces.length);
            for (ACE ace : aces) {
                State aceMap = new State(6);
                aceMap.put(KEY_ACE_USER, ace.getUsername());
                aceMap.put(KEY_ACE_PERMISSION, ace.getPermission());
                aceMap.put(KEY_ACE_GRANT, ace.isGranted());
                String creator = ace.getCreator();
                if (creator != null) {
                    aceMap.put(KEY_ACE_CREATOR, creator);
                }
                Calendar begin = ace.getBegin();
                if (begin != null) {
                    aceMap.put(KEY_ACE_BEGIN, begin);
                }
                Calendar end = ace.getEnd();
                if (end != null) {
                    aceMap.put(KEY_ACE_END, end);
                }
                Long status = ace.getLongStatus();
                if (status != null) {
                    aceMap.put(KEY_ACE_STATUS, status);
                }
                aceList.add(aceMap);
            }
            if (aceList.isEmpty()) {
                continue;
            }
            State aclMap = new State(2);
            aclMap.put(KEY_ACL_NAME, name);
            aclMap.put(KEY_ACL, (Serializable) aceList);
            aclList.add(aclMap);
        }
        return (Serializable) aclList;
    }

    protected static ACP memToAcp(String docId, Serializable acpSer) {
        if (acpSer == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Serializable> aclList = (List<Serializable>) acpSer;
        ACP acp = new ACPImpl();
        for (Serializable aclSer : aclList) {
            State aclMap = (State) aclSer;
            String name = (String) aclMap.get(KEY_ACL_NAME);
            @SuppressWarnings("unchecked")
            List<Serializable> aceList = (List<Serializable>) aclMap.get(KEY_ACL);
            if (aceList == null) {
                continue;
            }
            ACL acl = new ACLImpl(name);
            for (Serializable aceSer : aceList) {
                State aceMap = (State) aceSer;
                var granted = (Boolean) aceMap.get(KEY_ACE_GRANT);
                if (granted == null) {
                    log.warn(
                            "An ACE with grant=null has been detected on document: {}, ace: {}, defaulting to grant=false",
                            docId, aceMap);
                    granted = Boolean.FALSE;
                }
                String username = (String) aceMap.get(KEY_ACE_USER);
                String permission = (String) aceMap.get(KEY_ACE_PERMISSION);
                String creator = (String) aceMap.get(KEY_ACE_CREATOR);
                Calendar begin = (Calendar) aceMap.get(KEY_ACE_BEGIN);
                Calendar end = (Calendar) aceMap.get(KEY_ACE_END);
                // status not read, ACE always computes it on read
                ACE ace = ACE.builder(username, permission)
                             .isGranted(granted)
                             .creator(creator)
                             .begin(begin)
                             .end(end)
                             .build();
                acl.add(ace);
            }
            acp.addACL(acl);
        }
        return acp;
    }

    @Override
    public boolean isFulltextStoredInBlob() {
        return fulltextStoredInBlob;
    }

    @Override
    public Map<String, String> getBinaryFulltext(String id) {
        State state = transaction.getStateForRead(id);
        String fulltext = (String) state.get(KEY_FULLTEXT_BINARY);
        if (fulltextStoredInBlob && fulltext != null) {
            DBSDocument doc = getDocument(id);
            if (doc == null) {
                // could not find doc (shouldn't happen)
                fulltext = null;
            } else {
                // fulltext is actually the blob key
                // now retrieve the actual fulltext from the blob content
                try {
                    BlobInfo blobInfo = new BlobInfo();
                    blobInfo.key = fulltext;
                    String xpath = BaseDocument.FULLTEXT_BINARYTEXT_PROP;
                    Blob blob = getDocumentBlobManager().readBlob(blobInfo, doc, xpath);
                    fulltext = blob.getString();
                } catch (IOException e) {
                    throw new PropertyException("Cannot read fulltext blob for doc: " + id, e);
                }
            }
        }
        return Collections.singletonMap(BINARY_FULLTEXT_MAIN_KEY, fulltext);
    }

    @Override
    public void removeDocument(String id) {
        transaction.save();

        DBSDocumentState docState = transaction.getStateForUpdate(id);
        if (!BaseSession.canDeleteUndeletable(NuxeoPrincipal.getCurrent())) {
            Calendar retainUntil = (Calendar) docState.get(KEY_RETAIN_UNTIL);
            if (retainUntil != null && Calendar.getInstance().before(retainUntil)) {
                throw new DocumentExistsException("Cannot remove " + id + ", it is under retention / hold");
            }
            if (TRUE.equals(docState.get(KEY_HAS_LEGAL_HOLD))) {
                throw new DocumentExistsException("Cannot remove " + id + ", it is under retention / hold");
            }
            if (TRUE.equals(docState.get(KEY_IS_RETENTION_ACTIVE))) {
                throw new DocumentExistsException("Cannot remove " + id + ", it is under active retention");
            }
        }

        // notify blob manager before removal
        try {
            DBSDocument doc = getDocument(docState);
            getDocumentBlobManager().notifyBeforeRemove(doc);
        } catch (DocumentNotFoundException e) {
            // unknown type in db or null proxy target
            // ignore blob manager notification
        }

        // remove doc
        transaction.removeStates(Collections.singleton(id));

    }

    @Override
    public PartialList<Document> query(String query, String queryType, QueryFilter queryFilter, long countUpTo) {
        // query
        PartialList<String> pl = doQuery(query, queryType, queryFilter, (int) countUpTo);

        // get Documents in bulk
        List<Document> docs = getDocuments(pl);

        return new PartialList<>(docs, pl.totalSize());
    }

    @SuppressWarnings("resource") // Time.Context closed by stop()
    protected PartialList<String> doQuery(String query, String queryType, QueryFilter queryFilter, int countUpTo) {
        final Timer.Context timerContext = queryTimer.time();
        try {
            Mutable<String> idKeyHolder = new MutableObject<>();
            PartialList<Map<String, Serializable>> pl = doQueryAndFetch(query, queryType, queryFilter, false, countUpTo,
                    idKeyHolder);
            String idKey = idKeyHolder.getValue();
            List<String> ids = new ArrayList<>(pl.size());
            for (Map<String, Serializable> map : pl) {
                String id = (String) map.get(idKey);
                ids.add(id);
            }
            return new PartialList<>(ids, pl.totalSize());
        } finally {
            long duration = timerContext.stop();
            if (LOG_MIN_DURATION_NS >= 0 && duration > LOG_MIN_DURATION_NS) {
                String msg = String.format("duration_ms:\t%.2f\t%s %s\tquery\t%s", duration / 1000000.0, queryFilter,
                        countUpToAsString(countUpTo), query);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }
        }
    }

    protected PartialList<Map<String, Serializable>> doQueryAndFetch(String query, String queryType,
            QueryFilter queryFilter, boolean distinctDocuments, int countUpTo, Mutable<String> idKeyHolder) {
        if ("NXTAG".equals(queryType)) {
            // for now don't try to implement tags
            // and return an empty list
            return new PartialList<>(Collections.emptyList(), 0);
        }
        if (!NXQL.NXQL.equals(queryType)) {
            throw new NuxeoException("No QueryMaker accepts query type: " + queryType);
        }

        // transform the query according to the transformers defined by the
        // security policies
        SQLQuery sqlQuery = SQLQueryParser.parse(query);
        for (SQLQuery.Transformer transformer : queryFilter.getQueryTransformers()) {
            sqlQuery = transformer.transform(queryFilter.getPrincipal(), sqlQuery);
        }

        SelectClause selectClause = sqlQuery.select;
        if (selectClause.isEmpty()) {
            // turned into SELECT ecm:uuid
            selectClause.add(new Reference(NXQL.ECM_UUID));
        }
        boolean selectStar = selectClause.count() == 1 && (selectClause.containsOperand(new Reference(NXQL.ECM_UUID)));
        if (selectStar) {
            distinctDocuments = true;
        } else if (selectClause.isDistinct()) {
            throw new QueryParseException("SELECT DISTINCT not supported on DBS");
        }
        if (idKeyHolder != null) {
            Operand operand = selectClause.operands().iterator().next();
            String idKey = operand instanceof Reference ? ((Reference) operand).name : NXQL.ECM_UUID;
            idKeyHolder.setValue(idKey);
        }

        // Replace select clause for tags
        String ecmTag = selectClause.elements.keySet()
                                             .stream()
                                             .filter(k -> k.startsWith(NXQL.ECM_TAG))
                                             .findFirst()
                                             .orElse(null);
        String keyTag = null;
        if (ecmTag != null) {
            keyTag = FACETED_TAG + "/*1/" + FACETED_TAG_LABEL;
            selectClause.elements.replace(ecmTag, new Reference(keyTag));
        }

        // Add useful select clauses, used for order by path
        selectClause.elements.putIfAbsent(NXQL.ECM_UUID, new Reference(NXQL.ECM_UUID));
        selectClause.elements.putIfAbsent(NXQL.ECM_PARENTID, new Reference(NXQL.ECM_PARENTID));
        selectClause.elements.putIfAbsent(NXQL.ECM_NAME, new Reference(NXQL.ECM_NAME));

        QueryOptimizer optimizer = new DBSQueryOptimizer().withFacetFilter(queryFilter.getFacetFilter());
        sqlQuery = optimizer.optimize(sqlQuery);
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this, sqlQuery, queryFilter.getPrincipals(),
                fulltextSearchDisabled);

        int limit = (int) queryFilter.getLimit();
        int offset = (int) queryFilter.getOffset();
        if (offset < 0) {
            offset = 0;
        }
        if (limit < 0) {
            limit = 0;
        }

        int repoLimit;
        int repoOffset;
        OrderByClause repoOrderByClause;
        OrderByClause orderByClause = sqlQuery.orderBy;
        boolean postFilter = isOrderByPath(orderByClause);
        if (postFilter) {
            // we have to merge ordering and batching between memory and
            // repository
            repoLimit = 0;
            repoOffset = 0;
            repoOrderByClause = null;
        } else {
            // fast case, we can use the repository query directly
            repoLimit = limit;
            repoOffset = offset;
            repoOrderByClause = orderByClause;
        }

        // query the repository
        PartialList<Map<String, Serializable>> projections = transaction.queryAndFetch(evaluator, repoOrderByClause,
                distinctDocuments, repoLimit, repoOffset, countUpTo);

        for (Map<String, Serializable> proj : projections) {
            if (proj.containsKey(keyTag)) {
                proj.put(ecmTag, proj.remove(keyTag));
            }
        }

        if (postFilter) {
            // ORDER BY
            if (orderByClause != null) {
                doOrderBy(projections, orderByClause);
            }
            // LIMIT / OFFSET
            if (limit != 0) {
                int size = projections.size();
                int fromIndex = offset > size ? size : offset;
                int toIndex = fromIndex + limit > size ? size : fromIndex + limit;
                projections = projections.subList(fromIndex, toIndex);
            }
        }

        return projections;
    }

    /** Does an ORDER BY clause include ecm:path */
    protected boolean isOrderByPath(OrderByClause orderByClause) {
        if (orderByClause == null) {
            return false;
        }
        for (OrderByExpr ob : orderByClause.elements) {
            if (ob.reference.name.equals(NXQL.ECM_PATH)) {
                return true;
            }
        }
        return false;
    }

    protected String getPath(Map<String, Serializable> projection) {
        String name = (String) projection.get(NXQL.ECM_NAME);
        String parentId = (String) projection.get(NXQL.ECM_PARENTID);
        State state;
        if (parentId == null || (state = transaction.getStateForRead(parentId)) == null) {
            if ("".equals(name)) {
                return "/"; // root
            } else {
                return name; // placeless, no slash
            }
        }
        LinkedList<String> list = new LinkedList<>();
        list.addFirst(name);
        for (;;) {
            name = (String) state.get(KEY_NAME);
            parentId = (String) state.get(KEY_PARENT_ID);
            list.addFirst(name);
            if (parentId == null || (state = transaction.getStateForRead(parentId)) == null) {
                return StringUtils.join(list, '/');
            }
        }
    }

    protected void doOrderBy(List<Map<String, Serializable>> projections, OrderByClause orderByClause) {
        if (isOrderByPath(orderByClause)) {
            // add path info to do the sort
            for (Map<String, Serializable> projection : projections) {
                projection.put(ExpressionEvaluator.NXQL_ECM_PATH, getPath(projection));
            }
        }
        projections.sort(new OrderByComparator(orderByClause));
    }

    public static class OrderByComparator implements Comparator<Map<String, Serializable>> {

        protected final OrderByClause orderByClause;

        public OrderByComparator(OrderByClause orderByClause) {
            // replace ecm:path with ecm:__path for evaluation
            // (we don't want to allow ecm:path to be usable anywhere else
            // and resolve to a null value)
            OrderByList obl = new OrderByList();
            for (OrderByExpr ob : orderByClause.elements) {
                if (ob.reference.name.equals(NXQL.ECM_PATH)) {
                    ob = new OrderByExpr(new Reference(ExpressionEvaluator.NXQL_ECM_PATH), ob.isDescending);
                }
                obl.add(ob);
            }
            this.orderByClause = new OrderByClause(obl);
        }

        @Override
        @SuppressWarnings("unchecked")
        public int compare(Map<String, Serializable> map1, Map<String, Serializable> map2) {
            for (OrderByExpr ob : orderByClause.elements) {
                Reference ref = ob.reference;
                boolean desc = ob.isDescending;
                Object v1 = map1.get(ref.name);
                Object v2 = map2.get(ref.name);
                int cmp;
                if (v1 == null) {
                    cmp = v2 == null ? 0 : -1;
                } else if (v2 == null) {
                    cmp = 1;
                } else {
                    if (!(v1 instanceof Comparable)) {
                        throw new QueryParseException("Not a comparable: " + v1);
                    }
                    cmp = ((Comparable<Object>) v1).compareTo(v2);
                }
                if (desc) {
                    cmp = -cmp;
                }
                if (cmp != 0) {
                    return cmp;
                }
                // loop for lexicographical comparison
            }
            return 0;
        }
    }

    @Override
    public IterableQueryResult queryAndFetch(String query, String queryType, QueryFilter queryFilter,
            boolean distinctDocuments, Object[] params) {
        final Timer.Context timerContext = queryTimer.time();
        try {
            PartialList<Map<String, Serializable>> pl = doQueryAndFetch(query, queryType, queryFilter,
                    distinctDocuments, -1, null);
            return new DBSQueryResult(pl);
        } finally {
            long duration = timerContext.stop();
            if (LOG_MIN_DURATION_NS >= 0 && duration > LOG_MIN_DURATION_NS) {
                String msg = String.format("duration_ms:\t%.2f\t%s\tqueryAndFetch\t%s", duration / 1000000.0,
                        queryFilter, query);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }

        }
    }

    @SuppressWarnings("resource") // Time.Context closed by stop()
    @Override
    public PartialList<Map<String, Serializable>> queryProjection(String query, String queryType,
            QueryFilter queryFilter, boolean distinctDocuments, long countUpTo, Object[] params) {
        final Timer.Context timerContext = queryTimer.time();
        try {
            return doQueryAndFetch(query, queryType, queryFilter, distinctDocuments, (int) countUpTo, null);
        } finally {
            long duration = timerContext.stop();
            if (LOG_MIN_DURATION_NS >= 0 && duration > LOG_MIN_DURATION_NS) {
                String msg = String.format("duration_ms:\t%.2f\t%s\tqueryProjection\t%s", duration / 1000000.0,
                        queryFilter, query);
                if (log.isTraceEnabled()) {
                    log.info(msg, new Throwable("Slow query stack trace"));
                } else {
                    log.info(msg);
                }
            }

        }
    }

    @Override
    public ScrollResult<String> scroll(String query, int batchSize, int keepAliveSeconds) {
        SQLQuery sqlQuery = SQLQueryParser.parse(query);
        SelectClause selectClause = sqlQuery.select;
        selectClause.add(new Reference(NXQL.ECM_UUID));
        sqlQuery = new DBSQueryOptimizer().optimize(sqlQuery);
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this, sqlQuery, null, fulltextSearchDisabled);
        return transaction.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String query, QueryFilter queryFilter, int batchSize, int keepAliveSeconds) {
        SQLQuery sqlQuery = SQLQueryParser.parse(query);
        SelectClause selectClause = sqlQuery.select;
        selectClause.add(new Reference(NXQL.ECM_UUID));
        sqlQuery = new DBSQueryOptimizer().optimize(sqlQuery);
        for (SQLQuery.Transformer transformer : queryFilter.getQueryTransformers()) {
            sqlQuery = transformer.transform(queryFilter.getPrincipal(), sqlQuery);
        }
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this, sqlQuery, queryFilter.getPrincipals(),
                fulltextSearchDisabled);
        return transaction.scroll(evaluator, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        return transaction.scroll(scrollId);
    }

    private String countUpToAsString(long countUpTo) {
        if (countUpTo > 0) {
            return String.format("count total results up to %d", countUpTo);
        }
        return countUpTo == -1 ? "count total results UNLIMITED" : "";
    }

    protected static class DBSQueryResult implements IterableQueryResult, Iterator<Map<String, Serializable>> {

        boolean closed;

        protected List<Map<String, Serializable>> maps;

        protected long totalSize;

        protected long pos;

        protected DBSQueryResult(PartialList<Map<String, Serializable>> pl) {
            this.maps = pl;
            this.totalSize = pl.totalSize();
        }

        @Override
        public Iterator<Map<String, Serializable>> iterator() {
            return this;
        }

        @Override
        public void close() {
            closed = true;
            pos = -1;
        }

        @Override
        public boolean isLife() {
            return !closed;
        }

        @Override
        public boolean mustBeClosed() {
            return false; // holds no resources
        }

        @Override
        public long size() {
            return totalSize;
        }

        @Override
        public long pos() {
            return pos;
        }

        @Override
        public void skipTo(long pos) {
            if (pos < 0) {
                pos = 0;
            } else if (pos > totalSize) {
                pos = totalSize;
            }
            this.pos = pos;
        }

        @Override
        public boolean hasNext() {
            return pos < totalSize;
        }

        @Override
        public Map<String, Serializable> next() {
            if (closed || pos == totalSize) {
                throw new NoSuchElementException();
            }
            Map<String, Serializable> map = maps.get((int) pos);
            pos++;
            return map;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static String convToInternal(String name) {
        switch (name) {
        case NXQL.ECM_UUID:
            return KEY_ID;
        case NXQL.ECM_NAME:
            return KEY_NAME;
        case NXQL.ECM_POS:
            return KEY_POS;
        case NXQL.ECM_PARENTID:
            return KEY_PARENT_ID;
        case NXQL.ECM_MIXINTYPE:
            return KEY_MIXIN_TYPES;
        case NXQL.ECM_PRIMARYTYPE:
            return KEY_PRIMARY_TYPE;
        case NXQL.ECM_ISPROXY:
            return KEY_IS_PROXY;
        case NXQL.ECM_ISVERSION:
        case NXQL.ECM_ISVERSION_OLD:
            return KEY_IS_VERSION;
        case NXQL.ECM_LIFECYCLESTATE:
            return KEY_LIFECYCLE_STATE;
        case NXQL.ECM_LOCK_OWNER:
            return KEY_LOCK_OWNER;
        case NXQL.ECM_LOCK_CREATED:
            return KEY_LOCK_CREATED;
        case NXQL.ECM_PROXY_TARGETID:
            return KEY_PROXY_TARGET_ID;
        case NXQL.ECM_PROXY_VERSIONABLEID:
            return KEY_PROXY_VERSION_SERIES_ID;
        case NXQL.ECM_ISCHECKEDIN:
            return KEY_IS_CHECKED_IN;
        case NXQL.ECM_ISLATESTVERSION:
            return KEY_IS_LATEST_VERSION;
        case NXQL.ECM_ISLATESTMAJORVERSION:
            return KEY_IS_LATEST_MAJOR_VERSION;
        case NXQL.ECM_VERSIONLABEL:
            return KEY_VERSION_LABEL;
        case NXQL.ECM_VERSIONCREATED:
            return KEY_VERSION_CREATED;
        case NXQL.ECM_VERSIONDESCRIPTION:
            return KEY_VERSION_DESCRIPTION;
        case NXQL.ECM_VERSION_VERSIONABLEID:
            return KEY_VERSION_SERIES_ID;
        case NXQL.ECM_ISRECORD:
            return KEY_IS_RECORD;
        case NXQL.ECM_RETAINUNTIL:
            return KEY_RETAIN_UNTIL;
        case NXQL.ECM_HASLEGALHOLD:
            return KEY_HAS_LEGAL_HOLD;
        case NXQL.ECM_ANCESTORID:
        case ExpressionEvaluator.NXQL_ECM_ANCESTOR_IDS:
            return KEY_ANCESTOR_IDS;
        case ExpressionEvaluator.NXQL_ECM_PATH:
            return KEY_PATH_INTERNAL;
        case ExpressionEvaluator.NXQL_ECM_READ_ACL:
            return KEY_READ_ACL;
        case NXQL.ECM_FULLTEXT_JOBID:
            return KEY_FULLTEXT_JOBID;
        case NXQL.ECM_FULLTEXT_SCORE:
            return KEY_FULLTEXT_SCORE;
        case ExpressionEvaluator.NXQL_ECM_FULLTEXT_SIMPLE:
            return KEY_FULLTEXT_SIMPLE;
        case ExpressionEvaluator.NXQL_ECM_FULLTEXT_BINARY:
            return KEY_FULLTEXT_BINARY;
        case NXQL.ECM_ACL:
            return KEY_ACP;
        case DBSDocument.PROP_UID_MAJOR_VERSION:
        case DBSDocument.PROP_MAJOR_VERSION:
            return DBSDocument.KEY_MAJOR_VERSION;
        case DBSDocument.PROP_UID_MINOR_VERSION:
        case DBSDocument.PROP_MINOR_VERSION:
            return DBSDocument.KEY_MINOR_VERSION;
        case NXQL.ECM_ISTRASHED:
            return KEY_IS_TRASHED;
        case NXQL.ECM_BLOBKEYS:
            return KEY_BLOB_KEYS;
        case NXQL.ECM_FULLTEXT:
            throw new UnsupportedOperationException(name);
        }
        throw new QueryParseException("No such property: " + name);
    }

    public static String convToInternalAce(String name) {
        switch (name) {
        case NXQL.ECM_ACL_NAME:
            return KEY_ACL_NAME;
        case NXQL.ECM_ACL_PRINCIPAL:
            return KEY_ACE_USER;
        case NXQL.ECM_ACL_PERMISSION:
            return KEY_ACE_PERMISSION;
        case NXQL.ECM_ACL_GRANT:
            return KEY_ACE_GRANT;
        case NXQL.ECM_ACL_CREATOR:
            return KEY_ACE_CREATOR;
        case NXQL.ECM_ACL_BEGIN:
            return KEY_ACE_BEGIN;
        case NXQL.ECM_ACL_END:
            return KEY_ACE_END;
        case NXQL.ECM_ACL_STATUS:
            return KEY_ACE_STATUS;
        }
        return null;
    }

    public static String convToNXQL(String name) {
        switch (name) {
        case KEY_ID:
            return NXQL.ECM_UUID;
        case KEY_NAME:
            return NXQL.ECM_NAME;
        case KEY_POS:
            return NXQL.ECM_POS;
        case KEY_PARENT_ID:
            return NXQL.ECM_PARENTID;
        case KEY_MIXIN_TYPES:
            return NXQL.ECM_MIXINTYPE;
        case KEY_PRIMARY_TYPE:
            return NXQL.ECM_PRIMARYTYPE;
        case KEY_IS_PROXY:
            return NXQL.ECM_ISPROXY;
        case KEY_IS_VERSION:
            return NXQL.ECM_ISVERSION;
        case KEY_LIFECYCLE_STATE:
            return NXQL.ECM_LIFECYCLESTATE;
        case KEY_LOCK_OWNER:
            return NXQL.ECM_LOCK_OWNER;
        case KEY_LOCK_CREATED:
            return NXQL.ECM_LOCK_CREATED;
        case KEY_PROXY_TARGET_ID:
            return NXQL.ECM_PROXY_TARGETID;
        case KEY_PROXY_VERSION_SERIES_ID:
            return NXQL.ECM_PROXY_VERSIONABLEID;
        case KEY_IS_CHECKED_IN:
            return NXQL.ECM_ISCHECKEDIN;
        case KEY_IS_LATEST_VERSION:
            return NXQL.ECM_ISLATESTVERSION;
        case KEY_IS_LATEST_MAJOR_VERSION:
            return NXQL.ECM_ISLATESTMAJORVERSION;
        case KEY_VERSION_LABEL:
            return NXQL.ECM_VERSIONLABEL;
        case KEY_VERSION_CREATED:
            return NXQL.ECM_VERSIONCREATED;
        case KEY_VERSION_DESCRIPTION:
            return NXQL.ECM_VERSIONDESCRIPTION;
        case KEY_VERSION_SERIES_ID:
            return NXQL.ECM_VERSION_VERSIONABLEID;
        case KEY_IS_RECORD:
            return NXQL.ECM_ISRECORD;
        case KEY_RETAIN_UNTIL:
            return NXQL.ECM_RETAINUNTIL;
        case KEY_HAS_LEGAL_HOLD:
            return NXQL.ECM_HASLEGALHOLD;
        case KEY_MAJOR_VERSION:
            return "major_version"; // TODO XXX constant
        case KEY_MINOR_VERSION:
            return "minor_version";
        case KEY_FULLTEXT_SCORE:
            return NXQL.ECM_FULLTEXT_SCORE;
        case KEY_IS_TRASHED:
            return NXQL.ECM_ISTRASHED;
        case KEY_BLOB_KEYS:
            return NXQL.ECM_BLOBKEYS;
        case KEY_LIFECYCLE_POLICY:
        case KEY_ACP:
        case KEY_ANCESTOR_IDS:
        case KEY_BASE_VERSION_ID:
        case KEY_READ_ACL:
        case KEY_FULLTEXT_SIMPLE:
        case KEY_FULLTEXT_BINARY:
        case KEY_FULLTEXT_JOBID:
        case KEY_PATH_INTERNAL:
            return null;
        }
        throw new QueryParseException("No such property: " + name);
    }

    protected static final Type STRING_ARRAY_TYPE = new ListTypeImpl("", "", StringType.INSTANCE);

    public static Type getType(String name) {
        switch (name) {
        case KEY_IS_VERSION:
        case KEY_IS_CHECKED_IN:
        case KEY_IS_LATEST_VERSION:
        case KEY_IS_LATEST_MAJOR_VERSION:
        case KEY_IS_PROXY:
        case KEY_ACE_GRANT:
        case KEY_IS_TRASHED:
        case KEY_IS_RECORD:
        case KEY_HAS_LEGAL_HOLD:
            return BooleanType.INSTANCE;
        case KEY_VERSION_CREATED:
        case KEY_LOCK_CREATED:
        case KEY_ACE_BEGIN:
        case KEY_ACE_END:
        case KEY_RETAIN_UNTIL:
            return DateType.INSTANCE;
        case KEY_MIXIN_TYPES:
        case KEY_ANCESTOR_IDS:
        case KEY_PROXY_IDS:
        case KEY_READ_ACL:
        case KEY_BLOB_KEYS:
            return STRING_ARRAY_TYPE;
        }
        return null;
    }

    /**
     * Keys for boolean values that are stored as true or null (never false).
     *
     * @since 11.1
     */
    public static final Set<String> TRUE_OR_NULL_BOOLEAN_KEYS = Set.of( //
            KEY_IS_VERSION, //
            KEY_IS_CHECKED_IN, //
            KEY_IS_LATEST_VERSION, //
            KEY_IS_LATEST_MAJOR_VERSION, //
            KEY_IS_PROXY, //
            KEY_IS_TRASHED, //
            KEY_IS_RECORD, //
            KEY_HAS_LEGAL_HOLD);

    /**
     * Keys for values that are ids.
     *
     * @since 11.1
     */
    public static final Set<String> ID_VALUES_KEYS = Set.of( //
            KEY_ID, //
            KEY_PARENT_ID, //
            KEY_ANCESTOR_IDS, //
            KEY_VERSION_SERIES_ID, //
            KEY_BASE_VERSION_ID, //
            KEY_PROXY_TARGET_ID, //
            KEY_PROXY_IDS, //
            KEY_PROXY_VERSION_SERIES_ID, //
            KEY_FULLTEXT_JOBID);

    @Override
    public LockManager getLockManager() {
        // we have to ask the repository because the lock manager may be contributed
        return ((DBSRepository) repository).getLockManager();
    }

    /**
     * Marks this document id as belonging to a user change.
     *
     * @since 9.2
     */
    public void markUserChange(String id) {
        if (changeTokenEnabled) {
            transaction.markUserChange(id);
        }
    }

    /*
     * ----- Transaction management -----
     */

    @Override
    public void start() {
        transaction.begin();
    }

    @Override
    public void end() {
        // nothing
    }

    @Override
    public void commit() {
        transaction.commit();
    }

    @Override
    public void rollback() {
        transaction.rollback();
    }

}
