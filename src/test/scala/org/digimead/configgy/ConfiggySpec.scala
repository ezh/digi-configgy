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

import java.io.File
import java.io.FileOutputStream

import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.lib.test.TestHelperLogging
import org.digimead.lib.test.TestHelperStorage
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

class ConfiggySpec extends FunSpec with ShouldMatchers with BeforeAndAfter with TestHelperLogging with TestHelperStorage {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(defaultConfig(test.configMap), { Logging })
    withLogging(test.configMap) {
      Configgy
      test(test.configMap)
    }
  }

  before {
    //Logging.reset()
  }

  describe("A Configgy") {
    it("should load a simple config file") {
      config =>
        withTempFolder {
          folder =>
            val data1 =
              "name=\"Nibbler\"\n" +
                "\n" +
                "<log>\n" +
                "    filename=\"" + folder + "/test.log\"\n" +
                "    level=\"WARNING\"\n" +
                "</log>\n"
            writeConfigFile(folder, "test.conf", data1)

            Configgy.setup(new Configgy.DefaultInitFromFile(folder.getAbsolutePath(), "test.conf"))

            // verify the config file got loaded:
            Configgy.apply("name") should be("Nibbler")
        }
    }
  }

  it("should reload") {
    config =>
      withTempFolder {
        folder =>
          val data1 =
            "<robot>\n" +
              "    name=\"Nibbler\"\n" +
              "    age = 23002\n" +
              "</robot>\n" +
              "<unchanged>\n" +
              "    stuff = 0\n" +
              "</unchanged>\n"
          writeConfigFile(folder, "test.conf", data1)

          Configgy.setup(new Configgy.DefaultInitFromFile(folder.getAbsolutePath(), "test.conf"))

          Configgy.getInt("robot.age", 0) should be(23002)

          var checked = false
          var checkedAlso = false
          Configgy.subscribe("robot") { (attr: Option[ConfigMap]) => checked = true }
          Configgy.subscribe("unchanged") { (attr: Option[ConfigMap]) => checkedAlso = true }
          checked should be(false)

          val data2 =
            "<robot>\n" +
              "    name=\"Nibbler\"\n" +
              "    age = 23003\n" +
              "</robot>\n" +
              "<unchanged>\n" +
              "    stuff = 0\n" +
              "</unchanged>\n"
          writeConfigFile(folder, "test.conf", data2)

          Configgy.reload

          // all subscribers (even for unchanged nodes) should be called.
          checked should be(true)
          checkedAlso should be(true)
          Configgy.getInt("robot.age", 0) should be(23003)
      }
  }

  it("should change a nested value without invalidating ConfigMap references") {
    config =>
      withTempFolder {
        folder =>
          val data1 =
            "<robot>\n" +
              "    name=\"Nibbler\"\n" +
              "    age = 23002\n" +
              "    nested {\n" +
              "        thing = 5\n" +
              "    }\n" +
              "</robot>\n"
          writeConfigFile(folder, "test.conf", data1)

          Configgy.setup(new Configgy.DefaultInitFromFile(folder.getAbsolutePath(), "test.conf"))

          val robot = Configgy.configMap("robot")
          robot.getString("name") should be(Some("Nibbler"))
          robot.setString("name", "Bender")
          robot.getString("name") should be(Some("Bender"))
          val nested = Configgy.configMap("robot.nested")
          nested("thing") should be("5")
          robot("age") should be("23002")

          val data2 =
            "<robot>\n" +
              "    name=\"Nibbler\"\n" +
              "    age = 23004\n" +
              "</robot>\n"
          writeConfigFile(folder, "test.conf", data2)
          Configgy.reload
          nested.dump should be("{robot.nested: }")
          robot("age") should be("23004")
      }
  }

  private def writeConfigFile(folder: File, filename: String, data: String) = {
    log.debug("write config file to \"%s\"".format(folder.getAbsolutePath() + File.separator + filename))
    val f = new FileOutputStream(folder.getAbsolutePath() + File.separator + filename)
    f.write(data.getBytes)
    f.close
  }
}
