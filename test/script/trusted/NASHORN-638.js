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
 * NASHORN-638 : Callsite tracing and profiling are broken
 *
 * @test
 * @run
 */

/*
 * creates new script engine initialized with given options and
 * runs given code on it. Returns standard output captured.
 */

function runScriptEngine(opts, code) {
    var imports = new JavaImporter(
        Packages.jdk.nashorn.api.scripting,
        java.io, java.lang, java.util);

    with(imports) {
        var fac = new NashornScriptEngineFactory();
        // get current System.err
        var oldErr = System.err;
        var baos = new ByteArrayOutputStream();
        var newErr = new PrintStream(baos);
        try {
            // set new standard err
            System.setErr(newErr);
            var strType = Java.type("java.lang.String");
            var engine = fac.getScriptEngine(Java.toJavaArray(opts, strType));
            engine.eval(code);
            newErr.flush();
            return new java.lang.String(baos.toByteArray());
        } finally {
            // restore System.err to old value
            System.setErr(oldErr);
        }
    }
}

var str = runScriptEngine([ "-tcs=all" ], "new Date");
print("hello, world!");

if (str.indexOf(" ENTER ") == -1) {
    fail("expected 'ENTER' in trace mode output");
}

if (str.indexOf(" EXIT ") == -1) {
    fail("expected 'EXIT' in trace mode output");
}

