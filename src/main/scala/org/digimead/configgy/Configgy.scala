/**
 * Digi Configgy is a library for handling configurations
 *
 * Copyright 2009 Robey Pointer <robeypointer@gmail.com>
 * Copyright 2012-2014 Alexey Aksenov <ezh@ezh.msk.ru>
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

import java.io.File
import java.lang.management.ManagementFactory
import javax.{ management ⇒ jmx }
import org.slf4j.LoggerFactory
import scala.collection.{ Map, mutable }
import scala.collection.mutable.HashMap
import scala.language.implicitConversions

private abstract class Phase
private case object VALIDATE_PHASE extends Phase
private case object COMMIT_PHASE extends Phase

private class SubscriptionNode {
  var subscribers = new mutable.HashSet[Subscriber]
  var map = new mutable.HashMap[String, SubscriptionNode]

  def get(name: String): SubscriptionNode = {
    map.get(name) match {
      case Some(x) ⇒ x
      case None ⇒
        val node = new SubscriptionNode
        map(name) = node
        node
    }
  }

  override def toString() = {
    val out = new StringBuilder("%d" format subscribers.size)
    if (map.size > 0) {
      out.append(" { ")
      for (key ← map.keys) {
        out.append(key)
        out.append("=")
        out.append(map(key).toString)
        out.append(" ")
      }
      out.append("}")
    }
    out.toString
  }

  @throws(classOf[ValidationException])
  def validate(key: List[String], current: Option[ConfigMap], replacement: Option[ConfigMap], phase: Phase): Unit = {
    if ((current == None) && (replacement == None)) {
      // someone has subscribed to a nonexistent node... ignore.
      return
    }

    // first, call all subscribers for this node.
    for (subscriber ← subscribers) {
      phase match {
        case VALIDATE_PHASE ⇒ subscriber.validate(current, replacement)
        case COMMIT_PHASE ⇒ subscriber.commit(current, replacement)
      }
    }

    /* if we're walking a key, lookup the next segment's subscribers and
     * continue the validate/commit. if the key is exhausted, call
     * subscribers for ALL nodes below this one.
     */
    var nextNodes: Iterator[(String, SubscriptionNode)] = null
    key match {
      case Nil ⇒ nextNodes = map.iterator
      case segment :: _ ⇒ {
        map.get(segment) match {
          case None ⇒ return // done!
          case Some(node) ⇒ nextNodes = Iterator.single((segment, node))
        }
      }
    }

    for ((segment, node) ← nextNodes) {
      val subCurrent = current match {
        case None ⇒ None
        case Some(x) ⇒ x.getConfigMap(segment)
      }
      val subReplacement = replacement match {
        case None ⇒ None
        case Some(x) ⇒ x.getConfigMap(segment)
      }
      node.validate(if (key == Nil) Nil else key.tail, subCurrent, subReplacement, phase)
    }
  }
}

/**
 * An attribute map of key/value pairs and subscriptions, where values may
 * be other attribute maps. Config objects represent the "root" of a nested
 * set of attribute maps, and control the flow of subscriptions and events
 * for subscribers.
 */
abstract class Configgy extends Configgy.Interface {
  protected val log = LoggerFactory.getLogger(getClass)
  private var root = new Attributes(this, "")
  private val subscribers = new SubscriptionNode
  private val subscriberKeys = new HashMap[Int, (SubscriptionNode, Subscriber)]
  private var nextKey = 1

  private var jmxNodes: List[String] = Nil
  private var jmxPackageName: String = ""
  private var jmxSubscriptionKey: Option[SubscriptionKey] = None
  private var reloadAction: Option[() ⇒ Unit] = None

  /**
   * Importer for resolving "include" lines when loading config files.
   * By default, it's a FilesystemImporter based on the current working
   * directory.
   */
  var importer: Importer = new FilesystemImporter(new File(".").getCanonicalPath)

  /**
   * Read config data from a string and use it to populate this object.
   */
  def load(data: String) {
    reloadAction = Some(() ⇒ configure(data))
    reload()
  }

  /**
   * Read config data from a file and use it to populate this object.
   */
  def loadFile(filename: String) {
    reloadAction = Some(() ⇒ configure(importer.importFile(filename)))
    reload()
  }

  /**
   * Read config data from a file and use it to populate this object.
   */
  def loadFile(path: String, filename: String) {
    importer = new FilesystemImporter(path)
    loadFile(filename)
  }

