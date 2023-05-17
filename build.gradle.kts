plugins {
    kotlin("multiplatform") version "1.7.22"
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    `maven-publish`
}

group = "com.darkyen"
version = "0.6"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(8)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromiumHeadless()
                    useFirefoxDeveloperHeadless()
                }
            }
        }
    }
    
    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

kover {
    isDisabled.set(false)

    htmlReport {
        onCheck.set(true)
    }
}


