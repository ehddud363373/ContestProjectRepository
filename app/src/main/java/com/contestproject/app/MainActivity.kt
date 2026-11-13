package com.contestproject.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.contestproject.app.presentation.NapViewModel
import com.contestproject.app.presentation.ShimpyoRoot

class MainActivity : ComponentActivity() {

    private val viewModel: NapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pleos 심사 기준: 최상위 화면에서 CCP Back은 finish() 대신 moveTaskToBack(true).
        // 화면 안의 Back 처리(어둡게 덮은 화면 닫기 등)는 Compose BackHandler가 먼저 가져간다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent { ShimpyoRoot(viewModel) }
    }
}
