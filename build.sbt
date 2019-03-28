/*
scalafmt: {
  style = defaultWithAlign
  maxColumn = 150
  align.tokens = [
    { code = "=>", owner = "Case" }
    { code = "⇒", owner = "Case" }
    { code = "extends", owner = "Defn.(Class|Trait|Object)" }
    { code = "//", owner = ".*" }
    { code = "{", owner = "Template" }
    { code = "}", owner = "Template" }
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "%", owner = "Term.ApplyInfix" }
    { code = "%%", owner = "Term.ApplyInfix" }
    { code = "%%%", owner = "Term.ApplyInfix" }
    { code = "->", owner = "Term.ApplyInfix" }
    { code = "→", owner = "Term.ApplyInfix" }
    { code = "<-", owner = "Enumerator.Generator" }
    { code = "←", owner = "Enumerator.Generator" }
    { code = "=", owner = "(Enumerator.Val|Defn.(Va(l|r)|Def|Type))" }
  ]
}
 */

// Dependency versions
lazy val antlrVersion          = "4.7.1"
lazy val catsVersion           = "1.4.0"
lazy val commonsTextVersion    = "1.2"
lazy val circeVersion          = "0.10.0"
lazy val diffsonVersion        = "2.2.5"
lazy val effVersion            = "4.6.1"
lazy val jenaVersion           = "3.6.0"
lazy val jgraphtVersion        = "1.2.0"
lazy val logbackVersion        = "1.2.3"
lazy val loggingVersion        = "3.7.2"
lazy val rdf4jVersion          = "2.2.4"
lazy val scalacheckVersion     = "1.14.0"
lazy val scalacticVersion      = "3.0.5"
lazy val scalaTestVersion      = "3.0.5"
lazy val scalaGraphVersion     = "1.11.5"
lazy val scalatagsVersion      = "0.6.7"
lazy val scallopVersion        = "3.1.1"
lazy val seleniumVersion       = "2.35.0"
lazy val sextVersion           = "0.2.6"
lazy val typesafeConfigVersion = "1.3.2"
lazy val xercesVersion         = "2.11.0"

// Compiler plugin dependency versions
lazy val simulacrumVersion    = "0.11.0"
lazy val kindProjectorVersion = "0.9.5"
lazy val scalaMacrosVersion   = "2.1.1"

// Dependency modules
lazy val antlr4            = "org.antlr"                  % "antlr4"               % antlrVersion
lazy val catsCore          = "org.typelevel"              %% "cats-core"           % catsVersion
lazy val catsKernel        = "org.typelevel"              %% "cats-kernel"         % catsVersion
lazy val catsMacros        = "org.typelevel"              %% "cats-macros"         % catsVersion
lazy val circeCore         = "io.circe"                   %% "circe-core"          % circeVersion
lazy val circeGeneric      = "io.circe"                   %% "circe-generic"       % circeVersion
lazy val circeParser       = "io.circe"                   %% "circe-parser"        % circeVersion
lazy val commonsText       = "org.apache.commons"         %  "commons-text"        % commonsTextVersion
lazy val diffsonCirce      = "org.gnieh"                  %% "diffson-circe"       % diffsonVersion
lazy val eff               = "org.atnos"                  %% "eff"                 % effVersion
lazy val jgraphtCore       = "org.jgrapht"                % "jgrapht-core"         % jgraphtVersion
lazy val logbackClassic    = "ch.qos.logback"             % "logback-classic"      % logbackVersion
lazy val jenaArq           = "org.apache.jena"            % "jena-arq"             % jenaVersion
lazy val rdf4j_runtime     = "org.eclipse.rdf4j"          % "rdf4j-runtime"        % rdf4jVersion
lazy val scalaLogging      = "com.typesafe.scala-logging" %% "scala-logging"       % loggingVersion
lazy val scallop           = "org.rogach"                 %% "scallop"             % scallopVersion
lazy val scalactic         = "org.scalactic"              %% "scalactic"           % scalacticVersion
lazy val scalacheck        = "org.scalacheck"             %% "scalacheck"          % scalacheckVersion
lazy val scalaTest         = "org.scalatest"              %% "scalatest"           % scalaTestVersion
lazy val scalatags         = "com.lihaoyi"                %% "scalatags"           % scalatagsVersion
lazy val selenium          = "org.seleniumhq.selenium"    % "selenium-java"        % seleniumVersion
// lazy val htmlUnit          = "org.seleniumhq.selenium"    % "htmlunit-driver"      % seleniumVersion
lazy val sext              = "com.github.nikita-volkov"   % "sext"                 % sextVersion
lazy val typesafeConfig    = "com.typesafe"               % "config"               % typesafeConfigVersion
lazy val xercesImpl        = "xerces"                     % "xercesImpl"           % xercesVersion

