package com.kontext.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kontext.data.local.SessionManager

@Composable
fun ProfileScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is AuthUiState.Loading -> CircularProgressIndicator()
            is AuthUiState.Authenticated -> {
                AuthenticatedContent(
                    email = state.email,
                    onSignOut = viewModel::signOut
                )
            }
            else -> {
                UnauthenticatedContent(
                    onSignUp = viewModel::signUp,
                    onSignIn = viewModel::signIn,
                    error = (state as? AuthUiState.Error)?.message
                )
            }
        }
    }
}

@Composable
fun AuthenticatedContent(email: String, onSignOut: () -> Unit) {
    val sessionManager: SessionManager = hiltViewModel<ProfileViewModel>().sessionManager
    val currentLanguageCode = remember { sessionManager.getLanguageCode() }
    var selectedLanguageCode by remember { mutableStateOf(currentLanguageCode) }
    var showRestartPrompt by remember { mutableStateOf(false) }
    
    Text("Welcome,", style = MaterialTheme.typography.headlineSmall)
    Text(email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Language Selection Section
    Text("Learning Language", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    
    Column(modifier = Modifier.fillMaxWidth()) {
        LanguageOption(
            languageCode = "de",
            languageName = "German",
            isSelected = selectedLanguageCode == "de",
            onClick = {
                selectedLanguageCode = "de"
                sessionManager.setLanguageCode("de")
                showRestartPrompt = true
            }
        )
        LanguageOption(
            languageCode = "es",
            languageName = "Spanish",
            isSelected = selectedLanguageCode == "es",
            onClick = {
                selectedLanguageCode = "es"
                sessionManager.setLanguageCode("es")
                showRestartPrompt = true
            }
        )
        LanguageOption(
            languageCode = "fr",
            languageName = "French",
            isSelected = selectedLanguageCode == "fr",
            onClick = {
                selectedLanguageCode = "fr"
                sessionManager.setLanguageCode("fr")
                showRestartPrompt = true
            }
        )
    }
    
    if (showRestartPrompt) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "⚠️ Please restart the app for language change to take effect",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onSignOut) {
        Text("Sign Out")
    }
}

@Composable
fun LanguageOption(
    languageCode: String,
    languageName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    ) {
        Text(
            text = "$languageName ($languageCode)",
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun UnauthenticatedContent(
    onSignUp: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    error: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    Text(
        text = if (isSignUp) "Create Account" else "Welcome Back",
        style = MaterialTheme.typography.headlineMedium
    )
    Spacer(modifier = Modifier.height(32.dp))

    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            if (isSignUp) onSignUp(email, password) else onSignIn(email, password)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isSignUp) "Sign Up" else "Log In")
    }

    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = { isSignUp = !isSignUp }) {
        Text(if (isSignUp) "Already have an account? Log In" else "Don't have a specific account? Sign Up")
    }
}
