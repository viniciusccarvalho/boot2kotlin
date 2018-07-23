package io.igx.kotlin.controllers

import io.igx.kotlin.domain.TickerRequest
import io.igx.kotlin.service.CoinService
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.routing.routing
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import io.ktor.locations.*
import io.ktor.response.respond

/**
 * @author vinicius
 *
 */
class CoinController(override val kodein: Kodein) : KodeinAware {

    val app: Application by instance()
    val service: CoinService by instance("coinService")

    init {
        app.routing {
           get<TickerRequest> { request ->
                call.respond(service.find(request.symbol, request.start.atStartOfDay(), request.end.atTime(23, 59, 59)))
           }
        }
    }
}