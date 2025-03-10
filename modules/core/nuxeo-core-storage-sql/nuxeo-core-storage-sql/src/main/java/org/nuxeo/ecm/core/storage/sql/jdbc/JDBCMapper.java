/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.core.storage.sql.jdbc;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import static org.nuxeo.ecm.core.api.ScrollResultImpl.emptyResult;

import java.io.Serializable;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.ScrollResultImpl;
import org.nuxeo.ecm.core.query.QueryFilter;
import org.nuxeo.ecm.core.storage.sql.ColumnType;
import org.nuxeo.ecm.core.storage.sql.ColumnType.WrappedId;
import org.nuxeo.ecm.core.storage.sql.Mapper;
import org.nuxeo.ecm.core.storage.sql.Model;
import org.nuxeo.ecm.core.storage.sql.RepositoryImpl;
import org.nuxeo.ecm.core.storage.sql.RowId;
import org.nuxeo.ecm.core.storage.sql.Session.PathResolver;
import org.nuxeo.ecm.core.storage.sql.VCSClusterInvalidator;
import org.nuxeo.ecm.core.storage.sql.VCSInvalidations;
import org.nuxeo.ecm.core.storage.sql.jdbc.SQLInfo.SQLInfoSelect;
import org.nuxeo.ecm.core.storage.sql.jdbc.db.Column;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.Dialect;
import org.nuxeo.runtime.api.Framework;

/**
 * A {@link JDBCMapper} maps objects to and from a JDBC database. It is specific to a given database connection, as it
 * computes statements.
 * <p>
 * The {@link JDBCMapper} does the mapping according to the policy defined by a {@link Model}, and generates SQL
 * statements recorded in the {@link SQLInfo}.
 */
public class JDBCMapper extends JDBCRowMapper implements Mapper {

    private static final Log log = LogFactory.getLog(JDBCMapper.class);

    protected static Map<String, CursorResult> cursorResults = new ConcurrentHashMap<>();

    private final QueryMakerService queryMakerService;

    private final PathResolver pathResolver;

    private final RepositoryImpl repository;

    protected static final String NOSCROLL_ID = "noscroll";

    /**
     * Creates a new Mapper.
     *
     * @param model the model
     * @param pathResolver the path resolver (used for startswith queries)
     * @param sqlInfo the sql info
     * @param clusterInvalidator the cluster invalidator
     * @param repository the repository
     */
    public JDBCMapper(Model model, PathResolver pathResolver, SQLInfo sqlInfo, VCSClusterInvalidator clusterInvalidator,
            RepositoryImpl repository) {
        super(model, sqlInfo, clusterInvalidator, repository.getInvalidationsPropagator());
        this.pathResolver = pathResolver;
        this.repository = repository;
        queryMakerService = Framework.getService(QueryMakerService.class);
    }

    @Override
    public void close() {
        closeConnection();
    }

    @Override
    public int getTableSize(String tableName) {
        return sqlInfo.getDatabase().getTable(tableName).getColumns().size();
    }

    @Override
    public int getClusterNodeIdType() {
        return sqlInfo.getClusterNodeIdType();
    }

