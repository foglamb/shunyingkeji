buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven { url = uri("https://api.xposed.info") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven { url = uri("https://api.xposed.info") }
    }
}
