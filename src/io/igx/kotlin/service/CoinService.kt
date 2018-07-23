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