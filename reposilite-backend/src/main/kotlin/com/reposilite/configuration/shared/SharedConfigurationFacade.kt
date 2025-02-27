package com.reposilite.configuration.shared

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.api.Facade
import com.reposilite.status.FailureFacade
import io.javalin.openapi.JsonSchemaLoader
import panda.std.Result
import panda.std.Result.supplyThrowing
import panda.std.asError
import panda.std.ok
import panda.std.reactive.MutableReference
import java.util.function.Supplier
import kotlin.reflect.KClass

class SharedConfigurationFacade(
    private val journalist: Journalist,
    private val schemaGenerator: Lazy<SchemaGenerator>,
    private val failureFacade: FailureFacade,
    private val sharedSettingsProvider: SharedSettingsProvider,
    private val sharedConfigurationProvider: SharedConfigurationProvider
) : Facade {

    private val configHandlers = mutableMapOf<String, SharedSettingsReference<*>>()

    init {
        val jsonSchemaLoader = JsonSchemaLoader()
        val knownSchemes = jsonSchemaLoader.loadGeneratedSchemes().associateBy { it.name }

        sharedSettingsProvider.domains.forEach { (type, settings) ->
            registerSettingsWatcher(
                DefaultSharedSettingsReference(
                    type = type,
                    schema = knownSchemes[type.qualifiedName]
                        ?.let { Supplier { it.getContent() } }
                        ?: run {
                            journalist.logger.warn("[SharedConfigurationFacade] Cannot find scheme for $type, scheme has to be generated at runtime")
                            val scheme = schemaGenerator.value.generateSchema(type.java).also { cleanupScheme(it) }.toPrettyString()
                            Supplier { scheme.byteInputStream() }
                        },
                    getter = { settings.get() },
                    setter = { settings.update(it) }
                )
            )
        }
    }

    private fun <T : SharedSettings> registerSettingsWatcher(handler: SharedSettingsReference<T>): SharedSettingsReference<T> =
        handler.also {
            require(it.name !in configHandlers) { "There are already settings with that name! Please report to the plugin author." }
            configHandlers[it.name] = it
        }

    open class SharedSettingsUpdateException(
        val errors: Collection<Pair<SharedSettingsReference<*>, Exception>>
    ) : IllegalStateException("Cannot load shared configuration from file (${errors.size} errors):\n${errors.joinToString(System.lineSeparator())}")

    fun fetchConfiguration(): String =
        sharedConfigurationProvider.fetchConfiguration()

    internal fun loadSharedSettingsFromString(content: String): Result<Unit, SharedSettingsUpdateException> {
        val updateResult = supplyThrowing { DEFAULT_OBJECT_MAPPER.readTree(content) }
            .map { node -> getDomainNames().asSequence().filter { node.has(it) }.associateWith { node.get(it) } }
            .orElseGet { emptyMap() }
            .map { (name, obj) ->
                val ref = getSettingsReference<SharedSettings>(name)!!
                val settings = DEFAULT_OBJECT_MAPPER.readValue(obj.toString(), ref.type.java)
                ref to ref.update(settings)
            }

        updateResult
            .asSequence()
            .filter { (_, result) -> result.isOk }
            .joinToString(separator = ", ") { (ref) -> "'${ref.name}'" }
            .let { journalist.logger.info("Domains $it have been loaded from ${sharedConfigurationProvider.name()}") }

        val failures = updateResult
            .filter { (_, result) -> result.isErr }

        failures.forEach { (ref, result) ->
            journalist.logger.error("Shared configuration | Cannot update '${ref.name}' due to ${result.error}")
            journalist.logger.debug("Shared configuration | Source:")
            journalist.logger.debug(content)
            failureFacade.throwException("Shared configuration", result.error)
        }

        return failures
            .map { (ref, result) -> ref to result.error }
            .takeIf { it.isNotEmpty() }
            ?.let { SharedSettingsUpdateException(errors = it).asError() }
            ?: ok()
    }

    fun <S : SharedSettings> updateSharedSettings(name: String, body: S): Result<S, out Exception>? =
        getSettingsReference<S>(name)
            ?.update(body)
            ?.peek { sharedConfigurationProvider.updateConfiguration(renderConfiguration()) }

    private fun renderConfiguration(): String =
        getDomainNames()
            .associateWith { getSettingsReference<SharedSettings>(it)!!.get() }
            .let { DEFAULT_OBJECT_MAPPER.writeValueAsString(it) }

    inline fun <reified T : SharedSettings> getDomainSettings(): MutableReference<T> =
        getDomainSettings(T::class)

    @Suppress("UNCHECKED_CAST")
    fun <S : SharedSettings> getDomainSettings(settingsClass: KClass<S>): MutableReference<S> =
        sharedSettingsProvider.domains[settingsClass] as MutableReference<S>

    @Suppress("UNCHECKED_CAST")
    fun <S : SharedSettings> getSettingsReference(name: String): SharedSettingsReference<S>? =
        configHandlers[name] as? SharedSettingsReference<S>

    fun getDomainNames(): Collection<String> =
        configHandlers.keys

    fun isUpdateRequired(): Boolean =
        sharedConfigurationProvider.isUpdateRequired()

    fun isMutable(): Boolean =
        sharedConfigurationProvider.isMutable()

    fun getProviderName(): String =
        sharedConfigurationProvider.name()

}
