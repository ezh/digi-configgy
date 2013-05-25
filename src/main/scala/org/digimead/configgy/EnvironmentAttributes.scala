/**
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

import java.net.InetAddress
import scala.collection.{ immutable, mutable }
import scala.collection.JavaConversions._
import java.lang.UnsupportedOperationException
import org.slf4j.LoggerFactory

/**
 * A ConfigMap that wraps the system environment. This is used as a
 * fallback when looking up "$(...)" substitutions in config files.
 */
object EnvironmentAttributes extends ConfigMap {
  protected val log = LoggerFactory.getLogger(getClass)
  private val env = immutable.Map.empty[String, String] ++ (System.getenv()).iterator

  // deal with java.util.Properties extending
  // java.util.Hashtable[Object, Object] and not
  // java.util.Hashtable[String, String]
  private def getSystemProperties(): mutable.HashMap[String, String] = {
    val map = new mutable.HashMap[String, String]
    for (entry <- System.getProperties().iterator) {
      entry match {
        case (k: String, v: String) => map.put(k, v)
        case _ =>
      }
    }
    map
  }

  def getName() = ""

  def getString(key: String): Option[String] = {
    getSystemProperties().get(key).orElse(env.get(key))
  }

  def getConfigMap(key: String): Option[ConfigMap] = None
  def configMap(key: String): ConfigMap = throw new UnsupportedOperationException("not implemented")

  def getList(key: String): Seq[String] = getString(key) match {
    case None => Array[String]()
    case Some(x) => Array[String](x)
  }

  def setString(key: String, value: String): Unit = throw new UnsupportedOperationException("read-only attributes")
  def setList(key: String, value: Seq[String]): Unit = throw new UnsupportedOperationException("read-only attributes")
  def setConfigMap(key: String, value: ConfigMap): Unit = throw new UnsupportedOperationException("read-only attributes")

  def contains(key: String): Boolean = {
    env.contains(key) || getSystemProperties().contains(key)
  }

  def remove(key: String): Boolean = throw new UnsupportedOperationException("read-only attributes")
  def clear() = throw new UnsupportedOperationException("not implemented")
  def keys: Iterator[String] = (getSystemProperties().keySet ++ env.keySet).iterator
  def asMap(): Map[String, String] = throw new UnsupportedOperationException("not implemented")
  def toConfigString = throw new UnsupportedOperationException("not implemented")
  def subscribe(subscriber: Subscriber): SubscriptionKey = throw new UnsupportedOperationException("not implemented")
  def copy(): ConfigMap = this
  def copyInto[T <: ConfigMap](m: T) = m
  def inheritFrom: Option[ConfigMap] = None
  def inheritFrom_=(config: Option[ConfigMap]) = throw new UnsupportedOperationException("not implemented")
  def dump(): String = throw new UnsupportedOperationException("not implemented")
}
