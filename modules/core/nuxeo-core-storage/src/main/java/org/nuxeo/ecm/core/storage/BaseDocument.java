/*
 * (C) Copyright 2015-2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.storage;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.BlobNotFoundException;
import org.nuxeo.ecm.core.api.model.DocumentPart;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyConversionException;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.model.impl.ComplexProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.DocumentBlobManager;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BinaryType;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.IntegerType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.runtime.api.Framework;

/**
 * Base implementation for a Document.
 * <p>
 * Knows how to read and write values. It is generic in terms of a base State class from which one can read and write
 * values.
 *
 * @since 7.3
 */
public abstract class BaseDocument<T extends StateAccessor> implements Document {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    public static final String BLOB_NAME = "name";

    public static final String BLOB_MIME_TYPE = "mime-type";

    public static final String BLOB_ENCODING = "encoding";

    public static final String BLOB_DIGEST = "digest";

    public static final String BLOB_LENGTH = "length";

    public static final String BLOB_DATA = "data";

    public static final String DC_PREFIX = "dc:";

    public static final String DC_ISSUED = "dc:issued";

    // used instead of ecm:changeToken when change tokens are disabled
    public static final String DC_MODIFIED = "dc:modified";

    public static final String RELATED_TEXT_RESOURCES = "relatedtextresources";

    public static final String RELATED_TEXT_ID = "relatedtextid";

    public static final String RELATED_TEXT = "relatedtext";

    public static final String FULLTEXT_JOBID_PROP = "ecm:fulltextJobId";

    public static final String FULLTEXT_SIMPLETEXT_PROP = "ecm:fulltextSimple";

    public static final String FULLTEXT_BINARYTEXT_PROP = "ecm:fulltextBinary";

    public static final String IS_TRASHED_PROP = "ecm:isTrashed";

    public static final String MISC_LIFECYCLE_STATE_PROP = "ecm:lifeCycleState";

    public static final String LOCK_OWNER_PROP = "ecm:lockOwner";

    public static final String LOCK_CREATED_PROP = "ecm:lockCreated";

    /** @since 11.1 */
    public static final String IS_RECORD_PROP = "ecm:isRecord";

    /** @since 11.1 */
    public static final String RETAIN_UNTIL_PROP = "ecm:retainUntil";

    /** @since 11.1 */
    public static final String HAS_LEGAL_HOLD_PROP = "ecm:hasLegalHold";

    public static final Set<String> VERSION_WRITABLE_PROPS = new HashSet<>(Arrays.asList( //
            FULLTEXT_JOBID_PROP, //
            FULLTEXT_BINARYTEXT_PROP, //
            IS_TRASHED_PROP, //
            MISC_LIFECYCLE_STATE_PROP, //
            LOCK_OWNER_PROP, //
            LOCK_CREATED_PROP, //
            DC_ISSUED, //
            IS_RECORD_PROP, //
            RETAIN_UNTIL_PROP, //
            HAS_LEGAL_HOLD_PROP, //
            RELATED_TEXT_RESOURCES, //
            RELATED_TEXT_ID, //
            RELATED_TEXT //
    ));

    protected final static Pattern NON_CANONICAL_INDEX = Pattern.compile("[^/\\[\\]]+" // name
            + "\\[(\\d+)\\]" // index in brackets
    );

    protected static final Runnable NO_DIRTY = () -> {
    };

    /**
     * Gets the list of proxy schemas, if this is a proxy.
     *
     * @return the proxy schemas, or {@code null}
     */
    protected abstract List<Schema> getProxySchemas();

    /**
     * Gets a child state.
     *
     * @param state the parent state
     * @param name the child name
     * @param type the child's type
     * @return the child state, or {@code null} if it doesn't exist
     */
    protected abstract T getChild(T state, String name, Type type) throws PropertyException;

    /**
     * Gets a child state into which we will want to write data.
     * <p>
     * Creates it if needed.
     *
     * @param state the parent state
     * @param name the child name
     * @param type the child's type
     * @return the child state, never {@code null}
     * @since 7.4
     */
    protected abstract T getChildForWrite(T state, String name, Type type) throws PropertyException;

    /**
     * Gets a child state which is a list.
     *
     * @param state the parent state
     * @param name the child name
     * @return the child state, never {@code null}
     */
    protected abstract List<T> getChildAsList(T state, String name) throws PropertyException;

    /**
     * Update a list.
     *
     * @param state the parent state
     * @param name the child name
     * @param field the list element type
     * @param xpath the xpath of this list
     * @param values the values
     */
    protected abstract void updateList(T state, String name, Field field, String xpath, List<Object> values)
            throws PropertyException;

    /**
     * Update a list.
     *
     * @param state the parent state
     * @param name the child name
     * @param property the property
     * @return the list of states to write
     */
    protected abstract List<T> updateList(T state, String name, Property property) throws PropertyException;

