package com.github.kittinunf.result

import com.github.kittinunf.result.Kind.Failure
import com.github.kittinunf.result.Kind.Success

inline fun <V> Result<V, *>.success(f: (V) -> Unit) = fold(f, {})

inline fun <E : Throwable> Result<*, E>.failure(f: (E) -> Unit) = fold({}, f)

fun Result<*, *>.isSuccess() = this is Result.Success

fun Result<*, *>.isFailure() = this is Result.Failure

fun <V, E : Throwable> Result<V, E>.getOrNull(): V? = when (this) {
    is Result.Success -> value
    is Result.Failure -> null
}

fun <V, E : Throwable> Result<V, E>.getFailureOrNull(): E? = when (this) {
    is Result.Success -> null
    is Result.Failure -> error
}

inline infix fun <V, E : Exception> Result<V, E>.getOrElse(fallback: (E) -> V): V = when (this) {
    is Result.Success -> value
    is Result.Failure -> fallback(error)
}

inline fun <V, U, reified E : Throwable> Result<V, E>.map(transform: (V) -> U): Result<U, E> = try {
    when (this) {
        is Result.Success -> Result.success(transform(value))
        is Result.Failure -> Result.failure(error)
    }
} catch (ex: Exception) {
    when (ex) {
        is E -> Result.failure(ex)
        else -> throw ex
    }
}

inline fun <V, reified E : Throwable, reified EE : Throwable> Result<V, E>.mapError(transform: (E) -> EE): Result<V, EE> = try {
    when (this) {
        is Result.Success -> Result.success(value)
        is Result.Failure -> Result.failure(transform(error))
    }
} catch (ex: Exception) {
    when (ex) {
        is EE -> Result.failure(ex)
        else -> throw ex
    }
}

inline fun <V, U, reified E : Throwable> Result<V, E>.flatMap(transform: (V) -> Result<U, E>): Result<U, E> = try {
    when (this) {
        is Result.Success -> transform(value)
        is Result.Failure -> Result.failure(error)
    }
} catch (ex: Exception) {
    when (ex) {
        is E -> Result.failure(ex)
        else -> throw ex
    }
}

inline fun <V, reified E : Throwable, reified EE : Throwable> Result<V, E>.flatMapError(transform: (E) -> Result<V, EE>): Result<V, EE> = try {
    when (this) {
        is Result.Success -> Result.success(value)
        is Result.Failure -> transform(error)
    }
} catch (ex: Exception) {
    when (ex) {
        is EE -> Result.failure(ex)
        else -> throw ex
    }
}

inline fun <V, E : Throwable> Result<V, E>.onFailure(f: (E) -> Unit): Result<V, E> {
    when (this) {
        is Result.Success -> {
        }
        is Result.Failure -> {
            f(error)
        }
    }
    return this
}

inline fun <V, E : Throwable> Result<V, E>.onSuccess(f: (V) -> Unit): Result<V, E> {
    when (this) {
        is Result.Success -> {
            f(value)
        }
        is Result.Failure -> {
        }
    }
    return this
}

inline fun <V, U> Result<V, *>.fanout(other: () -> Result<U, *>): Result<Pair<V, U>, *> =
    flatMap { outer ->
        other().map { outer to it }
    }

inline fun <V, reified E : Throwable> List<Result<V, E>>.lift(): Result<List<V>, E> =
    fold(Result.success(mutableListOf<V>()) as Result<MutableList<V>, E>) { acc, result ->
        acc.flatMap { combine ->
            result.map {
                combine.apply { add(it) }
            }
        }
    }

inline fun <V, E : Throwable> Result<V, E>.any(predicate: (V) -> Boolean): Boolean = try {
    when (this) {
        is Result.Success -> predicate(value)
        is Result.Failure -> false
    }
} catch (ex: Throwable) {
    false
}

enum class Kind {
    Success,
    Failure
}

sealed class Result<out V, out E : Throwable> {

    open operator fun component1(): V? = null
    open operator fun component2(): E? = null

    inline fun <X> fold(success: (V) -> X, failure: (E) -> X): X = when (this) {
        is Success -> success(value)
        is Failure -> failure(error)
    }

    abstract fun get(): V

    abstract val kind: Kind

    class Success<out V : Any?> internal constructor(val value: V) : Result<V, Nothing>() {

        override val kind: Kind = Success

        override fun component1(): V = value

        override fun get(): V = value

        override fun toString() = "[Success: $value]"

        override fun hashCode(): Int = value.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Success<*> && value == other.value
        }
    }

    class Failure<out E : Throwable> internal constructor(val error: E) : Result<Nothing, E>() {

        override val kind: Kind = Failure

        override fun component2(): E = error

        override fun get() = throw error

        override fun toString() = "[Failure: $error]"

        override fun hashCode(): Int = error.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Failure<*> && error == other.error
        }
    }

    companion object {
        // Factory methods
        fun <E : Throwable> failure(throwable: E) = Failure(throwable)
        fun <V> success(value: V) = Success(value)

        @Suppress("UNCHECKED_CAST")
        inline fun <V, reified E : Throwable> of(noinline f: () -> V?): Result<V, E> = try {
            success(f()) as Result<V, E>
        } catch (ex: Exception) {
            when (ex) {
                is E -> failure(ex)
                else -> throw ex
            }
        }
    }
}

