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

import static java.util.Arrays.asList;

import java.util.ArrayList;

/**
 * 请求日志集合
 */
class RequestLogCollection implements RequestLog {

    /**
     * 请求日志的列表
     */
    private final ArrayList<RequestLog> delegates;

    /**
     * 构造方法
     *
     * @param requestLogs
     */
    public RequestLogCollection(RequestLog... requestLogs) {
        delegates = new ArrayList<>(asList(requestLogs));
    }

    /**
     * 添加
     *
     * @param requestLog
     */
    public void add(RequestLog requestLog) {
        delegates.add(requestLog);
    }

    /**
     * 记录日志
     *
     * @param request The request to log.
     * @param response The response to log.  Note that for some requests
     * the response instance may not have been fully populated (Eg 400 bad request
     * responses are sent without a servlet response object).  Thus for basic
     * log information it is best to consult {@link Response#getCommittedMetaData()}
     */
    @Override
    public void log(Request request, Response response) {
        for (RequestLog delegate:delegates) {
            delegate.log(request, response);
        }
    }
}
