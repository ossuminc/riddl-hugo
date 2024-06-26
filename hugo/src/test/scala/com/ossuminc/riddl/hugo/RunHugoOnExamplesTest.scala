/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.commands.CommandOptions
import com.ossuminc.riddl.commands.CommandPlugin
import com.ossuminc.riddl.hugo.HugoCommand
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.Path
import scala.annotation.unused

/** Unit Tests To Run Riddlc On Examples */
class RunHugoOnExamplesTest
    extends RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand]("hugo") {

  override val outDir: Path = Path.of("riddlc/target/test/hugo-examples")

  override def validateTestName(name: String): Boolean = name == "ReactiveBBQ"

  "Run Hugo On Examples" should { "should work" in { runTests() } }

  override def onSuccess(@unused commandName: String,
                         @unused caseName: String,
                         @unused configFile: Path,
                         @unused passesResult: PassesResult,
                         @unused tempDir: Path): Assertion = {
    // TODO: check themes dir
    // TODO: check config.toml setting values
    // TODO: check options
    succeed
  }
}
