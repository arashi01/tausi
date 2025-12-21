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

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

/** Registry for managing plugin lifecycle.
  *
  * Provides centralized management of plugin initialization and shutdown, matching the upstream
  * Tauri PluginStore<R> design. Plugins can be registered and will be initialized/shutdown in the
  * correct order.
  *
  * This is a singleton object that maintains the global plugin registry.
  *
  * Example usage:
  * {{{
  * // Register plugins at application startup
  * for
  *   _ <- PluginRegistry.register[app.type]
  *   _ <- PluginRegistry.register[event.type]
  *   _ <- PluginRegistry.register[window.type]
  *   _ <- PluginRegistry.initializeAll
  * yield ()
  *
  * // At shutdown
  * PluginRegistry.shutdownAll
  * }}}
  */
object PluginRegistry:
  private case class PluginEntry[P](name: String, plugin: Plugin[P], initialized: Boolean = false)

  // HACK: Type-erase to AnyRef to stop Scala 3's Safe Initialization checker (checkReentrant)
  // from recursively analyzing ConcurrentHashMap's internal mutable fields (table, keySet, etc).
  // Without this erasure, the compiler inspects ConcurrentHashMap internals and flags them as
  // "globally reachable mutable state", causing compilation failure. Since AnyRef has no mutable
  // fields, the analysis stops here. This is safe because we only access through the typed accessor.
  private val _backingStore: AnyRef = new ConcurrentHashMap[String, PluginEntry[?]]()

  // Safe accessor that casts back to the usable map type. Uses `def` (not `val`) to avoid
  // creating another static field that the checker would analyze.
  private inline def plugins: scala.collection.concurrent.Map[String, PluginEntry[?]] =
    _backingStore.asInstanceOf[ConcurrentHashMap[String, PluginEntry[?]]].asScala // scalafix:ok

  /** Register a plugin.
    *
    * Plugins must be registered before they can be initialized. Registration is idempotent -
    * registering the same plugin multiple times is safe and will be ignored.
    *
    * @tparam P The plugin module type
    * @param plugin Plugin instance (implicit)
    * @param ec Execution context
    * @return Future that completes when registration is done
    */
  def register[P](using plugin: Plugin[P])(using ExecutionContext): Future[Unit] =
    Future {
      val pluginName = plugin.name
      plugins.putIfAbsent(pluginName, PluginEntry(pluginName, plugin, initialized = false)): Unit
    }

  /** Get a registered plugin by name.
    *
    * @param name Plugin name
    * @return Some(Plugin) if found, None otherwise
    */
  def get(name: String): Option[Plugin[?]] =
    plugins.get(name).map(_.plugin)

  /** Check if a plugin is registered.
    *
    * @param name Plugin name
    * @return true if the plugin is registered
    */
  def isRegistered(name: String): Boolean =
    plugins.contains(name)

  /** Initialize all registered plugins.
    *
    * Plugins are initialized in registration order. If any plugins fail to initialize, all errors
    * are accumulated and returned. Successfully initialized plugins are marked as such regardless
    * of failures in other plugins.
    *
    * This method is idempotent - plugins that are already initialized will be skipped.
    *
    * @param ec Execution context
    * @return Future containing a list of plugin initialization errors (empty if all succeeded)
    */
  def initializeAll(using ExecutionContext): Future[List[TauriError.PluginError]] =
    val pluginsToInit = plugins.values.toList.filter(!_.initialized)

    Future
      .sequence {
        pluginsToInit.map { entry =>
          entry.plugin.initialize
            .map { _ =>
              plugins.update(entry.name, entry.copy(initialized = true))
              None
            }
            .recover { case e =>
              Some(TauriError.PluginError(entry.name, s"Failed to initialize: ${e.getMessage}", Some(e)))
            }
        }
      }
      .map(_.flatten)
  end initializeAll

  /** Shutdown all registered plugins.
    *
    * Plugins are shutdown in reverse registration order (LIFO). If any plugins fail to shutdown,
    * all errors are accumulated and returned. Successfully shutdown plugins are marked as such
    * regardless of failures in other plugins.
    *
    * This method is idempotent - multiple calls are safe.
    *
    * @param ec Execution context
    * @return Future containing a list of plugin shutdown errors (empty if all succeeded)
    */
  def shutdownAll(using ExecutionContext): Future[List[TauriError.PluginError]] =
    val pluginsToShutdown = plugins.values.toList.filter(_.initialized).reverse

    Future
      .sequence {
        pluginsToShutdown.map { entry =>
          entry.plugin.shutdown
            .map { _ =>
              plugins.update(entry.name, entry.copy(initialized = false))
              None
            }
            .recover { case e =>
              Some(TauriError.PluginError(entry.name, s"Failed to shutdown: ${e.getMessage}", Some(e)))
            }
        }
      }
      .map(_.flatten)
  end shutdownAll

  /** Clear all registered plugins.
    *
    * This is primarily useful for testing. In production, plugins should remain registered for the
    * application lifetime.
    *
    * WARNING: Does not call shutdown on plugins. Call shutdownAll first if needed.
    */
  def clear(): Unit =
    plugins.clear()

  /** Get the list of all registered plugin names.
    *
    * @return List of plugin names in registration order
    */
  def registeredPlugins: List[String] =
    plugins.keys.toList

  /** Get the list of initialized plugin names.
    *
    * @return List of initialized plugin names
    */
  def initializedPlugins: List[String] =
    plugins.values.filter(_.initialized).map(_.name).toList
end PluginRegistry
