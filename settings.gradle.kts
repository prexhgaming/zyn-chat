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

// The two library modules are the reusable core. An app that consumes these components from
// source (as an included build) gets only the libraries, so no second application module
// shows up beside it in the IDE.
include(
    ":zyn-chat-compose",
    ":zyn-chat-ondevice",
)

if (gradle.parent == null) {
    include(":zyn-chat-app")
}
