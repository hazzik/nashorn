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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;

import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuards;

/**
 * Instances of this class are quite ephemeral; they only exist for the duration of an invocation of
 * {@link ScriptObject#findSetMethod(CallSiteDescriptor, jdk.internal.dynalink.linker.LinkRequest)} and
 * serve as the actual encapsulation of the algorithm for creating an appropriate property setter method.
 */
final class SetMethodCreator {
    // See constructor parameters for description of fields
    private final ScriptObject       sobj;
    private final PropertyMap        map;
    private final FindProperty       find;
    private final CallSiteDescriptor desc;
    private final Class<?>           type;
    private final boolean            explicitInstanceOfCheck;

    /**
     * Creates a new property setter method creator.
     * @param sobj the object for which we're creating the property setter
     * @param find a result of a {@link ScriptObject#findProperty(String, boolean)} on the object for the property we
     * want to create a setter for. Can be null if the property does not yet exist on the object.
     * @param desc the descriptor of the call site that triggered the property setter lookup
     */
    SetMethodCreator(final ScriptObject sobj, final FindProperty find, final CallSiteDescriptor desc, final boolean explicitInstanceOfCheck) {
        this.sobj = sobj;
        this.map  = sobj.getMap();
        this.find = find;
        this.desc = desc;
        this.type = desc.getMethodType().parameterType(1);
        this.explicitInstanceOfCheck = explicitInstanceOfCheck;

    }

    private String getName() {
        return desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
    }

    private PropertyMap getMap() {
        return map;
    }

    /**
     * Creates the actual guarded invocation that represents the dynamic setter method for the property.
     * @return the actual guarded invocation that represents the dynamic setter method for the property.
     */
    GuardedInvocation createGuardedInvocation() {
        return createSetMethod().createGuardedInvocation();
    }

    /**
     * This class encapsulates the results of looking up a setter method; it's basically a triple of a method handle,
     * a Property object, and flags for invocation.
     *
     */
    private class SetMethod {
        private final MethodHandle methodHandle;
        private final Property property;

        /**
         * Creates a new lookup result.
         * @param methodHandle the actual method handle
         * @param property the property object. Can be null in case we're creating a new property in the global object.
         */
        SetMethod(final MethodHandle methodHandle, final Property property) {
            assert methodHandle != null;
            this.methodHandle = methodHandle;
            this.property     = property;
        }

        /**
         * Composes from its components an actual guarded invocation that represents the dynamic setter method for the property.
         * @return the composed guarded invocation that represents the dynamic setter method for the property.
         */
        GuardedInvocation createGuardedInvocation() {
            // getGuard() and getException() either both return null, or neither does. The reason for that is that now
            // getGuard returns a map guard that casts its argument to ScriptObject, and if that fails, we need to
            // relink on ClassCastException.
            return new GuardedInvocation(methodHandle, NashornGuards.getGuard(sobj, property, desc, explicitInstanceOfCheck),
                    (SwitchPoint)null, explicitInstanceOfCheck ? null : ClassCastException.class);
        }
    }

    private SetMethod createSetMethod() {
        if (find != null) {
            return createExistingPropertySetter();
        }

        checkStrictCreateNewVariable();

        if (sobj.isScope()) {
            return createGlobalPropertySetter();
        }

        return createNewPropertySetter();
    }

    private void checkStrictCreateNewVariable() {
        // In strict mode, assignment can not create a new variable.
        // See also ECMA Annex C item 4. ReferenceError is thrown.
        if (NashornCallSiteDescriptor.isScope(desc) && NashornCallSiteDescriptor.isStrict(desc)) {
            throw referenceError("not.defined", getName());
        }
    }

    private SetMethod createExistingPropertySetter() {
        final Property property = find.getProperty();
        final MethodHandle methodHandle = find.getSetter(type, NashornCallSiteDescriptor.isStrict(desc));

        assert methodHandle != null;
        assert property     != null;

        final MethodHandle boundHandle;
        if (!property.hasSetterFunction(find.getOwner()) && find.isInherited()) {
            boundHandle = ScriptObject.addProtoFilter(methodHandle, find.getProtoChainLength());
        } else {
            boundHandle = methodHandle;
        }
        return new SetMethod(boundHandle, property);
    }

    private SetMethod createGlobalPropertySetter() {
        final ScriptObject global = Context.getGlobal();
        return new SetMethod(MH.filterArguments(global.addSpill(type, getName()), 0, ScriptObject.GLOBALFILTER), null);
    }

    private SetMethod createNewPropertySetter() {
        final SetMethod sm = map.getFieldCount() < map.getFieldMaximum() ? createNewFieldSetter() : createNewSpillPropertySetter();
        final PropertyListeners listeners = map.getListeners();
        if (listeners != null) {
            listeners.propertyAdded(sm.property);
        }
        return sm;
    }

    private SetMethod createNewSetter(final Property property) {
        final PropertyMap oldMap   = getMap();
        final PropertyMap newMap   = getNewMap(property);
        final boolean     isStrict = NashornCallSiteDescriptor.isStrict(desc);
        final String      name     = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);

        //fast type specific setter
        final MethodHandle fastSetter = property.getSetter(type, newMap); //0 sobj, 1 value, slot folded for spill property already

        //slow setter, that calls ScriptObject.set with appropraite type and key name
        MethodHandle slowSetter = ScriptObject.SET_SLOW[getAccessorTypeIndex(type)];
        slowSetter = MH.insertArguments(slowSetter, 3, NashornCallSiteDescriptor.isStrict(desc));
        slowSetter = MH.insertArguments(slowSetter, 1, name);
        slowSetter = MH.asType(slowSetter, slowSetter.type().changeParameterType(0, Object.class));

        assert slowSetter.type().equals(fastSetter.type()) : "slow=" + slowSetter + " != fast=" + fastSetter;

        //cas map used as guard, if true that means we can do the set fast
        MethodHandle casMap = MH.insertArguments(ScriptObject.CAS_MAP, 1, oldMap, newMap);
        casMap = MH.dropArguments(casMap, 1, type);
        casMap = MH.asType(casMap, casMap.type().changeParameterType(0, Object.class));
        final MethodHandle casGuard = MH.guardWithTest(casMap, fastSetter, slowSetter);

        //outermost level needs an extendable check. if object can be extended, guard is true and
        //we can run the cas setter. The setter goes to "nop" VOID_RETURN if false or throws an
        //exception if we are in strict mode and object is not extensible
        MethodHandle extCheck = MH.insertArguments(ScriptObject.EXTENSION_CHECK, 1, isStrict, name);
        extCheck = MH.asType(extCheck, extCheck.type().changeParameterType(0, Object.class));
        extCheck = MH.dropArguments(extCheck, 1, type);

        MethodHandle nop = JSType.VOID_RETURN.methodHandle();
        nop = MH.dropArguments(nop, 0, Object.class, type);

        return new SetMethod(MH.asType(MH.guardWithTest(extCheck, casGuard, nop), fastSetter.type()), property);
    }

    private SetMethod createNewFieldSetter() {
        return createNewSetter(new AccessorProperty(getName(), 0, sobj.getClass(), getMap().getFieldCount(), type));
    }

    private SetMethod createNewSpillPropertySetter() {
        return createNewSetter(new SpillProperty(getName(), 0, getMap().getSpillLength(), type));
    }

    private PropertyMap getNewMap(final Property property) {
        return getMap().addProperty(property);
    }
}
