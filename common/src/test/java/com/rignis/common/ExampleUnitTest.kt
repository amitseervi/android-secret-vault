package com.rignis.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCoroutineCallback() = runTest {
        val result: Result<Boolean> = callbackToSuspend { onSuccess, onError ->
            utility(12, onSuccess, onError)
        }
    }

    fun utility(data: Int, onSuccess: (Result<Boolean>) -> Unit, onError: (e: Exception) -> Unit) {
        if (data == 21) {
            onSuccess(Result.success(true))
        } else {
            onError(RuntimeException("Data is not equal to 21"))
        }
    }
}