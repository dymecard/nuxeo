/*
 * (C) Copyright 2015-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */
package org.nuxeo.ecm.core.io.registry;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Exception thrown by the {@link MarshallerRegistry} and all {@link Marshaller}s.
 *
 * @since 7.2
 */
public class MarshallingException extends NuxeoException {

    private static final long serialVersionUID = 1L;

    public MarshallingException() {
        super();
    }

    public MarshallingException(String message) {
        super(message);
    }

    /**
     * @since 2021.14
     */
    public MarshallingException(String message, int statusCode) {
        super(message, statusCode);
    }

    public MarshallingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @since 2021.14
     */
    public MarshallingException(String message, Throwable cause, int statusCode) {
        super(message, cause, statusCode);
    }

    public MarshallingException(Throwable cause) {
        super(cause);
    }

    /**
     * @since 2021.14
     */
    public MarshallingException(Throwable cause, int statusCode) {
        super(cause, statusCode);
    }

}
