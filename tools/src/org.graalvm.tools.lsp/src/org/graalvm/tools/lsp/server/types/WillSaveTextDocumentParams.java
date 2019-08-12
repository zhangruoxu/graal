/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * The parameters send in a will save text document notification.
 */
public class WillSaveTextDocumentParams {

    final JSONObject jsonData;

    WillSaveTextDocumentParams(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The document that will be saved.
     */
    public TextDocumentIdentifier getTextDocument() {
        return new TextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public WillSaveTextDocumentParams setTextDocument(TextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * The 'TextDocumentSaveReason'.
     */
    public TextDocumentSaveReason getReason() {
        return TextDocumentSaveReason.get(jsonData.getInt("reason"));
    }

    public WillSaveTextDocumentParams setReason(TextDocumentSaveReason reason) {
        jsonData.put("reason", reason.getIntValue());
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        WillSaveTextDocumentParams other = (WillSaveTextDocumentParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (this.getReason() != other.getReason()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 97 * hash + Objects.hashCode(this.getTextDocument());
        hash = 97 * hash + Objects.hashCode(this.getReason());
        return hash;
    }

    public static WillSaveTextDocumentParams create(TextDocumentIdentifier textDocument, TextDocumentSaveReason reason) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        json.put("reason", reason.getIntValue());
        return new WillSaveTextDocumentParams(json);
    }
}
