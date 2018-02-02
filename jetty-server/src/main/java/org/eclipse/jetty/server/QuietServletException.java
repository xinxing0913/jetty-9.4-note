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

import javax.servlet.ServletException;

import org.eclipse.jetty.io.QuietException;


/* ------------------------------------------------------------ */
/** A ServletException that is logged less verbosely than
 * a normal ServletException.
 * <p>
 * Used for container generated exceptions that need only a message rather
 * than a stack trace.
 * </p>
 *
 * 静默的Serlvet异常
 * 它打印比较少的日志，它只是需要一个信息，并不需要一个记录栈
 */
public class QuietServletException extends ServletException implements QuietException {

    /**
     * 构造方法
     */
    public QuietServletException() {
        super();
    }

    /**
     * 构造方法
     *
     * @param message
     * @param rootCause
     */
    public QuietServletException(String message, Throwable rootCause) {
        super(message,rootCause);
    }

    /**
     * 构造方法
     *
     * @param message
     */
    public QuietServletException(String message) {
        super(message);
    }

    /**
     * 构造方法
     *
     * @param rootCause
     */
    public QuietServletException(Throwable rootCause) {
        super(rootCause);
    }
}
