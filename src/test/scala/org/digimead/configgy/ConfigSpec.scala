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

import java.io.{ File, FileOutputStream }
import java.lang.management.ManagementFactory
import javax.{ management ⇒ jmx }
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.lib.test.{ LoggingHelper, StorageHelper }
import org.scalatest.{ FunSpec, Matchers }

class ConfigSpec extends FunSpec with Matchers with StorageHelper with LoggingHelper with XLoggable {
  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    Schema.clear
    Configgy.clear
  }

  class FakeSubscriber extends Subscriber {
    def validate(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {}
    def commit(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {}
  }

  // remembers the before & after config nodes when committing a change
  class MemorySubscriber extends Subscriber {
    var used = false
    var savedCurrent: Option[ConfigMap] = None
    var savedReplacement: Option[ConfigMap] = None

    def validate(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {}
    def commit(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {
      used = true
      savedCurrent = current match {
        case None ⇒ None
        case Some(x) ⇒ Some(x.asInstanceOf[Attributes].copy)
      }
      savedReplacement = replacement
    }
  }

  // refuses any change to its node.
  class AngrySubscriber extends Subscriber {
    def validate(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = throw new ValidationException("no way!")
    def commit(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {}
  }

  describe("A Config") {
    it("should take subscriptions") {
      val c = (new Configgy.DefaultInit).implementation
      var id = c.subscribe("alpha.beta.gamma", new FakeSubscriber)

      c.debugSubscribers should be("subs=0 { alpha=0 { beta=0 { gamma=1 } } }")
      c.unsubscribe(id)
      c.debugSubscribers should be("subs=0 { alpha=0 { beta=0 { gamma=0 } } }")
      id = c.subscribe("alpha.beta") { (attr: Option[ConfigMap]) ⇒ Console.println("hello") }
      c.debugSubscribers should be("subs=0 { alpha=0 { beta=1 { gamma=0 } } }")
      c.unsubscribe(id)
      c.debugSubscribers should be("subs=0 { alpha=0 { beta=0 { gamma=0 } } }")
    }

    it("should call subscribers") {
      val c = (new Configgy.DefaultInit).implementation
      c("alpha.beta.gamma") = "hello"

      var checked = false
      c.subscribe("alpha.beta") { (attr: Option[ConfigMap]) ⇒ checked = true }
      checked should be(false)
      c("alpha.beta.delta") = "goodbye"
      checked should be(true)
    }

    it("should call subscribers with the old & new data") {
      val c = (new Configgy.DefaultInit).implementation
      c("alpha.beta.gamma") = "hello"

      val sub = new MemorySubscriber
      c.subscribe("alpha.beta", sub)
      sub.used should be(false)

      c("alpha.beta.delta") = "goodbye"
      sub.used should be(true)
      sub.savedCurrent.get.dump should be("{alpha.beta: gamma=\"hello\" }")
      sub.savedReplacement.get.dump should be("{alpha.beta: delta=\"goodbye\" gamma=\"hello\" }")
      c.dump should be("{: alpha={alpha: beta={alpha.beta: delta=\"goodbye\" gamma=\"hello\" } } }")

      c("alpha.beta.gamma") = "gutentag"
      sub.savedCurrent.get.dump should be("{alpha.beta: delta=\"goodbye\" gamma=\"hello\" }")
      sub.savedReplacement.get.dump should be("{alpha.beta: delta=\"goodbye\" gamma=\"gutentag\" }")
      c.dump should be("{: alpha={alpha: beta={alpha.beta: delta=\"goodbye\" gamma=\"gutentag\" } } }")
    }

    it("should abort a rejected change") {
      val c = (new Configgy.DefaultInit).implementation
      c("alpha.beta.gamma") = "hello"

      c.subscribe("alpha.beta", new AngrySubscriber)
      val thrown = the[ValidationException] thrownBy { c("alpha.beta.gamma") = "gutentag" }
      thrown.getMessage should equal("no way!")
      c("alpha.giraffe") = "tall!"
      c.dump should be("{: alpha={alpha: beta={alpha.beta: gamma=\"hello\" } giraffe=\"tall!\" } }")
    }

    it("should track changes to a ConfigMap that's tacked on") {
      val c = (new Configgy.DefaultInit).implementation
      val sub = new MemorySubscriber
      c.subscribe(sub)

      val hostsConfig = (new Configgy.DefaultInit).implementation
      c.setConfigMap("hosts", hostsConfig)
      sub.used should be(true)
      sub.used = false

      c.getConfigMap("hosts").get.setString("localhost", "awesome")
      sub.used should be(true)
    }

    it("should deal correctly with multiple subscribers at different nodes") {
      val c = (new Configgy.DefaultInit).implementation
      c("alpha.beta.gamma") = "hello"
      c("alpha.giraffe") = "tall!"
      c("forest.fires.are") = "bad"

      val rootsub = new MemorySubscriber
      c.subscribe(rootsub)
      val firesub = new AngrySubscriber
      c.subscribe("forest.fires", firesub)
      val betasub = new MemorySubscriber
      c.subscribe("alpha.beta", betasub)

      c("unrelated") = 39
      rootsub.used should be(true)
      betasub.used should be(false)
      rootsub.savedCurrent.get.dump should be(
        "{: alpha={alpha: beta={alpha.beta: gamma=\"hello\" } giraffe=\"tall!\" } forest={forest: fires={forest.fires: are=\"bad\" } } }")
      rootsub.savedReplacement.get.dump should be(
        "{: alpha={alpha: beta={alpha.beta: gamma=\"hello\" } giraffe=\"tall!\" } forest={forest: fires={forest.fires: are=\"bad\" } } unrelated=\"39\" }")

      rootsub.used = false
      c("forest.matches") = false
      rootsub.used should be(true)
      betasub.used should be(false)
      c.getConfigMap("forest").get.dump should be(
        "{forest: fires={forest.fires: are=\"bad\" } matches=\"false\" }")

      val thrown = the[ValidationException] thrownBy { c.remove("forest") }
      thrown.getMessage should equal("no way!")

      rootsub.used = false
      betasub.used = false
      c("alpha.beta.gamma") = "goodbye"
      rootsub.used should be(true)
      betasub.used should be(true)
      betasub.savedCurrent.get.dump should be("{alpha.beta: gamma=\"hello\" }")
      betasub.savedReplacement.get.dump should be("{alpha.beta: gamma=\"goodbye\" }")
    }

    it("should include relative files") {
      withTempFolder {
        folder ⇒
          val inner = new File(folder, "inner")
          inner.mkdir

          val data1 = "fruit = 17\ninclude \"inner/punch.conf\"\n"
          val f1 = new FileOutputStream(new File(folder, "fruit.conf"))
          f1.write(data1.getBytes)
          f1.close
          val data2 = "punch = 23\n"
          val f2 = new FileOutputStream(new File(inner, "punch.conf"))
          f2.write(data2.getBytes)
          f2.close

          val c = (new Configgy.DefaultInit).implementation
          c.loadFile(folder.getAbsolutePath(), "fruit.conf")
          c.dump should be("{: fruit=\"17\" punch=\"23\" }")
      }
    }

    it("should load a test resource as a sanity check") {
      getClass.getClassLoader.getResource("happy.conf") should not be (null)
    }

    it("should include from a resource") {
      val c = (new Configgy.DefaultInit).implementation
      c.importer = new ResourceImporter(getClass.getClassLoader)
      c.load("include \"happy.conf\"\n")
      c.dump should be("{: commie=\"501\" }")
    }

    it("should build from a map") {
      val c = (new Configgy.DefaultInitFromMap(Map("apples" -> "23", "oranges" -> "17", "fruit.misc" -> "x,y,z"))).implementation
      c.init
      c("apples") should be("23")
      c("oranges") should be("17")
      c("fruit.misc") should be("x,y,z")
      c.dump should be("{: apples=\"23\" fruit={fruit: misc=\"x,y,z\" } oranges=\"17\" }")
      c.configMap("fruit").getName should be("fruit")
    }

    it("should register jmx") {
      val c = (new Configgy.DefaultInitFromMap(Map("apples" -> "23", "oranges" -> "17", "fruit.misc" -> "x,y,z"))).implementation
      c.init
      c.registerWithJmx("com.example.test")
      val mbs = ManagementFactory.getPlatformMBeanServer()
      mbs.isRegistered(new jmx.ObjectName("com.example.test:type=Config,name=(root)")) should be(true)
      mbs.isRegistered(new jmx.ObjectName("com.example.test:type=Config,name=fruit")) should be(true)
      c("apples") should be("23")
      c("oranges") should be("17")
      c("fruit.misc") should be("x,y,z")
      mbs.getAttribute(new jmx.ObjectName("com.example.test:type=Config,name=(root)"), "apples") should be("23")
      mbs.getAttribute(new jmx.ObjectName("com.example.test:type=Config,name=(root)"), "oranges") should be("17")
      mbs.getAttribute(new jmx.ObjectName("com.example.test:type=Config,name=fruit"), "misc") should be("x,y,z")
    }

    it("should reload from string") {
      val c = (new Configgy.DefaultInitFromString("""apples="23" oranges="17" basket { apples = true oranges = false }""")).implementation
      c.init()
      c("apples") should be("23")
      c("oranges") should be("17")
      c("basket.apples", false) should be(true)
      c("basket.oranges", false) should be(false)
      c.setString("apples", "red")
      c.configMap("basket").setBool("apples", false)
      c("apples") should be("red")
      c("basket.apples", false) should be(false)
      c.reload()
      c("apples") should be("23")
      c("basket.apples", false) should be(true)
    }

    it("should reload from a resource") {
      val c = (new Configgy.DefaultInitFromResource("happy.conf", getClass.getClassLoader)).implementation
      c.init()
      c.getInt("commie") should be(Some(501))
      c.setInt("commie", 401)
      c.getInt("commie") should be(Some(401))
      c.reload()
      c.getInt("commie") should be(Some(501))
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) { adjustLoggingBeforeAll(configMap) }
}
