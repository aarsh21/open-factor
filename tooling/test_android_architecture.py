import importlib.util
from pathlib import Path
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("android_architecture", Path(__file__).with_name("verify_android_architecture.py"))
architecture = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(architecture)


class AndroidArchitectureTest(unittest.TestCase):
    def check(self, path, text, baseline=()):
        return architecture.source_violations(path, text, set(baseline))

    def test_portable_core_and_android_host_are_allowed(self):
        self.assertEqual([], self.check("core/src/main/kotlin/org/openfactor/app/features/workout/Commands.kt", "import kotlinx.coroutines.flow.StateFlow\nclass Commands"))
        self.assertEqual([], self.check("app/src/main/java/org/openfactor/app/features/health/android/Adapter.kt", "import androidx.lifecycle.Lifecycle"))

    def test_aliased_and_fully_qualified_platform_dependencies_are_rejected(self):
        path = "core/src/main/kotlin/org/openfactor/app/features/workout/Commands.kt"
        for code in ("import android.content.Context as Host", "val x = android.os.SystemClock.elapsedRealtime()", "import androidx.compose.runtime.Composable", "@Composable fun content() {}", "val x = BuildConfig.VERSION_NAME"):
            with self.subTest(code=code):
                self.assertTrue(self.check(path, code))

    def test_comments_and_literals_do_not_create_false_platform_edges(self):
        self.assertEqual([], self.check("core/src/main/kotlin/org/openfactor/app/features/media/Location.kt", '// android.net.Uri\nval x = "android.os.SystemClock"'))

    def test_flat_legacy_presentation_is_frozen_not_a_blanket_app_exemption(self):
        path = "app/src/main/java/org/openfactor/app/"
        self.assertEqual([], self.check(path + "Existing.kt", "class Existing", ("Existing.kt",)))
        self.assertTrue(self.check(path + "New.kt", "class New", ("Existing.kt",)))
        self.assertTrue(self.check(path + "services/New.kt", "class New"))

    def test_core_source_requires_a_feature_owner(self):
        self.assertTrue(self.check("core/src/main/kotlin/org/openfactor/app/Helpers.kt", "fun helper() = 1"))

    def test_string_navigation_is_rejected(self):
        self.assertTrue(self.check("app/src/main/java/org/openfactor/app/navigation/State.kt", "val route: String = value"))
        self.assertEqual([], self.check("app/src/main/java/org/openfactor/app/navigation/State.kt", "val destination: AppDestination"))

    def test_real_repository_profile(self):
        self.assertEqual([], architecture.validate(Path(__file__).resolve().parents[1]))

    def test_upward_core_and_consumer_dependencies_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            project = root / "openfactor"
            (project / "core").mkdir(parents=True)
            (project / "coreConsumer").mkdir()
            (project / "architecture.json").write_text('{"legacyAppRootSources":[], "coreDependencyPrefixes":[]}')
            (project / "core/build.gradle.kts").write_text('implementation(project(":app"))')
            (project / "coreConsumer/build.gradle.kts").write_text('testImplementation(project(":app"))')
            self.assertEqual(2, len(architecture.validate(root)))


if __name__ == "__main__":
    unittest.main()
