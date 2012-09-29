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

import java.io.FileOutputStream

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.lib.test.TestHelperLogging
import org.digimead.lib.test.TestHelperStorage
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class ConfiggyTest extends FunSuite with ShouldMatchers with BeforeAndAfter with TestHelperLogging with TestHelperStorage {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger
  implicit val timeout = 5000L

  override def withFixture(test: OneArgTest) {
    withLogging(test.configMap) {
      Configgy
      test(test.configMap)
    }
  }

  before { Logging.reset() }

  test("test Configgy initialization with DefaultInit") {
    config =>
      Configgy.setup(new Configgy.DefaultInit)
      Configgy.getImplementation().isInstanceOf[Configgy.Interface] should be(true)
      Configgy.getImplementation() should not be (null)
      assertLog("initialize Configgy$ with default Configgy implementation", _ == _)
      assertLog("dispose ", _.startsWith(_))
      assertLog("initialize default Configgy implementation", _ == _)
  }

  test("test Configgy initialization with DefaultInitFromFile") {
    config =>
      withTempFolder {
        val data1 =
          "name=\"Nibbler\"\n" +
            "\n" +
            "<log>\n" +
            "    filename=\"" + folderName + "/test.log\"\n" +
            "    level=\"WARNING\"\n" +
            "</log>\n"
        writeConfigFile("test.conf", data1)
        Configgy.setup(new Configgy.DefaultInitFromFile(folderName, "test.conf"))
        Configgy.getImplementation().isInstanceOf[Configgy.Interface] should be(true)
        Configgy.getImplementation() should not be (null)
        assertLog("initialize Configgy$ with default ConfiggyFromFile implementation", _ == _)
        assertLog("dispose ", _.startsWith(_))
        assertLog("initialize default ConfiggyFromFile implementation", _ == _)
      }
  }

  private def writeConfigFile(filename: String, data: String) = {
    log.debug("write config file to \"%s\"".format(folderName + "/" + filename))
    val f = new FileOutputStream(folderName + "/" + filename)
    f.write(data.getBytes)
    f.close
  }
}
