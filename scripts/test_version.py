from contextlib import redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from version import main, parse_version, properties, select_version, set_version


MANIFEST = """<project xmlns="http://maven.apache.org/POM/4.0.0">
    <properties>
        <revision>0.3.0</revision>
        <changelist>-SNAPSHOT</changelist>
        <sha1/>
        <unifmu.version>1.1.0</unifmu.version>
    </properties>
</project>
"""


class VersionTest(unittest.TestCase):
    def test_explicit_version(self):
        self.assertEqual(select_version("0.3.0", ["v0.3.0"], "v0.4.0"), "0.4.0")

    def test_next_patch(self):
        self.assertEqual(select_version("0.3.0", ["v0.3.4", "v0.3.9", "v0.3.10"], ""), "0.3.11")

    def test_new_minor_and_major_start_at_zero(self):
        self.assertEqual(select_version("0.3.0", ["Release/0.2.20"], ""), "0.3.0")
        self.assertEqual(select_version("1.0.0", ["v0.3.1"], ""), "1.0.0")

    def test_legacy_and_unrelated_tags(self):
        self.assertEqual(select_version("0.2.0", ["Release/0.2.20", "experiment", "v9.0.0-rc1"], ""), "0.2.21")

    def test_no_tags(self):
        self.assertEqual(select_version("0.3.0", [], ""), "0.3.1")

    def test_reject_duplicate_and_older_versions(self):
        for requested in ("0.3.1", "0.2.0"):
            with self.subTest(requested=requested), self.assertRaises(ValueError):
                select_version("0.3.0", ["v0.3.1"], requested)
        with self.assertRaises(ValueError):
            select_version("0.4.0", ["v0.3.1"], "0.3.2")

    def test_reject_invalid_versions(self):
        for value in ("", "1.2", "01.2.3", "1.2.3-SNAPSHOT", "1.2.3;exit 1", "v1.2.3\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_version(value)

    def test_prepare_preserves_format_and_dependency_version(self):
        updated = set_version(MANIFEST, "0.4.0")
        self.assertEqual(properties(updated), {"revision": "0.4.0", "sha1": "", "changelist": ""})
        self.assertIn("        <unifmu.version>1.1.0</unifmu.version>", updated)

    def test_reject_missing_and_duplicate_properties(self):
        with self.assertRaises(ValueError):
            set_version(MANIFEST.replace("<sha1/>", ""), "0.4.0")
        with self.assertRaises(ValueError):
            set_version(MANIFEST.replace("<sha1/>", "<sha1/><sha1/>"), "0.4.0")

    def test_prepare_command_updates_pom_and_prints_selected_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pom = root / "pom.xml"
            pom.write_text(MANIFEST, encoding="utf-8")
            output = io.StringIO()
            with patch("version.ROOT", root), \
                    patch("sys.argv", ["version.py", "--prepare", "--version", "v0.4.0"]), \
                    patch("version.subprocess.check_output", return_value="Release/0.2.20\nv0.3.0\n"), \
                    redirect_stdout(output):
                main()
            self.assertEqual(output.getvalue(), "0.4.0\n")
            self.assertEqual(properties(pom.read_text(encoding="utf-8")),
                             {"revision": "0.4.0", "sha1": "", "changelist": ""})

    def test_explicit_build_version_does_not_change_pom(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pom = root / "pom.xml"
            pom.write_text(MANIFEST, encoding="utf-8")
            output = io.StringIO()
            with patch("version.ROOT", root), \
                    patch("sys.argv", ["version.py", "--build-version", "v0.4.0"]), \
                    redirect_stdout(output):
                main()
            self.assertEqual(output.getvalue(), "0.4.0\n")
            self.assertEqual(pom.read_text(encoding="utf-8"), MANIFEST)


if __name__ == "__main__":
    unittest.main()
