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

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlParser;

/**
 * 描述文件
 */
public abstract class Descriptor {

    /**
     * 资源文件
     */
    protected Resource _xml;

    /**
     * 根节点
     */
    protected XmlParser.Node _root;

    /**
     * dtd文件
     */
    protected String _dtd;

    /**
     * 校验
     */
    protected boolean _validating;

    /**
     * 构造方法
     *
     * @param xml
     */
    public Descriptor (Resource xml) {
        _xml = xml;
    }

    /**
     * 确保解析通过
     *
     * @return
     * @throws ClassNotFoundException
     */
    public abstract XmlParser ensureParser() throws ClassNotFoundException;

    /**
     * 设置是否校验
     *
     * @param validating
     */
    public void setValidating (boolean validating) {
       _validating = validating;
    }

    /**
     * 解析
     *
     * @throws Exception
     */
    public void parse () throws Exception {
        if (_root == null) {
            try {
                XmlParser parser = ensureParser();
                _root = parser.parse(_xml.getInputStream());
                _dtd = parser.getDTD();
            } finally {
                _xml.close();
            }
        }
    }

    /**
     * 获取资源
     */
    public Resource getResource () {
        return _xml;
    }

    /**
     * 获取根节点
     *
     * @return
     */
    public XmlParser.Node getRoot () {
        return _root;
    }

    /**
     * 转换为字符串
     *
     * @return
     */
    public String toString() {
        return this.getClass().getSimpleName()+"("+_xml+")";
    }
}
