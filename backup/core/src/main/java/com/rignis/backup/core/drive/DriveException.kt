package com.rignis.backup.core.drive

sealed class DriveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkFailure(cause: Throwable) : DriveException("Could not reach Google Drive", cause)
    class AuthRequired(val httpCode: Int) : DriveException("Drive access token was rejected (HTTP $httpCode)")
    class RateLimited(val httpCode: Int) : DriveException("Drive rate limit or quota exceeded (HTTP $httpCode)")
    class ServerError(val httpCode: Int) : DriveException("Drive server error (HTTP $httpCode)")
    class NotFound(val fileId: String) : DriveException("Drive file not found: $fileId")
    class UnexpectedResponse(val httpCode: Int) : DriveException("Unexpected Drive response (HTTP $httpCode)")
}
