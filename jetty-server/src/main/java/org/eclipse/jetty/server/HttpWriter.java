//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.jetty.util.ByteArrayOutputStream2;

/**
 * Http输出字符流
 */
public abstract class HttpWriter extends Writer {

    /**
     * 最大输出字符
     */
    public static final int MAX_OUTPUT_CHARS = 512;

    /**
     * Http输出
     */
    final HttpOutput _out;

    /**
     *
     */
    final ByteArrayOutputStream2 _bytes;

    /**
     * char数组
     */
    final char[] _chars;

    /* ------------------------------------------------------------ */

    /**
     * 构造方法
     *
     * @param out
     */
    public HttpWriter(HttpOutput out) {
        _out=out;
        _chars=new char[MAX_OUTPUT_CHARS];
        _bytes = new ByteArrayOutputStream2(MAX_OUTPUT_CHARS);   
    }

    /* ------------------------------------------------------------ */

    /**
     * 关闭
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        _out.close();
    }

    /* ------------------------------------------------------------ */

    /**
     * 刷新
     *
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {
        _out.flush();
    }

    /* ------------------------------------------------------------ */

    /**
     * 写入字符串
     * 通过调用写入char数组的write方法实现
     *
     * @param s
     * @param offset
     * @param length
     * @throws IOException
     */
    @Override
    public void write (String s,int offset, int length) throws IOException {
        while (length > MAX_OUTPUT_CHARS) {
            write(s, offset, MAX_OUTPUT_CHARS);
            offset += MAX_OUTPUT_CHARS;
            length -= MAX_OUTPUT_CHARS;
        }

        s.getChars(offset, offset + length, _chars, 0);
        write(_chars, 0, length);
    }

    /* ------------------------------------------------------------ */

    /**
     * 写入char数组
     *
     * @param s
     * @param offset
     * @param length
     * @throws IOException
     */
    @Override
    public void write (char[] s,int offset, int length) throws IOException {
        throw new AbstractMethodError();
    }
}
