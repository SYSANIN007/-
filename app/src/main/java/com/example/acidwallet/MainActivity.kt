package com.example.acidwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.acidwallet.ui.AcidWalletApp
import com.example.acidwallet.ui.WalletViewModel
import com.example.acidwallet.ui.theme.AcidWalletTheme

/**
 * Единственная Activity приложения.
 *
 * Вся логика — в [WalletViewModel] (репозиторий + Room), вся отрисовка — в
 * Compose-экранах. Activity только собирает их вместе и задаёт тему.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AcidWalletTheme {
                val viewModel: WalletViewModel = viewModel()
                AcidWalletApp(viewModel)
            }
        }
    }
}
