package io.igx.kotlin

import io.igx.kotlin.config.common
import io.igx.kotlin.config.controllers
import io.igx.kotlin.config.repositories
import io.igx.kotlin.config.services
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.server.engine.*
import io.ktor.gson.*
import io.ktor.locations.Locations
import io.ktor.server.netty.Netty
import io.ktor.util.DataConversionException
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.singleton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main(args: Array<String>){
	embeddedServer(Netty, port = 8080) {
		kodeinApplication{
			application ->
			application.install(ContentNegotiation) {
				gson {
					setDateFormat("yyy-MM-dd HH:mm:ss")
				}
			}
			application.install(DefaultHeaders){
				header("X-Engine", "Ktor")
			}

			application.install(Locations)
			application.install(DataConversion){
				convert<LocalDate> {
					val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")
					decode { values, _ ->
						values.singleOrNull()?.let { LocalDate.parse(it, format) }
					}
					encode { value ->
						when(value) {
							null -> listOf()
							is LocalDate -> listOf(value.format(format))
							else -> throw DataConversionException("Cannot convert $value as Date")
						}
					}
				}
			}
			import(common)
			import(repositories)
			import(services)
			import(controllers)
		}
	}.start(wait = true)
}


fun Application.kodeinApplication(kodeinMapper : Kodein.MainBuilder.(Application) -> Unit = {}) {
	val app = this
	val kodein = Kodein {
		bind<Application>() with singleton { app }
		kodeinMapper(this, app)
	}
}

