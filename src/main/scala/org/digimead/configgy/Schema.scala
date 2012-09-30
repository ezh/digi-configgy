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

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap

import org.digimead.configgy.Configgy.getImplementation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Schema {
  val log = LoggerFactory.getLogger(getClass)
  private val entry = new HashMap[String, Node[_]]() with SynchronizedMap[String, Node[_]]

  def apply(path: String*): Option[Node[_]] =
    if (path.nonEmpty) entry.get(path.mkString(".")) else None
  def clear(): Unit = entry.clear
  def dump(): String = {
    val buffer = new StringBuilder
    val header = "path[is required, is exists] - description by origin"
    buffer.append("".padTo(header.length(), "-").mkString + "\n")
    buffer.append(header + "\n")
    buffer.append("".padTo(header.length(), "-").mkString + "\n")
    entry.toSeq.sortBy(_._1).foreach {
      case (path, entry) =>
        val isRequired = if (entry.required) "required" else "optional"
        val isExists = if (entry.exists) "exists" else "not exists"
        buffer.append("%s[%s, %s] - %s by %s\n".format(path, isRequired, isExists, entry.description, entry.origin))
    }
    buffer.result
  }
  def entries(): Seq[Node[_]] = entry.values.toSeq
  def optional[T](optionPath: String*)(description: String)(implicit log: Logger, m: Manifest[T]): Node[T] = {
    val node = new Node[T](optionPath.toSeq, description, log.getName(), false)
    entry(optionPath.mkString(".")) = node
    node
  }
  def required[T](optionPath: String*)(description: String)(implicit log: Logger, m: Manifest[T]): Node[T] = {
    val node = new Node[T](optionPath.toSeq, description, log.getName(), true)
    entry(optionPath.mkString(".")) = node
    node
  }
  /**
   * @param configMap - area of validation
   * @return sequence of required nodes that missed initialization
   */
  def validate(configMap: ConfigMap = Configgy): Seq[Node[_]] = {
    if (configMap.getName.isEmpty()) {
      log.debug("validate against whole Configgy configuration")
      entry.filter(t => t._2.required)
    } else {
      log.debug("validate against configuration values at " + configMap.getName)
      val prefix = configMap.getName + "."
      entry.filter(t => t._2.required && t._1.startsWith(prefix))
    }
  }.toSeq.sortBy(_._1).filterNot(_._2.exists).map(_._2)

  case class Node[T](val nodePath: Seq[String], val description: String,
    val origin: String, val required: Boolean)(implicit m: Manifest[T]) {
    if (nodePath.isEmpty) throw new IllegalArgumentException("Please provide key path for configgy value")
    m match {
      case Node.StringManifest =>
      case Node.BooleanManifest =>
      case Node.DoubleManifest =>
      case Node.IntManifest =>
      case Node.SeqStringManifest =>
      case Node.LongManifest =>
      case unexpected =>
        throw new IllegalArgumentException("Unexpected Configgy value type " + m.erasure)
    }
    protected lazy val configMapGetter = getConfigMapByPath(nodePath.dropRight(1), () => Some(Configgy))
    protected lazy val configMapSetter = getOrCreateConfigMapByPath(nodePath.dropRight(1), () => Configgy)
    protected lazy val nodeKey = nodePath.last
    protected lazy val nodeGetter: () => Option[T] =
      (m match {
        case Node.StringManifest => () => configMapGetter().flatMap(_.getString(nodePath.last))
        case Node.BooleanManifest => () => configMapGetter().flatMap(_.getBool(nodePath.last))
        case Node.DoubleManifest => () => configMapGetter().flatMap(_.getDouble(nodePath.last))
        case Node.IntManifest => () => configMapGetter().flatMap(_.getInt(nodePath.last))
        case Node.SeqStringManifest => () => configMapGetter().map(_.getList(nodePath.last))
        case Node.LongManifest => () => configMapGetter().flatMap(_.getLong(nodePath.last))
      }).asInstanceOf[() => Option[T]]
    protected lazy val nodeSetter: (T) => Unit =
      m match {
        case Node.StringManifest => (arg: T) => configMapSetter().setString(nodePath.last, arg.asInstanceOf[String])
        case Node.BooleanManifest => (arg: T) => configMapSetter().setBool(nodePath.last, arg.asInstanceOf[Boolean])
        case Node.DoubleManifest => (arg: T) => configMapSetter().setDouble(nodePath.last, arg.asInstanceOf[Double])
        case Node.IntManifest => (arg: T) => configMapSetter().setInt(nodePath.last, arg.asInstanceOf[Int])
        case Node.SeqStringManifest => (arg: T) => configMapSetter().setList(nodePath.last, arg.asInstanceOf[Seq[String]])
        case Node.LongManifest => (arg: T) => configMapSetter().setLong(nodePath.last, arg.asInstanceOf[Long])
      }
    protected lazy val nodeCheck: () => Boolean = () => configMapGetter().map(_.contains(nodePath.last)).getOrElse(false)
    protected lazy val nodeRemove: () => Boolean = () => configMapGetter().map(_.remove(nodePath.last)).getOrElse(false)
    log.debug("register node %s[%s] by %s".format(nodePath.mkString("."), (if (required) "required" else "optional"), origin))

    def apply() = nodeGetter()
    def getConfigMap() = configMapGetter()
    def configMap() = configMapSetter()
    def key() = nodeKey
    def getName() = nodePath.mkString(".")
    def get(): Option[T] = nodeGetter()
    def getOrElse(default: T): T =
      get getOrElse { set(default); default }
    def set(arg: T) = nodeSetter(arg)
    def :=(arg: T) = nodeSetter(arg)
    def exists() = nodeCheck()
    def remove() = nodeRemove()
    protected def getConfigMapByPath(path: Seq[String], base: () => Option[ConfigMap]): () => Option[ConfigMap] =
      if (path.isEmpty) base else getConfigMapByPath(path.tail, () => base().flatMap(_.getConfigMap(path.head)))
    protected def getOrCreateConfigMapByPath(path: Seq[String], base: () => ConfigMap): () => ConfigMap =
      if (path.isEmpty) base else getOrCreateConfigMapByPath(path.tail, () => base().configMap(path.head))
  }
  object Node {
    val StringManifest = manifest[String]
    val BooleanManifest = manifest[Boolean]
    val DoubleManifest = manifest[Double]
    val IntManifest = manifest[Int]
    val SeqStringManifest = manifest[Seq[String]]
    val LongManifest = manifest[Long]
  }
}
