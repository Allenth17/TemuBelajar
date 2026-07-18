package com.hiralen.temubelajar.core.domain

/**
 * Sealed class untuk wrapping hasil operasi async.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val error: String) : Result<Nothing>()
}

/** Kembalikan data jika Success, null jika Error. Receiver adalah Result<*> untuk flexibility. */
@Suppress("UNCHECKED_CAST")
fun <T> Result<*>.getOrNull(): T? = if (this is Result.Success<*>) this.data as? T else null

/** Kembalikan error message jika Error, null jika Success. Receiver adalah Result<*>. */
fun Result<*>.errorOrNull(): String? = if (this is Result.Error) this.error else null
