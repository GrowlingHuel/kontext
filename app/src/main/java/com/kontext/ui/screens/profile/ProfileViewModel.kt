package com.kontext.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.kontext.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val sessionManager: SessionManager
) : ViewModel()
