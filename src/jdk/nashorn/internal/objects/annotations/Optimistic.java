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

package jdk.nashorn.internal.objects.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Optimistic annotation is used for builtins that can throw UnwarrantedOptimism
 * exception if they are wrong, whose callsite is optimistic and contains a program
 * point. Every optimistic builtin callsite needs to bind its programPoint to an
 * argument of the implementation at compile time
 *
 * Typical use case would be something like: if you want to do an optimistic "push" function
 * in NativeArray that takes an int, write
 *
 *  <pre>
 *  @SpecializedFunction @Optimistic
 *  public static int push(final Object self, final int x, final int programPoint) {
 *    try {
 *    //push code assuming that this is an int array
 *    //return new length of array, assuming it's an int
 *    } catch (ArrayWasWrong e) {
 *       //undo any array modifications
 *       throw new UnwarrantedOptimismExceptionArrayType(x, programPoint);
 *    } catch (LengthOverFlowed e) {
 *       //undo any array modifications
 *       throw new UnwarratnedOptimismExceptionArrayLength(uint32lenThatOverFlowed, programPoint)
 *    }
 *  }
 *  </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Optimistic {
    //empty
}
