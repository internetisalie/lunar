package net.internetisalie.lunar.redis.resp

/**
 * Typed failure surface for the RESP client and codec (design §2.10).
 *
 * Every failure the client raises is one of these subtypes — never a raw [java.net.SocketTimeoutException]
 * or a bare [java.io.IOException] — so callers can classify without inspecting messages, and the no-`!!`
 * contract (engineering-contract §1) is honoured by returning typed errors instead of unsafe dereferences.
 */
sealed class RespException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** A connect or read exceeded its configured timeout; [op] names the operation. */
    class Timeout(op: String) : RespException("Redis $op timed out")

    /** The reply bytes did not match the RESP grammar; [detail] describes the violation. */
    class Protocol(detail: String) : RespException("Malformed RESP reply: $detail")

    /** An underlying socket/stream I/O failure. */
    class Io(cause: Throwable) : RespException("Redis connection I/O error", cause)

    /** The connected server predates a required capability (e.g. `EVAL_RO`); [required] names the floor. */
    class ServerVersion(required: String) :
        RespException("This server does not support this operation (requires $required)")
}
