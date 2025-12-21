inThisBuild(
  List(
    scalaVersion := "3.7.4",
    organization := "io.github.arashi01",
    description := "Scala toolkit for Tauri",
    startYear := Some(2025),
    homepage := Some(url("https://github.com/arashi01/tausi")),
    semanticdbEnabled := true,
    version := versionSetting.value,
    dynver := versionSetting.toTaskable.toTask.value,
    versionScheme := Some("semver-spec"),
    licenses := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    sonatypeCredentialHost := Sonatype.sonatypeCentralHost,
    publishCredentials,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/arashi01/tausi"),
        "scm:git:https://github.com/arashi01/tausi.git",
        Some("scm:git:git@github.com:arashi01/tausi.git")
      )
    ),
    // Tauri source version for code generation (git SHA or tag)
    SourceGenerators.tauriVersion := "956031d",
    // Commands to exclude from generation (implement manually for complex types)
    // Only exclude commands with truly unmappable Rust types (callbacks, complex event handlers, raw byte arrays)
    SourceGenerators.tauriCommandExclusions := Set(
      // Event commands with callback function types (EventName, EventTarget, CallbackFn)
      "plugin:event|listen",
      "plugin:event|once",
      // Image commands with raw byte arrays (js.Array[Int])
      "plugin:image|new",
      "plugin:image|from_bytes"
    )
  ) ++ formattingSettings
)

val libraries = new {
  val `cats-effect` = Def.setting("org.typelevel" %%% "cats-effect" % "3.7.0-RC1")
  val fs2 = Def.setting("co.fs2" %%% "fs2-core" % "3.13.0-M7")
  val laminar = Def.setting("com.raquo" %%% "laminar" % "17.2.1")
  val waypoint = Def.setting("com.raquo" %%% "waypoint" % "9.0.0")
  val zio = Def.setting("dev.zio" %%% "zio" % "2.1.22")
  val `zio-streams` = Def.setting("dev.zio" %%% "zio-streams" % "2.1.22")
  // Testing
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.1" % Test)
  val `munit-cats-effect` = Def.setting("org.typelevel" %%% "munit-cats-effect" % "2.2.0-RC1" % Test)
  val `munit-zio` = Def.setting("com.github.poslegm" %%% "munit-zio" % "0.4.0" % Test)
  val `scala-java-time` = Def.setting("io.github.cquiroz" %%% "scala-java-time" % "2.6.0")
}

val `tausi-api` =
  crossProject(JSPlatform, NativePlatform)
    .crossType(CrossType.Full)
    .in(file("modules/api"))
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(fileHeaderSettings)
    .settings(publishSettings)
    .jsSettings(
      Test / jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
      Compile / sourceGenerators += SourceGenerators.tauriCommandsGeneratorTask.taskValue
    )

val `tausi-cats` =
  project
    .in(file("modules/cats"))
    .enablePlugins(ScalaJSPlugin)
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(fileHeaderSettings)
    .settings(publishSettings)
    .dependsOn(`tausi-api`.js)
    .settings(libraryDependencies += libraries.`cats-effect`.value)
    .settings(libraryDependencies += libraries.fs2.value)
    .settings(libraryDependencies += libraries.`munit-cats-effect`.value)

val `tausi-zio` =
  project
    .in(file("modules/zio"))
    .enablePlugins(ScalaJSPlugin)
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(fileHeaderSettings)
    .settings(publishSettings)
    .dependsOn(`tausi-api`.js)
    .settings(libraryDependencies += libraries.zio.value)
    .settings(libraryDependencies += libraries.`zio-streams`.value)
    .settings(libraryDependencies += libraries.`munit-zio`.value)

val `tausi-laminar` =
  project
    .in(file("modules/laminar"))
    .enablePlugins(ScalaJSPlugin)
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(fileHeaderSettings)
    .settings(publishSettings)
    .dependsOn(`tausi-api`.js)
    .settings(libraryDependencies += libraries.laminar.value)

val `tausi-sample` =
  project
    .in(file("modules/sample"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(`tausi-zio`, `tausi-laminar`)
    .settings(unitTestSettings)
    .settings(libraryDependencies += libraries.laminar.value)
    .settings(libraryDependencies += libraries.waypoint.value)
    .settings(libraryDependencies += libraries.`scala-java-time`.value)
    .settings(libraryDependencies += libraries.`munit-zio`.value)
    .settings(scalaJSUseMainModuleInitializer := true)
    .settings(scalaJSLinkerConfig ~= { c =>
      import org.scalajs.linker.interface.*
      c.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("tausi.sample")))
        .withSourceMap(false)
    })

