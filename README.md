# Introduction
   
Spring boot is probably the most successful framework I've seen in my entire career. I have been a spring user since 2004, and I even had a small gig as a Spring committer not too long ago.

[Spring also loves Kotlin](https://www.infoq.com/presentations/spring-kotlin-boot), there's a great synergy between the framework and the language. The two teams have been working really close to each other to make it a seamless integration, it's much better now than it was a while back with lots of reflection issues.

But ever since I started writing code in kotlin, one thing could not get out of my head: _What would it take to write an application without Spring these days?_

And that is my goal with this post, to introduce you to a few kotlin frameworks that when combined together can give you a lot of what a modern microservice (sorry) toolkit.

# The application

We are about to create a very simple REST application that reads data from mongodb and presents it via RESTful interface.

The goal here is not to talk about the application itself but instead on how you could start putting things together without leveraging Spring Boot convenience.

I believe that the best way to learn new concepts is by introducing them on a context that you are already familiar with, that's why I decided to follow a very common approach found on many Spring applications when it comes to architectural design of each layer. More experienced ktor developers may find this awkward but I found it to be a very simple way to translate my current mindset into a new framework.

As most spring applications we will have the following components:

* A main class to bootstrap the container(s)
* A configuration class (or modules)
* A Repository
* A Service
* A Controller

## Pre requisites
You will need of a mongodb server, there's a file named `bitcoin.csv` at the root of the project folder. That file contains the price of a BTC coin from Jan 2018 - Jul 2018. Please import it using: `mongoimport -d kotlin -c coins --type csv --file bitcoin.csv --headerline --columnsHaveType`, check the [mongodb docs](https://docs.mongodb.com/manual/reference/program/mongoimport/) if you require more information on how to use the `mongoimport` tool

# Getting started

The full project source code is available [here](https://github.com/viniciusccarvalho/boot2kotlin).

We are going to rely on 3 kotlin frameworks

* [ktor](http://ktor.io/): An async web stack for kotlin
* [kodein](http://kodein.org/): A DI framework to help us with dependency injection
* [kmongo](http://litote.org/kmongo/): Provides extensions to the mongo client

KTor has a nice [generator](https://ktor.io/quickstart/generator.html#), looks very similar to the Spring initializr. Visit the generator e create a sample project using the following configurations:
* Group : io.igx.kotlin (that's the package of the sample app, feel free to change it, but copying from github repo may require changing package names)
* Server Features select the following : Static Content, Locations,  Default Headers, GSON

Click on Build and download the zip file.

Open the project on your IDE and add the following dependencies to your build.gradle
```
compile "org.kodein.di:kodein-di-generic-jvm:5.2.0"
compile "org.litote.kmongo:kmongo-native:3.8.1"
```

Don't worry too much about the generated file called `application.kt` we are going to change it quite a bit soon, let's now focus on dependency injection ...

## Dependency Injection with Kodein
The [kodein](http://kodein.org/) docs are very comprehensive I won't attempt to explain everything here, but instead I'll try to trace some parallels with Spring.
Kodein has a context, much like Spring, and it can be separated into modules (which I believe one can relate with `@Configuration` files in Spring that spawns sub context of a parent context)
In the project there's one file `KodeinModules.kt` that has all the modules we'll need, a sample of that file:

```kotlin
val common = Kodein.Module(name = "common") {
   bind("mongoClient") from singleton { KMongo.createClient() }
}

val repositories = Kodein.Module(name = "repositories") {
   bind(tag = "coinRepository") from singleton { CoinRepository(instance("mongoClient")) }
}

val services = Kodein.Module(name = "services") {
   bind(tag="coinService") from singleton { CoinService(instance("coinRepository")) }
}

```
   
This file creates three modules (for now) and wire them together, the classes containing the code for the `CoinService` and `CoinRepository` are listed below
   
```kotlin
package io.igx.kotlin.repository

import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import io.igx.kotlin.domain.Ticker
import org.litote.kmongo.*
import java.time.LocalDateTime

/**
 * @author vinicius
 *
 */
class CoinRepository(val client: MongoClient) {

    val collection: MongoCollection<Ticker> = client.getDatabase("kotlin").getCollection<Ticker>("coins")

    fun findInRange(symbol: String, start: LocalDateTime, end: LocalDateTime) : List<Ticker> {
        return collection.find(Ticker::symbol eq symbol, Ticker::lastUpdated gt start, Ticker::lastUpdated lt end).sort(ascending(Ticker::lastUpdated)).toList()
    }
}
```

```kotlin
package io.igx.kotlin.service

import io.igx.kotlin.domain.Ticker
import io.igx.kotlin.repository.CoinRepository
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author vinicius
 *
 */
class CoinService(val repository: CoinRepository) {

    fun find(symbol: String, start: LocalDateTime, end: LocalDateTime) : List<Ticker> {
        if(Duration.between(end, start).toDays() > 31){
            throw IllegalStateException("Maximum number of days for full queries can not exceed 31 calendar days")
        }
        return repository.findInRange(symbol, start, end)
    }

}

```

And for the project to compile you will need the `Model.kt` file with definition of our domain objects
```kotlin
package io.igx.kotlin.domain

import io.ktor.locations.Location
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * @author vinicius
 *
 */
data class Ticker(val symbol: String, val name: String, val price: Double, val marketCap: Double, val lastUpdated: LocalDateTime)

@Location("/coins/{symbol}")
data class TickerRequest(val symbol: String, val start: LocalDate, val end: LocalDate ) 
```

For now ignore the `@Location` declaration we will get back to it soon.

After wiring those files, you have a fully working `Kodein` application, you can write a very simple unit test to give it a try such as the one 
in the `RepositoryTests` class in the test folder

```kotlin
class RepositoryTests {

    val context = Kodein{
        import(common)
        import(repositories)
    }

    @Test
    fun testFindInRange() {
        val repository: CoinRepository by context.instance("coinRepository")
        val results = repository.findInRange("BTC", LocalDateTime.of(2018, 6, 1, 0, 0) ,LocalDateTime.now())
        assertTrue { results.isNotEmpty() }
    }
}
```
That's all it needs to start kodein context and run your tests, we use `import` on the kodein declaration to import only the modules we want to test
this will not add other modules such as the `services` or the `controllers` modules into the context. 

## Async web server with ktor

[Ktor](http://ktor.io/) is a framework to build async servers, HTTP being one of the many flavors.

The original `application.kt` looked like this:

```kotlin
fun main(args: Array<String>): Unit = io.ktor.server.netty.DevelopmentEngine.main(args)


fun Application.module() {
	install(ContentNegotiation) {
		gson {
		}
	}

	install(Locations) {
	}

	install(DefaultHeaders) {
		header("X-Engine", "Ktor") // will send this header with each response
	}

	routing {
		get("/") {
			call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
		}

		// Static feature. Try to access `/static/ktor_logo.svg`
		static("/static") {
			resources("static")
		}

		get("/json/gson") {
			call.respond(mapOf("hello" to "world"))
		}

		get<MyLocation> {
			call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
		}
	}
}

```

This would use the `HOCON` file to [bootstrap](http://ktor.io/servers/configuration.html) the right module configuration and install all the [features](http://ktor.io/features/index.html).
But we need to bootstrap kodein as well.


Integrating `ktor` and `kodein` requires a bit of fiddling on the `ktor` side. Both frameworks have similar concepts of `Application` and `Module`
but we need to make sure that the entry point of those two frameworks are configured correctly.

Ktor has a very flexible mechanism to modify it's [lifecycle](http://ktor.io/servers/lifecycle.html), so we will use that as the entry point to start
the server and leverage kotlin receiver functions to mix Kodein code within the application.


Let's modify the file `application.kt` so that it looks like this now:

```kotlin
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

```

The `kodeinApplication` function is where my mind blew! Kudos to the ktor devs on slack for sharing that. Basically this is a high order function 
which receiver will register within kodein context the Application object from ktor. 
After this, you can pass it to the `embededdServer` as a way to configure the applicaiton via the implicit object or to import kodein modules
as you would do on a regular `Kodein Builder`

The important bits here are really the `import` of kodein modules and the `kodeinApplication` function.

Let's not forget about our controllers declaration, if you go back to `KodeinModule.kt` add this section:

```kotlin
val controllers = Kodein.Module(name = "controllers") {
    bind(tag="coinController") from eagerSingleton { CoinController(kodein) }
}
```

This time we used a `eagerSingleton` since we will register the routes within our Controller during startup, otherwise you would not be able to access the route.

The `CoinController`
```kotlin
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
```

We are mapping a `get` operation using the [locations](http://ktor.io/features/locations.html) feature to bind a TypeSafe data class `TickerRequest` 
to the request operation:
```kotlin
@Location("/coins/{symbol}")
data class TickerRequest(val symbol: String, val start: LocalDate, val end: LocalDate )
```

The section `application.install(DataConversion){` on the bootstrapping by the way is to handle with the conversion of a query string on the `start`
and `end` parameters to `LocalDate`

Modify your build.gradle `mainClassName` section now:

```mainClassName = "io.igx.kotlin.ApplicationKt"```

# Running it
It's time to give it a try, just run `./gradlew build` and launch the server via the fat-jar built on the `/build/libs/` folder:

`java -jar build/libs/boot2kotlin-0.0.1-all.jar`

# Final thoughts

As with everything new, it's odd at first, not having Spring Boot to configure everything for you feels a bit daunting. But then the strange
becomes normal, and even simple. After a couple of days I feel very confident that I have another stack to give it a try and experiment. It's always good
to have more than one tool at hand.