    /**
     * Finds the internal name to use to refer to this property.
     */
    protected abstract String internalName(String name);

    /**
     * Canonicalizes a Nuxeo xpath.
     * <p>
     * Replaces {@code a/foo[123]/b} with {@code a/123/b}
     *
     * @param xpath the xpath
     * @return the canonicalized xpath.
     */
    protected static String canonicalXPath(String xpath) {
        if (xpath.indexOf('[') > 0) {
            xpath = NON_CANONICAL_INDEX.matcher(xpath).replaceAll("$1");
        }
        return xpath;
    }

    /** Copies the array with an appropriate class depending on the type. */
    protected static Object[] typedArray(Type type, Object[] array) {
        if (array == null) {
            array = EMPTY_STRING_ARRAY;
        }
        Class<?> klass;
        if (type instanceof StringType) {
            klass = String.class;
        } else if (type instanceof BooleanType) {
            klass = Boolean.class;
        } else if (type instanceof LongType) {
            klass = Long.class;
        } else if (type instanceof DoubleType) {
            klass = Double.class;
        } else if (type instanceof DateType) {
            klass = Calendar.class;
        } else if (type instanceof BinaryType) {
            klass = String.class;
        } else if (type instanceof IntegerType) {
            throw new RuntimeException("Unimplemented primitive type: " + type.getClass().getName());
        } else if (type instanceof SimpleTypeImpl) {
            // simple type with constraints -- ignore constraints XXX
            return typedArray(type.getSuperType(), array);
        } else {
            throw new RuntimeException("Invalid primitive type: " + type.getClass().getName());
        }
        int len = array.length;
        Object[] copy = (Object[]) Array.newInstance(klass, len);
        System.arraycopy(array, 0, copy, 0, len);
        return copy;
    }

    protected static boolean isVersionWritableProperty(String name) {
        return VERSION_WRITABLE_PROPS.contains(name) //
                || name.startsWith(FULLTEXT_BINARYTEXT_PROP) //
                || name.startsWith(FULLTEXT_SIMPLETEXT_PROP);
    }

    protected static void clearDirtyFlags(Property property) {
        if (property.isContainer()) {
            for (Property p : property) {
                clearDirtyFlags(p);
            }
        }
        property.clearDirtyFlags();
    }

    /**
     * Checks for ignored writes. May throw.
     */
    protected boolean checkReadOnlyIgnoredWrite(Property property, T state) throws PropertyException {
        String name = property.getField().getName().getPrefixedName();
        if (!isReadOnly() || isVersionWritableProperty(name)) {
            // do write
            return false;
        }
        if (!isVersion()) {
            throw new PropertyException("Cannot write readonly property: " + name);
        }
        if (getTopLevelSchema(property).isVersionWritabe()) {
            // do write
            return false;
        }
        if (!name.startsWith(DC_PREFIX)) {
            throw new PropertyException("Cannot set property on a version: " + name);
        }
        // ignore write if value can quickly be detected as unchanged
        Object value = property.getValueForWrite();
        Type type = property.getType();
        boolean equals;
        if (type.isSimpleType()) {
            Object oldValue = state.getSingle(name);
            equals = Objects.deepEquals(value, oldValue);
        } else if (type.isListType() && ((ListType) type).getFieldType().isSimpleType()) {
            Object[] oldValue = state.getArray(name);
            if (value == null && oldValue != null && oldValue.length == 0) {
                // empty array and null are the same
                equals = true;
            } else {
                equals = Objects.deepEquals(value, oldValue);
            }
        } else {
            // complex property or complex list, no quick way to detect changes
            equals = false;
        }
        if (equals) {
            // unchanged, ignore write even though strictly speaking writing the version is not allowed
            return true;
        } else {
            if (Framework.getService(SchemaManager.class).getAllowVersionWriteForDublinCore()) {
                // do write (compatibility with old Nuxeo versions that had this bug)
                return false;
            }
            throw new PropertyException("Cannot set property on a version: " + name);
        }
    }

    /**
     * Gets the {@link Schema} at the top-level of the type hierarchy for this {@link Property}.
     *
     * @since 9.3
     */
    protected Schema getTopLevelSchema(Property property) {
        for (;;) {
            Type type = property.getType();
            if (type instanceof Schema) {
                return (Schema) type;
            }
            property = property.getParent();
        }
    }

    protected BlobInfo getBlobInfo(T state) throws PropertyException {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = (String) state.getSingle(BLOB_DATA);
        blobInfo.filename = (String) state.getSingle(BLOB_NAME);
        blobInfo.mimeType = (String) state.getSingle(BLOB_MIME_TYPE);
        blobInfo.encoding = (String) state.getSingle(BLOB_ENCODING);
        blobInfo.digest = (String) state.getSingle(BLOB_DIGEST);
        blobInfo.length = (Long) state.getSingle(BLOB_LENGTH);
        return blobInfo;
    }

