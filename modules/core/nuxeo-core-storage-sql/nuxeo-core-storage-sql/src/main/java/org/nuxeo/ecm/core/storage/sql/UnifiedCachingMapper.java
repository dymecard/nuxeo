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
package org.nuxeo.ecm.core.storage.sql;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.query.QueryFilter;

/**
 * A {@link Mapper} that uses a {@link UnifiedCachingRowMapper} for row-related operation, and delegates to the
 * {@link Mapper} for others.
 */
public class UnifiedCachingMapper extends UnifiedCachingRowMapper implements CachingMapper {

    /**
     * The {@link Mapper} to which operations are delegated.
     */
    public Mapper mapper;

    @Override
    public void initialize(String repositoryName, Model model, Mapper mapper,
            VCSInvalidationsPropagator invalidationsPropagator, Map<String, String> properties) {
        super.initialize(repositoryName, model, mapper, invalidationsPropagator, properties);
        this.mapper = mapper;
    }

    @Override
    public ScrollResult scroll(String query, int batchSize, int keepAliveSeconds) {
        return mapper.scroll(query, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult scroll(String query, QueryFilter queryFilter, int batchSize, int keepAliveSeconds) {
        return mapper.scroll(query, queryFilter, batchSize, keepAliveSeconds);
    }

    @Override
    public ScrollResult scroll(String scrollId) {
        return mapper.scroll(scrollId);
    }

    @Override
    public Identification getIdentification() {
        return mapper.getIdentification();
    }

    @Override
    public void close() {
        super.close();
        mapper.close();
    }

    @Override
    public int getTableSize(String tableName) {
        return mapper.getTableSize(tableName);
    }

    @Override
    public Serializable getRootId(String repositoryId) {
        return mapper.getRootId(repositoryId);
    }

    @Override
    public void setRootId(Serializable repositoryId, Serializable id) {
        mapper.setRootId(repositoryId, id);
    }

    @Override
    public PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter,
            boolean countTotal) {
        return mapper.query(query, queryType, queryFilter, countTotal);
    }

    @Override
    public PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter, long countUpTo) {
        return mapper.query(query, queryType, queryFilter, countUpTo);
    }

    @Override
    public IterableQueryResult queryAndFetch(String query, String queryType, QueryFilter queryFilter,
            boolean distinctDocuments, Object... params) {
        return mapper.queryAndFetch(query, queryType, queryFilter, distinctDocuments, params);
    }

    @Override
    public PartialList<Map<String, Serializable>> queryProjection(String query, String queryType,
            QueryFilter queryFilter, boolean distinctDocuments, long countUpTo, Object... params) {
        return mapper.queryProjection(query, queryType, queryFilter, distinctDocuments, countUpTo, params);
    }

    @Override
    public Set<Serializable> getAncestorsIds(Collection<Serializable> ids) {
        return mapper.getAncestorsIds(ids);
    }

    @Override
    public void updateReadAcls() {
        mapper.updateReadAcls();
    }

    @Override
    public void rebuildReadAcls() {
        mapper.rebuildReadAcls();
    }

    @Override
    public int getClusterNodeIdType() {
        return mapper.getClusterNodeIdType();
    }

    @Override
    public void createClusterNode(Serializable nodeId) {
        mapper.createClusterNode(nodeId);
    }

    @Override
    public void removeClusterNode(Serializable nodeId) {
        mapper.removeClusterNode(nodeId);
    }

    @Override
    public void insertClusterInvalidations(Serializable nodeId, VCSInvalidations invalidations) {
        mapper.insertClusterInvalidations(nodeId, invalidations);
    }

    @Override
    public VCSInvalidations getClusterInvalidations(Serializable nodeId) {
        return mapper.getClusterInvalidations(nodeId);
    }

    @Override
    public void markReferencedBlobs(BiConsumer<String, String> markerCallback) {
        mapper.markReferencedBlobs(markerCallback);
    }

    @Override
    public int cleanupDeletedRows(int max, Calendar beforeTime) {
        return mapper.cleanupDeletedRows(max, beforeTime);
    }

}
