import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// La clave se lee de local.properties (no se versiona) o de la variable de entorno GEMINI_API_KEY.
// Nota: cualquier clave dentro de un APK se puede extraer; para producción conviene usar un proxy.
val localProperties = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY")
    ?: System.getenv("GEMINI_API_KEY")
    ?: ""
// Clave aparte para describir el entorno (objetos) en la cámara normal.
val geminiEntornoApiKey: String = localProperties.getProperty("GEMINI_ENTORNO_API_KEY")
    ?: System.getenv("GEMINI_ENTORNO_API_KEY")
    ?: ""
// Clave aparte para describir las imágenes de una captura de pantalla (doble volumen arriba).
val geminiPantallaApiKey: String = localProperties.getProperty("GEMINI_PANTALLA_API_KEY")
    ?: System.getenv("GEMINI_PANTALLA_API_KEY")
    ?: ""

android {
    namespace = "com.cecapi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cecapi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Placeholder: point this at YOUR backend proxy, never at api.anthropic.com
        // directly — an Anthropic API key must never ship inside a mobile client.
        // See feature/aiassistant/AiAssistantApi.kt for the expected request/response shape.
        buildConfigField("String", "AI_PROXY_BASE_URL", "\"http://TU-IP-LOCAL:8000/preguntar\"")
        // Clave de Gemini usada solo para reconocer monedas cuando se pide por voz.
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        // Clave de Gemini usada para describir objetos en la cámara normal (1 principal + 2 secundarios).
        buildConfigField("String", "GEMINI_ENTORNO_API_KEY", "\"$geminiEntornoApiKey\"")
        // Clave de Gemini usada para describir las imágenes de la pantalla (accesibilidad).
        buildConfigField("String", "GEMINI_PANTALLA_API_KEY", "\"$geminiPantallaApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.tensorflow.lite.task.vision) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.tensorflow.lite)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}
