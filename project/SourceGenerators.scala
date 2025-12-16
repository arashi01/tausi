import sbt.*
import sbt.Keys.*
import scala.sys.process.*
import java.io.File

/** Defines the sbt tasks for automatically generating Tauri command bindings from Rust source.
  *
  * This object encapsulates the logic for caching and executing the code generators, ensuring they
  * only run when input Rust files have changed.
  *
  * The generator fetches Tauri source code from the official repository at a specific commit/tag
  * specified by the `tauriVersion` setting.
  */
object SourceGenerators {

  /** Setting key for the Tauri git revision to use for code generation */
  val tauriVersion = settingKey[String]("Git SHA or tag of Tauri to generate bindings from")

  /** Setting key for excluding specific commands from generation (to implement manually) */
  val tauriCommandExclusions = settingKey[Set[String]]("Set of Tauri command IDs to exclude from generation (e.g., 'plugin:event|listen')")

  /** Clone or update Tauri repository in build directory
    *
    * @param targetDir Directory to clone into
    * @param revision Git SHA or tag to checkout
    * @param log SBT logger
    * @return Directory containing checked out Tauri source
    */
  private def fetchTauriSource(targetDir: File, revision: String, log: Logger): File = {
    val tauriDir = targetDir / "tauri-repo"
    val tauriUrl = "https://github.com/tauri-apps/tauri.git"

    if (!tauriDir.exists()) {
      log.info(s"Cloning Tauri repository from $tauriUrl...")
      val cloneCmd = s"git clone --depth 1 --no-single-branch $tauriUrl ${tauriDir.getAbsolutePath}"
      val cloneResult = cloneCmd.!
      if (cloneResult != 0) {
        sys.error(s"Failed to clone Tauri repository from $tauriUrl")
      }
    } else {
      log.debug(s"Tauri repository already exists at ${tauriDir.getAbsolutePath}")
    }

    // Fetch latest changes and checkout specified revision
    log.info(s"Checking out Tauri revision: $revision")
    val fetchCmd = Process(Seq("git", "fetch", "--depth", "1", "origin", revision), tauriDir)
    val checkoutCmd = Process(Seq("git", "checkout", revision), tauriDir)

    val fetchResult = fetchCmd.!
    if (fetchResult != 0) {
      log.warn(s"Failed to fetch revision $revision, attempting checkout anyway...")
    }

    val checkoutResult = checkoutCmd.!
    if (checkoutResult != 0) {
      sys.error(s"Failed to checkout Tauri revision: $revision")
    }

    log.info(s"Successfully checked out Tauri at revision: $revision")
    tauriDir
  }

  /** Get the full git SHA for the current checkout
    *
    * @param repoDir Git repository directory
    * @return Full SHA hash
    */
  private def getGitSha(repoDir: File): String = {
    val result = Process(Seq("git", "rev-parse", "HEAD"), repoDir).!!
    result.trim
  }

  /** Defines the task for the 'api-core' module to generate Tauri commands. */
  val tauriCommandsGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val buildTargetDir = target.value
    val revision = tauriVersion.value
    val exclusions = tauriCommandExclusions.value

    // Fetch Tauri source at specified revision
    val tauriRepoDir = fetchTauriSource(buildTargetDir, revision, log)
    val tauriSourceDir = tauriRepoDir / "crates" / "tauri" / "src"
    val actualSha = getGitSha(tauriRepoDir)

    // Input files: all plugin.rs files in Tauri source
    val inputFiles: Set[File] = if (tauriSourceDir.exists()) {
      (tauriSourceDir ** "plugin.rs").get.toSet
    } else {
      log.warn(s"Tauri source directory not found: ${tauriSourceDir.getAbsolutePath}")
      Set.empty
    }

    // The function that performs the generation.
    // It takes a set of modified input files and returns a set of generated output files.
    val generate: Set[File] => Set[File] = { changedFiles =>
      if (changedFiles.nonEmpty) {
        log.info(s"SourceGenerators: ${changedFiles.size} Tauri plugin file(s) changed. Regenerating commands...")

        // Parse all Rust commands from Tauri source
        val allCommands = RustCommandParser.parseCommands(tauriSourceDir, log)
        val commands = allCommands.filterNot(cmd => exclusions.contains(cmd.commandId))
        val excludedCount = allCommands.size - commands.size

        if (excludedCount > 0) {
          log.info(s"SourceGenerators: Excluded $excludedCount command(s) based on tauriCommandExclusions setting")
        }
        log.info(s"SourceGenerators: Parsed ${commands.size} commands from Tauri source")

        // Generate Scala command definitions
        val outputDir = sourceManagedDir / "scala"
        val generated: Map[File, String] = TauriCommandGenerator.generate(commands, outputDir, actualSha, log)

        // Write all generated files
        generated.foreach { case (file, content) =>
          IO.write(file, content)
        }

        log.info(s"SourceGenerators: Finished generating ${generated.size} command module(s).")
        generated.keySet
      } else {
        log.debug("SourceGenerators: No Tauri plugin files changed, skipping generation.")
        Set.empty
      }
    }

    // Use sbt's cached function to track input file changes.
    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-tauri-commands-generator",
      inStyle = FilesInfo.lastModified,
      outStyle = FilesInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }
}
