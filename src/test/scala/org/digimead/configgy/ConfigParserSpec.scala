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

import org.digimead.configgy.Configgy.getImplementation
import org.digimead.configgy.extensions.stringToConfiggyString
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.LoggingHelper
import org.digimead.lib.test.StorageHelper
import org.scalatest.FunSpec
import org.scalatest.Matchers

class ConfigParserSpec extends FunSpec with Matchers with StorageHelper with LoggingHelper with Loggable {
  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    Schema.clear
    Configgy.clear
  }

  class FakeImporter extends Importer {
    def importFile(filename: String, required: Boolean): String = {
      filename match {
        case "test1" ⇒
          "staff = \"weird skull\"\n"
        case "test2" ⇒
          "<inner>\n" +
            "    cat=\"meow\"\n" +
            "    include \"test3\"\n" +
            "    dog ?= \"blah\"\n" +
            "</inner>"
        case "test3" ⇒
          "dog=\"bark\"\n" +
            "cat ?= \"blah\"\n"
        case "test4" ⇒
          "cow=\"moo\"\n"
        case "test5" ⇒
          if (!required) "" else throw new ParseException("File not found")
      }
    }
  }

  def parse(in: String) = {
    val attr = (new Configgy.DefaultInit).implementation
    attr.importer = new FakeImporter
    attr.load(in)
    attr
  }

  describe("ConfigParser") {
    it("should parse assignment") {
      parse("weight = 48").dump should be("{: weight=\"48\" }")
    }

    it("should parse conditional assignment") {
      parse("weight = 48\n weight ?= 16").dump should be("{: weight=\"48\" }")
    }

    it("should ignore comments") {
      parse("# doing stuff\n  weight = 48\n  # more comments\n").dump should be("{: weight=\"48\" }")
    }

    it("should parse booleans") {
      parse("wine off\nwhiskey on\n").dump should be("{: whiskey=\"true\" wine=\"false\" }")
      parse("wine = false\nwhiskey = on\n").dump should be("{: whiskey=\"true\" wine=\"false\" }")
    }

    it("should handle nested blocks") {
      parse("alpha=\"hello\"\n<beta>\n    gamma=23\n</beta>").dump should be("{: alpha=\"hello\" beta={beta: gamma=\"23\" } }")
      parse("alpha=\"hello\"\n<beta>\n    gamma=23\n    toaster on\n</beta>").dump should be(
        "{: alpha=\"hello\" beta={beta: gamma=\"23\" toaster=\"true\" } }")
    }

    it("should handle nested blocks in braces") {
      parse("alpha=\"hello\"\nbeta {\n    gamma=23\n}").dump should be(
        "{: alpha=\"hello\" beta={beta: gamma=\"23\" } }")
      parse("alpha=\"hello\"\nbeta {\n    gamma=23\n    toaster on\n}").dump should be(
        "{: alpha=\"hello\" beta={beta: gamma=\"23\" toaster=\"true\" } }")
    }

    describe("should handle string lists") {
      it("normal") {
        val data2 = "cats = [\"Commie\", \"Buttons\", \"Sockington\"]"
        val b = parse(data2)
        b.getList("cats").toList should be(List("Commie", "Buttons", "Sockington"))
        b.getList("cats")(0) should be("Commie")

        val data =
          "<home>\n" +
            "    states = [\"California\", \"Tennessee\", \"Idaho\"]\n" +
            "    regions = [\"pacific\", \"southeast\", \"northwest\"]\n" +
            "</home>\n"
        val a = parse(data)
        a.dump should be("{: home={home: regions=[pacific,southeast,northwest] states=[California,Tennessee,Idaho] } }")
        a.getList("home.states").toList.mkString(",") should be("California,Tennessee,Idaho")
        a.getList("home.states")(0) should be("California")
        a.getList("home.regions")(1) should be("southeast")
      }
      it("without comma separators") {
        val data2 = "cats = [\"Commie\" \"Buttons\" \"Sockington\"]"
        val b = parse(data2)
        b.getList("cats").toList should be(List("Commie", "Buttons", "Sockington"))
        b.getList("cats")(0) should be("Commie")
      }
      it("with a trailing comma") {
        val data2 = "cats = [\"Commie\", \"Buttons\", \"Sockington\",]"
        val b = parse(data2)
        b.getList("cats").toList should be(List("Commie", "Buttons", "Sockington"))
        b.getList("cats")(0) should be("Commie")
      }
    }
  }

  it("should handle camelCase lists") {
    val data =
      "<daemon>\n" +
        "    useLess = [\"one\",\"two\"]\n" +
        "</daemon>\n"
    val a = parse(data)
    a.getList("daemon.useLess").toList should be(List("one", "two"))
  }

  it("should handle lists with numbers") {
    val data = "ports = [ 9940, 9941, 9942 ]\n"
    val a = parse(data)
    a.dump should be("{: ports=[9940,9941,9942] }")
    a.getList("ports").toList should be(List("9940", "9941", "9942"))
  }

  it("should import files") {
    val data1 =
      "toplevel=\"skeletor\"\n" +
        "<inner>\n" +
        "    include \"test1\"\n" +
        "    home = \"greyskull\"\n" +
        "</inner>\n"
    parse(data1).dump should be("{: inner={inner: home=\"greyskull\" staff=\"weird skull\" } toplevel=\"skeletor\" }")

    val data2 =
      "toplevel=\"hat\"\n" +
        "include \"test2\"\n" +
        "include \"test4\"\n"
    parse(data2).dump should be("{: cow=\"moo\" inner={inner: cat=\"meow\" dog=\"bark\" } toplevel=\"hat\" }")
  }

  it("should import optional files") {
    val data1 =
      "toplevel=\"skeletor\"\n" +
        "<inner>\n" +
        "    include? \"test1\"\n" +
        "    home = \"greyskull\"\n" +
        "</inner>\n"
    parse(data1).dump should be("{: inner={inner: home=\"greyskull\" staff=\"weird skull\" } toplevel=\"skeletor\" }")

    val data2 =
      "toplevel=\"hat\"\n" +
        "include? \"test2\"\n" +
        "include \"test4\"\n" +
        "include? \"test5\"\n"
    parse(data2).dump should be("{: cow=\"moo\" inner={inner: cat=\"meow\" dog=\"bark\" } toplevel=\"hat\" }")
  }

  it("should throw an exception when importing non-existent file") {
    val data1 = "include \"test5\"\n"
    val thrown = the[ParseException] thrownBy { parse(data1) }
    thrown.getMessage should equal("File not found")
  }

  it("should ignore optionally imported non-existent file") {
    val data1 = "include? \"test5\"\n"
    parse(data1).dump should be("{: }")
  }

  it("should refuse to overload key types") {
    val data =
      "cat = 23\n" +
        "<cat>\n" +
        "    dog = 1\n" +
        "</cat>\n"
    val thrown = the[ConfigException] thrownBy { parse(data) }
    thrown.getMessage should equal("Illegal key cat")
  }

  it("should catch unknown block modifiers") {
    val thrown = the[ParseException] thrownBy { parse("<upp name=\"fred\">\n</upp>\n") }
    thrown.getMessage should equal("Unknown block modifier")
  }

  it("should handle an outer scope after a closed block") {
    val data =
      "alpha = 17\n" +
        "<inner>\n" +
        "    name = \"foo\"\n" +
        "    <further>\n" +
        "        age = 500\n" +
        "    </further>\n" +
        "    zipcode = 99999\n" +
        "</inner>\n" +
        "beta = 19\n"
    parse(data).dump should be("{: alpha=\"17\" beta=\"19\" inner={inner: further={inner.further: age=\"500\" } name=\"foo\" zipcode=\"99999\" } }")
  }

  it("should allow whole numbers to be identifiers") {
    parse("1 = 2").dump should be("{: 1=\"2\" }")
    parse("1 = 2\n 3 = 4").dump should be("{: 1=\"2\" 3=\"4\" }")
    parse("20 = 1").dump should be("{: 20=\"1\" }")
    parse("2 = \"skeletor\"").dump should be("{: 2=\"skeletor\" }")
    parse("4 = \"hostname:1234\"").dump should be("{: 4=\"hostname:1234\" }")
    parse("""4 = ["a", "b"]""").dump should be("""{: 4=[a,b] }""")
  }

  describe("ConfigParser interpolation") {
    it("should interpolate strings") {
      parse("horse=\"ed\" word=\"sch$(horse)ule\"").dump should be(
        "{: horse=\"ed\" word=\"schedule\" }")
      parse("lastname=\"Columbo\" firstname=\"Bob\" fullname=\"$(firstname) $(lastname)\"").dump should be(
        "{: firstname=\"Bob\" fullname=\"Bob Columbo\" lastname=\"Columbo\" }")
    }

    it("should not interpolate unassigned strings") {
      parse("horse=\"ed\" word=\"sch\\$(horse)ule\"").dump should be("{: horse=\"ed\" word=\"sch$(horse)ule\" }")
    }

    it("should interpolate nested references") {
      parse("horse=\"ed\"\n" +
        "<alpha>\n" +
        "    horse=\"frank\"\n" +
        "    drink=\"$(horse)ly\"\n" +
        "    <beta>\n" +
        "        word=\"sch$(horse)ule\"\n" +
        "        greeting=\"$(alpha.drink) yours\"\n" +
        "    </beta>\n" +
        "</alpha>").dump should be(
        "{: alpha={alpha: beta={alpha.beta: greeting=\"frankly yours\" word=\"schedule\" } drink=\"frankly\" horse=\"frank\" } horse=\"ed\" }")
    }

    it("interpolate environment vars") {
      parse("user=\"$(USER)\"").dump should not be ("{: user=\"$(USER)\" }")
    }

    it("should interpolate properties") {
      val value = System.getProperty("java.home")
      parse("java_home=\"$(java.home)\"").dump should be("{: java_home=\"" + value.quoteC + "\" }")
    }

    it("should properties have precedence over environment") {
      // find an environment variable that exists
      val iter = System.getenv.entrySet.iterator
      iter.hasNext should be(true)
      val entry = iter.next
      val key = entry.getKey
      val value1 = entry.getValue
      val value2 = value1 + "-test"

      val orig_prop = System.getProperty(key)
      System.clearProperty(key)

      try {
        parse("v=\"$(" + key + ")\"").dump should be("{: v=\"" + value1.quoteC + "\" }")
        System.setProperty(key, value2)
        parse("v=\"$(" + key + ")\"").dump should be("{: v=\"" + value2.quoteC + "\" }")
      } finally {
        if (orig_prop eq null)
          System.clearProperty(key)
        else
          System.setProperty(key, orig_prop)
      }

      parse("v=\"$(" + key + ")\"").dump should be("{: v=\"" + value1.quoteC + "\" }")
    }
  }

  describe("ConfigParser inheritance") {
    it("should inherit") {
      val data =
        "<daemon>\n" +
          "    ulimit_fd = 32768\n" +
          "    uid = 16\n" +
          "</daemon>\n" +
          "\n" +
          "<upp inherit=\"daemon\">\n" +
          "    uid = 23\n" +
          "</upp>\n"
      val a = parse(data)
      a.dump should be("{: daemon={daemon: uid=\"16\" ulimit_fd=\"32768\" } upp={upp (inherit=daemon): uid=\"23\" } }")
      a.getString("upp.ulimit_fd", "9") should be("32768")
      a.getString("upp.uid", "100") should be("23")
    }

    it("should inherit using braces") {
      val data =
        "daemon {\n" +
          "    ulimit_fd = 32768\n" +
          "    uid = 16\n" +
          "}\n" +
          "\n" +
          "upp (inherit=\"daemon\") {\n" +
          "    uid = 23\n" +
          "}\n"
      val a = parse(data)
      a.dump should be("{: daemon={daemon: uid=\"16\" ulimit_fd=\"32768\" } upp={upp (inherit=daemon): uid=\"23\" } }")
      a.getString("upp.ulimit_fd", "9") should be("32768")
      a.getString("upp.uid", "100") should be("23")
    }

    it("should use parent scope for lookups") {
      val data =
        "<daemon><inner>\n" +
          "  <common>\n" +
          "    ulimit_fd = 32768\n" +
          "    uid = 16\n" +
          "  </common>\n" +
          "  <upp inherit=\"common\">\n" +
          "    uid = 23\n" +
          "  </upp>\n" +
          "  <slac inherit=\"daemon.inner.common\">\n" +
          "  </slac>\n" +
          "</inner></daemon>\n"
      val a = parse(data)
      a.dump should be("{: daemon={daemon: inner={daemon.inner: common={daemon.inner.common: uid=\"16\" ulimit_fd=\"32768\" } " +
        "slac={daemon.inner.slac (inherit=daemon.inner.common): } upp={daemon.inner.upp (inherit=daemon.inner.common): uid=\"23\" } } } }")
      a.getString("daemon.inner.upp.ulimit_fd", "9") should be("32768")
      a.getString("daemon.inner.upp.uid", "100") should be("23")
      a.getString("daemon.inner.slac.uid", "100") should be("16")
    }

    it("should handle camel case id in block") {
      val data =
        "<daemon>\n" +
          "    useLess = 3\n" +
          "</daemon>\n"
      val exp =
        "{: daemon={daemon: useLess=\"3\" } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.useLess", "14") should be("3")
    }

    it("should handle dash block") {
      val data =
        "<daemon>\n" +
          "    <base-dat>\n" +
          "        ulimit_fd = 32768\n" +
          "    </base-dat>\n" +
          "</daemon>\n"
      val exp =
        "{: daemon={daemon: base-dat={daemon.base-dat: ulimit_fd=\"32768\" } } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.base-dat.ulimit_fd", "14") should be("32768")
    }

    it("should handle camelcase block") {
      val data =
        "<daemon>\n" +
          "    <baseDat>\n" +
          "        ulimit_fd = 32768\n" +
          "    </baseDat>\n" +
          "</daemon>\n"
      val exp =
        "{: daemon={daemon: baseDat={daemon.baseDat: ulimit_fd=\"32768\" } } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.baseDat.ulimit_fd", "14") should be("32768")
    }

    it("should handle assignment after block") {
      val data =
        "<daemon>\n" +
          "    <base>\n" +
          "        ulimit_fd = 32768\n" +
          "    </base>\n" +
          "    useless = 3\n" +
          "</daemon>\n"
      val exp =
        "{: daemon={daemon: base={daemon.base: ulimit_fd=\"32768\" } useless=\"3\" } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.useless", "14") should be("3")
      a.getString("daemon.base.ulimit_fd", "14") should be("32768")
    }

    it("should two consecutive groups") {
      val data =
        "<daemon>\n" +
          "    useless = 3\n" +
          "</daemon>\n" +
          "\n" +
          "<upp inherit=\"daemon\">\n" +
          "    uid = 16\n" +
          "</upp>\n"
      val exp =
        "{: daemon={daemon: useless=\"3\" } " +
          "upp={upp (inherit=daemon): uid=\"16\" } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.useless", "14") should be("3")
      a.getString("upp.uid", "1") should be("16")
    }

    it("should handle a complex case") {
      val data =
        "<daemon>\n" +
          "    useLess = 3\n" +
          "    <base-dat>\n" +
          "        ulimit_fd = 32768\n" +
          "    </base-dat>\n" +
          "</daemon>\n" +
          "\n" +
          "<upp inherit=\"daemon.base-dat\">\n" +
          "    uid = 16\n" +
          "    <alpha inherit=\"upp\">\n" +
          "        name=\"alpha\"\n" +
          "    </alpha>\n" +
          "    <beta inherit=\"daemon\">\n" +
          "        name=\"beta\"\n" +
          "    </beta>\n" +
          "    someInt=1\n" +
          "</upp>\n"
      val exp =
        "{: daemon={daemon: base-dat={daemon.base-dat: ulimit_fd=\"32768\" } useLess=\"3\" } " +
          "upp={upp (inherit=daemon.base-dat): alpha={upp.alpha (inherit=upp): name=\"alpha\" } " +
          "beta={upp.beta (inherit=daemon): name=\"beta\" } someInt=\"1\" uid=\"16\" } }"
      val a = parse(data)
      a.dump should be(exp)
      a.getString("daemon.useLess", "14") should be("3")
      a.getString("upp.uid", "1") should be("16")
      a.getString("upp.ulimit_fd", "1024") should be("32768")
      a.getString("upp.name", "23") should be("23")
      a.getString("upp.alpha.name", "") should be("alpha")
      a.getString("upp.beta.name", "") should be("beta")
      a.getString("upp.alpha.ulimit_fd", "") should be("32768")
      a.getString("upp.beta.useLess", "") should be("3")
      a.getString("upp.alpha.useLess", "") should be("")
      a.getString("upp.beta.ulimit_fd", "") should be("")
      a.getString("upp.someInt", "4") should be("1")
    }

    it("inherit should apply explicitly") {
      val data =
        "sanfrancisco {\n" +
          "  beer {\n" +
          "    racer5 = 10\n" +
          "  }\n" +
          "}\n" +
          "\n" +
          "atlanta (inherit=\"sanfrancisco\") {\n" +
          "  beer (inherit=\"sanfrancisco.beer\") {\n" +
          "    redbrick = 9\n" +
          "  }\n" +
          "}\n"
      val a = parse(data)
      a.configMap("sanfrancisco").inheritFrom should be(None)
      a.configMap("sanfrancisco.beer").inheritFrom should be(None)
      a.configMap("atlanta").inheritFrom.get.dump should include("sanfrancisco")
      a.configMap("atlanta.beer").inheritFrom.get.dump should include("sanfrancisco.beer")
      a.getString("sanfrancisco.beer.deathandtaxes.etc") should be(None)
    }

    it("shouldn't choke on poundsign in strings") {
      val data =
        "irc {\n" +
          "  channel = \"#turtle\"\n" +
          "  server = \"irc.example.com\"\n" +
          "  username = \"user\"\n" +
          "  password = \"pass\"\n" +
          "}\n"
      val a = parse(data)
      a("irc.channel") should be("#turtle")
      a("irc.server") should be("irc.example.com")
    }
  }

  it("should read a really long list of strings without recursing to death") {
    val c = (new Configgy.DefaultInit).implementation
    c.importer = new ResourceImporter(getClass.getClassLoader)
    c.load("include \"evil.conf\"\n")
    c.getList("things").size should be(1000)
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) { adjustLoggingBeforeAll(configMap) }
}
