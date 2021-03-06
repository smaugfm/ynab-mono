package com.github.smaugfm

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.smaugfm.events.EventDispatcher
import com.github.smaugfm.mono.MonoApi
import com.github.smaugfm.mono.MonoApi.Companion.setupWebhookAll
import com.github.smaugfm.settings.Settings
import com.github.smaugfm.telegram.TelegramApi
import com.github.smaugfm.telegram.handlers.TelegramHandlers
import com.github.smaugfm.telegram.handlers.errorHandler
import com.github.smaugfm.ynab.YnabApi
import com.github.smaugfm.ynab.handlers.YnabHandlers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

class YnabMono : CliktCommand() {
    val setWebhook by option().flag(default = false)
    val monoWebhookUrl by option().convert { URI(it) }.required()
    val monoWebhookPort by option().int()
    val settings by option("--settings").convert { Settings.load(Paths.get(it)) }.default(Settings.loadDefault())

    private val serversCoroutinesContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    override fun run() {
        logger.info(
            "Input arguments:\n\t" +
                "${this::settings.name}: $settings\n\t" +
                "${this::monoWebhookUrl.name}: $monoWebhookUrl\n\t" +
                "${this::monoWebhookPort.name}: $monoWebhookPort\n\t" +
                "${this::setWebhook.name}: $setWebhook",
        )

        runBlocking {
            val monoApis = settings.monoTokens.map(::MonoApi)
            logger.info("Created monobank apis. ")
            val telegramApi = TelegramApi(
                settings.telegramBotUsername,
                settings.telegramBotToken,
            )
            logger.info("Created telegram api.")
            val ynabApi = YnabApi(settings.ynabToken, settings.ynabBudgetId)
            logger.info("Created ynab api.")

            if (setWebhook) {
                logger.info("Setting up mono webhooks.")
                if (!monoApis.setupWebhookAll(monoWebhookUrl, monoWebhookPort ?: monoWebhookUrl.port))
                    return@runBlocking
            } else {
                logger.info("Skipping mono webhook setup.")
            }

            val dispatcher = EventDispatcher(
                { dispatcher, _, _ ->
                    errorHandler(dispatcher, settings.mappings)
                },
                YnabHandlers(ynabApi, settings.mappings),
                TelegramHandlers(telegramApi, settings.mappings)
            )

            logger.info("Events dispatcher created.")

            val telegramServerJob = telegramApi
                .startServer(serversCoroutinesContext, dispatcher)

            logger.info("Telegram bot started.")
            val monoWebhookServer =
                MonoApi.startMonoWebhookServerAsync(
                    serversCoroutinesContext,
                    monoWebhookUrl,
                    monoWebhookPort ?: monoWebhookUrl.port,
                    dispatcher
                )
            logger.info("Mono webhook listener started.")
            logger.info("Setup completed. Listening...\n")

            telegramServerJob.join()
            monoWebhookServer.join()
        }
    }
}

fun main(args: Array<String>) {
    YnabMono().main(args)
}