// Compiler plugin modules
lazy val scalaMacrosParadise = "org.scalamacros"      % "paradise"        % scalaMacrosVersion cross CrossVersion.full
lazy val simulacrum          = "com.github.mpilquist" %% "simulacrum"     % simulacrumVersion
lazy val kindProjector       = "org.spire-math"       %% "kind-projector" % kindProjectorVersion

lazy val shaclex = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin, SbtNativePackager, WindowsPlugin, JavaAppPackaging)
  .disablePlugins(RevolverPlugin)
//  .settings(
//    buildInfoKeys := BuildInfoKey.ofN(name, version, scalaVersion, sbtVersion),
//    buildInfoPackage := "es.weso.shaclex.buildinfo" 
//  )
  .settings(commonSettings, packagingSettings, publishSettings, ghPagesSettings, wixSettings)
  .aggregate(schemaInfer, schema, shacl, shex, srdfJena, srdf4j, srdf, utils, converter, rbe, typing, validating, shapeMaps, depGraphs, slang)
  .dependsOn(schemaInfer, schema, shacl, shex, srdfJena, srdf4j, srdf, utils, converter, rbe, typing, validating, shapeMaps, depGraphs, slang)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(noDocProjects: _*),
    libraryDependencies ++= Seq(
      logbackClassic,
      scalaLogging,
      scallop
    ),
    cancelable in Global      := true,
    fork                      := true,
    parallelExecution in Test := false
  )

