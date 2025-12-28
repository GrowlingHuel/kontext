package com.kontext.data.repository

import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signOut()
    suspend fun getCurrentUser(): String? // Returns email or User ID
}

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepositoryImpl @Inject constructor() : AuthRepository {
    // Mock implementation until dependencies are fixed
    private var simulatedUser: String? = null

    override suspend fun signUp(email: String, password: String): AuthResult {
        if (email.isBlank() || password.isBlank()) return AuthResult.Error("Invalid credentials")
        simulatedUser = email
        return AuthResult.Success
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        // Accept any non-empty credential for mock
        if (email.isBlank() || password.isBlank()) return AuthResult.Error("Invalid credentials")
        simulatedUser = email
        return AuthResult.Success
    }

    override suspend fun signOut() {
        simulatedUser = null
    }

    override suspend fun getCurrentUser(): String? {
        return simulatedUser
    }
}
