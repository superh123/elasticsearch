/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

/**
 * Characterizes what happens when an {@code ip} field is given an object value
 * with {@code ignore_malformed} enabled. Sibling mappers guard their
 * ignore_malformed catch with {@code currentToken().isValue()} so structural
 * tokens (objects/arrays) are never treated as ignorable malformed values.
 * IpFieldMapper has no such guard, so on paper the exception thrown for an
 * object token is swallowed while the object itself is never consumed,
 * desynchronizing the document parser.
 */
public class IpFieldMapperObjectValueTests extends MapperServiceTestCase {

    private static XContentBuilder mapping(boolean ignoreMalformed) throws IOException {
        return mapping(b -> {
            b.startObject("field");
            b.field("type", "ip");
            b.field("ignore_malformed", ignoreMalformed);
            b.endObject();
            b.startObject("canary");
            b.field("type", "ip");
            b.endObject();
        });
    }

    public void testObjectValueIgnoredCleanlyWhenIgnoreMalformed() throws IOException {
        MapperService mapperService = createMapperService(mapping(true));
        ParsedDocument doc = mapperService.documentMapper().parse(source(b -> {
            b.startObject("field");
            b.field("a", 1);
            b.endObject();
            b.field("canary", "1.2.3.4");
        }));
        // the ip field itself is ignored
        assertThat(doc.rootDoc().getFields("field"), empty());
        List<String> ignored = doc.rootDoc()
            .getFields("_ignored")
            .stream()
            .map(f -> f.binaryValue().utf8ToString())
            .toList();
        assertThat(ignored, contains("field"));
        // the contents of the object must not leak out as spurious root fields
        assertThat(doc.rootDoc().getFields("a"), empty());
        assertNull(mapperService.mappingLookup().getMapper("a"));
        // and the field after it must still be indexed
        assertThat(doc.rootDoc().getFields("canary"), not(empty()));
    }

    public void testObjectValueHardFailsWhenIgnoreMalformedFalse() throws IOException {
        MapperService mapperService = createMapperService(mapping(false));
        DocumentParsingException e = expectThrows(
            DocumentParsingException.class,
            () -> mapperService.documentMapper().parse(source(b -> {
                b.startObject("field");
                b.field("a", 1);
                b.endObject();
                b.field("canary", "1.2.3.4");
            }))
        );
        assertThat(e.getMessage(), containsString("field"));
    }
}
