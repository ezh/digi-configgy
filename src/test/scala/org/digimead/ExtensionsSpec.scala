/**
 * Digi Configgy is a library for handling configurations
 *
 * Copyright 2009 Robey Pointer <robeypointer@gmail.com>
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

package org.digimead

import org.digimead.configgy.extensions.byteArrayToConfiggyByteArray
import org.digimead.configgy.extensions.stringToConfiggyString
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

class ExtensionsSpecextends extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  describe("An extensions") {
    it("quoteC") {
      config =>
        "nothing".quoteC should be("nothing")
        "name\tvalue\t\u20acb\u00fcllet?\u20ac".quoteC should be("name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac")
        "she said \"hello\"".quoteC should be("she said \\\"hello\\\"")
        "\\backslash".quoteC should be("\\\\backslash")
    }

    it("unquoteC") {
      config =>
        "nothing".unquoteC should be("nothing")
        "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac".unquoteC should be("name\tvalue\t\u20acb\u00fcllet?\u20ac")
        "she said \\\"hello\\\"".unquoteC should be("she said \"hello\"")
        "\\\\backslash".unquoteC should be("\\backslash")
        "real\\$dollar".unquoteC should be("real\\$dollar")
        "silly\\/quote".unquoteC should be("silly/quote")
    }

    it("hexlify") {
      config =>
        "hello".getBytes.slice(1, 4).hexlify should be("656c6c")
        "hello".getBytes.hexlify should be("68656c6c6f")
    }

    it("unhexlify") {
      config =>
        "656c6c".unhexlify.toList should be("hello".getBytes.slice(1, 4).toList)
        "68656c6c6f".unhexlify.toList should be("hello".getBytes.toList)
    }
  }
}