lazy val schemaInfer = project
  .in(file("modules/schemaInfer"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .dependsOn(
    schema,
    srdf
  )

lazy val schema = project
  .in(file("modules/schema"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .dependsOn(
    shex,
    shacl,
    slang,
    shapeMaps,
    converter,
    srdfJena
  )

lazy val depGraphs = project
  .in(file("modules/depGraphs"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsMacros,
      jgraphtCore
    )
  )

lazy val slang = project
  .in(file("modules/slang"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .dependsOn(srdf,
    shex, shacl,
    utils,
    srdf4j % Test,
    srdfJena % Test)
  .settings(
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsMacros
    )
  )

lazy val shacl = project
  .in(file("modules/shacl"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .dependsOn(srdf,
    utils,
    typing,
    validating,
    srdf4j % Test,
    srdfJena % Test)
  .settings(
    logBuffered in Test       := false,
    parallelExecution in Test := false,
    fork in Test              := true,
    libraryDependencies ++= Seq(
      typesafeConfig % Test,
      sext,
      catsCore,
      catsKernel,
      catsMacros
    )
  )


lazy val CompatTest = config("compat") extend (Test) describedAs("Tests that check compatibility (some may fail)")
def compatFilter(name: String): Boolean = name endsWith "CompatTest"
def testFilter(name: String): Boolean = /*(name endsWith "Test") && */ !compatFilter(name)

lazy val shex = project
  .in(file("modules/shex"))
  .enablePlugins(Antlr4Plugin)
  .disablePlugins(RevolverPlugin)
  .configs(CompatTest)
  .settings(
    commonSettings,
    publishSettings,
    antlrSettings("es.weso.shex.parser"),
    inConfig(CompatTest)(Defaults.testTasks),
    testOptions in Test := Seq(Tests.Filter(testFilter)),
    testOptions in CompatTest := Seq(Tests.Filter(compatFilter)),
  )
  .dependsOn(
    srdf,
    typing,
    utils % "test -> test; compile -> compile",
    validating,
    shapeMaps,
    rbe,
    depGraphs,
    srdfJena % Test,
    srdf4j % Test
  )
  .settings(
    libraryDependencies ++= Seq(
      typesafeConfig % Test,
      logbackClassic % Test,
      scalaLogging,
      circeCore,
      circeGeneric,
      circeParser,
      scalaTest % Test,
      scalacheck % Test
    )
  )

lazy val shapeMaps = project
  .in(file("modules/shapeMaps"))
  .enablePlugins(Antlr4Plugin)
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings, antlrSettings("es.weso.shapeMaps.parser"))
  .dependsOn(
    srdf,
    utils,
    srdfJena % Test)
  .settings(
    libraryDependencies ++= Seq(
      sext % Test,
      scalaLogging,
      catsCore,
      catsKernel,
      catsMacros,
      circeCore,
      circeGeneric,
      circeParser
    )
  )

lazy val converter = project
  .in(file("modules/converter"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .dependsOn(
    shex,
    shacl,
    srdfJena % Test
  )

lazy val rbe = project
  .in(file("modules/rbe"))
  .disablePlugins(RevolverPlugin)
  .dependsOn(validating, typing)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin(scalaMacrosParadise),
      simulacrum,
      catsCore,
      catsKernel,
      catsMacros,
      scalacheck % Test
    )
  )

lazy val srdf = project
  .in(file("modules/srdf"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .settings(
//    crossScalaVersions := Seq("2.12.6","2.13.0-M3"),
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsMacros,
      circeCore,
      circeGeneric,
      circeParser,
      scalaLogging
    )
  )

lazy val srdfJena = project
  .in(file("modules/srdfJena"))
  .disablePlugins(RevolverPlugin)
  .dependsOn(srdf, utils)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      logbackClassic % Test,
      scalaLogging,
      typesafeConfig % Test,
      jenaArq,
      catsCore,
      catsKernel,
      catsMacros
    )
  )

lazy val srdf4j = project
  .in(file("modules/srdf4j"))
  .disablePlugins(RevolverPlugin)
  .dependsOn(srdf, utils)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      logbackClassic % Test,
      scalaLogging,
      typesafeConfig % Test,
      rdf4j_runtime,
      catsCore,
      catsKernel,
      catsMacros
    )
  )

lazy val typing = project
  .in(file("modules/typing"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsMacros
    )
  )

lazy val utils = project
  .in(file("modules/utils"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      eff,
      circeCore,
      circeGeneric,
      circeParser,
      catsCore,
      catsKernel,
      catsMacros,
      diffsonCirce,
      xercesImpl,
      commonsText
    )
  )

lazy val validating = project
  .in(file("modules/validating"))
  .disablePlugins(RevolverPlugin)
  .dependsOn(srdf, srdfJena % Test, utils % "test -> test; compile -> compile")
  .settings(commonSettings, publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin(kindProjector),
      eff,
      catsCore,
      catsKernel,
      catsMacros
    )
  )

/* ********************************************************
 ******************** Grouped Settings ********************
 **********************************************************/

lazy val noDocProjects = Seq[ProjectReference](
  validating
)

lazy val noPublishSettings = Seq(
//  publish := (),
//  publishLocal := (),
  publishArtifact := false
)

lazy val sharedDependencies = Seq(
  libraryDependencies ++= Seq(
    scalactic,
    scalaTest % Test
  )
)

lazy val packagingSettings = Seq(
  mainClass in Compile        := Some("es.weso.shaclex.Main"),
  mainClass in assembly       := Some("es.weso.shaclex.Main"),
  test in assembly            := {},
  assemblyJarName in assembly := "shaclex.jar",
  packageSummary in Linux     := name.value,
  packageSummary in Windows   := name.value,
  packageDescription          := name.value
)

lazy val compilationSettings = Seq(
  scalaVersion := "2.12.7",
  // format: off
  scalacOptions ++= Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.  "-encoding", "UTF-8",
    "-language:_",
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint",
    "-Yrangepos",
//    "-Ylog-classpath",
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
//    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
//    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
//    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
//    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
//    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
//    "-Ywarn-unused:privates",            // Warn if a private member is unused.
//    "-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
//    "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-Ypartial-unification",
  )
  // format: on
)

lazy val wixSettings = Seq(
  wixProductId        := "39b564d5-d381-4282-ada9-87244c76e14b",
  wixProductUpgradeId := "6a710435-9af4-4adb-a597-98d3dd0bade1"
// The same numbers as in the docs?
// wixProductId := "ce07be71-510d-414a-92d4-dff47631848a",
// wixProductUpgradeId := "4552fb0e-e257-4dbd-9ecb-dba9dbacf424"
)

lazy val ghPagesSettings = Seq(
  git.remoteRepo := "git@github.com:labra/shaclex.git"
)

lazy val commonSettings = compilationSettings ++ sharedDependencies ++ Seq(
  organization := "es.weso",
  resolvers ++= Seq(
    Resolver.bintrayRepo("labra", "maven"),
    Resolver.bintrayRepo("weso", "weso-releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)

def antlrSettings(packageName: String) = Seq(
  antlr4GenListener in Antlr4 := true,
  antlr4GenVisitor in Antlr4  := true,
  antlr4Dependency in Antlr4  := antlr4,
  antlr4PackageName in Antlr4 := Some(packageName),
)

lazy val publishSettings = Seq(
  maintainer      := "Jose Emilio Labra Gayo <labra@uniovi.es>",
  homepage        := Some(url("https://github.com/labra/shaclex")),
  licenses        := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo         := Some(ScmInfo(url("https://github.com/labra/shaclex"), "scm:git:git@github.com:labra/shaclex.git")),
  autoAPIMappings := true,
  apiURL          := Some(url("http://labra.github.io/shaclex/latest/api/")),
  pomExtra        := <developers>
                       <developer>
                         <id>labra</id>
                         <name>Jose Emilio Labra Gayo</name>
                         <url>https://github.com/labra/</url>
                       </developer>
                     </developers>,
  scalacOptions in doc ++= Seq(
    "-diagrams-debug",
    "-doc-source-url",
    scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
    "-sourcepath",
    baseDirectory.in(LocalRootProject).value.getAbsolutePath,
    "-diagrams",
  ),
  publishMavenStyle              := true,
  bintrayRepository in bintray   := "weso-releases",
  bintrayOrganization in bintray := Some("weso")
)
