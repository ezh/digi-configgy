/**
 * Digi Configgy is a library for handling configurations
 *
 * Copyright 2012 Alexey Aksenov <ezh@ezh.msk.ru>
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

import org.slf4j.LoggerFactory
import org.slf4j.Logger

object Schema {
  val log = LoggerFactory.getLogger(getClass)
  @volatile private var entry = Seq[Value[_]]()
  def dump() {
    System.out.println("\n--------------------------")
    System.out.println("path[is required, is exists] - description by origin")
    System.out.println("--------------------------")
    entry.map(e => {
      (e.optionPath.mkString("/"), e)
    }).sortBy(_._1).foreach {
      case (path, entry) =>
        val isRequired = if (entry.required) "required" else "optional"
        val isExists = "not exists"
        System.out.println("%s[%s, %s] - %s by %s".format(path, isRequired, isExists, entry.description, entry.origin))
    }
  }
  def required[T](optionPath: String*)(description: String)(implicit log: Logger, m: Manifest[T]): Value[T] =
    new Value[T](optionPath.toArray, description, log.getName(), true)

  class Value[T](val optionPath: Array[String], val description: String,
    val origin: String, val required: Boolean)(implicit m: Manifest[T]) {
    assert(optionPath.nonEmpty, "please provide key path for configgy value")
    assert(m <:< Value.StringManifest || m <:< Value.BooleanManifest || m <:< Value.DoubleManifest ||
      m <:< Value.IntManifest || m <:< Value.SeqStringManifest || m <:< Value.LongManifest,
      "unexpected Configgy value type " + m.erasure)
    log.debug("register %s key /%s for %s".format((if (required) "required" else "optional"),
      optionPath.mkString("/"), origin))
    entry = entry :+ this
    def get(): Option[T] = {
      val prefix = optionPath.dropRight(1)
      val suffix = optionPath.last
      getConfigMapByPath(prefix, Configgy).map {
        configMap =>
          (m match {
            case Value.StringManifest => configMap.getString(suffix)
            case Value.BooleanManifest => configMap.getBool(suffix)
            case Value.DoubleManifest => configMap.getDouble(suffix)
            case Value.IntManifest => configMap.getInt(suffix)
            case Value.SeqStringManifest => configMap.getList(suffix)
            case Value.LongManifest => configMap.getLong(suffix)
          }).asInstanceOf[T]
      }
    }
    def set(value: T): Unit = {
      val prefix = optionPath.dropRight(1)
      val suffix = optionPath.last
      getConfigMapByPath(prefix, Configgy).map {
        configMap =>
          (m match {
            case Value.StringManifest => configMap.setString(suffix, value.asInstanceOf[String])
            case Value.BooleanManifest => configMap.setBool(suffix, value.asInstanceOf[Boolean])
            case Value.DoubleManifest => configMap.setDouble(suffix, value.asInstanceOf[Double])
            case Value.IntManifest => configMap.setInt(suffix, value.asInstanceOf[Int])
            case Value.SeqStringManifest => configMap.setList(suffix, value.asInstanceOf[Seq[String]])
            case Value.LongManifest => configMap.setLong(suffix, value.asInstanceOf[Long])
          }).asInstanceOf[T]
      }
    }
    protected def getConfigMapByPath(path: Array[String], base: ConfigMap): Option[ConfigMap] =
      if (path.isEmpty)
        Some(base)
      else
        base.getConfigMap(path.head) match {
          case Some(configMap) => getConfigMapByPath(path.tail, configMap)
          case None => None
        }
  }
  object Value {
    val StringManifest = manifest[String]
    val BooleanManifest = manifest[Boolean]
    val DoubleManifest = manifest[Double]
    val IntManifest = manifest[Int]
    val SeqStringManifest = manifest[Seq[String]]
    val LongManifest = manifest[Long]
  }
}