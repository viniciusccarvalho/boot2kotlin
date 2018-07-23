package io.igx.kotlin.config

import io.igx.kotlin.controllers.CoinController
import io.igx.kotlin.repository.CoinRepository
import io.igx.kotlin.service.CoinService
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.eagerSingleton
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.litote.kmongo.KMongo

/**
 * @author vinicius
 *
 */

val common = Kodein.Module(name = "common") {
    bind("mongoClient") from singleton { KMongo.createClient() }
}

val repositories = Kodein.Module(name = "repositories") {
    bind(tag = "coinRepository") from singleton { CoinRepository(instance("mongoClient")) }
}

val services = Kodein.Module(name = "services") {
    bind(tag="coinService") from singleton { CoinService(instance("coinRepository")) }
}

val controllers = Kodein.Module(name = "controllers") {
    bind(tag="coinController") from eagerSingleton { CoinController(kodein) }
}