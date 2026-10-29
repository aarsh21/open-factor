pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "OpenFactor"
include(":app")
// An external-origin provider fixture for opt-in Health Connect integration checks.
include(":healthFixture")
