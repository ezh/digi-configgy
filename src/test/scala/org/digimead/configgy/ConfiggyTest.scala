/**
 * Digi Configgy is a library for handling configurations
 *
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

import java.io.{ File, FileOutputStream }
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.lib.test.{ LoggingHelper, StorageHelper }
import org.mockito.Mockito
import org.scalatest.{ FunSuite, Matchers }
import scala.collection.JavaConversions.asScalaBuffer

class ConfiggyTest extends FunSuite with Matchers with StorageHelper with LoggingHelper with XLoggable {
  implicit val timeout = 5000L

  before {
    DependencyInjection(org.digimead.digi.lib.default, false)
    Schema.clear
    Configgy.clear
  }

  test("test Configgy initialization with DefaultInit") {
    implicit val option = Mockito.times(3)
    withMockitoLogCaptor { Configgy.setup(new Configgy.DefaultInit) } { logCaptor ⇒
      val _1st = logCaptor.getAllValues()(0)
      _1st.getLevel() should be(org.apache.log4j.Level.DEBUG)
      _1st.getMessage.toString should startWith("dispose ")
      val _2nd = logCaptor.getAllValues()(1)
      _2nd.getLevel() should be(org.apache.log4j.Level.DEBUG)
      _2nd.getMessage.toString should be("initialize Configgy$ with default Configgy implementation")
      val _3rd = logCaptor.getAllValues()(2)
      _3rd.getLevel() should be(org.apache.log4j.Level.DEBUG)
      _3rd.getMessage.toString should be("initialize default Configgy implementation")
    }
    Configgy.getImplementation().isInstanceOf[Configgy.Interface] should be(true)
    Configgy.getImplementation() should not be (null)
  }

  test("test Configgy initialization with DefaultInitFromFile") {
    withTempFolder {
      folder ⇒
        val data1 =
          "name=\"Nibbler\"\n" +
            "\n" +
            "<log>\n" +
            "    filename=\"" + folder + "/test.log\"\n" +
            "    level=\"WARNING\"\n" +
            "</log>\n"
        writeConfigFile(folder, "test.conf", data1)
        implicit val option = Mockito.times(4)
        withMockitoLogCaptor { Configgy.setup(new Configgy.DefaultInitFromFile(folder.getAbsolutePath(), "test.conf")) } { logCaptor ⇒
          val _1st = logCaptor.getAllValues()(0)
          _1st.getLevel() should be(org.apache.log4j.Level.DEBUG)
          _1st.getMessage.toString should startWith("dispose ")
          val _2nd = logCaptor.getAllValues()(1)
          _2nd.getLevel() should be(org.apache.log4j.Level.DEBUG)
          _2nd.getMessage.toString should be("initialize Configgy$ with default ConfiggyFromFile implementation")
          val _3rd = logCaptor.getAllValues()(2)
          _3rd.getLevel() should be(org.apache.log4j.Level.DEBUG)
          _3rd.getMessage.toString should be("initialize default ConfiggyFromFile implementation")
        }
    }
  }

  private def writeConfigFile(folder: File, filename: String, data: String) = {
    log.debug("write config file to \"%s\"".format(folder.getAbsolutePath() + File.separator + filename))
    val f = new FileOutputStream(folder.getAbsolutePath() + File.separator + filename)
    f.write(data.getBytes)
    f.close
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) { adjustLoggingBeforeAll(configMap) }
}
