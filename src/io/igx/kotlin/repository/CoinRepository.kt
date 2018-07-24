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
        return collection.find(Ticker::symbol eq symbol, Ticker::lastUpdated gt start, Ticker::lastUpdated lt end).sort(ascending(Ticker::lastUpdated))
                .toCollection(mutableListOf())
    }
}