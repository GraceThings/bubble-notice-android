// 顶层构建文件，可在此添加适用于所有子项目/模块的通用配置。 / Top-level build file for configuration options common to all modules.
plugins {
    alias(libs.plugins.android.application) apply false
    
    alias(libs.plugins.kotlin.compose) apply false
}
