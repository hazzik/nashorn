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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import jdk.internal.dynalink.linker.ConversionComparator.Comparison;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;

/**
 * Default implementation of the {@link LinkerServices} interface.
 *
 * @author Attila Szegedi
 */
public class LinkerServicesImpl implements LinkerServices {

    private static final RuntimePermission GET_CURRENT_LINK_REQUEST = new RuntimePermission("dynalink.getCurrentLinkRequest");
    private static final ThreadLocal<LinkRequest> threadLinkRequest = new ThreadLocal<>();

    private final TypeConverterFactory typeConverterFactory;
    private final GuardingDynamicLinker topLevelLinker;

    /**
     * Creates a new linker services object.
     *
     * @param typeConverterFactory the type converter factory exposed by the services.
     * @param topLevelLinker the top level linker used by the services.
     */
    public LinkerServicesImpl(final TypeConverterFactory typeConverterFactory,
            final GuardingDynamicLinker topLevelLinker) {
        this.typeConverterFactory = typeConverterFactory;
        this.topLevelLinker = topLevelLinker;
    }

    @Override
    public boolean canConvert(Class<?> from, Class<?> to) {
        return typeConverterFactory.canConvert(from, to);
    }

    @Override
    public MethodHandle asType(MethodHandle handle, MethodType fromType) {
        return typeConverterFactory.asType(handle, fromType);
    }

    @Override
    public MethodHandle asTypeLosslessReturn(MethodHandle handle, MethodType fromType) {
        return Implementation.asTypeLosslessReturn(this, handle, fromType);
    }

    @Override
    public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
        return typeConverterFactory.getTypeConverter(sourceType, targetType);
    }

    @Override
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2) {
        return typeConverterFactory.compareConversion(sourceType, targetType1, targetType2);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest) throws Exception {
        final LinkRequest prevLinkRequest = threadLinkRequest.get();
        threadLinkRequest.set(linkRequest);
        try {
            return topLevelLinker.getGuardedInvocation(linkRequest, this);
        } finally {
            threadLinkRequest.set(prevLinkRequest);
        }
    }

    /**
     * Returns the currently processed link request, or null if the method is invoked outside of the linking process.
     * @return the currently processed link request, or null.
     * @throws SecurityException if the calling code doesn't have the {@code "dynalink.getCurrentLinkRequest"} runtime
     * permission.
     */
    public static LinkRequest getCurrentLinkRequest() {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(GET_CURRENT_LINK_REQUEST);
        }
        return threadLinkRequest.get();
    }
}
