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