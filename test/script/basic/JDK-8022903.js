/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * JDK-8022903: Enhance for-in and for-each for Lists and Maps
 *
 * @test
 * @run
 */

var colors = new java.util.ArrayList()
colors.add("red")
colors.add("purple")
colors.add("pink")

for(var index in colors) {
    print("colors[" + index + "]=" + colors[index])
}

for each(var color in colors) {
    print(color)
}

var capitals = new java.util.LinkedHashMap()
capitals.Sweden = "Stockholm"
capitals.Hungary = "Budapest"
capitals.Croatia = "Zagreb"

for(var key in capitals) {
    print("capital of " + key + " is " + capitals[key])
}

for each(var capital in capitals) {
    print(capital)
}
