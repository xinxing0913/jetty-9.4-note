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

package org.eclipse.jetty.webapp;

import java.util.List;

import org.eclipse.jetty.util.resource.Resource;

/**
 * Ordering options for jars in WEB-INF lib.
 *
 * 顺序
 * 主要分为两个:
 * 1.绝对顺序
 * 2.相对顺序
 */
public interface Ordering {

    /**
     * 排序
     *
     * @param fragments
     * @return
     */
    public List<Resource> order(List<Resource> fragments); 
}
