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

import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.lib.test.LoggingHelper
import org.scalatest.{ FunSpec, Matchers }
import scala.Array.canBuildFrom
import scala.util.Sorting

class AttributesSpec extends FunSpec with Matchers with LoggingHelper with XLoggable {
  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    Schema.clear
    Configgy.clear
  }

  describe("An attributes") {
    it("should set values") {
      val s = new Attributes(null, "root")
      s.dump should be("{root: }")
      s.setString("name", "Communist")
      s.dump should be("{root: name=\"Communist\" }")
      s.setInt("age", 8)
      s.dump should be("{root: age=\"8\" name=\"Communist\" }")
      s.setInt("age", 19)
      s.dump should be("{root: age=\"19\" name=\"Communist\" }")
      s.setBool("sleepy", true)
      s.dump should be("{root: age=\"19\" name=\"Communist\" sleepy=\"true\" }")

      // try both APIs.
      val s2 = new Attributes(null, "root")
      s2.dump should be("{root: }")
      s2("name") = "Communist"
      s2.dump should be("{root: name=\"Communist\" }")
      s2("age") = 8
      s2.dump should be("{root: age=\"8\" name=\"Communist\" }")
      s2("age") = 19
      s2.dump should be("{root: age=\"19\" name=\"Communist\" }")
      s2("sleepy") = true
      s2.dump should be("{root: age=\"19\" name=\"Communist\" sleepy=\"true\" }")
    }
    it("should get values") {
      val s = new Attributes(null, "root")
      s("name") = "Communist"
      s("age") = 8
      s("sleepy") = true
      s("money") = 1900500400300L
      s.getString("name", "") should be("Communist")
      s.getInt("age", 999) should be(8)
      s.getInt("unknown", 500) should be(500)
      s.getLong("money", 0) should be(1900500400300L)
      s("name") should be("Communist")
      s("age", null) should be("8")
      s("age", "500") should be("8")
      s("age", 500) should be(8)
      s("unknown", "500") should be("500")
      s("money", 0L) should be(1900500400300L)
      s("money").toLong should be(1900500400300L)
      s("age").toInt should be(8)
      s("sleepy").toBoolean should be(true)
    }
    it("should case-preserve keys in get/set") {
      val s = new Attributes(null, "")
      s("Name") = "Communist"
      s("AGE") = 8
      s("Name") should be("Communist")
      s("naME", "") should be("")
      s("age", 0) should be(0)
      s("AGE") should be("8")
    }
    it("should set compound values") {
      val s = new Attributes(null, "")
      s("name") = "Communist"
      s("age") = 8
      s("disposition") = "fighter"
      s("diet.food") = "Meow Mix"
      s("diet.liquid") = "water"
      s("data") = "\r\r\u00ff\u00ff"
      s.dump should be("{: age=\"8\" data=\"\\r\\r\\xff\\xff\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } " +
        "disposition=\"fighter\" name=\"Communist\" }")
    }
    it("should know what it contains") {
      val s = new Attributes(null, "")
      s("name") = "Communist"
      s("age") = 8
      s("diet.food") = "Meow Mix"
      s("diet.liquid") = "water"
      s.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")
      s.contains("age") should be(true)
      s.contains("unknown") should be(false)
      s.contains("diet.food") should be(true)
      s.contains("diet.gas") should be(false)
      s.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")
    }
    it("should auto-vivify") {
      val s = new Attributes(null, "")
      s("a.b.c") = 8
      s.dump should be("{: a={a: b={a.b: c=\"8\" } } }")
      s.getString("a.d.x") should be(None)
      // shouldn't have changed the attr map:
      s.dump should be("{: a={a: b={a.b: c=\"8\" } } }")
    }
    it("should compare with ==") {
      val s = new Attributes(null, "root")
      s("name") = "Communist"
      s("age") = 8
      s("diet.food.dry") = "Meow Mix"
      val t = new Attributes(null, "root")
      t("name") = "Communist"
      t("age") = 8
      t("diet.food.dry") = "Meow Mix"
      s should be(t)
    }
    it("should remove values") {
      val s = new Attributes(null, "")
      s("name") = "Communist"
      s("age") = 8
      s("diet.food") = "Meow Mix"
      s("diet.liquid") = "water"
      s.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")
      s.remove("diet.food") should be(true)
      s.remove("diet.food") should be(false)
      s.dump should be("{: age=\"8\" diet={diet: liquid=\"water\" } name=\"Communist\" }")
    }
    it("should convert to a map") {
      val s = new Attributes(null, "")
      s("name") = "Communist"
      s("age") = 8
      s("disposition") = "fighter"
      s("diet.food") = "Meow Mix"
      s("diet.liquid") = "water"
      val map = s.asMap

      // turn it into a sorted list, so we get a deterministic answer
      val keyList = map.keys.toList.toArray
      Sorting.quickSort(keyList)
      (for (k ← keyList) yield (k + "=" + map(k))).mkString("{ ", ", ", " }") should be(
        "{ age=8, diet.food=Meow Mix, diet.liquid=water, disposition=fighter, name=Communist }")
    }
    it("should copy") {
      val s = new Attributes(null, "")
      s("name") = "Communist"
      s("age") = 8
      s("diet.food") = "Meow Mix"
      s("diet.liquid") = "water"
      val t = s.copy()

      s.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")
      t.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")

      s("diet.food") = "fish"

      s.dump should be("{: age=\"8\" diet={diet: food=\"fish\" liquid=\"water\" } name=\"Communist\" }")
      t.dump should be("{: age=\"8\" diet={diet: food=\"Meow Mix\" liquid=\"water\" } name=\"Communist\" }")
    }
    it("should copy with inheritance") {
      val s = new Attributes(null, "s")
      s("name") = "Communist"
      s("age") = 1
      val t = new Attributes(null, "t")
      t("age") = 8
      t("disposition") = "hungry"
      t.inheritFrom = Some(s)

      val x = t.copy()
      t.dump should be("{t (inherit=s): age=\"8\" disposition=\"hungry\" }")
      x.dump should be("{t: age=\"8\" disposition=\"hungry\" name=\"Communist\" }")
    }
    it("should find lists") {
      val s = new Attributes(null, "")
      s("port") = 6667
      s("hosts") = List("localhost", "skunk.example.com")
      s.getList("hosts").toList should be(List("localhost", "skunk.example.com"))
      s.getList("non-hosts").toList should be(Nil)
    }
    it("should add a nested ConfigMap") {
      val s = new Attributes(null, "")
      val sub = new Attributes(null, "")
      s("name") = "Sparky"
      sub("name") = "Muffy"
      s.setConfigMap("dog", sub)
      s.dump should be("{: dog={dog: name=\"Muffy\" } name=\"Sparky\" }")
      sub("age") = 10
      s.dump should be("{: dog={dog: name=\"Muffy\" } name=\"Sparky\" }")
    }
    it("should toConfigString") {
      val s = new Attributes(null, "")
      s("name") = "Sparky"
      s("age") = "10"
      s("diet") = "poor"
      s("muffy.name") = "Muffy"
      s("muffy.age") = "11"
      s("fido.name") = "Fido"
      s("fido.age") = "5"
      s("fido.roger.name") = "Roger"
      s.configMap("fido.roger").inheritFrom = Some(s.configMap("muffy"))

      val expected = """age = "10"
diet = "poor"
fido {
  age = "5"
  name = "Fido"
  roger (inherit="muffy") {
    name = "Roger"
  }
}
muffy {
  age = "11"
  name = "Muffy"
}
name = "Sparky"
"""
      s.toConfigString should be(expected)
    }
    it("should copyInto") {
      val s = new Attributes(null, "")
      s("name") = "Sparky"
      s("age") = "10"
      s("unused") = "nothing"
      s("longish") = "900"
      s("boolish") = "true"
      s("doublish") = "2.5"
      s("floatish") = "8.75"

      case class Person(var name: String, var age: Int, var weight: Int, var longish: Long)
      val obj = new Person("", 0, 0, 0L)
      s.copyInto(obj)
      obj should be(new Person("Sparky", 10, 0, 900L))

      case class Other(var boolish: Boolean, var doublish: Double, var floatish: Float)
      val obj2 = new Other(false, 0.0, 0.0f)
      s.copyInto(obj2)
      obj2 should be(new Other(true, 2.5, 8.75f))
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) { adjustLoggingBeforeAll(configMap) }
}
