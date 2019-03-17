/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.reflect.Array;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("unused")
final class HostObject implements TruffleObject {

    static final int LIMIT = 5;

    static final HostObject NULL = new HostObject(null, null, false);

    final Object obj;
    final PolyglotLanguageContext languageContext;
    private final boolean staticClass;

    private HostObject(Object obj, PolyglotLanguageContext languageContext, boolean staticClass) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.staticClass = staticClass;
    }

    static HostObject forClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, false);
    }

    static HostObject forStaticClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, true);
    }

    static HostObject forObject(Object object, PolyglotLanguageContext languageContext) {
        assert object != null && !(object instanceof Class<?>);
        return new HostObject(object, languageContext, false);
    }

    static boolean isInstance(Object obj) {
        return obj instanceof HostObject;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof HostObject;
    }

    HostObject withContext(PolyglotLanguageContext context) {
        return new HostObject(this.obj, context, this.staticClass);
    }

    static boolean isJavaInstance(Class<?> targetType, Object javaObject) {
        if (javaObject instanceof HostObject) {
            final Object value = valueOf(javaObject);
            return targetType.isInstance(value);
        } else {
            return false;
        }
    }

    boolean isPrimitive() {
        return PolyglotImpl.isGuestPrimitive(obj);
    }

    static Object valueOf(Object value) {
        final HostObject obj = (HostObject) value;
        return obj.obj;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(obj);
    }

    boolean isClass() {
        return obj instanceof Class<?>;
    }

    boolean isArrayClass() {
        if (isClass() && asClass().isArray()) {
            return true;
        }
        return false;
    }
    boolean isList() {
        return obj instanceof List;
    }

    boolean isArray() {
        return obj != null && obj.getClass().isArray();
    }

    boolean isDefaultClass() {
        if (isClass() && !asClass().isArray()) {
            return true;
        }
        return false;
    }

    boolean isList() {
        return obj instanceof List;
    }

    @ExportMessage
    boolean hasMembers() {
        return !isNull();
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedReadable) {
            assert cachedReadable == doUncached(receiver, name);
            return cachedReadable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isReadable(receiver.getLookupClass(), name, receiver.isStaticClass(), receiver.isClass());
        }

    }

    @ExportMessage
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        if (isNull()) {
            throw UnsupportedMessageException.create();
        }
        Object obj = VMAccessor.NODES.getSourceVM(getRootNode());
        String[] fields = HostInteropReflect.findUniquePublicMemberNames((PolyglotEngineImpl) obj, receiver.getLookupClass(), receiver.isStaticClass(), receiver.isClass(), includeInternal);
        return HostObject.forObject(fields, receiver.languageContext);
    }

    @ExportMessage
    Object readMember(String name,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Cached LookupInnerClassNode lookupInnerClass) throws UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            throw UnsupportedMessageException.create();
        }
        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();
        HostFieldDesc foundField = lookupField.execute(lookupClass, name, isStatic);
        if (foundField != null) {
            return readField.execute(foundField, this);
        }
        HostMethodDesc foundMethod = lookupMethod.execute(lookupClass, name, isStatic);
        if (foundMethod != null) {
            return new HostFunction(foundMethod, this.obj, this.languageContext);
        }

        if (isStatic) {
            LookupInnerClassNode lookupInnerClassNode = lookupInnerClass;
            if (HostInteropReflect.STATIC_TO_CLASS.equals(name)) {
                return HostObject.forClass(lookupClass, languageContext);
            }
            Class<?> innerclass = lookupInnerClassNode.execute(lookupClass, name);
            if (innerclass != null) {
                return HostObject.forStaticClass(innerclass, languageContext);
            }
        } else if (isClass() && HostInteropReflect.CLASS_TO_STATIC.equals(name)) {
            return HostObject.forStaticClass(asClass(), languageContext);
        }
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage
    static class IsMemberModifiable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedModifiable) {
            assert cachedModifiable == doUncached(receiver, name);
            return cachedModifiable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isModifiable(receiver.getLookupClass(), name, receiver.isStaticClass());
        }

    }

    @ExportMessage
    static class IsMemberInternal {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedInternal) {
            assert cachedInternal == doUncached(receiver, name);
            return cachedInternal;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isInternal(receiver.getLookupClass(), name, receiver.isStaticClass());
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Cached WriteFieldNode writeField)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        if (isNull()) {
            throw UnsupportedMessageException.create();
        }
        HostFieldDesc f = lookupField.execute(getLookupClass(), member, isStaticClass());
        if (f == null) {
            throw UnknownIdentifierException.create(member);
        }
        try {
            writeField.execute(f, this, value);
        } catch (ClassCastException | NullPointerException e) {
            // conversion failed by ToJavaNode
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    @ExportMessage
    static class IsMemberInvocable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedInvokable) {
            assert cachedInvokable == doUncached(receiver, name);
            return cachedInvokable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isInvokable(receiver.getLookupClass(), name, receiver.isStaticClass());
        }
    }

    @ExportMessage
    Object invokeMember(String name, Object[] args,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @CachedLibrary(limit = "5") InteropLibrary fieldValues) throws UnsupportedTypeException, ArityException, UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            throw UnsupportedMessageException.create();
        }

        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();

        // (1) look for a method; if found, invoke it on obj.
        HostMethodDesc foundMethod = lookupMethod.execute(lookupClass, name, isStatic);
        if (foundMethod != null) {
            return executeMethod.execute(foundMethod, obj, args, languageContext);
        }

        // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
        HostFieldDesc foundField = lookupField.execute(lookupClass, name, isStatic);
        if (foundField != null) {
            Object fieldValue = readField.execute(foundField, this);
            if (fieldValues.isExecutable(fieldValue)) {
                return fieldValues.execute(fieldValue, args);
            }
        }
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    static class IsArrayElementExisting {

        @Specialization(guards = "receiver.isHostArray()")
        static boolean doArray(HostObject receiver, long index) {
            long size = Array.getLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = "receiver.isList()")
        static boolean doList(HostObject receiver, long index) {
            long size = receiver.getListSize();
            return index >= 0 && index < size;
        }

        @Specialization(guards = "!receiver.hasArrayElements()")
        static boolean doOther(HostObject receiver, long index) {
            return false;
        }
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index) {
        return isList() && getListSize() == index;
    }

    @ExportMessage
    static class WriteArrayElement {

        @Specialization(guards = {"receiver.isHostArray()"})
        @SuppressWarnings("unchecked")
        static void doArray(HostObject receiver, long index, Object value,
                        @Shared("toHost") @Cached ToHostNode toHostNode,
                        @Cached ArraySet arraySet) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index > Integer.MAX_VALUE) {
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object javaValue;
            try {
                javaValue = toHostNode.execute(value, obj.getClass().getComponentType(), null, receiver.languageContext);
            } catch (ClassCastException | NullPointerException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                arraySet.execute(obj, (int) index, javaValue);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"receiver.isList()"})
        @SuppressWarnings("unchecked")
        static void doList(HostObject receiver, long index, Object value,
                        @Shared("toHost") @Cached ToHostNode toHostNode) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index > Integer.MAX_VALUE) {
                throw InvalidArrayIndexException.create(index);
            }
            Object javaValue;
            try {
                javaValue = toHostNode.execute(value, Object.class, null, receiver.languageContext);
            } catch (ClassCastException | NullPointerException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                List<Object> list = ((List<Object>) receiver.obj);
                setList(list, index, javaValue);
            } catch (IndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @TruffleBoundary
        private static void setList(List<Object> list, long index, final Object hostValue) {
            if (index == list.size()) {
                list.add(hostValue);
            } else {
                list.set((int) index, hostValue);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.hasArrayElements()"})
        static void doNotArrayOrList(HostObject receiver, long index, Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    static class IsArrayElementRemovable {
        @Specialization(guards = "receiver.isList()")
        static boolean doList(HostObject receiver, long index) {
            return index >= 0 && index < callSize(receiver);
        }

        @TruffleBoundary
        private static int callSize(HostObject receiver) {
            return ((List<?>) receiver.obj).size();
        }

        @Specialization(guards = "!receiver.isList()")
        static boolean doOther(HostObject receiver, long index) {
            return false;
        }
        @Specialization(guards = {"checkArray(receiver)"})
        protected Object doArrayIntIndex(HostObject receiver, int index) {
            return doArrayAccess(receiver, index);
        }

        @Specialization(guards = {"checkArray(receiver)", "index.getClass() == clazz"}, replaces = "doArrayIntIndex")
        protected Object doArrayCached(HostObject receiver, Number index,
                        @Cached("index.getClass()") Class<? extends Number> clazz) {
            return doArrayAccess(receiver, clazz.cast(index).intValue());
        }

        @Specialization(guards = {"checkArray(receiver)"}, replaces = "doArrayCached")
        protected Object doArrayGeneric(HostObject receiver, Number index) {
            return doArrayAccess(receiver, index.intValue());
        }
    }

    @ExportMessage
    static class RemoveArrayElement {
        @Specialization(guards = "isList(receiver)")
        static void doList(HostObject receiver, long index) throws InvalidArrayIndexException {
            if (index > Integer.MAX_VALUE) {
                throw InvalidArrayIndexException.create(index);
            }
            try {
                boundaryRemove(receiver, index);
            } catch (IndexOutOfBoundsException outOfBounds) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        private static Object boundaryRemove(HostObject receiver, long index) throws IndexOutOfBoundsException {
            return ((List<Object>) receiver.obj).remove((int) index);
        }

        @Specialization(guards = "!receiver.isList()")
        static void doOther(HostObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
        protected Object doListGeneric(HostObject receiver, Number index) {
            return doListIntIndex(receiver, index.intValue());
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"!checkArray(receiver)", "!isList(receiver)"})
        protected static Object notArray(HostObject receiver, Number index) {
            throw UnsupportedMessageException.raise(Message.READ);
        }

        @CompilationFinal Boolean publicAccessEnabled;

        final boolean isPublicAccess(HostObject receiver) {
            Boolean isPublicAccess = this.publicAccessEnabled;
            if (isPublicAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.publicAccessEnabled = isPublicAccess = HostClassCache.forInstance(receiver).isPublicAccess();
            }
            assert isPublicAccess == HostClassCache.forInstance(receiver).isPublicAccess();
            return isPublicAccess;
        }

        final boolean isList(HostObject receiver) {
            return isPublicAccess(receiver) ? receiver.isList() : false;
        }

        final boolean checkArray(HostObject receiver) {
            return isPublicAccess(receiver) ? receiver.isArray() : false;
        }

        private Object doArrayAccess(HostObject object, int index) {
            Object obj = object.obj;
            assert object.isArray();
            Object val = null;
            try {
                val = arrayGet.execute(obj, index);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }

            return toGuest.apply(object.languageContext, val);
        }
    }

    @ExportMessage
    boolean hasArrayElements() {
        return isHostArray() || obj instanceof List<?>;
    }

    @ExportMessage
    abstract static class ReadArrayElement {

        @Specialization(guards = {"receiver.isHostArray()"})
        protected static Object doArray(HostObject receiver, long index,
                        @Cached ArrayGet arrayGet,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest) throws InvalidArrayIndexException {
            if (index > Integer.MAX_VALUE) {
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            assert receiver.isHostArray();
            Object val = null;
            try {
                val = arrayGet.execute(obj, (int) index);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            return toGuest.execute(receiver.languageContext, val);
        }

        @TruffleBoundary
        @Specialization(guards = {"receiver.isList()"})
        protected static Object doList(HostObject receiver, long index,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest) throws InvalidArrayIndexException {
            try {
                if (index > Integer.MAX_VALUE) {
                    throw InvalidArrayIndexException.create(index);
                }
                return toGuest.execute(receiver.languageContext, ((List<?>) receiver.obj).get((int) index));
            } catch (IndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.hasArrayElements()", "!receiver.isList()"})
        protected static Object doNotArrayOrList(HostObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    long getArraySize() throws UnsupportedMessageException {
        if (isHostArray()) {
            return Array.getLength(obj);
        } else if (isList()) {
            return getListSize();
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary(allowInlining = true)
    int getListSize() {
        return ((List<?>) obj).size();
    }

    boolean isHostArray() {
        return obj != null && obj.getClass().isArray();
    }

    @ExportMessage
    boolean isNull() {
        return obj == null;
    }

    @ExportMessage
    static class IsInstantiable {

        @Specialization(guards = "!receiver.isClass()")
        @SuppressWarnings("unused")
        static boolean doUnsupported(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.isArrayClass()")
        static boolean doArrayCached(@SuppressWarnings("unused") HostObject receiver) {
            return true;
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static boolean doObjectCached(HostObject receiver,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor) {
            return lookupConstructor.execute(receiver.asClass()) != null;
        }
    }

    @ExportMessage
    boolean isExecutable(@Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod) {
        return !isNull() && !isClass() && lookupMethod.execute(getLookupClass()) != null;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Shared("hostExecute") @Cached HostExecuteNode doExecute,
                    @Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        if (!isNull() && !isClass()) {
            HostMethodDesc method = lookupMethod.execute(getLookupClass());
            if (method != null) {
                return doExecute.execute(method, obj, args, languageContext);
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static class Instantiate {

        @Specialization(guards = "!receiver.isClass()")
        @SuppressWarnings("unused")
        static Object doUnsupported(HostObject receiver, Object[] args) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "receiver.isArrayClass()")
        static Object doArrayCached(HostObject receiver, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary indexes) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            if (args.length != 1) {
                throw ArityException.create(1, args.length);
            }
            Object arg0 = args[0];
            int length;
            if (indexes.fitsInInt(arg0)) {
                length = indexes.asInt(arg0);
            } else {
                throw UnsupportedTypeException.create(args);
            }
            Object array = Array.newInstance(receiver.asClass().getComponentType(), length);
            return HostObject.forObject(array, receiver.languageContext);
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static Object doObjectCached(HostObject receiver, Object[] arguments,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor,
                        @Shared("hostExecute") @Cached HostExecuteNode executeMethod) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            assert !receiver.isArrayClass();
            if (receiver.isClass()) {
                HostMethodDesc constructor = lookupConstructor.execute(receiver.asClass());
                if (constructor != null) {
                    return executeMethod.execute(constructor, null, arguments, receiver.languageContext);
                }
            }
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    protected boolean isNumber() {
        if (isNull()) {
            return false;
        }
        Class<?> c = obj.getClass();
        return c == Byte.class || c == Short.class || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    @ExportMessage
    boolean fitsInByte(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInByte(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInShort(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInShort(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInInt(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInInt(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInLong(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInLong(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInFloat(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInFloat(obj);
        } else {
            return false;
        }
    }

<<<<<<< HEAD
    @ExportMessage
    boolean fitsInDouble(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInDouble(obj);
        } else {
            return false;
        }
    }

     final boolean isPublicAccess(HostObject receiver) {
            Boolean isPublicAccess = this.publicAccessEnabled;
            if (isPublicAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.publicAccessEnabled = isPublicAccess = HostClassCache.forInstance(receiver).isPublicAccess();
            }
            assert isPublicAccess == HostClassCache.forInstance(receiver).isPublicAccess();
            return isPublicAccess;
        }

    @ExportMessage
    byte asByte(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asByte(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    short asShort(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asShort(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    int asInt(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asInt(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long asLong(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asLong(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    float asFloat(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asFloat(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    double asDouble(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asDouble(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isString() {
        if (isNull()) {
            return false;
        }
        Class<?> c = obj.getClass();
        return c == String.class || c == Character.class;
    }

    @ExportMessage
    String asString(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary strings) throws UnsupportedMessageException {
        if (isString()) {
            return strings.asString(obj);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isBoolean() {
        if (isNull()) {
            return false;
        }
        return obj.getClass() == Boolean.class;
    }

    @ExportMessage
    boolean asBoolean() throws UnsupportedMessageException {
        if (isBoolean()) {
            return (boolean) obj;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    boolean isStaticClass() {
        return staticClass;
    }

    Class<?> getObjectClass() {
        return obj == null ? null : obj.getClass();
    }

    Class<?> asStaticClass() {
        assert isStaticClass();
        return (Class<?>) obj;
    }

    Class<?> asClass() {
        assert isClass();
        return (Class<?>) obj;
    }

    /**
     * Gets the {@link Class} for member lookups.
     */
    Class<?> getLookupClass() {
        if (obj == null) {
            return null;
        } else if (isStaticClass()) {
            return asStaticClass();
        } else {
            return obj.getClass();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HostObject) {
            HostObject other = (HostObject) o;
            return this.obj == other.obj && this.languageContext == other.languageContext;
        }
        return false;
    }

    @Override
    public String toString() {
        if (obj == null) {
            return "null";
        }
        if (isClass()) {
            return "JavaClass[" + asClass().getTypeName() + "]";
        }
        return "JavaObject[" + obj + " (" + getObjectClass().getTypeName() + ")" + "]";
    }

    @GenerateUncached
    abstract static class ArraySet extends Node {

        protected abstract void execute(Object array, int index, Object value);

        @Specialization
        static void doBoolean(boolean[] array, int index, boolean value) {
            array[index] = value;
        }

        @Specialization
        static void doByte(byte[] array, int index, byte value) {
            array[index] = value;
        }

        @Specialization
        static void doShort(short[] array, int index, short value) {
            array[index] = value;
        }

        @Specialization
        static void doChar(char[] array, int index, char value) {
            array[index] = value;
        }

        @Specialization
        static void doInt(int[] array, int index, int value) {
            array[index] = value;
        }

        @Specialization
        static void doLong(long[] array, int index, long value) {
            array[index] = value;
        }

        @Specialization
        static void doFloat(float[] array, int index, float value) {
            array[index] = value;
        }

        @Specialization
        static void doDouble(double[] array, int index, double value) {
            array[index] = value;
        }

        @Specialization
        static void doObject(Object[] array, int index, Object value) {
            array[index] = value;
        }
    }

    @GenerateUncached
    abstract static class ArrayGet extends Node {

        protected abstract Object execute(Object array, int index);

<       @Specialization
        static boolean doBoolean(boolean[] array, int index) {
            return array[index];
        }

        @Specialization
        static byte doByte(byte[] array, int index) {
            return array[index];
=        }

        @Specialization
        static short doShort(short[] array, int index) {
            return array[index];
        }

        @Specialization
        static char doChar(char[] array, int index) {
            return array[index];
        }

        @Specialization
        static int doInt(int[] array, int index) {
            return array[index];
        }

        @Specialization
        static long doLong(long[] array, int index) {
            return array[index];
        }

        @Specialization
        static float doFloat(float[] array, int index) {
            return array[index];
        }

        @Specialization
        static double doDouble(double[] array, int index) {
            return array[index];
        }

        @Specialization
        static Object doObject(Object[] array, int index) {
            return array[index];
        }
    }

    @GenerateUncached
    abstract static class LookupConstructorNode extends Node {
        static final int LIMIT = 3;

        LookupConstructorNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        HostMethodDesc doCached(Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        HostMethodDesc doUncached(Class<?> clazz) {
            Object obj = VMAccessor.NODES.getSourceVM(getRootNode());
            return HostClassDesc.forClass((PolyglotEngineImpl) obj, clazz).lookupConstructor();
        }
    }

    @GenerateUncached
    abstract static class LookupFieldNode extends Node {
        static final int LIMIT = 3;

        LookupFieldNode() {
        }

        public abstract HostFieldDesc execute(Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        HostFieldDesc doCached(Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name, onlyStatic)") HostFieldDesc cachedField) {
            assert cachedField == doUncached(clazz, name, onlyStatic);
            return cachedField;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        HostFieldDesc doUncached(Class<?> clazz, String name, boolean onlyStatic) {
            Object obj = VMAccessor.NODES.getSourceVM(getRootNode());
            return HostInteropReflect.findField((PolyglotEngineImpl) obj, clazz, name, onlyStatic);
        }
    }

    @GenerateUncached
    abstract static class LookupFunctionalMethodNode extends Node {
        static final int LIMIT = 3;

        LookupFunctionalMethodNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        HostMethodDesc doCached(Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static HostMethodDesc doUncached(Class<?> clazz) {
            Object obj = VMAccessor.NODES.getSourceVM(getRootNode());
            return HostClassDesc.forClass((PolyglotEngineImpl) obj, clazz).getFunctionalMethod();
        }
    }

    @GenerateUncached
    abstract static class LookupInnerClassNode extends Node {
        static final int LIMIT = 3;

        LookupInnerClassNode() {
        }

        public abstract Class<?> execute(Class<?> outerclass, String name);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        Class<?> doCached(Class<?> clazz, String name,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name)") Class<?> cachedInnerClass) {
            assert cachedInnerClass == doUncached(clazz, name);
            return cachedInnerClass;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        Class<?> doUncached(Class<?> clazz, String name) {
            return HostInteropReflect.findInnerClass(clazz, name);
        }
    }

    @GenerateUncached
    abstract static class LookupMethodNode extends Node {
        static final int LIMIT = 3;

        LookupMethodNode() {
        }

        public abstract HostMethodDesc execute(Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        HostMethodDesc doCached(Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name, onlyStatic)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(clazz, name, onlyStatic);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
         HostMethodDesc doUncached(Class<?> clazz, String name, boolean onlyStatic) {
            Object obj = VMAccessor.NODES.getSourceVM(getRootNode());
            return HostInteropReflect.findMethod((PolyglotEngineImpl) obj, clazz, name, onlyStatic);
        }
    }

    @GenerateUncached
    abstract static class ReadFieldNode extends Node {
        static final int LIMIT = 3;

        ReadFieldNode() {
        }

        public abstract Object execute(HostFieldDesc field, HostObject object);

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static Object doCached(HostFieldDesc field, HostObject object,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached ToGuestValueNode toGuest) {
            Object val = cachedField.get(object.obj);
            return toGuest.execute(object.languageContext, val);
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(HostFieldDesc field, HostObject object,
                        @Cached ToGuestValueNode toGuest) {
            Object val = field.get(object.obj);
            return toGuest.execute(object.languageContext, val);
        }
    }

    @GenerateUncached
    abstract static class WriteFieldNode extends Node {
        static final int LIMIT = 3;

        WriteFieldNode() {
        }

        public abstract void execute(HostFieldDesc field, HostObject object, Object value) throws UnsupportedTypeException, UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static void doCached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached ToHostNode toHost) throws UnsupportedTypeException, UnknownIdentifierException {
            Object value;
            try {
                value = toHost.execute(rawValue, cachedField.getType(), cachedField.getGenericType(), object.languageContext);
            } catch (ClassCastException | NullPointerException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.create(new Object[]{rawValue}, e.getMessage());
            }
            cachedField.set(object.obj, value);
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static void doUncached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached ToHostNode toHost) throws UnsupportedTypeException, UnknownIdentifierException {
            Object val = toHost.execute(rawValue, field.getType(), field.getGenericType(), object.languageContext);
            field.set(object.obj, val);
        }
    }

    abstract static class BaseNode extends Node {

        @CompilationFinal Boolean publicAccessEnabled;

        final boolean isPublicAccess(HostObject receiver) {
            Boolean isPublicAccess = this.publicAccessEnabled;
            if (isPublicAccess == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.publicAccessEnabled = isPublicAccess = HostClassCache.forInstance(receiver).isPublicAccess();
            }
            return isPublicAccess;
        }

        final boolean isList(HostObject receiver) {
            return isPublicAccess(receiver) ? receiver.isList() : false;
        }

        final boolean checkArray(HostObject receiver) {
            return isPublicAccess(receiver) ? receiver.isArray() : false;
        }
    }

}
