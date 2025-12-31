package com.kontext.util

/**
 * A generic wrapper for handling success/error states in Repository operations.
 * Replaces nullable returns and scattered exception handling with a unified type.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Exception,
        val message: String = exception.message ?: "Unknown error"
    ) : Result<Nothing>()
    
    object Loading : Result<Nothing>()
}

/**
 * Extension function to execute an action if Result is Success.
 * Returns the original Result to allow chaining.
 */
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

/**
 * Extension function to execute an action if Result is Error.
 * Returns the original Result to allow chaining.
 */
fun <T> Result<T>.onError(action: (Exception, String) -> Unit): Result<T> {
    if (this is Result.Error) action(exception, message)
    return this
}

/**
 * Maps the data of a successful Result to a new type.
 * Propagates Error unchanged.
 */
fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }
}

/**
 * Returns the data if Success, or null if Error/Loading.
 */
fun <T> Result<T>.getOrNull(): T? {
    return if (this is Result.Success) data else null
}

/**
 * Returns the data if Success, or throws the exception if Error.
 */
fun <T> Result<T>.getOrThrow(): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> throw IllegalStateException("Result is still loading")
    }
}
