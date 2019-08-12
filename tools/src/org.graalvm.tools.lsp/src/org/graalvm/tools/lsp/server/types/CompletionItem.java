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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A completion item represents a text snippet that is
 * proposed to complete text that is being typed.
 */
public class CompletionItem {

    final JSONObject jsonData;

    CompletionItem(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The label of this completion item. By default
     * also the text that is inserted when selecting
     * this completion.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public CompletionItem setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * The kind of this completion item. Based of the kind
     * an icon is chosen by the editor.
     */
    public CompletionItemKind getKind() {
        return CompletionItemKind.get(jsonData.optInt("kind"));
    }

    public CompletionItem setKind(CompletionItemKind kind) {
        jsonData.putOpt("kind", kind != null ? kind.getIntValue() : null);
        return this;
    }

    /**
     * A human-readable string with additional information
     * about this item, like type or symbol information.
     */
    public String getDetail() {
        return jsonData.optString("detail");
    }

    public CompletionItem setDetail(String detail) {
        jsonData.putOpt("detail", detail);
        return this;
    }

    /**
     * A human-readable string that represents a doc-comment.
     */
    public Object getDocumentation() {
        Object obj = jsonData.opt("documentation");
        if (obj instanceof JSONObject) {
            return new MarkupContent((JSONObject) obj);
        }
        return obj;
    }

    public CompletionItem setDocumentation(Object documentation) {
        if (documentation instanceof MarkupContent) {
            jsonData.put("documentation", ((MarkupContent) documentation).jsonData);
        } else {
            jsonData.putOpt("documentation", documentation);
        }
        return this;
    }

    /**
     * Indicates if this item is deprecated.
     */
    public Boolean getDeprecated() {
        return jsonData.optBoolean("deprecated");
    }

    public CompletionItem setDeprecated(Boolean deprecated) {
        jsonData.putOpt("deprecated", deprecated);
        return this;
    }

    /**
     * Select this item when showing.
     *
     * *Note* that only one completion item can be selected and that the
     * tool / client decides which item that is. The rule is that the *first*
     * item of those that match best is selected.
     */
    public Boolean getPreselect() {
        return jsonData.optBoolean("preselect");
    }

    public CompletionItem setPreselect(Boolean preselect) {
        jsonData.putOpt("preselect", preselect);
        return this;
    }

    /**
     * A string that should be used when comparing this item
     * with other items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    public String getSortText() {
        return jsonData.optString("sortText");
    }

    public CompletionItem setSortText(String sortText) {
        jsonData.putOpt("sortText", sortText);
        return this;
    }

    /**
     * A string that should be used when filtering a set of
     * completion items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    public String getFilterText() {
        return jsonData.optString("filterText");
    }

    public CompletionItem setFilterText(String filterText) {
        jsonData.putOpt("filterText", filterText);
        return this;
    }

    /**
     * A string that should be inserted into a document when selecting
     * this completion. When `falsy` the [label](#CompletionItem.label)
     * is used.
     *
     * The `insertText` is subject to interpretation by the client side.
     * Some tools might not take the string literally. For example
     * VS Code when code complete is requested in this example `con<cursor position>`
     * and a completion item with an `insertText` of `console` is provided it
     * will only insert `sole`. Therefore it is recommended to use `textEdit` instead
     * since it avoids additional client side interpretation.
     *
     * @deprecated Use textEdit instead.
     */
    public String getInsertText() {
        return jsonData.optString("insertText");
    }

    public CompletionItem setInsertText(String insertText) {
        jsonData.putOpt("insertText", insertText);
        return this;
    }

    /**
     * The format of the insert text. The format applies to both the `insertText` property
     * and the `newText` property of a provided `textEdit`.
     */
    public InsertTextFormat getInsertTextFormat() {
        return InsertTextFormat.get(jsonData.optInt("insertTextFormat"));
    }

    public CompletionItem setInsertTextFormat(InsertTextFormat insertTextFormat) {
        jsonData.putOpt("insertTextFormat", insertTextFormat != null ? insertTextFormat.getIntValue() : null);
        return this;
    }

    /**
     * An [edit](#TextEdit) which is applied to a document when selecting
     * this completion. When an edit is provided the value of
     * [insertText](#CompletionItem.insertText) is ignored.
     *
     * *Note:* The text edit's range must be a [single line] and it must contain the position
     * at which completion has been requested.
     */
    public TextEdit getTextEdit() {
        return jsonData.has("textEdit") ? new TextEdit(jsonData.optJSONObject("textEdit")) : null;
    }

    public CompletionItem setTextEdit(TextEdit textEdit) {
        jsonData.putOpt("textEdit", textEdit != null ? textEdit.jsonData : null);
        return this;
    }

    /**
     * An optional array of additional [text edits](#TextEdit) that are applied when
     * selecting this completion. Edits must not overlap (including the same insert position)
     * with the main [edit](#CompletionItem.textEdit) nor with themselves.
     *
     * Additional text edits should be used to change text unrelated to the current cursor position
     * (for example adding an import statement at the top of the file if the completion item will
     * insert an unqualified type).
     */
    public List<TextEdit> getAdditionalTextEdits() {
        final JSONArray json = jsonData.optJSONArray("additionalTextEdits");
        if (json == null) {
            return null;
        }
        final List<TextEdit> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new TextEdit(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public CompletionItem setAdditionalTextEdits(List<TextEdit> additionalTextEdits) {
        if (additionalTextEdits != null) {
            final JSONArray json = new JSONArray();
            for (TextEdit textEdit: additionalTextEdits) {
                json.put(textEdit.jsonData);
            }
            jsonData.put("additionalTextEdits", json);
        }
        return this;
    }

    /**
     * An optional set of characters that when pressed while this completion is active will accept it first and
     * then type that character. *Note* that all commit characters should have `length=1` and that superfluous
     * characters will be ignored.
     */
    public List<String> getCommitCharacters() {
        final JSONArray json = jsonData.optJSONArray("commitCharacters");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public CompletionItem setCommitCharacters(List<String> commitCharacters) {
        if (commitCharacters != null) {
            final JSONArray json = new JSONArray();
            for (String string: commitCharacters) {
                json.put(string);
            }
            jsonData.put("commitCharacters", json);
        }
        return this;
    }

    /**
     * An optional [command](#Command) that is executed *after* inserting this completion. *Note* that
     * additional modifications to the current document should be described with the
     * [additionalTextEdits](#CompletionItem.additionalTextEdits)-property.
     */
    public Command getCommand() {
        return jsonData.has("command") ? new Command(jsonData.optJSONObject("command")) : null;
    }

    public CompletionItem setCommand(Command command) {
        jsonData.putOpt("command", command != null ? command.jsonData : null);
        return this;
    }

    /**
     * An data entry field that is preserved on a completion item between
     * a [CompletionRequest](#CompletionRequest) and a [CompletionResolveRequest]
     * (#CompletionResolveRequest)
     */
    public Object getData() {
        return jsonData.opt("data");
    }

    public CompletionItem setData(Object data) {
        jsonData.putOpt("data", data);
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
        CompletionItem other = (CompletionItem) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (this.getKind() != other.getKind()) {
            return false;
        }
        if (!Objects.equals(this.getDetail(), other.getDetail())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentation(), other.getDocumentation())) {
            return false;
        }
        if (this.getDeprecated() != other.getDeprecated()) {
            return false;
        }
        if (this.getPreselect() != other.getPreselect()) {
            return false;
        }
        if (!Objects.equals(this.getSortText(), other.getSortText())) {
            return false;
        }
        if (!Objects.equals(this.getFilterText(), other.getFilterText())) {
            return false;
        }
        if (!Objects.equals(this.getInsertText(), other.getInsertText())) {
            return false;
        }
        if (this.getInsertTextFormat() != other.getInsertTextFormat()) {
            return false;
        }
        if (!Objects.equals(this.getTextEdit(), other.getTextEdit())) {
            return false;
        }
        if (!Objects.equals(this.getAdditionalTextEdits(), other.getAdditionalTextEdits())) {
            return false;
        }
        if (!Objects.equals(this.getCommitCharacters(), other.getCommitCharacters())) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getData(), other.getData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.getLabel());
        if (this.getKind() != null) {
            hash = 53 * hash + Objects.hashCode(this.getKind());
        }
        if (this.getDetail() != null) {
            hash = 53 * hash + Objects.hashCode(this.getDetail());
        }
        if (this.getDocumentation() != null) {
            hash = 53 * hash + Objects.hashCode(this.getDocumentation());
        }
        if (this.getDeprecated() != null) {
            hash = 53 * hash + Boolean.hashCode(this.getDeprecated());
        }
        if (this.getPreselect() != null) {
            hash = 53 * hash + Boolean.hashCode(this.getPreselect());
        }
        if (this.getSortText() != null) {
            hash = 53 * hash + Objects.hashCode(this.getSortText());
        }
        if (this.getFilterText() != null) {
            hash = 53 * hash + Objects.hashCode(this.getFilterText());
        }
        if (this.getInsertText() != null) {
            hash = 53 * hash + Objects.hashCode(this.getInsertText());
        }
        if (this.getInsertTextFormat() != null) {
            hash = 53 * hash + Objects.hashCode(this.getInsertTextFormat());
        }
        if (this.getTextEdit() != null) {
            hash = 53 * hash + Objects.hashCode(this.getTextEdit());
        }
        if (this.getAdditionalTextEdits() != null) {
            hash = 53 * hash + Objects.hashCode(this.getAdditionalTextEdits());
        }
        if (this.getCommitCharacters() != null) {
            hash = 53 * hash + Objects.hashCode(this.getCommitCharacters());
        }
        if (this.getCommand() != null) {
            hash = 53 * hash + Objects.hashCode(this.getCommand());
        }
        if (this.getData() != null) {
            hash = 53 * hash + Objects.hashCode(this.getData());
        }
        return hash;
    }

    /**
     * Create a completion item and seed it with a label.
     * @param label The completion item's label
     */
    public static CompletionItem create(String label) {
        final JSONObject json = new JSONObject();
        json.put("label", label);
        return new CompletionItem(json);
    }
}
