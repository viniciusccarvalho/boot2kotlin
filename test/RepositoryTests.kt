import io.igx.kotlin.config.common
import io.igx.kotlin.config.repositories
import io.igx.kotlin.repository.CoinRepository
import org.junit.Test
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * @author Vinicius Carvalho
 *
 */
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