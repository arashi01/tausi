/*
 * Copyright (c) 2025 Tausi contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tausi.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** Typeclass for Tauri plugins.
  *
  * Provides a unified interface for plugin lifecycle management, matching the upstream Tauri
  * Plugin<R> trait. Each plugin module (app, event, window, etc.) can implement this typeclass to
  * participate in the application lifecycle.
  *
  * Example:
  * {{{
  * object myPlugin extends MyPluginGenerated:
  *   given plugin: Plugin[myPlugin.type] with
  *     def name = "my-plugin"
  *     def initialize(using ExecutionContext) = Future.successful(())
  *     def shutdown(using ExecutionContext) = Future.successful(())
  * }}}
  *
  * @tparam P The plugin module type (singleton object type)
  */
trait Plugin[P]:
  /** The plugin name.
    *
    * This identifier is used for:
    *   - Plugin registration and lookup
    *   - Command routing (e.g., "plugin:app|version")
    *   - Configuration mapping
    *   - Logging and diagnostics
    *
    * Should match the plugin name used in Tauri's Rust implementation.
    */
  def name: String

  /** Initialize the plugin.
    *
    * Called once during application startup, after the Tauri runtime is ready. Plugins should
    * perform any necessary setup here, such as:
    *   - Registering event listeners
    *   - Initializing state
    *   - Setting up resources
    *
    * If initialization fails, the Future should complete with an error.
    *
    * @param ec Execution context for async operations
    * @return Future that completes when initialization is done
    */
  def initialize(using ExecutionContext): Future[Unit]

  /** Shutdown the plugin.
    *
    * Called during application shutdown to clean up plugin resources. Plugins should:
    *   - Unregister event listeners
    *   - Close open resources
    *   - Persist state if needed
    *   - Release any held references
    *
    * This method should be idempotent - multiple calls should be safe.
    *
    * @param ec Execution context for async operations
    * @return Future that completes when shutdown is done
    */
  def shutdown(using ExecutionContext): Future[Unit]
end Plugin

object Plugin:
  /** Summon a Plugin instance for a given type.
    *
    * Example:
    * {{{
    * val appPlugin = Plugin[app.type]
    * println(appPlugin.name)  // "app"
    * }}}
    */
  def apply[P](using plugin: Plugin[P]): Plugin[P] = plugin

  /** Default no-op plugin implementation.
    *
    * Useful for plugins that don't need lifecycle management:
    * {{{
    * object simplePlugin extends SimplePluginGenerated:
    *   given plugin: Plugin[simplePlugin.type] = Plugin.noop("simple")
    * }}}
    */
  def noop[P](pluginName: String): Plugin[P] = new Plugin[P]:
    def name: String = pluginName
    def initialize(using ExecutionContext): Future[Unit] = Future.successful(())
    def shutdown(using ExecutionContext): Future[Unit] = Future.successful(())
end Plugin