    protected void setBlobInfo(T state, BlobInfo blobInfo) throws PropertyException {
        state.setSingle(BLOB_DATA, blobInfo.key);
        state.setSingle(BLOB_NAME, blobInfo.filename);
        state.setSingle(BLOB_MIME_TYPE, blobInfo.mimeType);
        state.setSingle(BLOB_ENCODING, blobInfo.encoding);
        state.setSingle(BLOB_DIGEST, blobInfo.digest);
        state.setSingle(BLOB_LENGTH, blobInfo.length);
    }

    /**
     * Gets a value (may be complex/list) from the document at the given xpath.
     */
    protected Object getValueObject(T state, String xpath) throws PropertyException {
        xpath = canonicalXPath(xpath);
        String[] segments = xpath.split("/");

        /*
         * During this loop state may become null if we read an uninitialized complex property (DBS), in that case the
         * code must treat it as reading uninitialized values for its children.
         */
        ComplexType parentType = getType();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            Field field = parentType.getField(segment);
            if (field == null && i == 0) {
                // check facets
                SchemaManager schemaManager = Framework.getService(SchemaManager.class);
                for (String facet : getFacets()) {
                    CompositeType facetType = schemaManager.getFacet(facet);
                    field = facetType.getField(segment);
                    if (field != null) {
                        break;
                    }
                }
            }
            if (field == null && i == 0 && getProxySchemas() != null) {
                // check proxy schemas
                for (Schema schema : getProxySchemas()) {
                    field = schema.getField(segment);
                    if (field != null) {
                        break;
                    }
                }
            }
            if (field == null) {
                throw new PropertyNotFoundException(xpath, i == 0 ? null : "Unknown segment: " + segment);
            }
            String name = field.getName().getPrefixedName(); // normalize from segment
            Type type = field.getType();

            // check if we have a complex list index in the next position
            if (i < segments.length - 1 && StringUtils.isNumeric(segments[i + 1])) {
                int index = Integer.parseInt(segments[i + 1]);
                i++;
                if (!type.isListType() || ((ListType) type).getFieldType().isSimpleType()) {
                    throw new PropertyNotFoundException(xpath, "Cannot use index after segment: " + segment);
                }
                List<T> list = state == null ? Collections.emptyList() : getChildAsList(state, name);
                if (index >= list.size()) {
                    throw new PropertyNotFoundException(xpath, "Index out of bounds: " + index);
                }
                // find complex list state
                state = list.get(index);
                parentType = (ComplexType) ((ListType) type).getFieldType();
                if (i == segments.length - 1) {
                    // last segment
                    return getValueComplex(state, parentType, xpath);
                } else {
                    // not last segment
                    continue;
                }
            }

            if (i == segments.length - 1) {
                // last segment
                return state == null ? null : getValueField(state, field, xpath);
            } else {
                // not last segment
                if (type.isSimpleType()) {
                    // scalar
                    throw new PropertyNotFoundException(xpath, "Segment must be last: " + segment);
                } else if (type.isComplexType()) {
                    // complex property
                    state = state == null ? null : getChild(state, name, type);
                    // here state can be null (DBS), continue loop with it, meaning uninitialized for read
                    parentType = (ComplexType) type;
                } else {
                    // list
                    ListType listType = (ListType) type;
                    if (listType.isArray()) {
                        // array of scalars
                        throw new PropertyNotFoundException(xpath, "Segment must be last: " + segment);
                    } else {
                        // complex list but next segment was not numeric
                        throw new PropertyNotFoundException(xpath, "Missing list index after segment: " + segment);
                    }
                }
            }
        }
        throw new AssertionError("not reached");
    }

    protected Object getValueField(T state, Field field, String xpath) throws PropertyException {
        Type type = field.getType();
        String name = field.getName().getPrefixedName();
        name = internalName(name);
        String xp = xpath == null ? name : xpath + '/' + name;
        if (type.isSimpleType()) {
            // scalar
            return state.getSingle(name);
        } else if (type.isComplexType()) {
            // complex property
            T childState = getChild(state, name, type);
            if (childState == null) {
                return null;
            }
            return getValueComplex(childState, (ComplexType) type, xp);
        } else {
            // array or list
            Type fieldType = ((ListType) type).getFieldType();
            if (fieldType.isSimpleType()) {
                // array
                return state.getArray(name);
            } else {
                // complex list
                List<T> childStates = getChildAsList(state, name);
                List<Object> list = new ArrayList<>(childStates.size());
                int i = 0;
                for (T childState : childStates) {
                    String xpi = xp + '/' + i++;
                    Object value = getValueComplex(childState, (ComplexType) fieldType, xpi);
                    list.add(value);
                }
                return list;
            }
        }
    }

    protected Object getValueComplex(T state, ComplexType complexType, String xpath) throws PropertyException {
        if (TypeConstants.isContentType(complexType)) {
            return getValueBlob(state, xpath);
        }
        Map<String, Object> map = new HashMap<>();
        for (Field field : complexType.getFields()) {
            String name = field.getName().getPrefixedName();
            String xp = xpath + '/' + name;
            Object value = getValueField(state, field, xp);
            map.put(name, value);
        }
        return map;
    }

    protected Blob getValueBlob(T state, String xpath) throws PropertyException {
        BlobInfo blobInfo = getBlobInfo(state);
        DocumentBlobManager blobManager = Framework.getService(DocumentBlobManager.class);
        try {
            return blobManager.readBlob(blobInfo, this, xpath);
        } catch (IOException e) {
            throw new BlobNotFoundException("Unable to find blob with key: " + blobInfo.key, e);
        }
    }

    /**
     * Sets a value (may be complex/list) into the document at the given xpath.
     */
    protected void setValueObject(T state, String xpath, Object value) throws PropertyException {
        xpath = canonicalXPath(xpath);
        String[] segments = xpath.split("/");

        ComplexType parentType = getType();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            Field field = parentType.getField(segment);
            if (field == null && i == 0) {
                // check facets
                SchemaManager schemaManager = Framework.getService(SchemaManager.class);
                for (String facet : getFacets()) {
                    CompositeType facetType = schemaManager.getFacet(facet);
                    field = facetType.getField(segment);
                    if (field != null) {
                        break;
                    }
                }
            }
            if (field == null && i == 0 && getProxySchemas() != null) {
                // check proxy schemas
                for (Schema schema : getProxySchemas()) {
                    field = schema.getField(segment);
                    if (field != null) {
                        break;
                    }
                }
            }
            if (field == null) {
                throw new PropertyNotFoundException(xpath, i == 0 ? null : "Unknown segment: " + segment);
            }
            String name = field.getName().getPrefixedName(); // normalize from segment
            Type type = field.getType();

            // check if we have a complex list index in the next position
            if (i < segments.length - 1 && StringUtils.isNumeric(segments[i + 1])) {
                int index = Integer.parseInt(segments[i + 1]);
                i++;
                if (!type.isListType() || ((ListType) type).getFieldType().isSimpleType()) {
                    throw new PropertyNotFoundException(xpath, "Cannot use index after segment: " + segment);
                }
                List<T> list = getChildAsList(state, name);
                if (index >= list.size()) {
                    throw new PropertyNotFoundException(xpath, "Index out of bounds: " + index);
                }
                // find complex list state
                state = list.get(index);
                field = ((ListType) type).getField();
                if (i == segments.length - 1) {
                    // last segment
                    setValueComplex(state, field, xpath, value);
                } else {
                    // not last segment
                    parentType = (ComplexType) field.getType();
                }
                continue;
            }

            if (i == segments.length - 1) {
                // last segment
                setValueField(state, field, xpath, value);
            } else {
                // not last segment
                if (type.isSimpleType()) {
                    // scalar
                    throw new PropertyNotFoundException(xpath, "Segment must be last: " + segment);
                } else if (type.isComplexType()) {
                    // complex property
                    state = getChildForWrite(state, name, type);
                    parentType = (ComplexType) type;
                } else {
                    // list
                    ListType listType = (ListType) type;
                    if (listType.isArray()) {
                        // array of scalars
                        throw new PropertyNotFoundException(xpath, "Segment must be last: " + segment);
                    } else {
                        // complex list but next segment was not numeric
                        throw new PropertyNotFoundException(xpath, "Missing list index after segment: " + segment);
                    }
                }
            }
        }
    }

    protected void setValueField(T state, Field field, String xpath, Object value) throws PropertyException {
        Type type = field.getType();
        String name = field.getName().getPrefixedName(); // normalize from map key
        name = internalName(name);
        // TODO we could check for read-only here
        if (type.isSimpleType()) {
            // scalar
            state.setSingle(name, value);
        } else if (type.isComplexType()) {
            // complex property
            T childState = getChildForWrite(state, name, type);
            setValueComplex(childState, field, xpath, value);
        } else {
            // array or list
            ListType listType = (ListType) type;
            Type fieldType = listType.getFieldType();
            if (fieldType.isSimpleType()) {
                // array
                if (value instanceof List) {
                    value = ((List<?>) value).toArray(new Object[0]);
                }
                state.setArray(name, (Object[]) value);
            } else {
                // complex list
                if (value != null && !(value instanceof List)) {
                    throw new PropertyException(
                            "Expected List value for: " + name + ", got " + value.getClass().getName() + " instead");
                }
                @SuppressWarnings("unchecked")
                List<Object> values = value == null ? Collections.emptyList() : (List<Object>) value;
                updateList(state, name, listType.getField(), xpath, values);
            }
        }
    }

    protected void setValueComplex(T state, Field field, String xpath, Object value) throws PropertyException {
        ComplexType complexType = (ComplexType) field.getType();
        if (TypeConstants.isContentType(complexType)) {
            if (value != null && !(value instanceof Blob)) {
                throw new PropertyException(
                        "Expected Blob value for: " + xpath + ", got " + value.getClass().getName() + " instead");
            }
            setValueBlob(state, (Blob) value, xpath);
            return;
        }
        if (value != null && !(value instanceof Map)) {
            throw new PropertyException(
                    "Expected Map value for: " + xpath + ", got " + value.getClass().getName() + " instead");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = value == null ? Collections.emptyMap() : (Map<String, Object>) value;
        Set<String> keys = new HashSet<>(map.keySet());
        for (Field f : complexType.getFields()) {
            String name = f.getName().getPrefixedName();
            keys.remove(name);
            value = map.get(name);
            setValueField(state, f, xpath + '/' + name, value);
        }
        if (!keys.isEmpty()) {
            throw new PropertyException("Unknown key: " + keys.iterator().next() + " for " + xpath);
        }
    }

    protected void setValueBlob(T state, Blob blob, String xpath) throws PropertyException {
        BlobInfo blobInfo = new BlobInfo();
        DocumentBlobManager blobManager = Framework.getService(DocumentBlobManager.class);
        try {
            blobInfo.key = blobManager.writeBlob(blob, this, xpath);
        } catch (IOException e) {
            throw new PropertyException("Cannot get blob info for: " + blob, e);
        }
        if (blob != null) {
            blobInfo.filename = blob.getFilename();
            blobInfo.mimeType = blob.getMimeType();
            blobInfo.encoding = blob.getEncoding();
            blobInfo.digest = blob.getDigest();
            blobInfo.length = blob.getLength() == -1 ? null : Long.valueOf(blob.getLength());
        }
        setBlobInfo(state, blobInfo);
    }

    protected void setPropertyBlobData(String xpath, String string) {
        Blob blob = string == null ? null : Blobs.createBlob(string);
        DocumentBlobManager blobManager = Framework.getService(DocumentBlobManager.class);
        String key;
        try {
            key = blobManager.writeBlob(blob, this, xpath);
        } catch (IOException e) {
            throw new PropertyException("Cannot write binary for doc: " + getUUID(), e);
        }
        setPropertyValue(xpath, key);
    }

    /**
     * Reads state into a complex property.
     */
    protected void readComplexProperty(T state, ComplexProperty complexProperty) throws PropertyException {
        readComplexProperty(state, complexProperty, null);
    }

    protected void readComplexProperty(T state, ComplexProperty complexProperty, String xpath)
            throws PropertyException {
        if (state == null) {
            complexProperty.init(null);
            return;
        }
        if (complexProperty instanceof BlobProperty) {
            Blob blob = getValueBlob(state, xpath);
            complexProperty.init((Serializable) blob);
            return;
        }
        for (Property property : complexProperty) {
            String name = property.getField().getName().getPrefixedName();
            name = internalName(name);
            String xp = xpath == null ? name : xpath + '/' + name;
            Type type = property.getType();
            try {
                if (type.isSimpleType()) {
                    // simple property
                    Object value = state.getSingle(name);
                    property.init((Serializable) value);
                } else if (type.isComplexType()) {
                    // complex property
                    T childState = getChild(state, name, type);
                    readComplexProperty(childState, (ComplexProperty) property, xp);
                    ((ComplexProperty) property).removePhantomFlag();
                } else {
                    ListType listType = (ListType) type;
                    if (listType.getFieldType().isSimpleType()) {
                        // array
                        Object[] array = state.getArray(name);
                        array = typedArray(listType.getFieldType(), array);
                        property.init(array);
                    } else {
                        // complex list
                        Field listField = listType.getField();
                        List<T> childStates = getChildAsList(state, name);
                        // TODO property.init(null) if null children in DBS
                        List<Object> list = new ArrayList<>(childStates.size());
                        int i = 0;
                        for (T childState : childStates) {
                            String xpi = xp + '/' + i++;
                            var p = (ComplexProperty) complexProperty.getRoot().createProperty(property, listField, 0);
                            readComplexProperty(childState, p, xpi);
                            list.add(p.getValue());
                        }
                        property.init((Serializable) list);
                    }
                }
            } catch (ClassCastException e) {
                throw new PropertyConversionException(
                        String.format("Unable to read property: %s for document: %s", xp, getUUID()), e);
            }
        }
    }

    protected static class BlobWriteInfo<T extends StateAccessor> {

        public final T state;

        public final Blob blob;

        public final String xpath;

        public BlobWriteInfo(T state, Blob blob, String xpath) {
            this.state = state;
            this.blob = blob;
            this.xpath = xpath;
        }
    }

    protected static class BlobWriteContext<T extends StateAccessor> implements WriteContext {

        public final Map<BaseDocument<T>, List<BlobWriteInfo<T>>> blobWriteInfos = new HashMap<>();

        public final Set<String> xpaths = new HashSet<>();

        /**
         * Records a change to a given xpath.
         */
        public void recordChange(String xpath) {
            xpaths.add(xpath);
        }

        /**
         * Records a blob update.
         */
        public void recordBlob(BaseDocument<T> doc, T state, Blob blob, String xpath) {
            BlobWriteInfo<T> info = new BlobWriteInfo<>(state, blob, xpath);
            blobWriteInfos.computeIfAbsent(doc, k -> new ArrayList<>()).add(info);
        }

        @Override
        public Set<String> getChanges() {
            return xpaths;
        }

        // note, in the proxy case baseDoc may be different from the doc in the map
        @Override
        public void flush(Document baseDoc) {
            // first, write all updated blobs
            for (Entry<BaseDocument<T>, List<BlobWriteInfo<T>>> es : blobWriteInfos.entrySet()) {
                BaseDocument<T> doc = es.getKey();
                for (BlobWriteInfo<T> info : es.getValue()) {
                    doc.setValueBlob(info.state, info.blob, info.xpath);
                }
            }
            // then inform the blob manager about the changed xpaths
            DocumentBlobManager blobManager = Framework.getService(DocumentBlobManager.class);
            blobManager.notifyChanges(baseDoc, xpaths);
        }
    }

    @Override
    public WriteContext getWriteContext() {
        return new BlobWriteContext<T>();
    }

    /**
     * Writes state from a complex property.
     *
     * @deprecated since 11.1, use
     *             {@link #writeDocumentPart(StateAccessor, DocumentPart, org.nuxeo.ecm.core.model.Document.WriteContext, boolean)}
     *             instead
     */
    @Deprecated
    protected boolean writeComplexProperty(T state, ComplexProperty complexProperty, WriteContext writeContext)
            throws PropertyException {
        return writeDocumentPart(state, (DocumentPart) complexProperty, writeContext, false);
    }

    /**
     * Writes state from a document part.
     *
     * @return {@code true} if something changed
     */
    protected boolean writeDocumentPart(T state, DocumentPart dp, WriteContext writeContext, boolean create)
            throws PropertyException {
        boolean writeAll = create;
        boolean writeAllChildren = dp.getClearComplexPropertyBeforeSet();
        return writeComplexProperty(state, (ComplexProperty) dp, null, writeAll, writeAllChildren, writeContext);
    }

    /**
     * Writes state from a complex property.
     * <p>
     * Writes only properties that are dirty, unless writeAll is true in which case everything is written.
     *
     * @return {@code true} if something changed
     */
    protected boolean writeComplexProperty(T state, ComplexProperty complexProperty, String xpath, boolean writeAll,
            boolean writeAllChildren, WriteContext wc) throws PropertyException {
        @SuppressWarnings("unchecked")
        BlobWriteContext<T> writeContext = (BlobWriteContext<T>) wc;
        if (complexProperty instanceof BlobProperty) {
            Serializable value = complexProperty.getValueForWrite();
            if (value != null && !(value instanceof Blob)) {
                throw new PropertyException("Cannot write a non-Blob value: " + value);
            }
            writeContext.recordBlob(this, state, (Blob) value, xpath);
            return true;
        }
        boolean changed = false;
        for (Property property : complexProperty) {
            // write dirty properties, but also phantoms with non-null default values
            // this is critical for DeltaLong updates to work, they need a non-null initial value
            if (writeAll || property.isDirty() || property.isPhantom() && property.hasDefaultValue()) {
                // do the write
            } else {
                continue;
            }
            String name = property.getField().getName().getPrefixedName();
            name = internalName(name);
            if (checkReadOnlyIgnoredWrite(property, state)) {
                continue;
            }
            String xp = xpath == null ? name : xpath + '/' + name;
            writeContext.recordChange(xp);
            changed = true;

            Type type = property.getType();
            if (type.isSimpleType()) {
                // simple property
                Serializable value = property.getValueForWrite();
                state.setSingle(name, value);
            } else if (type.isComplexType()) {
                // complex property
                T childState = getChildForWrite(state, name, type);
                writeComplexProperty(childState, (ComplexProperty) property, xp, writeAllChildren, writeAllChildren,
                        writeContext);
            } else {
                ListType listType = (ListType) type;
                if (listType.getFieldType().isSimpleType()) {
                    // array
                    Serializable value = property.getValueForWrite();
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        Object[] array;
                        // use properly-typed array, useful for mem backend that doesn't re-convert all types
                        Class<?> klass = Object.class;
                        for (Object o : list) {
                            if (o != null) {
                                klass = o.getClass();
                                break;
                            }
                        }
                        array = (Object[]) Array.newInstance(klass, list.size());
                        value = list.toArray(array);
                    } else if (value instanceof Object[]) {
                        Object[] ar = (Object[]) value;
                        if (ar.length != 0) {
                            // use properly-typed array, useful for mem backend that doesn't re-convert all types
                            Class<?> klass = Object.class;
                            for (Object o : ar) {
                                if (o != null) {
                                    klass = o.getClass();
                                    break;
                                }
                            }
                            Object[] array;
                            if (ar.getClass().getComponentType() == klass) {
                                array = ar;
                            } else {
                                // copy to array with proper component type
                                array = (Object[]) Array.newInstance(klass, ar.length);
                                System.arraycopy(ar, 0, array, 0, ar.length);
                            }
                            value = array;
                        }
                    } else if (value == null) {
                        // ok
                    } else {
                        throw new IllegalStateException(value.toString());
                    }
                    state.setArray(name, (Object[]) value);
                } else {
                    // complex list
                    // update it
                    List<T> childStates = updateList(state, name, property);
                    // write values
                    int i = 0;
                    for (Property childProperty : property.getChildren()) {
                        T childState = childStates.get(i);
                        String xpi = xp + '/' + i;
                        boolean moved = childProperty.isMoved();
                        boolean c = writeComplexProperty(childState, (ComplexProperty) childProperty, xpi,
                                writeAllChildren || moved, writeAllChildren || moved, writeContext);
                        if (c) {
                            writeContext.recordChange(xpi);
                        }
                        i++;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Visits all the blobs of this document and calls the passed blob visitor on each one.
     */
    protected void visitBlobs(T state, Consumer<BlobAccessor> blobVisitor, Runnable markDirty)
            throws PropertyException {
        Visit visit = new Visit(blobVisitor, markDirty);
        // structural type
        visit.visitBlobsComplex(state, getType());
        // dynamic facets
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        for (String facet : getFacets()) {
            CompositeType facetType = schemaManager.getFacet(facet);
            if (facetType != null) { // if not obsolete facet
                visit.visitBlobsComplex(state, facetType);
            }
        }
        // proxy schemas
        if (getProxySchemas() != null) {
            for (Schema schema : getProxySchemas()) {
                visit.visitBlobsComplex(state, schema);
            }
        }
    }

    protected class StateBlobAccessor implements BlobAccessor {

        protected final Collection<String> path;

        protected final T state;

        protected final Runnable markDirty;

        public StateBlobAccessor(Collection<String> path, T state, Runnable markDirty) {
            this.path = path;
            this.state = state;
            this.markDirty = markDirty;
        }

        @Override
        public String getXPath() {
            return StringUtils.join(path, "/");
        }

        @Override
        public Blob getBlob() throws PropertyException {
            return getValueBlob(state, getXPath());
        }

        @Override
        public void setBlob(Blob blob) throws PropertyException {
            // markDirty has to be called *before* we change the state
            markDirty.run();
            setValueBlob(state, blob, getXPath());
        }
    }

    protected class Visit {

        protected final Consumer<BlobAccessor> blobVisitor;

        protected final Runnable markDirty;

        protected final Deque<String> path;

        public Visit(Consumer<BlobAccessor> blobVisitor, Runnable markDirty) {
            this.blobVisitor = blobVisitor;
            this.markDirty = markDirty;
            path = new ArrayDeque<>();
        }

        public void visitBlobsComplex(T state, ComplexType complexType) throws PropertyException {
            if (TypeConstants.isContentType(complexType)) {
                blobVisitor.accept(new StateBlobAccessor(path, state, markDirty));
                return;
            }
            for (Field field : complexType.getFields()) {
                visitBlobsField(state, field);
            }
        }

        protected void visitBlobsField(T state, Field field) throws PropertyException {
            Type type = field.getType();
            if (type.isSimpleType()) {
                // scalar
            } else if (type.isComplexType()) {
                // complex property
                String name = field.getName().getPrefixedName();
                T childState = getChild(state, name, type);
                if (childState != null) {
                    path.addLast(name);
                    visitBlobsComplex(childState, (ComplexType) type);
                    path.removeLast();
                }
            } else {
                // array or list
                Type fieldType = ((ListType) type).getFieldType();
                if (fieldType.isSimpleType()) {
                    // array
                } else {
                    // complex list
                    String name = field.getName().getPrefixedName();
                    path.addLast(name);
                    int i = 0;
                    for (T childState : getChildAsList(state, name)) {
                        path.addLast(String.valueOf(i++));
                        visitBlobsComplex(childState, (ComplexType) fieldType);
                        path.removeLast();
                    }
                    path.removeLast();
                }
            }
        }
    }

    @Override
    public Lock getLock() {
        try {
            return getSession().getLockManager().getLock(getUUID());
        } catch (DocumentNotFoundException e) {
            return getDocumentLock();
        }
    }

    @Override
    public Lock setLock(Lock lock) {
        if (lock == null) {
            throw new NullPointerException("Attempt to use null lock on: " + getUUID());
        }
        try {
            return getSession().getLockManager().setLock(getUUID(), lock);
        } catch (DocumentNotFoundException e) {
            return setDocumentLock(lock);
        }
    }

    @Override
    public Lock removeLock(String owner) {
        try {
            return getSession().getLockManager().removeLock(getUUID(), owner);
        } catch (DocumentNotFoundException e) {
            return removeDocumentLock(owner);
        }
    }

    /**
     * Gets the lock from this recently created and unsaved document.
     *
     * @return the lock, or {@code null} if no lock is set
     * @since 7.4
     */
    protected abstract Lock getDocumentLock();

    /**
     * Sets a lock on this recently created and unsaved document.
     *
     * @param lock the lock to set
     * @return {@code null} if locking succeeded, or the existing lock if locking failed
     * @since 7.4
     */
    protected abstract Lock setDocumentLock(Lock lock);

    /**
     * Removes a lock from this recently created and unsaved document.
     *
     * @param owner the owner to check, or {@code null} for no check
     * @return {@code null} if there was no lock or if removal succeeded, or a lock if it blocks removal due to owner
     *         mismatch
     * @since 7.4
     */
    protected abstract Lock removeDocumentLock(String owner);

    // also used as a regexp for split
    public static final String TOKEN_SEP = "-";

    /**
     * Builds the user-visible change token from low-level change token and system change token information.
     *
     * @param sysChangeToken the system change token
     * @param changeToken the change token
     * @return the user-visible change token
     * @since 9.2
     */
    public static String buildUserVisibleChangeToken(Long sysChangeToken, Long changeToken) {
        if (sysChangeToken == null || changeToken == null) {
            return null;
        }
        return sysChangeToken.toString() + TOKEN_SEP + changeToken.toString();
    }

    /**
     * Validates that the passed user-visible change token is compatible with the current change token.
     *
     * @param sysChangeToken the system change token
     * @param changeToken the change token
     * @param userVisibleChangeToken the user-visible change token
     * @return {@code false} if the change token is not valid
     * @since 9.2
     */
    public static boolean validateUserVisibleChangeToken(Long sysChangeToken, Long changeToken,
            String userVisibleChangeToken) {
        if (sysChangeToken == null || changeToken == null) {
            return true;
        }
        // we only compare the change token, not the system change token, to allow background system updates
        String[] parts = userVisibleChangeToken.split(TOKEN_SEP);
        if (parts.length != 2) {
            return false; // invalid format
        }
        return parts[1].equals(changeToken.toString());
    }

    /**
     * Validates that the passed user-visible change token is compatible with the current legacy change token.
     *
     * @param modified the {@code dc:modified} timestamp
     * @param userVisibleChangeToken the user-visible change token
     * @return {@code false} if the change token is not valid
     * @since 9.2
     */
    protected boolean validateLegacyChangeToken(Calendar modified, String userVisibleChangeToken) {
        if (modified == null) {
            return true;
        }
        return userVisibleChangeToken.equals(String.valueOf(modified.getTimeInMillis()));
    }

    /**
     * Gets the legacy change token for the given timestamp.
     *
     * @param modified the {@code dc:modified} timestamp
     * @return the legacy change token
     * @since 9.2
     */
    protected String getLegacyChangeToken(Calendar modified) {
        if (modified == null) {
            return null;
        }
        return String.valueOf(modified.getTimeInMillis());
    }

    /**
     * Updates a change token to its new value.
     *
     * @param changeToken the change token (not {@code null})
     * @return the updated change token
     * @since 9.2
     */
    public static Long updateChangeToken(Long changeToken) {
        return Long.valueOf(changeToken.longValue() + 1);
    }

    @Override
    public boolean isUnderRetentionOrLegalHold() {
        Calendar retainUntil;
        return hasLegalHold()
                || (((retainUntil = getRetainUntil()) != null) && Calendar.getInstance().before(retainUntil));
    }

    protected boolean allowNewRetention(Calendar current, Calendar retainUntil) {
        if (current == null) {
            return true;
        }
        if (current.compareTo(CoreSession.RETAIN_UNTIL_INDETERMINATE) == 0) {
            return true;
        }
        if (retainUntil == null) {
            // setting back to null is allowed if retention has already expired
            return Calendar.getInstance().after(current);
        }
        // can only extend retention
        return retainUntil.after(current);
    }

}
