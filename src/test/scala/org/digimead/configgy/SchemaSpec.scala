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

import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.slf4j.Logger

class SchemaSpec extends FunSpec with ShouldMatchers with BeforeAndAfter with TestHelperLogging {
  type FixtureParam = Map[String, Any]
  implicit val log: Logger = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  before {
    Schema.clear
    Configgy.clear
  }

  describe("A Schema") {
    it("should assign optional element") {
      config =>
        val schemaEntity = Schema.Node[String](Seq("optional"), "some useless key", log.getName(), false)
        val entity = Schema.optional[String]("optional")("some useless key")
        Schema.entries.exists(_ == schemaEntity) should be(true)
        entity should be(schemaEntity)
    }
    it("should assign required element") {
      config =>
        val schemaEntity = Schema.Node[String](Seq("required"), "very important key", log.getName(), true)
        val entity = Schema.required[String]("required")("very important key")
        Schema.entries.exists(_ == schemaEntity) should be(true)
        entity should be(schemaEntity)
    }
    it("should check input arguments") {
      config =>
        val thrown1 = evaluating { Schema.Node[String](Seq(), "some useless key", log.getName(), false) } should produce[IllegalArgumentException]
        thrown1.getMessage should equal("Please provide key path for configgy value")
        val thrown2 = evaluating { Schema.required[Float]("required")("very important key") } should produce[IllegalArgumentException]
        thrown2.getMessage should equal("Unexpected Configgy value type float")
    }
    it("should dump") {
      config =>
        Schema.required[String]("test")("test key")
        val result = Schema.dump
        log.debug(result)
        result.split("\n") should have size (4)
        result should include("test[required, not exists] - test key by @~*~*~*~*")
    }
    it("should clear") {
      config =>
        Schema.required[String]("test")("test key")
        Schema.entries should not be ('empty)
        Schema.clear()
        Schema.entries should be('empty)
    }
    it("should validate") {
      config =>
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
      config =>
        val e = Schema.required[String]("test1", "test2", "test")("test key")
        Schema("test1", "test2", "test") should be(Some(e))
    }
    // original write 1.63s for 1000000
    // schema write 1.36s for 1000000
    it("should be fast enough") {
      config =>
        var time = 0L

        time = System.currentTimeMillis
        for (i <- 0 to 1000000)
          Configgy("test1.test2.test3") = "test" + i
        val totalWriteOrig = System.currentTimeMillis - time
        System.err.println("original write total: " + totalWriteOrig + "ms")
        Configgy.clear
        val e = Schema.required[String]("test1", "test2", "test3")("other test key")
        time = System.currentTimeMillis
        for (i <- 0 to 1000000)
          e := "test" + i
        val totalWriteViaSchema = System.currentTimeMillis - time
        System.err.println("schema write total: " + totalWriteViaSchema + "ms")
        assert(totalWriteOrig > totalWriteViaSchema)
    }
    describe("should handle elements") {
      it("at root level") {
        config =>
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
        config =>
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
          val thrown = evaluating { e2 := "aaa" } should produce[ConfigException]
          thrown.getMessage should equal("Illegal key test")
      }
    }
  }
  it("should detect exists/not exists elements") {
    config =>
      val e = Schema.required[String]("test")("test key")
      e.exists should be(false)
      val result = Schema.dump
      log.debug(result)
      result.split("\n") should have size (4)
      result should include("test[required, not exists] - test key by @~*~*~*~*")
      e := "ABC"
      Schema.dump should include("test[required, exists] - test key by @~*~*~*~*")
  }
}
