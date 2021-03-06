/**
 * Digi Configgy is a library for handling configurations
 *
 * Copyright 2009 Robey Pointer <robeypointer@gmail.com>
 * Copyright 2012-2013 Alexey Aksenov <ezh@ezh.msk.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.digimead.configgy

import scala.collection.JavaConversions._

import org.slf4j.LoggerFactory

import javax.{ management => jmx }

class JmxWrapper(node: Attributes) extends jmx.DynamicMBean {
  val log = LoggerFactory.getLogger(getClass)
  val operations: Array[jmx.MBeanOperationInfo] = Array(
    new jmx.MBeanOperationInfo("set", "set a string value",
      Array(
        new jmx.MBeanParameterInfo("key", "java.lang.String", "config key"),
        new jmx.MBeanParameterInfo("value", "java.lang.String", "string value")), "void", jmx.MBeanOperationInfo.ACTION),
    new jmx.MBeanOperationInfo("remove", "remove a value",
      Array(
        new jmx.MBeanParameterInfo("key", "java.lang.String", "config key")), "void", jmx.MBeanOperationInfo.ACTION),
    new jmx.MBeanOperationInfo("add_list", "append a value to a list",
      Array(
        new jmx.MBeanParameterInfo("key", "java.lang.String", "config key"),
        new jmx.MBeanParameterInfo("value", "java.lang.String", "value")), "void", jmx.MBeanOperationInfo.ACTION),
    new jmx.MBeanOperationInfo("remove_list", "remove a value to a list",
      Array(
        new jmx.MBeanParameterInfo("key", "java.lang.String", "config key"),
        new jmx.MBeanParameterInfo("value", "java.lang.String", "value")), "void", jmx.MBeanOperationInfo.ACTION))

  def getMBeanInfo() =
    new jmx.MBeanInfo("org.digimead.configgy.ConfigMap", // The name of the Java class of the MBean described by this MBeanInfo
        "configuration node",                            // description
        node.asJmxAttributes(),                          // attributes
        null,                                            // constructors
        operations,                                      // operations
        null,                                            // notifications
        new jmx.ImmutableDescriptor("immutableInfo=false")) // descriptor

  def getAttribute(name: String): AnyRef = node.asJmxDisplay(name)

  def getAttributes(names: Array[String]): jmx.AttributeList = {
    val rv = new jmx.AttributeList
    for (name <- names) rv.add(new jmx.Attribute(name, getAttribute(name)))
    rv
  }

  def invoke(actionName: String, params: Array[Object], signature: Array[String]): AnyRef = {
    actionName match {
      case "set" =>
        params match {
          case Array(name: String, value: String) =>
            try {
              node.setString(name, value)
            } catch {
              case e: Exception =>
                log.warn("exception: %s", e.getMessage)
                throw e
            }
          case _ =>
            throw new jmx.MBeanException(new Exception("bad signature " + params.toList.toString))
        }
      case "remove" =>
        params match {
          case Array(name: String) =>
            node.remove(name)
          case _ =>
            throw new jmx.MBeanException(new Exception("bad signature " + params.toList.toString))
        }
      case "add_list" =>
        params match {
          case Array(name: String, value: String) =>
            node.setList(name, node.getList(name).toList ++ List(value))
          case _ =>
            throw new jmx.MBeanException(new Exception("bad signature " + params.toList.toString))
        }
      case "remove_list" =>
        params match {
          case Array(name: String, value: String) =>
            node.setList(name, node.getList(name).toList.filterNot(_ == value))
          case _ =>
            throw new jmx.MBeanException(new Exception("bad signature " + params.toList.toString))
        }
      case _ =>
        throw new jmx.MBeanException(new Exception("no such method"))
    }
    null
  }

  def setAttribute(attr: jmx.Attribute): Unit = {
    attr.getValue() match {
      case s: String =>
        node.setString(attr.getName(), s)
      case _ =>
        throw new jmx.InvalidAttributeValueException()
    }
  }

  def setAttributes(attrs: jmx.AttributeList): jmx.AttributeList = {
    for (attr <- attrs.asList) setAttribute(attr)
    attrs
  }
}