val `tausi-native` =
  project
    .in(file(".scala-native"))
    .settings(publish / skip := true) // TODO: Future module
    .aggregate(
      `tausi-api`.native
    )

val `tausi-js` =
  project
    .in(file(".scala-js"))
    .settings(publish / skip := true)
    .aggregate(
      `tausi-api`.js,
      `tausi-cats`,
      `tausi-zio`,
      `tausi-laminar`
    )

lazy val `tausi-root` =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(
      `tausi-js`,
      `tausi-native`
    )

def baseCompilerOptions = List(
  // Language features
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:strictEquality",

  // Kind projector / macros
  "-Xkind-projector",
  "-Xmax-inlines:64",

  // Core checks
  "-unchecked",
  "-deprecation",
  "-feature",
  "-explain",

  // Warning flags
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wunused:implicits",
  "-Wunused:explicits",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:params",
  "-Wunused:privates",

  // Scala 3-specific checks
  "-Yrequire-targetName",
  "-Ycheck-reentrant",
  "-Ycheck-mods"
)

def compilerOptions = baseCompilerOptions ++ List(
  "-Yexplicit-nulls",
  "-Xcheck-macros",
  "-Xfatal-warnings"
  // Suppress warning for intentional inline class instantiation in codec derivation
//  "-Wconf:msg=New anonymous class definition will be duplicated at each inline site:s"
)

def compilerSettings = List(
  Compile / compile / scalacOptions ++= compilerOptions,
  Test / compile / scalacOptions ++= baseCompilerOptions,
  Compile / doc / scalacOptions := Nil,
  Test / doc / scalacOptions := Nil
)

def formattingSettings = List(
  scalafmtDetailedError := true,
  scalafmtPrintDiff := true
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies ++= List(
    libraries.munit.value,
    libraries.`scala-java-time`.value % Test
  ),
  testFrameworks += new TestFramework("munit.Framework")
)

def fileHeaderSettings: List[Setting[?]] =
  List(
    headerLicense := {
      val developmentTimeline = {
        import java.time.Year
        val start = startYear.value.get
        val current: Int = Year.now.getValue
        if (start == current) s"$current" else s"$start, $current"
      }
      Some(HeaderLicense.MIT(developmentTimeline, "Tausi contributors."))
    },
    headerEmptyLine := false
  )

def publishCredentials: Setting[Task[Seq[Credentials]]] = credentials :=
  (for {
    user <- Option(System.getenv("PUBLISH_USER"))
    pass <- Option(System.getenv("PUBLISH_USER_PASSPHRASE"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    user,
    pass
  )).toSeq

def pgpSettings: List[Setting[?]] = List(
  PgpKeys.pgpSelectPassphrase := None,
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

def platformSourceDirectory(platform: String): Setting[Seq[File]] = sourceDirectories += (sourceDirectory.value / platform)

def versionSetting: Def.Initialize[String] = Def.setting(
  dynverGitDescribeOutput.value.mkVersion(
    (in: sbtdynver.GitDescribeOutput) =>
      if (!in.isSnapshot()) in.ref.dropPrefix
      else {
        val ref = in.ref.dropPrefix
        // Strip pre-release or build metadata (e.g., "-m.1" or "+build.5")
        val base = ref.takeWhile(c => c != '-' && c != '+')
        val numericParts =
          base.split("\\.").toList.map(_.trim).flatMap(s => scala.util.Try(s.toInt).toOption)

        if (numericParts.nonEmpty) {
          val incremented = numericParts.updated(numericParts.length - 1, numericParts.last + 1)
          s"${incremented.mkString(".")}-SNAPSHOT"
        } else {
          s"$base-SNAPSHOT"
        }
      },
    "SNAPSHOT"
  )
)

def publishSettings: List[Setting[?]] = publishCredentials +: pgpSettings ++: List(
  packageOptions += Package.ManifestAttributes(
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> Keys.version.value,
    "Implementation-Title" -> name.value
  ),
  publishTo := sonatypePublishToBundle.value,
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  developers := List(
    Developer(
      "arashi01",
      "Ali Rashid",
      "https://github.com/arashi01",
      url("https://github.com/arashi01")
    )
  )
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")
addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
