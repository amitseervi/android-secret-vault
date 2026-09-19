package com.rignis.auth.data

import androidx.fragment.app.FragmentActivity

// Empty host Activity so instrumented tests can construct CipherManagerImpl
// (which needs a FragmentActivity) without pulling in the real app module.
class TestFragmentActivity : FragmentActivity()
