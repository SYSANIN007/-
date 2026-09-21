// Корневой build-файл проекта «ACID Wallet».
// Все плагины объявлены здесь и применяются в модуле :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
