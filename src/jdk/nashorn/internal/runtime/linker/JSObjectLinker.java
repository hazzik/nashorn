/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardedTypeConversion;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.lookup.MethodHandleFunctionality;
import jdk.nashorn.internal.runtime.JSType;

/**
 * A Dynalink linker to handle web browser built-in JS (DOM etc.) objects as well
 * as ScriptObjects from other Nashorn contexts.
 */
final class JSObjectLinker implements TypeBasedGuardingDynamicLinker, GuardingTypeConverterFactory {
    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    static boolean canLinkTypeStatic(final Class<?> type) {
        // can link JSObject
        return JSObject.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final LinkRequest requestWithoutContext = request.withoutRuntimeContext(); // Nashorn has no runtime context
        final Object self = requestWithoutContext.getReceiver();
        final CallSiteDescriptor desc = requestWithoutContext.getCallSiteDescriptor();

        if (desc.getNameTokenCount() < 2 || !"dyn".equals(desc.getNameToken(CallSiteDescriptor.SCHEME))) {
            // We only support standard "dyn:*[:*]" operations
            return null;
        }

        final GuardedInvocation inv;
        if (self instanceof JSObject) {
            inv = lookup(desc);
        } else {
            throw new AssertionError(); // Should never reach here.
        }

        return Bootstrap.asTypeSafeReturn(inv, linkerServices, desc);
    }

    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final boolean sourceIsAlwaysJSObject = JSObject.class.isAssignableFrom(sourceType);
        if(!sourceIsAlwaysJSObject && !sourceType.isAssignableFrom(JSObject.class)) {
            return null;
        }

        final MethodHandle converter = CONVERTERS.get(targetType);
        if(converter == null) {
            return null;
        }

        return new GuardedTypeConversion(new GuardedInvocation(converter, sourceIsAlwaysJSObject ? null : IS_JSOBJECT_GUARD).asType(MethodType.methodType(targetType, sourceType)), true);
    }


    private static GuardedInvocation lookup(final CallSiteDescriptor desc) {
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
        final int c = desc.getNameTokenCount();

        switch (operator) {
            case "getProp":
            case "getElem":
            case "getMethod":
                return c > 2 ? findGetMethod(desc) : findGetIndexMethod();
            case "setProp":
            case "setElem":
                return c > 2 ? findSetMethod(desc) : findSetIndexMethod();
            case "call":
                return findCallMethod(desc);
            case "new":
                return findNewMethod(desc);
            default:
                return null;
        }
    }

    private static GuardedInvocation findGetMethod(final CallSiteDescriptor desc) {
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        final MethodHandle getter = MH.insertArguments(JSOBJECT_GETMEMBER, 1, name);
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findGetIndexMethod() {
        return new GuardedInvocation(JSOBJECTLINKER_GET, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetMethod(final CallSiteDescriptor desc) {
        final MethodHandle getter = MH.insertArguments(JSOBJECT_SETMEMBER, 1, desc.getNameToken(2));
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetIndexMethod() {
        return new GuardedInvocation(JSOBJECTLINKER_PUT, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findCallMethod(final CallSiteDescriptor desc) {
        // TODO: if call site is already a vararg, don't do asCollector
        MethodHandle mh = JSOBJECT_CALL;
        if (NashornCallSiteDescriptor.isApplyToCall(desc)) {
            mh = MH.insertArguments(JSOBJECT_CALL_TO_APPLY, 0, JSOBJECT_CALL);
        }
        return new GuardedInvocation(MH.asCollector(mh, Object[].class, desc.getMethodType().parameterCount() - 2), IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        final MethodHandle func = MH.asCollector(JSOBJECT_NEW, Object[].class, desc.getMethodType().parameterCount() - 1);
        return new GuardedInvocation(func, IS_JSOBJECT_GUARD);
    }

    @SuppressWarnings("unused")
    private static boolean isJSObject(final Object self) {
        return self instanceof JSObject;
    }

    @SuppressWarnings("unused")
    private static Object get(final Object jsobj, final Object key) {
        if (key instanceof Integer) {
            return ((JSObject)jsobj).getSlot((Integer)key);
        } else if (key instanceof Number) {
            final int index = getIndex((Number)key);
            if (index > -1) {
                return ((JSObject)jsobj).getSlot(index);
            }
        } else if (key instanceof String) {
            return ((JSObject)jsobj).getMember((String)key);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static void put(final Object jsobj, final Object key, final Object value) {
        if (key instanceof Integer) {
            ((JSObject)jsobj).setSlot((Integer)key, value);
        } else if (key instanceof Number) {
            ((JSObject)jsobj).setSlot(getIndex((Number)key), value);
        } else if (key instanceof String) {
            ((JSObject)jsobj).setMember((String)key, value);
        }
    }

    @SuppressWarnings("unused")
    private static int toInt32(final JSObject obj) {
        return JSType.toInt32(toNumber(obj));
    }

    @SuppressWarnings("unused")
    private static long toLong(final JSObject obj) {
        return JSType.toLong(toNumber(obj));
    }

    private static double toNumber(final JSObject obj) {
        return obj == null ? 0 : obj.toNumber();
    }

    @SuppressWarnings("unused")
    private static boolean toBoolean(final JSObject obj) {
        return obj != null;
    }

    private static int getIndex(final Number n) {
        final double value = n.doubleValue();
        return JSType.isRepresentableAsInt(value) ? (int)value : -1;
    }

    @SuppressWarnings("unused")
    private static Object callToApply(final MethodHandle mh, final JSObject obj, final Object thiz, final Object... args) {
        assert args.length >= 2;
        final Object   receiver  = args[0];
        final Object[] arguments = new Object[args.length - 1];
        System.arraycopy(args, 1, arguments, 0, arguments.length);
        try {
            return mh.invokeExact(obj, thiz, new Object[] { receiver, arguments });
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();

    // method handles of the current class
    private static final MethodHandle IS_JSOBJECT_GUARD  = findOwnMH_S("isJSObject", boolean.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_GET = findOwnMH_S("get", Object.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_PUT = findOwnMH_S("put", Void.TYPE, Object.class, Object.class, Object.class);

    // method handles of JSObject class
    private static final MethodHandle JSOBJECT_GETMEMBER     = findJSObjectMH_V("getMember", Object.class, String.class);
    private static final MethodHandle JSOBJECT_SETMEMBER     = findJSObjectMH_V("setMember", Void.TYPE, String.class, Object.class);
    private static final MethodHandle JSOBJECT_CALL          = findJSObjectMH_V("call", Object.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECT_CALL_TO_APPLY = findOwnMH_S("callToApply", Object.class, MethodHandle.class, JSObject.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECT_NEW           = findJSObjectMH_V("newObject", Object.class, Object[].class);

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();
    static {
        CONVERTERS.put(boolean.class, findOwnMH_S("toBoolean", boolean.class, JSObject.class));
        CONVERTERS.put(int.class,     findOwnMH_S("toInt32", int.class, JSObject.class));
        CONVERTERS.put(long.class,    findOwnMH_S("toLong", long.class, JSObject.class));
        CONVERTERS.put(double.class,  findOwnMH_S("toNumber", double.class, JSObject.class));
    }

    private static MethodHandle findJSObjectMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), JSObject.class, name, MH.type(rtype, types));
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), JSObjectLinker.class, name, MH.type(rtype, types));
    }
}
