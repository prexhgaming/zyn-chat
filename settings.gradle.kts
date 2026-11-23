pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Stream Chat SDK / AI artifacts.
        maven("https://stream-io-repo.com")
    }
}

rootProject.name = "zyn-chat"

// A single module: the app, the compose component tree and the on-device model tree all live in
// :zyn-chat-app, so there is exactly one build script and one test invocation.
include(":zyn-chat-app")