    @Override
    public void createClusterNode(Serializable nodeId) {
        Calendar now = Calendar.getInstance();
        String sql = sqlInfo.getCreateClusterNodeSql();
        List<Column> columns = sqlInfo.getCreateClusterNodeColumns();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (logger.isLogEnabled()) {
                logger.logSQL(sql, Arrays.asList(nodeId, now));
            }
            columns.get(0).setToPreparedStatement(ps, 1, nodeId);
            columns.get(1).setToPreparedStatement(ps, 2, now);
            ps.execute();

        } catch (SQLException e) {
            try {
                checkConcurrentUpdate(e);
            } catch (ConcurrentUpdateException cue) {
                cue.addInfo("Duplicate cluster node with id: " + nodeId
                        + " (a crashed node must be cleaned up, or the cluster configuration fixed)");
                throw cue;
            }
            throw new NuxeoException(e);
        }
    }

    @Override
    public void removeClusterNode(Serializable nodeId) {
        // delete from cluster_nodes
        String sql = sqlInfo.getDeleteClusterNodeSql();
        Column column = sqlInfo.getDeleteClusterNodeColumn();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (logger.isLogEnabled()) {
                logger.logSQL(sql, Collections.singletonList(nodeId));
            }
            column.setToPreparedStatement(ps, 1, nodeId);
            ps.execute();
            // delete un-processed invals from cluster_invals
            deleteClusterInvals(nodeId);
        } catch (SQLException e) {
            throw new NuxeoException(e);
        }
    }

    protected void deleteClusterInvals(Serializable nodeId) throws SQLException {
        String sql = sqlInfo.getDeleteClusterInvalsSql();
        Column column = sqlInfo.getDeleteClusterInvalsColumn();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (logger.isLogEnabled()) {
                logger.logSQL(sql, Collections.singletonList(nodeId));
            }
            column.setToPreparedStatement(ps, 1, nodeId);
            int n = ps.executeUpdate();
            countExecute();
            if (logger.isLogEnabled()) {
                logger.logCount(n);
            }
        }
    }

    @Override
    public void insertClusterInvalidations(Serializable nodeId, VCSInvalidations invalidations) {
        String sql = dialect.getClusterInsertInvalidations();
        List<Column> columns = sqlInfo.getClusterInvalidationsColumns();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int kind = VCSInvalidations.MODIFIED;
            while (true) {
                Set<RowId> rowIds = invalidations.getKindSet(kind);

                // reorganize by id
                Map<Serializable, Set<String>> res = new HashMap<>();
                for (RowId rowId : rowIds) {
                    Set<String> tableNames = res.get(rowId.id);
                    if (tableNames == null) {
                        res.put(rowId.id, tableNames = new HashSet<>());
                    }
                    tableNames.add(rowId.tableName);
                }

                // do inserts
                for (Entry<Serializable, Set<String>> en : res.entrySet()) {
                    Serializable id = en.getKey();
                    String fragments = join(en.getValue(), ' ');
                    if (logger.isLogEnabled()) {
                        logger.logSQL(sql, Arrays.<Serializable> asList(nodeId, id, fragments, Long.valueOf(kind)));
                    }
                    Serializable frags;
                    if (dialect.supportsArrays() && columns.get(2).getJdbcType() == Types.ARRAY) {
                        frags = fragments.split(" ");
                    } else {
                        frags = fragments;
                    }
                    columns.get(0).setToPreparedStatement(ps, 1, nodeId);
                    columns.get(1).setToPreparedStatement(ps, 2, id);
                    columns.get(2).setToPreparedStatement(ps, 3, frags);
                    columns.get(3).setToPreparedStatement(ps, 4, Long.valueOf(kind));
                    ps.execute();
                    countExecute();
                }
                if (kind == VCSInvalidations.MODIFIED) {
                    kind = VCSInvalidations.DELETED;
                } else {
                    break;
                }
            }
        } catch (SQLException e) {
            throw new NuxeoException("Could not invalidate", e);
        }
    }

    // join that works on a set
    protected static String join(Collection<String> strings, char sep) {
        if (strings.isEmpty()) {
            throw new RuntimeException();
        }
        if (strings.size() == 1) {
            return strings.iterator().next();
        }
        int size = 0;
        for (String word : strings) {
            size += word.length() + 1;
        }
        StringBuilder sb = new StringBuilder(size);
        for (String word : strings) {
            sb.append(word);
            sb.append(sep);
        }
        sb.setLength(size - 1);
        return sb.toString();
    }

    @Override
    public VCSInvalidations getClusterInvalidations(Serializable nodeId) {
        VCSInvalidations invalidations = new VCSInvalidations();
        String sql = dialect.getClusterGetInvalidations();
        List<Column> columns = sqlInfo.getClusterInvalidationsColumns();
        if (logger.isLogEnabled()) {
            logger.logSQL(sql, Collections.singletonList(nodeId));
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setToPreparedStatement(ps, 1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                countExecute();
                while (rs.next()) {
                    // first column ignored, it's the node id
                    Serializable id = columns.get(1).getFromResultSet(rs, 1);
                    Serializable frags = columns.get(2).getFromResultSet(rs, 2);
                    int kind = ((Long) columns.get(3).getFromResultSet(rs, 3)).intValue();
                    String[] fragments;
                    if (dialect.supportsArrays() && frags instanceof String[]) {
                        fragments = (String[]) frags;
                    } else {
                        fragments = ((String) frags).split(" ");
                    }
                    invalidations.add(id, fragments, kind);
                }
            }
            if (logger.isLogEnabled()) {
                // logCount(n);
                logger.log("  -> " + invalidations);
            }
            if (dialect.isClusteringDeleteNeeded()) {
                deleteClusterInvals(nodeId);
            }
            return invalidations;
        } catch (SQLException e) {
            throw new NuxeoException("Could not invalidate", e);
        }
    }

    @Override
    public Serializable getRootId(String repositoryId) {
        String sql = sqlInfo.getSelectRootIdSql();
        if (logger.isLogEnabled()) {
            logger.logSQL(sql, Collections.<Serializable> singletonList(repositoryId));
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, repositoryId);
            try (ResultSet rs = ps.executeQuery()) {
                countExecute();
                if (!rs.next()) {
                    if (logger.isLogEnabled()) {
                        logger.log("  -> (none)");
                    }
                    return null;
                }
                Column column = sqlInfo.getSelectRootIdWhatColumn();
                Serializable id = column.getFromResultSet(rs, 1);
                if (logger.isLogEnabled()) {
                    logger.log("  -> " + Model.MAIN_KEY + '=' + id);
                }
                // check that we didn't get several rows
                if (rs.next()) {
                    throw new NuxeoException("Row query for " + repositoryId + " returned several rows: " + sql);
                }
                return id;
            }
        } catch (SQLException e) {
            throw new NuxeoException("Could not select: " + sql, e);
        }
    }

    @Override
    public void setRootId(Serializable repositoryId, Serializable id) {
        String sql = sqlInfo.getInsertRootIdSql();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            List<Column> columns = sqlInfo.getInsertRootIdColumns();
            List<Serializable> debugValues = null;
            if (logger.isLogEnabled()) {
                debugValues = new ArrayList<>(2);
            }
            int i = 0;
            for (Column column : columns) {
                i++;
                String key = column.getKey();
                Serializable v;
                if (key.equals(Model.MAIN_KEY)) {
                    v = id;
                } else if (key.equals(Model.REPOINFO_REPONAME_KEY)) {
                    v = repositoryId;
                } else {
                    throw new RuntimeException(key);
                }
                column.setToPreparedStatement(ps, i, v);
                if (debugValues != null) {
                    debugValues.add(v);
                }
            }
            if (debugValues != null) {
                logger.logSQL(sql, debugValues);
                debugValues.clear();
            }
            ps.execute();
            countExecute();
        } catch (SQLException e) {
            throw new NuxeoException("Could not insert: " + sql, e);
        }
    }

    protected QueryMaker findQueryMaker(String queryType) {
        for (Class<? extends QueryMaker> klass : queryMakerService.getQueryMakers()) {
            QueryMaker queryMaker;
            try {
                queryMaker = klass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new NuxeoException(e);
            }
            if (queryMaker.accepts(queryType)) {
                return queryMaker;
            }
        }
        return null;
    }

    protected void prepareUserReadAcls(QueryFilter queryFilter) {
        String sql = dialect.getPrepareUserReadAclsSql();
        Serializable principals = queryFilter.getPrincipals();
        if (sql == null || principals == null) {
            return;
        }
        if (!dialect.supportsArrays()) {
            principals = String.join(Dialect.ARRAY_SEP, (String[]) principals);
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (logger.isLogEnabled()) {
                logger.logSQL(sql, Collections.singleton(principals));
            }
            setToPreparedStatement(ps, 1, principals);
            ps.execute();
            countExecute();
        } catch (SQLException e) {
            throw new NuxeoException("Failed to prepare user read acl cache", e);
        }
    }

    @Override
    public PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter,
            boolean countTotal) {
        return query(query, queryType, queryFilter, countTotal ? -1 : 0);
    }

    @Override
    public PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter, long countUpTo) {
        PartialList<Serializable> result = queryProjection(query, queryType, queryFilter, countUpTo,
                (info, rs) -> info.whatColumns.get(0).getFromResultSet(rs, 1));

        if (logger.isLogEnabled()) {
            logger.logIds(result, countUpTo != 0, result.totalSize());
        }

        return result;
    }

    // queryFilter used for principals and permissions
    @Override
    public IterableQueryResult queryAndFetch(String query, String queryType, QueryFilter queryFilter,
            boolean distinctDocuments, Object... params) {
        if (dialect.needsPrepareUserReadAcls()) {
            prepareUserReadAcls(queryFilter);
        }
        QueryMaker queryMaker = findQueryMaker(queryType);
        if (queryMaker == null) {
            throw new NuxeoException("No QueryMaker accepts query: " + queryType + ": " + query);
        }
        query = computeDistinctDocuments(query, distinctDocuments);
        try {
            return new ResultSetQueryResult(queryMaker, query, queryFilter, pathResolver, this, params);
        } catch (SQLException e) {
            throw new NuxeoException("Invalid query: " + queryType + ": " + query, e, SC_BAD_REQUEST);
        }
    }

    @Override
    public PartialList<Map<String, Serializable>> queryProjection(String query, String queryType,
            QueryFilter queryFilter, boolean distinctDocuments, long countUpTo, Object... params) {
        query = computeDistinctDocuments(query, distinctDocuments);
        PartialList<Map<String, Serializable>> result = queryProjection(query, queryType, queryFilter, countUpTo,
                (info, rs) -> info.mapMaker.makeMap(rs), params);

        if (logger.isLogEnabled()) {
            logger.logMaps(result, countUpTo != 0, result.totalSize());
        }

        return result;
    }

    protected String computeDistinctDocuments(String query, boolean distinctDocuments) {
        if (distinctDocuments) {
            String q = query.toLowerCase();
            if (q.startsWith("select ") && !q.startsWith("select distinct ")) {
                // Replace "select" by "select distinct", split at "select ".length() index
                query = "SELECT DISTINCT " + query.substring(7);
            }
        }
        return query;
    }

    protected <T> PartialList<T> queryProjection(String query, String queryType, QueryFilter queryFilter,
            long countUpTo, BiFunctionSQLException<SQLInfoSelect, ResultSet, T> extractor, Object... params) {
        if (dialect.needsPrepareUserReadAcls()) {
            prepareUserReadAcls(queryFilter);
        }
        QueryMaker queryMaker = findQueryMaker(queryType);
        if (queryMaker == null) {
            throw new NuxeoException("No QueryMaker accepts query: " + queryType + ": " + query);
        }
        QueryMaker.Query q = queryMaker.buildQuery(sqlInfo, model, pathResolver, query, queryFilter, params);

        if (q == null) {
            logger.log("Query cannot return anything due to conflicting clauses");
            return new PartialList<>(Collections.emptyList(), 0);
        }
        long limit = queryFilter.getLimit();
        long offset = queryFilter.getOffset();

        if (logger.isLogEnabled()) {
            String sql = q.selectInfo.sql;
            if (limit != 0) {
                sql += " -- LIMIT " + limit + " OFFSET " + offset;
            }
            if (countUpTo != 0) {
                sql += " -- COUNT TOTAL UP TO " + countUpTo;
            }
            logger.logSQL(sql, q.selectParams);
        }

        String sql = q.selectInfo.sql;

        if (countUpTo == 0 && limit > 0 && dialect.supportsPaging()) {
            // full result set not needed for counting
            sql = dialect.addPagingClause(sql, limit, offset);
            limit = 0;
            offset = 0;
        } else if (countUpTo > 0 && dialect.supportsPaging()) {
            // ask one more row
            sql = dialect.addPagingClause(sql, Math.max(countUpTo + 1, limit + offset), 0);
        }

        try (PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY)) {
            int i = 1;
            for (Serializable object : q.selectParams) {
                setToPreparedStatement(ps, i++, object);
            }
            try (ResultSet rs = ps.executeQuery()) {
                countExecute();

                // limit/offset
                long totalSize = -1;
                boolean available;
                if ((limit == 0) || (offset == 0)) {
                    available = rs.first();
                    if (!available) {
                        totalSize = 0;
                    }
                    if (limit == 0) {
                        limit = -1; // infinite
                    }
                } else {
                    available = rs.absolute((int) offset + 1);
                }

                List<T> projections = new LinkedList<>();
                int rowNum = 0;
                while (available && (limit != 0)) {
                    try {
                        T projection = extractor.apply(q.selectInfo, rs);
                        projections.add(projection);
                        rowNum = rs.getRow();
                        available = rs.next();
                        limit--;
                    } catch (SQLDataException e) {
                        // actually no data available, MariaDB Connector/J lied, stop now
                        available = false;
                    }
                }

                // total size
                if (countUpTo != 0 && (totalSize == -1)) {
                    if (!available && (rowNum != 0)) {
                        // last row read was the actual last
                        totalSize = rowNum;
                    } else {
                        // available if limit reached with some left
                        // rowNum == 0 if skipped too far
                        rs.last();
                        totalSize = rs.getRow();
                    }
                    if (countUpTo > 0 && totalSize > countUpTo) {
                        // the result where truncated we don't know the total size
                        totalSize = -2;
                    }
                }

                return new PartialList<>(projections, totalSize);
            }
        } catch (SQLException e) {
            throw new NuxeoException("Invalid query: " + query, e, SC_BAD_REQUEST);
        }
    }

    public int setToPreparedStatement(PreparedStatement ps, int i, Serializable object) throws SQLException {
        if (object instanceof Calendar) {
            dialect.setToPreparedStatementTimestamp(ps, i, object, null);
        } else if (object instanceof java.sql.Date) {
            ps.setDate(i, (java.sql.Date) object);
        } else if (object instanceof Long) {
            ps.setLong(i, ((Long) object).longValue());
        } else if (object instanceof WrappedId) {
            dialect.setId(ps, i, object.toString());
        } else if (object instanceof Object[]) {
            int jdbcType;
            if (object instanceof String[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.STRING).jdbcType;
            } else if (object instanceof Boolean[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.BOOLEAN).jdbcType;
            } else if (object instanceof Long[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.LONG).jdbcType;
            } else if (object instanceof Double[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.DOUBLE).jdbcType;
            } else if (object instanceof java.sql.Date[]) {
                jdbcType = Types.DATE;
            } else if (object instanceof java.sql.Clob[]) {
                jdbcType = Types.CLOB;
            } else if (object instanceof Calendar[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.TIMESTAMP).jdbcType;
                object = dialect.getTimestampFromCalendar((Calendar[]) object);
            } else if (object instanceof Integer[]) {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.INTEGER).jdbcType;
            } else {
                jdbcType = dialect.getJDBCTypeAndString(ColumnType.CLOB).jdbcType;
            }
            Array array = dialect.createArrayOf(jdbcType, (Object[]) object, connection);
            ps.setArray(i, array);
        } else {
            ps.setObject(i, object);
        }
        return i;
    }

    @Override
    public ScrollResult<String> scroll(String query, int batchSize, int keepAliveSeconds) {
        if (!dialect.supportsScroll()) {
            return defaultScroll(query);
        }
        checkForTimedoutScroll();
        QueryFilter queryFilter = new QueryFilter(null, null, null, null, Collections.emptyList(), 0, 0);
        return scrollSearch(query, queryFilter, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult<String> scroll(String query, QueryFilter queryFilter, int batchSize, int keepAliveSeconds) {
        if (!dialect.supportsScroll()) {
            return defaultScroll(query);
        }
        if (dialect.needsPrepareUserReadAcls()) {
            prepareUserReadAcls(queryFilter);
        }
        checkForTimedoutScroll();
        return scrollSearch(query, queryFilter, batchSize, keepAliveSeconds);
    }

    protected void checkForTimedoutScroll() {
        cursorResults.forEach((id, cursor) -> cursor.timedOut(id));
    }

    @SuppressWarnings("resource") // PreparedStatement + ResultSet for cursor, must not be closed
    protected ScrollResult<String> scrollSearch(String query, QueryFilter queryFilter, int batchSize,
            int keepAliveSeconds) {
        QueryMaker queryMaker = findQueryMaker("NXQL");
        QueryMaker.Query q = queryMaker.buildQuery(sqlInfo, model, pathResolver, query, queryFilter);
        if (q == null) {
            logger.log("Query cannot return anything due to conflicting clauses");
            throw new NuxeoException("Query cannot return anything due to conflicting clauses");
        }
        if (logger.isLogEnabled()) {
            logger.logSQL(q.selectInfo.sql, q.selectParams);
        }
        try {
            if (connection.getAutoCommit()) {
                throw new NuxeoException("Scroll should be done inside a transaction");
            }
            // ps MUST NOT be auto-closed because it's referenced by a cursor
            PreparedStatement ps = connection.prepareStatement(q.selectInfo.sql, ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
            ps.setFetchSize(batchSize);
            int i = 1;
            for (Serializable object : q.selectParams) {
                setToPreparedStatement(ps, i++, object);
            }
            // rs MUST NOT be auto-closed because it's referenced by a cursor
            ResultSet rs = ps.executeQuery();
            String scrollId = UUID.randomUUID().toString();
            registerCursor(scrollId, ps, rs, batchSize, keepAliveSeconds);
            return scroll(scrollId);
        } catch (SQLException e) {
            throw new NuxeoException("Error on query", e);
        }
    }

    protected class CursorResult {
        protected final int keepAliveSeconds;

        protected final PreparedStatement preparedStatement;

        protected final ResultSet resultSet;

        protected final int batchSize;

        protected long lastCallTimestamp;

        CursorResult(PreparedStatement preparedStatement, ResultSet resultSet, int batchSize, int keepAliveSeconds) {
            this.preparedStatement = preparedStatement;
            this.resultSet = resultSet;
            this.batchSize = batchSize;
            this.keepAliveSeconds = keepAliveSeconds;
            lastCallTimestamp = System.currentTimeMillis();
        }

        boolean timedOut(String scrollId) {
            long now = System.currentTimeMillis();
            if (now - lastCallTimestamp > (keepAliveSeconds * 1000)) {
                if (unregisterCursor(scrollId)) {
                    log.warn("Scroll " + scrollId + " timed out");
                }
                return true;
            }
            return false;
        }

        void touch() {
            lastCallTimestamp = System.currentTimeMillis();
        }

        synchronized void close() throws SQLException {
            if (resultSet != null) {
                resultSet.close();
            }
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        }
    }

    protected void registerCursor(String scrollId, PreparedStatement ps, ResultSet rs, int batchSize,
            int keepAliveSeconds) {
        cursorResults.put(scrollId, new CursorResult(ps, rs, batchSize, keepAliveSeconds));
    }

    protected boolean unregisterCursor(String scrollId) {
        CursorResult cursor = cursorResults.remove(scrollId);
        if (cursor != null) {
            try {
                cursor.close();
                return true;
            } catch (SQLException e) {
                log.error("Failed to close cursor for scroll: " + scrollId, e);
                // do not propagate exception on cleaning
            }
        }
        return false;
    }

    protected ScrollResult<String> defaultScroll(String query) {
        // the database has no proper support for cursor just return everything in one batch
        QueryMaker queryMaker = findQueryMaker("NXQL");
        List<String> ids;
        QueryFilter queryFilter = new QueryFilter(null, null, null, null, Collections.emptyList(), 0, 0);
        try (IterableQueryResult ret = new ResultSetQueryResult(queryMaker, query, queryFilter, pathResolver, this)) {
            ids = new ArrayList<>((int) ret.size());
            for (Map<String, Serializable> map : ret) {
                ids.add(map.get("ecm:uuid").toString());
            }
        } catch (SQLException e) {
            throw new NuxeoException("Invalid scroll query: " + query, e);
        }
        return new ScrollResultImpl<>(NOSCROLL_ID, ids);
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        if (NOSCROLL_ID.equals(scrollId) || !dialect.supportsScroll()) {
            // there is only one batch in this case
            return emptyResult();
        }
        CursorResult cursorResult = cursorResults.get(scrollId);
        if (cursorResult == null) {
            throw new NuxeoException("Unknown or timed out scrollId");
        } else if (cursorResult.timedOut(scrollId)) {
            throw new NuxeoException("Timed out scrollId");
        }
        cursorResult.touch();
        List<String> ids = new ArrayList<>(cursorResult.batchSize);
        synchronized (cursorResult) {
            try {
                if (cursorResult.resultSet == null || cursorResult.resultSet.isClosed()) {
                    unregisterCursor(scrollId);
                    return emptyResult();
                }
                while (ids.size() < cursorResult.batchSize) {
                    if (cursorResult.resultSet.next()) {
                        ids.add(cursorResult.resultSet.getString(1));
                    } else {
                        cursorResult.close();
                        if (ids.isEmpty()) {
                            unregisterCursor(scrollId);
                        }
                        break;
                    }
                }
            } catch (SQLException e) {
                throw new NuxeoException("Error during scroll", e);
            }
        }
        return new ScrollResultImpl<>(scrollId, ids);
    }

    @Override
    public Set<Serializable> getAncestorsIds(Collection<Serializable> ids) {
        SQLInfoSelect select = sqlInfo.getSelectAncestorsIds();
        if (select == null) {
            return getAncestorsIdsIterative(ids);
        }
        Serializable whereIds = newIdArray(ids);
        Set<Serializable> res = new HashSet<>();
        if (logger.isLogEnabled()) {
            logger.logSQL(select.sql, Collections.singleton(whereIds));
        }
        Column what = select.whatColumns.get(0);
        try (PreparedStatement ps = connection.prepareStatement(select.sql)) {
            setToPreparedStatementIdArray(ps, 1, whereIds);
            try (ResultSet rs = ps.executeQuery()) {
                countExecute();
                List<Serializable> debugIds = null;
                if (logger.isLogEnabled()) {
                    debugIds = new LinkedList<>();
                }
                while (rs.next()) {
                    if (dialect.supportsArraysReturnInsteadOfRows()) {
                        Serializable[] resultIds = dialect.getArrayResult(rs.getArray(1));
                        for (Serializable id : resultIds) {
                            if (id != null) {
                                res.add(id);
                                if (logger.isLogEnabled()) {
                                    debugIds.add(id);
                                }
                            }
                        }
                    } else {
                        Serializable id = what.getFromResultSet(rs, 1);
                        if (id != null) {
                            res.add(id);
                            if (logger.isLogEnabled()) {
                                debugIds.add(id);
                            }
                        }
                    }
                }
                if (logger.isLogEnabled()) {
                    logger.logIds(debugIds, false, 0);
                }
            }
            return res;
        } catch (SQLException e) {
            throw new NuxeoException("Failed to get ancestors ids", e);
        }
    }

    /**
     * Uses iterative parentid selection.
     */
    protected Set<Serializable> getAncestorsIdsIterative(Collection<Serializable> ids) {
        try {
            LinkedList<Serializable> todo = new LinkedList<>(ids);
            Set<Serializable> done = new HashSet<>();
            Set<Serializable> res = new HashSet<>();
            while (!todo.isEmpty()) {
                done.addAll(todo);
                SQLInfoSelect select = sqlInfo.getSelectParentIds(todo.size());
                if (logger.isLogEnabled()) {
                    logger.logSQL(select.sql, todo);
                }
                Column what = select.whatColumns.get(0);
                Column where = select.whereColumns.get(0);
                try (PreparedStatement ps = connection.prepareStatement(select.sql)) {
                    int i = 1;
                    for (Serializable id : todo) {
                        where.setToPreparedStatement(ps, i++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        countExecute();
                        todo = new LinkedList<>();
                        List<Serializable> debugIds = null;
                        if (logger.isLogEnabled()) {
                            debugIds = new LinkedList<>();
                        }
                        while (rs.next()) {
                            Serializable id = what.getFromResultSet(rs, 1);
                            if (id != null) {
                                res.add(id);
                                if (!done.contains(id)) {
                                    todo.add(id);
                                }
                                if (logger.isLogEnabled()) {
                                    debugIds.add(id); // NOSONAR
                                }
                            }
                        }
                        if (logger.isLogEnabled()) {
                            logger.logIds(debugIds, false, 0);
                        }
                    }
                }
            }
            return res;
        } catch (SQLException e) {
            throw new NuxeoException("Failed to get ancestors ids", e);
        }
    }

    @Override
    public void updateReadAcls() {
        if (!dialect.supportsReadAcl()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("updateReadAcls: updating");
        }
        try (Statement st = connection.createStatement()) {
            String sql = dialect.getUpdateReadAclsSql();
            if (logger.isLogEnabled()) {
                logger.log(sql);
            }
            st.execute(sql);
            countExecute();
        } catch (SQLException e) {
            checkConcurrentUpdate(e);
            throw new NuxeoException("Failed to update read acls", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("updateReadAcls: done.");
        }
    }

    @Override
    public void rebuildReadAcls() {
        if (!dialect.supportsReadAcl()) {
            return;
        }
        log.debug("rebuildReadAcls: rebuilding ...");
        try (Statement st = connection.createStatement()) {
            String sql = dialect.getRebuildReadAclsSql();
            logger.log(sql);
            st.execute(sql);
            countExecute();
        } catch (SQLException e) {
            throw new NuxeoException("Failed to rebuild read acls", e);
        }
        log.debug("rebuildReadAcls: done.");
    }

    @Override
    public void markReferencedBlobs(BiConsumer<String, String> markerCallback) {
        log.debug("Starting binaries GC mark");
        String repositoryName = getRepositoryName();
        try (Statement st = connection.createStatement()) {
            int i = -1;
            for (String sql : sqlInfo.getBinariesSql) {
                i++;
                Column col = sqlInfo.getBinariesColumns.get(i);
                if (logger.isLogEnabled()) {
                    logger.log(sql);
                }
                try (ResultSet rs = st.executeQuery(sql)) {
                    countExecute();
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        String key = (String) col.getFromResultSet(rs, 1);
                        if (key != null) {
                            markerCallback.accept(key, repositoryName);
                        }
                    }
                    if (logger.isLogEnabled()) {
                        logger.logCount(n);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark binaries for gC", e);
        }
        log.debug("End of binaries GC mark");
    }

    /**
     * @since 7.10-HF25, 8.10-HF06, 9.2
     */
    @FunctionalInterface
    protected interface BiFunctionSQLException<T, U, R> {

        /**
         * Applies this function to the given arguments.
         *
         * @param t the first function argument
         * @param u the second function argument
         * @return the function result
         */
        R apply(T t, U u) throws SQLException;

    }

}
