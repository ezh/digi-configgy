/**
 * Digi Configgy is a library for handling configurations
 *
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

import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.lib.test.LoggingHelper
import org.scalatest.{ FunSpec, Matchers }

class SchemaSpec extends FunSpec with Matchers with LoggingHelper with XLoggable {
  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    Schema.clear
    Configgy.clear
  }

  describe("A Schema") {
    it("should assign optional element") {
      val schemaEntity = Schema.Node[String](List("optional"), "some useless key", log.getName(), false)(() ⇒ None)
      val entity = Schema.optional[String]("optional")("some useless key")
      Schema.entries.exists(_ == schemaEntity) should be(true)
      entity should be(schemaEntity)
    }
    it("should assign required element") {
      val schemaEntity = Schema.Node[String](Seq("required"), "very important key", log.getName(), true)(() ⇒ None)
      val entity = Schema.required[String]("required")("very important key")
      Schema.entries.exists(_ == schemaEntity) should be(true)
      entity should be(schemaEntity)
    }
    it("should check input arguments") {
      val thrown1 = the[IllegalArgumentException] thrownBy { Schema.Node[String](Seq(), "some useless key", log.getName(), false)(() ⇒ None) }
      thrown1.getMessage should equal("Please provide key path for configgy value")
      val thrown2 = the[IllegalArgumentException] thrownBy { Schema.required[Float]("required")("very important key") }
      thrown2.getMessage should equal("Unexpected Configgy value type float")
    }
    it("should dump") {
      Schema.required[String]("test")("test key")
      val result = Schema.dump
      log.debug(result)
      result.split("\n") should have size (4)
      result should include("test[required, String, not exists] - test key by @configgy.SchemaSpec")
    }
    it("should clear") {
      Schema.required[String]("test")("test key")
      Schema.entries should not be ('empty)
      Schema.clear()
      Schema.entries should be('empty)
    }
    it("should validate") {
      Schema.optional[String]("test1", "test2", "test")("test key")
      Schema.validate() should be('empty)
      Schema.required[String]("test1", "test2", "test3")("other test key")
      val failed = Schema.validate()
      failed should not be ('empty)
      failed should have size (1)
      failed(0).key should be("test3")
      failed(0).getName should be("test1.test2.test3")
      Schema.validate(Configgy.configMap("a")) should be('empty)
      Schema.validate(Configgy.configMap("test1.test2")) should not be ('empty)
    }
    it("should return element by path") {
      val e = Schema.required[String]("test1", "test2", "test")("test key")
      Schema("test1", "test2", "test") should be(Some(e))
    }
    // original write 1.63s for 1000000
    // schema write 1.36s for 1000000
    it("should be fast enough", Tag.Optional) {
      var time = 0L

      time = System.currentTimeMillis
      for (i ← 0 to 1000000)
        Configgy("test1.test2.test3") = "test" + i
      val totalWriteOrig = System.currentTimeMillis - time
      System.err.println("original write total: " + totalWriteOrig + "ms")
      Configgy.clear
      val e = Schema.required[String]("test1", "test2", "test3")("other test key")
      time = System.currentTimeMillis
      for (i ← 0 to 1000000)
        e := "test" + i
      val totalWriteViaSchema = System.currentTimeMillis - time
      System.err.println("schema write total: " + totalWriteViaSchema + "ms")
      assert(totalWriteOrig > totalWriteViaSchema)
    }
    describe("should handle elements") {
      it("at root level") {
        val e = Schema.required[String]("test")("test key")
        e.getConfigMap should be(Some(Configgy.getImplementation()))
        e.exists should be(false)
        e.get should be(None)
        e.remove should be(false)
        Configgy("test") = "abc"
        e.exists should be(true)
        e.get should be(Some("abc"))
        e.remove should be(true)
        Configgy.contains("test") should be(false)
        e.set("cde")
        e.get should be(Some("cde"))
        Configgy("test") should be("cde")
        e := "111"
        e.get should be(Some("111"))
        Configgy("test") should be("111")
      }
      it("at nested level") {
        val e = Schema.required[String]("testBase", "test")("test key")
        e.getConfigMap should be(None)
        e.exists should be(false)
        e.get should be(None)
        e.remove should be(false)
        Configgy("testBase.test") = "abc"
        e.exists should be(true)
        e.get should be(Some("abc"))
        e.remove should be(true)
        Configgy.contains("testBase.test") should be(false)
        e.set("cde")
        e.get should be(Some("cde"))
        Configgy("testBase.test") should be("cde")
        e := "111"
        e.get should be(Some("111"))
        Configgy("testBase.test") should be("111")
        Configgy.contains("testBase") should be(true)
        Configgy.remove("testBase") should be(true)
        Configgy.contains("testBase") should be(false)
        e.set("123")
        Configgy("testBase.test") should be("123")
        val e2 = Schema.required[String]("testBase", "test", "other")("other test key")
        val thrown = the[ConfigException] thrownBy { e2 := "aaa" }
        thrown.getMessage should equal("Illegal key test")
      }
    }
  }
  it("should detect exists/not exists elements") {
    val e = Schema.required[String]("test")("test key")
    e.exists should be(false)
    val result = Schema.dump
    log.debug(result)
    result.split("\n") should have size (4)
    result should include("test[required, String, not exists] - test key by @configgy.SchemaSpec")
    e := "ABC"
    Schema.dump should include("test[required, String, exists] - test key by @configgy.SchemaSpec")
  }
  describe("default argument") {
    it("should be lazy") {
      var touch = ""
      val e = Schema.required[String]("test")("test key", { touch = "yes"; Some(touch) })
      assert(touch === "")
      assert(e.get === Some("yes"))
      assert(touch === "yes")
      assert(Configgy("test") === "yes")
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) { adjustLoggingBeforeAll(configMap) }
}