  /**
   * Reloads the configuration from whatever source it was previously loaded
   * from, undoing any in-memory changes.  This is a no-op if the configuration
   * data has not be loaded from a source (file or string).
   */
  def reload() {
    reloadAction.foreach(_())
  }

  private def configure(data: String) {
    val newRoot = new Attributes(this, "")
    new ConfigParser(newRoot, importer) parse data

    if (root.isMonitored) {
      // throws exception if validation fails:
      List(VALIDATE_PHASE, COMMIT_PHASE) foreach (p ⇒ subscribers.validate(Nil, Some(root), Some(newRoot), p))
    }

    if (root.isMonitored) newRoot.setMonitored
    root.replaceWith(newRoot)
  }

  // -----  subscriptions

  def subscribe(key: String, subscriber: Subscriber): SubscriptionKey = synchronized {
    root.setMonitored
    var subkey = nextKey
    nextKey += 1
    var node = subscribers
    if (key ne null) {
      for (segment ← key.split("\\.")) {
        node = node.get(segment)
      }
    }
    node.subscribers += subscriber
    subscriberKeys += ((subkey, (node, subscriber)))
    new SubscriptionKey(this, subkey)
  }

  def subscribe(key: String)(f: (Option[ConfigMap]) ⇒ Unit): SubscriptionKey = {
    subscribe(key, new Subscriber {
      def validate(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {}
      def commit(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {
        f(replacement)
      }
    })
  }

  def subscribe(subscriber: Subscriber) = subscribe(null.asInstanceOf[String], subscriber)

  override def subscribe(f: (Option[ConfigMap]) ⇒ Unit): SubscriptionKey = subscribe(null.asInstanceOf[String])(f)

  def unsubscribe(subkey: SubscriptionKey) = synchronized {
    subscriberKeys.get(subkey.id) match {
      case None ⇒ false
      case Some((node, sub)) ⇒ {
        node.subscribers -= sub
        subscriberKeys -= subkey.id
        true
      }
    }
  }

  /**
   * Return a formatted string of all the subscribers, useful for debugging.
   */
  def debugSubscribers(): String = synchronized {
    "subs=" + subscribers.toString
  }

  /**
   * Un-register this object from JMX. Any existing JMX nodes for this config object will vanish.
   */
  def unregisterWithJmx() = {
    val mbs = ManagementFactory.getPlatformMBeanServer()
    for (name ← jmxNodes) {
      log.debug("unregister jmx MBean " + name)
      mbs.unregisterMBean(new jmx.ObjectName(name))
    }
    jmxNodes = Nil
    for (key ← jmxSubscriptionKey) unsubscribe(key)
    jmxSubscriptionKey = None
  }

  /**
   * Register this object as a tree of JMX nodes that can be used to view and modify the config.
   * This has the effect of subscribing to the root node, in order to reflect changes to the
   * config object in JMX.
   *
   * @param packageName the name (usually your app's package name) that config objects should
   *     appear inside
   */
  def registerWithJmx(packageName: String): Unit = {
    val mbs = ManagementFactory.getPlatformMBeanServer()
    val nodes = root.getJmxNodes(packageName, "")
    val nodeNames = nodes.map { case (name, bean) ⇒ name }
    // register any new nodes
    nodes.filter { name ⇒ !(jmxNodes contains name) }.foreach {
      case (name, bean) ⇒
        val jmxName = new jmx.ObjectName(name)
        log.debug("register jmx MBean " + name)
        // remove junk if necessary
        if (mbs.isRegistered(jmxName))
          mbs.unregisterMBean(jmxName)
        mbs.registerMBean(bean, jmxName)
    }
    // unregister nodes that vanished
    (jmxNodes filterNot (nodeNames.contains)).foreach { name ⇒
      log.debug("unregister jmx MBean " + name)
      mbs.unregisterMBean(new jmx.ObjectName(name))
    }

    jmxNodes = nodeNames
    jmxPackageName = packageName
    if (jmxSubscriptionKey == None) {
      jmxSubscriptionKey = Some(subscribe { _ ⇒ registerWithJmx(packageName) })
    }
  }

  // -----  modifications that happen within monitored Attributes nodes

  @throws(classOf[ValidationException])
  private def deepChange(name: String, key: String, operation: (ConfigMap, String) ⇒ Boolean): Boolean = synchronized {
    val fullKey = if (name == "") (key) else (name + "." + key)
    val newRoot = root.copy
    val keyList = fullKey.split("\\.").toList

    if (!operation(newRoot, fullKey)) {
      return false
    }

    // throws exception if validation fails:
    subscribers.validate(keyList, Some(root), Some(newRoot), VALIDATE_PHASE)
    subscribers.validate(keyList, Some(root), Some(newRoot), COMMIT_PHASE)

    if (root.isMonitored) newRoot.setMonitored
    root.replaceWith(newRoot)
    true
  }

  def deepSet(name: String, key: String, value: String) = {
    deepChange(name, key, { (newRoot, fullKey) ⇒ newRoot(fullKey) = value; true })
  }

  def deepSet(name: String, key: String, value: Seq[String]) = {
    deepChange(name, key, { (newRoot, fullKey) ⇒ newRoot(fullKey) = value; true })
  }

  def deepSet(name: String, key: String, value: ConfigMap) = {
    deepChange(name, key, { (newRoot, fullKey) ⇒ newRoot.setConfigMap(fullKey, value); true })
  }

  def deepRemove(name: String, key: String): Boolean = {
    deepChange(name, key, { (newRoot, fullKey) ⇒ newRoot.remove(fullKey) })
  }

  // -----  implement AttributeMap by wrapping our root object:

  def getString(key: String): Option[String] = root.getString(key)
  def getConfigMap(key: String): Option[ConfigMap] = root.getConfigMap(key)
  def configMap(key: String): ConfigMap = root.configMap(key)
  def getList(key: String): Seq[String] = root.getList(key)
  def setString(key: String, value: String): Unit = root.setString(key, value)
  def setList(key: String, value: Seq[String]): Unit = root.setList(key, value)
  def setConfigMap(key: String, value: ConfigMap): Unit = root.setConfigMap(key, value)
  def contains(key: String): Boolean = root.contains(key)
  def remove(key: String): Boolean = root.remove(key)
  def clear() = root.clear()
  def keys: Iterator[String] = root.keys
  def asMap(): Map[String, String] = root.asMap()
  def toConfigString = root.toConfigString
  def copy(): ConfigMap = root.copy()
  def copyInto[T <: ConfigMap](m: T): T = root.copyInto(m)
  def inheritFrom = root.inheritFrom
  def inheritFrom_=(config: Option[ConfigMap]) = root.inheritFrom = (config)
  def getName(): String = root.name
  def dump(): String = root.dump

  override def toString = "default Configgy implementation"
}

/**
 * Main API entry point into the configgy library.
 */
object Configgy {
  implicit def getImplementation(c: Configgy.type = Configgy): Interface = c.implementation
  private var implementation: Interface = _
  val log = LoggerFactory.getLogger(getClass)

  ConfiggyInitializationArgument.foreach(setup)

  def setup(arg: Init): Unit = synchronized {
    Option(implementation).foreach(_.dispose)
    log.debug("initialize Configgy$ with " + arg.implementation)
    implementation = arg.implementation
    implementation.init()
  }

  trait Interface extends ConfigMap {
    def dump(): String
    // initialization
    def init()
    def dispose()
    // import
    /**
     * Importer for resolving "include" lines when loading config files.
     * By default, it's a FilesystemImporter based on the current working
     * directory.
     */
    var importer: Importer
    /**
     * Read config data from a string and use it to populate this object.
     */
    def load(data: String)
    /**
     * Read config data from a file and use it to populate this object.
     */
    def loadFile(filename: String)
    /**
     * Read config data from a file and use it to populate this object.
     */
    def loadFile(path: String, filename: String)
    /**
     * Reload the previously-loaded config file from disk. Any changes will
     * take effect immediately. **All** subscribers will be called to
     * verify and commit the change (even if their nodes didn't actually
     * change).
     */
    def reload()
    // -----  jmx
    /**
     * Register this object as a tree of JMX nodes that can be used to view and modify the config.
     * This has the effect of subscribing to the root node, in order to reflect changes to the
     * config object in JMX.
     *
     * @param packageName the name (usually your app's package name) that config objects should
     *     appear inside
     */
    def registerWithJmx(packageName: String): Unit
    /**
     * Un-register this object from JMX. Any existing JMX nodes for this config object will vanish.
     */
    def unregisterWithJmx(): Unit
    // -----  modifications that happen within monitored Attributes nodes
    def deepSet(name: String, key: String, value: String)
    def deepSet(name: String, key: String, value: Seq[String])
    def deepSet(name: String, key: String, value: ConfigMap)
    def deepRemove(name: String, key: String): Boolean
    // -----  subscriptions
    def subscribe(key: String, subscriber: Subscriber): SubscriptionKey
    def subscribe(key: String)(f: (Option[ConfigMap]) ⇒ Unit): SubscriptionKey
    def subscribe(subscriber: Subscriber): SubscriptionKey
    def unsubscribe(subkey: SubscriptionKey)
    /**
     * Return a formatted string of all the subscribers, useful for debugging.
     */
    def debugSubscribers(): String
  }
  trait Init {
    val implementation: Interface
  }
  class DefaultInit extends Init {
    val implementation: Interface = new Configgy {
      override protected val log = LoggerFactory.getLogger(getClass.getPackage().getName() + ".Configgy$$")
      def init() = log.debug("initialize " + this)
      def dispose() {
        log.debug("dispose " + this)
        implementation.unregisterWithJmx
      }
    }
  }
  /**
   * Create a Config object from a config file of the given filename.
   * The base folder will be extracted from the filename and used as a base
   * path for resolving filenames given in "include" lines.
   */
  class DefaultInitFromFile(file: File) extends Init {
    def this(path: String, filename: String) = this(new File(path + File.separator + filename))
    def this(filename: String) = this({
      val n = filename.lastIndexOf('/')
      if (n < 0)
        new File(new File(".").getCanonicalPath + File.separator + filename)
      else
        new File(filename.substring(0, n) + File.separator + filename.substring(n + 1))
    })
    val implementation: Interface = new Configgy {
      override protected val log = LoggerFactory.getLogger(getClass.getPackage().getName() + ".ConfiggyFromFile$$")
      def init() {
        log.debug("initialize " + this)
        try {
          log.debug("loading file {}", file)
          implementation.loadFile(file.getParent(), file.getName())
        } catch {
          case e: Throwable ⇒
            log.error("Failed to load config file '%s'".format(file.getAbsolutePath()), e)
            throw e
        }
      }
      def dispose() {
        log.debug("dispose " + this)
        implementation.unregisterWithJmx
      }
      override def toString = "default ConfiggyFromFile implementation"
    }
  }
  /**
   * Create a Config object from the given named resource inside this jar
   * file, using a specific class loader. "include" lines will also operate
   * on resource paths.
   */
  class DefaultInitFromResource(name: String, classLoader: ClassLoader) extends Init {
    def this(name: String) = this(name, ClassLoader.getSystemClassLoader)
    val implementation: Interface = new Configgy() {
      override protected val log = LoggerFactory.getLogger(getClass.getPackage().getName() + ".ConfiggyFromResource$$")
      def init() {
        log.debug("initialize " + this)
        try {
          log.debug("loading resource {} with class loader {}", name.asInstanceOf[Any], classLoader)
          implementation.importer = new ResourceImporter(classLoader)
          implementation.loadFile(name)
        } catch {
          case e: Throwable ⇒
            log.error("Failed to load config resource '%s'".format(name), e)
            throw e
        }
      }
      def dispose() {
        log.debug("dispose " + this)
        implementation.unregisterWithJmx
      }
      override def toString = "default ConfiggyFromResource implementation"
    }
  }
  /**
   * Create a Config object from a string containing a config file's contents.
   */
  class DefaultInitFromString(data: String) extends Init {
    val implementation: Interface = new Configgy() {
      override protected val log = LoggerFactory.getLogger(getClass.getPackage().getName() + ".ConfiggyFromString$$")
      def init() {
        log.debug("initialize " + this)
        implementation.load(data)
      }
      def dispose() {
        log.debug("dispose " + this)
        implementation.unregisterWithJmx
      }
      override def toString = "default ConfiggyFromString implementation"
    }
  }
  /**
   * Create a Config object from a map of String keys and String values.
   */
  class DefaultInitFromMap(m: Map[String, String]) extends Init {
    val implementation: Interface = new Configgy() {
      override protected val log = LoggerFactory.getLogger(getClass.getPackage().getName() + ".ConfiggyFromMap$$")
      def init() {
        log.debug("initialize " + this)
        for ((k, v) ← m.iterator) this(k) = v
      }
      def dispose() {
        log.debug("dispose " + this)
        implementation.unregisterWithJmx
      }
      override def toString = "default ConfiggyFromMap implementation"
    }
  }
}
